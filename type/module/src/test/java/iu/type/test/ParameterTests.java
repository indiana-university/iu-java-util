package iu.type.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;

import edu.iu.type.IuType;

@SuppressWarnings("javadoc")
public class ParameterTests {

	@SuppressWarnings("unused")
	private void parameterTest(String param) {
	}

	@Test
	public void testBasicParam() {
		var method = IuType.of(getClass()).method("parameterTest", String.class);
		var parameter = method.parameter(0);
		assertSame(method, parameter.declaringExecutable());
		assertEquals(0, parameter.index());
		assertEquals("arg0", parameter.name());
		assertEquals("arg0:String", parameter.toString());
	}

}
