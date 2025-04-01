package eut.nebulouscloud.iot_dpp.monitoring;

import java.util.Map;
import java.util.Optional;

import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import eut.nebulouscloud.iot_dpp.monitoring.QueuesMonitoringPlugin.QueuesMonitoringPluginConsumer;

public class EMSQueuesMonitoringPlugin extends QueuesMonitoringPlugin {
	static Logger LOGGER = LoggerFactory.getLogger(EMSQueuesMonitoringPlugin.class);
	Map<String, String> properties;

	@Override
	public void init(Map<String, String> properties) {
		LOGGER.info("LoggerFactory init");
		this.properties = properties;

	}
	private ActiveMQServer server;

	@Override
	public void registered(ActiveMQServer server) {
		LOGGER.info("LoggerFactory registered");
		this.server = server;
		String monitoredQueueRegex = Optional.ofNullable(properties.getOrDefault("monitored_queue_regex", null))
				.orElseThrow(() -> new IllegalStateException("monitored_queue_regex parameter is not defined"));
		String reportingTopicPrefix = properties.getOrDefault("reporting_topic_prefix", "monitoring");
		int QUERY_INTERVAL_MS = Integer.parseInt(properties.getOrDefault("query_interval_seconds", "3")) * 1000;
	
		String emsURL = PluginPropertiesUtils.getEMSUrl(properties);

		String emsUser = Optional.ofNullable(properties.getOrDefault("ems_user", null))
				.orElseThrow(() -> new IllegalStateException("ems_user parameter is not defined"));
		String emsPassword = Optional.ofNullable(properties.getOrDefault("ems_password", null))
				.orElseThrow(() -> new IllegalStateException("ems_password parameter is not defined"));

		LOGGER.info("Init EMSQueuesMonitoringPlugin with parameters:");
		LOGGER.info("monitoredQueueRegex: " + monitoredQueueRegex);
		LOGGER.info("reportingTopicPrefix: " + reportingTopicPrefix);
		LOGGER.info("query_interval_ms: " + QUERY_INTERVAL_MS);		
		LOGGER.info("emsURL: " + emsURL);
		LOGGER.info("emsUser: " + emsUser);

		EventManagementSystemPublisher publisher = new EventManagementSystemPublisher(emsURL, emsUser, emsPassword,
				reportingTopicPrefix);
		QueuesMonitoringPluginConsumer consumer = new EMSQueuesMonitoringPluginConsumer(publisher);
		process = new QueuesMonitoringProcess(server, monitoredQueueRegex, QUERY_INTERVAL_MS , consumer);
		new Thread(process).start();
	}

}
