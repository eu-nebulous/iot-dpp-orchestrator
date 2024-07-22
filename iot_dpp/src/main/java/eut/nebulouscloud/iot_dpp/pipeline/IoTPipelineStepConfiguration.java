package eut.nebulouscloud.iot_dpp.pipeline;

import eut.nebulouscloud.iot_dpp.GroupIDExtractionParameters;

public class IoTPipelineStepConfiguration {
	public String inputStream;		
	public GroupIDExtractionParameters groupingKeyAccessor;
	public IoTPipelineStepConfiguration(String inputStream, GroupIDExtractionParameters groupingKeyAccessor) {
		super();
		this.inputStream = inputStream;
		this.groupingKeyAccessor = groupingKeyAccessor;
	}
	
	
	public IoTPipelineStepConfiguration() {
		super();
	}


	public String getInputStream() {
		return inputStream;
	}
	public void setInputStream(String inputStream) {
		this.inputStream = inputStream;
	}
	public GroupIDExtractionParameters getGroupingKeyAccessor() {
		return groupingKeyAccessor;
	}
	public void setGroupingKeyAccessor(GroupIDExtractionParameters groupingKeyAccessor) {
		this.groupingKeyAccessor = groupingKeyAccessor;
	}
	
	
}
