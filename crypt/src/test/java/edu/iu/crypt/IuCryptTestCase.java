/*
 * Copyright © 2024 Indiana University
 * All rights reserved.
 *
 * BSD 3-Clause License
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 
 * - Redistributions of source code must retain the above copyright notice, this
 *   list of conditions and the following disclaimer.
 * 
 * - Redistributions in binary form must reproduce the above copyright notice,
 *   this list of conditions and the following disclaimer in the documentation
 *   and/or other materials provided with the distribution.
 * 
 * - Neither the name of the copyright holder nor the names of its
 *   contributors may be used to endorse or promote products derived from
 *   this software without specific prior written permission.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package edu.iu.crypt;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpResponse;
import java.security.cert.X509Certificate;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.MockedStatic;

import edu.iu.IdGenerator;
import edu.iu.IuException;
import edu.iu.IuText;
import edu.iu.client.IuHttp;
import iu.crypt.DigestUtils;

@SuppressWarnings("javadoc")
public class IuCryptTestCase {

	private static final String AUTH = IdGenerator.generateId();

	// Sample certificates for demonstration and testing purposes.
	// NOT FOR PRODUCTION USE
	protected static final String CERT_TEXT = "-----BEGIN CERTIFICATE-----\r\n" //
			+ "MIID5TCCAs2gAwIBAgIUDSy2fR7Mli1vvbswCfNcW8crSZYwDQYJKoZIhvcNAQEL\r\n"
			+ "BQAwgYAxCzAJBgNVBAYTAlVTMRAwDgYDVQQIDAdJbmRpYW5hMRQwEgYDVQQHDAtC\r\n"
			+ "bG9vbWluZ3RvbjEbMBkGA1UECgwSSW5kaWFuYSBVbml2ZXJzaXR5MQ8wDQYDVQQL\r\n"
			+ "DAZTVEFSQ0gxGzAZBgNVBAMMEml1LWphdmEtY3J5cHQtdGVzdDAgFw0yNDAzMTAx\r\n"
			+ "OTIxNDlaGA8yMTI0MDMxMTE5MjE0OVowgYAxCzAJBgNVBAYTAlVTMRAwDgYDVQQI\r\n"
			+ "DAdJbmRpYW5hMRQwEgYDVQQHDAtCbG9vbWluZ3RvbjEbMBkGA1UECgwSSW5kaWFu\r\n"
			+ "YSBVbml2ZXJzaXR5MQ8wDQYDVQQLDAZTVEFSQ0gxGzAZBgNVBAMMEml1LWphdmEt\r\n"
			+ "Y3J5cHQtdGVzdDCCASIwDQYJKoZIhvcNAQEBBQADggEPADCCAQoCggEBALL0kKuy\r\n"
			+ "9h1E6AqPrFu3dUvOb3f2fPjyqlyStGGk4P8rUljJd+QyubAapIF2Sq420a9Q7atp\r\n"
			+ "EBZiLeC0fV8VbrBTYSFp2Up3rxUcEVkDZKpCjbwZ16RZIenGZWYBkLQh5P/VjrUG\r\n"
			+ "HCD9QSnTy08yBLAFrnOzBRL0mLoLmRVbam47QUV98pNAsmZF0wxsrSp6pmMSnHGY\r\n"
			+ "zlWFX9/vnrSWGMSKy229hYKMfSbY76sJNt605JWK19A3NjgeMT0rWZcCHnpv1s63\r\n"
			+ "DWx2ZQuKVNgTZm5oftLPQ6Dj4PwqEo9aMqahnIYw8t37zbq3ZsZgL+4Hcu866YAe\r\n"
			+ "W0GhvZVeOd89zS8CAwEAAaNTMFEwHQYDVR0OBBYEFNVoqadb2L5DK9+5yJ3WPxQs\r\n"
			+ "Dv/dMB8GA1UdIwQYMBaAFNVoqadb2L5DK9+5yJ3WPxQsDv/dMA8GA1UdEwEB/wQF\r\n"
			+ "MAMBAf8wDQYJKoZIhvcNAQELBQADggEBADzre/3bkFb36eYUeTrun+334+9v3VM2\r\n"
			+ "S6Sa2ycrUqquA0igkVI7Bf6odne+rW8z3YVxlRLBqOfuqCz/XShv+NiSvGTe4oGd\r\n"
			+ "rZv1Uz6s8SaUgbrOD7CphrUpkXl10jLiOwK77bBQBXXIjiTgReVQlZj3ni9ysvUP\r\n"
			+ "j05uY1zNDU631DQSHUZkPDAv4t5rCS9atoznIGDLgkSRDYLSbGoX7/1qSUg/yZvl\r\n"
			+ "vJ2qfMhgmuzhrTOF4rGNOZmJ/eMarqBu3oRBdpsZzdGQehAoEqoVTgrnhZ7KdWKE\r\n"
			+ "U++EQOj4ZKOR2YyYTXuYGLNZZiJZs9U6GmI32qLnxQIlhl6wxDKvjMs=\r\n" //
			+ "-----END CERTIFICATE-----\r\n";

	protected static final String ANOTHER_CERT_TEXT = "-----BEGIN CERTIFICATE-----\r\n" //
			+ "MIIClzCCAhygAwIBAgIURBnmOnYrSqsKrszgC751/Iat0uEwCgYIKoZIzj0EAwIw\r\n" //
			+ "gYAxCzAJBgNVBAYTAlVTMRAwDgYDVQQIDAdJbmRpYW5hMRQwEgYDVQQHDAtCbG9v\r\n" //
			+ "bWluZ3RvbjEbMBkGA1UECgwSSW5kaWFuYSBVbml2ZXJzaXR5MQ8wDQYDVQQLDAZT\r\n" //
			+ "VEFSQ0gxGzAZBgNVBAMMEml1LWphdmEtY3J5cHQtdGVzdDAgFw0yNDAzMTAxOTE2\r\n" //
			+ "MjRaGA8yMTI0MDMxMTE5MTYyNFowgYAxCzAJBgNVBAYTAlVTMRAwDgYDVQQIDAdJ\r\n" //
			+ "bmRpYW5hMRQwEgYDVQQHDAtCbG9vbWluZ3RvbjEbMBkGA1UECgwSSW5kaWFuYSBV\r\n" //
			+ "bml2ZXJzaXR5MQ8wDQYDVQQLDAZTVEFSQ0gxGzAZBgNVBAMMEml1LWphdmEtY3J5\r\n" //
			+ "cHQtdGVzdDB2MBAGByqGSM49AgEGBSuBBAAiA2IABB21Lelr9GqaBwPWN9aNn+ms\r\n" //
			+ "rjbWINECr3iEkqnCKMta7Zii6Gg8cjmUiLgVIpPfAXGUIo8Jr6SPH+Vb6845xRVj\r\n" //
			+ "ls4Gd/mhzbs1UeBKORACUCwt2PKWiIJFPXMgTpEY+aNTMFEwHQYDVR0OBBYEFIol\r\n" //
			+ "C3PH9md71NuPiuJQXhDl888QMB8GA1UdIwQYMBaAFIolC3PH9md71NuPiuJQXhDl\r\n" //
			+ "888QMA8GA1UdEwEB/wQFMAMBAf8wCgYIKoZIzj0EAwIDaQAwZgIxAKHtm01BrBpO\r\n" //
			+ "+uNkzwxfsk8o5/Y3V31T53VN0N22+IMc2Fo0fX6EiRj7JUINzTJN/QIxAOKD0Dab\r\n" //
			+ "ieNBfzWg9IDvuGnDWNEzN0l6IrnHdnEwVDQUpzFNw8UjGz8ohdztRSVKlQ==\r\n" //
			+ "-----END CERTIFICATE-----\r\n";


	protected static final X509Certificate CERT = PemEncoded.parse(CERT_TEXT).next().asCertificate();
	protected static final byte[] CERT_S1 = IuException.unchecked(() -> DigestUtils.sha1(CERT.getEncoded()));
	protected static final byte[] CERT_S256 = IuException.unchecked(() -> DigestUtils.sha256(CERT.getEncoded()));

	protected static final X509Certificate ANOTHER_CERT = PemEncoded.parse(ANOTHER_CERT_TEXT).next().asCertificate();
	protected static final byte[] ANOTHER_CERT_S1 = IuException.unchecked(() -> DigestUtils.sha1(ANOTHER_CERT.getEncoded()));
	protected static final byte[] ANOTHER_CERT_S256 = IuException.unchecked(() -> DigestUtils.sha256(ANOTHER_CERT.getEncoded()));

	private MockedStatic<IuHttp> mockHttp;

	protected URI uri(String content) {
		final var response = mock(HttpResponse.class);
		when(response.statusCode()).thenReturn(200);
		when(response.body()).thenReturn(new ByteArrayInputStream(IuText.utf8(content)));

		final var uri = URI.create("test://" + AUTH + '/' + IdGenerator.generateId());
		mockHttp.when(() -> IuHttp.send(eq(uri), any())).thenReturn(response);
		return uri;
	}

	@BeforeAll
	public static void setupClass() {
		System.setProperty("iu.http.allowedUri", "test://" + AUTH);
	}

	@BeforeEach
	public void setup() {
		mockHttp = mockStatic(IuHttp.class);
		mockHttp.when(() -> IuHttp.get(any())).thenCallRealMethod();
		mockHttp.when(() -> IuHttp.get(any(), any())).thenCallRealMethod();
		mockHttp.when(() -> IuHttp.send(any(), any(), any())).thenCallRealMethod();
		mockHttp.when(() -> IuHttp.validate(any(), any())).thenCallRealMethod();
		mockHttp.when(() -> IuHttp.expectStatus(anyInt())).thenCallRealMethod();
		mockHttp.when(() -> IuHttp.checkHeaders(any())).thenCallRealMethod();
	}

	@AfterEach
	public void teardown() {
		mockHttp.close();
	}

}
