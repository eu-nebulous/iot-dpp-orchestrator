package eut.nebulouscloud.iot_dpp.monitoring.events;

/**
 * Event generated when a message is published to the pub/sub system
 */
public class MessagePublishedEvent extends MessageLifecycleEvent{	

	/**
	 * The name of the pub/sub cluster node where the message was published
	 */
	public final String node;
	
	/**
	 * The Id of the client that published the message
	 */
	public final String clientId;

	public MessagePublishedEvent(long messageId, String messageQueue, String node, String clientId, long size,long timestamp) {		
		super(messageId,messageQueue,size,timestamp);
		this.node = node;
		this.clientId = clientId;
	}
	
}
