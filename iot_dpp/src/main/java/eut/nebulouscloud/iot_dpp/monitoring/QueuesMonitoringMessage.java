package eut.nebulouscloud.iot_dpp.monitoring;

import java.util.Date;

public class QueuesMonitoringMessage {
	
	public String queue;
	public Date time;
	public long messageCount;
	public long maxMessageAge;
	public int consumersCount;
	public int groupCount;
	public double messagesAddedFrequency;
	public long messagesAdded;
	public QueuesMonitoringMessage(String queue) {
		super();
		this.queue = queue;
		
	}
	
	
	

}
