package edu.iu.crypt;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.verify;

import java.io.ByteArrayInputStream;

import org.junit.jupiter.api.Test;

import edu.iu.IdGenerator;
import edu.iu.IuText;
import edu.iu.crypt.WebKey.Algorithm;
import edu.iu.test.IuTest;

@SuppressWarnings("javadoc")
public class WebSignatureTest extends IuCryptApiTestCase {

	@Test
	public void testBuilderSignText() {
		final var text = IdGenerator.generateId();
		final var builder = mock(WebSignature.Builder.class, CALLS_REAL_METHODS);
		try (final var mockByteArrayInputStream = mockConstruction(ByteArrayInputStream.class, (a, ctx) -> {
			assertArrayEquals(IuText.utf8(text), (byte[]) ctx.arguments().get(0));
		})) {
			builder.sign(text);
			verify(builder).sign(IuText.utf8(text));
			verify(builder).sign(mockByteArrayInputStream.constructed().get(0));
		}
	}

	@Test
	public void testBuilderForAlgorithm() {
		final var algorithm = IuTest.rand(Algorithm.class);
		WebSignature.builder(algorithm);
		verify(Init.SPI).getJwsBuilder(algorithm);
	}
	
}
