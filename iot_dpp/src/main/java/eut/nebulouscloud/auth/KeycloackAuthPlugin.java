package eut.nebulouscloud.auth;

import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.security.auth.Subject;
import org.apache.activemq.artemis.spi.core.protocol.RemotingConnection;
import org.apache.activemq.artemis.spi.core.security.ActiveMQBasicSecurityManager;
import org.apache.activemq.artemis.spi.core.security.jaas.RolePrincipal;
import org.apache.activemq.artemis.spi.core.security.jaas.UserPrincipal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.impl.DefaultClaims;

/**
 * ActiveMQ Artemis plugin that provides authentication using Keycloak JWT tokens.
 * This plugin extends the basic security manager to support both traditional username/password
 * authentication and JWT token-based authentication through Keycloak.
 * 
 * The plugin verifies JWT tokens using the realm's public key and extracts roles from the token claims.
 * It supports role-based access control by mapping Keycloak roles to ActiveMQ permissions.
 */
public class KeycloackAuthPlugin extends ActiveMQBasicSecurityManager {

	private static final Logger LOGGER = LoggerFactory.getLogger(KeycloackAuthPlugin.class);
	
	/** The public key used to verify JWT tokens from Keycloak */
	private String realmPublicKey;

	/**
	 * Initializes the plugin with the provided properties.
	 * Required properties:
	 * - keycloak.realmPublicKey: The public key of the Keycloak realm for JWT verification
	 *
	 * @param properties The configuration properties
	 * @return The initialized security manager
	 * @throws IllegalStateException if the realm public key is not provided
	 */
	@Override
	public ActiveMQBasicSecurityManager init(Map<String, String> properties) {
		LOGGER.debug("Initializing KeycloackAuthPlugin with properties: {}", properties);

		realmPublicKey = Optional.ofNullable(properties.get("keycloak.realmPublicKey"))
				.orElseThrow(() -> new IllegalStateException("keycloak.realmPublicKey parameter is not defined"));

		LOGGER.info("KeycloackAuthPlugin initialized with realm public key");
		return super.init(properties);
	}

	/**
	 * Authenticates a user using either traditional credentials or a JWT token.
	 * If traditional authentication fails, the plugin attempts to validate the password
	 * as a JWT token and extract roles from it.
	 *
	 * @param userToAuthenticate The username or token subject
	 * @param passwordToAuthenticate The password or JWT token
	 * @param remotingConnection The connection being authenticated
	 * @param securityDomain The security domain for authentication
	 * @return A Subject containing the authenticated user and their roles, or null if authentication fails
	 */
	@Override
	public Subject authenticate(String userToAuthenticate, String passwordToAuthenticate,
			RemotingConnection remotingConnection, String securityDomain) {
		LOGGER.debug("Authenticating user: {} with security domain: {}", userToAuthenticate, securityDomain);

		// First try traditional authentication
		Subject res = super.authenticate(userToAuthenticate, passwordToAuthenticate, remotingConnection,
				securityDomain);
		if (res != null) {
			LOGGER.debug("User {} authenticated successfully using traditional credentials", userToAuthenticate);
			return res;
		} else {
			//If traditional authentication fails, check if the passwordToAuthenticate is JWT token
			List<String> roles;
			try {
				LOGGER.debug("Attempting JWT token authentication for user: {}", userToAuthenticate);
				roles = extractRealmRolesFromJWT(passwordToAuthenticate);
			} catch (InvalidKeySpecException e) {
				LOGGER.error("Failed to extract roles from JWT token", e);
				return null;
			}
			if (roles != null) {
				//The passwordToAuthenticate is a JWT token. Create a new subject with the user and roles
				Subject subject = new Subject();
				subject.getPrincipals().add(new UserPrincipal(userToAuthenticate));
				for (String role : roles) {
					subject.getPrincipals().add(new RolePrincipal(role));
				}
				LOGGER.debug("User {} authenticated successfully using JWT token with roles: {}", 
						userToAuthenticate, roles);
				return subject;
			} else {
				LOGGER.debug("JWT token authentication failed for user: {}", userToAuthenticate);
				return null;
			}
		}
	}

	/**
	 * Extracts realm roles from a JWT token.
	 * This method verifies the token signature using the realm's public key and
	 * extracts the roles from the token claims.
	 *
	 * @param token The JWT token to process
	 * @return A list of role names extracted from the token, or null if the token is invalid
	 * @throws InvalidKeySpecException if there is an error processing the public key
	 */
	public List<String> extractRealmRolesFromJWT(String token) throws InvalidKeySpecException {
		LOGGER.debug("Extracting roles from JWT token");
		try {
			byte[] keyBytes = Base64.getDecoder().decode(realmPublicKey);
			KeyFactory keyFactory = KeyFactory.getInstance("RSA");
			RSAPublicKey rsaPublicKey = (RSAPublicKey) keyFactory.generatePublic(new X509EncodedKeySpec(keyBytes));
			
			DefaultClaims claims = (DefaultClaims) Jwts.parser()
				.verifyWith(rsaPublicKey)
				.build()
				.parse(token)
				.getPayload();
			
			@SuppressWarnings("unchecked")
			List<String> roles = (List<String>) ((HashMap)claims.get("realm_access")).get("roles");
			LOGGER.debug("Successfully extracted {} roles from JWT token", roles.size());
			return roles;
		} catch (NoSuchAlgorithmException e) {
			LOGGER.error("RSA algorithm not available", e);
			return null;
		} catch (Exception e) {
			LOGGER.debug("Error processing JWT token", e);
			return null;
		}
	}
}
