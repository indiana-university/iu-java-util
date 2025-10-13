package iu.auth.config;

import static iu.auth.config.IuHttpAware.HOST;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.net.http.HttpRequest;
import java.util.List;
import java.util.function.Supplier;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import edu.iu.IdGenerator;
import edu.iu.IuWebUtils;
import edu.iu.auth.oauth.OAuthAuthorizationClient;
import edu.iu.client.IuHttp;
import edu.iu.client.IuJson;
import edu.iu.client.IuJsonAdapter;
import edu.iu.crypt.WebKey;
import edu.iu.crypt.WebToken;

@SuppressWarnings("javadoc")
@ExtendWith(IuHttpAware.class)
public class OAuthAccessTokenGrantTest {

	private OAuthAuthorizationClient mockClient;
	private Supplier<OAuthAuthorizationClient> clientSupplier;
	private WebToken mockWebToken;
	private static final String TOKEN = IdGenerator.generateId();
	private static final int EXPIRES_IN = 3600;
	private static final URI TOKEN_URI = URI.create("https://" + HOST + "/token");
	private static final URI REDIRECT_URI = URI.create("https://" + HOST + "/redirect");
	private static final URI JWKS_URI = URI.create("https://" + HOST + "/jwks");

	@BeforeEach
	void setup() {
		mockClient = mock(OAuthAuthorizationClient.class);
		when(mockClient.getTokenUri()).thenReturn(TOKEN_URI);
		when(mockClient.getRedirectUri()).thenReturn(REDIRECT_URI);
		when(mockClient.getJwksUri()).thenReturn(JWKS_URI);
		clientSupplier = () -> mockClient;
		mockWebToken = mock(WebToken.class);
	}

	@Test
	void testConstructorAndClientSupplier() {
		OidcAuthorizationGrant grant = new OidcAuthorizationGrant(clientSupplier);
		assertNotNull(grant);
		assertEquals(mockClient, grant.getClient());
	}

	@Test
	void testTokenAuthSetsHeadersAndBody() {
		OidcAuthorizationGrant grant = new OidcAuthorizationGrant(clientSupplier);
		HttpRequest.Builder builder = mock(HttpRequest.Builder.class);
		when(builder.header(anyString(), anyString())).thenReturn(builder);
		when(builder.POST(any())).thenReturn(builder);
		try (MockedStatic<IuWebUtils> webUtilsMock = Mockito.mockStatic(IuWebUtils.class)) {
			webUtilsMock.when(() -> IuWebUtils.createQueryString(any()))
					.thenReturn("grant_type=authorization_code&redirect_uri=" + REDIRECT_URI);
			grant.tokenAuth(builder);
			verify(builder).header("Content-Type", "application/x-www-form-urlencoded");
			verify(builder).POST(any());
		}
	}

	@Test
	void testVerifyTokenRequiresNbfAndExp() {
		OidcAuthorizationGrant grant = new OidcAuthorizationGrant(clientSupplier);
		when(mockWebToken.getNotBefore()).thenReturn(null);
		when(mockWebToken.getExpires()).thenReturn(null);
		assertThrows(NullPointerException.class, () -> grant.verifyToken(mockWebToken));
		when(mockWebToken.getNotBefore()).thenReturn(java.time.Instant.now());
		assertThrows(NullPointerException.class, () -> grant.verifyToken(mockWebToken));
		when(mockWebToken.getExpires()).thenReturn(java.time.Instant.now().plusSeconds(3600));
		assertDoesNotThrow(() -> grant.verifyToken(mockWebToken));
	}

	@SuppressWarnings({ "rawtypes" })
	@Test
	void testGetAccessTokenRetrievesAndCachesToken() {
		OidcAuthorizationGrant grant = Mockito.spy(new OidcAuthorizationGrant(clientSupplier));
		final var tokenResponse = IuJson.object().add("access_token", TOKEN).add("expires_in", EXPIRES_IN).build();
		doReturn(mockWebToken).when(grant).validateJwt(TOKEN);
		doNothing().when(grant).verifyToken(mockWebToken);
		try (MockedStatic<IuHttp> httpMock = Mockito.mockStatic(IuHttp.class);
				MockedStatic<IuJson> jsonMock = Mockito.mockStatic(IuJson.class);
				MockedStatic<IuJsonAdapter> adapterMock = Mockito.mockStatic(IuJsonAdapter.class)) {
			httpMock.when(() -> IuHttp.send(any(URI.class), any(), any())).thenReturn(tokenResponse);
			jsonMock.when(() -> IuJson.get(tokenResponse, "access_token")).thenReturn(TOKEN);
			jsonMock.when(() -> IuJson.get(tokenResponse, "expires_in", IuJsonAdapter.of(Integer.class)))
					.thenReturn(EXPIRES_IN);
			String token1 = grant.getAccessToken();
			String token2 = grant.getAccessToken();
			assertEquals(TOKEN, token1);
			assertEquals(token1, token2); // cached
			httpMock.verify(() -> IuHttp.send(any(URI.class), any(), any()), times(1));
		}
	}

	@SuppressWarnings({ "rawtypes" })
	@Test
	void testGetAccessTokenRefreshesOnExpiry() throws InterruptedException {
		OidcAuthorizationGrant grant = Mockito.spy(new OidcAuthorizationGrant(clientSupplier));
		var tokenResponse = IuJson.object().add("access_token", TOKEN).add("expires_in", 1).build(); // expires
																										// immediately
		doReturn(mockWebToken).when(grant).validateJwt(TOKEN);
		doNothing().when(grant).verifyToken(mockWebToken);
		try (MockedStatic<IuHttp> httpMock = Mockito.mockStatic(IuHttp.class);
				MockedStatic<IuJson> jsonMock = Mockito.mockStatic(IuJson.class);
				MockedStatic<IuJsonAdapter> adapterMock = Mockito.mockStatic(IuJsonAdapter.class)) {
			httpMock.when(() -> IuHttp.send(any(URI.class), any(), any())).thenReturn(tokenResponse);
			jsonMock.when(() -> IuJson.get(tokenResponse, "access_token")).thenReturn(TOKEN);
			jsonMock.when(() -> IuJson.get(tokenResponse, "expires_in", IuJsonAdapter.of(Integer.class))).thenReturn(1);
			String token1 = grant.getAccessToken();
			Thread.sleep(1000L);
			String token2 = grant.getAccessToken();
			assertEquals(token1, token2);
			httpMock.verify(() -> IuHttp.send(any(URI.class), any(), any()), times(2));
		}
	}

	@Test
	void testValidateJwtCallsWebTokenVerifyWithCorrectArguments() {
		OidcAuthorizationGrant grant = Mockito.spy(new OidcAuthorizationGrant(clientSupplier));
		String jwtString = IdGenerator.generateId();
		WebKey mockWebKey = mock(WebKey.class);
		WebToken expectedToken = mock(WebToken.class);
		try (MockedStatic<WebKey> webKeyMock = Mockito.mockStatic(WebKey.class);
				MockedStatic<WebToken> webTokenMock = Mockito.mockStatic(WebToken.class)) {
			// Mock JWKS conversion
			List<WebKey> webKeyList = List.of(mockWebKey);
			webKeyMock.when(() -> WebKey.readJwks(JWKS_URI)).thenReturn(webKeyList);
			// Mock WebToken.verify
			webTokenMock.when(() -> WebToken.verify(jwtString, mockWebKey)).thenReturn(expectedToken);
			WebToken result = grant.validateJwt(jwtString);
			assertEquals(expectedToken, result);
			webTokenMock.verify(() -> WebToken.verify(jwtString, mockWebKey), times(1));
		}
	}
}