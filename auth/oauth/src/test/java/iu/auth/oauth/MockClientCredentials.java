package iu.auth.oauth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.net.http.HttpRequest.Builder;
import java.util.Set;

import javax.security.auth.Subject;

import edu.iu.IdGenerator;
import edu.iu.auth.IuApiCredentials;
import edu.iu.auth.IuAuthenticationException;
import iu.auth.principal.PrincipalVerifier;
import iu.auth.principal.PrincipalVerifierRegistry;

@SuppressWarnings("javadoc")
final class MockClientCredentials implements IuApiCredentials {

	private static final long serialVersionUID = 1L;

	private final class Verifier implements PrincipalVerifier<MockClientCredentials> {
		@Override
		public Class<MockClientCredentials> getType() {
			return MockClientCredentials.class;
		}

		@Override
		public String getRealm() {
			return clientId;
		}

		@Override
		public boolean isAuthoritative() {
			return true;
		}

		@Override
		public void verify(MockClientCredentials id, String realm) throws IuAuthenticationException {
			assertEquals(realm, clientId);
			assertSame(MockClientCredentials.this, id);
		}
	}

	private final String clientId = IdGenerator.generateId();

	MockClientCredentials() {
		PrincipalVerifierRegistry.registerVerifier(new Verifier());
	}

	@Override
	public String getName() {
		return clientId;
	}

	@Override
	public Subject getSubject() {
		return new Subject(true, Set.of(this), Set.of(), Set.of());
	}

	@Override
	public void applyTo(Builder httpRequestBuilder) throws IuAuthenticationException {
		httpRequestBuilder.header("Authorization", "Mock " + clientId);
	}

}
