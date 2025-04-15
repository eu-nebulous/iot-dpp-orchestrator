package eut.nebulouscloud.auth;

import static org.junit.Assert.assertTrue;

import java.security.KeyFactory;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.config.WildcardConfiguration;
import org.apache.activemq.artemis.core.config.impl.ConfigurationImpl;
import org.apache.activemq.artemis.core.security.Role;
import org.apache.activemq.artemis.core.server.embedded.EmbeddedActiveMQ;
import org.apache.activemq.artemis.spi.core.security.ActiveMQBasicSecurityManager;
import org.eclipse.paho.client.mqttv3.IMqttClient;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.MqttSecurityException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.impl.DefaultClaims;

/**
 * Test the correct functionaltiy of DynamicBridgePluginTest
 */
class AuthPluginTest {

	static Logger LOGGER = LoggerFactory.getLogger(AuthPluginTest.class);
	static int QueuesMonitoringProcesQueryIntervalSeconds = 3;
	ObjectMapper om = new ObjectMapper();
	private static int brokerPort = 3355;
	private static MockKeycloakServer mockKeycloakServer;
	private static final int KEYCLOAK_PORT = 8081;
	private static final String KEYCLOAK_REALM = "master";
	private static final String KEYCLOAK_CLIENT_ID = "message_broker";
	private static final String KEYCLOAK_CLIENT_SECRET = "Rp1PWLOtpoTP84OcjsmqsPYPlOubqJ7D";
	private static final String KEYCLOAK_REALM_KEY = "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAs8vF9zlzt2/vP6FWdDJkIHNq2GO9Oce/VpDlrZb4Jj/awVXDcuUV0acOXVll4XTycElWEjk7m9kGrnMFAA/s/c1UePNiaK8axMdk+Sirm8rzwU6lfvTVsGtyzoVqTAEr19TJvWN1UDjxRc9bFUouoeQeAL+fzn57Kjups2HkmnmKeajPJ7/KSorJ/B78vo4cuj/2yQv3cm0MGm7U8LqV/RVgmW3yrGOEJKR3p9rhk2tVEyO6QsWJA6HOgEEkvOg1mNxAMDy9d8u8AG2pZ3uuR5BTgD5fp7By74iUrn3KC+O+M6PuRjWXitKYlm8qA4H7+pKl1dQsu4hSScRN0XNfRwIDAQAB";
	private static KeycloackRolesSyncPlugin syncp;
	private static EmbeddedActiveMQ broker;

	@BeforeAll
	static void setUp() throws Exception {
		// Start the mock Keycloak server
		mockKeycloakServer = new MockKeycloakServer(KEYCLOAK_PORT, KEYCLOAK_REALM, KEYCLOAK_CLIENT_ID,
				KEYCLOAK_CLIENT_SECRET);
		mockKeycloakServer.start();
		broker = createBroker("test-server", brokerPort);
		LOGGER.info("Mock Keycloak server started for testing");

	}

	@AfterAll
	static void tearDown() {
		// Stop the mock Keycloak server
		if (mockKeycloakServer != null) {
			mockKeycloakServer.stop();
			LOGGER.info("Mock Keycloak server stopped");
		}
		if (broker != null) {
			try {
				broker.stop();
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}



	private static EmbeddedActiveMQ createBroker(String nodeName, int port) throws Exception {
		Configuration config = new ConfigurationImpl();
		config.setName(nodeName);
		String foldersRoot = "data/" + new Date().getTime() + "/data_" + port;
		config.setBindingsDirectory(foldersRoot + "/bindings");
		config.setJournalDirectory(foldersRoot + "/journal");
		config.setJournalRetentionDirectory(foldersRoot + "/journalRetention");
		config.setLargeMessagesDirectory(foldersRoot + "/lm");
		config.setNodeManagerLockDirectory(foldersRoot + "/nodeManagerLock");
		config.setPagingDirectory(foldersRoot + "/paging");
		config.addAcceptorConfiguration("netty", "tcp://localhost:" + port);
		Map<String, Set<Role>> security_setting = new HashMap<String, Set<Role>>();
		security_setting.put("#",
				Set.of(new Role("admin", true, true, true, true, true, true, true, true, true, true),
						new Role("bridge", true, false, false, false, false, false, false, false, false, false),
						new Role("app", true, false, false, false, false, false, false, false, false, false)));
		// PATH, READ, WRITE, .config..config.
		config.setSecurityRoles(security_setting);
		config.setSecurityEnabled(true);
		WildcardConfiguration wc = new WildcardConfiguration();
		wc.setAnyWords('>');
		config.setWildCardConfiguration(wc);
		EmbeddedActiveMQ server = new EmbeddedActiveMQ();
		{
			KeycloackAuthPlugin sm = new KeycloackAuthPlugin();
			Map<String, String> properties = new HashMap<>();
			properties.put(ActiveMQBasicSecurityManager.BOOTSTRAP_USER, "admin");
			properties.put(ActiveMQBasicSecurityManager.BOOTSTRAP_PASSWORD, "admin");
			properties.put(ActiveMQBasicSecurityManager.BOOTSTRAP_ROLE, "admin");
			properties.put("keycloak.realmPublicKey", KEYCLOAK_REALM_KEY);
			sm.init(properties);
			server.setSecurityManager(sm);
		}

		{
			syncp = new KeycloackRolesSyncPlugin();
			Map<String, String> properties = new HashMap<>();
			properties.put("keycloak.baseUrl", "http://localhost:" + KEYCLOAK_PORT);
			properties.put("keycloak.realm", KEYCLOAK_REALM);
			properties.put("keycloak.clientId", KEYCLOAK_CLIENT_ID);
			properties.put("keycloak.clientSecret", KEYCLOAK_CLIENT_SECRET);
			syncp.init(properties);
			config.getBrokerPlugins().add(syncp);
		}

		config.setJMXManagementEnabled(true);
		server.setConfiguration(config);

		server.start();
		while (!server.getActiveMQServer().isActive()) {
			System.out.println("Waiting for server to start...");
			Thread.sleep(500);
		}

		return server;
	}

	@Test
	void Test1() throws Exception {

		LOGGER.info("Starting controlPlaneClient");

		// Can't connect if user doesn't exist

		try {
			// Test MQTT client connection with authentication
			IMqttClient publisher = new MqttClient("tcp://localhost:" + brokerPort, "publisher");
			MqttConnectOptions connOpts = new MqttConnectOptions();
			connOpts.setUserName("app_user_1");
			connOpts.setPassword("app_user_1".toCharArray());
			publisher.connect(connOpts);
			assert (false);
		} catch (MqttSecurityException ex) {
			LOGGER.info("Got MqttSecurityException as expected", ex);
			assert (true);
		}

	}

	@Test
	void Test2() throws Exception {
		// Can connect, but can't write

		// Test MQTT client connection with authentication
		IMqttClient publisher = new MqttClient("tcp://localhost:" + brokerPort, "publisher");
		MqttConnectOptions connOpts = new MqttConnectOptions();
		connOpts.setUserName("app_user_1");		
		connOpts.setPassword(mockKeycloakServer.signIn("app_user_1", List.of()).toCharArray());
		publisher.connect(connOpts);

		try {
			MqttMessage message = new MqttMessage("hello".getBytes());
			LOGGER.info("publish " + message.getId());
			message.setQos(2);
			publisher.publish("topic.a", message);
			assert (false);

		} catch (Exception ex) {
			LOGGER.info("Got exception, as expected", ex);
			assert (true);
		}
	}

	@Test
	void Test3() throws Exception {

		{
			Map<String, Object> role1 = new HashMap<String, Object>();
			role1.put("id", "1");
			role1.put("name", "topic_a_publisher");
			Map<String, Object> roleAttributes = new HashMap<String, Object>();
			roleAttributes.put("is_nebulous_role", List.of(true));
			roleAttributes.put("match", List.of("topic\\.a"));
			roleAttributes.put("read", List.of(true));
			roleAttributes.put("write", List.of(false));
			roleAttributes.put("createAddress", List.of(false));
			roleAttributes.put("createNonDurableQueue", List.of(false));
			role1.put("attributes", roleAttributes);
			mockKeycloakServer.setRoles(List.of(role1));
			syncp.refreshRoles();
			Thread.sleep(1000);

			try {
				IMqttClient publisher = new MqttClient("tcp://localhost:" + brokerPort, "publisher");
				MqttConnectOptions connOpts = new MqttConnectOptions();
				connOpts.setUserName("app_user_1");
				connOpts.setPassword(mockKeycloakServer.signIn("app_user_1", List.of("topic_a_publisher")).toCharArray());
				publisher.connect(connOpts);
				MqttMessage message = new MqttMessage("hello".getBytes());
				message.setQos(2);
				publisher.publish("topic.a", message);
				assertTrue(false);
			} catch (Exception e) {
				assertTrue(true);
				// TODO: handle exception
			}

		}

		{
			Map<String, Object> role1 = new HashMap<String, Object>();
			role1.put("id", "1");
			role1.put("name", "topic_a_publisher");
			Map<String, Object> roleAttributes = new HashMap<String, Object>();
			roleAttributes.put("is_nebulous_role", List.of(true));
			roleAttributes.put("match", List.of("topic\\.a"));
			roleAttributes.put("read", List.of(true));
			roleAttributes.put("write", List.of(true));
			roleAttributes.put("createAddress", List.of(true));
			roleAttributes.put("createNonDurableQueue", List.of(true));
			role1.put("attributes", roleAttributes);
			mockKeycloakServer.setRoles(List.of(role1));
			syncp.refreshRoles();
			Thread.sleep(1000);

			IMqttClient publisher = new MqttClient("tcp://localhost:" + brokerPort, "publisher");
			MqttConnectOptions connOpts = new MqttConnectOptions();
			connOpts.setUserName("app_user_1");
			connOpts.setPassword(mockKeycloakServer.signIn("app_user_1", List.of("topic_a_publisher")).toCharArray());
			publisher.connect(connOpts);
			MqttMessage message = new MqttMessage("hello".getBytes());
			message.setQos(2);
			publisher.publish("topic.a", message);

		}
	}

	
	@Test
	void validate2() throws Exception {

		KeycloackAuthPlugin sm = new KeycloackAuthPlugin();
		Map<String, String> properties = new HashMap<>();
		properties.put(ActiveMQBasicSecurityManager.BOOTSTRAP_USER, "admin");
		properties.put(ActiveMQBasicSecurityManager.BOOTSTRAP_PASSWORD, "admin");
		properties.put(ActiveMQBasicSecurityManager.BOOTSTRAP_ROLE, "admin");
		properties.put("keycloak.realmPublicKey", mockKeycloakServer.getPublicKey());
		sm.init(properties);
		String token = mockKeycloakServer.signIn("admin",List.of("thisismyrole"));		
		LOGGER.info(token);
		LOGGER.info(mockKeycloakServer.getPublicKey());
		List<String> roles = sm.extractRealmRolesFromJWT(token);		
		assertTrue(roles.contains("thisismyrole"));

	}
}
