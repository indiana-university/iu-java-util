package iu.type.bundle;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;

import java.util.ServiceLoader;

import org.junit.jupiter.api.Test;

@SuppressWarnings("javadoc")
public class TypeBundleSpiTest {

	@Test
	@SuppressWarnings("unchecked")
	public void testCloseOnError() {
		var e = new RuntimeException();
		try (final var mockServiceLoader = mockStatic(ServiceLoader.class)) {
			mockServiceLoader.when(() -> ServiceLoader.load(any(Class.class), any())).thenThrow(e);
			assertSame(e, assertThrows(RuntimeException.class, () -> new TypeBundleSpi()));
		}
	}

}
