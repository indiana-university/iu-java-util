package iu.jwt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

import org.junit.jupiter.api.Test;

import edu.iu.IdGenerator;
import edu.iu.crypt.WebKey;
import edu.iu.jwt.WebToken;
import iu.jwt.spi.Init;

@SuppressWarnings("javadoc")
public class JwtSpiTest {

	static {
		Init.init();
	}

	@Test
	void testInstance() {
		assertInstanceOf(JwtSpi.class, Init.SPI);
	}
	
	@Test
	void testBuilder() {
		assertInstanceOf(JwtBuilder.class, WebToken.builder());
	}

	@Test
	void testVerify() {
		final var jwt = IdGenerator.generateId();
		final var issuerKey = mock(WebKey.class);
		final var verified = mock(Jwt.class);
		try (final var mockJwt = mockStatic(Jwt.class)) {
			mockJwt.when(() -> Jwt.verify(jwt, issuerKey)).thenReturn(verified);
			assertEquals(verified, WebToken.verify(jwt, issuerKey));
		}
	}

	@Test
	void testDecryptAndVerify() {
		final var jwt = IdGenerator.generateId();
		final var issuerKey = mock(WebKey.class);
		final var decryptKey = mock(WebKey.class);
		final var verified = mock(Jwt.class);
		try (final var mockJwt = mockStatic(Jwt.class)) {
			mockJwt.when(() -> Jwt.decryptAndVerify(jwt, issuerKey, decryptKey)).thenReturn(verified);
			assertEquals(verified, WebToken.decryptAndVerify(jwt, issuerKey, decryptKey));
		}
	}

}
