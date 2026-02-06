package eut.nebulouscloud.cluster_info;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.config.WildcardConfiguration;
import org.apache.activemq.artemis.core.config.amqpBrokerConnectivity.AMQPBrokerConnectConfiguration;
import org.apache.activemq.artemis.core.config.amqpBrokerConnectivity.AMQPBrokerConnectionElement;
import org.apache.activemq.artemis.core.config.impl.ConfigurationImpl;
import org.apache.activemq.artemis.core.security.Role;
import org.apache.activemq.artemis.core.server.embedded.EmbeddedActiveMQ;
import org.apache.activemq.artemis.spi.core.security.ActiveMQBasicSecurityManager;
import org.apache.qpid.protonj2.client.Message;
import org.apache.qpid.protonj2.client.exceptions.ClientException;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;

import eu.nebulouscloud.exn.Connector;
import eu.nebulouscloud.exn.core.Consumer;
import eu.nebulouscloud.exn.core.Context;
import eu.nebulouscloud.exn.core.Handler;
import eu.nebulouscloud.exn.core.Publisher;
import eu.nebulouscloud.exn.core.SyncedPublisher;
import eu.nebulouscloud.exn.handlers.ConnectorHandler;

import eu.nebulouscloud.exn.settings.StaticExnConfig;
import eut.nebulouscloud.bridge.NebulOuSCoreMessage;
import eut.nebulouscloud.bridge.NebulousCoreMessageBrokerLocalStore;

/**
 * Test the correct functionaltiy of DynamicBridgePluginTest
 */
class ClusterInfoTest {

	static Logger LOGGER = LoggerFactory.getLogger(ClusterInfoTest.class);
	static int QueuesMonitoringProcesQueryIntervalSeconds = 3;
	ObjectMapper om = new ObjectMapper();
	static Context testContext = null;

	private EmbeddedActiveMQ createControlPlaneBroker(String nodeName, int port) throws Exception {
		Configuration config = new ConfigurationImpl();
		config.setName(nodeName);
		String foldersRoot = "data/" + new Date().getTime() + "/data_" + port;
		config.setBindingsDirectory(foldersRoot + "/bindings");
		config.setJournalDirectory(foldersRoot + "/journal");
		config.setJournalRetentionDirectory(foldersRoot + "/journalRetention");
		config.setLargeMessagesDirectory(foldersRoot + "/lm");
		config.setNodeManagerLockDirectory(foldersRoot + "/nodeManagerLock");
		config.setPagingDirectory(foldersRoot + "/paging");
		config.addAcceptorConfiguration("netty", "tcp://localhost:" + port);
		config.addAcceptorConfiguration("invm", "vm://0");
		config.setWildCardConfiguration(
				new WildcardConfiguration().setDelimiter('.').setAnyWords('>').setSingleWord('*'));
		ClusterInfoPlugin dbp = new ClusterInfoPlugin();
		dbp.init(Map.of("APP_ID", "myapp", "INFLUXDB_BUCKET", "mybucket", "INFLUXDB_ORG", "myorg", "INFLUXDB_TOKEN",
				"mytoken", "INFLUXDB_URL", "influxdburl", "BROKER_USERNAME", "admin", "BROKER_PASSWORD", "admin","BROKER_PORT",port+""));
		config.getBrokerPlugins().add(dbp);
		Map<String, Set<Role>> roles = new HashMap<String, Set<Role>>();
		roles.put(">", Set.of(new Role("admin", true, true, true, true, true, true, true, true, true, true),
				new Role("bridge", true, false, false, false, false, false, false, false, false, false)));
		config.setSecurityRoles(roles);

		/*
		 * AddressSettings as = new AddressSettings(); as.setAutoCreateAddresses(true);
		 * as.setAutoCreateQueues(true); config.addAddressesSetting(">",as);
		 */
		config.setSecurityEnabled(true);
		EmbeddedActiveMQ server = new EmbeddedActiveMQ();
		ActiveMQBasicSecurityManager sm = new ActiveMQBasicSecurityManager();
		sm.init(Map.of(ActiveMQBasicSecurityManager.BOOTSTRAP_USER, "admin",
				ActiveMQBasicSecurityManager.BOOTSTRAP_PASSWORD, "admin", ActiveMQBasicSecurityManager.BOOTSTRAP_ROLE,
				"admin"));

		server.setSecurityManager(sm);
		config.setJMXManagementEnabled(true);
		server.setConfiguration(config);

		server.start();
		while (!server.getActiveMQServer().isActive()) {
			System.out.println("Waiting for server to start...");
			Thread.sleep(500);
		}
		sm.addNewUser("user1", "user1", "admin");
		return server;
	}

	@Test
	void test2() throws Exception {
		EmbeddedActiveMQ broker = createControlPlaneBroker("control-plane", 3356);
		Consumer testConsumer = new Consumer("monitoring", "eu.>", new Handler() {

			@Override
			public void onMessage(String key, String address, Map body, Message message, Context context) {
				String to = null;
				try {
					to = message.to();
				} catch (ClientException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				try {
					if (message.to().contains("eu.nebulouscloud.controlPlaneClient.health"))
						return;
				} catch (ClientException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				
				if("topic://eu.nebulouscloud.app_cluster.influxdb.get".equals(to))
				{
					LOGGER.info("");
				}
				Object correlationId = 0;
				try {
					correlationId = message.correlationId();
				} catch (ClientException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				String content ="";
				try{
					content = body.toString();
					content = om.writeValueAsString(body);
				}
				catch (Exception e) {
				}
				String subject = "?";
				try {
					subject = message.subject();
				} catch (ClientException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				Map<Object, Object> props = new HashMap<Object, Object>();
				try {
					message.forEachProperty((k, v) -> props.put(k, v));
				} catch (ClientException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				LOGGER.info("\r\n{}\r\nsubject:{}\r\nproperties:{}\r\ncorrelationId:{}\r\npayload:{}", to, subject,
						props, correlationId,content);
			}
		}, true, true);
		
		
		Connector controlPlaneClient = new Connector("controlPlaneClient", new ConnectorHandler() {
			public void onReady(Context context) {
				testContext = context;
			}
		}, List.of(), List.of(testConsumer), true, true,
				new StaticExnConfig("localhost", 3356, "admin", "admin", 15, "eu.nebulouscloud"));
		controlPlaneClient.start();
		while (testContext == null) {
			try {
				LOGGER.info("Waiting for testContext");
				Thread.sleep(1000);

			} catch (Exception ex) {
				LOGGER.error("", ex);
			}
		}
		LOGGER.info("Optimiser-controller connected to ActiveMQ");

		SyncedPublisher getInfluxdbconnection = new SyncedPublisher("getInfluxdbconnection",
				ClusterInfoPlugin.INFLUXDB_CREDENTIALS_GET_TOPIC, true, true);
		testContext.registerPublisher(getInfluxdbconnection);
		Map<String, Object> msg = Map.of("body", "");
		Map<String, Object> response = getInfluxdbconnection.sendSync(msg, "30a13d38-61a8-4523-892e-3529f1e19cc3", null, false);
		LOGGER.info(response.toString());
		while(true)
		{
			Thread.sleep(1000);
		}
		
	}

	@Test
	void Test1() throws Exception {

		/**
		 * Create a local ActiveMQ server
		 */

		try {
			String APP_ID = "1146110908rest-processor-app1723196772";
			String NEBULOUS_CONTROL_PLANE_PASSWORD = APP_ID;
			int controlPlanePort = 3356;
			EmbeddedActiveMQ broker = createControlPlaneBroker("control-plane", controlPlanePort);
			NebulousCoreMessageBrokerLocalStore controlPlaneMessageStore = new NebulousCoreMessageBrokerLocalStore();
			LOGGER.info("Starting controlPlaneClient");
			Consumer testConsumer = new Consumer("monitoring", "eu.>", new Handler() {

				@Override
				public void onMessage(String key, String address, Map body, Message message, Context context) {
					String to = null;
					try {
						to = message.to();
					} catch (ClientException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
					try {
						if (message.to().contains("eu.nebulouscloud.controlPlaneClient.health"))
							return;
					} catch (ClientException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
					LOGGER.info(to);
					controlPlaneMessageStore.onMessage(key, address, body, message, context);
				}
			}, true, true);
			Publisher testPublisher = new Publisher("testPublisher", ClusterInfoPlugin.INFLUXDB_CREDENTIALS_GET_TOPIC,
					true, true);
			Connector controlPlaneClient = new Connector("controlPlaneClient", new ConnectorHandler() {
				public void onReady(AtomicReference<Context> context) {
					LOGGER.info("Optimiser-controller connected to ActiveMQ");
				}
			}, List.of(testPublisher), List.of(testConsumer), true, true,
					new StaticExnConfig("0.0.0.0", controlPlanePort, "admin", "admin"));
			controlPlaneClient.start();
			Thread.sleep(1000);
			controlPlaneMessageStore.clear();
			testPublisher.send(Map.of("body", ""), null, false);
			Thread.sleep(1000);
			Optional<NebulOuSCoreMessage> defineMessage = controlPlaneMessageStore.findFirst(APP_ID,
					ClusterInfoPlugin.INFLUXDB_CREDENTIALS_GET_REPLY_TOPIC, null, 3);
			assertTrue(defineMessage.isPresent());

		} finally {
			try {
				// broker.stop();
			} catch (Exception e) {
				LOGGER.error("", e);
			}
		}

	}

}
