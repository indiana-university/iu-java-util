package edu.iu.crypt;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;

import edu.iu.IdGenerator;

@SuppressWarnings("javadoc")
public class WebTokenTest extends IuCryptApiTestCase {

	@Test
	public void testBuilder() {
		WebToken.builder();
		verify(Init.SPI).getJwtBuilder();
	}
	
	@Test
	public void testDecryptAndVerify() {
		final var jwt = IdGenerator.generateId();
		final var issuerKey = mock(WebKey.class);
		final var audienceKey = mock(WebKey.class);
		WebToken.decryptAndVerify(jwt, issuerKey, audienceKey);
		verify(Init.SPI).decryptAndVerifyJwt(jwt, issuerKey, audienceKey);
	}
	
	@Test
	public void testVerify() {
		final var jwt = IdGenerator.generateId();
		final var issuerKey = mock(WebKey.class);
		WebToken.verify(jwt, issuerKey);
		verify(Init.SPI).verifyJwt(jwt, issuerKey);
	}
	
}
