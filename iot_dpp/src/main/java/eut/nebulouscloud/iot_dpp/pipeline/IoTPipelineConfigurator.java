package eut.nebulouscloud.iot_dpp.pipeline;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import javax.jms.Message;
import javax.jms.Queue;
import javax.jms.QueueConnection;
import javax.jms.QueueRequestor;
import javax.jms.QueueSession;
import javax.jms.Session;
import javax.naming.InitialContext;

import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.management.ResourceNames;
import org.apache.activemq.artemis.api.jms.management.JMSManagementHelper;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.ComponentConfigurationRoutingType;
import org.apache.activemq.artemis.core.server.plugin.ActiveMQServerPlugin;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import eut.nebulouscloud.iot_dpp.GroupIDAnnotationDivertTransfomer;
import eut.nebulouscloud.iot_dpp.MessageGroupIDAnotator;

/**
 * This class is responsible for parsing the IoT pipeline definition JSON and
 * configure the local Artemis message broker to enact said pipeline
 */
public class IoTPipelineConfigurator implements ActiveMQServerPlugin {

	private final static String IOT_PIPELINE_DIVERT_PREFIX = "iotdpp.";
	public final static String IOT_DPP_TOPICS_PREFIX = "iotdpp.";

	Logger LOGGER = LoggerFactory.getLogger(IoTPipelineConfigurator.class);
	QueueConnection connection = null;
	ActiveMQConnectionFactory connectionFactory;
	InitialContext initialContext = null;
	QueueSession session = null;
	Queue managementQueue;
	QueueRequestor requestor;	
	public final static String IOT_DPP_PIPELINE_STEPS_ENV_VAR = "IOT_DPP_PIPELINE_STEPS";
	Map<String, IoTPipelineStepConfiguration> pipelineSteps;

	private class IoTPipelineConfiguratorProcess implements Runnable {
		ActiveMQServer server;
		public IoTPipelineConfiguratorProcess(ActiveMQServer server) {
			this.server = server;
		}

		private void connect() {
			System.setProperty("java.naming.factory.initial",
					"org.apache.activemq.artemis.jndi.ActiveMQInitialContextFactory");
			int maxTryCount = 3;
			for (int i = 0; i < maxTryCount; i++) {
				try {
					initialContext = new InitialContext();
					LOGGER.info("Connecting to broker");
					connectionFactory = new ActiveMQConnectionFactory( "vm://0");
			        connection = connectionFactory.createQueueConnection();
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
				// createDivert​(String name, String routingName, String address, String
				// forwardingAddress, boolean exclusive, String filterString,
				// String transformerClassName, String transformerPropertiesAsJSON, String
				// routingType)
				JMSManagementHelper.putOperationInvocation(m, ResourceNames.BROKER, "createDivert", divertName,
						divertName, address, forwardingAddress, exclusive, filterString, transformerClassName,
						transformerPropertiesAsJSON, routingType);

				Message reply = requestor.request(m);
				boolean success = JMSManagementHelper.hasOperationSucceeded(reply);
				if (success) {
					Object result = JMSManagementHelper.getResult(reply);
					return true;
				} else {
					String error = JMSManagementHelper.getResult(reply).toString();
					LOGGER.error("Failed to list diverts: " + error);
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
		
		private void configAddressSpace()
		{
			try {
				String addressPattern = IOT_DPP_TOPICS_PREFIX+"#";
				//String addressPattern = "iotdpp#";
				AddressSettings settings = new AddressSettings();	
				//https://activemq.apache.org/components/artemis/documentation/latest/address-settings.html#address-settings
				settings.setAutoCreateAddresses(false);
				settings.setAutoCreateQueues(false); //Change this to false for strict mode.
				settings.setAutoDeleteAddresses(false);
				settings.setAutoDeleteQueues(false);
				settings.setAutoDeleteCreatedQueues(false);
				settings.setDefaultPurgeOnNoConsumers(false);				
				Message m = session.createMessage();
				JMSManagementHelper.putOperationInvocation(m, ResourceNames.BROKER, "addAddressSettings",addressPattern,settings.toJSON());
				Message reply = requestor.request(m);
				boolean success = JMSManagementHelper.hasOperationSucceeded(reply);
				if (success) {
					LOGGER.info("success addAddressSettings"+addressPattern);
				} else {
					String error = JMSManagementHelper.getResult(reply).toString();
					LOGGER.error("Failed to addAddressSettings "+addressPattern+" "+error );
				}

			} catch (Exception ex) {
				LOGGER.error("failed to addAddressSettings", ex);
			}
			
			
		}
		
		private void deleteAddress(String address)
		{
			try {
				Message m = session.createMessage();
				JMSManagementHelper.putOperationInvocation(m, ResourceNames.BROKER, "deleteAddress",address,true);
				Message reply = requestor.request(m);
				List<String> ret = new LinkedList<String>();
				boolean success = JMSManagementHelper.hasOperationSucceeded(reply);
				if (success) {
					LOGGER.info("deleted address "+address);
				} else {
					String error = JMSManagementHelper.getResult(reply).toString();
					LOGGER.error("Failed to deleteAddress "+address+" "+error );
				}

			} catch (Exception ex) {
				LOGGER.error("failed to deleteAddress", ex);
			}
			
		}
		
		private void createAddress(String address)
		{
			try {
				deleteAddress(address);
				Message m = session.createMessage();
				JMSManagementHelper.putOperationInvocation(m, ResourceNames.BROKER, "createAddress",address,"MULTICAST");
				Message reply = requestor.request(m);
				List<String> ret = new LinkedList<String>();
				boolean success = JMSManagementHelper.hasOperationSucceeded(reply);
				if (success) {
					LOGGER.info("created address "+address);
				} else {
					String error = JMSManagementHelper.getResult(reply).toString();
					LOGGER.error("Failed to createAddress "+address+" "+error );
				}

			} catch (Exception ex) {
				LOGGER.error("failed to createAddress", ex);
			}
			
		}

		private void createDivert(String stepName, IoTPipelineStepConfiguration inputConfig)
				throws JsonProcessingException {

			String divertName = IOT_PIPELINE_DIVERT_PREFIX + stepName + ".input";
			String divertAddress = IOT_DPP_TOPICS_PREFIX + inputConfig.inputStream + ".output";
			String forwardingAddress = IOT_DPP_TOPICS_PREFIX + stepName + ".input." + inputConfig.inputStream;

			ObjectMapper om = new ObjectMapper();
			String divertProperties = om
					.writeValueAsString(Map.of(MessageGroupIDAnotator.GROUP_ID_EXTRACTION_CONFIG_MAP_ENV_VAR,
							Map.of(forwardingAddress, inputConfig.groupingKeyAccessor)));		

			createDivert(divertName, divertAddress, forwardingAddress, false, null,
					GroupIDAnnotationDivertTransfomer.class.getName(), divertProperties,
					ComponentConfigurationRoutingType.MULTICAST.toString());

		}

		private void initDiverts() {
			LOGGER.info("initDiverts");
			List<String> diverts = listDivertNames();
			diverts = diverts != null ? diverts : List.of();
			LOGGER.info("Existing diverts: " + String.join(", ", diverts));
			diverts = diverts.stream().filter(d -> d.startsWith(IOT_PIPELINE_DIVERT_PREFIX))
					.collect(Collectors.toList());
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
					LOGGER.error("", e);
				}
			}

			diverts = listDivertNames();
			diverts = diverts != null ? diverts : List.of();
			LOGGER.info("Existing diverts: " + String.join(", ", diverts));
			diverts = diverts.stream().filter(d -> d.startsWith(IOT_PIPELINE_DIVERT_PREFIX))
					.collect(Collectors.toList());
			LOGGER.info("NebulOuS diverts: " + String.join(", ", diverts));

		}
		
		
		private void createAddresses()
		{
			
			LOGGER.info("createAddresses");
			
			Set<String> addresses = new HashSet<String>();
			
			for (String step : pipelineSteps.keySet()) {
					addresses.add(IOT_DPP_TOPICS_PREFIX + pipelineSteps.get(step).inputStream + ".output");
					addresses.add(IOT_DPP_TOPICS_PREFIX + step + ".input." + pipelineSteps.get(step).inputStream);
					addresses.add(IOT_DPP_TOPICS_PREFIX +step+ ".output");
			}
			
			for(String address : addresses)
			{
				try{
					createAddress(address);
				}catch(Exception ex)
				{
					LOGGER.error("Failed to create address ");
				}
			
			}
			
		
		}

		private void createQueues() {			
			LOGGER.info("createQueues");
			for (String step : pipelineSteps.keySet()) {
				LOGGER.info("create queues for step " + step);
				createOutputQueue(step);
				createInputQueue(step, pipelineSteps.get(step));

			}
		}
		
		
		private void deleteQueue(String queueName)
		{
			try {
			LOGGER.info(String.format("Deleting queue. QueueName: %s", queueName));
			Message m = session.createMessage();
			
			
			JMSManagementHelper.putOperationInvocation(m, ResourceNames.BROKER, "destroyQueue", queueName,true,true);
			Message reply = requestor.request(m);
			boolean success = JMSManagementHelper.hasOperationSucceeded(reply);
			if (success) {
				LOGGER.info("Deleted queue: "+queueName);
				
			} else {
				String error = JMSManagementHelper.getResult(reply).toString();
				LOGGER.warn("Failed to deleteQueue: "+queueName+" : " + error);
			}
			}catch(Exception ex)
			{
				LOGGER.warn("Failed to deleteQueue: "+queueName+" : " + ex);
			}
		}

		private boolean createInputQueue(String stepName, IoTPipelineStepConfiguration inputConfig) {
			try {
				String addressName = IOT_DPP_TOPICS_PREFIX + stepName + ".input." + inputConfig.inputStream;
				String queueName = "all." + addressName;
				
				deleteQueue(queueName);
				
				LOGGER.info(String.format("Creating queue. Address: %s QueueName: %s", addressName, queueName));
				Message m = session.createMessage();
				

				QueueConfiguration config = new QueueConfiguration();
				config.setAddress(addressName);
				config.setConfigurationManaged(true);
				config.setName(queueName);
				config.setPurgeOnNoConsumers(false);
				config.setDurable(true);
				String filter = "NOT ((AMQAddress = 'activemq.management') OR (AMQAddress = 'activemq.notifications'))";
				config.setFilterString(filter);
				
				JMSManagementHelper.putOperationInvocation(m, ResourceNames.BROKER, "createQueue", config.toJSON());
				
				Message reply = requestor.request(m);
				boolean success = JMSManagementHelper.hasOperationSucceeded(reply);
				if (success) {
					Object result = JMSManagementHelper.getResult(reply);
					return true;
				} else {
					String error = JMSManagementHelper.getResult(reply).toString();
					LOGGER.error("Failed to createInputQueue: " + error);
					return false;
				}

			} catch (Exception ex) {
				LOGGER.error("", ex);
				return false;
			}
		}
		
		private boolean createOutputQueue(String stepName) {
			try {
				String addressName = IOT_DPP_TOPICS_PREFIX + stepName + ".output";
				String queueName = "all." + addressName;
				
				deleteQueue(queueName);
				
				LOGGER.info(String.format("Creating queue. Address: %s QueueName: %s", addressName, queueName));
				Message m = session.createMessage();
				

				QueueConfiguration config = new QueueConfiguration();
				config.setAddress(addressName);
				config.setName(queueName);
				config.setConfigurationManaged(true);
				config.setPurgeOnNoConsumers(false);
				config.setDurable(true);
				String filter = "NOT ((AMQAddress = 'activemq.management') OR (AMQAddress = 'activemq.notifications'))";
				config.setFilterString(filter);
				
				JMSManagementHelper.putOperationInvocation(m, ResourceNames.BROKER, "createQueue", config.toJSON());
				
				Message reply = requestor.request(m);
				boolean success = JMSManagementHelper.hasOperationSucceeded(reply);
				if (success) {
					Object result = JMSManagementHelper.getResult(reply);
					return true;
				} else {
					String error = JMSManagementHelper.getResult(reply).toString();
					LOGGER.error("Failed to createInputQueue: " + error);
					return false;
				}

			} catch (Exception ex) {
				LOGGER.error("", ex);
				return false;
			}
		}

		@Override
		public void run() {
			
			while(server!=null && !server.isActive())
			{
				LOGGER.info("Wait for server activation");

				try {
					Thread.sleep(1000);
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
			
			
			LOGGER.info("IoTPipelineConfigurator registered");
			connect();
			configAddressSpace();
			createAddresses();
			createQueues();
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
		LOGGER.info("IoTPipelineConfigurator init");
		String pipelineStepsString = Optional.ofNullable(properties.getOrDefault(IOT_DPP_PIPELINE_STEPS_ENV_VAR, null))
				.orElseThrow(
						() -> new IllegalStateException(IOT_DPP_PIPELINE_STEPS_ENV_VAR + " parameter is not defined"));
		
		pipelineSteps = parsePipelineSteps(pipelineStepsString);
		
		try {
			LOGGER.info(new ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(pipelineSteps));
		} catch (JsonProcessingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}

	@Override
	public void registered(ActiveMQServer server) {
		LOGGER.info("IoTPipelineConfigurator registered");
		new Thread(new IoTPipelineConfiguratorProcess(server)).start();		
	}

}
