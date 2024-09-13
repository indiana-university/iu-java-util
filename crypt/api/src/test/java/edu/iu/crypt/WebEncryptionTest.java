package edu.iu.crypt;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import org.junit.jupiter.api.Test;

import edu.iu.IdGenerator;
import edu.iu.IuText;
import edu.iu.crypt.WebEncryption.Encryption;
import edu.iu.crypt.WebKey.Algorithm;
import edu.iu.test.IuTest;

@SuppressWarnings("javadoc")
public class WebEncryptionTest extends IuCryptApiTestCase {

	@Test
	public void testEncryptionFrom() {
		for (final var enc : Encryption.values())
			assertSame(enc, Encryption.from(enc.enc));
	}

	@Test
	public void testBuilderDefaultEncryptFromText() {
		final var builder = mock(WebEncryption.Builder.class, CALLS_REAL_METHODS);
		final var text = IdGenerator.generateId();
		try (final var mockByteArrayInputStream = mockConstruction(ByteArrayInputStream.class, (a, ctx) -> {
			assertArrayEquals(IuText.utf8(text), (byte[]) ctx.arguments().get(0));
		})) {
			builder.encrypt(text);
			verify(builder).encrypt(IuText.utf8(text));
			verify(builder).encrypt(mockByteArrayInputStream.constructed().get(0));
		}
	}

	@SuppressWarnings("unchecked")
	@Test
	public void testBuilderToEncryptionAndAlgorithm() {
		final var encryption = IuTest.rand(Encryption.class);
		final var algorithm = IuTest.rand(Algorithm.class);
		final var builder = mock(WebEncryption.Builder.class);
		final var recipient = mock(WebEncryptionRecipient.Builder.class);
		when(builder.compact()).thenReturn(builder);
		when(builder.addRecipient(algorithm)).thenReturn(recipient);
		try (final var mockWebEncryption = mockStatic(WebEncryption.class)) {
			mockWebEncryption.when(() -> WebEncryption.to(encryption, algorithm)).thenCallRealMethod();
			mockWebEncryption.when(() -> WebEncryption.builder(encryption, true)).thenReturn(builder);
			assertSame(recipient, WebEncryption.to(encryption, algorithm));
			verify(builder).compact();
		}
	}

	@Test
	public void testBuilderToEncryption() {
		final var encryption = IuTest.rand(Encryption.class);
		WebEncryption.builder(encryption);
		verify(Init.SPI).getJweBuilder(encryption, true);
	}
	
	@Test
	public void testParse() {
		final var jwe = IdGenerator.generateId();
		WebEncryption.parse(jwe);
		verify(Init.SPI).parseJwe(jwe);
	}
	
	@Test
	public void testDecryptText() {
		final var jwe = mock(WebEncryption.class, CALLS_REAL_METHODS);
		final var jwk = mock(WebKey.class);
		try (final var mockByteArrayOutputStream = mockConstruction(ByteArrayOutputStream.class)) {
			jwe.decryptText(jwk);
            verify(jwe).decrypt(jwk);
            verify(jwe).decrypt(jwk, mockByteArrayOutputStream.constructed().get(0));
		}
	}
	
}
