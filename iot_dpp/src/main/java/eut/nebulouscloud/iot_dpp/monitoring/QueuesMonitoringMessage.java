package eut.nebulouscloud.iot_dpp.monitoring;

public class QueuesMonitoringMessage {
	
	public String queue;
	public long messageCount;
	public long maxMessageAge;
	public int consumersCount;
	public int groupCount;
	public QueuesMonitoringMessage(String queue) {
		super();
		this.queue = queue;
		
	}
	
	
	

}
