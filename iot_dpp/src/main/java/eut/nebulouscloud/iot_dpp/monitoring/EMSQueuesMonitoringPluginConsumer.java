package eut.nebulouscloud.iot_dpp.monitoring;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import eut.nebulouscloud.iot_dpp.monitoring.QueuesMonitoringPlugin.QueuesMonitoringPluginConsumer;
import eut.nebulouscloud.iot_dpp.monitoring.events.MessageAcknowledgedEvent;
import eut.nebulouscloud.iot_dpp.monitoring.events.MessageDeliveredEvent;
import eut.nebulouscloud.iot_dpp.monitoring.events.MessagePublishedEvent;

public class EMSQueuesMonitoringPluginConsumer implements QueuesMonitoringPluginConsumer{
	Logger LOGGER = LoggerFactory.getLogger(EMSQueuesMonitoringPluginConsumer.class);
	
	EventManagementSystemPublisher publisher;
	public EMSQueuesMonitoringPluginConsumer(EventManagementSystemPublisher publisher)
	{
		this.publisher = publisher;
	}
	
	private void consume(QueuesMonitoringMessage message)
	{
		try {
			String messageAddress = message.queue.replaceAll("\\.", "_");
			Map<String,Double> metrics = new HashMap();		
			metrics.put("ConsumersCount", (double)message.consumersCount);
			metrics.put("GroupCount", (double)message.groupCount);
			metrics.put("MaxMessageAge", (double)message.maxMessageAge);
			metrics.put("MessageCount", (double)message.messageCount);
			long timestamp = new Date().getTime();
			for(String metric : metrics.keySet())
			{
				publisher._send(String.join("_",messageAddress,metric), metrics.get(metric), timestamp);
			}
			
		} catch (Exception e) {
			LOGGER.error("Unable to send event to the EMS", e);
		}
	}

	@Override
	public void consume(List<QueuesMonitoringMessage> messages) {
		for(QueuesMonitoringMessage message:messages)
		{
			consume(message);
		}
		
		

		
	}

}
