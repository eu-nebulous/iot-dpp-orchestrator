package eut.nebulouscloud.iot_dpp.monitoring;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.apache.activemq.artemis.core.server.plugin.ActiveMQServerMessagePlugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import eut.nebulouscloud.iot_dpp.monitoring.QueuesMonitoringPlugin.QueuesMonitoringPluginConsumer;

/**
 * Apache Artemis plugin that periodically collects usage metrics from the
 * queues of the broker (messages_count, max_message_age, consumers_count,
 * group_count) messages_count: The number of pending messages for any queue.
 * max_message_age: Age of the oldest pending message on a given queue.
 * consumers_count: The number of active consumers subscribed to a queue.
 * group_count: The number of message groupings for a queue. Messages on a queue
 * are grouped by the value of the “JMSXGroupID” attribute associated to each
 * message
 */
public class QueuesMonitoringPlugin implements ActiveMQServerMessagePlugin {
	
	public QueuesMonitoringProcess process;

	Logger LOGGER = LoggerFactory.getLogger(QueuesMonitoringPlugin.class);

	@Override
	public void init(Map<String, String> properties) {
		String topicPrefix = Optional.ofNullable(properties.getOrDefault("topic_prefix", null))
				.orElseThrow(() -> new IllegalStateException("topic_prefix parameter is not defined"));
		int QUERY_INTERVAL_MS = Integer.parseInt(properties.getOrDefault("query_interval_seconds", "3")) * 1000;
		String activemqURL = properties.getOrDefault("local_activemq_url", "tcp://localhost:61616");
		String activemqUser = properties.getOrDefault("local_activemq_user", "artemis");
		String activemqPassword = properties.getOrDefault("local_activemq_password", "artemis");

		process = new QueuesMonitoringProcess(topicPrefix, QUERY_INTERVAL_MS, activemqURL, activemqUser,
				activemqPassword, null);
		new Thread(process).start();

	}

	

	public interface QueuesMonitoringPluginConsumer {
		void consume(List<QueuesMonitoringMessage> messages);

	}

}
