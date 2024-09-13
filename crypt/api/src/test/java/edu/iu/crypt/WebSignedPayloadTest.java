package edu.iu.crypt;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

import edu.iu.IdGenerator;
import edu.iu.IuIterable;
import edu.iu.IuText;

@SuppressWarnings("javadoc")
public class WebSignedPayloadTest extends IuCryptApiTestCase {

	@Test
	public void testParseJws() {
		final var jws = IdGenerator.generateId();
		WebSignedPayload.parse(jws);
		verify(Init.SPI).parseJws(jws);
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	@Test
	public void testVerifySuccess() {
		final var key = mock(WebKey.class);
		final var payload = IuText.base64Url(IdGenerator.generateId());
		final var signature = mock(WebSignature.class);
		final var signedPayload = mock(WebSignedPayload.class, CALLS_REAL_METHODS);
		when(signedPayload.getPayload()).thenReturn(payload);
		when(signedPayload.getSignatures()).thenReturn((Iterable) IuIterable.iter(signature));
		signedPayload.verify(key);
		verify(signature).verify(payload, key);
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	@Test
	public void testVerifyTwoFailures() {
		final var key = mock(WebKey.class);
		final var payload = IuText.base64Url(IdGenerator.generateId());

		final var e1 = new IllegalStateException();
		final var signature = mock(WebSignature.class);
		doThrow(e1).when(signature).verify(payload, key);

		final var e2 = new IllegalArgumentException();
		final var signature2 = mock(WebSignature.class);
		doThrow(e2).when(signature2).verify(payload, key);

		final var signedPayload = mock(WebSignedPayload.class, CALLS_REAL_METHODS);
		when(signedPayload.getPayload()).thenReturn(payload);
		when(signedPayload.getSignatures()).thenReturn((Iterable) IuIterable.iter(signature, signature2));
		final var error = assertThrows(IllegalStateException.class, () -> signedPayload.verify(key));
		assertSame(e1, error);
		assertSame(e2, error.getSuppressed()[0]);
	}

}
