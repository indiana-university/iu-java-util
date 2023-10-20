package iu.type;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;

import edu.iu.type.IuType;

@SuppressWarnings("javadoc")
public class ConstructorTests {

	@Test
	public void testDirect() throws Exception {
		class HasDirect {
		}
		var type = IuType.of(HasDirect.class);
		var con = type.constructor(getClass());
		assertEquals("HasDirect(ConstructorTests)", con.toString());
		assertSame(type, ((TypeFacade<?,?>) con.declaringType()).template);
		assertEquals(1, con.parameters().size());
		assertSame(getClass(), con.parameter(0).type().erasedClass());
		assertInstanceOf(HasDirect.class, con.exec(this));
	}

	@Test
	public void testNestedParam() throws Exception {
		@SuppressWarnings("unused")
		class HasNestedParam<N extends Number> {
			<A extends N> HasNestedParam(Class<A> aClass) {
			}
		}
		var type = IuType.of(HasNestedParam.class);
		var con = type.constructor(getClass(), Class.class);
		assertEquals("HasNestedParam(ConstructorTests,Class<A>)", con.toString());
		assertSame(Number.class, con.parameter(1).type().referTo(Class.class).typeParameter("T").erasedClass());
	}

}
