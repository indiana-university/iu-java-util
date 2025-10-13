package iu.auth.config;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static iu.auth.config.IuHttpAware.HOST;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublisher;
import java.util.function.Supplier;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import edu.iu.IdGenerator;
import edu.iu.IuWebUtils;
import edu.iu.auth.oauth.OAuthAuthorizationClient;
import edu.iu.crypt.WebToken;

@SuppressWarnings("javadoc")
@ExtendWith(IuHttpAware.class)
public class OidcAuthorizationGrantTest {

	private OAuthAuthorizationClient mockClient;
	private Supplier<OAuthAuthorizationClient> clientSupplier;
	private WebToken mockWebToken;
	private static final URI REDIRECT_URI = URI.create("https://" + HOST + "/redirect");

	@BeforeEach
	void setup() {
		mockClient = mock(OAuthAuthorizationClient.class);
		when(mockClient.getRedirectUri()).thenReturn(REDIRECT_URI);
		clientSupplier = () -> mockClient;
		mockWebToken = mock(WebToken.class);
	}

	@Test
	void testConstructorAndGetClient() {
		OidcAuthorizationGrant grant = new OidcAuthorizationGrant(clientSupplier);
		assertNotNull(grant);
		assertEquals(mockClient, grant.getClient());
	}

	@Test
	void testTokenAuthSetsHeadersAndBody() {
		OidcAuthorizationGrant grant = new OidcAuthorizationGrant(clientSupplier);
		HttpRequest.Builder builder = mock(HttpRequest.Builder.class);
		when(builder.header(anyString(), anyString())).thenReturn(builder);
		when(builder.POST(any(BodyPublisher.class))).thenReturn(builder);
		try (MockedStatic<IuWebUtils> webUtilsMock = Mockito.mockStatic(IuWebUtils.class)) {
			webUtilsMock.when(() -> IuWebUtils.createQueryString(any()))
					.thenReturn("grant_type=authorization_code&redirect_uri=" + REDIRECT_URI);
			grant.tokenAuth(builder);
			verify(builder).header("Content-Type", "application/x-www-form-urlencoded");
			verify(builder).POST(any(BodyPublisher.class));
		}
	}

	@Test
	void testVerifyTokenThrowsOnNullNbfOrExp() {
		OidcAuthorizationGrant grant = new OidcAuthorizationGrant(clientSupplier);
		when(mockWebToken.getNotBefore()).thenReturn(null);
		when(mockWebToken.getExpires()).thenReturn(null);
		assertThrows(NullPointerException.class, () -> grant.verifyToken(mockWebToken));
		when(mockWebToken.getNotBefore()).thenReturn(java.time.Instant.now());
		assertThrows(NullPointerException.class, () -> grant.verifyToken(mockWebToken));
		when(mockWebToken.getExpires()).thenReturn(java.time.Instant.now().plusSeconds(3600));
		assertDoesNotThrow(() -> grant.verifyToken(mockWebToken));
	}

	@Test
	void testValidateJwtDelegatesToParent() {
		OidcAuthorizationGrant grant = spy(new OidcAuthorizationGrant(clientSupplier));
		String jwtString = IdGenerator.generateId();
		WebToken expectedToken = mock(WebToken.class);
		doReturn(expectedToken).when((OAuthAccessTokenGrant) grant).validateJwt(jwtString);
		WebToken result = grant.validateJwt(jwtString);
		assertEquals(expectedToken, result);
	}
}