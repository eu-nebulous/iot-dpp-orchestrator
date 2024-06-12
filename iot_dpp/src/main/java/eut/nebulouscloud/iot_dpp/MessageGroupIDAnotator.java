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

import com.fasterxml.jackson.core.type.TypeReference;
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

	public static String GROUP_ID_EXTRACTION_CONFIG_PATH_ENV_VAR = "NEB_IOT_DPP_GROUPID_EXTRACTION_CONFIG_PATH";

	/**
	 * Dictionary with groupId extraction parameters. Each key in the dictionary
	 * corresponds to an address, the value is the information on how to extract the
	 * group Id from the messages on that address.
	 */
	Map<String, GroupIDExtractionParameters> groupIdExtractionParameterPerAddress;

	public void readGroupIDExtractionParametersFile(String path) {
		if (path == null) {
			LOGGER.error(String.format(
					"'%s' environment variable with a JSON object containing the grouping extraction parameters was not provided.",
					GROUP_ID_EXTRACTION_CONFIG_PATH_ENV_VAR));
			groupIdExtractionParameterPerAddress = new HashMap<String, GroupIDExtractionParameters>();
			return;
		}
		try {
			byte[] fileBytes = Files.readAllBytes(Path.of(path));

			TypeReference<Map<String, GroupIDExtractionParameters>> typeReference = new TypeReference<Map<String, GroupIDExtractionParameters>>() {
			};

			groupIdExtractionParameterPerAddress = om.readValue(fileBytes, typeReference);

		} catch (Exception ex) {
			LOGGER.error(String.format(
					"Problem reading grouping extraction parameters from path '%s' provided by env var '%s'", path,
					GROUP_ID_EXTRACTION_CONFIG_PATH_ENV_VAR), ex);
			groupIdExtractionParameterPerAddress = new HashMap<String, GroupIDExtractionParameters>();
		}

	}

	public MessageGroupIDAnotator() {
		readGroupIDExtractionParametersFile(System.getenv(GROUP_ID_EXTRACTION_CONFIG_PATH_ENV_VAR));
	}

	public MessageGroupIDAnotator(String path) {
		readGroupIDExtractionParametersFile(path);
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
		String body = message.getStringBody();
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

		String body = message.getStringBody();
		/*try {
			ActiveMQBuffer buf = message.toCore().getBodyBuffer();
			
			byte[] data = new byte[buf.writerIndex() - buf.readerIndex()];
			buf.readFully(data);
			body = new String(data);
		} catch (Exception ex) {
			LOGGER.error(
					String.format("Can't extract group id from JSON body on message %s since body is null", message),
					ex);
			return null;
		}*/

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
