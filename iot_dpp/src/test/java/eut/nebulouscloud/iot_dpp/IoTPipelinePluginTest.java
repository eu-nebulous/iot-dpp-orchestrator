package eut.nebulouscloud.iot_dpp;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import org.apache.activemq.artemis.core.config.ClusterConnectionConfiguration;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.config.DivertConfiguration;
import org.apache.activemq.artemis.core.config.TransformerConfiguration;
import org.apache.activemq.artemis.core.config.impl.ConfigurationImpl;
import org.apache.activemq.artemis.core.security.CheckType;
import org.apache.activemq.artemis.core.security.Role;
import org.apache.activemq.artemis.core.server.cluster.impl.MessageLoadBalancingType;
import org.apache.activemq.artemis.core.server.embedded.EmbeddedActiveMQ;
import org.apache.activemq.artemis.spi.core.security.ActiveMQSecurityManager;
import org.eclipse.paho.client.mqttv3.IMqttClient;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;

import eut.nebulouscloud.iot_dpp.GroupIDExtractionParameters.GroupIDExpressionSource;
import eut.nebulouscloud.iot_dpp.monitoring.QueuesMonitoringMessage;
import eut.nebulouscloud.iot_dpp.pipeline.IoTPipelineConfigurator;
import eut.nebulouscloud.iot_dpp.pipeline.IoTPipelineStepConfiguration;

/**
 * Test the correct functionaltiy of QueuesMonitoringPlugin
 */
class IoTPipelinePluginTest {
	ObjectMapper om = new ObjectMapper();
	static Logger LOGGER = LoggerFactory.getLogger(IoTPipelinePluginTest.class);
	static int QueuesMonitoringProcesQueryIntervalSeconds = 3;

	/**
	 * Creates a local ActiveMQ server listening at localhost:61616. The server
	 * accepts requests from any user. Configures the MessageMonitoringPluging and
	 * sets it to store generated events in the provided events list
	 * 
	 * @param events A list that will contain all the events generated by the
	 *               MessageMonitoringPluging
	 * @return the created MessageMonitoringPluging instance.
	 * @throws Exception
	 */
	private EmbeddedActiveMQ createActiveMQBroker(String nodeName, int port,
			Map<String, IoTPipelineStepConfiguration> pipelineSteps, List<Integer> otherServers) throws Exception {
		Configuration config = new ConfigurationImpl();
		config.setName(nodeName);
		String foldersRoot = "data/" + new Date().getTime() + "/data_" + port;
		config.setBindingsDirectory(foldersRoot + "/bindings");
		config.setJournalDirectory(foldersRoot + "/journal");
		config.setJournalRetentionDirectory(foldersRoot + "/journalRetention");
		config.setLargeMessagesDirectory(foldersRoot + "/lm");
		config.setNodeManagerLockDirectory(foldersRoot + "/nodeManagerLock");
		config.setPagingDirectory(foldersRoot + "/paging");
		config.addConnectorConfiguration("serverAt" + port + "Connector", "tcp://localhost:" + port);
		config.addAcceptorConfiguration("netty", "tcp://localhost:" + port);
		
		

		ClusterConnectionConfiguration cluster = new ClusterConnectionConfiguration();
		cluster.setAddress("");
		cluster.setConnectorName("serverAt" + port + "Connector");
		cluster.setName("my-cluster");
		cluster.setAllowDirectConnectionsOnly(false);
		cluster.setMessageLoadBalancingType(MessageLoadBalancingType.ON_DEMAND);
		cluster.setRetryInterval(100);
		config.setClusterConfigurations(List.of(cluster));
		if (otherServers != null) {
			for (Integer otherPort : otherServers) {
				cluster.setStaticConnectors(List.of("serverAt" + otherPort + "Connector"));
				config.addConnectorConfiguration("serverAt" + otherPort + "Connector", "tcp://localhost:" + otherPort);
			}
		}

		IoTPipelineConfigurator configuratorPlugin = new IoTPipelineConfigurator();
		configuratorPlugin.init(
				Map.of(IoTPipelineConfigurator.IOT_DPP_PIPELINE_STEPS_ENV_VAR, om.writeValueAsString(pipelineSteps),
						"local_activemq_url", "tcp://localhost:"+port,
						"local_activemq_user","artemis","local_activemq_password","artemis"
						
						
						
						
						));
		config.getBrokerPlugins().add(configuratorPlugin);
		
		EmbeddedActiveMQ server = new EmbeddedActiveMQ();
		server.setSecurityManager(new ActiveMQSecurityManager() {
			@Override
			public boolean validateUserAndRole(String user, String password, Set<Role> roles, CheckType checkType) {
				return true;
			}

			@Override
			public boolean validateUser(String user, String password) {
				return true;
			}
		});
		server.setConfiguration(config);
		server.start();
	
		Thread.sleep(1000*10);
		return server;
	}

	private EmbeddedActiveMQ createActiveMQBroker(String nodeName,
			Map<String, IoTPipelineStepConfiguration> pipelineSteps, int port) throws Exception {
		return createActiveMQBroker(nodeName, port, pipelineSteps, null);
	}

	private IMqttClient buildWorker(String brokerURL, String clientId, String inputTopic, String outputTopic,
			List<MessageReceptionRecord> messages) throws Exception {
		IMqttClient consumer = new MqttClient("tcp://" + brokerURL, clientId);
		consumer.setCallback(new MqttCallback() {

			@Override
			public void messageArrived(String topic, MqttMessage message) throws Exception {
				messages.add(new MessageReceptionRecord(consumer.getClientId(), topic,
						om.readValue(new String(message.getPayload()), TestMessage.class)));
				LOGGER.info("Worker "+consumer.getClientId()+" messageArrived");
				consumer.publish(outputTopic, message.getPayload(), 2, false);

			}

			@Override
			public void deliveryComplete(IMqttDeliveryToken token) {
			}

			@Override
			public void connectionLost(Throwable cause) {
				LOGGER.error("Worker "+consumer.getClientId() + " connectionLost", cause);
			}
		});
		MqttConnectOptions opts = new MqttConnectOptions();
		// opts.setUserName("artemis");
		// opts.setPassword("artemis".toCharArray());
		opts.setCleanSession(false);

		consumer.connect(opts);
		consumer.subscribe(inputTopic, 2);
		return consumer;
	}

	/**
	 * Test message monitoring plugin when MQTT clients are interacting with the
	 * ActiveMQ broker.
	 * 
	 * @throws Exception
	 */
	@Test
	void MQTTTestSingleNode() throws Exception {

		/**
		 * Create a local ActiveMQ server
		 */
		// String testTopic = ;
		int brokerPort = 6169;
		String brokerURL = "localhost:" + brokerPort;
		// localhost:1883"
		Map<String, IoTPipelineStepConfiguration> map = new HashMap<String, IoTPipelineStepConfiguration>();
		map.put("stepB", new IoTPipelineStepConfiguration("src",
				new GroupIDExtractionParameters(GroupIDExpressionSource.BODY_JSON, "fieldB")));
		map.put("stepC", new IoTPipelineStepConfiguration("stepB",
				new GroupIDExtractionParameters(GroupIDExpressionSource.BODY_JSON, "fieldC")));
		EmbeddedActiveMQ broker = null;
		try {

			
			//[INFO ] 2024-07-22 13:23:44.496 [Thread-0] IoTPipelineConfigurator - callCreateDivert divertName: iotdpp.src.input, address: iotdpp.src.output 
			//forwardingAddress: iotdpp.src.input.src exclusive: false filterString: null, transformerClassName: eut.nebulouscloud.iot_dpp.GroupIDAnnotationDivertTransfomer,
			//transformerPropertiesAsJSON: {"NEB_IOT_DPP_GROUPID_EXTRACTION_CONFIG_MAP":{"iotdpp.src.input.src":{"source":"BODY_JSON","expression":"fieldA"}}}, routingType: STRIP

			broker = createActiveMQBroker("test-server", map, brokerPort);
			List<MessageReceptionRecord> messages = Collections
					.synchronizedList(new LinkedList<MessageReceptionRecord>());
			IMqttClient stepBWorker1 = buildWorker(brokerURL, "step_B_worker_1", "$share/all/iotdpp.stepB.input.src","iotdpp.stepB.output",
					messages);
			IMqttClient stepBWorker2 = buildWorker(brokerURL, "step_B_worker_2", "$share/all/iotdpp.stepB.input.src","iotdpp.stepB.output",
					messages);
			IMqttClient stepCWorker1 = buildWorker(brokerURL, "step_C_worker_1", "$share/all/iotdpp.stepC.input.stepB","iotdpp.stepC.output",
					messages);
			IMqttClient stepCWorker2 = buildWorker(brokerURL, "step_C_worker_2", "$share/all/iotdpp.stepC.input.stepB","iotdpp.stepC.output",
					messages);

			/**
			 * Publish a message to the topic
			 */
			IMqttClient publisher = new MqttClient("tcp://" + brokerURL, "publisher");
			MqttConnectOptions connOpts = new MqttConnectOptions();
			connOpts.setUserName("artemis");
			connOpts.setPassword("artemis".toCharArray());

			publisher.connect(connOpts);
			
			

			int messagesCount = 30;
			Random rand = new Random();
			for (int i = 0; i < messagesCount; i++) {
				// MqttMessage message = new MqttMessage("hola from publisher".getBytes());
				// message.setQos(2);
				// publisherBrokerA.publish(testTopic, message);

				TestMessage payload = new TestMessage(i, rand.nextInt(1, 10), rand.nextInt(1, 10));
				MqttMessage message = new MqttMessage(om.writeValueAsString(payload).getBytes());
				message.setQos(2);
				publisher.publish("iotdpp.src.output", message);
				//publisher.publish("iotdpp.stepA.input.src", message);
				//
				Thread.sleep(1);
			}
			Thread.sleep(1000);
			for (String step : List.of("B", "C")) {
				List<MessageReceptionRecord> stepInputMessages = messages.stream()
						.filter(m -> m.consumerId.startsWith("step_" + step)).toList();
				assertEquals(messagesCount, stepInputMessages.size());

				List<Integer> fieldWithGroupingKeyValues = stepInputMessages.stream().map(m -> m.payload.getField(step))
						.distinct().toList();
				for (Integer groupingKeyValue : fieldWithGroupingKeyValues) {
					assertEquals(1, stepInputMessages.stream().filter(m -> m.payload.getField(step) == groupingKeyValue)
							.map(m -> m.consumerId).distinct().count());
				}

			}

		} finally {
			try {
				broker.stop();
			} catch (Exception e) {
			}
		}

	}

}