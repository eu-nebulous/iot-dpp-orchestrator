package eut.nebulouscloud.cluster_info;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.Message;
import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.RoutingContext;
import org.apache.activemq.artemis.core.server.plugin.ActiveMQServerPlugin;
import org.apache.activemq.artemis.protocol.amqp.broker.AMQPStandardMessage;
import org.apache.activemq.artemis.spi.core.security.ActiveMQBasicSecurityManager;
import org.apache.activemq.artemis.utils.collections.ConcurrentHashSet;
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
/***
 * ActiveMQ Artemis plugin that intercepts messages to provide InfluxDB credentials to applications.
 * Listens for requests on the "eu.nebulouscloud.app_cluster.influxdb.get" topic and responds with
 * InfluxDB connection details (URL, token, organization, and application-specific bucket).
 * In case the application is bridged, it will not answer the request, but the bridged application will.
 * Also processes bridging configuration messages to maintain a registry of bridged application IDs.
 */
public class ClusterInfoPlugin implements ActiveMQServerPlugin {

	protected Logger LOGGER = LoggerFactory.getLogger(ClusterInfoPlugin.class);
	ActiveMQServer server;
	ActiveMQBasicSecurityManager securityManager;
	ObjectMapper om = new ObjectMapper();
	private String username;
	private String password;
	private String influxdb_token;
	private String influxdb_url;
	private String influxdb_org;
	private boolean isNebulousControlPlane;
	private String port;
	private Set<String> brigedApps = new ConcurrentHashSet<String>(); //IDs of the apps that have influxdb deployed on the app cluster master
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

		this.isNebulousControlPlane =Boolean.parseBoolean(Optional.ofNullable(properties.get("IS_NEBULOUS_CONTROL_PLANE"))
				.orElse("false"));
		
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

		try {
			AMQPStandardMessage request = (AMQPStandardMessage) m;
			String applicationId = request.getStringProperty("application");
			
			if(brigedApps.contains(applicationId) && isNebulousControlPlane)
			{
				LOGGER.info("Recieved request for influxdb credentials from a bridged app {}. Do nothing, the app will answer",applicationId);
				return;
			}else
			{
				LOGGER.info("Recieved request for influxdb credentials from an app that is not known to be bridged {}. Answer with NebulOuS control plane influxdb details",applicationId);

			}
			
			Map<String, Object> payload = new HashMap<>();
			payload.put("INFLUXDB_URL", influxdb_url);
			payload.put("INFLUXDB_TOKEN", influxdb_token);
			payload.put("INFLUXDB_ORG", influxdb_org);
			payload.put("INFLUXDB_BUCKET", String.format("nebulous_%s_bucket", applicationId));
			String jsonPayload = om.writeValueAsString(payload);
			Object correlationId = request.getProperties().getCorrelationId();

			try {			
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
				Sender sender = session.openSender("topic://" + INFLUXDB_CREDENTIALS_GET_REPLY_TOPIC, senderOptions);
				sender.openFuture().get();

				// Create reply message
				org.apache.qpid.protonj2.client.Message<Map<String, Object>> replyMessage =org.apache.qpid.protonj2.client.Message.create(payload);
				replyMessage.to("topic://" + INFLUXDB_CREDENTIALS_GET_REPLY_TOPIC);
				replyMessage.durable(true);

				// Set properties
				if (applicationId != null) {
					replyMessage.property("application", applicationId);
					replyMessage.subject(applicationId);
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
					INFLUXDB_CREDENTIALS_GET_REPLY_TOPIC, applicationId, jsonPayload);

			LOGGER.info("Successfully sent InfluxDB credentials reply for application: {}", applicationId);
		} catch (Exception ex) {
			LOGGER.error("Failed to process InfluxDB credentials get request for application", ex);
		}
	}
	
	 
	private void processBridgingConfigMessage(Message message) {
		try {
			String appID = message.getStringProperty("application");
			LOGGER.debug("Processing bridging config message for application: {}", appID);
			brigedApps.add(appID);
					
		} catch (Exception ex) {
			LOGGER.error("Failed to process bridging configuration message: {}", ex.getMessage(), ex);
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

		if (message.getAddress().equals("eu.nebulouscloud.bridging")) {
			processBridgingConfigMessage(message);
		}
	}

}
