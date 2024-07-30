package eut.nebulouscloud.iot_dpp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
//remove
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import javax.jms.Connection;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;

import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.config.impl.ConfigurationImpl;
import org.apache.activemq.artemis.core.security.CheckType;
import org.apache.activemq.artemis.core.security.Role;
import org.apache.activemq.artemis.core.server.embedded.EmbeddedActiveMQ;
import org.apache.activemq.artemis.jms.client.ActiveMQJMSConnectionFactory;
import org.apache.activemq.artemis.jms.client.ActiveMQTextMessage;
import org.apache.activemq.artemis.spi.core.security.ActiveMQSecurityManager;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;

import eut.nebulouscloud.iot_dpp.GroupIDExtractionParameters.GroupIDExpressionSource;

class MessageGroupIdAnnotationPluginTest {
	static Logger LOGGER = LoggerFactory.getLogger(MessageGroupIdAnnotationPluginTest.class);
	static MessageGroupIDAnnotationPlugin groupIdExtractionPlugin = new MessageGroupIDAnnotationPlugin();
	ObjectMapper om = new ObjectMapper();
	/**
	 * Creates a local ActiveMQ server listening at localhost:61616. The server
	 * accepts requests from any user. Configures the MessageGroupIDAnnotationPlugin
	 * and sets it to use the provided groupIdExtractionParameterPerTopic dict.
	 * 
	 * @param events The groupIdExtractionParameterPerTopic dict. Its contents can
	 *               be changed by the test code during the test execution and the
	 *               plugin will react accordingly.
	 * @return the created EmbeddedActiveMQ instance.
	 * @throws Exception
	 */
	static private EmbeddedActiveMQ createLocalServer(int port) throws Exception {
		Configuration config = new ConfigurationImpl();

		String foldersRoot = "data/" + new Date().getTime() + "/data_" + port;
		config.setBindingsDirectory(foldersRoot + "/bindings");
		config.setJournalDirectory(foldersRoot + "/journal");
		config.setJournalRetentionDirectory(foldersRoot + "/journalRetention");
		config.setLargeMessagesDirectory(foldersRoot + "/lm");
		config.setNodeManagerLockDirectory(foldersRoot + "/nodeManagerLock");
		config.setPagingDirectory(foldersRoot + "/paging");
		config.addConnectorConfiguration("serverAt" + port + "Connector", "tcp://localhost:" + port);
		config.addAcceptorConfiguration("netty", "tcp://localhost:" + port);
							
		config.getBrokerMessagePlugins().add(groupIdExtractionPlugin);
		EmbeddedActiveMQ server = new EmbeddedActiveMQ();
		server.setSecurityManager(new ActiveMQSecurityManager() {
			@Override
			public boolean validateUserAndRole(String user, String password, Set<Role> roles, CheckType checkType) {
				return true;
			}

			@Override
			public boolean validateUser(String user, String password) {
				return true;
			}
		});
		server.setConfiguration(config);
		server.start();
		Thread.sleep(1000);
		return server;
	}

	static EmbeddedActiveMQ server = null;
	
	static Session session;
	static Connection connection;

	@BeforeAll
	static void createServer() throws Exception {
		LOGGER.info("createServer");
		server = createLocalServer(6161);
		ActiveMQJMSConnectionFactory connectionFactory = new ActiveMQJMSConnectionFactory("tcp://localhost:6161",
				"artemis", "artemis");
		connection = connectionFactory.createConnection();
		connection.start();

	}

	@AfterAll
	static void destroyServer() {
		try {
			server.stop();
		} catch (Exception ex) {
		}
	}

	@BeforeEach
	void before() throws JMSException {
		session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
		groupIdExtractionPlugin.init(Map.of());
	}

	@AfterEach
	void adter() {
		if (session != null) {
			try {
				session.close();
			} catch (Exception ex) {
			}
		}
	}

	
	/**
	 * Test the construction of the plugin reading the config from a file
	 * @throws IOException
	 */
	@Test
	void createPluginTest() throws IOException {		
		
		MessageGroupIDAnnotationPlugin plugin = new MessageGroupIDAnnotationPlugin();
		plugin.init(Map.of(MessageGroupIDAnotator.GROUP_ID_EXTRACTION_CONFIG_PATH_ENV_VAR,"ssadsadsadsa"));
		assertTrue(plugin.annotator.groupIdExtractionParameterPerAddress.isEmpty()); 
		
		
		Map<String, GroupIDExtractionParameters> map = new HashMap<String, GroupIDExtractionParameters>();
		map.put("A", new GroupIDExtractionParameters(GroupIDExpressionSource.BODY_JSON, "address.city"));
		map.put("B", new GroupIDExtractionParameters(GroupIDExpressionSource.BODY_XML, "address.city"));
		Path tempPath = Files.createTempFile("GroupIDExtractionParameters", ".json");
		
		Files.write(tempPath, om.writeValueAsBytes(map));
		plugin = new MessageGroupIDAnnotationPlugin();
		plugin.init(Map.of(MessageGroupIDAnotator.GROUP_ID_EXTRACTION_CONFIG_PATH_ENV_VAR,tempPath.toString()));
		assertTrue(!plugin.annotator.groupIdExtractionParameterPerAddress.isEmpty());
		assertEquals(2, plugin.annotator.groupIdExtractionParameterPerAddress.keySet().size());
		assertTrue(plugin.annotator.groupIdExtractionParameterPerAddress.containsKey("A"));
		assertTrue(plugin.annotator.groupIdExtractionParameterPerAddress.get("A").getExpression().equals(map.get("A").getExpression()));
		assertTrue(plugin.annotator.groupIdExtractionParameterPerAddress.get("A").getSource().equals(map.get("A").getSource()));
		assertTrue(plugin.annotator.groupIdExtractionParameterPerAddress.containsKey("B"));
		assertTrue(plugin.annotator.groupIdExtractionParameterPerAddress.get("B").getExpression().equals(map.get("B").getExpression()));
		assertTrue(plugin.annotator.groupIdExtractionParameterPerAddress.get("B").getSource().equals(map.get("B").getSource()));
	}

	/**
	 * Test it can extract a simple JSON value
	 * 
	 * @throws Exception
	 */
	@Test
	void JSONTest1() throws Exception {

		String address = "testaddress";
		
		Map<String, GroupIDExtractionParameters> map = new HashMap<String, GroupIDExtractionParameters>();
		map.put(address,new GroupIDExtractionParameters(GroupIDExpressionSource.BODY_JSON, "address.city"));
		groupIdExtractionPlugin.init(Map.of(MessageGroupIDAnotator.GROUP_ID_EXTRACTION_CONFIG_MAP_ENV_VAR,om.writeValueAsString(map)));
		
		Destination destination = session.createQueue(address);
		MessageProducer producer = session.createProducer(destination);
		MessageConsumer consumer = session.createConsumer(destination);
		String text = "{\"address\":{\"city\":\"Lleida\",\"street\":\"C\\\\Cavallers\"},\"name\":\"Jon doe\",\"age\":\"22\"}";
		TextMessage originalMessage = session.createTextMessage(text);
		producer.send(originalMessage);
		Message message = consumer.receive();
		String value = ((ActiveMQTextMessage) message).getCoreMessage()
				.getStringProperty(MessageGroupIDAnotator.MESSAGE_GROUP_ANNOTATION.toString());
		assertEquals("Lleida", value);

	}

	/**
	 * In case of invalid message body, MESSAGE_GROUP_ANNOTATION should remain null
	 * 
	 * @throws Exception
	 */
	@Test
	void JSONTest2() throws Exception {

		String address = "testaddress";
		Map<String, GroupIDExtractionParameters> groupIDExtractionParameters = new HashMap<String, GroupIDExtractionParameters>();
		groupIDExtractionParameters.put(address,
				new GroupIDExtractionParameters(GroupIDExpressionSource.BODY_JSON, "address.city"));
		groupIdExtractionPlugin.init(Map.of(MessageGroupIDAnotator.GROUP_ID_EXTRACTION_CONFIG_MAP_ENV_VAR,om.writeValueAsString(groupIDExtractionParameters)));
	
		Destination destination = session.createQueue(address);
		MessageProducer producer = session.createProducer(destination);
		MessageConsumer consumer = session.createConsumer(destination);
		String text = "{\"ad2\"}";
		TextMessage originalMessage = session.createTextMessage(text);
		producer.send(originalMessage);
		Message message = consumer.receive();
		String value = ((ActiveMQTextMessage) message).getCoreMessage()
				.getStringProperty(MessageGroupIDAnotator.MESSAGE_GROUP_ANNOTATION.toString());
		assertEquals(null, value);

	}

	/**
	 * Test it can extract a complex JSON value
	 * 
	 * @throws Exception
	 */
	@Test
	void JSONTest3() throws Exception {

		String address = "testaddress";
		Map<String, GroupIDExtractionParameters> groupIDExtractionParameters = new HashMap<String, GroupIDExtractionParameters>();
		groupIDExtractionParameters.put(address,
				new GroupIDExtractionParameters(GroupIDExpressionSource.BODY_JSON, "address"));
		groupIdExtractionPlugin.init(Map.of(MessageGroupIDAnotator.GROUP_ID_EXTRACTION_CONFIG_MAP_ENV_VAR,om.writeValueAsString(groupIDExtractionParameters)));
		

		Destination destination = session.createQueue(address);
		MessageProducer producer = session.createProducer(destination);
		MessageConsumer consumer = session.createConsumer(destination);
		String text = "{\"address\":{\"city\":\"Lleida\",\"street\":\"C\\\\Cavallers\"},\"name\":\"Jon doe\",\"age\":\"22\"}";
		TextMessage originalMessage = session.createTextMessage(text);
		producer.send(originalMessage);
		Message message = consumer.receive();
		String value = ((ActiveMQTextMessage) message).getCoreMessage()
				.getStringProperty(MessageGroupIDAnotator.MESSAGE_GROUP_ANNOTATION.toString());
		assertEquals("{city=Lleida, street=C\\Cavallers}", value);

	}

	/**
	 * Test it can extract a simple XML value
	 * 
	 * @throws Exception
	 */
	@Test
	void XMLTest1() throws Exception {

		String address = "testaddress";
		Map<String, GroupIDExtractionParameters> groupIDExtractionParameters = new HashMap<String, GroupIDExtractionParameters>();
		groupIDExtractionParameters.put(address,
				new GroupIDExtractionParameters(GroupIDExpressionSource.BODY_XML, "/root/address/city"));
		groupIdExtractionPlugin.init(Map.of(MessageGroupIDAnotator.GROUP_ID_EXTRACTION_CONFIG_MAP_ENV_VAR,om.writeValueAsString(groupIDExtractionParameters)));
		
		Destination destination = session.createQueue(address);
		MessageProducer producer = session.createProducer(destination);
		MessageConsumer consumer = session.createConsumer(destination);
		String text = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n" + "<root>\r\n" + "   <address>\r\n"
				+ "      <city>Lleida</city>\r\n" + "      <street>C\\Cavallers</street>\r\n" + "   </address>\r\n"
				+ "   <age>22</age>\r\n" + "   <name>Jon doe</name>\r\n" + "</root>";
		TextMessage originalMessage = session.createTextMessage(text);
		producer.send(originalMessage);
		Message message = consumer.receive();

		String value = ((ActiveMQTextMessage) message).getCoreMessage()
				.getStringProperty(MessageGroupIDAnotator.MESSAGE_GROUP_ANNOTATION.toString());
		assertEquals("Lleida", value);

	}

	/**
	 * Test it can extract a complex XML value
	 * 
	 * @throws Exception
	 */
	@Test
	void XMLTest2() throws Exception {

		String address = "testaddress";
		Map<String, GroupIDExtractionParameters> groupIDExtractionParameters = new HashMap<String, GroupIDExtractionParameters>();
		groupIDExtractionParameters.put(address,
				new GroupIDExtractionParameters(GroupIDExpressionSource.BODY_XML, "/root/address"));
		Destination destination = session.createQueue(address);
		groupIdExtractionPlugin.init(Map.of(MessageGroupIDAnotator.GROUP_ID_EXTRACTION_CONFIG_MAP_ENV_VAR,om.writeValueAsString(groupIDExtractionParameters)));
		
		
		MessageProducer producer = session.createProducer(destination);
		MessageConsumer consumer = session.createConsumer(destination);
		String text = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n" + "<root>\r\n" + "   <address>\r\n"
				+ "      <city>Lleida</city>\r\n" + "      <street>C\\Cavallers</street>\r\n" + "   </address>\r\n"
				+ "   <age>22</age>\r\n" + "   <name>Jon doe</name>\r\n" + "</root>";
		TextMessage originalMessage = session.createTextMessage(text);
		producer.send(originalMessage);
		Message message = consumer.receive();
		String value = ((ActiveMQTextMessage) message).getCoreMessage()
				.getStringProperty(MessageGroupIDAnotator.MESSAGE_GROUP_ANNOTATION.toString());
		assertTrue(value != null);
		assertTrue(value.contains("Lleida"));
		assertTrue(value.contains("Cavallers"));
	}
	
	
	
	
	

}
