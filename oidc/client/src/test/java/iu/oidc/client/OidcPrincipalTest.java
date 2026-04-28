package iu.oidc.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.util.function.Function;

import org.junit.jupiter.api.Test;

import edu.iu.IdGenerator;
import edu.iu.client.IuJson;
import edu.iu.client.IuJsonAdapter;
import edu.iu.client.IuJsonPropertyNameFormat;
import edu.iu.jwt.WebToken;

@SuppressWarnings("javadoc")
public class OidcPrincipalTest {

	static {
		edu.iu.crypt.Init.init();
		iu.jwt.spi.Init.init();
	}

	@SuppressWarnings("unchecked")
	@Test
	void testProperties() {
		final var sub = IdGenerator.generateId();
		final var foo = IdGenerator.generateId();
		final var bar = IdGenerator.generateId();
		final var idToken = WebToken.builder().sub(sub).claim("foo", foo, String.class).build();
		final var userinfoClaims = IuJson.object().add("sub", sub).add("bar", bar).build();
		final var setCookie = IdGenerator.generateId();

		final var uri = URI.create(IdGenerator.generateId());
		final var accessToken = IdGenerator.generateId();
		final var accessTokenLookup = mock(Function.class);
		when(accessTokenLookup.apply(uri)).thenReturn(accessToken);

		final var principal = new OidcPrincipal(idToken, userinfoClaims, setCookie, accessTokenLookup,
				t -> IuJsonAdapter.adapt(t, IuJsonPropertyNameFormat.LOWER_CASE_WITH_UNDERSCORES));

		assertEquals(sub, principal.getName());
		assertEquals(idToken, principal.getIdToken());
		assertEquals(setCookie, principal.getSetCookie());
		assertEquals(accessToken, principal.getAccessToken(uri));
		assertEquals(foo, principal.getClaim("foo", String.class));
		assertEquals(bar, principal.getClaim("bar", String.class));
	}

	@Test
	void testSubVerification() {
		final var sub = IdGenerator.generateId();
		final var idToken = WebToken.builder().sub(sub).build();
		assertEquals("userinfo missing sub claim",
				assertThrows(IllegalArgumentException.class,
						() -> new OidcPrincipal(idToken, IuJson.object().build(), null, a -> null,
								t -> IuJsonAdapter.adapt(t, IuJsonPropertyNameFormat.LOWER_CASE_WITH_UNDERSCORES)))
						.getMessage());
		assertEquals("userinfo sub claim doesn't match id token", assertThrows(IllegalArgumentException.class,
				() -> new OidcPrincipal(idToken, IuJson.object().add("sub", IdGenerator.generateId()).build(), null,
						a -> null, t -> IuJsonAdapter.adapt(t, IuJsonPropertyNameFormat.LOWER_CASE_WITH_UNDERSCORES)))
				.getMessage());

	}

}
