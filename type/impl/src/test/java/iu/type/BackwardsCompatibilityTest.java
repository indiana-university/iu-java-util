package iu.type;

import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;

@SuppressWarnings("javadoc")
public class BackwardsCompatibilityTest {

	@Test
	public void testSameLoader() {
		assertSame(getClass(), BackwardsCompatibility.getCompatibleClass(getClass()));
	}

}
