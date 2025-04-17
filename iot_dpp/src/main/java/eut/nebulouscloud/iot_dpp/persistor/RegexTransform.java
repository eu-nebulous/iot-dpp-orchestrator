package eut.nebulouscloud.iot_dpp.persistor;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RegexTransform {

	String matchExpression;
	Pattern matchExpressionPattern;
	String resultExpression;

	public RegexTransform(String matchExpression, String resultExpression) {
		this.matchExpression = matchExpression;
		this.matchExpressionPattern = Pattern.compile(matchExpression);
		this.resultExpression = resultExpression;
	}

	public String eval(String text) throws Exception {
		try {
			Matcher m = matchExpressionPattern.matcher(text);
			if (m.matches()) {
				String result = resultExpression;
				for (int i = 1; i < m.groupCount() + 1; i++) {
					result = result.replaceAll("\\$" + (i), m.group(i));
				}
				return result;

			} else {
				return text;
			}
		} catch (Exception ex) {
			throw new Exception(
					"Couldn't extract apply regex transform to '{}' matchExpression:'{}' resultExpression:'{}'");
		}

	}

}
