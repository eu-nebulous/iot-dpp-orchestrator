package eut.nebulouscloud.bridge;

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
import org.apache.activemq.artemis.core.config.amqpBrokerConnectivity.AMQPBrokerConnectConfiguration;
import org.apache.activemq.artemis.core.config.amqpBrokerConnectivity.AMQPBrokerConnectionElement;
import org.apache.activemq.artemis.core.config.impl.ConfigurationImpl;
import org.apache.activemq.artemis.core.security.Role;
import org.apache.activemq.artemis.core.server.embedded.EmbeddedActiveMQ;
import org.apache.activemq.artemis.spi.core.security.ActiveMQBasicSecurityManager;
import org.apache.qpid.protonj2.client.Message;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;

import eu.nebulouscloud.exn.Connector;
import eu.nebulouscloud.exn.core.Consumer;
import eu.nebulouscloud.exn.core.Context;
import eu.nebulouscloud.exn.core.Handler;
import eu.nebulouscloud.exn.core.Publisher;
import eu.nebulouscloud.exn.handlers.ConnectorHandler;
import eu.nebulouscloud.exn.settings.StaticExnConfig;

/**
 * Test the correct functionaltiy of DynamicBridgePluginTest
 */
class DynamicBridgePluginTest {

	static Logger LOGGER = LoggerFactory.getLogger(DynamicBridgePluginTest.class);
	static int QueuesMonitoringProcesQueryIntervalSeconds = 3;
	ObjectMapper om = new ObjectMapper();

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
		// config.addConnectorConfiguration("serverAt" + port + "Connector",
		// "tcp://localhost:" + port);
		config.addAcceptorConfiguration("netty", "tcp://localhost:" + port);
		// config.addAMQPConnection(new
		// AMQPBrokerConnectConfiguration("AMQP","tcp://localhost:" + port ));
		// config.setAmqpUseCoreSubscriptionNaming(false);
		/*
		 * Map<String, Object> params = new HashMap<>(); params.put("protocols",
		 * "AMQP"); params.put("host", "0.0.0.0"); params.put("port", port);
		 * config.addAcceptorConfiguration( new
		 * TransportConfiguration(NettyAcceptorFactory.class.getName(), params,
		 * "amqp"));
		 */
		/*
		 * config.setWildCardConfiguration(new WildcardConfiguration()
		 * .setDelimiter('.') .setAnyWords('>') .setSingleWord('*'));
		 */

		// config.addAcceptorConfiguration("amqp", "amqp://localhost:" + port);
		ControlPlaneBridgePlugin dbp = new ControlPlaneBridgePlugin();
		config.getBrokerPlugins().add(dbp);
		Map<String, Set<Role>> roles = new HashMap<String, Set<Role>>();
		roles.put("#", Set.of(new Role("admin", true, true, true, true, true, true, true, true, true, true),
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

	private EmbeddedActiveMQ createAppClusterBroker(String nodeName, int port, int controlPlanePort, String appID,
			String appPwd) throws Exception {
		Configuration config = new ConfigurationImpl();
		config.setName(nodeName);
		String foldersRoot = "data/" + new Date().getTime() + "/data_" + port;
		config.setBindingsDirectory(foldersRoot + "/bindings");
		config.setJournalDirectory(foldersRoot + "/journal");
		config.setJournalRetentionDirectory(foldersRoot + "/journalRetention");
		config.setLargeMessagesDirectory(foldersRoot + "/lm");
		config.setNodeManagerLockDirectory(foldersRoot + "/nodeManagerLock");
		config.setPagingDirectory(foldersRoot + "/paging");
		AMQPBrokerConnectConfiguration bk = new AMQPBrokerConnectConfiguration();
		AMQPBrokerConnectionElement el = new AMQPBrokerConnectionElement();
		// el.
		// AMQPBrokerConnectConfiguration bk = new AMQPBrokerConnectConfiguration();
		// AMQPBrokerConnectionElement el = new AMQPBrokerConnectionElement();
		// el.set
		// bk.addSender(null)
		// config.addAMQPConnection(null)

		// config.addConnectorConfiguration("serverAt" + port + "Connector",
		// "tcp://localhost:" + port);
		config.addAcceptorConfiguration("netty", "tcp://localhost:" + port);
		// config.addAMQPConnection(new
		// AMQPBrokerConnectConfiguration("AMQP","tcp://localhost:" + port ));
		// config.setAmqpUseCoreSubscriptionNaming(false);
		/*
		 * Map<String, Object> params = new HashMap<>(); params.put("protocols",
		 * "AMQP"); params.put("host", "0.0.0.0"); params.put("port", port);
		 * config.addAcceptorConfiguration( new
		 * TransportConfiguration(NettyAcceptorFactory.class.getName(), params,
		 * "amqp"));
		 */
		/*
		 * config.setWildCardConfiguration(new WildcardConfiguration()
		 * .setDelimiter('.') .setAnyWords('>') .setSingleWord('*'));
		 */

		// config.addAcceptorConfiguration("amqp", "amqp://localhost:" + port);
		AppBridgePlugin dbp = new AppBridgePlugin();
		Map<String, String> params = new HashMap<String, String>();
		params.put("APPLICATION_ID", appID);
		params.put("NEBULOUS_MESSAGE_BRIDGE_PASSWORD", appPwd);
		params.put("NEBULOUS_CONTROL_PLANE_BROKER_ADDRESS", "localhost:" + controlPlanePort);
		params.put("APPLICATION_ID", appID);
		dbp.init(params);
		config.getBrokerPlugins().add(dbp);
		Map<String, Set<Role>> roles = new HashMap<String, Set<Role>>();
		roles.put("#", Set.of(new Role("admin", true, true, true, true, true, true, true, true, true, true),
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
		return server;
	}

	@Test
	void Test1() throws Exception {

		/**
		 * Create a local ActiveMQ server
		 */

		try {
			String APP_ID = "1146110908rest-processor-app1723196772";
			String NEBULOUS_CONTROL_PLANE_PASSWORD = APP_ID;
			EmbeddedActiveMQ broker = null;
			NebulousCoreMessageBrokerLocalStore controlPlaneMessageStore = new NebulousCoreMessageBrokerLocalStore();
			int controlPlanePort = 3355;
			broker = createControlPlaneBroker("test-server", controlPlanePort);

			LOGGER.info("Starting controlPlaneClient");
			Consumer controlPlaneConsummer = new Consumer("monitoring", "eu.#", new Handler() {

				@Override
				public void onMessage(String key, String address, Map body, Message message, Context context) {
					controlPlaneMessageStore.onMessage(key, address, body, message, context);
				}
			}, true, true);
			Publisher controlPlaneDefinePub = new Publisher("controlPlanePublisher",
					"eu.nebulouscloud.exn.sal.cluster.define", true, true);
			Publisher controlPlaneMetricModelPub = new Publisher("controlPlanePublisherB",
					"eu.nebulouscloud.ui.dsl.metric_model", true, true);
			Connector controlPlaneClient = new Connector("controlPlaneClient", new ConnectorHandler() {
				public void onReady(AtomicReference<Context> context) {
					LOGGER.info("Optimiser-controller connected to ActiveMQ");
				}
			}, List.of(controlPlaneDefinePub, controlPlaneMetricModelPub), List.of(controlPlaneConsummer), true, true,
					new StaticExnConfig("0.0.0.0", controlPlanePort, "admin", "admin"));
			controlPlaneClient.start();
			Thread.sleep(1000);
			String jsonString = "{\"name\":\"11461-9\",\"master-node\":\"m11461-9-master\",\"nodes\":[{\"nodeName\":\"m11461-9-master\",\"nodeCandidateId\":\"8a7484bf912bf07c01913683e4c528e4\",\"cloudId\":\"uio-openstack-optimizer\"},{\"nodeName\":\"n11461-9-dummy-app-worker-1-1\",\"nodeCandidateId\":\"8a7484bf912bf07c01913683e4c528e4\",\"cloudId\":\"uio-openstack-optimizer\"},{\"nodeName\":\"n11461-9-dummy-app-controller-1-1\",\"nodeCandidateId\":\"8a7484bf912bf07c01913683e4c528e4\",\"cloudId\":\"uio-openstack-optimizer\"}],\"env-var\":{\"NEBULOUS_CONTROL_PLANE_PASSWORD\":\""
					+ NEBULOUS_CONTROL_PLANE_PASSWORD + "\",     \"APPLICATION_ID\":\"" + APP_ID
					+ "\",\"BROKER_ADDRESS\":\"158.37.63.86\",\"ACTIVEMQ_HOST\":\"158.37.63.86\",\"BROKER_PORT\":\"32754\",\"ACTIVEMQ_PORT\":\"32754\",\"ONM_IP\":\"158.39.201.249\",\"ONM_URL\":\"https://onm.cd.nebulouscloud.eu\",\"AMPL_LICENSE\":\"NjYxZTQzNDQ5ODE2NDczZWIzNDIwNDc2NzZlZjI5Mzc1MjQ0MDUyMGM3MzczYzI5MTg1ODBjNWFmNzBmMzZiN2U3YWYxNzZjYTY2NjQyYTZjMWYzYzFiNjQwNmFlYTgxMTRiZjhhNDg5ZjQ0OGJjZGIyYTc2MDYzNzNiMjNiMTdjNWQ4ZjlhMjg2MjcyYzg4ZjIxOWZjZWZjMTY0MzIxMmU2ZWFjZTY5M2EzMDliYjNlMzBkN2UzNTI3MjA3OTgxZTBhMjNhNWNkOGIzYjcyOGUwZTc2ZWJiZDQwMjNhZTZiNGJkZmFiYmY1MDdkZTJlODM0M2UyNmNjNDc4NjlhNjQ0ZmZkODYxZmQzNjE0ZmVmYTJkYmZhNzI0YmMyODU3MTFmM2Q1Zjg3M2IyOTk0ODViZGNlOTBiYTRlNzc1YjQwMjI1MTI3MzIzNTBlYzZhNjExOGI4NjkyNmUwMDhjNjg1OTQwNjAyYjA5NzhlYzAxMjlmY2Q4NzM0ZDhjNGM2NDIwYmQ4MzE4OWU0NWM0MTk1ZWE4MzMxMzI0NjE4ZjBjN2RlYTViMTk0MTQ0MTJjN2MzMTNiOTIzMmQ4MTVlMGIxZDYzZjYxY2M0MWM1MzIzMDdkOTBiYjkwMWMyYTM0NTZhMWU0MGQ0OTkzOTAxMWEwMTIwMjEwYzNkYWE1YjNlN2YzZTk4ZGNhMDRmZTgyNDA3ZDc4MzQ0NGIzODcwMGU1MzdlNGJkOWI3MmY3MGY1NDQwZGM4YmE1OWE5MjU1YzJlMWM0OGRmYWM2ZTAwNmE5MGZkMzI1ODYwYzVkMzFkNDRlZTBhNTZjZTJlNTM2OWM3MTMzOTE4NWNhZjAxMWIxNzY2NGE3YTRjNWRhZjM5MjMxM2Q4YWUxODdmZTI0NzY2M2JmYjI2MDIwMGFjNGIyN2JmNGI0NDIzNTYxMzE1MmJlZDQxODMzYTZlOWViNTE1YjBjMjNiNjkzMmRhNjE2MmQ3OTE0OWY4NTE1MTdiYTgwNDY4MjAzMzcwODA0YjYyZmZi\"}}";
			controlPlaneDefinePub.send(Map.of("body", jsonString), APP_ID, false);
			Optional<NebulOuSCoreMessage> defineMessage = controlPlaneMessageStore.findFirst(APP_ID,
					"eu.nebulouscloud.exn.sal.cluster.define", null, 3);
			assertTrue(defineMessage.isPresent());
			LOGGER.info("Starting appClusterClient");

			NebulousCoreMessageBrokerLocalStore clusterMessageStore = new NebulousCoreMessageBrokerLocalStore();
			// Consumer appClusterConsumer = new Consumer("monitoring", APP_ID+"-eu.#", new
			// Handler() {
			// 1146110908rest-processor-app1723196772-eu.nebulouscloud.ui.dsl.metric_model
			Consumer appClusterConsumer = new Consumer("appClusterConsumer",
					APP_ID + "-eu.nebulouscloud.ui.dsl.metric_model", new Handler() {
						@Override
						public void onMessage(String key, String address, Map body, Message message, Context context) {
							clusterMessageStore.onMessage(key, address, body, message, context);
						}
					}, true, true);
			// Publisher appClusterPublisher = new Publisher("appClusterPublisher",
			// "eu.nebulouscloud.exn.sal.cluster.define",true,true);
			Connector appClusterClient = new Connector("appClusterClient", new ConnectorHandler() {
			}, List.of(), List.of(appClusterConsumer), false, false,
					new StaticExnConfig("0.0.0.0", controlPlanePort, APP_ID, APP_ID));
			appClusterClient.start();

			// 1146110908rest-processor-app1723196772-eu.nebulouscloud.optimiser.controller.metric_list
			// 1146110908rest-processor-app1723196772-eu.#
			// controlPlanePublisher.send(om.readValue(jsonString, Map.class));

			/**
			 * Assert that a message for same APP_ID is recieved
			 */
			controlPlaneMetricModelPub.send(Map.of("hola", "hola"), APP_ID, false);
			Thread.sleep(2000);
			assert (clusterMessageStore.allMessages().size() > 0);
			clusterMessageStore.clear();

			/**
			 * Assert that a message for other APP_ID is not recieved
			 */
			controlPlaneMetricModelPub.send(Map.of("hola", "hola"), APP_ID + "B", false);
			Thread.sleep(2000);
			assert (clusterMessageStore.allMessages().size() == 0);

			/*
			 * while(true) {
			 * 
			 * LOGGER.info("clusterMessageStore count:"+clusterMessageStore.allMessages().
			 * size()); Thread.sleep(1000); }
			 */

		} finally {
			try {
				// broker.stop();
			} catch (Exception e) {
				LOGGER.error("", e);
			}
		}

	}

	@Test
	void Test2() throws Exception {

		/**
		 * Create a local ActiveMQ server
		 */

		try {
			String APP_ID = "1146110908rest-processor-app1723196772";
			String NEBULOUS_CONTROL_PLANE_PASSWORD = APP_ID;

			int controlPlanePort = 3355;
			EmbeddedActiveMQ broker = createControlPlaneBroker("control-plane", controlPlanePort);
			NebulousCoreMessageBrokerLocalStore controlPlaneMessageStore = new NebulousCoreMessageBrokerLocalStore();

			LOGGER.info("Starting controlPlaneClient");
			Consumer controlPlaneConsummer = new Consumer("monitoring", "eu.#", new Handler() {

				@Override
				public void onMessage(String key, String address, Map body, Message message, Context context) {
					controlPlaneMessageStore.onMessage(key, address, body, message, context);
				}
			}, true, true);
			Publisher controlPlaneDefinePub = new Publisher("controlPlanePublisher",
					"eu.nebulouscloud.exn.sal.cluster.define", true, true);
			Publisher controlPlaneMetricModelPub = new Publisher("controlPlanePublisherB",
					"eu.nebulouscloud.ui.dsl.metric_model", true, true);
			Connector controlPlaneClient = new Connector("controlPlaneClient", new ConnectorHandler() {
				public void onReady(AtomicReference<Context> context) {
					LOGGER.info("Optimiser-controller connected to ActiveMQ");
				}
			}, List.of(controlPlaneDefinePub, controlPlaneMetricModelPub), List.of(controlPlaneConsummer), true, true,
					new StaticExnConfig("0.0.0.0", controlPlanePort, "admin", "admin"));
			controlPlaneClient.start();
			Thread.sleep(1000);
			String jsonString = "{\"name\":\"11461-9\",\"master-node\":\"m11461-9-master\",\"nodes\":[{\"nodeName\":\"m11461-9-master\",\"nodeCandidateId\":\"8a7484bf912bf07c01913683e4c528e4\",\"cloudId\":\"uio-openstack-optimizer\"},{\"nodeName\":\"n11461-9-dummy-app-worker-1-1\",\"nodeCandidateId\":\"8a7484bf912bf07c01913683e4c528e4\",\"cloudId\":\"uio-openstack-optimizer\"},{\"nodeName\":\"n11461-9-dummy-app-controller-1-1\",\"nodeCandidateId\":\"8a7484bf912bf07c01913683e4c528e4\",\"cloudId\":\"uio-openstack-optimizer\"}],\"env-var\":{\"NEBULOUS_CONTROL_PLANE_PASSWORD\":\""
					+ NEBULOUS_CONTROL_PLANE_PASSWORD + "\",     \"APPLICATION_ID\":\"" + APP_ID
					+ "\",\"BROKER_ADDRESS\":\"158.37.63.86\",\"ACTIVEMQ_HOST\":\"158.37.63.86\",\"BROKER_PORT\":\"32754\",\"ACTIVEMQ_PORT\":\"32754\",\"ONM_IP\":\"158.39.201.249\",\"ONM_URL\":\"https://onm.cd.nebulouscloud.eu\",\"AMPL_LICENSE\":\"NjYxZTQzNDQ5ODE2NDczZWIzNDIwNDc2NzZlZjI5Mzc1MjQ0MDUyMGM3MzczYzI5MTg1ODBjNWFmNzBmMzZiN2U3YWYxNzZjYTY2NjQyYTZjMWYzYzFiNjQwNmFlYTgxMTRiZjhhNDg5ZjQ0OGJjZGIyYTc2MDYzNzNiMjNiMTdjNWQ4ZjlhMjg2MjcyYzg4ZjIxOWZjZWZjMTY0MzIxMmU2ZWFjZTY5M2EzMDliYjNlMzBkN2UzNTI3MjA3OTgxZTBhMjNhNWNkOGIzYjcyOGUwZTc2ZWJiZDQwMjNhZTZiNGJkZmFiYmY1MDdkZTJlODM0M2UyNmNjNDc4NjlhNjQ0ZmZkODYxZmQzNjE0ZmVmYTJkYmZhNzI0YmMyODU3MTFmM2Q1Zjg3M2IyOTk0ODViZGNlOTBiYTRlNzc1YjQwMjI1MTI3MzIzNTBlYzZhNjExOGI4NjkyNmUwMDhjNjg1OTQwNjAyYjA5NzhlYzAxMjlmY2Q4NzM0ZDhjNGM2NDIwYmQ4MzE4OWU0NWM0MTk1ZWE4MzMxMzI0NjE4ZjBjN2RlYTViMTk0MTQ0MTJjN2MzMTNiOTIzMmQ4MTVlMGIxZDYzZjYxY2M0MWM1MzIzMDdkOTBiYjkwMWMyYTM0NTZhMWU0MGQ0OTkzOTAxMWEwMTIwMjEwYzNkYWE1YjNlN2YzZTk4ZGNhMDRmZTgyNDA3ZDc4MzQ0NGIzODcwMGU1MzdlNGJkOWI3MmY3MGY1NDQwZGM4YmE1OWE5MjU1YzJlMWM0OGRmYWM2ZTAwNmE5MGZkMzI1ODYwYzVkMzFkNDRlZTBhNTZjZTJlNTM2OWM3MTMzOTE4NWNhZjAxMWIxNzY2NGE3YTRjNWRhZjM5MjMxM2Q4YWUxODdmZTI0NzY2M2JmYjI2MDIwMGFjNGIyN2JmNGI0NDIzNTYxMzE1MmJlZDQxODMzYTZlOWViNTE1YjBjMjNiNjkzMmRhNjE2MmQ3OTE0OWY4NTE1MTdiYTgwNDY4MjAzMzcwODA0YjYyZmZi\"}}";
			controlPlaneDefinePub.send(Map.of("body", jsonString), APP_ID, false);
			Optional<NebulOuSCoreMessage> defineMessage = controlPlaneMessageStore.findFirst(APP_ID,
					"eu.nebulouscloud.exn.sal.cluster.define", null, 3);
			assertTrue(defineMessage.isPresent());
			LOGGER.info("Starting appClusterClient");
			Thread.sleep(2000);

			int appClusterBrokerPort = 3356;
			EmbeddedActiveMQ appClusterBroker = createAppClusterBroker("app-cluster", appClusterBrokerPort,
					controlPlanePort, APP_ID, NEBULOUS_CONTROL_PLANE_PASSWORD);
			NebulousCoreMessageBrokerLocalStore clusterMessageStore = new NebulousCoreMessageBrokerLocalStore();
			Consumer appClusterConsumer = new Consumer("appClusterConsumer", "eu.#", new Handler() {
				@Override
				public void onMessage(String key, String address, Map body, Message message, Context context) {
					clusterMessageStore.onMessage(key, address, body, message, context);
				}
			}, true, true);
			Publisher appClusterPublisher = new Publisher("appClusterPublisher", "eu.nebulouscloud.ui.dsl.metric_model",
					true, true);
			Connector appClusterClient = new Connector("appClusterClient", new ConnectorHandler() {
			}, List.of(appClusterPublisher), List.of(appClusterConsumer), false, false,
					new StaticExnConfig("0.0.0.0", appClusterBrokerPort, "admin", "admin"));
			appClusterClient.start();
			Thread.sleep(2000);
			/**
			 * Assert that app recieves messages for APP_ID
			 */
			clusterMessageStore.clear();
			controlPlaneMessageStore.clear();
			controlPlaneMetricModelPub.send(Map.of("hola", "hola"), APP_ID, false);
			Thread.sleep(2000);
			assertEquals(1, controlPlaneMessageStore.allMessages().size());
			assert (clusterMessageStore.allMessages().size() > 0);

			/**
			 * Assert that app doesnt' recieve messages for different APP_ID
			 */
			clusterMessageStore.clear();
			controlPlaneMessageStore.clear();
			controlPlaneMetricModelPub.send(Map.of("hola", "hola"), APP_ID + "B", false);
			Thread.sleep(2000);
			assertEquals(0, clusterMessageStore.allMessages().size());

			/**
			 * Assert that control plane recieves messages for APP_ID
			 */
			clusterMessageStore.clear();
			controlPlaneMessageStore.clear();
			appClusterPublisher.send(Map.of("hola", "hola"), APP_ID, false);
			Thread.sleep(2000);
			assertEquals(1, clusterMessageStore.allMessages().size());
			assertEquals(1, controlPlaneMessageStore.allMessages().size());

			/**
			 * Assert that control plane doesn't recieve messages for other APP_ID
			 */
			clusterMessageStore.clear();
			controlPlaneMessageStore.clear();
			appClusterPublisher.send(Map.of("hola", "hola"), APP_ID + "B", false);
			Thread.sleep(2000);
			assertEquals(1, clusterMessageStore.allMessages().size());
			assertEquals(0, controlPlaneMessageStore.allMessages().size());

		} finally {
			try {
				// broker.stop();
			} catch (Exception e) {
				LOGGER.error("", e);
			}
		}

	}

	@Test
	void Test3() throws Exception {

		/**
		 * Create a local ActiveMQ server
		 */

		try {
			int controlPlanePort = 5672;
			EmbeddedActiveMQ broker = createControlPlaneBroker("control-plane", controlPlanePort);
			NebulousCoreMessageBrokerLocalStore controlPlaneMessageStore = new NebulousCoreMessageBrokerLocalStore();

			LOGGER.info("Starting controlPlaneClient");
			Consumer controlPlaneConsummer = new Consumer("monitoring", "eu.#", new Handler() {

				@Override
				public void onMessage(String key, String address, Map body, Message message, Context context) {
					controlPlaneMessageStore.onMessage(key, address, body, message, context);
				}
			}, true, true);
			Publisher controlPlaneDefinePub = new Publisher("controlPlanePublisher",
					"eu.nebulouscloud.exn.sal.cluster.define", true, true);
			Publisher controlPlaneMetricModelPub = new Publisher("controlPlanePublisherB",
					"eu.nebulouscloud.ui.dsl.metric_model", true, true);
			Connector controlPlaneClient = new Connector("controlPlaneClient", new ConnectorHandler() {
				public void onReady(AtomicReference<Context> context) {
					LOGGER.info("Optimiser-controller connected to ActiveMQ");
				}
			}, List.of(controlPlaneDefinePub, controlPlaneMetricModelPub), List.of(controlPlaneConsummer), true, true,
					new StaticExnConfig("0.0.0.0", controlPlanePort, "admin", "admin"));
			controlPlaneClient.start();
			while (true)
				Thread.sleep(1000);

		} finally {
			try {
				// broker.stop();
			} catch (Exception e) {
				LOGGER.error("", e);
			}
		}

	}

}
