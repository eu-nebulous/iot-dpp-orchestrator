package eut.nebulouscloud.iot_dpp.monitoring;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EventManagementSystemPublisher {

	Logger LOGGER = LoggerFactory.getLogger(EventManagementSystemPublisher.class);

	private static EventManagementSystemPublisher instance;

	private EventManagementSystemPublisher() {

	}

	public static void send(String address, String payload) {
		if (instance == null)
			instance = new EventManagementSystemPublisher();
		
		//TODO: implement actual sending of the message to the EMS using the EXN ActiveMQ library

	}
}
