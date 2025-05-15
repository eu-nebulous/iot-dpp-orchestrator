package eut.nebulouscloud.auth;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import io.jsonwebtoken.Jwts;

/**
 * A mock Keycloak server for testing the KeycloakAuthPlugin and
 * KeycloakRolesSyncPlugin. This server simulates the basic authentication and
 * role retrieval functionality of Keycloak.
 */
public class MockKeycloakServer {
	private static final Logger LOGGER = LoggerFactory.getLogger(MockKeycloakServer.class);
	private final HttpServer server;
	private final ObjectMapper objectMapper = new ObjectMapper();
	private final Map<String, String> userCredentials = new HashMap<>();
	private final Map<String, List<String>> userRoles = new HashMap<>();
	private final String realm;
	private final String clientId;
	private final String clientSecret;
	private final int port;
	private List<Map<String, Object>> roles = List.of();
	private java.security.KeyPair keyPair;

	
	/**
	 * Creates a new MockKeycloakServer with the specified configuration.
	 *
	 * @param port         The port to listen on
	 * @param realm        The Keycloak realm name
	 * @param clientId     The client ID
	 * @param clientSecret The client secret
	 * @throws IOException If the server cannot be started
	 */
	public MockKeycloakServer(int port, String realm, String clientId, String clientSecret) throws IOException {
		this.port = port;
		this.realm = realm;
		this.clientId = clientId;
		this.clientSecret = clientSecret;

		// Initialize the server
		server = HttpServer.create(new InetSocketAddress(port), 0);

		// Set up handlers
		server.createContext("/realms/" + realm + "/protocol/openid-connect/token", new TokenHandler());
		server.createContext("/admin/realms/" + realm + "/roles", new ListRolesHandler());
		server.createContext("/admin/realms/" + realm + "/roles-by-id", new GetRoleHandler());

		
		try {
			KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
			kpg.initialize(2048);
			keyPair = kpg.generateKeyPair();
		} catch (NoSuchAlgorithmException e) {
			LOGGER.error("NoSuchAlgorithmException",e);
		}
		
		
		server.createContext("/", new Default());
		// Set the executor
		server.setExecutor(Executors.newFixedThreadPool(10));

	}
	

	public String getPublicKey()
	{
		return Base64.getMimeEncoder().encodeToString(keyPair.getPublic().getEncoded()).replace("\n", "").replace("\r", "");
	}

	

	public String signIn(String user, List<String> roles) {

		return Jwts.builder().subject((user)).issuedAt(new Date())
				.expiration(new Date((new Date()).getTime() + 60 * 1000)).claim("realm_access", Map.of("roles", roles)).signWith(keyPair.getPrivate()).compact();
	}

	

	public void setRoles(List<Map<String, Object>> roles) {
		this.roles = roles;
	}

	/**
	 * Starts the mock server.
	 */
	public void start() {
		server.start();
		LOGGER.info("Mock Keycloak server started on port {}", port);
	}

	/**
	 * Stops the mock server.
	 */
	public void stop() {
		server.stop(0);
		LOGGER.info("Mock Keycloak server stopped");
	}

	private class Default implements HttpHandler {
		@Override
		public void handle(HttpExchange exchange) throws IOException {
			String path = exchange.getRequestURI().getPath();
			String method = exchange.getRequestMethod();

			sendError(exchange, 404, "Not found");
		}
	}

	/**
	 * Handler for token requests.
	 */
	private class TokenHandler implements HttpHandler {
		@Override
		public void handle(HttpExchange exchange) throws IOException {
			if (!"POST".equals(exchange.getRequestMethod())) {
				sendError(exchange, 405, "Method Not Allowed");
				return;
			}

			// Parse the request body
			String requestBody = new String(exchange.getRequestBody().readAllBytes());
			Map<String, String> params = parseFormData(requestBody);

			// Check if this is a client credentials flow
			if ("client_credentials".equals(params.get("grant_type"))) {
				if (clientId.equals(params.get("client_id")) && clientSecret.equals(params.get("client_secret"))) {
					// Generate a token for the client
					sendTokenResponse(exchange, "client_token", List.of("admin"));
					return;
				}
			}

			// Check if this is a password flow
			if ("password".equals(params.get("grant_type"))) {
				String username = params.get("username");
				String password = params.get("password");

				if (userCredentials.containsKey(username) && userCredentials.get(username).equals(password)) {
					// Generate a token for the user
					List<String> roles = userRoles.getOrDefault(username, new ArrayList<>());
					sendTokenResponse(exchange, username + "_token", roles);
					return;
				}
			}

			// Authentication failed
			sendError(exchange, 401, "Unauthorized");
		}

		private void sendTokenResponse(HttpExchange exchange, String token, List<String> roles) throws IOException {
			ObjectNode response = objectMapper.createObjectNode();
			response.put("access_token", token);
			response.put("token_type", "Bearer");
			response.put("expires_in", 300);
			response.put("refresh_expires_in", 1800);
			response.put("refresh_token", token + "_refresh");
			response.put("id_token", token + "_id");
			response.put("session_state", "session_id");
			response.put("scope", "openid profile roles");

			// Add roles to the token claims
			ObjectNode claims = objectMapper.createObjectNode();
			ObjectNode realmAccess = objectMapper.createObjectNode();
			ObjectNode resourceAccess = objectMapper.createObjectNode();

			// Add realm roles
			ObjectNode realmRoles = objectMapper.createObjectNode();
			ArrayNode rolesArray = realmRoles.putArray("roles");
			for (String role : roles) {
				rolesArray.add(role);
			}
			realmAccess.set("roles", realmRoles);
			claims.set("realm_access", realmAccess);

			// Add resource roles
			ObjectNode clientRoles = objectMapper.createObjectNode();
			ArrayNode clientRolesArray = clientRoles.putArray("roles");
			for (String role : roles) {
				clientRolesArray.add(role);
			}
			resourceAccess.set(clientId, clientRoles);
			claims.set("resource_access", resourceAccess);

			response.set("claims", claims);

			// Send the response
			byte[] responseBytes = objectMapper.writeValueAsBytes(response);
			exchange.getResponseHeaders().set("Content-Type", "application/json");
			exchange.sendResponseHeaders(200, responseBytes.length);
			exchange.getResponseBody().write(responseBytes);
		}
	}

	/**
	 * Handler for roles requests.
	 */
	private class ListRolesHandler implements HttpHandler {
		@Override
		public void handle(HttpExchange exchange) throws IOException {
			if (!"GET".equals(exchange.getRequestMethod())) {
				sendError(exchange, 405, "Method Not Allowed");
				return;
			}

			// Get the authorization header
			String authHeader = exchange.getRequestHeaders().getFirst("Authorization");
			if (authHeader == null || !authHeader.startsWith("Bearer ")) {
				sendError(exchange, 401, "Unauthorized");
				return;
			}

			// Send the response
			byte[] responseBytes = objectMapper.writeValueAsBytes(roles);
			exchange.getResponseHeaders().set("Content-Type", "application/json");
			exchange.sendResponseHeaders(200, responseBytes.length);
			exchange.getResponseBody().write(responseBytes);
		}
	}

	private class GetRoleHandler implements HttpHandler {
		@Override
		public void handle(HttpExchange exchange) throws IOException {
			if (!"GET".equals(exchange.getRequestMethod())) {
				sendError(exchange, 405, "Method Not Allowed");
				return;
			}

			// Get the authorization header
			String authHeader = exchange.getRequestHeaders().getFirst("Authorization");
			if (authHeader == null || !authHeader.startsWith("Bearer ")) {
				sendError(exchange, 401, "Unauthorized");
				return;
			}

			// Get the role ID from the URL path
			String path = exchange.getRequestURI().getPath();

			String roleId = path.substring(path.lastIndexOf('/') + 1);

			Map<String, Object> role = roles.stream().filter(r -> ((Map<String, Object>) r).get("id").equals(roleId))
					.findFirst().orElse(null);
			if (role == null) {
				sendError(exchange, 404, "Role not found");
				return;
			}

			// Create the response
			// Send the response
			byte[] responseBytes = objectMapper.writeValueAsBytes(role);
			exchange.getResponseHeaders().set("Content-Type", "application/json");
			exchange.sendResponseHeaders(200, responseBytes.length);
			exchange.getResponseBody().write(responseBytes);
		}
	}

	/**
	 * Handler for client registration requests.
	 */
	private class ClientRegistrationHandler implements HttpHandler {
		@Override
		public void handle(HttpExchange exchange) throws IOException {
			if (!"GET".equals(exchange.getRequestMethod())) {
				sendError(exchange, 405, "Method Not Allowed");
				return;
			}

			// Get the authorization header
			String authHeader = exchange.getRequestHeaders().getFirst("Authorization");
			if (authHeader == null || !authHeader.startsWith("Bearer ")) {
				sendError(exchange, 401, "Unauthorized");
				return;
			}

			// Extract the token
			String token = authHeader.substring(7);

			// Check if the token is valid
			boolean isValidToken = false;
			for (Map.Entry<String, String> entry : userCredentials.entrySet()) {
				if (token.equals(entry.getKey() + "_token")) {
					isValidToken = true;
					break;
				}
			}

			if (!isValidToken) {
				sendError(exchange, 401, "Invalid token");
				return;
			}

			// Create the response
			ObjectNode response = objectMapper.createObjectNode();
			response.put("client_id", clientId);
			response.put("client_secret", clientSecret);
			response.put("redirect_uris", "[]");
			response.put("response_types", "[]");
			response.put("grant_types", "[]");
			response.put("application_type", "web");
			response.put("token_endpoint_auth_method", "client_secret_post");

			// Send the response
			byte[] responseBytes = objectMapper.writeValueAsBytes(response);
			exchange.getResponseHeaders().set("Content-Type", "application/json");
			exchange.sendResponseHeaders(200, responseBytes.length);
			exchange.getResponseBody().write(responseBytes);
		}
	}

	/**
	 * Sends an error response.
	 */
	private void sendError(HttpExchange exchange, int code, String message) throws IOException {
		ObjectNode response = objectMapper.createObjectNode();
		response.put("error", message);

		byte[] responseBytes = objectMapper.writeValueAsBytes(response);
		exchange.getResponseHeaders().set("Content-Type", "application/json");
		exchange.sendResponseHeaders(code, responseBytes.length);
		exchange.getResponseBody().write(responseBytes);
	}

	/**
	 * Parses form data from a request body.
	 */
	private Map<String, String> parseFormData(String formData) {
		Map<String, String> result = new HashMap<>();
		String[] pairs = formData.split("&");
		for (String pair : pairs) {
			String[] keyValue = pair.split("=");
			if (keyValue.length == 2) {
				result.put(keyValue[0], keyValue[1]);
			}
		}
		return result;
	}

	/**
	 * Main method to start the mock server for testing.
	 */
	public static void main(String[] args) throws IOException {
		MockKeycloakServer server = new MockKeycloakServer(8080, "master", "message_broker",
				"Rp1PWLOtpoTP84OcjsmqsPYPlOubqJ7D");
		server.start();

		// Keep the server running
		try {
			Thread.sleep(Long.MAX_VALUE);
		} catch (InterruptedException e) {
			server.stop();
		}
	}
}