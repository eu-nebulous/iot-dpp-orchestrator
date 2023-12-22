package eut.nebulouscloud.iot_dpp;

/**
 * DTO for handling information regarding the ActiveMQ Group Id value extraction criteria for a certain address.
 */
public class GroupIDExtractionParameters {

	public enum GroupIDExpressionSource {
		PROPERTY, BODY_JSON, BODY_XML
	}
	/**
	 * Message part that contains the group ID
	 */
	private GroupIDExpressionSource source;
	
	/**
	 * Expression to extract the group Id from the message
	 */
	private String expression;

	public GroupIDExtractionParameters(GroupIDExpressionSource source, String expression) {
		super();
		this.source = source;
		this.expression = expression;
	}

	public GroupIDExpressionSource getSource() {
		return source;
	}

	public void setSource(GroupIDExpressionSource source) {
		this.source = source;
	}

	public String getExpression() {
		return expression;
	}

	public void setExpression(String expression) {
		this.expression = expression;
	}

}
