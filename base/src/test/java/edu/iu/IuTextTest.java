package edu.iu;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

@SuppressWarnings("javadoc")
public class IuTextTest {

	@Test
	public void testUtf8() {
		assertEquals("foobar", IuText.utf8(IuText.utf8("foobar")));
		assertNull(IuText.utf8((byte[]) null));
		assertArrayEquals(new byte[0], IuText.utf8((String) null));
	}

}
