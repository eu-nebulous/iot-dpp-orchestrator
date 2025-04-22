package eut.nebulouscloud.auth;

import static org.junit.Assert.assertTrue;

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
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Test the correct functionaltiy of DynamicBridgePluginTest
 */
class AuthPluginTest2 {

	static Logger LOGGER = LoggerFactory.getLogger(AuthPluginTest2.class);
	static int QueuesMonitoringProcesQueryIntervalSeconds = 3;
	ObjectMapper om = new ObjectMapper();
	private static int brokerPort = 3355;
	private static final int KEYCLOAK_PORT = 8080;
	private static final String KEYCLOAK_REALM = "nebulous_iot";
	private static final String KEYCLOAK_CLIENT_ID = "message_broker";
	private static final String KEYCLOAK_CLIENT_SECRET = "pG9p9NYWBKM4RpeQeNxsE1NSlQmpmAWF";
	private static final String KEYCLOAK_PUBLIC_KEY="";
	private static KeycloackRolesSyncPlugin syncp;
	private static EmbeddedActiveMQ broker;



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
			properties.put("keycloak.realmPublicKey",KEYCLOAK_PUBLIC_KEY );
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

		EmbeddedActiveMQ broker = createBroker("test-server", brokerPort);

		while(true)
		{
			try {
				IMqttClient publisher = new MqttClient("tcp://localhost:" + brokerPort, "publisher");
				MqttConnectOptions connOpts = new MqttConnectOptions();
				connOpts.setUserName("app_user_1");
				connOpts.setPassword("".toCharArray());
				publisher.connect(connOpts);
				MqttMessage message = new MqttMessage("hello".getBytes());
				message.setQos(2);
				publisher.publish("topic.a", message);
				return;
			} catch (Exception e) {
				LOGGER.error("",e);
				// TODO: handle exception
			}
		}
		

	}

}
