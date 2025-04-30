package eut.nebulouscloud.bridge;

import java.util.List;

import org.apache.activemq.artemis.core.config.BridgeConfiguration;
import org.apache.activemq.artemis.core.config.TransformerConfiguration;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.plugin.ActiveMQServerPlugin;
import org.apache.activemq.artemis.spi.core.security.ActiveMQBasicSecurityManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;

abstract class NebulOuSBridge implements ActiveMQServerPlugin {
	
	protected Logger LOGGER;
	ActiveMQServer server;
	ActiveMQBasicSecurityManager securityManager;
	ObjectMapper om = new ObjectMapper();
	
	@Override
	public void registered(ActiveMQServer server) {		
		this.server = server;
		this.securityManager = ((ActiveMQBasicSecurityManager)server.getSecurityManager());
	}
	
	protected String constructBridgeUserRole(String appId)
	{
		return "bridge";
	}	
	
	protected String constructBridgeUserName(String appId)
	{
		return "bridge-"+appId;
	}
	

	protected String extractAPPIdFromUserName(String userName)
	{
		if(userName != null && userName.startsWith("bridge"))
		{
			return  userName.replaceFirst("bridge-","");
		}
		return null;
	}
	
	
	protected void addUser(String appId, String password) {
		try {
			LOGGER.info("Adding bridge user '{}' for application '{}'", constructBridgeUserName(appId), appId);
			securityManager.addNewUser(constructBridgeUserName(appId), password, constructBridgeUserRole(appId));
			LOGGER.info("Successfully added bridge user '{}' for application '{}'", constructBridgeUserName(appId), appId);
		} catch (Exception ex) {
			LOGGER.error("Failed to add user '{}' for application '{}': {}", constructBridgeUserName(appId), appId, ex.getMessage());
		}
	}
	



	protected void createTopicBridge(String appId, String password,String connectorName,String addressName)
	{
		try {
			LOGGER.info("Creating topic bridge for app '{}' with address '{}' using connector '{}'", appId, addressName, connectorName);
			server.getActiveMQServerControl().createQueue("topic://"+addressName,appId+"-"+addressName);
			BridgeConfiguration conf = new BridgeConfiguration();
			conf.setName(appId+"-"+addressName);
			conf.setQueueName(appId+"-"+addressName);
			conf.setStaticConnectors(List.of(connectorName));
			conf.setFilterString( "application='"+appId+"'  AND NEB_BRIDGED IS NULL");
			conf.setUser(constructBridgeUserName(appId));			
			conf.setPassword(password);
			TransformerConfiguration tc = new TransformerConfiguration("eut.nebulouscloud.bridge.BridgeTransformer");
			conf.setTransformerConfiguration(tc);
			server.deployBridge(conf);			
			LOGGER.info("Successfully created topic bridge for app '{}' with address '{}' using connector '{}'", appId, addressName, connectorName);
		} catch (Exception e) {
			LOGGER.error("Failed to create topic bridge for app '{}' with address '{}' using connector '{}': {}", appId, addressName, connectorName, e.getMessage());
		}
	}
}
