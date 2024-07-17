package eut.nebulouscloud.iot_dpp;

import java.util.Map;

import org.apache.activemq.artemis.api.core.Message;
import org.apache.activemq.artemis.core.server.transformer.Transformer;

public class GroupIDAnnotationDivertTransfomer implements Transformer {

	MessageGroupIDAnotator annotator;

	public void init(Map<String, String> properties) {
		annotator = new MessageGroupIDAnotator(properties.get("GROUP_ID_EXTRACTION_CONFIG_PATH"));
	}

	public GroupIDAnnotationDivertTransfomer() {
	}

	@Override
	public Message transform(Message message) {
		annotator.setGroupIDAnnotation(message, true);
		return message;
	}

}
