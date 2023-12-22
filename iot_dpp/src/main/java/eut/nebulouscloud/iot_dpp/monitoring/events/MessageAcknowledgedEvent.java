package eut.nebulouscloud.iot_dpp.monitoring.events;

/**
 * Event generated when a message is acknowledged by a client of the the pub/sub
 * system
 */
public class MessageAcknowledgedEvent extends MessageLifecycleEvent {

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
	 * Id of the client that originally published the message
	 */
	public final String publishClientId;

	/**
	 * Addres where the message was originally published
	 */
	public final String publishAddress;

	/**
	 * Time when the message was published (milliseconds since epoch).
	 */
	public final long publishTimestamp;

	/**
	 * Time when the message was delivered to the client.
	 */
	public final long deliverTimestamp;

	public MessageAcknowledgedEvent(MessageDeliveredEvent deliverEvent,long timestamp) {
		super(deliverEvent.messageId, deliverEvent.messageAddress, deliverEvent.messageSize, timestamp);
		this.node = deliverEvent.node;
		this.clientId = deliverEvent.clientId;
		this.publishAddress = deliverEvent.messageAddress;
		this.publishNode = deliverEvent.publishNode;
		this.publishClientId = deliverEvent.publishClientId;
		this.publishTimestamp = deliverEvent.publishTimestamp;
		this.deliverTimestamp = deliverEvent.timestamp;
	}


}
