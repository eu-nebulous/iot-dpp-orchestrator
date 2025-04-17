package eut.nebulouscloud.iot_dpp.persistor;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.Option;
import com.jayway.jsonpath.ParseContext;


public class DataExtractionTest {
	static Logger LOGGER = LoggerFactory.getLogger(DataExtractionTest.class);
	
	private ParseContext jsonParser = JsonPath.using(com.jayway.jsonpath.Configuration.defaultConfiguration()
			.addOptions(Option.DEFAULT_PATH_LEAF_TO_NULL).addOptions(Option.ALWAYS_RETURN_LIST));
	
	@Test
	public void testExtractors() throws Exception {
		
		
		assertEquals("a",new RegexTransform("(.*)","$1").eval("a"));
		assertEquals("aa", new RegexTransform("(.*)","$1$1").eval("a"));
		assertEquals("a-a", new RegexTransform("(.*)","$1-$1").eval("a"));
		assertEquals("los", new RegexTransform("pepito\\.(.*)\\.palotes","$1").eval("pepito.los.palotes"));
		assertEquals("pepito.palotes", new RegexTransform("(.*)\\.(.*)\\.(.*)","$1.$3").eval("pepito.los.palotes"));
		assertEquals("pepito.eldelos.palotes", new RegexTransform("(.*)\\.(.*)\\.(.*)","$1.eldelos.$3").eval("pepito.los.palotes"));
		assertEquals("pepito.eldelos.palotes", new RegexTransform("(.*)\\.(.*)\\.(.*)","$1.eldelos.$3").eval("pepito.los.palotes"));
		// Additional test scenarios for regex transformations
		assertEquals("", new RegexTransform( "(.*)", "$1").eval("")); // Empty string
		assertEquals("123", new RegexTransform(".*?(\\d+).*", "$1").eval("abc123def")); // Extract numbers
		assertEquals("hello-world", new RegexTransform("(\\w+)\\s+(\\w+)", "$1-$2").eval("hello world")); // Word replacement
		assertEquals("original", new RegexTransform("nonmatching", "replacement").eval("original")); // Non-matching pattern
				
	}
	@Test
	public void testBucketExtractors() throws Exception {

		assertEquals("address", new StringExtractor(
				"{ADDRESS|.+\\.([^\\.]*)$|$1}") 
				.extract("super.fancy.address",null)
			);
		
		String dict = "{"+
		"name:pepito,"+
		"surname:lospalotes,"+
		"addresses:[\"calle de la piruleta 33\",\"rue de la fua\"]"+
		"}";
		//assertEquals("pepito", MessageDataExtractionUtils.evalStringExpression("{BODY|$.name}",null,dict));
		assertEquals("pepito-lospalotes", new StringExtractor("{BODY|$.name}-{BODY|$.surname}").extract("",jsonParser.parse(dict)));
		assertEquals("calle de la piruleta 33", new StringExtractor("{BODY|$.addresses[0]}").extract("",jsonParser.parse(dict)));
		assertThrows(Exception.class, new org.junit.function.ThrowingRunnable() {
			@Override
			public void run() throws Throwable {
				new StringExtractor("{BODY|$.addresses[3]}").extract("", jsonParser.parse(dict));
			}
		});
		
		// Additional test scenarios for JSON path extraction
		String complexDict = "{" +
			"user: {" +
				"profile: {" +
					"firstName: John," +
					"lastName: Doe," +
					"age: 30," +
					"addresses: [" +
						"{street: '123 Main St', city: 'New York'}," +
						"{street: '456 Oak Ave', city: 'Boston'}" +
					"]" +
				"}" +
			"}" +
		"}";
		
		assertEquals("John", new StringExtractor("{BODY|$.user.profile.firstName}").extract("",jsonParser.parse(complexDict)));
		assertEquals("30", new StringExtractor("{BODY|$.user.profile.age}").extract("",jsonParser.parse(complexDict)));
		assertEquals("123 Main St", new StringExtractor("{BODY|$.user.profile.addresses[0].street}").extract("",jsonParser.parse(complexDict)));
		assertEquals("Boston", new StringExtractor("{BODY|$.user.profile.addresses[1].city}").extract("",jsonParser.parse(complexDict)));
		
		// Test combined expressions
		assertEquals("John-Doe-address", new StringExtractor(
			"{BODY|$.user.profile.firstName}-{BODY|$.user.profile.lastName}-{ADDRESS|.+\\.([^\\.]*)$|$1}"
		).extract("super.fancy.address",jsonParser.parse(complexDict)));
		
		// Test non-existent paths
		assertThrows(Exception.class, new org.junit.function.ThrowingRunnable() {
			@Override
			public void run() throws Throwable {
				new StringExtractor("{BODY|$.user.profile.nonexistent}").extract("",jsonParser.parse(complexDict));
			}
		});
		
		assertThrows(Exception.class, new org.junit.function.ThrowingRunnable() {
			@Override
			public void run() throws Throwable {
				new StringExtractor("{BODY|$.user.profile.addresses[5]}").extract("",jsonParser.parse(complexDict));
			}
		});
	
		
		// Test with null dictionary
		assertThrows(Exception.class, new org.junit.function.ThrowingRunnable() {
			@Override
			public void run() throws Throwable {
				new StringExtractor("{BODY|$.name}").extract("",null);
			}
		});
		
		// Test with empty dictionary
		assertThrows(Exception.class, new org.junit.function.ThrowingRunnable() {
			@Override
			public void run() throws Throwable {
				new StringExtractor("{BODY|$.name}").extract("",jsonParser.parse("{}"));
			}
		});
		
	}
	
	private void assertEequalsKV(Map<String,String> expected,Map<String,String> res)
	{
		LOGGER.info("expected: "+expected.toString());
		LOGGER.info("res: "+res.toString());
		assertEquals(expected.size(),res.size());
		for(String expectedKey:expected.keySet())
		{
			boolean found = res.containsKey(expectedKey) && res.get(expectedKey).equals(expected.get(expectedKey));
			
			assertTrue(found);
		}
	}
	
	@Test
	public void testExtractors2() throws Exception {
		String complexDict = "{" +
				"user: {" +
					"profile: {" +
						"firstName: John," +
						"lastName: Doe," +
						"age: 30," +
						"roles: [\"admin\",\"reader\"]," +
						"tags: [" +
						"{k: 'a', v: '1'}," +
						"{k: 'b', v: '2'}" +
						"],"+
						"tags2: [" +
						"{'a': '1'}," +
						"{'b': '2'}" +
						"],"+
						"addresses: [" +
							"{street: '123 Main St', city: 'New York'}," +
							"{street: '456 Oak Ave', city: 'Boston'}" +
						"]" +
					"}" +
				"}" +
			"}";
		{
			Map<String,String> res =  new JSONKeyValueExtractor("BODY|$|$.user.profile.firstName|$.user.profile.lastName").extract("",jsonParser.parse(complexDict));
			Map<String,String> expected =  Map.of("John","Doe");
			assertEequalsKV(expected,res);
		}
		
		{
			Map<String,String> res =  new JSONKeyValueExtractor("BODY|$|$.user.profile.roles[*]|true").extract("",jsonParser.parse(complexDict));
			Map<String,String> expected =  Map.of("admin","true", "reader","true");
			assertEequalsKV(expected,res);	
		}
		
		{
			Map<String,String> res =  new JSONKeyValueExtractor("BODY|$|$.user.profile.roles[*]|$.user.profile.firstName").extract("",jsonParser.parse(complexDict));
			Map<String,String> expected =  Map.of("admin","John", "reader","John");
			assertEequalsKV(expected,res);	
		}
		
		{
			Map<String,String> res =  new JSONKeyValueExtractor("BODY|$|$.user.profile.tags[*].k|$.user.profile.tags[*].v").extract("",jsonParser.parse(complexDict));
			Map<String,String> expected =  Map.of("a","1", "b","2");
			assertEequalsKV(expected,res);	
		}
		
		{
			Map<String,String> res =  new JSONKeyValueExtractor("BODY|$|$.user.profile.tags2[*]").extract("",jsonParser.parse(complexDict));
			Map<String,String> expected =  Map.of("a","1", "b","2");
			assertEequalsKV(expected,res);	
		}
	}
	
	
	@Test
	public void testTimeExtractor() throws Exception {
	
		{
			String format = "yyyy-MM-dd HH:mm:ss z";
			SimpleDateFormat formatter =new SimpleDateFormat(format);
			Date expected = formatter.parse(formatter.format(new Date()));
			Date res =  new TimeExtractor("BODY|$.date|"+format).extract(jsonParser.parse("{'date':'"+formatter.format(expected)+"'}"));
			assertEquals(expected.getTime(),res.getTime());
		}
		
		{
			Date expected = new Date();
			Date res =  new TimeExtractor("BODY|$.date|TIMESTAMP").extract(jsonParser.parse("{'date':'"+expected.getTime()+"'}"));
			assertEquals(expected.getTime(),res.getTime());
		}
		{
			Date expected = new Date();
			Date res =  new TimeExtractor("BODY|$.date|TIMESTAMP").extract(jsonParser.parse("{'date':"+expected.getTime()+"}"));
			assertEquals(expected.getTime(),res.getTime());
		}
		
	}
	
	
	@Test
	public void testAcceptorExpression() throws Exception {
		String complexDict = "{" +
				"user: {" +
					"profile: {" +
						"firstName: John," +
						"lastName: Doe," +
						"age: 30," +
						"roles: [\"admin\",\"reader\"]," +
						"tags: [" +
						"{k: 'a', v: '1'}," +
						"{k: 'b', v: '2'}" +
						"],"+
						"tags2: [" +
						"{'a': '1'}," +
						"{'b': '2'}" +
						"],"+
						"addresses: [" +
							"{street: '123 Main St', city: 'New York'}," +
							"{street: '456 Oak Ave', city: 'Boston'}" +
						"]" +
					"}" +
				"}" +
			"}";
		{
			
			boolean  res =  new MessageFilter(".*|$|OR").eval("this.is.a.super.complex.topic", jsonParser.parse(complexDict));
			assertTrue(res);
		}
		
		{
			
			boolean  res =  new MessageFilter(".*|$|AND").eval("this.is.a.super.complex.topic", jsonParser.parse(complexDict));
			assertTrue(res);
		}
		
		{
			
			boolean  res =  new MessageFilter(".*|$.pepe|AND").eval("this.is.a.super.complex.topic", jsonParser.parse(complexDict));
			assertFalse(res);
		}
		
		{
			
			boolean  res =  new MessageFilter(".*|$.pepe|OR").eval("this.is.a.super.complex.topic", jsonParser.parse(complexDict));
			assertTrue(res);
		}
		
		{
			
			boolean  res =  new MessageFilter("topic|$.[?(@.age<50)]|OR").eval("this.is.a.super.complex.topic", jsonParser.parse("{age:30}"));
			assertTrue(res);
		}
		{
			
			boolean  res =  new MessageFilter("topic|$.[?(@.age<50)]|OR").eval("this.is.a.super.complex.topic", jsonParser.parse("{age:50}"));
			assertFalse(res);
		}
	}

	
}
