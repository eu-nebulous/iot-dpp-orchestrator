package eut.nebulouscloud.iot_dpp.monitoring;

import java.util.Date;

import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.Message;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.postoffice.RoutingStatus;
import org.apache.activemq.artemis.core.postoffice.impl.PostOfficeImpl;
import org.apache.activemq.artemis.core.server.LargeServerMessage;
import org.apache.activemq.artemis.core.server.MessageReference;
import org.apache.activemq.artemis.core.server.ServerConsumer;
import org.apache.activemq.artemis.core.server.ServerSession;
import org.apache.activemq.artemis.core.server.impl.AckReason;
import org.apache.activemq.artemis.core.server.impl.ActiveMQServerImpl;
import org.apache.activemq.artemis.core.server.impl.ServerSessionImpl;
import org.apache.activemq.artemis.core.server.plugin.ActiveMQServerMessagePlugin;
import org.apache.activemq.artemis.core.server.plugin.ActiveMQServerPlugin;
import org.apache.activemq.artemis.core.transaction.Transaction;
import org.apache.activemq.artemis.reader.MessageUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;

import eut.nebulouscloud.iot_dpp.monitoring.events.MessageAcknowledgedEvent;
import eut.nebulouscloud.iot_dpp.monitoring.events.MessageDeliveredEvent;
import eut.nebulouscloud.iot_dpp.monitoring.events.MessageLifecycleEvent;
import eut.nebulouscloud.iot_dpp.monitoring.events.MessagePublishedEvent;

/**
 * ActiveMQ Artemis plugin for tracking the lifecycle of messages inside an
 * ActiveMQ cluster. On each step of the message lifecycle (a producer writes a
 * message to the cluster, the message is delivered to a consumer, the consumer
 * ACKs the message), the plugin generates events with relevant information that
 * can be used for understanding how IoT applications components on NebulOuS are
 * communicating.
 */
public abstract class MessageLifecycleMonitoringPlugin implements ActiveMQServerPlugin {

	Logger LOGGER = LoggerFactory.getLogger(MessageLifecycleMonitoringPlugin.class);
	
	public String monitoredTopicPrefix = "";
	
	protected ObjectMapper om = new ObjectMapper();
	/**
	 * Annotation used to identify the ActiveMQ cluster node that first received a
	 * message receiving
	 */
	SimpleString RECEIVING_NODE_ANNOTATION = new SimpleString("NEBULOUS_RECEIVING_NODE");

	/**
	 * Annotation used to track the original id of the message when it was first
	 * received by the cluster.
	 * 
	 */
	SimpleString ORIGINAL_MESSAGE_ID_ANNOTATION = new SimpleString("NEBULOUS_ORIGINAL_MESSAGE_ID");

	/**
	 * Annotation used to mark the moment a message was sent to the client
	 */
	SimpleString DELIVER_TIMESTAMP_ANNOTATION = new SimpleString("NEBULOUS_DELIVER_TIMESTAMP");

	/**
	 * Abstract method called whenever a message event is raised: - a producer
	 * writes a message to the cluster - the message is delivered to a consumer -
	 * the consumer ACKs the message
	 * 
	 * @param event
	 */
	protected abstract void notifyEvent(MessageLifecycleEvent event);

	/**
	 * Retrieves server name from consumer reference.
	 * 
	 * @param consumer
	 * @return
	 */
	private String getServerName(ServerConsumer consumer) {
		// TODO: Find a better way of retrieving server name rather than parsing the
		// string representation of the consumer object.
		return consumer.toString().split("server=ActiveMQServerImpl::name=")[1].split("]")[0];
	}

	/**
	 * Retrieves the estimated message size.
	 * 
	 * @param message
	 * @return
	 * @throws ActiveMQException
	 */
	private long getEstimatedMessageSize(Message message) throws ActiveMQException {
		if (message instanceof LargeServerMessage)
			return ((LargeServerMessage) message).getLargeBody().getBodySize();
		return message.getEncodeSize();
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

	private Long getLongAnnotationValueOrDefault(Message message, SimpleString annotation, Long defaultValue) {

		Object annotationValue = message.getAnnotation(annotation);
		if (annotationValue == null)
			return defaultValue;
		return (Long) annotationValue;
	}

	/**
	 * Constructs a MessagePublishedMonitoringEvent from a Message reference
	 * 
	 * @param message
	 * @return
	 * @throws Exception
	 */
	private MessagePublishedEvent buildPublishEvent(Message message) throws Exception {
		String clientId = message.getStringProperty(MessageUtil.CONNECTION_ID_PROPERTY_NAME);
		long size = getEstimatedMessageSize(message);
		String sourceNode = getStringAnnotationValueOrDefault(message, RECEIVING_NODE_ANNOTATION, null);
		long messageId = getLongAnnotationValueOrDefault(message, ORIGINAL_MESSAGE_ID_ANNOTATION, -1l);
		return new MessagePublishedEvent(messageId, message.getAddress(), sourceNode, clientId, size,
				message.getTimestamp());
	}

	/**
	 * Constructs a MessageDeliveredMonitoringEvent from a Message and consumer
	 * reference
	 * 
	 * @param message
	 * @param consumer
	 * @return
	 * @throws Exception
	 */
	private MessageDeliveredEvent buildDeliverEvent(Message message, ServerConsumer consumer)
			throws Exception {
		MessagePublishedEvent publishEvent = buildPublishEvent(message);
		String nodeName = getServerName(consumer);
		Long deliveryTimeStamp = getLongAnnotationValueOrDefault(message, DELIVER_TIMESTAMP_ANNOTATION, -1l);
		return new MessageDeliveredEvent(publishEvent, consumer.getQueueAddress().toString(), nodeName,
				consumer.getConnectionClientID(), deliveryTimeStamp);
	}

	

	@Override
	public void afterSend(ServerSession session, Transaction tx, Message message, boolean direct,
			boolean noAutoCreateQueue, RoutingStatus result) throws ActiveMQException {
		try {

			
			if (!message.getAddress().toString().matches(monitoredTopicPrefix)) {
				LOGGER.trace("Ignoring "+message.getAddress().toString()+" Not matching with "+monitoredTopicPrefix);
				return;
			}	
			/**
			 * If message is already annotated with any of our custom annotations means that
			 * the message is not being sent by a ActiveMQ external client (it is due to
			 * intra-cluster communication).
			 * Ignore it
			 */
			if (message.getAnnotation(RECEIVING_NODE_ANNOTATION) != null
					|| message.getAnnotation(ORIGINAL_MESSAGE_ID_ANNOTATION) != null) {
				return;
			}
			
			String nodeName = ((ActiveMQServerImpl) ((PostOfficeImpl) ((ServerSessionImpl) session).postOffice).getServer())
					.getConfiguration().getName();
			if (message.getAnnotation(RECEIVING_NODE_ANNOTATION) == null)
				message.setAnnotation(RECEIVING_NODE_ANNOTATION, nodeName);

			if (message.getAnnotation(ORIGINAL_MESSAGE_ID_ANNOTATION) == null)
				message.setAnnotation(ORIGINAL_MESSAGE_ID_ANNOTATION, message.getMessageID());
			
			MessageLifecycleEvent event = buildPublishEvent(message);
			LOGGER.debug("MessagePublishedMonitoringEvent: " + om.writeValueAsString(event));
			notifyEvent(event);
		} catch (Exception e) {
			LOGGER.error("afterSend failed", e);
		}

	}

	/**
	 * Anotates the message with the current time as value for
	 * DELIVER_TIMESTAMP_ANNOTATION
	 */
	@Override
	public void beforeDeliver(ServerConsumer consumer, MessageReference reference) throws ActiveMQException {
		reference.getMessage().setAnnotation(DELIVER_TIMESTAMP_ANNOTATION, new Date().getTime());
	}

	@Override
	public void afterDeliver(ServerConsumer consumer, MessageReference reference) throws ActiveMQException {

		try {


			if (!reference.getQueue().getAddress().toString().matches(monitoredTopicPrefix)) {
				LOGGER.trace("Ignoring "+reference.getQueue().getAddress().toString()+" Not matching with "+monitoredTopicPrefix);
				return;
			}

			Message message = reference.getMessage();

			MessageDeliveredEvent deliverEvent = buildDeliverEvent(message, consumer);
			LOGGER.debug("MessageDeliveredMonitoringEvent: " + om.writeValueAsString(deliverEvent));
			notifyEvent(deliverEvent);

		} catch (Exception e) {
			LOGGER.error("afterDeliver failed", e);
		}
	}

	@Override
	public void messageAcknowledged(Transaction tx, MessageReference reference, AckReason reason,
			ServerConsumer consumer) throws ActiveMQException {

		try {

			if (!reference.getQueue().getAddress().toString().matches(monitoredTopicPrefix)) {
				LOGGER.trace("Ignoring "+reference.getQueue().getAddress().toString()+" Not matching with "+monitoredTopicPrefix);
				return;
			}


			Message message = reference.getMessage();
			MessageDeliveredEvent deliverEvent = buildDeliverEvent(message, consumer);
			MessageAcknowledgedEvent ackEvent = new MessageAcknowledgedEvent(deliverEvent,
					new Date().getTime());
			LOGGER.debug("MessageAcknowledgedMonitoringEvent: " + om.writeValueAsString(ackEvent));
			notifyEvent(ackEvent);
		} catch (Exception e) {
			LOGGER.error("messageAcknowledged failed", e);
		}
	}

}
