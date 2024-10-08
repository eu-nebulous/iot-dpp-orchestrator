package eut.nebulouscloud.iot_dpp.monitoring;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.Message;
import org.apache.activemq.artemis.core.postoffice.RoutingStatus;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.RoutingContext;
import org.apache.activemq.artemis.core.server.ServerSession;
import org.apache.activemq.artemis.core.server.metrics.plugins.LoggingMetricsPlugin;
import org.apache.activemq.artemis.core.server.plugin.ActiveMQServerMessagePlugin;
import org.apache.activemq.artemis.core.server.plugin.ActiveMQServerPlugin;
import org.apache.activemq.artemis.core.transaction.Transaction;
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
public class QueuesMonitoringPlugin implements ActiveMQServerPlugin {

	public QueuesMonitoringProcess process;

	Logger LOGGER = LoggerFactory.getLogger(QueuesMonitoringPlugin.class);

	Map<String, String> properties;
	String monitoredTopicPrefix;

	@Override
	public void init(Map<String, String> properties) {
		this.properties = properties;
	}

	@Override
	public void registered(ActiveMQServer server) {

		LOGGER.info("QueuesMonitoringPlugin registered");
		monitoredTopicPrefix = Optional.ofNullable(properties.getOrDefault("monitored_topic_prefix", null))
				.orElseThrow(() -> new IllegalStateException("monitored_topic_prefix parameter is not defined"));
		int QUERY_INTERVAL_MS = Integer.parseInt(properties.getOrDefault("query_interval_seconds", "3")) * 1000;
		String activemqURL = properties.getOrDefault("local_activemq_url", "tcp://localhost:61616");

		String activemqUser = Optional.ofNullable(properties.getOrDefault("local_activemq_user", null))
				.orElseThrow(() -> new IllegalStateException("local_activemq_user parameter is not defined"));
		String activemqPassword = Optional.ofNullable(properties.getOrDefault("local_activemq_user", null))
				.orElseThrow(() -> new IllegalStateException("local_activemq_password parameter is not defined"));

		process = new QueuesMonitoringProcess(server, monitoredTopicPrefix, QUERY_INTERVAL_MS, activemqURL,
				activemqUser, activemqPassword, null);
		new Thread(process).start();

	}


	public interface QueuesMonitoringPluginConsumer {
		void consume(List<QueuesMonitoringMessage> messages);

	}

}
