package eut.nebulouscloud.iot_dpp.monitoring;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import eut.nebulouscloud.iot_dpp.monitoring.QueuesMonitoringPlugin.QueuesMonitoringPluginConsumer;
import eut.nebulouscloud.iot_dpp.monitoring.events.MessageAcknowledgedEvent;
import eut.nebulouscloud.iot_dpp.monitoring.events.MessageDeliveredEvent;
import eut.nebulouscloud.iot_dpp.monitoring.events.MessageLifecycleEvent;
import eut.nebulouscloud.iot_dpp.monitoring.events.MessagePublishedEvent;
/**
 * Extension of the abstract class MessageLifecycleMonitoringPlugin that handles
 * sending the generated events to the EMS
 */
public class EMSMessageLifecycleMonitoringPlugin extends MessageLifecycleMonitoringPlugin {

	Logger LOGGER = LoggerFactory.getLogger(EMSMessageLifecycleMonitoringPlugin.class);
	
	EventManagementSystemPublisher publisher;
	
	@Override
	public void init(Map<String, String> properties) {
		LOGGER.info("EMSMessageLifecycleMonitoringPlugin init");
		String emsURL = Optional.ofNullable(properties.getOrDefault("ems_url", null))
					.orElseThrow(() -> new IllegalStateException("ems_url parameter is not defined")); 
		String emsUser = Optional.ofNullable(properties.getOrDefault("ems_user", null))
				.orElseThrow(() -> new IllegalStateException("ems_user parameter is not defined")); 
		String emsPassword = Optional.ofNullable(properties.getOrDefault("ems_password", null))
				.orElseThrow(() -> new IllegalStateException("ems_password parameter is not defined"));
		
		String reportingTopicPrefix = properties.getOrDefault("reporting_topic_prefix", "/topic/");
		
		publisher = new EventManagementSystemPublisher(emsURL, emsUser, emsPassword, reportingTopicPrefix);		
		LOGGER.info("Init EMSMessageLifecycleMonitoringPlugin with parameters:");
		LOGGER.info("reportingTopicPrefix"+reportingTopicPrefix);
		LOGGER.info("emsURL: "+emsURL);
		LOGGER.info("emsUser: "+emsUser);
		
	}

   
	@Override
	protected void notifyEvent(MessageLifecycleEvent event) {
		
		try {
			String messageAddress = event.messageAddress.replaceAll("\\.", "_");
			String eventType ="";
			Map<String,Double> metrics = new HashMap();
			if(event instanceof MessagePublishedEvent)
			{
				MessagePublishedEvent e = (MessagePublishedEvent)event;
				eventType = "MessagePublished";
				metrics.put("Size",(double) e.messageSize);				
			}
			
			if(event instanceof MessageDeliveredEvent)
			{
				MessageDeliveredEvent e = (MessageDeliveredEvent)event;
				eventType = "MessageDelivered";				
				metrics.put("Latency",(double) e.timestamp- e.publishTimestamp);
				metrics.put("Size",(double) e.messageSize);				
			}
			
			if(event instanceof MessageAcknowledgedEvent)
			{
				MessageAcknowledgedEvent e = (MessageAcknowledgedEvent)event;
				eventType = "MessageAcknowledged";				
				metrics.put("Latency",(double) e.timestamp- e.publishTimestamp);
				metrics.put("Size",(double) e.messageSize);
			}
			
			for(String metric : metrics.keySet())
			{
				this.publisher._send(String.join("_",messageAddress,eventType,metric), metrics.get(metric), event.timestamp);
			}
			
		} catch (Exception e) {
			LOGGER.error("Unable to send event to the EMS", e);
		}

	}

}
