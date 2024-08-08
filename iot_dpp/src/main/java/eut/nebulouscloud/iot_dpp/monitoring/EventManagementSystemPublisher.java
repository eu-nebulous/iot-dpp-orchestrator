package eut.nebulouscloud.iot_dpp.monitoring;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.apache.activemq.artemis.api.core.Message;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.client.ActiveMQClient;
import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.apache.activemq.artemis.api.core.client.ClientProducer;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;

public class EventManagementSystemPublisher {
	static Logger LOGGER = LoggerFactory.getLogger(EventManagementSystemPublisher.class);

	private static EventManagementSystemPublisher instance;
	protected ObjectMapper om = new ObjectMapper();
	private final String emsUser;
	private final String emsPassword;
	private final String emsURL;
	private final String reportingTopicPrefix;
	ClientSession session;
	Map<String, ClientProducer> producers;

	public EventManagementSystemPublisher(String emsURL, String emsUser, String emsPassword, String reportingTopicPrefix) {
		this.emsUser = emsUser;
		this.reportingTopicPrefix = reportingTopicPrefix;
		this.emsURL = emsURL;
		this.emsPassword = emsPassword;
		connect();
	}
	
	/*public static void init(String emsURL, String emsUser, String emsPassword) {
		
		if (instance == null) {
			LOGGER.info("Init EventManagementSystemPublisher");
			instance = new EventManagementSystemPublisher(emsURL,emsUser,emsPassword);
		}else
		{
			LOGGER.warn("EventManagementSystemPublisher is already initialized. No need to initialize it again.");
		}
	}*/

	private void connect() {
		try {
			ServerLocator serverLocator = ActiveMQClient.createServerLocator(emsURL);
			ClientSessionFactory factory = serverLocator.createSessionFactory();
			session = factory.createSession(emsUser, emsPassword, true, true, true, false, 1);
			producers = new HashMap<String, ClientProducer>();
		} catch (Exception ex) {
			LOGGER.error("Can't setup EMS publisher", ex);
		}
	}

	public void _send(String topicName, double value, long timestamp) {
		if (session == null || session.isClosed()) {
			connect();
		}
		String topic = reportingTopicPrefix+topicName;
		try {
			String payload = om.writeValueAsString(Map.of("metricValue", value, "level", 1, "timestamp", timestamp));
			if (!producers.containsKey(topic)) {
				producers.put(topic, session.createProducer());
			}
			ClientMessage message = session.createMessage(Message.TEXT_TYPE, false);
			message.getBodyBuffer().writeNullableSimpleString(SimpleString.toSimpleString(payload));
			LOGGER.info(String.format("Publish metric %s -> %s", topic, payload));
			producers.get(topic).send(topic, message);
		} catch (Exception ex) {
			LOGGER.error("Can't send message to EMS", ex);
		}
	}

	/*public static void send(String topicName, double value, long timestamp) {

		instance._send(topicName, value, timestamp);
	}*/
}
