package eut.nebulouscloud.bridge;

import org.apache.activemq.artemis.api.core.Message;
import org.apache.activemq.artemis.core.server.transformer.Transformer;

/**
 * Transformer that adds NEB_BRIDGED=true property to messages so that eut.nebulouscloud.bridge doesn't doesn't create an infinite loop of messages.
 */
public class BridgeTransformer implements Transformer {

	public static String NEB_BRIDGED_PROP = "NEB_BRIDGED";
	@Override
	public Message transform(Message message) {
		message.putBooleanProperty(NEB_BRIDGED_PROP, true);
		return message;
	}

}
