package eut.nebulouscloud.cluster_info;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.Message;
import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.RoutingContext;
import org.apache.activemq.artemis.core.server.plugin.ActiveMQServerPlugin;
import org.apache.activemq.artemis.protocol.amqp.broker.AMQPStandardMessage;
import org.apache.activemq.artemis.spi.core.security.ActiveMQBasicSecurityManager;
import org.apache.qpid.protonj2.client.Client;
import org.apache.qpid.protonj2.client.ClientOptions;
import org.apache.qpid.protonj2.client.Connection;
import org.apache.qpid.protonj2.client.ConnectionOptions;
import org.apache.qpid.protonj2.client.DeliveryMode;
import org.apache.qpid.protonj2.client.Sender;
import org.apache.qpid.protonj2.client.SenderOptions;
import org.apache.qpid.protonj2.client.Session;
import org.apache.qpid.protonj2.client.Tracker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;

class ClusterInfoPlugin implements ActiveMQServerPlugin {

	protected Logger LOGGER = LoggerFactory.getLogger(ClusterInfoPlugin.class);
	ActiveMQServer server;
	ActiveMQBasicSecurityManager securityManager;
	ObjectMapper om = new ObjectMapper();
	private String username;
	private String password;
	private String influxdb_token;
	private String influxdb_url;
	private String influxdb_org;
	private String port;
	private String influxdb_bucket;

	@Override
	public void init(Map<String, String> properties) {
		this.username = Optional.ofNullable(properties.get("BROKER_USERNAME"))
				.orElseThrow(() -> new IllegalStateException("BROKER_USERNAME parameter is not defined"));
		this.port = Optional.ofNullable(properties.get("BROKER_PORT"))
				.orElseThrow(() -> new IllegalStateException("BROKER_PORT parameter is not defined"));

		this.password = Optional.ofNullable(properties.get("BROKER_PASSWORD"))
				.orElseThrow(() -> new IllegalStateException("BROKER_PASSWORD parameter is not defined"));

		this.influxdb_url = Optional.ofNullable(properties.get("INFLUXDB_URL"))
				.orElseThrow(() -> new IllegalStateException("INFLUXDB_URL parameter is not defined"));

		this.influxdb_org = Optional.ofNullable(properties.get("INFLUXDB_ORG"))
				.orElseThrow(() -> new IllegalStateException("INFLUXDB_ORG parameter is not defined"));

		this.influxdb_token = Optional.ofNullable(properties.get("INFLUXDB_TOKEN"))
				.orElseThrow(() -> new IllegalStateException("INFLUXDB_TOKEN parameter is not defined"));

		this.influxdb_token = Optional.ofNullable(properties.get("INFLUXDB_BUCKET"))
				.orElseThrow(() -> new IllegalStateException("INFLUXDB_BUCKET parameter is not defined"));

		LOGGER.info("ClusterInfoPlugin initialized with username: {}", username);
	}

	@Override
	public void registered(ActiveMQServer server) {
		this.server = server;
		this.securityManager = ((ActiveMQBasicSecurityManager) server.getSecurityManager());
	}

	public static String INFLUXDB_CREDENTIALS_GET_TOPIC = "eu.nebulouscloud.app_cluster.influxdb.get";
	public static String INFLUXDB_CREDENTIALS_GET_REPLY_TOPIC = INFLUXDB_CREDENTIALS_GET_TOPIC + ".reply";

	private void processInfluxDBCredentialsGetTopic(Message m) {

		// LOGGER.info("Processing InfluxDB credentials get request for application:
		// {}", appID);
		String appID = "";
		try {

			// Create JSON payload - structure it as a Map with "body" key to match AMQP format
			Map<String, Object> payload = new HashMap<>();
			payload.put("INFLUXDB_URL", influxdb_url);
			payload.put("INFLUXDB_TOKEN", influxdb_token);
			payload.put("INFLUXDB_ORG", influxdb_org);
			payload.put("INFLUXDB_BUCKET", influxdb_bucket);
			
			
			// Add other parameters as needed
			String jsonPayload = om.writeValueAsString(payload);
			// Wrap in Map with "body" key to match what AMQP consumer expects
			
			AMQPStandardMessage message = (AMQPStandardMessage) m;
			
			// Extract properties from AMQP message
			String applicationProperty = message.getStringProperty("application");
			Object correlationId = message.getProperties().getCorrelationId();
			
			try {
				// Get broker connection info - use default AMQP port
				// Since we're running inside the broker, connect to localhost
				String brokerHost = "localhost";
				// Create Qpid client connection
				ClientOptions clientOptions = new ClientOptions();
				Client client = Client.create(clientOptions);
				
				ConnectionOptions connectionOptions = new ConnectionOptions();
				connectionOptions.user(username);
				connectionOptions.password(password);
				Connection connection = client.connect(brokerHost, Integer.parseInt(port), connectionOptions);
				connection.openFuture().get();
				
				// Create session and sender
				Session session = connection.openSession();
				session.openFuture().get();
				
				SenderOptions senderOptions = new SenderOptions();
				senderOptions.deliveryMode(DeliveryMode.AT_LEAST_ONCE);
				Sender sender = session.openSender("topic://"+INFLUXDB_CREDENTIALS_GET_REPLY_TOPIC, senderOptions);
				sender.openFuture().get();
				
				// Create reply message
				org.apache.qpid.protonj2.client.Message<Map<String, Object>> replyMessage = 
						
					org.apache.qpid.protonj2.client.Message.create(payload);
				replyMessage.to("topic://"+INFLUXDB_CREDENTIALS_GET_REPLY_TOPIC);
				replyMessage.durable(true);
				
				// Set properties
				if (applicationProperty != null) {
					replyMessage.property("application", applicationProperty);
					replyMessage.subject(applicationProperty);
				}
				
				// Set correlation ID
				if (correlationId != null) {
					replyMessage.correlationId(correlationId.toString());
				}
				
				// Send message
				Tracker tracker = sender.send(replyMessage);
				tracker.settlementFuture().get();
				
				LOGGER.info("Correlation ID: {}", correlationId != null ? correlationId.toString() : "null");
				
				// Clean up
				sender.close();
				session.close();
				connection.close();
				client.close();
				
			} catch (Exception ex) {
				LOGGER.error("failed to reply", ex);
			}
			LOGGER.debug("Sent reply message to topic '{}' for application '{}' with payload: {}",
					INFLUXDB_CREDENTIALS_GET_REPLY_TOPIC, appID, jsonPayload);

			LOGGER.info("Successfully sent InfluxDB credentials reply for application: {}", appID);
		} catch (Exception ex) {
			LOGGER.error("Failed to process InfluxDB credentials get request for application: {}", appID, ex);
		}
	}

	@Override
	public void beforeMessageRoute(Message message, RoutingContext context, boolean direct, boolean rejectDuplicates)
			throws ActiveMQException {

		if (message.getAddress().toString().equals(INFLUXDB_CREDENTIALS_GET_TOPIC)
				|| message.getAddress().toString().equals("topic://" + INFLUXDB_CREDENTIALS_GET_TOPIC)) {
			LOGGER.info("INFLUXDB_CREDENTIALS_GET_TOPIC intercepted");
			processInfluxDBCredentialsGetTopic(message);
		}

	}

}
