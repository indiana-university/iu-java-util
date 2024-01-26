package iu.auth;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.ServiceLoader;

import org.junit.jupiter.api.Test;

@SuppressWarnings("javadoc")
public class IuAuthSpiFactoryTest {

	private interface TestService {
	}

	@SuppressWarnings("unchecked")
	@Test
	public void testSingletons() {
		try (final var mockServiceLoader = mockStatic(ServiceLoader.class)) {
			mockServiceLoader.when(() -> ServiceLoader.load(any(Class.class), any(ClassLoader.class))).then(a -> {
				final Class<?> c = a.getArgument(0);
				assertSame(c.getClassLoader(), a.getArgument(1));
				final var mockLoader = mock(ServiceLoader.class);
				final var mockProvider = mock(c);
				when(mockLoader.findFirst()).thenReturn(Optional.of(mockProvider));
				return mockLoader;
			});
			final var i = assertInstanceOf(TestService.class, IuAuthSpiFactory.get(TestService.class));
			assertSame(i, IuAuthSpiFactory.get(TestService.class));
		}
	}

}
