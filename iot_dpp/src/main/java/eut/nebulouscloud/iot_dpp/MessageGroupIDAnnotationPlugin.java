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
 * ActiveMQ Artemis plugin that sets, for any incoming message, the appropariate value for the JMSXGroupID according to the configuration.
 */
public class MessageGroupIDAnnotationPlugin implements ActiveMQServerMessagePlugin {
	static Logger LOGGER = LoggerFactory.getLogger(MessageGroupIDAnnotationPlugin.class);

	MessageGroupIDAnotator annotator;

	public MessageGroupIDAnnotationPlugin() {
		annotator = new MessageGroupIDAnotator();
	}

	public MessageGroupIDAnnotationPlugin(String path) {
		annotator = new MessageGroupIDAnotator(path);
	}

	public MessageGroupIDAnnotationPlugin(Map<String, GroupIDExtractionParameters> groupIdExtractionParameterPerTopic) {
		annotator = new MessageGroupIDAnotator(groupIdExtractionParameterPerTopic);
	}

	@Override
	public void beforeSend(ServerSession session, Transaction tx, Message message, boolean direct,
			boolean noAutoCreateQueue) {
		annotator.setGroupIDAnnotation(message, false);
	}

}
