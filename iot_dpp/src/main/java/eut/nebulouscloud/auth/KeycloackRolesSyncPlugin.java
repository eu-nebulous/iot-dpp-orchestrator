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

/**
 * ActiveMQ Artemis plugin that synchronizes roles between Keycloak and ActiveMQ.
 * This plugin periodically fetches roles from Keycloak and updates the ActiveMQ
 * security configuration to match. It supports role-based access control (RBAC)
 * by mapping Keycloak roles to ActiveMQ permissions.
 */
public class KeycloackRolesSyncPlugin implements ActiveMQServerBasePlugin {
	private static final Logger LOGGER = LoggerFactory.getLogger(KeycloackRolesSyncPlugin.class);
	private KeycloakClient keycloakClient;
	private static final int REFRESH_INTERVAL_SECONDS = 10; 
	private ActiveMQServer server;

	/**
	 * Initializes the plugin with the provided properties.
	 * Required properties:
	 * - keycloak.baseUrl: The base URL of the Keycloak server
	 * - keycloak.realm: The realm name
	 * - keycloak.clientId: The client ID for authentication
	 * - keycloak.clientSecret: The client secret for authentication
	 *
	 * @param properties The configuration properties
	 * @throws IllegalStateException if any required property is missing
	 */
	@Override
	public void init(Map<String, String> properties) {
		LOGGER.debug("Initializing KeycloackRolesSyncPlugin with properties: {}", properties);

		String keycloakBaseUrl = Optional.ofNullable(properties.get("keycloak.baseUrl"))
				.orElseThrow(() -> new IllegalStateException("keycloak.baseUrl parameter is not defined"));

		String realm = Optional.ofNullable(properties.get("keycloak.realm"))
				.orElseThrow(() -> new IllegalStateException("keycloak.realm parameter is not defined"));

		String clientId = Optional.ofNullable(properties.get("keycloak.clientId"))
				.orElseThrow(() -> new IllegalStateException("keycloak.clientId parameter is not defined"));

		String clientSecret = Optional.ofNullable(properties.get("keycloak.clientSecret"))
				.orElseThrow(() -> new IllegalStateException("keycloak.clientSecret parameter is not defined"));

		LOGGER.info("Keycloak configuration - Base URL: {}, Realm: {}, Client ID: {}", 
				keycloakBaseUrl, realm, clientId);

		// Initialize the Keycloak client
		keycloakClient = new KeycloakClient(keycloakBaseUrl, realm, clientId, clientSecret);
	}

	/**
	 * Called when the plugin is registered with the ActiveMQ server.
	 * Starts the background thread for role synchronization.
	 *
	 * @param server The ActiveMQ server instance
	 */
	public void registered(ActiveMQServer server) {
		this.server = server;
		// Start the background thread to refresh roles
		LOGGER.info("Starting role refresh thread with interval of {} seconds", REFRESH_INTERVAL_SECONDS);
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

			if(!result.containsKey(keycloakRole.match)) {
				result.put(keycloakRole.match, new HashSet<>());
			}
			// Add the role to the set
			result.get(keycloakRole.match).add(activeMQRole);
			LOGGER.debug("Added role {} with match {} to ActiveMQ roles", keycloakRole.name, keycloakRole.match);
		}
		
		return result;
	}

	/**
	 * Forces a refresh of the cached roles from Keycloak.
	 * This method updates the ActiveMQ security configuration with the latest roles from Keycloak.
	 */
	public void refreshRoles() {
		LOGGER.debug("Refreshing roles from Keycloak");
		if (keycloakClient != null) {
			try {
				Map<String, Set<Role>> keycloakMatches = convertKeycloakRolesToActiveMQRoles(keycloakClient.getDetailedRoles());
				Map<String, Set<Role>> initialLocalMatches = server.getConfiguration().getSecurityRoles();
				Map<String, Set<Role>> finalLocalMatches = new HashMap<>();

				// Iterate over the current activemq matches
				for(String match : initialLocalMatches.keySet()) {
					Set<Role> matchRoles = new HashSet<>();
					// Get the keycloak roles for the current match
					Set<Role> keycloakRoles = keycloakMatches.containsKey(match) ? keycloakMatches.get(match) : Set.of();
					// Iterate over the local roles for the current match
					for(Role localRole : initialLocalMatches.get(match)) {
						// Search for a role with the same name in the keycloak roles for the current match
						Optional<Role> keycloackRole = keycloakRoles.stream()
							.filter(r -> r.getName().equals(localRole.getName()))
							.findFirst();
						// If the role is found in the keycloak roles for the current match, add it to the match roles
						if(keycloackRole.isPresent()) {
							matchRoles.add(keycloackRole.get());
							LOGGER.debug("Updated role {} for match {}", localRole.getName(), match);
						}else
						{
							// If the role is not found in the keycloak roles for the current match, remove it from the match roles (don't add it to the finalLocalMatches)
						}
						
					}						
					finalLocalMatches.put(match, matchRoles);		
				}

				// Iterate over the new keycloak matches (that are not in the initialLocalMatches) and add them to the finalLocalMatches
				List<String> newMatches = keycloakMatches.keySet().stream()
					.filter(k -> !initialLocalMatches.containsKey(k))
					.collect(Collectors.toList());
				for(String match : newMatches) {
					finalLocalMatches.put(match, keycloakMatches.get(match));
					LOGGER.debug("Added new match {} with {} roles", match, keycloakMatches.get(match).size());
				}

				// Update the security repository to contain the finalLocalMatches
				server.getSecurityRepository().clear();
				for(String m : finalLocalMatches.keySet()) {
					LOGGER.info("Adding match {} with {} roles", m, finalLocalMatches.get(m).size());
					server.getSecurityRepository().addMatch(m, finalLocalMatches.get(m));
				}
				
				LOGGER.info("Successfully refreshed roles from Keycloak");
			} catch (Exception e) {
				LOGGER.error("Failed to refresh roles from Keycloak", e);
			}
		} else {
			LOGGER.warn("Cannot refresh roles: KeycloakClient is null");
		}
	}

	/**
	 * Background thread that periodically refreshes the list of roles from Keycloak.
	 * This thread runs continuously, refreshing roles at the specified interval.
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
