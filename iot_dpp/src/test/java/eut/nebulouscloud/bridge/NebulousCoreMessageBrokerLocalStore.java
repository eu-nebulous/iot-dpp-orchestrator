package eut.nebulouscloud.bridge;

import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.IntStream;

import org.apache.qpid.protonj2.client.Message;
import org.apache.qpid.protonj2.client.exceptions.ClientException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import eu.nebulouscloud.exn.core.Context;

/***
 * Class for facilitating the interaction with the NebulOuS message broker with
 * connection parameters host:"localhost", port: 5672, user: "admin",
 * password:"admin". Implements several functions to send messages to mimic the
 * behaviour of certain NebulOuS components. Registers any received message and
 * offers methods to query them.
 */
public class NebulousCoreMessageBrokerLocalStore {
	static Logger LOGGER = LoggerFactory.getLogger(NebulousCoreMessageBrokerLocalStore.class);
	protected ObjectMapper om = new ObjectMapper();

	private List<NebulOuSCoreMessage> messages = Collections.synchronizedList(new LinkedList<NebulOuSCoreMessage>());

	public NebulousCoreMessageBrokerLocalStore() {
	}

	/**
	 * Waits for a message that matches the given predicate to appear and returns it
	 * (if found). If timeout is reached without the message being recieved, returns
	 * an empty optional.
	 * 
	 * @param appId          the app Id to filter by. If null, no filtering occurs
	 *                       by appId
	 * @param topic          the topic to filter by. If null, no filtering occurs by
	 *                       topic
	 * @param predicate      The search predicate. If null, it is not used.
	 * @param timeoutSeconds The maximum timeout to wait for a message with the
	 *                       given predicate to be found in the list (in seconds).
	 *                       It must be a positive integer or 0.
	 * @return An optional with the first message that matchs the predicate if any
	 *         found.
	 */
	public Optional<NebulOuSCoreMessage> findFirst(String appId, String topic, Predicate<NebulOuSCoreMessage> predicate,
			int timeoutSeconds) {
		Optional<NebulOuSCoreMessage> result = Optional.empty();
		long timeout = new Date().getTime() + (timeoutSeconds * 1000);
		Predicate<NebulOuSCoreMessage> finalPredicate = predicate != null
				? messagesFromAppAndTopic(appId, topic).and(predicate)
				: messagesFromAppAndTopic(appId, topic);
		do {
			synchronized (messages) {
				result = messages.stream().filter(finalPredicate).findFirst();
			}
			if (result.isEmpty() && new Date().getTime() < timeout) {
				LOGGER.trace(String.format("Waiting for message. %.2fs left for timeout.",
						((timeout - new Date().getTime()) / 1000.0)));
				try {
					Thread.sleep(1000);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
		} while (result.isEmpty() && new Date().getTime() < timeout);
		if (new Date().getTime() > timeout) {
			LOGGER.error("Timeout waiting for a message");
		}
		return result;

	};

	/**
	 * Same as findFirst but reversed
	 * 
	 * @param appId
	 * @param topic
	 * @param predicate
	 * @param timeoutSeconds
	 * @return
	 */
	public Optional<NebulOuSCoreMessage> findLast(String appId, String topic, Predicate<NebulOuSCoreMessage> predicate,
			int timeoutSeconds) {
		Optional<NebulOuSCoreMessage> result = Optional.empty();
		long timeout = new Date().getTime() + (timeoutSeconds * 1000);
		Predicate<NebulOuSCoreMessage> finalPredicate = predicate != null
				? messagesFromAppAndTopic(appId, topic).and(predicate)
				: messagesFromAppAndTopic(appId, topic);
		do {
			synchronized (messages) {
				Object[] temp = messages.toArray();
				result = ((Collection<NebulOuSCoreMessage>) IntStream.range(0, temp.length)
						.mapToObj(i -> temp[temp.length - i - 1])).stream().filter(finalPredicate).findFirst();
			}
			if (result.isEmpty() && new Date().getTime() < timeout) {
				LOGGER.error(String.format("Waiting for message. %.2fs left for timeout.",
						((timeout - new Date().getTime()) / 1000.0)));
				try {
					Thread.sleep(1000);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
		} while (result.isEmpty() && new Date().getTime() < timeout);
		if (new Date().getTime() > timeout) {
			LOGGER.error("Timeout waiting for a message");
		}
		return result;

	};

	public void onMessage(String key, String address, Map body, Message message, Context context) {
		String to = "??";
		try {
			to = message.to() != null ? message.to() : address;
		} catch (Exception ex) {
			ex.printStackTrace();
		}
		Map<Object, Object> props = new HashMap<Object, Object>();

		try {
			message.forEachProperty((k, v) -> props.put(k, v));
		} catch (ClientException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		String subject = "?";
		try {
			subject = message.subject();
		} catch (ClientException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		Object correlationId = 0;
		try {
			correlationId = message.correlationId();
		} catch (ClientException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		LOGGER.trace("\r\n{}\r\nsubject:{}\r\npayload:{}\r\nproperties:{}\r\ncorrelationId:{}", to, subject, body,
				props, correlationId);

		NebulOuSCoreMessage internal = new NebulOuSCoreMessage(new Date(), to, body,
				(String) props.getOrDefault("application", null),
				correlationId != null ? correlationId.toString() : "");
		messages.add(internal);
		try {
			LOGGER.trace(om.writeValueAsString(body));
		} catch (JsonProcessingException e) {
		}
	}

	/**
	 * Build a predicate that filters by messages for a certain appId and topic
	 * 
	 * @param appId: The appId to filter for. If null, it is ignored
	 * @param topic: The topic to filter for. If null, it is ignored.
	 * @return
	 */
	public static Predicate<NebulOuSCoreMessage> messagesFromAppAndTopic(String appId, String topic) {
		return messagesFromApp(appId).and((Predicate<NebulOuSCoreMessage>) m -> topic == null || topic.equals(m.topic));
	}

	/**
	 * Builds a predicate that filters messages for the given app ID. If appID is
	 * null, the predicate has no effect.
	 * 
	 * @param id
	 * @return
	 */
	public static Predicate<NebulOuSCoreMessage> messagesFromApp(String id) {
		return ((Predicate<NebulOuSCoreMessage>) m -> id == null || id.equals(m.applicationId));
	}
	
	public List<NebulOuSCoreMessage> allMessages()
	{
		return new LinkedList<NebulOuSCoreMessage>(messages);
	}
	
	public void clear()
	{
		messages.removeIf(a->true);
	}
	
	/**
	 * Adds a new message to the cache
	 * 
	 * @param message
	 */
	public void add(NebulOuSCoreMessage message) {
		try {
			LOGGER.trace("Adding message:" + om.writeValueAsString(message));
		} catch (JsonProcessingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		this.messages.add(message);
	}

}
