package iu.auth.principal;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;

import java.time.Instant;

import javax.security.auth.Subject;

import org.junit.jupiter.api.Test;

import edu.iu.IdGenerator;
import edu.iu.auth.IuPrincipalIdentity;

@SuppressWarnings("javadoc")
public class IdentityPrincipalVerifierTest {

	private static final class Id implements IuPrincipalIdentity {
		private final String name;

		private Id(String name) {
			this.name = name;
		}

		@Override
		public String getName() {
			return name;
		}

		@Override
		public Instant getIssuedAt() {
			fail();
			return null;
		}

		@Override
		public Instant getAuthTime() {
			fail();
			return null;
		}

		@Override
		public Instant getExpires() {
			fail();
			return null;
		}

		@Override
		public Subject getSubject() {
			fail();
			return null;
		}
	}

	@Test
	public void testVerifier() {
		final var name = IdGenerator.generateId();
		final var id = new Id(name);
		final var verifier = new IdentityPrincipalVerifier<>(Id.class, id);
		assertEquals(name, verifier.getRealm());
		assertNull(verifier.getAuthenticationEndpoint());
		assertNull(verifier.getAuthScheme());
		assertEquals(Id.class, verifier.getType());
		assertFalse(verifier.isAuthoritative());
		assertDoesNotThrow(() -> verifier.verify(id));
		assertThrows(IllegalArgumentException.class, () -> verifier.verify(new Id(name)));
	}

}
