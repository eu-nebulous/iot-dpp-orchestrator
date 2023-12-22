package eut.nebulouscloud.iot_dpp.monitoring;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import eut.nebulouscloud.iot_dpp.monitoring.events.MessageLifecycleEvent;

/**
 * Extension of the abstract class MessageLifecycleMonitoringPlugin that handles
 * sending the generated events to the EMS
 */
public class EMSMessageLifecycleMonitoringPlugin extends MessageLifecycleMonitoringPlugin {

	Logger LOGGER = LoggerFactory.getLogger(EMSMessageLifecycleMonitoringPlugin.class);
	protected ObjectMapper om = new ObjectMapper();
	private static final String EMS_METRICS_TOPIC = "eu.nebulouscloud.monitoring.realtime.iot.messaging_events";

	@Override
	protected void notifyEvent(MessageLifecycleEvent event) {
		String str;
		try {
			str = om.writeValueAsString(event);
			EventManagementSystemPublisher.send(EMS_METRICS_TOPIC, str);
		} catch (Exception e) {
			LOGGER.error("Unable to send event to the EMS", e);
		}

	}

}
