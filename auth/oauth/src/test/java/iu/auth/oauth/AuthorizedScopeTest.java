package iu.auth.oauth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.junit.jupiter.api.Test;

@SuppressWarnings("javadoc")
public class AuthorizedScopeTest {

	@Test
	public void testProps() {
		final var scope = new AuthorizedScope("foo", "bar");
		assertEquals("foo", scope.getName());
		assertEquals("bar", scope.getRealm());
	}

	@Test
	public void testEquals() {
		final var scope1 = new AuthorizedScope("foo", "bar");
		final var scope2 = new AuthorizedScope("foo", "baz");
		final var scope3 = new AuthorizedScope("bar", "baz");
		final var scope4 = new AuthorizedScope("foo", "baz");
		assertNotEquals(scope1, new Object());
		assertNotEquals(scope1, scope2);
		assertNotEquals(scope2, scope1);
		assertNotEquals(scope1, scope3);
		assertNotEquals(scope3, scope1);
		assertNotEquals(scope2, scope3);
		assertNotEquals(scope3, scope2);
		assertEquals(scope2, scope4);
		assertEquals(scope4, scope2);
		assertEquals(scope2.hashCode(), scope4.hashCode());
	}

}
