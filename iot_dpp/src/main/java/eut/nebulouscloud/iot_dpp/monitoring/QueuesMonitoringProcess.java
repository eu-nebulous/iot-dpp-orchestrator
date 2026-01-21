package eut.nebulouscloud.iot_dpp.monitoring;

import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.stream.StreamSupport;

import javax.naming.InitialContext;

import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.MessageReference;
import org.apache.activemq.artemis.core.server.ServerConsumer;
import org.apache.commons.lang3.mutable.MutableLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import eut.nebulouscloud.iot_dpp.monitoring.QueuesMonitoringPlugin.QueuesMonitoringPluginConsumer;
import io.jsonwebtoken.lang.Arrays;

public class QueuesMonitoringProcess implements Runnable {
	Logger LOGGER = LoggerFactory.getLogger(QueuesMonitoringProcess.class);
	InitialContext initialContext = null;
	public QueuesMonitoringPluginConsumer consumer;
	final ActiveMQServer server;
	final String monitoredQueueRegex;
	final int queryIntervalMS;

	public QueuesMonitoringProcess(ActiveMQServer server, String monitoredQueueRegex, int queryIntervalMS,
			QueuesMonitoringPluginConsumer consumer) {
		this.server = server;

		this.monitoredQueueRegex = monitoredQueueRegex;
		this.queryIntervalMS = queryIntervalMS;
		this.consumer = consumer;
	}


	private List<String> getQueuesNames() throws Exception {
		return Arrays.asList(server.getActiveMQServerControl().getQueueNames());
	}


	private long getMessageCount(String queue) {
		try {
			return server.queueQuery(SimpleString.of(queue)).getMessageCount();
		} catch (Exception e) {
			LOGGER.error("Can't get getMessageCount for queue {}",queue,e);
			return -1;
		}
	
	}

	private long getMessagesAdded(String queue) {		
		try {
		return server.locateQueue(queue).getMessagesAdded();
		} catch (Exception e) {
			LOGGER.error("Can't get getMessagesAdded for queue {}",queue,e);
			return -1;
		}
	}


	private long getOldestMessageAge(String queue) {

		long a = server.locateQueue(queue).getDeliveringMessages().values().stream().map((List<MessageReference> e)->{return e.size()!=0?e.get(0).getMessage().getTimestamp():Long.MAX_VALUE;}).max(Long::compare).orElse(Long.MAX_VALUE);
		long b = server.locateQueue(queue).getScheduledMessages().stream().map(m->m.getMessage().getTimestamp()).max(Long::compare).orElse(Long.MAX_VALUE);
		MutableLong c = new MutableLong(Long.MAX_VALUE);
		server.locateQueue(queue).forEach((m)->{if(m.getMessage().getTimestamp()<c.getValue()) {c.setValue(m.getMessage().getTimestamp());}});
		return System.currentTimeMillis() - Math.min(Math.min(a,b),c.getValue());
	}

	private int getConsumersCount(String queue) {
		try {
			return server.queueQuery(SimpleString.of(queue)).getConsumerCount();
		} catch (Exception e) {
			LOGGER.error("Can't get getConsumerCount for queue {}",queue,e);
			return -1;
		}
	}

	private int getGroupCount(String queue) {
		try {
			return server.queueQuery(SimpleString.of(queue)).getConsumerCount();
		} catch (Exception e) {
			LOGGER.error("Can't get getGroupCount for queue {}",queue,e);
			return -1;
		}
	}

	Map<String, QueuesMonitoringMessage> previousMetrics = new HashMap<String, QueuesMonitoringMessage>();

	private List<QueuesMonitoringMessage> collectMetrics() throws Exception {

		List<QueuesMonitoringMessage> ret = new LinkedList<QueuesMonitoringMessage>();
		List<String> queues = getQueuesNames();

		for (String queue : queues) {
			if(queue.startsWith("$sys")) continue;
			QueuesMonitoringMessage message = new QueuesMonitoringMessage(queue);
			message.time = new Date();
			message.messageCount = getMessageCount(queue);
			message.consumersCount = getConsumersCount(queue);
			message.groupCount = getGroupCount(queue);
			if (message.messageCount > 0) {
				message.maxMessageAge = getOldestMessageAge(queue);
			}

			message.messagesAdded = getMessagesAdded(queue);

			if (previousMetrics.containsKey(queue)) {
				long previousMessagesAdded = previousMetrics.get(queue).messagesAdded;
				message.messagesAddedFrequency = (message.messagesAdded - previousMessagesAdded)
						/ ((message.time.getTime() - previousMetrics.get(queue).time.getTime()) / 1000.0);

			} else {
				message.messagesAddedFrequency = 0.0;
			}
			previousMetrics.put(queue, message);
			LOGGER.info("Collected metrics for queue {}:{}", queue, message);
			ret.add(message);
		}
		return ret;
	}

	@Override
	public void run() {

		while (server != null && !server.isActive()) {
			LOGGER.info("Wait for server activation");

			try {
				Thread.sleep(1000);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}

		System.setProperty("java.naming.factory.initial",
				"org.apache.activemq.artemis.jndi.ActiveMQInitialContextFactory");
		LOGGER.info("QueuesMonitoringProcess start");
		while (true) {
			try {
				// server.getMetricsManager().getMeterRegistry().getMeters()
				List<QueuesMonitoringMessage> metrics = collectMetrics();
				if (consumer != null) {
					consumer.consume(metrics);
				} else {
					LOGGER.warn("QueuesMonitoringProcess doesn't have a consumer to consume collected metrics");
				}

				LOGGER.info("Waiting {} for next round", queryIntervalMS);
				Thread.sleep(queryIntervalMS);

			} catch (Exception ex) {
				LOGGER.error("", ex);
				try {
					Thread.sleep(queryIntervalMS);
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}

		}

	}

}
