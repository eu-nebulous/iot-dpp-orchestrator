package eut.nebulouscloud.iot_dpp;

import java.io.ByteArrayInputStream;
import java.util.HashMap;
import java.util.Map;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;

import org.apache.activemq.artemis.api.core.ActiveMQBuffer;
import org.apache.activemq.artemis.api.core.ICoreMessage;
import org.apache.activemq.artemis.api.core.Message;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.server.LargeServerMessage;
import org.apache.activemq.artemis.core.server.ServerSession;
import org.apache.activemq.artemis.core.server.plugin.ActiveMQServerMessagePlugin;
import org.apache.activemq.artemis.core.transaction.Transaction;
import org.apache.commons.lang3.NotImplementedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;

import com.fasterxml.jackson.core.exc.StreamReadException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DatabindException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * Class for extracting the value to be used as JMSXGroupID
 * from the message.
 */
public class MessageGroupIDAnotator {
	static Logger LOGGER = LoggerFactory.getLogger(MessageGroupIDAnotator.class);
	static ObjectMapper om = new ObjectMapper();
	public static SimpleString MESSAGE_GROUP_ANNOTATION = new SimpleString("JMSXGroupID");
	public static final String GROUP_ID_EXTRACTION_CONFIG_PATH_ENV_VAR = "NEB_IOT_DPP_GROUPID_EXTRACTION_CONFIG_PATH";
	public static final String GROUP_ID_EXTRACTION_CONFIG_MAP_ENV_VAR = "NEB_IOT_DPP_GROUPID_EXTRACTION_CONFIG_MAP";

	/**
	 * Dictionary with groupId extraction parameters. Each key in the dictionary
	 * corresponds to an address, the value is the information on how to extract the
	 * group Id from the messages on that address.
	 */
	Map<String, GroupIDExtractionParameters> groupIdExtractionParameterPerAddress;

	private static Map<String, GroupIDExtractionParameters> readGroupIDExtractionParametersFile(String path) {

		if (path == null) {
			LOGGER.error("readGroupIDExtractionParametersFile. Path is null");
			return  new HashMap<String, GroupIDExtractionParameters>();
		}
		try {
			byte[] fileBytes = Files.readAllBytes(Path.of(path));
			return parseGroupIDExtractionParametersMap(new String(fileBytes));			
			
		} catch (Exception ex) {
			LOGGER.error(String.format(
					"Problem reading grouping extraction parameters from path '%s'", path), ex);
			return new HashMap<String, GroupIDExtractionParameters>();
		}

	}
	
	private static Map<String, GroupIDExtractionParameters> parseGroupIDExtractionParametersMap(String map) {

		try {

			TypeReference<Map<String, GroupIDExtractionParameters>> typeReference = new TypeReference<Map<String, GroupIDExtractionParameters>>() {
			};

			return om.readValue(map.getBytes(), typeReference);

		} catch (Exception ex) {
			LOGGER.error(String.format(
					"Problem reading grouping extraction parameters from JSON '%s'", map
				), ex);
			return new HashMap<String, GroupIDExtractionParameters>();
		}

	} 
	
	

	public MessageGroupIDAnotator() {
		
	}
	
	

	public static MessageGroupIDAnotator fromConfigFilePath(String path) {
		MessageGroupIDAnotator a = new MessageGroupIDAnotator();
		a.groupIdExtractionParameterPerAddress =  MessageGroupIDAnotator.readGroupIDExtractionParametersFile(path);
		return a;
	}
	
	public static MessageGroupIDAnotator fromJSON(String json) {
		MessageGroupIDAnotator a = new MessageGroupIDAnotator();
		a.groupIdExtractionParameterPerAddress =  MessageGroupIDAnotator.parseGroupIDExtractionParametersMap(json);		
		return a;
	}
	
	
	public static  MessageGroupIDAnotator fromProperties(Map<String, String> properties)
	{
		
		
		if(properties.containsKey(GROUP_ID_EXTRACTION_CONFIG_PATH_ENV_VAR) && !properties.get(GROUP_ID_EXTRACTION_CONFIG_PATH_ENV_VAR).isBlank())
		{
			LOGGER.info("Init from config file path: "+properties.get(GROUP_ID_EXTRACTION_CONFIG_PATH_ENV_VAR));
			return MessageGroupIDAnotator.fromConfigFilePath(properties.get(GROUP_ID_EXTRACTION_CONFIG_PATH_ENV_VAR));
		}else
		{
			if(properties.containsKey(GROUP_ID_EXTRACTION_CONFIG_MAP_ENV_VAR) && !properties.get(GROUP_ID_EXTRACTION_CONFIG_MAP_ENV_VAR).isBlank()) {
			
				LOGGER.info("Init from config map: "+properties.get(GROUP_ID_EXTRACTION_CONFIG_MAP_ENV_VAR));
				return MessageGroupIDAnotator.fromJSON(properties.get(GROUP_ID_EXTRACTION_CONFIG_MAP_ENV_VAR));
			}
			else
			{
				LOGGER.error(String.format("Can't init GroupIDAnnotationDivertTransfomer. \"%s\" nor \"%s\"  where provided",GROUP_ID_EXTRACTION_CONFIG_PATH_ENV_VAR,GROUP_ID_EXTRACTION_CONFIG_MAP_ENV_VAR));
				return new MessageGroupIDAnotator();
				
			}
		}
	}
	
	public MessageGroupIDAnotator(Map<String, GroupIDExtractionParameters> groupIdExtractionParameterPerTopic) {
		this.groupIdExtractionParameterPerAddress = groupIdExtractionParameterPerTopic;
	}

	/**
	 * Extracts the string value for an annotation from the message. Returns
	 * defaultValue if annotation is not found or null.
	 * 
	 * @param message
	 * @param annotation
	 * @param defaultValue
	 * @return
	 */
	private String getStringAnnotationValueOrDefault(Message message, SimpleString annotation, String defaultValue) {

		Object annotationValue = message.getAnnotation(annotation);
		if (annotationValue == null)
			return defaultValue;
		if (annotationValue instanceof SimpleString)
			return ((SimpleString) annotationValue).toString();
		return (String) annotationValue;
	}

	

	public void setGroupIDAnnotation(Message message, boolean overrideExisting) {
		String destinationTopic = message.getAddress();
		if (groupIdExtractionParameterPerAddress.containsKey(destinationTopic)) {
			String groupID = getStringAnnotationValueOrDefault(message, MESSAGE_GROUP_ANNOTATION, null);
			if (!overrideExisting && groupID != null) {
				LOGGER.debug("Message already assigned to groupID " + groupID);
				return;
			}
			groupID = extractGroupID(message, groupIdExtractionParameterPerAddress.get(destinationTopic));
			if (groupID != null) {
				LOGGER.debug(String.format("Message %s  assigned group id %s", message.toString(), groupID));
				message.putStringProperty(MESSAGE_GROUP_ANNOTATION, new SimpleString(groupID));

				message.putStringProperty(new SimpleString("_AMQ_GROUP_ID"), new SimpleString(groupID));
			} else {
				LOGGER.warn(String.format("GroupId assigned to message %s is null. Ignoring it", message.toString()));
			}

		} else {
			LOGGER.debug("Ignoring message with address " + destinationTopic);
		}
	}

	public static String extractGroupID(Message message, GroupIDExtractionParameters expression) {

		switch (expression.getSource()) {
		case BODY_JSON:
			return extractGroupIDFromJSONBody(message, expression.getExpression());
		case BODY_XML:
			return extractGroupIdFromXMLBody(message, expression.getExpression());
		case PROPERTY:
			return extractGroupIDFromMessageProperties(message, expression.getExpression());
		default:
			throw new NotImplementedException(
					String.format("Source %s not supported in extractGroupID", expression.getSource()));

		}

	}

	public static String extractGroupIdFromXMLBody(Message message, String xpath) {
		if (message instanceof LargeServerMessage) {
			LOGGER.error("Can't extract group ID from XML body on LargeServerMessages");
			return null;
		}
		String body = getBody(message);
		if (body == null) {
			LOGGER.error(
					String.format("Can't extract group id from XML body on message %s since body is null", message));
		}

		try {
			DocumentBuilderFactory builderFactory = DocumentBuilderFactory.newInstance();
			DocumentBuilder builder = builderFactory.newDocumentBuilder();
			Document xmlDocument = builder.parse(new ByteArrayInputStream(message.getStringBody().getBytes()));
			XPath xPath = XPathFactory.newInstance().newXPath();
			return (String) xPath.compile(xpath).evaluate(xmlDocument, XPathConstants.STRING);
		} catch (Exception ex) {
			LOGGER.error(String.format("Can't extract group id on path %s from XML body of message %s", xpath, message),
					ex);
			return null;
		}

	}
	
	private static String getBody(Message message)
	{
		String body = message.getStringBody();
		if(body==null)
		{
			try {
				ActiveMQBuffer buf = message.toCore().getBodyBuffer();
				
				byte[] data = new byte[buf.writerIndex() - buf.readerIndex()];
				buf.readFully(data);
				body = new String(data);
			} catch (Exception ex) {
				LOGGER.error("cant get body",ex);
				
			}
		}
		return body;
	}

	/**
	 * https://restfulapi.net/json-jsonpath/
	 * 
	 * @param message
	 * @param jsonPath
	 * @return
	 */
	public static String extractGroupIDFromJSONBody(Message message, String jsonPath) {
		if (message instanceof LargeServerMessage) {
			LOGGER.error("Can't extract group id from JSON body on LargeServerMessages");
			return null;
		}
		
		String body = getBody(message);


		if (body == null) {
			LOGGER.error(
					String.format("Can't extract group id from JSON body on message %s since body is null", message));
			return null;
		}

		try {
			return JsonPath.read(body, jsonPath).toString();
		} catch (Exception ex) {
			LOGGER.error(
					String.format("Can't extract group id on path %s from JSON body of message %s", jsonPath, body),
					ex);
			return null;
		}

	}

	public static String extractGroupIDFromMessageProperties(Message message, String sourceProperty) {
		if (!message.getPropertyNames().contains(sourceProperty)) {
			LOGGER.debug(String.format(
					"Can't extract groupID from property '%s' since this property doesn't exist in the message %s ",
					sourceProperty, message.toString()));
			return null;
		}
		return message.getObjectProperty(sourceProperty).toString();
	}

}
