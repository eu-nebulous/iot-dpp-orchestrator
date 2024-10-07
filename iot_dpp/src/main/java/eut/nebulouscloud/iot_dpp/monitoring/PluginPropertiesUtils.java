package eut.nebulouscloud.iot_dpp.monitoring;

import java.util.Map;

public class PluginPropertiesUtils {
	
	static String getEMSUrl(Map<String, String> properties)
	{
		String emsHost = properties.getOrDefault("ems_host", null);
		String emsPort = properties.getOrDefault("ems_port", null);		
		String emsURL =	properties.getOrDefault("ems_url", null);
	
		
		
		if((emsHost == null || emsHost.isBlank())&& (emsURL == null || emsURL.isBlank())) 
		{
			throw new IllegalStateException("ems_url or ems_host parameter is not defined");
		}
		if((emsURL == null || emsURL.isBlank()))
		{
			if(emsPort==null || emsPort.isBlank())
			{
				throw new IllegalStateException("ems_url is empty. You should provide ems_port");
			}
			emsURL = emsHost+":"+emsPort;
		}
		
		return emsURL;
	}
	

}
