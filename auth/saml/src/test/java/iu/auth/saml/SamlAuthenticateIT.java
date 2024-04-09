package iu.auth.saml;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.InputStream;
import java.io.StringReader;
import java.net.CookieManager;
import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse.BodyHandlers;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;

import org.jsoup.Jsoup;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import edu.iu.IdGenerator;
import edu.iu.IuException;
import edu.iu.IuWebUtils;
import edu.iu.auth.saml.IuSamlClient;
import edu.iu.auth.saml.IuSamlProvider;
import edu.iu.test.VaultProperties;
import iu.auth.util.XmlDomUtil;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;

@EnabledIf("edu.iu.test.VaultProperties#isConfigured")
public class SamlAuthenticateIT {

	private static IuSamlProvider provider;
	private static File metaData ;
	private static String ldpMetaDataUrl;
	private static String providerEntityId = System.getenv("SERVICE_PROVIDER_ENTITY_ID");
	private static String postUrl = System.getenv("POST_URL");


	@BeforeAll
	public static void setupClass() {
		String samlCertificate = VaultProperties.getProperty("iu-endpoint.saml.certificate");
		String privateKey = VaultProperties.getProperty("iu-endpoint.saml.privateKey");
		ldpMetaDataUrl = VaultProperties.getProperty("iu.ldp.stg.metadata.url");
		HttpRequest request = IuException.unchecked(() -> HttpRequest.newBuilder().GET() //
				.uri(new URI(ldpMetaDataUrl)) //
				.build());

		final var response = IuException.unchecked(() -> HttpClient.newHttpClient().send(request, BodyHandlers.ofInputStream()));
		int statusCode = response.statusCode();
		if (statusCode == 200) {
			InputStream is = response.body();
			String xml = XmlDomUtil.xmlToString(is);
			metaData = IuException.unchecked(() -> File.createTempFile("idp-stg-metadta-test", ".xml"));
			BufferedWriter bw = IuException.unchecked(() -> new BufferedWriter( new FileWriter(metaData, true)));
			IuException.unchecked(() -> bw.write(xml));
			IuException.unchecked(()-> bw.close());
		}

		provider = IuSamlProvider.from(new IuSamlClient() {

			@Override
			public String getServiceProviderEntityId() {
				return providerEntityId;
			}

			@Override
			public List<URI> getMetaDataUris() {
				return Arrays.asList(metaData.toURI());
			}

			@Override
			public X509Certificate getCertificate() {
				return PemEncoded.parse(samlCertificate).next().asCertificate();
			}

			@Override
			public List<URI> getAcsUris() {
				return IuException.unchecked(()-> Arrays.asList(new URI(postUrl)));
			}

			@Override
			public String getPrivateKey() {
				return privateKey;
			}

			@Override
			public List<InetAddress> getAllowedRange() {
				return IuException.unchecked(() -> Arrays.asList(InetAddress.getByName("http://localhost:8080")));
			}

		}) ;
	}

	@Test
	public void testSamlAuthenication() throws Exception{
		URI entityId = IuException.unchecked(() -> new URI(ldpMetaDataUrl));
		URI postURL = IuException.unchecked(() -> new URI(postUrl));
		var sessionId = IdGenerator.generateId();

		URI location = provider.authRequest(entityId, postURL, sessionId);
		System.out.println("Location: " + location);
		final var cookieHandler = new CookieManager();
		final var http = HttpClient.newBuilder().cookieHandler(cookieHandler).build();
		final var initRequest = HttpRequest.newBuilder(location).build();
		final var initResponse = http.send(initRequest, BodyHandlers.ofString());
		assertEquals(302, initResponse.statusCode());	

		final var firstRedirectLocation = initResponse.headers().firstValue("Location").get();
		assertTrue(firstRedirectLocation.startsWith(location.getPath() + '?'), () -> firstRedirectLocation);
		System.out.println("Location: " + firstRedirectLocation);

		final var loginRequestUri = new URI(location.getScheme()+ "://" +
				location.getHost() + firstRedirectLocation);
		final var firstLoginRequest = HttpRequest.newBuilder(loginRequestUri).build();
		final var firstLoginResponse = http.send(firstLoginRequest, BodyHandlers.ofString());
		assertEquals(200, firstLoginResponse.statusCode());
		assertEquals("text/html;charset=utf-8", firstLoginResponse.headers().firstValue("Content-Type").get());

		final var parsedSecondLoginRedirectForm = Jsoup.parse(firstLoginResponse.body()).selectFirst("form");
		assertEquals(firstRedirectLocation, parsedSecondLoginRedirectForm.attr("action"));
		assertEquals("POST", parsedSecondLoginRedirectForm.attr("method").toUpperCase());

		final var secondLoginRedirectParams = new LinkedHashMap<String, Iterable<String>>();
		for (final var i : parsedSecondLoginRedirectForm.select("input[type='hidden']"))
			secondLoginRedirectParams.put(i.attr("name"), List.of(i.attr("value")));
		final var secondLoginRedirectQuery = IuWebUtils.createQueryString(secondLoginRedirectParams);

		final var secondLoginRequest = HttpRequest.newBuilder(loginRequestUri)
				.header("Content-Type", "application/x-www-form-urlencoded")
				.POST(BodyPublishers.ofString(secondLoginRedirectQuery)).build();
		final var secondLoginResponse = http.send(secondLoginRequest, BodyHandlers.ofString());
		assertEquals(302, secondLoginResponse.statusCode());
		final var secondRedirectLocation = secondLoginResponse.headers().firstValue("Location").get();
		assertTrue(secondRedirectLocation.startsWith(location.getPath() + '?'), () -> secondRedirectLocation);


		final var loginFormUri = new URI(
				location.getScheme()+ "://" +
						location.getHost() + secondRedirectLocation);

		final var loginFormRequest = HttpRequest.newBuilder(loginFormUri).build();
		final var loginFormResponse = http.send(loginFormRequest, BodyHandlers.ofString());

		assertEquals(200, loginFormResponse.statusCode());
		assertEquals("text/html;charset=utf-8", loginFormResponse.headers().firstValue("Content-Type").get());

		final var parsedLoginForm = Jsoup.parse(loginFormResponse.body()).getElementById("fm1");
		assertEquals(secondRedirectLocation, parsedLoginForm.attr("action"));
		assertEquals("POST", parsedLoginForm.attr("method").toUpperCase());

		final var loginFormParams = new LinkedHashMap<String, Iterable<String>>();
		loginFormParams.put("j_username", List.of(VaultProperties.getProperty("test.username")));
		loginFormParams.put("j_password", List.of(VaultProperties.getProperty("test.password")));
		loginFormParams.put("_eventId_proceed", List.of(""));
		final var loginFormQuery = IuWebUtils.createQueryString(loginFormParams);

		final var loginFormPost = HttpRequest.newBuilder(loginFormUri)
				.header("Content-Type", "application/x-www-form-urlencoded")
				.POST(BodyPublishers.ofString(loginFormQuery)).build();
		final var loginSuccessResponse = http.send(loginFormPost, BodyHandlers.ofString());
		assertEquals(200, loginSuccessResponse.statusCode());
		final var parsedLoginSuccessForm = Jsoup.parse(loginSuccessResponse.body()).selectFirst("form");
		assertEquals(postURL.toString(), parsedLoginSuccessForm.attr("action"));
		assertEquals("POST", parsedSecondLoginRedirectForm.attr("method").toUpperCase());

		final var loginSuccessParams = new LinkedHashMap<String, String>();
		for (final var i : parsedLoginSuccessForm.select("input[type='hidden']"))
			loginSuccessParams.put(i.attr("name"), i.attr("value"));

		//TODO verify relay state and saml response values
		String relayState = loginSuccessParams.get("RelayState");
		String samlResponse = loginSuccessParams.get("SAMLResponse");
		System.out.println(relayState);
		//JsonValue jsonValue =Json.createReader(new StringReader(SamlUtil.decrypt(relayState))).readValue();
		//System.out.println("relay state :: " + jsonValue.toString() );
		//provider.validate(null, acsurl, samlResponse);
		String jsonString = SamlUtil.decrypt(relayState);
		System.out.println(jsonString);

		JsonReader jsonReader = Json.createReader(new StringReader(jsonString));
		JsonObject relayStatejsonObject = jsonReader.readObject();
		String relayStateSessionId = relayStatejsonObject.getJsonString("sessionId").getString();
		String relayStatePostUrl = relayStatejsonObject.getJsonString("returnUrl").getString();

		assertEquals(sessionId,relayStateSessionId);
		assertEquals(postURL.toString(), relayStatePostUrl);
		
		provider.validate(InetAddress.getByName("http://localhost:8080"), postURL.toString(), samlResponse);
		metaData.delete();

	}
}
