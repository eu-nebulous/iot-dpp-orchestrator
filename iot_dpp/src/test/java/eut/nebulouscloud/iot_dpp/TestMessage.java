package eut.nebulouscloud.iot_dpp;

import java.security.InvalidParameterException;

import org.apache.commons.lang3.NotImplementedException;

public class TestMessage {
		public int id;
		public int fieldB;
		public int fieldC;
		public TestMessage(int id, int fieldB, int fieldC) {
			super();
			this.id = id;
			this.fieldB = fieldB;
			this.fieldC = fieldC;
		}
		public TestMessage() {
			super();
		}
		
		public int getField(String fieldName)
		{
			if(fieldName.equals("B")) return fieldB;
			if(fieldName.equals("C"))return fieldC;
			throw new InvalidParameterException("Invalid field name "+fieldName+" must be either B or C");
		}

		
		
		
	
}
