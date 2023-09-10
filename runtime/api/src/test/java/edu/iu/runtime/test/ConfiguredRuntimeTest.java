package edu.iu.runtime.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.lang.reflect.Type;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.ServiceLoader;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

import edu.iu.runtime.IuRuntime;
import edu.iu.runtime.IuRuntimeConfiguration;
import iu.runtime.EmptyRuntime;
import iu.runtime.RuntimeFactory;

@Isolated
public class ConfiguredRuntimeTest {

	@Test
	public void testGetValue() {
		assertTrue(IuRuntime.PROVIDER instanceof EmptyRuntime);

		IuRuntimeConfiguration env;
		try (var serviceLoader = mockStatic(ServiceLoader.class)) {
			var runtime = mock(IuRuntime.class);
			when(runtime.getEnvironment()).thenReturn((reference, type) -> {
				assertEquals(String.class, type);
				if ("foo".equals(reference))
					return "baz";
				else
					throw new IllegalArgumentException();
			});

			var iter = mock(Iterator.class);
			when(iter.hasNext()).thenReturn(true, false);
			when(iter.next()).thenReturn(runtime).thenThrow(NoSuchElementException.class);

			var loader = mock(ServiceLoader.class);
			when(loader.iterator()).thenReturn(iter);

			serviceLoader.when(() -> ServiceLoader.load(IuRuntime.class, null)).thenReturn(loader);

			env = RuntimeFactory.getProvider().getEnvironment();
		}
		assertThrows(IllegalArgumentException.class, () -> env.getValue("bar"));
		assertEquals("baz", env.getValue("foo"));
		assertEquals("baz", env.getValue("foo", String.class));
		assertEquals("baz", env.getValue("foo", (Type) String.class));
		assertEquals("baz", env.getValue("foo", "bar"));
		assertEquals("baz", env.getValue("foo", String.class, "bar"));
		assertEquals("baz", env.getValue("foo", (Type) String.class, "bar"));
	}

}
