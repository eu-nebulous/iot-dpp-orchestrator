package eut.nebulouscloud.iot_dpp.pipeline;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.jms.Message;
import javax.jms.Queue;
import javax.jms.QueueConnection;
import javax.jms.QueueRequestor;
import javax.jms.QueueSession;
import javax.jms.Session;
import javax.naming.InitialContext;

import org.apache.activemq.artemis.api.config.ActiveMQDefaultConfiguration;
import org.apache.activemq.artemis.api.core.management.ActiveMQServerControl;
import org.apache.activemq.artemis.api.core.management.ResourceNames;
import org.apache.activemq.artemis.api.jms.management.JMSManagementHelper;
import org.apache.activemq.artemis.core.config.DivertConfiguration;
import org.apache.activemq.artemis.core.config.TransformerConfiguration;
import org.apache.activemq.artemis.core.management.impl.ActiveMQServerControlImpl;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.ComponentConfigurationRoutingType;
import org.apache.activemq.artemis.core.server.plugin.ActiveMQServerBasePlugin;
import org.apache.activemq.artemis.core.server.plugin.ActiveMQServerMessagePlugin;
import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import eut.nebulouscloud.iot_dpp.GroupIDAnnotationDivertTransfomer;
import eut.nebulouscloud.iot_dpp.GroupIDExtractionParameters;
import eut.nebulouscloud.iot_dpp.MessageGroupIDAnnotationPlugin;
import eut.nebulouscloud.iot_dpp.MessageGroupIDAnotator;
import eut.nebulouscloud.iot_dpp.monitoring.QueuesMonitoringPlugin;

/**
 * This class is responsible for parsing the IoT pipeline definition JSON and
 * configure the local Artemis message broker to enact said pipeline
 */
public class IoTPipelineConfigurator implements ActiveMQServerBasePlugin {

	private final static String IOT_PIPELINE_DIVERT_PREFIX = "iotdpp.";
	private final static String IOT_DPP_TOPICS_PREFIX = "iotdpp.";

	Logger LOGGER = LoggerFactory.getLogger(IoTPipelineConfigurator.class);
	QueueConnection connection = null;
	ActiveMQConnectionFactory connectionFactory;
	InitialContext initialContext = null;
	QueueSession session = null;
	Queue managementQueue;
	QueueRequestor requestor;
	String activemqURL;
	String activemqUser;
	String activemqPassword;
	public final static String IOT_DPP_PIPELINE_STEPS_ENV_VAR = "IOT_DPP_PIPELINE_STEPS";
	Map<String, IoTPipelineStepConfiguration> pipelineSteps;

	private class IoTPipelineConfiguratorProcess implements Runnable {
		private void connect(String activemqURL, String activemqUser, String activemqPassword) {
			System.setProperty("java.naming.factory.initial",
					"org.apache.activemq.artemis.jndi.ActiveMQInitialContextFactory");
			int maxTryCount = 3;
			for (int i = 0; i < maxTryCount; i++) {
				try {
					initialContext = new InitialContext();
					LOGGER.info("Connecting to {}", activemqURL);
					connectionFactory = new ActiveMQConnectionFactory(activemqURL);
					connection = connectionFactory.createQueueConnection(activemqUser, activemqPassword);
					session = connection.createQueueSession(false, Session.AUTO_ACKNOWLEDGE);
					managementQueue = session.createQueue("activemq.management");
					connection.start();
					requestor = new QueueRequestor(session, managementQueue);
					return;
				} catch (Exception ex) {
					LOGGER.error("", ex);
					LOGGER.error("Wait 5 seconds");
					try {
						Thread.sleep(2 * 1000);
					} catch (InterruptedException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}
			}
			return;

		}

		private boolean removeDivert(String divertName) {
			try {

				LOGGER.info(String.format("callRemoveDivert divertName: %s", divertName));
				Message m = session.createMessage();
				JMSManagementHelper.putOperationInvocation(m, ResourceNames.BROKER, "destroyDivert", divertName);
				Message reply = requestor.request(m);
				boolean success = JMSManagementHelper.hasOperationSucceeded(reply);
				if (success) {
					Object result = JMSManagementHelper.getResult(reply);
					return true;
				} else {
					LOGGER.error("Failed to list diverts");
					return false;
				}

			} catch (Exception ex) {
				LOGGER.error("", ex);
				return false;
			}
		}

		private boolean createDivert(String divertName, String address, String forwardingAddress, boolean exclusive,
				String filterString, String transformerClassName, String transformerPropertiesAsJSON,
				String routingType) {
			try {

				LOGGER.info(String.format(
						"callCreateDivert divertName: %s, address: %s forwardingAddress: %s exclusive: %s filterString: %s, transformerClassName: %s, transformerPropertiesAsJSON: %s, routingType: %s",
						divertName, address, forwardingAddress, "" + exclusive, filterString, transformerClassName,
						transformerPropertiesAsJSON, routingType));
				Message m = session.createMessage();				
				//	createDivert​(String name, String routingName, String address, String forwardingAddress, boolean exclusive, String filterString, 
				//String transformerClassName, String transformerPropertiesAsJSON, String routingType)
				JMSManagementHelper.putOperationInvocation(m, ResourceNames.BROKER, "createDivert", divertName,divertName, address,
						forwardingAddress, exclusive, filterString, transformerClassName, transformerPropertiesAsJSON,routingType
						);
				
				Message reply = requestor.request(m);
				boolean success = JMSManagementHelper.hasOperationSucceeded(reply);
				if (success) {
					Object result = JMSManagementHelper.getResult(reply);
					return true;
				} else {
					String error = JMSManagementHelper.getResult(reply).toString();
					LOGGER.error("Failed to list diverts: "+error);
					return false;
				}

			} catch (Exception ex) {
				LOGGER.error("", ex);
				return false;
			}
		}

		private List<String> listDivertNames() {
			// https://activemq.apache.org/components/artemis/documentation/javadocs/javadoc-latest/org/apache/activemq/artemis/api/core/management/ActiveMQServerControl.html
			try {
				Message m = session.createMessage();
				JMSManagementHelper.putOperationInvocation(m, ResourceNames.BROKER, "listDivertNames");
				Message reply = requestor.request(m);
				List<String> ret = new LinkedList<String>();
				boolean success = JMSManagementHelper.hasOperationSucceeded(reply);
				if (success) {
					Object[] addresses = (Object[]) JMSManagementHelper.getResult(reply);

					for (Object address : addresses) {
						String addressName = (String) address;
						ret.add(addressName);

					}
				} else {
					LOGGER.error("Failed to list diverts");
				}
				return ret;

			} catch (Exception ex) {
				LOGGER.error("", ex);
			}
			return null;

		}

		private void createDivert(String stepName, IoTPipelineStepConfiguration inputConfig) throws JsonProcessingException {

			String divertName = IOT_PIPELINE_DIVERT_PREFIX + stepName + ".input";
			String divertAddress = IOT_DPP_TOPICS_PREFIX + inputConfig.inputStream + ".output";
			String forwardingAddress = IOT_DPP_TOPICS_PREFIX + stepName + ".input." + inputConfig.inputStream;

			ObjectMapper om = new ObjectMapper();
			String divertProperties = om
					.writeValueAsString(Map.of(MessageGroupIDAnotator.GROUP_ID_EXTRACTION_CONFIG_MAP_ENV_VAR,Map.of(forwardingAddress, inputConfig.groupingKeyAccessor)));
			
			createDivert(divertName, divertAddress, forwardingAddress, false, null,
					GroupIDAnnotationDivertTransfomer.class.getName(), divertProperties,
					ComponentConfigurationRoutingType.MULTICAST.toString());
			
		}

		private void initDiverts() {
			LOGGER.info("initDiverts");
			List<String> diverts = listDivertNames();
			diverts = diverts != null ? diverts : List.of();
			LOGGER.info("Existing diverts: " + String.join(", ", diverts));
			diverts = diverts.stream().filter(d -> d.startsWith(IOT_PIPELINE_DIVERT_PREFIX)).toList();
			LOGGER.info("NebulOuS diverts: " + String.join(", ", diverts));
			if (!diverts.isEmpty()) {
				LOGGER.info("Remove exising NebulOuS diverts");
				for (String divert : diverts) {
					removeDivert(divert);
				}
			}
			LOGGER.info("Create diverts");
			for (String step : pipelineSteps.keySet()) {
				LOGGER.info("Init divert for step " + step);
				try {
					createDivert(step, pipelineSteps.get(step));
				} catch (JsonProcessingException e) {
					LOGGER.error("",e);
				}
			}
			
			diverts = listDivertNames();
			diverts = diverts != null ? diverts : List.of();
			LOGGER.info("Existing diverts: " + String.join(", ", diverts));
			diverts = diverts.stream().filter(d -> d.startsWith(IOT_PIPELINE_DIVERT_PREFIX)).toList();
			LOGGER.info("NebulOuS diverts: " + String.join(", ", diverts));

		}

		@Override
		public void run() {
			LOGGER.info("IoTPipelineConfigurator registered");
			connect(activemqURL, activemqUser, activemqPassword);
			initDiverts();

		}

	}

	private Map<String, IoTPipelineStepConfiguration> parsePipelineSteps(String serializedPipelineSteps) {
		ObjectMapper om = new ObjectMapper();
		try {

			TypeReference<Map<String, IoTPipelineStepConfiguration>> typeReference = new TypeReference<Map<String, IoTPipelineStepConfiguration>>() {
			};

			return om.readValue(serializedPipelineSteps.getBytes(), typeReference);

		} catch (Exception ex) {
			LOGGER.error(String.format("Problem reading IoTPipelineStepConfiguration from JSON '%s'",
					serializedPipelineSteps), ex);
			return new HashMap<String, IoTPipelineStepConfiguration>();
		}
	}

	@Override
	public void init(Map<String, String> properties) {

		activemqURL = properties.getOrDefault("local_activemq_url", "tcp://localhost:61616");
		activemqUser = Optional.ofNullable(properties.getOrDefault("local_activemq_user", null))
				.orElseThrow(() -> new IllegalStateException("local_activemq_user parameter is not defined"));
		activemqPassword = Optional.ofNullable(properties.getOrDefault("local_activemq_user", null))
				.orElseThrow(() -> new IllegalStateException("local_activemq_password parameter is not defined"));

		String pipelineStepsString = Optional.ofNullable(properties.getOrDefault(IOT_DPP_PIPELINE_STEPS_ENV_VAR, null))
				.orElseThrow(
						() -> new IllegalStateException(IOT_DPP_PIPELINE_STEPS_ENV_VAR + " parameter is not defined"));

		pipelineSteps = parsePipelineSteps(pipelineStepsString);

	}

	@Override
	public void registered(ActiveMQServer server) {

		new Thread(new IoTPipelineConfiguratorProcess()).start();
		// As seen in QueuesMonitoringProcess

		// Message m = session.createMessage();
		// createDivert​(String name, String routingName, String address, String
		// forwardingAddress, boolean exclusive, String filterString, String
		// transformerClassName)

		// JMSManagementHelper.putOperationInvocation(m, ResourceNames.BROKER,
		// "getQueueNames");

		/*
		 * DivertConfiguration divertAtoC = new DivertConfiguration();
		 * divertAtoC.setName("divertAtoC"); divertAtoC.setAddress("neb.step_A_output");
		 * divertAtoC.setExclusive(false);
		 * divertAtoC.setForwardingAddress("neb.step_C_input");
		 * divertAtoC.setTransformerConfiguration(divertTransformerConfig);
		 * 
		 * server.setProperties(null);
		 */
	}

}
