package eut.nebulouscloud.iot_dpp.monitoring;

import java.util.Map;

import javax.jms.MessageProducer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;

import net.ser1.stomp.Client;
import net.ser1.stomp.Listener;

public class EventManagementSystemPublisher {
	static Logger LOGGER = LoggerFactory.getLogger(EventManagementSystemPublisher.class);


	
	protected ObjectMapper om = new ObjectMapper();
	private final String emsUser;
	private final String emsPassword;
	private final String emsURL;
	private final String reportingTopicPrefix;
	Map<String, MessageProducer> producers;
	Client client;

	public EventManagementSystemPublisher(String emsURL, String emsUser, String emsPassword, String reportingTopicPrefix) {
		this.emsUser = emsUser;
		this.reportingTopicPrefix = reportingTopicPrefix;
		this.emsURL = emsURL;
		this.emsPassword = emsPassword;
		connect();
	}
	

	private void connect() {
		try {
			
			if(!emsURL.contains(":"))
			{
				LOGGER.error("Invalid EMS url. Must be of the form 'host':'port'",emsURL);
				return;
				
			}
			String emsHost = emsURL.split(":")[0];
			Integer emsPort = Integer.parseInt(emsURL.split(":")[1]);
			
			
			client = new Client( emsHost, emsPort, emsUser, emsPassword );
			client.addErrorListener( new Listener() {
				    public void message( Map header, String message ) {
				    	System.out.println(header);
				    	System.out.println(message);
				    }
				  });
		} catch (Exception ex) {
			LOGGER.error("Can't setup EMS publisher", ex);
		}
	}

	public void _send(String topicName, double value, long timestamp) {
		if (client == null || client.isClosed() || client.isConnected()) {
			connect();
		}
		String topic = reportingTopicPrefix+topicName;
		try {
			String payload = om.writeValueAsString(Map.of("metricValue", value, "level", 1, "timestamp", timestamp));		
			LOGGER.info(String.format("Publish metric %s -> %s", topic, payload));
			client.send(topic, payload);
		} catch (Exception ex) {
			LOGGER.error("Can't send message to EMS", ex);
		}
	}

	/*public static void send(String topicName, double value, long timestamp) {

		instance._send(topicName, value, timestamp);
	}*/
}
