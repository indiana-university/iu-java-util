package iu.type;

import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

@SuppressWarnings("javadoc")
public class FacadeAssertionTests {

	@SuppressWarnings("unused")
	private Object field;

	@Test
	public void testFieldTypeMatches() {
		assertThrows(AssertionError.class, () -> new FieldFacade<>(getClass().getDeclaredField("field"),
				TypeFactory.resolveRawClass(String.class), TypeFactory.resolveRawClass(getClass())));
	}

}
