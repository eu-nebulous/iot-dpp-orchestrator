package eut.nebulouscloud.iot_dpp;

public class MessageReceptionRecord {
	public String consumerId;
	public String queue;
	public TestMessage payload;

	public MessageReceptionRecord(String consumerId, String queue, TestMessage payload) {
		super();
		this.consumerId = consumerId;
		this.queue = queue;
		this.payload = payload;
	}

}
