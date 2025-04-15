package eut.nebulouscloud.auth;

import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.core.type.TypeReference;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;

/**
 * Client for interacting with Keycloak authentication service.
 * This class handles token acquisition, management, and role synchronization with Keycloak.
 * It provides functionality to authenticate with Keycloak, obtain access tokens, and retrieve role information.
 */
public class KeycloakClient {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(KeycloakClient.class);
    
    /** The base URL of the Keycloak server */
    private final String keycloakBaseUrl;
    
    /** The realm name in Keycloak */
    private final String realm;
    
    /** The client ID for authentication */
    private final String clientId;
    
    /** The client secret for authentication */
    private final String clientSecret;
    
    /** Object mapper for JSON serialization/deserialization */
    private final ObjectMapper objectMapper;
    
    /** The current access token */
    private String accessToken;
    
    /** The type of token (e.g., "Bearer") */
    private String tokenType;
    
    /** The expiration time of the current token */
    private long tokenExpirationTime;
    
    /**
     * Creates a new KeycloakClient with the specified configuration.
     * 
     * @param keycloakBaseUrl The base URL of the Keycloak service
     * @param realm The realm name
     * @param clientId The client ID
     * @param clientSecret The client secret
     */
    public KeycloakClient(String keycloakBaseUrl, String realm, String clientId, String clientSecret) {
        this.keycloakBaseUrl = keycloakBaseUrl;
        this.realm = realm;
        this.clientId = clientId;
        this.clientSecret = clientSecret;        
        this.objectMapper = new ObjectMapper();
        
        LOGGER.info("KeycloakClient initialized with base URL: {}, realm: {}", keycloakBaseUrl, realm);
    }
    
    /**
     * Obtains an access token from Keycloak using client credentials flow.
     * This method handles the OAuth2 client credentials grant type.
     * 
     * @return true if token was successfully obtained, false otherwise
     */
    public boolean obtainToken() {
        try {
            LOGGER.debug("Obtaining token from Keycloak");
            String tokenEndpoint = keycloakBaseUrl + "/realms/" + realm + "/protocol/openid-connect/token";
            CloseableHttpClient httpClient = HttpClients.createDefault();
            HttpPost httpPost = new HttpPost(tokenEndpoint);
            
            // Set headers
            httpPost.setHeader("Content-Type", "application/x-www-form-urlencoded");
            
            // Set request body
            String requestBody = "grant_type=client_credentials&client_id=" + clientId + "&client_secret=" + clientSecret;
            StringEntity entity = new StringEntity(requestBody);
            httpPost.setEntity(entity);
            
            // Execute request
            CloseableHttpResponse response = httpClient.execute(httpPost);
            HttpEntity responseEntity = response.getEntity();
            
            if (response.getStatusLine().getStatusCode() == 200 && responseEntity != null) {
                String responseBody = EntityUtils.toString(responseEntity);
                JsonNode jsonResponse = objectMapper.readTree(responseBody);
                
                // Extract token information
                accessToken = jsonResponse.get("access_token").asText();
                tokenType = jsonResponse.get("token_type").asText();
                int expiresIn = jsonResponse.get("expires_in").asInt();
                
                // Calculate expiration time (current time + expires_in seconds)
                tokenExpirationTime = System.currentTimeMillis() + (expiresIn * 1000);
                
                LOGGER.debug("Successfully obtained token from Keycloak, expires in {} seconds", expiresIn);
                return true;
            } else {
                LOGGER.error("Failed to obtain token from Keycloak. Status code: {}", 
                        response.getStatusLine().getStatusCode());
                return false;
            }
        } catch (Exception e) {
            LOGGER.error("Error obtaining token from Keycloak", e);
            return false;
        }
    }
    
    /**
     * Gets the current access token, refreshing it if necessary.
     * 
     * @return The access token or null if token could not be obtained
     */
    public String getAccessToken() {
        // Check if token is expired or not yet obtained
        if (accessToken == null || System.currentTimeMillis() >= tokenExpirationTime) {
            LOGGER.debug("Token expired or not obtained, attempting to obtain new token");
            if (!obtainToken()) {
                return null;
            }
        }
        return accessToken;
    }
    
    /**
     * Gets the token type (e.g., "Bearer").
     * 
     * @return The token type
     */
    public String getTokenType() {
        return tokenType;
    }
    
    /**
     * Gets the full authorization header value (e.g., "Bearer eyJhbGciOiJSUzI1...").
     * 
     * @return The authorization header value or null if token is not available
     */
    public String getAuthorizationHeader() {
        String token = getAccessToken();
        if (token != null && tokenType != null) {
            return tokenType + " " + token;
        }
        return null;
    }
    
    /**
     * Lists all roles defined in the Keycloak realm.
     * 
     * @return A list of role information as maps, or an empty list if the request fails
     */
    public List<Map<String, Object>> listRoles() {
        try {
            LOGGER.debug("Listing roles from Keycloak");
            
            // Ensure we have a valid token
            String authHeader = getAuthorizationHeader();
            if (authHeader == null) {
                LOGGER.error("Failed to obtain authorization header for listing roles");
                return new ArrayList<>();
            }
            
            // Construct the roles endpoint URL
            String rolesEndpoint = keycloakBaseUrl + "/admin/realms/" + realm + "/roles";
            
            // Create and configure the HTTP request
            CloseableHttpClient httpClient = HttpClients.createDefault();
            HttpGet httpGet = new HttpGet(rolesEndpoint);
            httpGet.setHeader("Authorization", authHeader);
            
            // Execute the request
            CloseableHttpResponse response = httpClient.execute(httpGet);
            HttpEntity responseEntity = response.getEntity();
            
            if (response.getStatusLine().getStatusCode() == 200 && responseEntity != null) {
                String responseBody = EntityUtils.toString(responseEntity);
                
                // Parse the JSON response into a list of maps
                List<Map<String, Object>> roles = objectMapper.readValue(
                    responseBody, 
                    new TypeReference<List<Map<String, Object>>>() {}
                );
                
                LOGGER.debug("Successfully retrieved {} roles from Keycloak", roles.size());
                return roles;
            } else {
                LOGGER.error("Failed to list roles from Keycloak. Status code: {}", 
                        response.getStatusLine().getStatusCode());
                return new ArrayList<>();
            }
        } catch (Exception e) {
            LOGGER.error("Error listing roles from Keycloak", e);
            return new ArrayList<>();
        }
    }
    
    /**
     * Gets detailed information for a specific role by its ID.
     * 
     * @param roleId The ID of the role to retrieve
     * @return A map containing the role details, or null if the request fails
     */
    public Map<String, Object> getRoleById(String roleId) {
        try {
            LOGGER.debug("Getting role details for role ID: {}", roleId);
            
            // Ensure we have a valid token
            String authHeader = getAuthorizationHeader();
            if (authHeader == null) {
                LOGGER.error("Failed to obtain authorization header for getting role details");
                return null;
            }
            
            // Construct the role endpoint URL
            String roleEndpoint = keycloakBaseUrl + "/admin/realms/" + realm + "/roles-by-id/" + roleId;
            
            // Create and configure the HTTP request
            CloseableHttpClient httpClient = HttpClients.createDefault();
            HttpGet httpGet = new HttpGet(roleEndpoint);
            httpGet.setHeader("Authorization", authHeader);
            
            // Execute the request
            CloseableHttpResponse response = httpClient.execute(httpGet);
            HttpEntity responseEntity = response.getEntity();
            
            if (response.getStatusLine().getStatusCode() == 200 && responseEntity != null) {
                String responseBody = EntityUtils.toString(responseEntity);
                
                // Parse the JSON response into a map
                Map<String, Object> roleDetails = objectMapper.readValue(
                    responseBody, 
                    new TypeReference<Map<String, Object>>() {}
                );
                
                LOGGER.debug("Successfully retrieved role details for role ID: {}", roleId);
                return roleDetails;
            } else {
                LOGGER.error("Failed to get role details for role ID: {}. Status code: {}", 
                        roleId, response.getStatusLine().getStatusCode());
                return null;
            }
        } catch (Exception e) {
            LOGGER.error("Error getting role details for role ID: {}", roleId, e);
            return null;
        }
    }

    /**
     * Checks if a string value represents a boolean true.
     * 
     * @param value The string value to check
     * @return true if the value represents a boolean true, false otherwise
     */
    public boolean isTrue(String value) {	
        return value != null && (
            value.toLowerCase().equals("true") ||
            value.toLowerCase().equals("yes") ||
            value.toLowerCase().equals("1") ||
            value.toLowerCase().equals("t")
        );
    }
    
    /**
     * Gets the string value of a property, removing array brackets if present.
     * 
     * @param value The property value to process
     * @return The processed string value, or null if the input is null
     */
    public String getPropertyValue(Object value) {
        if(value == null) return null;
        String ret = value.toString();
        return ret.replaceAll("^\\[","").replaceAll("\\]$","");
    }

    /**
     * Checks if a boolean attribute in a map is true.
     * 
     * @param attributes The map of attributes
     * @param attributeName The name of the attribute to check
     * @return true if the attribute exists and is true, false otherwise
     */
    private boolean isBooleanAttributeTrue(Map<String, Object> attributes, String attributeName) {
        if (attributes.containsKey(attributeName)) {
            return isTrue(getPropertyValue(attributes.get(attributeName)));
        }
        return false;
    }
    
    /**
     * Gets detailed information for all roles with their attributes.
     * This method retrieves all roles from Keycloak and processes their attributes
     * to create KeycloakRole objects.
     * 
     * @return A list of KeycloakRole objects, or an empty list if the request fails
     */
    public List<KeycloakRole> getDetailedRoles() {
        List<KeycloakRole> detailedRoles = new ArrayList<>();
        
        // First, get the list of all roles
        List<Map<String, Object>> roles = listRoles();
        LOGGER.debug("Retrieved {} roles from Keycloak", roles.size());
        
        // For each role, get its detailed information
        for (Map<String, Object> role : roles) {
            String roleId = (String) role.get("id");
            String roleName = (String) role.get("name");   
            Map<String, Object> roleDetails = getRoleById(roleId);
            
            if (roleDetails != null) {
                // Extract attributes
                @SuppressWarnings("unchecked")
                Map<String, Object> attributes = (Map<String, Object>) roleDetails.get("attributes");

                if(attributes == null){
                    LOGGER.debug("Skipping role with missing attributes: {} with details: {}", roleName, roleDetails);
                    continue;
                }

                // Check if this is a Nebulous role              
                if (!isBooleanAttributeTrue(attributes, "is_nebulous_role")) {
                    LOGGER.debug("Skipping non-Nebulous role: {} with details: {}", roleName, roleDetails);
                    continue;
                }

                String match = getPropertyValue(attributes.get("match"));
                if (match == null || match.isBlank()){
                    LOGGER.debug("Skipping role with missing match: {} with details: {}", roleName, roleDetails);
                    continue;
                }

                // Initialize all permission flags
                boolean read = isBooleanAttributeTrue(attributes, "read");
                boolean write = isBooleanAttributeTrue(attributes, "write");
                boolean send = isBooleanAttributeTrue(attributes, "send");
                boolean consume = isBooleanAttributeTrue(attributes, "consume");
                boolean createAddress = isBooleanAttributeTrue(attributes, "createAddress");
                boolean deleteAddress = isBooleanAttributeTrue(attributes, "deleteAddress");
                boolean createDurableQueue = isBooleanAttributeTrue(attributes, "createDurableQueue");
                boolean deleteDurableQueue = isBooleanAttributeTrue(attributes, "deleteDurableQueue");
                boolean createNonDurableQueue = isBooleanAttributeTrue(attributes, "createNonDurableQueue");
                boolean deleteNonDurableQueue = isBooleanAttributeTrue(attributes, "deleteNonDurableQueue");
                boolean manage = isBooleanAttributeTrue(attributes, "manage");
                boolean browse = isBooleanAttributeTrue(attributes, "browse");
                boolean view = isBooleanAttributeTrue(attributes, "view");
                boolean edit = isBooleanAttributeTrue(attributes, "edit");
                
                // Create a KeycloakRole object with all the extracted properties
                KeycloakRole keycloakRole = new KeycloakRole(
                    roleId, roleName, match, read, write,
                    send, consume, createAddress, deleteAddress,
                    createDurableQueue, deleteDurableQueue, createNonDurableQueue,
                    deleteNonDurableQueue, manage, browse, view, edit
                );
                
                detailedRoles.add(keycloakRole);
                LOGGER.debug("Added role: {}", keycloakRole);
            } else {
                LOGGER.debug("Skipping role with missing details: {} with details: {}", roleName, roleDetails);
            }
        }
        
        LOGGER.info("Successfully processed {} detailed roles from Keycloak", detailedRoles.size());
        return detailedRoles;
    }
} 