package iu.saml;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import edu.iu.IdGenerator;
import edu.iu.IuIterable;
import edu.iu.saml.IuSamlAssertion;

@SuppressWarnings("javadoc")
public class SamlPrincipalTest {

	@Test
	void testInvalid() {
		final var postAuth = mock(SamlPostAuthentication.class);
		when(postAuth.isInvalid()).thenReturn(true);
		assertThrows(IllegalStateException.class, () -> SamlPrincipal.from(postAuth));
	}

	@Test
	void testProperties() {
		final var postAuth = mock(SamlPostAuthentication.class);

		final var name = IdGenerator.generateId();
		final var authnAuthority = IdGenerator.generateId();
		final var authnInstant = Instant.now();
		final var expires = authnInstant.plusSeconds(10L);
		final var assertion = mock(IuSamlAssertion.class);
		when(postAuth.getName()).thenReturn(name);
		when(postAuth.getExpires()).thenReturn(expires);
		when(postAuth.getAuthnAuthority()).thenReturn(authnAuthority);
		when(postAuth.getAuthnInstant()).thenReturn(authnInstant);
		when(postAuth.getAssertions()).thenReturn(IuIterable.iter(assertion));

		final var principal = SamlPrincipal.from(postAuth);
		assertEquals(name, principal.getName());
		assertEquals(assertion, principal.getAssertions().iterator().next());
		assertEquals("SamlPrincipal [name=" + name + ", authnAuthority=" + authnAuthority + ", authnInstant="
				+ authnInstant + ", expires=" + expires + ", assertions=[" + assertion + "]]", principal.toString());
	}

}
