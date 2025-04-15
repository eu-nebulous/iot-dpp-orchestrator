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
import java.util.Set;

import javax.security.auth.Subject;

import org.apache.activemq.artemis.core.security.CheckType;
import org.apache.activemq.artemis.core.security.Role;
import org.apache.activemq.artemis.spi.core.protocol.RemotingConnection;
import org.apache.activemq.artemis.spi.core.security.ActiveMQBasicSecurityManager;
import org.apache.activemq.artemis.spi.core.security.jaas.RolePrincipal;
import org.apache.activemq.artemis.spi.core.security.jaas.UserPrincipal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.impl.DefaultClaims;


public class KeycloackAuthPlugin extends ActiveMQBasicSecurityManager {

	private static final Logger LOGGER = LoggerFactory.getLogger(KeycloackAuthPlugin.class);
	private String realmPublicKey;

	@Override
	public ActiveMQBasicSecurityManager init(Map<String, String> properties) {
		LOGGER.debug("Initializing KeycloackAuthPlugin with properties: {}", properties);

		realmPublicKey = Optional.ofNullable(properties.get("keycloak.realmPublicKey"))
				.orElseThrow(() -> new IllegalStateException("keycloak.realmPublicKey parameter is not defined"));

		return super.init(properties);
	}

	@Override
	public Subject authenticate(String userToAuthenticate, String passwordToAuthenticate,
			RemotingConnection remotingConnection, String securityDomain) {
		LOGGER.debug("Authenticating user: {} with security domain: {}", userToAuthenticate, securityDomain);

		Subject res = super.authenticate(userToAuthenticate, passwordToAuthenticate, remotingConnection,
				securityDomain);
		if (res != null) {
			return res;
		} else {
			// Check if it is JWT token
			List<String> roles;
			try {
				roles = extractRealmRolesFromJWT(passwordToAuthenticate);
			} catch (InvalidKeySpecException e) {
				LOGGER.error("Can't extract roles from claim",e);
				return null;
			}
			if (roles != null) {
				Subject subject = new Subject();
				subject.getPrincipals().add(new UserPrincipal(userToAuthenticate));
				for (String role : roles) {
					subject.getPrincipals().add(new RolePrincipal(role));
				}
				return subject;
			} else {
				return null;
			}

		}

	}

	@Override
	public boolean authorize(Subject subject, Set<Role> roles, CheckType checkType, String address) {
		// TODO Auto-generated method stub
		boolean ret = super.authorize(subject, roles, checkType, address);
		return ret;
	}

	public List<String> extractRealmRolesFromJWT(String token) throws InvalidKeySpecException
	{
		byte[] keyBytes = Base64.getDecoder().decode(realmPublicKey);
		KeyFactory keyFactory = null;
		try {
			keyFactory = KeyFactory.getInstance("RSA");
		} catch (NoSuchAlgorithmException e) {
			LOGGER.error("NoSuchAlgorithmException",e);
			return null;
		}
		RSAPublicKey rsaPublicKey = (RSAPublicKey) keyFactory.generatePublic(new X509EncodedKeySpec(keyBytes));
		DefaultClaims jwe = (DefaultClaims) Jwts.parser().verifyWith(rsaPublicKey).build().parse(token).getPayload();		
		return (List<String>) ((HashMap)jwe.get("realm_access")).get("roles");
	}
	
}
