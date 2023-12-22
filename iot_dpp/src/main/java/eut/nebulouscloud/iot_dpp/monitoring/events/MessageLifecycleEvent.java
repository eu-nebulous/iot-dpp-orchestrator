package eut.nebulouscloud.iot_dpp.monitoring.events;

/**
 * A base class for modelling events related to messages
 */
public abstract class MessageLifecycleEvent {
	/**
	 * Id of the message
	 */
	public final long messageId;
	
	/**
	 * Timestamp when the event occurred 
	 */
	public final long timestamp;
	
	/**
	 * Size of the message (in bytes)
	 */
	public final long messageSize;
	
	
	public final String messageAddress;
	
	
	public MessageLifecycleEvent(long messageId,String messageAddress,long size, long timestamp)
	{
		this.timestamp = timestamp;
		this.messageAddress = messageAddress;
		this.messageId = messageId;
		this.messageSize = size;
		
	}
	

}
