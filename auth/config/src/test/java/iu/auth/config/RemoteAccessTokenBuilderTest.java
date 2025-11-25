package iu.auth.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

import java.lang.reflect.Type;
import java.net.URI;

import org.junit.jupiter.api.Test;

import edu.iu.IdGenerator;
import edu.iu.auth.config.CallerAttributes;
import edu.iu.client.IuJson;
import edu.iu.client.IuJsonAdapter;

@SuppressWarnings("javadoc")
public class RemoteAccessTokenBuilderTest {

	@Test
	public void testAdaptNonClass() {
		try (final var mockJsonAdapter = mockStatic(IuJsonAdapter.class)) {
			final var mockType = mock(Type.class);
			final var mockAdapter = mock(IuJsonAdapter.class);
			mockJsonAdapter.when(() -> IuJsonAdapter.of(mockType)).thenReturn(mockAdapter);
			assertSame(mockAdapter, RemoteAccessTokenBuilder.adaptAuthorizationDetails(mockType));
		}
	}

	@Test
	public void testAdaptNonInterface() {
		try (final var mockJsonAdapter = mockStatic(IuJsonAdapter.class)) {
			class A {
			}
			final var mockAdapter = mock(IuJsonAdapter.class);
			mockJsonAdapter.when(() -> IuJsonAdapter.of((Type) A.class)).thenReturn(mockAdapter);
			assertSame(mockAdapter, RemoteAccessTokenBuilder.adaptAuthorizationDetails(A.class));
		}
	}

	@Test
	public void testScope() {
		final var scope = IdGenerator.generateId();
		final var t = new RemoteAccessTokenBuilder<>().scope(scope).build();
		assertEquals(scope, t.getScope());
	}

	@Test
	public void testCallerAttrbutes() {
		final var requestUri = URI.create(IdGenerator.generateId());
		final var remoteAddr = IdGenerator.generateId();
		final var userAgent = IdGenerator.generateId();
		final var authnPrincipal = IdGenerator.generateId();

		final var t = new RemoteAccessTokenBuilder<>() //
				.caller(requestUri, remoteAddr, userAgent, authnPrincipal) //
				.build();

		final var callerAttributes = t.getCallerAttributes();
		assertEquals(requestUri, callerAttributes.getRequestUri());
		assertEquals(remoteAddr, callerAttributes.getRemoteAddr());
		assertEquals(userAgent, callerAttributes.getUserAgent());
		assertEquals(authnPrincipal, callerAttributes.getAuthnPrincipal());
	}

}
