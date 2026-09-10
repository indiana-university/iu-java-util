package iu.oidc.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest.BodyPublishers;
import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.extension.ExtendWith;

import edu.iu.IuRuntimeEnvironment;
import edu.iu.client.HttpException;
import edu.iu.client.IuHttp;
import edu.iu.client.IuJson;
import edu.iu.config.IuConfig;
import edu.iu.crypt.WebKey;
import iu.oidc.client.config.IuOidcProvider;

@ExtendWith(OidcClientITSupport.class)
@EnabledIf("iu.oidc.client.OidcClientITSupport#isEnabled")
public class OidcProvidersIT {

	private IuOidcProvider provider;

	@BeforeEach
	void setup() {
		provider = IuConfig.load(IuOidcProvider.class, IuRuntimeEnvironment.env("it.oidc.provider"));
	}

	@Test
	void testLoadMetadata() throws IOException {
		final var iss = provider.getIssuer();
		final var metadata = OidcProviders.getMetadata(provider);
		System.out.println("Metadata");
		System.out.println(metadata);
		assertEquals(iss, metadata.getIssuer());
	}

	@Test
	void testLoadJwks() throws IOException {
		final var metadata = OidcProviders.getMetadata(provider);
		final var jwks = WebKey.readJwks(metadata.getJwksUri());
		System.out.println("JWKS:" + jwks);
		final var i = jwks.iterator();
		assertTrue(i.hasNext());
		final Set<String> keyIds = new HashSet<>();
		while (i.hasNext()) {
			final var key = i.next();
			assertNotNull(key.getAlgorithm());
			assertNotNull(key.getKeyId());
			assertTrue(keyIds.add(key.getKeyId()));
			assertNotNull(key.getPublicKey());
			assertNull(key.getPrivateKey());
		}
	}

	@Test
	void testMetadataIncludesRequiredFields() throws IOException {
		final var metadata = OidcProviders.getMetadata(provider);
		assertNotNull(metadata.getAuthorizationEndpoint());
		assertNotNull(metadata.getJwksUri());
		assertTrue(metadata.getResponseTypesSupported().iterator().hasNext());
		assertTrue(metadata.getSubjectTypesSupported().iterator().hasNext());

		var foundRs256 = false;
		for (final var alg : metadata.getIdTokenSigningAlgValuesSupported())
			if ("RS256".equals(alg))
				foundRs256 = true;
		assertTrue(foundRs256, "id_token_signing_alg_values_supported must include RS256");
	}

	@Test
	void testMetadataNotFound() {
		final var badUri = URI.create(provider.getIssuer() + "/.well-known/nonexistent-xyz");
		final var e = assertThrows(HttpException.class, () -> IuHttp.get(badUri, IuHttp.READ_JSON_OBJECT));
		assertNotNull(e.getResponse());
		final var status = e.getResponse().statusCode();
		assertTrue(status >= 400 && status < 500, "expected 4xx, got " + status);
	}

	@Test
	void testJwksNotFound() throws IOException {
		final var metadata = OidcProviders.getMetadata(provider);
		final var badUri = URI.create(metadata.getJwksUri() + "-bogus");
		final var e = assertThrows(HttpException.class, () -> IuHttp.get(badUri));
		assertNotNull(e.getResponse());
		final var status = e.getResponse().statusCode();
		assertTrue(status >= 400 && status < 500, "expected 4xx, got " + status);
	}

	@Test
	void testTokenEndpointRejectsUnauthenticatedRequest() throws IOException {
		final var metadata = OidcProviders.getMetadata(provider);
		final var e = assertThrows(HttpException.class,
				() -> IuHttp.send(metadata.getTokenEndpoint(),
						b -> b.header("Content-Type", "application/x-www-form-urlencoded")
								.POST(BodyPublishers.ofString("grant_type=client_credentials"))));
		final var response = e.getResponse();
		assertNotNull(response);
		final var status = response.statusCode();
		assertTrue(status == 400 || status == 401, "expected 400 or 401, got " + status);

		final var contentType = response.headers().firstValue("Content-Type").orElse("");
		if (contentType.contains("json")) {
			final var error = IuJson.parse(response.body()).asJsonObject();
			assertNotNull(error.getString("error", null), "expected an \"error\" member in " + error);
		}
	}

	@Test
	void testUserinfoEndpointRequiresBearerToken() throws IOException {
		final var metadata = OidcProviders.getMetadata(provider);
		final var e = assertThrows(HttpException.class, () -> IuHttp.get(metadata.getUserinfoEndpoint()));
		final var response = e.getResponse();
		assertNotNull(response);
		final var status = response.statusCode();
		assertTrue(status == 400 || status == 401, "expected 400 or 401, got " + status);
		if (status == 401)
			assertTrue(response.headers().firstValue("WWW-Authenticate").isPresent(),
					"expected WWW-Authenticate header on 401 response");
	}

	@Test
	void testAuthorizeEndpointRejectsMissingClientId() throws IOException {
		final var metadata = OidcProviders.getMetadata(provider);
		final var e = assertThrows(HttpException.class, () -> IuHttp.get(metadata.getAuthorizationEndpoint()));
		assertNotNull(e.getResponse());
		final var status = e.getResponse().statusCode();
		assertTrue(status >= 400 && status < 500, "expected 4xx, got " + status);
	}

}
