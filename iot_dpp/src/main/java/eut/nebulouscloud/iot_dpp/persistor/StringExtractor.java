package eut.nebulouscloud.iot_dpp.persistor;

import java.util.LinkedList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.activemq.artemis.api.core.Pair;

import com.jayway.jsonpath.DocumentContext;

public class StringExtractor {
	static Pattern addresPattern = Pattern.compile("\\{ADDRESS\\|(.+)\\|(.+)\\}");
	static Pattern bodyPattern = Pattern.compile("\\{BODY\\|([^}]+)\\}");
	List<String> addressPatternPlaceholders = new LinkedList<String>();
	List<RegexTransform> addressRegexTransforms = new LinkedList<RegexTransform>();
	List<Pair<String,String>> bodyPatternPlaceholders = new LinkedList<Pair<String,String>>();
	String expression;

	public StringExtractor(String expression) {
		this.expression = expression;
		Matcher addressPatternMatcher = addresPattern.matcher(expression);
		while (addressPatternMatcher.find()) {
			addressPatternPlaceholders.add(addressPatternMatcher.group(0));
			String regex = addressPatternMatcher.group(1);
			String repl = addressPatternMatcher.group(2);
			addressRegexTransforms.add(new RegexTransform(regex, repl));
		}

		Matcher bodyPatternMatcher = bodyPattern.matcher(expression);
		while (bodyPatternMatcher.find()) {
			String placeholder = bodyPatternMatcher.group(0);
			String jsonpathExpr = bodyPatternMatcher.group(1);
			bodyPatternPlaceholders.add(new Pair(placeholder,jsonpathExpr));
		}

	}

	public String extract(String address, DocumentContext bodyContext) throws Exception {
		String finalExpression = this.expression;
		for (int i = 0; i < addressPatternPlaceholders.size(); i++) {
			String res = addressRegexTransforms.get(i).eval(address);
			if (res != null) {
				finalExpression = finalExpression.replace(addressPatternPlaceholders.get(i), res);
			}
		}

		for (Pair<String,String> bodyPatternPlaceholder : bodyPatternPlaceholders) {
			String placeholder = bodyPatternPlaceholder.getA();
			String jsonpathExpr = bodyPatternPlaceholder.getB();			
			try {
				List res = bodyContext.read(jsonpathExpr);
				if (res != null && !res.isEmpty()) {
					finalExpression = finalExpression.replace(placeholder, res.get(0).toString());
				}
			} catch (Exception ex) {
				throw new Exception("Couldn't eval JsonPath '"+jsonpathExpr+"' on body '"+bodyContext.jsonString()+"' resultExpression:'"+finalExpression+"'", ex);
			}
		}

		return finalExpression;
	}

}
