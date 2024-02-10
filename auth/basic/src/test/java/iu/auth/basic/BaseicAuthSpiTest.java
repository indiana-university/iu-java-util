package iu.auth.basic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mockConstruction;

import java.util.List;

import org.junit.jupiter.api.Test;

@SuppressWarnings("javadoc")
public class BaseicAuthSpiTest {

	@Test
	public void testSpi() {
		final var basicSpi = new BasicAuthSpi();
		try (final var mockBasic = mockConstruction(BasicAuthCredentials.class)) {
			final var basic = basicSpi.createCredentials("foo", "bar");
			assertEquals(List.of(basic), mockBasic.constructed());
		}
	}

}
