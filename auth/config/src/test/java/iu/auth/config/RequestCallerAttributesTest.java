package iu.auth.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.URI;

import org.junit.jupiter.api.Test;

import edu.iu.IdGenerator;
import edu.iu.auth.IuRequestAttributes;

@SuppressWarnings("javadoc")
public class RequestCallerAttributesTest {

	@Test
	public void testCallerAttributes() {
		final var remoteAddr = IdGenerator.generateId();
		final var requestUri = URI.create(IdGenerator.generateId());
		final var userAgent = IdGenerator.generateId();
		final var authnPrincipal = IdGenerator.generateId();
		final var impersonatedPrincipal = IdGenerator.generateId();

		final var requestAttributes = mock(IuRequestAttributes.class);
		when(requestAttributes.getRemoteAddr()).thenReturn(remoteAddr);
		when(requestAttributes.getRequestUri()).thenReturn(requestUri);
		when(requestAttributes.getUserAgent()).thenReturn(userAgent);

		final var callerAttributes = new RequestCallerAttributes(requestAttributes, authnPrincipal,
				impersonatedPrincipal);
		assertEquals(remoteAddr, callerAttributes.getRemoteAddr());
		assertEquals(requestUri, callerAttributes.getRequestUri());
		assertEquals(userAgent, callerAttributes.getUserAgent());
		assertEquals(authnPrincipal, callerAttributes.getAuthnPrincipal());
		assertEquals(impersonatedPrincipal, callerAttributes.getImpersonatedPrincipal());
	}

}
