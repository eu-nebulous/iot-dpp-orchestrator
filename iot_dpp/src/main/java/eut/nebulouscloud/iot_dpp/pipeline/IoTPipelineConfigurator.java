package eut.nebulouscloud.iot_dpp.pipeline;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.config.DivertConfiguration;
import org.apache.activemq.artemis.core.config.TransformerConfiguration;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.ComponentConfigurationRoutingType;
import org.apache.activemq.artemis.core.server.plugin.ActiveMQServerPlugin;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
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
	public final static String IOT_DPP_PIPELINE_STEPS_ENV_VAR = "IOT_DPP_PIPELINE_STEPS";
	Map<String, IoTPipelineStepConfiguration> pipelineSteps;

	private class IoTPipelineConfiguratorProcess implements Runnable {
		ActiveMQServer server;

		public IoTPipelineConfiguratorProcess(ActiveMQServer server) {
			this.server = server;
		}

		private boolean removeDivert(String divertName) {

			LOGGER.info(String.format("callRemoveDivert divertName: %s", divertName));
			try {
				server.destroyDivert(SimpleString.of(divertName));
				return true;
			} catch (Exception ex) {
				LOGGER.error("", ex);
				return false;
			}

		}

		private List<String> listDivertNames() {
			try {
				return Arrays.asList(server.getActiveMQServerControl().listDivertNames());

			} catch (Exception ex) {
				LOGGER.error("", ex);
			}
			return null;
		}

		private boolean configAddressSpace() {
			try {
				String addressPattern = IOT_DPP_TOPICS_PREFIX + "#";
				// String addressPattern = "iotdpp#";
				AddressSettings settings = new AddressSettings();
				// https://activemq.apache.org/components/artemis/documentation/latest/address-settings.html#address-settings
				settings.setAutoCreateAddresses(false);
				settings.setAutoCreateQueues(false); // Change this to false for strict mode.
				settings.setAutoDeleteAddresses(false);
				settings.setAutoDeleteQueues(false);
				settings.setAutoDeleteCreatedQueues(false);
				settings.setDefaultPurgeOnNoConsumers(false);
				server.getAddressSettingsRepository().addMatch(addressPattern, settings);
				return true;

			} catch (Exception ex) {
				LOGGER.error("failed to addAddressSettings", ex);
				return false;
			}

		}

		private void deleteAddress(String address) {
			try {
				server.getActiveMQServerControl().deleteAddress(address);
			} catch (Exception ex) {
				LOGGER.error("failed to deleteAddress", ex);
			}

		}

		private void createAddress(String address) {
			try {
				deleteAddress(address);
				server.getActiveMQServerControl().createAddress(address, "MULTICAST");

			} catch (Exception ex) {
				LOGGER.error("failed to createAddress", ex);
			}

		}

		private void createDivert(String stepName, IoTPipelineStepConfiguration inputConfig)
				throws JsonProcessingException {

			try {
				String divertName = IOT_PIPELINE_DIVERT_PREFIX + stepName + ".input";
				String divertAddress = IOT_DPP_TOPICS_PREFIX + inputConfig.inputStream + ".output";
				String forwardingAddress = IOT_DPP_TOPICS_PREFIX + stepName + ".input." + inputConfig.inputStream;

				ObjectMapper om = new ObjectMapper();
				DivertConfiguration conf = new DivertConfiguration();
				conf.setName(divertName);
				conf.setAddress(divertAddress);
				conf.setForwardingAddress(forwardingAddress);
				conf.setExclusive(false);
				conf.setFilterString(null);
				TransformerConfiguration tc = new TransformerConfiguration(
						GroupIDAnnotationDivertTransfomer.class.getName());
				tc.setProperties(Map.of(MessageGroupIDAnotator.GROUP_ID_EXTRACTION_CONFIG_MAP_ENV_VAR,
						om.writeValueAsString(Map.of(forwardingAddress, inputConfig.groupingKeyAccessor))));
				conf.setRoutingType(ComponentConfigurationRoutingType.MULTICAST);
				conf.setTransformerConfiguration(tc);
				server.deployDivert(conf);
			} catch (Exception ex) {
				LOGGER.error("Failed to create divert", ex);
			}

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

		private void createAddresses() {

			LOGGER.info("createAddresses");

			Set<String> addresses = new HashSet<String>();

			for (String step : pipelineSteps.keySet()) {
				addresses.add(IOT_DPP_TOPICS_PREFIX + pipelineSteps.get(step).inputStream + ".output");
				addresses.add(IOT_DPP_TOPICS_PREFIX + step + ".input." + pipelineSteps.get(step).inputStream);
				addresses.add(IOT_DPP_TOPICS_PREFIX + step + ".output");
			}

			for (String address : addresses) {
				try {
					createAddress(address);
				} catch (Exception ex) {
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

		private boolean deleteQueue(String queueName) {
			try {
				LOGGER.info(String.format("Deleting queue. QueueName: %s", queueName));
				server.destroyQueue(SimpleString.of(queueName));
				return true;
			} catch (Exception ex) {
				LOGGER.warn("Failed to deleteQueue: " + queueName + " : " + ex);
				return false;
			}
		}

		private boolean createInputQueue(String stepName, IoTPipelineStepConfiguration inputConfig) {
			try {
				String addressName = IOT_DPP_TOPICS_PREFIX + stepName + ".input." + inputConfig.inputStream;
				String queueName = "all." + addressName;

				deleteQueue(queueName);

				LOGGER.info(String.format("Creating queue. Address: %s QueueName: %s", addressName, queueName));
				QueueConfiguration config = new QueueConfiguration();
				config.setAddress(addressName);
				config.setConfigurationManaged(true);
				config.setName(queueName);
				config.setPurgeOnNoConsumers(false);
				config.setDurable(true);
				String filter = "NOT ((AMQAddress = 'activemq.management') OR (AMQAddress = 'activemq.notifications'))";
				config.setFilterString(filter);
				server.createQueue(config);
				return true;

			} catch (Exception ex) {
				LOGGER.error("Failed to createInputQueue", ex);
				return false;
			}
		}

		private boolean createOutputQueue(String stepName) {
			try {
				String addressName = IOT_DPP_TOPICS_PREFIX + stepName + ".output";
				String queueName = "all." + addressName;
				deleteQueue(queueName);
				LOGGER.info(String.format("Creating queue. Address: %s QueueName: %s", addressName, queueName));
				QueueConfiguration config = new QueueConfiguration();
				config.setAddress(addressName);
				config.setName(queueName);
				config.setConfigurationManaged(true);
				config.setPurgeOnNoConsumers(false);
				config.setDurable(true);
				String filter = "NOT ((AMQAddress = 'activemq.management') OR (AMQAddress = 'activemq.notifications'))";
				config.setFilterString(filter);

				server.createQueue(config);
				return true;
			} catch (Exception ex) {
				LOGGER.error("Failed to createOutputQueue", ex);
				return false;
			}
		}

		@Override
		public void run() {

			while (server != null && !server.isActive()) {
				LOGGER.info("Wait for server activation");

				try {
					Thread.sleep(1000);
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}

			LOGGER.info("IoTPipelineConfigurator registered");

			configAddressSpace();
			createAddresses();
			createQueues();
			initDiverts();

		}

	}

	private Map<String, IoTPipelineStepConfiguration> parsePipelineSteps(String serializedPipelineSteps) {
		ObjectMapper om = new ObjectMapper();
		try {
			String jsonContent = serializedPipelineSteps;
			
			// If the input ends with .json, treat it as a file path and read its contents
			if (serializedPipelineSteps.toLowerCase().endsWith(".json")) {
				LOGGER.info("Reading pipeline steps from JSON file: " + serializedPipelineSteps);
				try {
					jsonContent = new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(serializedPipelineSteps)));					
				} catch (Exception ex) {
					LOGGER.error("Failed to read JSON file: " + serializedPipelineSteps, ex);
					return new HashMap<String, IoTPipelineStepConfiguration>();
				}
			}

			TypeReference<Map<String, IoTPipelineStepConfiguration>> typeReference = new TypeReference<Map<String, IoTPipelineStepConfiguration>>() {
			};

			return om.readValue(jsonContent.getBytes(), typeReference);

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

		LOGGER.info("pipelineStepsString: " + pipelineStepsString);		
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
