package iu.type.container;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

import edu.iu.IdGenerator;
import edu.iu.type.IuType;
import iu.type.container.spi.IuEnvironment;

@SuppressWarnings("javadoc")
public class EnvironmentResourceTest {

	@Test
	public void testDefaultValue() {
		final var env = mock(IuEnvironment.class);
		final var type = IuType.of(String.class);
		final var name = IdGenerator.generateId();
		final var defaultValue = IdGenerator.generateId();
		final var resource = new EnvironmentResource<>(env, name, type, defaultValue);
		assertEquals(name, resource.name());
		assertEquals(type, resource.type());
		assertFalse(resource.needsAuthentication());
		assertTrue(resource.shared());
		assertEquals(0, resource.priority());
		assertThrows(UnsupportedOperationException.class, () -> resource.factory(null));

		assertEquals(defaultValue, resource.factory().get());
		verify(env).resolve(name, type.erasedClass());
	}

	@Test
	public void testMissingValue() {
		final var env = mock(IuEnvironment.class);
		final var type = IuType.of(String.class);
		final var name = IdGenerator.generateId();
		final var resource = new EnvironmentResource<>(env, name, type, null);
		assertEquals(name, resource.name());
		assertEquals(type, resource.type());
		assertFalse(resource.needsAuthentication());
		assertTrue(resource.shared());
		assertEquals(0, resource.priority());
		assertThrows(UnsupportedOperationException.class, () -> resource.factory(null));

		final var error = assertThrows(IllegalArgumentException.class, resource.factory()::get);
		assertEquals("Missing environment entry for " + name + "!" + type.name(), error.getMessage());
		verify(env).resolve(name, type.erasedClass());
	}

	@Test
	public void testExactMatch() {
		final var env = mock(IuEnvironment.class);
		final var type = IuType.of(String.class);
		final var name = IdGenerator.generateId();
		final var value = IdGenerator.generateId();
		when(env.resolve(name, type.erasedClass())).thenReturn(value);
		final var resource = new EnvironmentResource<>(env, name, type, null);
		assertEquals(name, resource.name());
		assertEquals(type, resource.type());
		assertFalse(resource.needsAuthentication());
		assertTrue(resource.shared());
		assertEquals(0, resource.priority());
		assertThrows(UnsupportedOperationException.class, () -> resource.factory(null));

		assertEquals(value, resource.factory().get());
	}

	@Test
	public void testRelativeMatch() {
		final var env = mock(IuEnvironment.class);
		final var type = IuType.of(String.class);

		final var context1 = IdGenerator.generateId();
		final var context2 = IdGenerator.generateId();
		final var name = IdGenerator.generateId();

		final var value = IdGenerator.generateId();
		when(env.resolve(name, type.erasedClass())).thenReturn(value);

		final var resource = new EnvironmentResource<>(env, context1 + '/' + context2 + '/' + name, type, null);
		assertEquals(value, resource.factory().get());
	}

	@Test
	public void testPrefixMatch() {
		final var env = mock(IuEnvironment.class);
		final var type = IuType.of(String.class);

		final var context1 = IdGenerator.generateId();
		final var context2 = IdGenerator.generateId();
		final var name = IdGenerator.generateId();

		final var value = IdGenerator.generateId();
		when(env.resolve("/" + context1 + "/" + name, type.erasedClass())).thenReturn(value);

		final var resource = new EnvironmentResource<>(env, context1 + '/' + context2 + '/' + name, type, null);
		assertEquals(value, resource.factory().get());
	}

	@Test
	public void testSuffixMatch() {
		final var env = mock(IuEnvironment.class);
		final var type = IuType.of(String.class);

		final var context1 = IdGenerator.generateId();
		final var context2 = IdGenerator.generateId();
		final var name = IdGenerator.generateId();

		final var value = IdGenerator.generateId();
		when(env.resolve(context2 + "/" + name, type.erasedClass())).thenReturn(value);

		final var resource = new EnvironmentResource<>(env, context1 + '/' + context2 + '/' + name, type, null);
		assertEquals(value, resource.factory().get());
	}

	@Test
	public void testNoMatch() {
		final var env = mock(IuEnvironment.class);
		final var type = IuType.of(String.class);

		final var context1 = IdGenerator.generateId();
		final var context2 = IdGenerator.generateId();
		final var name = IdGenerator.generateId();

		final var value = IdGenerator.generateId();

		final var resource = new EnvironmentResource<>(env, context1 + '/' + context2 + '/' + name, type, value);
		assertEquals(value, resource.factory().get());
	}

}
