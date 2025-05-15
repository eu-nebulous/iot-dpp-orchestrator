package eut.nebulouscloud.bridge;

import java.util.Map;
import java.util.Optional;

import javax.jms.Message;
import javax.jms.QueueConnection;
import javax.jms.QueueSender;
import javax.jms.QueueSession;
import javax.jms.Session;

import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.apache.activemq.artemis.spi.core.security.ActiveMQBasicSecurityManager;
import org.slf4j.LoggerFactory;

public class AppBridgePlugin extends NebulOuSBridge {
	ActiveMQBasicSecurityManager securityManager;

	static String CLUSTER_DEFINE_TOPIC = "eu.nebulouscloud.exn.sal.cluster.define";
	private String APPID;
	private String appBrokerAddress;
	private String bridgeUserPassword;
	private String controlPlaneBrokerAddress;

	public AppBridgePlugin() {
		LOGGER = LoggerFactory.getLogger(AppBridgePlugin.class);
	}

	private static String[] appToControlPLaneTopics = new String[] { "eu.nebulouscloud.ui.dsl.metric_model",
			"eu.nebulouscloud.monitoring.slo.new", "eu.nebulouscloud.optimiser.solver.solution",
			"eu.nebulouscloud.solver.state","eu.nebulouscloud.monitoring.commands.reply","eu.nebulouscloud.ems.boot"  };

	private void createBridgeFromAppToControlPlane(String appId, String appBridgePassword) {
		String connectorName = "nebulous_control_plane";
		if (server.getConfiguration().getConnectorConfigurations().containsKey(connectorName)) {
			LOGGER.trace("Ignoring createBridgeFromAppToControlPlane, bridge already exsists for app '{}'", appId);
			return;
		}

		try {
			LOGGER.info("Adding static connector to app '{}' using address '{}'", appId, controlPlaneBrokerAddress);
			server.getActiveMQServerControl().addConnector(connectorName, controlPlaneBrokerAddress);
		} catch (Exception e) {
			LOGGER.error("Failed to register static connector to app '{}' using address '{}': {}", appId,
					controlPlaneBrokerAddress, e.getMessage(), e);
		}
		for (String toApplicationTopic : appToControlPLaneTopics) {
			createTopicBridge(appId, appBridgePassword, connectorName, toApplicationTopic);
		}
	}

	@Override
	public void init(Map<String, String> properties) {

		LOGGER.info("init...");
		APPID = Optional.ofNullable(properties.getOrDefault("APPLICATION_ID", null))
				.orElseThrow(() -> new IllegalStateException("APPLICATION_ID parameter is not defined"));

		bridgeUserPassword = Optional.ofNullable(properties.getOrDefault("NEBULOUS_MESSAGE_BRIDGE_PASSWORD", null))
				.orElseThrow(
						() -> new IllegalStateException("NEBULOUS_MESSAGE_BRIDGE_PASSWORD parameter is not defined"));

		appBrokerAddress = "tcp://"
				+ Optional.ofNullable(properties.getOrDefault("APP_BROKER_ADDRESS", null))
						.orElseThrow(() -> new IllegalStateException(
								"APP_BROKER_ADDRESS parameter is not defined"));		
		
		controlPlaneBrokerAddress = "tcp://"
				+ Optional.ofNullable(properties.getOrDefault("NEBULOUS_CONTROL_PLANE_BROKER_ADDRESS", null))
						.orElseThrow(() -> new IllegalStateException(
								"NEBULOUS_CONTROL_PLANE_BROKER_ADDRESS parameter is not defined"));
	}
	
	/**
	 * Sends a message to the control plane broker containing application bridge details.
	 * The message includes the application  and broker address.
	 * 
	 * @return true if the message was sent successfully, false otherwise
	 */
	private boolean sendBrokerDetailsMessage() {
		LOGGER.info("Attempting to send broker details message to control plane. App: {} broker address: {}", APPID, appBrokerAddress);

		try (ActiveMQConnectionFactory connectionFactory = new ActiveMQConnectionFactory(controlPlaneBrokerAddress);
				QueueConnection connection = connectionFactory.createQueueConnection(constructBridgeUserName(APPID),
						bridgeUserPassword);
				QueueSession session = connection.createQueueSession(false, Session.AUTO_ACKNOWLEDGE);
				QueueSender sender = session.createSender(session.createQueue("eu.nebulouscloud.bridging"));) {
			
			Message message = session.createMessage();
			message.setStringProperty("application", APPID);
			message.setStringProperty("appBrokerAddress", appBrokerAddress);			
			sender.send(message);
			LOGGER.info("Successfully sent broker details message to control plane. App: {} broker address: {}", APPID, appBrokerAddress);
			return true;
		} catch (Exception ex) {
			LOGGER.error("Failed to send broker details message to control plane. App: {} broker address: {}", APPID, appBrokerAddress, ex.getMessage(), ex);
			return false;
		}
	}
	
	@Override
	public void registered(ActiveMQServer server) {
		LOGGER.info("registered...");
		super.registered(server);
		new Thread(new ConfigRunner(server)).start();

	}

	class ConfigRunner implements Runnable {

		ActiveMQServer server;

		private ConfigRunner(ActiveMQServer server) {
			this.server = server;
		}

		@Override
		public void run() {
			while (!server.isActive()) {
				LOGGER.info("Waiting for server to start...");
				try {
					Thread.sleep(500);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}			
			addUser(APPID, bridgeUserPassword);
			sendBrokerDetailsMessage();
			createBridgeFromAppToControlPlane(APPID, bridgeUserPassword);
		}

	}

}
