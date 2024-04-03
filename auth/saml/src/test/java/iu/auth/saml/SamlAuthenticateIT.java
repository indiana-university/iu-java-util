package iu.auth.saml;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.CookieManager;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

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

@EnabledIf("edu.iu.test.VaultProperties#isConfigured")
public class SamlAuthenticateIT {

	private static IuSamlProvider provider;

	@BeforeAll
	public static void setupClass() {
		String samlCertificate = VaultProperties.getProperty("iu-endpoint.saml.certificate");
		provider = IuSamlProvider.from(new IuSamlClient() {

			@Override
			public String getServiceProviderEntityId() {
				return "urn:iu:ess:applyiu-test";
			}

			@Override
			public List<URI> getMetaDataUrls() {
				// use httpClient and read metadata from https://idp-stg.login.iu.edu/idp/shibboleth.
				Path path = Paths.get("/java/open_source_projects/iu-java-util/auth/saml/src/test/java/resources/META-INF/idp-stg-metadata.xml");
				//C:\java\open_source_projects\iu-java-util\auth\saml\src\test\java\resources\META-INF\idp-stg-metadata.xml
				final var request =  IuException.unchecked(() -> HttpRequest.newBuilder().GET() //
						.uri(new URI("https://sisjee-stage.iu.edu/essweb-stg/web/sisad/trex/SAML2/SP")) //
						.build());

				final var response = IuException.unchecked(() -> HttpClient.newHttpClient().send(request, BodyHandlers.ofInputStream()));
				int statusCode = response.statusCode();
				if (statusCode == 200) {
				  InputStream is = response.body();
				  String xml = XmlDomUtil.xmlToString(is);
				// Creating a File object for the directory path
			        File directoryPath = new File(
			            "/java/open_source_projects/iu-java-util/auth/saml/src/test/java/resources/META-INF/");
				  File tempFile = IuException.unchecked(() -> File.createTempFile("idp-stg-metadta-test", ".xml",
                          directoryPath));
				  BufferedWriter bw = IuException.unchecked(() -> new BufferedWriter( new FileWriter(tempFile, true)));
				    IuException.unchecked(() -> bw.write(xml));
				    IuException.unchecked(()-> bw.close());
				  //tempFile.deleteOnExit();
				    return Arrays.asList(tempFile.toURI());
				}
				return null;
				
				//return  Arrays.asList(tempFile.toURI());

			}

			@Override
			public X509Certificate getCertificate() {
				PemEncoded.parse(samlCertificate).next().asCertificate();
				return null;
			}

			@Override
			public List<URI> getAcsUrls() {
				return IuException.unchecked(()-> Arrays.asList(new URI("test://saml-posturl")));
			}

			@Override
			public String getPrivateKey() {
				return IdGenerator.generateId();
			}

			
		}) ;
	}

	@Test
	public void testSamlAuthenication() throws Exception{
		URI entityId = IuException.unchecked(() -> new URI("https://idp-stg.login.iu.edu/idp/shibboleth"));
		URI postURL = IuException.unchecked(() -> new URI("test://saml-posturl"));
		URI location = provider.authorize(entityId, postURL);
		System.out.println("Location: " + location);
		final var cookieHandler = new CookieManager();
		final var http = HttpClient.newBuilder().cookieHandler(cookieHandler).build();
		final var initRequest = HttpRequest.newBuilder(location).build();
		final var initResponse = http.send(initRequest, BodyHandlers.ofString());
		
		assertEquals(302, initResponse.statusCode());	
	}
}
