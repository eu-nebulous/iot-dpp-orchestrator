package eut.nebulouscloud.bridge;

import java.util.HashMap;
import java.util.Map;

import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.Message;
import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.RoutingContext;
import org.apache.activemq.artemis.protocol.amqp.broker.AMQPStandardMessage;
import org.apache.qpid.proton.amqp.messaging.AmqpValue;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;

public class ControlPlaneBridgePlugin extends NebulOuSBridge {

	public ControlPlaneBridgePlugin() {
		LOGGER = LoggerFactory.getLogger(ControlPlaneBridgePlugin.class);
	}
	
	static String CLUSTER_DEFINE_TOPIC = "eu.nebulouscloud.exn.sal.cluster.define";
	private Map<String, String> bridgePasswords = new HashMap<String, String>();
	private static String[] bridgedTopics = new String[] { "eu.nebulouscloud.ui.dsl.metric_model",
			"eu.nebulouscloud.optimiser.controller.model", "eu.nebulouscloud.optimiser.controller.metric_list",
			"eu.nebulouscloud.optimiser.controller.app_state", "eu.nebulouscloud.monitoring.slo.new",
			"eu.nebulouscloud.optimiser.solver.solution", "eu.nebulouscloud.optimiser.solver.context",
			"eu.nebulouscloud.solver.state","eu.nebulouscloud.monitoring.commands","eu.nebulouscloud.monitoring.metric_list","eu.nebulouscloud.ems.boot.reply" };

	private String getBody(Message message) {
		return (String) ((Map<String, Object>) ((AmqpValue) ((AMQPStandardMessage) message).getBody()).getValue())
				.get("body");
	}

	/**
	 * Processes a bridging configuration message received from an application.
	 * This method handles the setup of a bridge connection between the control plane
	 * and an application's broker. It verifies the application's registration status
	 * and initiates the bridge creation if valid.
	 *
	 * @param message The AMQP message containing bridging configuration details
	 * @throws Exception If there's an error processing the message or creating the bridge
	 */
	private void processBridgingConfigMessage(Message message) {
		try {
			String appID = message.getStringProperty("application");
			LOGGER.debug("Processing bridging config message for application: {}", appID);
			
			if (!bridgePasswords.containsKey(appID)) {
				LOGGER.error("Bridge connection attempt from user '{}' failed - application ID not registered", appID);
				return;
			}			
			LOGGER.info("Remote broker details message received for application: {}", appID);
			String appBrokerAddress = message.getStringProperty("appBrokerAddress");
			LOGGER.debug("Creating bridge for application: {} with broker address: {}", appID, appBrokerAddress);			
			createBridgeFromControlPlaneToApp(appID, bridgePasswords.get(appID), appBrokerAddress);
			LOGGER.info("Successfully processed bridging configuration for application: {}", appID);
		} catch (Exception ex) {
			LOGGER.error("Failed to process bridging configuration message: {}", ex.getMessage(), ex);
		}
	}

	@Override
	public void beforeMessageRoute(Message message, RoutingContext context, boolean direct, boolean rejectDuplicates)
			throws ActiveMQException {
		if (message.getAddress().toString().equals(CLUSTER_DEFINE_TOPIC)
				|| message.getAddress().toString().equals("topic://" + CLUSTER_DEFINE_TOPIC)) {
			LOGGER.info("Cluster define message intercepted");
			processClusterDefineMessage(message);
		}
		if (context.getServerSession() == null)
			return;
		String userAppId = extractAPPIdFromUserName(context.getServerSession().getValidatedUser());
		if (userAppId != null) {
			String claimedAppId = message.getStringProperty("application");			
			if (!userAppId.equals(claimedAppId)) {
				LOGGER.error("Message blocked by interceptor - user '{}' claims app '{}' but message is for app '{}'",
						context.getServerSession().getValidatedUser(), userAppId, claimedAppId);
				throw new ActiveMQException("Message blocked: User '" + context.getServerSession().getValidatedUser()
						+ "' attempted to send message for app '" + claimedAppId + "' but is only authorized for app '"
						+ userAppId + "'");
			}
			if (message.getAddress().equals("eu.nebulouscloud.bridging")) {
				processBridgingConfigMessage(message);
			}
		}

	}

	protected String constructConnectorName(String appId) {
		return "master-" + appId;
	}

	private void createBridgeFromControlPlaneToApp(String appId, String appBridgePassword, String appBrokerAddress) {


		String connectorName = constructConnectorName(appId);
		if (server.getConfiguration().getConnectorConfigurations().containsKey(connectorName)) {
			LOGGER.trace("Ignoring createBridgeFromControlPlaneToApp, bridge already exsists for app '{}'", appId);
			return;
		}
		LOGGER.info("Creating bridge from control plane to app '{}'", appId);
		try {
			LOGGER.info("Adding static connector to app '{}' using address '{}'", appId, appBrokerAddress);
			server.getActiveMQServerControl().addConnector(connectorName, appBrokerAddress);
		} catch (Exception e) {
			LOGGER.error("Failed to register static connector to app '{}' using address '{}': {}", appId,
					appBrokerAddress, e.getMessage(), e);
		}
		for (String toApplicationTopic : bridgedTopics) {
			createTopicBridge(appId, appBridgePassword, connectorName, toApplicationTopic);
		}
	}

	private void processClusterDefineMessage(Message message) {
		// {"when":"2024-08-09T09:46:25.613420252Z","metaData":{"user":"admin"},"body":"{\"name\":\"11461-9\",\"master-node\":\"m11461-9-master\",\"nodes\":[{\"nodeName\":\"m11461-9-master\",\"nodeCandidateId\":\"8a7484bf912bf07c01913683e4c528e4\",\"cloudId\":\"uio-openstack-optimizer\"},{\"nodeName\":\"n11461-9-dummy-app-worker-1-1\",\"nodeCandidateId\":\"8a7484bf912bf07c01913683e4c528e4\",\"cloudId\":\"uio-openstack-optimizer\"},{\"nodeName\":\"n11461-9-dummy-app-controller-1-1\",\"nodeCandidateId\":\"8a7484bf912bf07c01913683e4c528e4\",\"cloudId\":\"uio-openstack-optimizer\"}],\"env-var\":{\"APPLICATION_ID\":\"1146110908rest-processor-app1723196771743\",\"BROKER_ADDRESS\":\"158.37.63.86\",\"ACTIVEMQ_HOST\":\"158.37.63.86\",\"BROKER_PORT\":\"32754\",\"ACTIVEMQ_PORT\":\"32754\",\"ONM_IP\":\"158.39.201.249\",\"ONM_URL\":\"https://onm.cd.nebulouscloud.eu\",\"AMPL_LICENSE\":\"NjYxZTQzNDQ5ODE2NDczZWIzNDIwNDc2NzZlZjI5Mzc1MjQ0MDUyMGM3MzczYzI5MTg1ODBjNWFmNzBmMzZiN2U3YWYxNzZjYTY2NjQyYTZjMWYzYzFiNjQwNmFlYTgxMTRiZjhhNDg5ZjQ0OGJjZGIyYTc2MDYzNzNiMjNiMTdjNWQ4ZjlhMjg2MjcyYzg4ZjIxOWZjZWZjMTY0MzIxMmU2ZWFjZTY5M2EzMDliYjNlMzBkN2UzNTI3MjA3OTgxZTBhMjNhNWNkOGIzYjcyOGUwZTc2ZWJiZDQwMjNhZTZiNGJkZmFiYmY1MDdkZTJlODM0M2UyNmNjNDc4NjlhNjQ0ZmZkODYxZmQzNjE0ZmVmYTJkYmZhNzI0YmMyODU3MTFmM2Q1Zjg3M2IyOTk0ODViZGNlOTBiYTRlNzc1YjQwMjI1MTI3MzIzNTBlYzZhNjExOGI4NjkyNmUwMDhjNjg1OTQwNjAyYjA5NzhlYzAxMjlmY2Q4NzM0ZDhjNGM2NDIwYmQ4MzE4OWU0NWM0MTk1ZWE4MzMxMzI0NjE4ZjBjN2RlYTViMTk0MTQ0MTJjN2MzMTNiOTIzMmQ4MTVlMGIxZDYzZjYxY2M0MWM1MzIzMDdkOTBiYjkwMWMyYTM0NTZhMWU0MGQ0OTkzOTAxMWEwMTIwMjEwYzNkYWE1YjNlN2YzZTk4ZGNhMDRmZTgyNDA3ZDc4MzQ0NGIzODcwMGU1MzdlNGJkOWI3MmY3MGY1NDQwZGM4YmE1OWE5MjU1YzJlMWM0OGRmYWM2ZTAwNmE5MGZkMzI1ODYwYzVkMzFkNDRlZTBhNTZjZTJlNTM2OWM3MTMzOTE4NWNhZjAxMWIxNzY2NGE3YTRjNWRhZjM5MjMxM2Q4YWUxODdmZTI0NzY2M2JmYjI2MDIwMGFjNGIyN2JmNGI0NDIzNTYxMzE1MmJlZDQxODMzYTZlOWViNTE1YjBjMjNiNjkzMmRhNjE2MmQ3OTE0OWY4NTE1MTdiYTgwNDY4MjAzMzcwODA0YjYyZmZi\"}}"}
		String password = null;
		String applicationId = null;
		try {
			String content = getBody(message);
			LOGGER.info(content);
			// Parse the outer JSON
			JsonNode rootNode = om.readTree(content);
			// Get and parse the body string
			// String bodyString = rootNode.get("body").asText();
			String bodyString = content;
			JsonNode bodyNode = om.readTree(bodyString);

			// Get the env-var object and convert to Map
			Map<String, String> envVars = om.convertValue(bodyNode.get("env-var"), Map.class);

			if (!envVars.containsKey("NEBULOUS_MESSAGE_BRIDGE_PASSWORD")) {
				LOGGER.error("Can't find NEBULOUS_MESSAGE_BRIDGE_PASSWORD var");
				return;
			} else {
				password = envVars.get("NEBULOUS_MESSAGE_BRIDGE_PASSWORD");
			}
			if (!envVars.containsKey("APPLICATION_ID")) {
				LOGGER.error("Can't find APPLICATION_ID var");
				return;
			} else {
				applicationId = envVars.get("APPLICATION_ID");
			}

		} catch (Exception ex) {
			LOGGER.error("Couldn't parse APP creation message body", ex);
			return;
		}

		if (bridgePasswords.containsKey(applicationId)) {
			LOGGER.error("Application id " + applicationId + " already exists!");
			return;
		}
		addUser(applicationId, password);
		bridgePasswords.put(applicationId, password);
	}

	@Override
	public void init(Map<String, String> properties) {
		LOGGER.info("init...");
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
					Thread.sleep(1000);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
			try {
				if (!server.queueQuery(SimpleString.of("eu.nebulouscloud.bridging")).isExists()) {
					QueueConfiguration config = new QueueConfiguration();
					config.setAddress("eu.nebulouscloud.bridging");
					config.setRoutingType(RoutingType.ANYCAST);
					config.setName("eu.nebulouscloud.bridging");
					config.setConfigurationManaged(true);
					config.setAutoCreateAddress(true);
					config.setPurgeOnNoConsumers(false);
					server.createQueue(config);
				}

			} catch (Exception e) {
				e.printStackTrace();
			}

		}

	}

}
