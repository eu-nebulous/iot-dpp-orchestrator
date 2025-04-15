package eut.nebulouscloud.auth;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.HashMap;
import java.util.HashSet;

import org.apache.activemq.artemis.core.security.Role;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.plugin.ActiveMQServerBasePlugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class KeycloackRolesSyncPlugin implements ActiveMQServerBasePlugin {
	private static final Logger LOGGER = LoggerFactory.getLogger(KeycloackAuthPlugin.class);
	private KeycloakClient keycloakClient;
	private static final int REFRESH_INTERVAL_SECONDS = 10; // 5 minutes
	private ActiveMQServer server;

	@Override
	public void init(Map<String, String> properties) {
		LOGGER.debug("Initializing KeycloackAuthPlugin with properties: {}", properties);

		String keycloakBaseUrl = Optional.ofNullable(properties.get("keycloak.baseUrl"))
				.orElseThrow(() -> new IllegalStateException("keycloak.baseUrl parameter is not defined"));

		String realm = Optional.ofNullable(properties.get("keycloak.realm"))
				.orElseThrow(() -> new IllegalStateException("keycloak.realm parameter is not defined"));

		String clientId = Optional.ofNullable(properties.get("keycloak.clientId"))
				.orElseThrow(() -> new IllegalStateException("keycloak.clientId parameter is not defined"));

		String clientSecret = Optional.ofNullable(properties.get("keycloak.clientSecret"))
				.orElseThrow(() -> new IllegalStateException("keycloak.clientSecret parameter is not defined"));

		LOGGER.debug("Keycloak configuration - Base URL: {}, Realm: {}, Client ID: {}", keycloakBaseUrl, realm,
				clientId);

		// Initialize the Keycloak client
		keycloakClient = new KeycloakClient(keycloakBaseUrl, realm, clientId, clientSecret);

	}

	public void registered(ActiveMQServer server) {
		this.server = server;
		// Start the background thread to refresh roles
		LOGGER.debug("Starting role refresh thread");
		new Thread(new RoleRefreshRunner()).start();
	}

	/**
	 * Converts a list of KeycloakRole objects to a Map<String, Set<Role>> where the key is the match value
	 * and the value is a set of ActiveMQ Role objects with the appropriate permissions.
	 * 
	 * @param keycloakRoles The list of KeycloakRole objects to convert
	 * @return A Map where keys are match values and values are sets of ActiveMQ Role objects
	 */
	private Map<String, Set<Role>> convertKeycloakRolesToActiveMQRoles(List<KeycloakRole> keycloakRoles) {
		Map<String, Set<Role>> result = new HashMap<>();
		
		for (KeycloakRole keycloakRole : keycloakRoles) {
			// Create a new ActiveMQ Role with the permissions from the KeycloakRole
			Role activeMQRole = new Role(
				keycloakRole.name,
				keycloakRole.read,
				keycloakRole.write,
				keycloakRole.send,
				keycloakRole.consume,
				keycloakRole.createAddress,
				keycloakRole.deleteAddress,
				keycloakRole.createDurableQueue,
				keycloakRole.deleteDurableQueue,
				keycloakRole.createNonDurableQueue,
				keycloakRole.deleteNonDurableQueue
			);

			if(!result.containsKey(keycloakRole.match))
			{
				result.put(keycloakRole.match, new HashSet<>());
			}
			// Add the role to the set
			result.get(keycloakRole.match).add(activeMQRole);
		}
		
		return result;
	}

	/**
	 * Forces a refresh of the cached roles
	 * 
	 * @return The updated list of Keycloak roles
	 */
	public void refreshRoles() {
		LOGGER.debug("Refreshing roles from Keycloak");
		if (keycloakClient != null) {
			try {
				
				Map<String, Set<Role>> keycloakMatches = convertKeycloakRolesToActiveMQRoles(keycloakClient.getDetailedRoles());
				Map<String, Set<Role>> initialLocalMatches =  server.getConfiguration().getSecurityRoles();
				Map<String, Set<Role>> finalLocalMatches = new HashMap<>();

				for(String match : initialLocalMatches.keySet())
				{
					Set<Role> matchRoles = new HashSet<>();
					Set<Role> keycloakRoles = keycloakMatches.containsKey(match)?keycloakMatches.get(match):Set.of();
					for(Role localRole : initialLocalMatches.get(match))
					{
						Optional<Role> keycloackRole = keycloakRoles.stream().filter(r -> r.getName().equals(localRole.getName())).findFirst();
						if(keycloackRole.isPresent())
						{
							matchRoles.add(keycloackRole.get());
						}else
						{
							//matchRoles.add(localRole); //TODO: remove local role if came from keycloak
						}

					}						
					finalLocalMatches.put(match, matchRoles);		
				}
				List<String> newMatches = keycloakMatches.keySet().stream().filter(k -> !initialLocalMatches.containsKey(k)).collect(Collectors.toList());
				for(String match : newMatches)
				{
					finalLocalMatches.put(match, keycloakMatches.get(match));
				}
				server.getSecurityRepository().clear();
				for(String m:finalLocalMatches.keySet())
				{
					LOGGER.info("Adding match {} - {}",m,finalLocalMatches.get(m));
					server.getSecurityRepository().addMatch(m, finalLocalMatches.get(m));
				}				
			} catch (Exception e) {
				LOGGER.error("Failed to refresh roles from Keycloak", e);
			}
		} else {
			LOGGER.warn("Cannot refresh roles: KeycloakClient is null");
		}
	}

	/**
	 * Background thread that periodically refreshes the list of roles from Keycloak
	 */
	private class RoleRefreshRunner implements Runnable {

		@Override
		public void run() {
			LOGGER.info("Starting Keycloak role refresh thread");

			// Initial refresh
			LOGGER.debug("Performing initial role refresh");
			refreshRoles();

			// Periodic refresh
			while (true) {
				try {
					LOGGER.debug("Sleeping for {} seconds before next role refresh", REFRESH_INTERVAL_SECONDS);
					// Sleep for the refresh interval
					TimeUnit.SECONDS.sleep(REFRESH_INTERVAL_SECONDS);

					// Refresh roles
					LOGGER.debug("Performing periodic role refresh");
					refreshRoles();
				} catch (InterruptedException e) {
					LOGGER.warn("Role refresh thread interrupted", e);
					Thread.currentThread().interrupt();
					break;
				} catch (Exception e) {
					LOGGER.error("Error refreshing roles", e);
					// Continue running despite errors
				}
			}

			LOGGER.info("Keycloak role refresh thread stopped");
		}
	}
}
