package eut.nebulouscloud.iot_dpp;

import java.util.Map;

import org.apache.activemq.artemis.api.core.Message;
import org.apache.activemq.artemis.core.server.transformer.Transformer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GroupIDAnnotationDivertTransfomer implements Transformer {

	static Logger LOGGER = LoggerFactory.getLogger(GroupIDAnnotationDivertTransfomer.class);
	MessageGroupIDAnotator annotator;


	@Override
	public void init(Map<String, String> properties) {
		annotator = MessageGroupIDAnotator.fromProperties(properties);
	
	}


	public GroupIDAnnotationDivertTransfomer() {
	}

	@Override
	public Message transform(Message message) {
		if(annotator!=null)
		{
			annotator.setGroupIDAnnotation(message, true);
		}		
		return message;
	}

}
