package eut.nebulouscloud.iot_dpp.monitoring;

import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import eut.nebulouscloud.iot_dpp.monitoring.QueuesMonitoringPlugin.QueuesMonitoringPluginConsumer;

public class EMSQueuesMonitoringPlugin extends QueuesMonitoringPlugin{
	static Logger LOGGER = LoggerFactory.getLogger(EMSQueuesMonitoringPlugin.class);

	@Override
	public void init(Map<String, String> properties) {
		String topicPrefix = Optional.ofNullable(properties.getOrDefault("topic_prefix", null))
				.orElseThrow(() -> new IllegalStateException("topic_prefix parameter is not defined"));
		int QUERY_INTERVAL_MS = Integer.parseInt(properties.getOrDefault("query_interval_seconds", "3")) * 1000;
		String activemqURL = properties.getOrDefault("local_activemq_url", "tcp://localhost:61616");
		String activemqUser = properties.getOrDefault("local_activemq_user", "artemis");
		String activemqPassword = properties.getOrDefault("local_activemq_password", "artemis");

		String emsURL = properties.getOrDefault("ems_url", "tcp://localhost:61616");
		String emsUser = properties.getOrDefault("ems_user", "aaa");
		String emsPassword = properties.getOrDefault("ems_password", "111");
		
		
		LOGGER.info("Init EMSQueuesMonitoringPlugin with parameters:");
		LOGGER.info("topicPrefix: "+topicPrefix);
		LOGGER.info("query_interval_ms: "+QUERY_INTERVAL_MS);
		LOGGER.info("activemqURL: "+activemqURL);
		LOGGER.info("activemqUser: "+activemqUser);
		LOGGER.info("emsURL: "+emsURL);
		LOGGER.info("emsUser: "+emsUser);
		
		
		
		EventManagementSystemPublisher publisher = new EventManagementSystemPublisher(emsURL, emsUser, emsPassword);
		QueuesMonitoringPluginConsumer consumer = new EMSQueuesMonitoringPluginConsumer(publisher);
		process = new QueuesMonitoringProcess(topicPrefix, QUERY_INTERVAL_MS, activemqURL, activemqUser,
				activemqPassword, consumer);
		new Thread(process).start();

	}
}
