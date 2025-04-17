package eut.nebulouscloud.iot_dpp.persistor;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.jayway.jsonpath.DocumentContext;

public class TimeExtractor {
	static Pattern timePattern = Pattern.compile("^BODY\\|(.+)\\|(.+)$");
	String timeJsonpathExpression;
	SimpleDateFormat dateFormatter;

	public TimeExtractor(String dateTimeExpression) throws Exception {
		if (dateTimeExpression == null)
			return;
		Matcher timePatternMatcher = timePattern.matcher(dateTimeExpression);
		boolean init = false;
		while (timePatternMatcher.find()) {
			timeJsonpathExpression = timePatternMatcher.group(1);
			String dateFormatExpr = timePatternMatcher.group(2);
			if ("TIMESTAMP".equals(dateFormatExpr)) {
				dateFormatter = null;
				init = true;
				return;
			} else {
				dateFormatter = new SimpleDateFormat(dateFormatExpr);
				init = true;
				return;
			}
			
		}
		if (!init)
			throw new Exception("Couldn't init the time parser");
	}
	
	public Date extract(DocumentContext bodyJsonpath) throws Exception {		
		List res = bodyJsonpath.read(timeJsonpathExpression);
		if (res != null && !res.isEmpty()) {
			if (dateFormatter == null) {
				return new Date(Long.parseLong(res.get(0).toString()));
			} else {
				return dateFormatter.parse(res.get(0).toString());
			}
		}
		return null;
	}
	
}
