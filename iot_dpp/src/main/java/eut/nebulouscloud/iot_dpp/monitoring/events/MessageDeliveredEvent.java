package eut.nebulouscloud.iot_dpp.monitoring.events;

/**
 * Event generated when a message is delivered to a client of the the pub/sub
 * system
 */
public class MessageDeliveredEvent extends MessageLifecycleEvent {

	/**
	 * Node where the client is connected
	 */
	public final String node;

	/**
	 * Id of the client recieving the message
	 */
	public final String clientId;

	/**
	 * Node where the message was originaly published
	 */
	public final String publishNode;

	/**
	 * Addres where the message was originally published
	 */
	public final String publishQueue;

	/**
	 * 
	 * /** Id of the client that originally published the message
	 */
	public final String publishClientId;

	/**
	 * Time when the message was published (milliseconds since epoch).
	 */
	public final long publishTimestamp;

	public MessageDeliveredEvent(MessagePublishedEvent publishEvent, String messageQueue, String node,
			String clientId, long timestamp) {
		super(publishEvent.messageId, messageQueue, publishEvent.messageSize, timestamp);
		this.node = node;
		this.clientId = clientId;
		this.publishQueue = publishEvent.messageQueue;
		this.publishNode = publishEvent.node;
		this.publishClientId = publishEvent.clientId;
		this.publishTimestamp = publishEvent.timestamp;
	}



}
