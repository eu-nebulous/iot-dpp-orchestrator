package eut.nebulouscloud.iot_dpp;

import java.io.ByteArrayInputStream;
import java.util.Map;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;

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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;

/**
 * ActiveMQ Artemis plugin for extracting the value to be used as JMSXGroupID from the message.
 */
public class MessageGroupIDAnnotationPlugin implements ActiveMQServerMessagePlugin {
	static Logger LOGGER = LoggerFactory.getLogger(MessageGroupIDAnnotationPlugin.class);
	static ObjectMapper om = new ObjectMapper();
	public static SimpleString MESSAGE_GROUP_ANNOTATION = new SimpleString("JMSXGroupID");

	/**
	 * Dictionary with groupId extraction parameters.
	 * Each key in the dictionary corresponds to an address, the value is the information on how to extract the group Id from the messages on that address.
	 */
	Map<String, GroupIDExtractionParameters> groupIdExtractionParameterPerAddress;
	
	
	public MessageGroupIDAnnotationPlugin()
	{
		
	}
	
	public MessageGroupIDAnnotationPlugin(Map<String, GroupIDExtractionParameters> groupIdExtractionParameterPerTopic)
	{
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

	@Override
	public void beforeSend(ServerSession session, Transaction tx, Message message, boolean direct,
			boolean noAutoCreateQueue) {

		String destinationTopic = message.getAddress();
		if (groupIdExtractionParameterPerAddress.containsKey(destinationTopic)) {
			String groupID = getStringAnnotationValueOrDefault(message, MESSAGE_GROUP_ANNOTATION, null);
			if (groupID != null) {
				LOGGER.debug("Message already assigned to groupID " + groupID);
				return;
			}			
			groupID = extractGroupID(message, groupIdExtractionParameterPerAddress.get(destinationTopic));
			if (groupID != null) {
				LOGGER.debug(String.format("Message %s  assigned group id %s", message.toString(), groupID));
				message.putStringProperty(MESSAGE_GROUP_ANNOTATION, new SimpleString(groupID));
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
			return extractGroupIdFromXMLBody(message,expression.getExpression());			
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
			Document xmlDocument = builder.parse( new ByteArrayInputStream(message.getStringBody().getBytes()));
			XPath xPath = XPathFactory.newInstance().newXPath();
			return (String) xPath.compile(xpath).evaluate(xmlDocument, XPathConstants.STRING);
		} catch (Exception ex) {
			LOGGER.error(
					String.format("Can't extract group id on path %s from XML body of message %s", xpath, message),
					ex);
			return null;
		}

	}

	/**
	 * https://restfulapi.net/json-jsonpath/
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
		if (body == null) {
			LOGGER.error(
					String.format("Can't extract group id from JSON body on message %s since body is null", message));
		}

		try {
			return JsonPath.read(body, jsonPath).toString();
		} catch (Exception ex) {
			LOGGER.error(
					String.format("Can't extract group id on path %s from JSON body of message %s", jsonPath, message),
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
