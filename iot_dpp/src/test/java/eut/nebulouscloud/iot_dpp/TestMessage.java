package eut.nebulouscloud.iot_dpp;

import java.security.InvalidParameterException;

import org.apache.commons.lang3.NotImplementedException;

public class TestMessage {
		public int id;
		public int fieldA;
		public int fieldB;
		public TestMessage(int id, int fieldA, int fieldB) {
			super();
			this.id = id;
			this.fieldA = fieldA;
			this.fieldB = fieldB;
		}
		public TestMessage() {
			super();
		}
		
		public int getField(String fieldName)
		{
			if(fieldName.equals("A")) return fieldA;
			if(fieldName.equals("B"))return fieldB;
			throw new InvalidParameterException("Invalid field name "+fieldName+" must be either A or B");
		}

		
		
		
	
}
