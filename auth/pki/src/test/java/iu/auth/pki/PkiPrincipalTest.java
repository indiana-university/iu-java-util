/*
 * Copyright © 2025 Indiana University
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
package iu.auth.pki;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

import org.junit.jupiter.api.Test;

import edu.iu.IuException;
import edu.iu.IuIterable;
import edu.iu.client.IuJson;
import edu.iu.crypt.WebEncryption.Encryption;
import edu.iu.crypt.WebKey;
import edu.iu.crypt.WebKey.Algorithm;
import iu.crypt.CryptJsonAdapters;

@SuppressWarnings("javadoc")
public class PkiPrincipalTest extends PkiTestCase {

	@Test
	public void testRejectWithoutSignatureAlg() {
		final var e = assertThrows(NullPointerException.class, () -> new PkiPrincipal(pkp("{\n" //
				+ "    \"type\": \"pki\",\n" //
				+ "    \"jwk\": {\n" //
				+ "        \"kid\": \"urn:example:iu-java-auth-pki#PkiPrincipalTest\",\n" //
				+ "        \"x5c\": [\n" //
				+ "            \"MIICVjCCAgigAwIBAgIUAKviLLvb+6wKsnXvXNBLpKZKaCEwBQYDK2VwMIGbMQswCQYDVQQGEwJVUzEQMA4GA1UECAwHSW5kaWFuYTEUMBIGA1UEBwwLQmxvb21pbmd0b24xGzAZBgNVBAoMEkluZGlhbmEgVW5pdmVyc2l0eTEPMA0GA1UECwwGU1RBUkNIMTYwNAYDVQQDDC11cm46ZXhhbXBsZTppdS1qYXZhLWF1dGgtcGtpI1BraVByaW5jaXBhbFRlc3QwIBcNMjQwNzA1MjMwNjQ1WhgPMjEyNDA3MDYyMzA2NDVaMIGbMQswCQYDVQQGEwJVUzEQMA4GA1UECAwHSW5kaWFuYTEUMBIGA1UEBwwLQmxvb21pbmd0b24xGzAZBgNVBAoMEkluZGlhbmEgVW5pdmVyc2l0eTEPMA0GA1UECwwGU1RBUkNIMTYwNAYDVQQDDC11cm46ZXhhbXBsZTppdS1qYXZhLWF1dGgtcGtpI1BraVByaW5jaXBhbFRlc3QwKjAFBgMrZXADIQAib8PLP9Zbvzw9jmLUi5eTz0ki/hYrxiO8X/g0ba8cKaNaMFgwHQYDVR0OBBYEFEG+0oz0eEqo84ic5u6P7qVylJYbMB8GA1UdIwQYMBaAFEG+0oz0eEqo84ic5u6P7qVylJYbMAkGA1UdEwQCMAAwCwYDVR0PBAQDAgeAMAUGAytlcANBANb6jbbnHNSimeJ8+dVWwwGZtu8MrT7FcVL8zRMX8XWQsX4k19nSVPKHV+0Kj1NE4/P4y2bMW0BkRxLoZ+v0fg8=\"\n"
				+ "        ],\n" //
				+ "        \"kty\": \"OKP\",\n" //
				+ "        \"crv\": \"Ed25519\",\n" //
				+ "        \"x\": \"Im_Dyz_WW788PY5i1IuXk89JIv4WK8YjvF_4NG2vHCk\"\n" //
				+ "    }\n" //
				+ "}")));
		assertEquals("Missing digital signature algorithm", e.getMessage(), () -> IuException.trace(e));
	}

	@Test
	public void testRejectWithoutKeyUse() {
		final var e = assertThrows(IllegalArgumentException.class, () -> new PkiPrincipal(pkp("{\n" //
				+ "    \"type\": \"pki\",\n" //
				+ "    \"alg\": \"ES256\",\n" //
				+ "    \"encrypt_alg\": \"ECDH-ES\",\n" //
				+ "    \"enc\": \"A128GCM\",\n" //
				+ "    \"jwk\": {\n" //
				+ "        \"kid\": \"urn:example:iu-java-auth-pki#PkiPrincipalTest\",\n" //
				+ "        \"x5c\": [\n" //
				+ "            \"MIICljCCAjygAwIBAgIUXPG4oVqGnJvUVYnRJyieU6EiedwwCgYIKoZIzj0EAwIwgZsxCzAJBgNVBAYTAlVTMRAwDgYDVQQIDAdJbmRpYW5hMRQwEgYDVQQHDAtCbG9vbWluZ3RvbjEbMBkGA1UECgwSSW5kaWFuYSBVbml2ZXJzaXR5MQ8wDQYDVQQLDAZTVEFSQ0gxNjA0BgNVBAMMLXVybjpleGFtcGxlOml1LWphdmEtYXV0aC1wa2kjUGtpUHJpbmNpcGFsVGVzdDAgFw0yNDA3MDUyMzU2MjFaGA8yMTI0MDcwNjIzNTYyMVowgZsxCzAJBgNVBAYTAlVTMRAwDgYDVQQIDAdJbmRpYW5hMRQwEgYDVQQHDAtCbG9vbWluZ3RvbjEbMBkGA1UECgwSSW5kaWFuYSBVbml2ZXJzaXR5MQ8wDQYDVQQLDAZTVEFSQ0gxNjA0BgNVBAMMLXVybjpleGFtcGxlOml1LWphdmEtYXV0aC1wa2kjUGtpUHJpbmNpcGFsVGVzdDBZMBMGByqGSM49AgEGCCqGSM49AwEHA0IABJqkIAXxeAaUF57DYCGZlVi7ONyioF7hSHCrWlf/JbvRoveSwfeTJtZjmumeancGisZgTNSoJ2bMboI8kgXj0bSjWjBYMB0GA1UdDgQWBBTU5Qr5oXFUnquU9RusDHQc1ULdhzAfBgNVHSMEGDAWgBTU5Qr5oXFUnquU9RusDHQc1ULdhzAJBgNVHRMEAjAAMAsGA1UdDwQEAwIDCDAKBggqhkjOPQQDAgNIADBFAiBDBAZYCZyWa6MXWakuv8C3gylK0pVxDhfrZ095e3gY2gIhALiITwAYuSAao28ZFKcPNCDj4hLfp0A46zaq3VnhGHs7\"\n" //
				+ "        ],\n" //
				+ "        \"kty\": \"EC\",\n" //
				+ "        \"crv\": \"P-256\",\n" //
				+ "        \"x\": \"mqQgBfF4BpQXnsNgIZmVWLs43KKgXuFIcKtaV_8lu9E\",\n" //
				+ "        \"y\": \"oveSwfeTJtZjmumeancGisZgTNSoJ2bMboI8kgXj0bQ\"\n" //
				+ "    }\n" //
				+ "}\n")));
		assertEquals("X.509 certificate doesn't allow digital signature", e.getMessage(), () -> IuException.trace(e));
	}

	@Test
	public void testRejectWithWrongAlg() {
		final var e = assertThrows(IllegalArgumentException.class, () -> new PkiPrincipal(pkp("{\n" //
				+ "    \"type\": \"pki\",\n" //
				+ "    \"alg\": \"ES256\",\n" //
				+ "    \"jwk\": {\n" //
				+ "        \"kid\": \"urn:example:iu-java-auth-pki#PkiPrincipalTest\",\n" //
				+ "        \"x5c\": [\n" //
				+ "            \"MIICVjCCAgigAwIBAgIUAKviLLvb+6wKsnXvXNBLpKZKaCEwBQYDK2VwMIGbMQswCQYDVQQGEwJVUzEQMA4GA1UECAwHSW5kaWFuYTEUMBIGA1UEBwwLQmxvb21pbmd0b24xGzAZBgNVBAoMEkluZGlhbmEgVW5pdmVyc2l0eTEPMA0GA1UECwwGU1RBUkNIMTYwNAYDVQQDDC11cm46ZXhhbXBsZTppdS1qYXZhLWF1dGgtcGtpI1BraVByaW5jaXBhbFRlc3QwIBcNMjQwNzA1MjMwNjQ1WhgPMjEyNDA3MDYyMzA2NDVaMIGbMQswCQYDVQQGEwJVUzEQMA4GA1UECAwHSW5kaWFuYTEUMBIGA1UEBwwLQmxvb21pbmd0b24xGzAZBgNVBAoMEkluZGlhbmEgVW5pdmVyc2l0eTEPMA0GA1UECwwGU1RBUkNIMTYwNAYDVQQDDC11cm46ZXhhbXBsZTppdS1qYXZhLWF1dGgtcGtpI1BraVByaW5jaXBhbFRlc3QwKjAFBgMrZXADIQAib8PLP9Zbvzw9jmLUi5eTz0ki/hYrxiO8X/g0ba8cKaNaMFgwHQYDVR0OBBYEFEG+0oz0eEqo84ic5u6P7qVylJYbMB8GA1UdIwQYMBaAFEG+0oz0eEqo84ic5u6P7qVylJYbMAkGA1UdEwQCMAAwCwYDVR0PBAQDAgeAMAUGAytlcANBANb6jbbnHNSimeJ8+dVWwwGZtu8MrT7FcVL8zRMX8XWQsX4k19nSVPKHV+0Kj1NE4/P4y2bMW0BkRxLoZ+v0fg8=\"\n"
				+ "        ],\n" //
				+ "        \"kty\": \"OKP\",\n" //
				+ "        \"crv\": \"Ed25519\",\n" //
				+ "        \"x\": \"Im_Dyz_WW788PY5i1IuXk89JIv4WK8YjvF_4NG2vHCk\"\n" //
				+ "    }\n" //
				+ "}")));
		assertEquals("Invalid key type ED25519 for algorithm ES256", e.getMessage(), () -> IuException.trace(e));
	}

	@Test
	public void testRejectEncryptWithoutKeyUse() {
		final var e = assertThrows(IllegalArgumentException.class, () -> new PkiPrincipal(pkp("{\n" //
				+ "    \"type\": \"pki\",\n" //
				+ "    \"alg\": \"EdDSA\",\n" //
				+ "    \"encrypt_alg\": \"ECDH-ES\",\n" //
				+ "    \"enc\": \"A128GCM\",\n" //
				+ "    \"jwk\": {\n" //
				+ "        \"kid\": \"urn:example:iu-java-auth-pki#PkiPrincipalTest\",\n" //
				+ "        \"x5c\": [\n" //
				+ "            \"MIICVjCCAgigAwIBAgIUef1x7ga++7llmdwO1hPHBTeqiWcwBQYDK2VwMIGbMQswCQYDVQQGEwJVUzEQMA4GA1UECAwHSW5kaWFuYTEUMBIGA1UEBwwLQmxvb21pbmd0b24xGzAZBgNVBAoMEkluZGlhbmEgVW5pdmVyc2l0eTEPMA0GA1UECwwGU1RBUkNIMTYwNAYDVQQDDC11cm46ZXhhbXBsZTppdS1qYXZhLWF1dGgtcGtpI1BraVByaW5jaXBhbFRlc3QwIBcNMjQwNzA2MDExNjI2WhgPMjEyNDA3MDcwMTE2MjZaMIGbMQswCQYDVQQGEwJVUzEQMA4GA1UECAwHSW5kaWFuYTEUMBIGA1UEBwwLQmxvb21pbmd0b24xGzAZBgNVBAoMEkluZGlhbmEgVW5pdmVyc2l0eTEPMA0GA1UECwwGU1RBUkNIMTYwNAYDVQQDDC11cm46ZXhhbXBsZTppdS1qYXZhLWF1dGgtcGtpI1BraVByaW5jaXBhbFRlc3QwKjAFBgMrZXADIQByK7tn/52BeEq0+fVcpB2aFXUdJ5tnFKtwClCDbuqEDqNaMFgwHQYDVR0OBBYEFPr+gKWzhuhjJmcuUWqBaBWepa/BMB8GA1UdIwQYMBaAFPr+gKWzhuhjJmcuUWqBaBWepa/BMAkGA1UdEwQCMAAwCwYDVR0PBAQDAgeAMAUGAytlcANBAKCwY9Xvcc7NULUqtxqqjdEuXWhqc+EQi1Dp7mtklwWr7DnrBU2CG7yZk/w1r8Iqo60Ehs4y7RwBYms+avGWcgw=\"\n" //
				+ "        ],\n" //
				+ "        \"kty\": \"OKP\",\n" //
				+ "        \"crv\": \"Ed25519\",\n" //
				+ "        \"x\": \"ciu7Z_-dgXhKtPn1XKQdmhV1HSebZxSrcApQg27qhA4\"\n" //
				+ "    }\n" //
				+ "}")));
		assertEquals("X.509 certificate doesn't allow encryption", e.getMessage(), () -> IuException.trace(e));
	}

	@Test
	public void testRejectEncryptWrongKeyType() {
		final var e = assertThrows(IllegalArgumentException.class, () -> new PkiPrincipal(pkp("{\n" //
				+ "    \"type\": \"pki\",\n" //
				+ "    \"alg\": \"ES256\",\n" //
				+ "    \"encrypt_alg\": \"RSA-OAEP\",\n" //
				+ "    \"enc\": \"A128GCM\",\n" //
				+ "    \"jwk\": {\n" //
				+ "        \"kid\": \"urn:example:iu-java-auth-pki#PkiPrincipalTest\",\n" //
				+ "        \"x5c\": [\n" //
				+ "            \"MIIClTCCAjygAwIBAgIUPIS7Hs0RIMI9SHFgZ0ewu5HH8tYwCgYIKoZIzj0EAwIwgZsxCzAJBgNVBAYTAlVTMRAwDgYDVQQIDAdJbmRpYW5hMRQwEgYDVQQHDAtCbG9vbWluZ3RvbjEbMBkGA1UECgwSSW5kaWFuYSBVbml2ZXJzaXR5MQ8wDQYDVQQLDAZTVEFSQ0gxNjA0BgNVBAMMLXVybjpleGFtcGxlOml1LWphdmEtYXV0aC1wa2kjUGtpUHJpbmNpcGFsVGVzdDAgFw0yNDA3MDYwMTE5NTFaGA8yMTI0MDcwNzAxMTk1MVowgZsxCzAJBgNVBAYTAlVTMRAwDgYDVQQIDAdJbmRpYW5hMRQwEgYDVQQHDAtCbG9vbWluZ3RvbjEbMBkGA1UECgwSSW5kaWFuYSBVbml2ZXJzaXR5MQ8wDQYDVQQLDAZTVEFSQ0gxNjA0BgNVBAMMLXVybjpleGFtcGxlOml1LWphdmEtYXV0aC1wa2kjUGtpUHJpbmNpcGFsVGVzdDBZMBMGByqGSM49AgEGCCqGSM49AwEHA0IABCUU6R+LLrUxOjfPmgxaxJFnoHhJUXbZw2y/FzyjBx7d3WFZ/xObQd6FA5F9DSG4rFGUO6wdWtUTwWZvMLekBcyjWjBYMB0GA1UdDgQWBBQK79XBNda7y1P+zFdHvccI169GHTAfBgNVHSMEGDAWgBQK79XBNda7y1P+zFdHvccI169GHTAJBgNVHRMEAjAAMAsGA1UdDwQEAwIDiDAKBggqhkjOPQQDAgNHADBEAiAKuV8Q3gBXx0rnqEbTDun+x3k625umeaqGOtuy2CSt7wIgXtBy89JWyZ+yg9qp0WyZ4lu1vJLrDqqUWzmnVgGTmTQ=\"\n" //
				+ "        ],\n" //
				+ "        \"kty\": \"EC\",\n" //
				+ "        \"crv\": \"P-256\",\n" //
				+ "        \"x\": \"JRTpH4sutTE6N8-aDFrEkWegeElRdtnDbL8XPKMHHt0\",\n" //
				+ "        \"y\": \"3WFZ_xObQd6FA5F9DSG4rFGUO6wdWtUTwWZvMLekBcw\"\n" //
				+ "    }\n" //
				+ "}")));
		assertEquals("Invalid key type EC_P256 for algorithm RSA_OAEP", e.getMessage(), () -> IuException.trace(e));
	}

	private static Predicate<Object> keyWithId(String id) {
		return key -> (key instanceof WebKey) && id.equals(((WebKey) key).getKeyId());
	}

	@Test
	public void testWellKnown() {
		// For demonstration only, not for production use
		final var pki = new PkiPrincipal(pkp("{\n" //
				+ "    \"type\": \"pki\",\n" //
				+ "    \"alg\": \"EdDSA\",\n" //
				+ "    \"encrypt_alg\": null,\n" //
				+ "    \"enc\": null,\n" //
				+ "    \"jwk\": {\n" //
				+ "        \"kid\": \"urn:example:iu-java-auth-pki#PkiPrincipalTest\",\n" //
				+ "        \"x5c\": [\n" //
				+ "            \"MIICVjCCAgigAwIBAgIUAKviLLvb+6wKsnXvXNBLpKZKaCEwBQYDK2VwMIGbMQswCQYDVQQGEwJVUzEQMA4GA1UECAwHSW5kaWFuYTEUMBIGA1UEBwwLQmxvb21pbmd0b24xGzAZBgNVBAoMEkluZGlhbmEgVW5pdmVyc2l0eTEPMA0GA1UECwwGU1RBUkNIMTYwNAYDVQQDDC11cm46ZXhhbXBsZTppdS1qYXZhLWF1dGgtcGtpI1BraVByaW5jaXBhbFRlc3QwIBcNMjQwNzA1MjMwNjQ1WhgPMjEyNDA3MDYyMzA2NDVaMIGbMQswCQYDVQQGEwJVUzEQMA4GA1UECAwHSW5kaWFuYTEUMBIGA1UEBwwLQmxvb21pbmd0b24xGzAZBgNVBAoMEkluZGlhbmEgVW5pdmVyc2l0eTEPMA0GA1UECwwGU1RBUkNIMTYwNAYDVQQDDC11cm46ZXhhbXBsZTppdS1qYXZhLWF1dGgtcGtpI1BraVByaW5jaXBhbFRlc3QwKjAFBgMrZXADIQAib8PLP9Zbvzw9jmLUi5eTz0ki/hYrxiO8X/g0ba8cKaNaMFgwHQYDVR0OBBYEFEG+0oz0eEqo84ic5u6P7qVylJYbMB8GA1UdIwQYMBaAFEG+0oz0eEqo84ic5u6P7qVylJYbMAkGA1UdEwQCMAAwCwYDVR0PBAQDAgeAMAUGAytlcANBANb6jbbnHNSimeJ8+dVWwwGZtu8MrT7FcVL8zRMX8XWQsX4k19nSVPKHV+0Kj1NE4/P4y2bMW0BkRxLoZ+v0fg8=\"\n"
				+ "        ],\n" //
				+ "        \"kty\": \"OKP\",\n" //
				+ "        \"crv\": \"Ed25519\",\n" //
				+ "        \"x\": \"Im_Dyz_WW788PY5i1IuXk89JIv4WK8YjvF_4NG2vHCk\"\n" //
				+ "    }\n" //
				+ "}"));
		assertEquals("urn:example:iu-java-auth-pki#PkiPrincipalTest", pki.getName());
		final var sub = pki.getSubject();
		assertEquals(Set.of(pki), sub.getPrincipals());
		assertEquals(Set.of(), sub.getPrivateCredentials());

		final var pub = sub.getPublicCredentials();
		assertEquals(1, pub.size());
		assertTrue(pub.stream().anyMatch(keyWithId("verify")));
		assertFalse(pub.stream().anyMatch(keyWithId("encrypt")));

		final var now = Instant.now();
		assertTrue(now.isAfter(pki.getAuthTime()));
		assertFalse(now.isBefore(pki.getIssuedAt()));
		assertTrue(now.isBefore(pki.getExpires()));

		assertEquals("Well-Known PKI Principal urn:example:iu-java-auth-pki#PkiPrincipalTest, Self-Issued",
				pki.toString());
	}

	@Test
	public void testAudInternal() {
		// For demonstration only, not for production use
		final var pki = new PkiPrincipal(pkp("{\n" //
				+ "    \"type\": \"pki\",\n" //
				+ "    \"alg\": \"EdDSA\",\n" //
				+ "    \"encrypt_alg\": null,\n" //
				+ "    \"enc\": null,\n" //
				+ "    \"jwk\": {\n" //
				+ "        \"kid\": \"urn:example:iu-java-auth-pki#PkiPrincipalTest\",\n" //
				+ "        \"x5c\": [\n" //
				+ "            \"MIICVjCCAgigAwIBAgIUAKviLLvb+6wKsnXvXNBLpKZKaCEwBQYDK2VwMIGbMQswCQYDVQQGEwJVUzEQMA4GA1UECAwHSW5kaWFuYTEUMBIGA1UEBwwLQmxvb21pbmd0b24xGzAZBgNVBAoMEkluZGlhbmEgVW5pdmVyc2l0eTEPMA0GA1UECwwGU1RBUkNIMTYwNAYDVQQDDC11cm46ZXhhbXBsZTppdS1qYXZhLWF1dGgtcGtpI1BraVByaW5jaXBhbFRlc3QwIBcNMjQwNzA1MjMwNjQ1WhgPMjEyNDA3MDYyMzA2NDVaMIGbMQswCQYDVQQGEwJVUzEQMA4GA1UECAwHSW5kaWFuYTEUMBIGA1UEBwwLQmxvb21pbmd0b24xGzAZBgNVBAoMEkluZGlhbmEgVW5pdmVyc2l0eTEPMA0GA1UECwwGU1RBUkNIMTYwNAYDVQQDDC11cm46ZXhhbXBsZTppdS1qYXZhLWF1dGgtcGtpI1BraVByaW5jaXBhbFRlc3QwKjAFBgMrZXADIQAib8PLP9Zbvzw9jmLUi5eTz0ki/hYrxiO8X/g0ba8cKaNaMFgwHQYDVR0OBBYEFEG+0oz0eEqo84ic5u6P7qVylJYbMB8GA1UdIwQYMBaAFEG+0oz0eEqo84ic5u6P7qVylJYbMAkGA1UdEwQCMAAwCwYDVR0PBAQDAgeAMAUGAytlcANBANb6jbbnHNSimeJ8+dVWwwGZtu8MrT7FcVL8zRMX8XWQsX4k19nSVPKHV+0Kj1NE4/P4y2bMW0BkRxLoZ+v0fg8=\"\n"
				+ "        ],\n" //
				+ "        \"kty\": \"OKP\",\n" //
				+ "        \"crv\": \"Ed25519\",\n" //
				+ "        \"x\": \"Im_Dyz_WW788PY5i1IuXk89JIv4WK8YjvF_4NG2vHCk\",\n" //
				+ "        \"d\": \"3lB1o3jwcv-j9qmwo9d2ct2EP94gYpzYobVed2QLpqA\"\n" //
				+ "    }\n" //
				+ "}"));
		assertEquals("urn:example:iu-java-auth-pki#PkiPrincipalTest", pki.getName());
		final var sub = pki.getSubject();
		assertEquals(Set.of(pki), sub.getPrincipals());

		final var priv = sub.getPrivateCredentials();
		assertEquals(1, priv.size());
		final var verify = (WebKey) priv.iterator().next();
		assertEquals("verify", verify.getKeyId());

		final var pub = sub.getPublicCredentials();
		assertEquals(1, pub.size());
		assertEquals(verify.wellKnown(), pub.iterator().next());

		final var now = Instant.now();
		assertTrue(now.isAfter(pki.getAuthTime()));
		assertFalse(now.isBefore(pki.getIssuedAt()));
		assertTrue(now.isBefore(pki.getExpires()));

		assertEquals("Authoritative PKI Principal urn:example:iu-java-auth-pki#PkiPrincipalTest, Self-Issued",
				pki.toString());
	}

	@Test
	public void testAudRestricted() {
		// For demonstration only, not for production use
		final var pki = new PkiPrincipal(pkp("{\n" //
				+ "    \"type\": \"pki\",\n" //
				+ "    \"alg\": \"ES256\",\n" //
				+ "    \"encrypt_alg\": \"ECDH-ES\",\n" //
				+ "    \"enc\": \"A128GCM\",\n" //
				+ "    \"jwk\": {\n" //
				+ "        \"kid\": \"urn:example:iu-java-auth-pki#PkiPrincipalTest\",\n" //
				+ "        \"x5c\": [\n" //
				+ "            \"MIIClzCCAjygAwIBAgIULDargtUW0DjpsOoPGh4ysNF+QA4wCgYIKoZIzj0EAwIwgZsxCzAJBgNVBAYTAlVTMRAwDgYDVQQIDAdJbmRpYW5hMRQwEgYDVQQHDAtCbG9vbWluZ3RvbjEbMBkGA1UECgwSSW5kaWFuYSBVbml2ZXJzaXR5MQ8wDQYDVQQLDAZTVEFSQ0gxNjA0BgNVBAMMLXVybjpleGFtcGxlOml1LWphdmEtYXV0aC1wa2kjUGtpUHJpbmNpcGFsVGVzdDAgFw0yNDA3MDYwMDIxMDlaGA8yMTI0MDcwNzAwMjEwOVowgZsxCzAJBgNVBAYTAlVTMRAwDgYDVQQIDAdJbmRpYW5hMRQwEgYDVQQHDAtCbG9vbWluZ3RvbjEbMBkGA1UECgwSSW5kaWFuYSBVbml2ZXJzaXR5MQ8wDQYDVQQLDAZTVEFSQ0gxNjA0BgNVBAMMLXVybjpleGFtcGxlOml1LWphdmEtYXV0aC1wa2kjUGtpUHJpbmNpcGFsVGVzdDBZMBMGByqGSM49AgEGCCqGSM49AwEHA0IABEEpIbyg2H6WaLGC1vv/JtdJnq7EZ+v1u/7grj2TDBkk54cCz2Vt+DT9HhntEmpSwJeoDd62L8ZVISw/7wytY7mjWjBYMB0GA1UdDgQWBBTq57y75qrVZVN5GWC8cnbDBJjLOTAfBgNVHSMEGDAWgBTq57y75qrVZVN5GWC8cnbDBJjLOTAJBgNVHRMEAjAAMAsGA1UdDwQEAwIDiDAKBggqhkjOPQQDAgNJADBGAiEA6hr6mhNrj3J+hm9DHi2j/0fV1V+o4imzxlRhgRC5P3cCIQDU08uWitXt7EiWLQds3+U0qDfFjJWtGhr6FhrEsmNYtA==\"\n" //
				+ "        ],\n" //
				+ "        \"kty\": \"EC\",\n" //
				+ "        \"crv\": \"P-256\",\n" //
				+ "        \"x\": \"QSkhvKDYfpZosYLW-_8m10mersRn6_W7_uCuPZMMGSQ\",\n" //
				+ "        \"y\": \"54cCz2Vt-DT9HhntEmpSwJeoDd62L8ZVISw_7wytY7k\",\n" //
				+ "        \"d\": \"oQw3WlpRjzG3EHLuy05dfD9SQaxcVbZibXD2M1oBZyE\"\n" //
				+ "    }\n" //
				+ "}\n"));
		assertEquals("urn:example:iu-java-auth-pki#PkiPrincipalTest", pki.getName());
		final var sub = pki.getSubject();
		assertEquals(Set.of(pki), sub.getPrincipals());

		final var priv = sub.getPrivateCredentials();
		assertEquals(2, priv.size());
		final var verify = (WebKey) priv.stream().filter(keyWithId("verify")).findAny().get();
		final var encrypt = (WebKey) priv.stream().filter(keyWithId("encrypt")).findAny().get();

		final var pub = sub.getPublicCredentials();
		assertEquals(2, pub.size());
		assertTrue(pub.contains(verify.wellKnown()));
		assertTrue(pub.contains(encrypt.wellKnown()));

		final var now = Instant.now();
		assertTrue(now.isAfter(pki.getAuthTime()));
		assertFalse(now.isBefore(pki.getIssuedAt()));
		assertTrue(now.isBefore(pki.getExpires()));

		assertEquals("Authoritative PKI Principal urn:example:iu-java-auth-pki#PkiPrincipalTest, Self-Issued",
				pki.toString());
	}

	@Test
	public void testWellKnownRestricted() {
		// For demonstration only, not for production use
		final var pki = new PkiPrincipal(pkp("{\n" //
				+ "    \"type\": \"pki\",\n" //
				+ "    \"alg\": \"ES256\",\n" //
				+ "    \"encrypt_alg\": \"ECDH-ES\",\n" //
				+ "    \"enc\": \"A128GCM\",\n" //
				+ "    \"jwk\": {\n" //
				+ "        \"kid\": \"urn:example:iu-java-auth-pki#PkiPrincipalTest\",\n" //
				+ "        \"x5c\": [\n" //
				+ "            \"MIIClzCCAjygAwIBAgIULDargtUW0DjpsOoPGh4ysNF+QA4wCgYIKoZIzj0EAwIwgZsxCzAJBgNVBAYTAlVTMRAwDgYDVQQIDAdJbmRpYW5hMRQwEgYDVQQHDAtCbG9vbWluZ3RvbjEbMBkGA1UECgwSSW5kaWFuYSBVbml2ZXJzaXR5MQ8wDQYDVQQLDAZTVEFSQ0gxNjA0BgNVBAMMLXVybjpleGFtcGxlOml1LWphdmEtYXV0aC1wa2kjUGtpUHJpbmNpcGFsVGVzdDAgFw0yNDA3MDYwMDIxMDlaGA8yMTI0MDcwNzAwMjEwOVowgZsxCzAJBgNVBAYTAlVTMRAwDgYDVQQIDAdJbmRpYW5hMRQwEgYDVQQHDAtCbG9vbWluZ3RvbjEbMBkGA1UECgwSSW5kaWFuYSBVbml2ZXJzaXR5MQ8wDQYDVQQLDAZTVEFSQ0gxNjA0BgNVBAMMLXVybjpleGFtcGxlOml1LWphdmEtYXV0aC1wa2kjUGtpUHJpbmNpcGFsVGVzdDBZMBMGByqGSM49AgEGCCqGSM49AwEHA0IABEEpIbyg2H6WaLGC1vv/JtdJnq7EZ+v1u/7grj2TDBkk54cCz2Vt+DT9HhntEmpSwJeoDd62L8ZVISw/7wytY7mjWjBYMB0GA1UdDgQWBBTq57y75qrVZVN5GWC8cnbDBJjLOTAfBgNVHSMEGDAWgBTq57y75qrVZVN5GWC8cnbDBJjLOTAJBgNVHRMEAjAAMAsGA1UdDwQEAwIDiDAKBggqhkjOPQQDAgNJADBGAiEA6hr6mhNrj3J+hm9DHi2j/0fV1V+o4imzxlRhgRC5P3cCIQDU08uWitXt7EiWLQds3+U0qDfFjJWtGhr6FhrEsmNYtA==\"\n" //
				+ "        ],\n" //
				+ "        \"kty\": \"EC\",\n" //
				+ "        \"crv\": \"P-256\",\n" //
				+ "        \"x\": \"QSkhvKDYfpZosYLW-_8m10mersRn6_W7_uCuPZMMGSQ\",\n" //
				+ "        \"y\": \"54cCz2Vt-DT9HhntEmpSwJeoDd62L8ZVISw_7wytY7k\"\n" //
				+ "    }\n" //
				+ "}\n"));
		assertEquals("urn:example:iu-java-auth-pki#PkiPrincipalTest", pki.getName());
		final var sub = pki.getSubject();
		assertEquals(Set.of(pki), sub.getPrincipals());
		assertEquals(Set.of(), sub.getPrivateCredentials());

		final var pub = sub.getPublicCredentials();
		assertEquals(2, pub.size());
		assertTrue(pub.stream().anyMatch(keyWithId("verify")));
		assertTrue(pub.stream().anyMatch(keyWithId("encrypt")));

		final var now = Instant.now();
		assertTrue(now.isAfter(pki.getAuthTime()));
		assertFalse(now.isBefore(pki.getIssuedAt()));
		assertTrue(now.isBefore(pki.getExpires()));

		assertEquals("Well-Known PKI Principal urn:example:iu-java-auth-pki#PkiPrincipalTest, Self-Issued",
				pki.toString());
	}

	@Test
	public void testAudDeveloper() {
		// For demonstration only, not for production use
		final var pki = new PkiPrincipal(pkp("{\n" //
				+ "    \"type\": \"pki\",\n" //
				+ "    \"alg\": \"ES384\",\n" //
				+ "    \"encrypt_alg\": \"ECDH-ES\",\n" //
				+ "    \"enc\": \"A192GCM\",\n" //
				+ "    \"jwk\": {\n" //
				+ "        \"kid\": \"urn:example:iu-java-auth-pki#PkiPrincipalTest\",\n" //
				+ "        \"x5c\": [\n" //
				+ "            \"MIIC1zCCAlegAwIBAgIUBvK+pPy+Dj/y5l8brmDRMnXGnA4wBQYDK2VxMIGeMQswCQYDVQQGEwJVUzEQMA4GA1UECAwHSW5kaWFuYTEUMBIGA1UEBwwLQmxvb21pbmd0b24xGzAZBgNVBAoMEkluZGlhbmEgVW5pdmVyc2l0eTEPMA0GA1UECwwGU1RBUkNIMTkwNwYDVQQDDDB1cm46ZXhhbXBsZTppdS1qYXZhLWF1dGgtcGtpI1BraVByaW5jaXBhbFRlc3RfQ0EwIBcNMjQwNzA2MDEyNjUxWhgPMjEyNDA3MDcwMTI2NTFaMIGbMQswCQYDVQQGEwJVUzEQMA4GA1UECAwHSW5kaWFuYTEUMBIGA1UEBwwLQmxvb21pbmd0b24xGzAZBgNVBAoMEkluZGlhbmEgVW5pdmVyc2l0eTEPMA0GA1UECwwGU1RBUkNIMTYwNAYDVQQDDC11cm46ZXhhbXBsZTppdS1qYXZhLWF1dGgtcGtpI1BraVByaW5jaXBhbFRlc3QwdjAQBgcqhkjOPQIBBgUrgQQAIgNiAAT3YZJMHgU6AWQ/3oy79J5XXnaVX9KjBGmJl/cdRImehmqTiyRSrX156llkWPY/r8UwyOY6key5yYUR9UsbHqjBxybZiCGPaH0sUGpLqkyXaIKXQo/9sjfLEu7gGW3ydNCjWjBYMAkGA1UdEwQCMAAwCwYDVR0PBAQDAgOIMB0GA1UdDgQWBBQX58Hl/1ICEebfVbCiGGZHQSFfBzAfBgNVHSMEGDAWgBSfNR6bNL4nz1FqZPSwZzTIR59uDjAFBgMrZXEDcwAw+qw6Eg2PGX0j/FfAetE/rA05a7GQ+KlZ5w3MeLWR5l8Jhl+VcDN87phNjllIlZY5MrmoU75XCYAZJ1ZDDk8cK10NdUxxHalu8ak7XIuodq+3kV0qGqgdPeiiOexwHM+1YBOBkFabcLSZ3Ex5W44kLwA=\",\n" //
				+ "            \"MIICsDCCAjCgAwIBAgIUMPncuDW4Ov8V7OpdZApQhar4J+YwBQYDK2VxMIGeMQswCQYDVQQGEwJVUzEQMA4GA1UECAwHSW5kaWFuYTEUMBIGA1UEBwwLQmxvb21pbmd0b24xGzAZBgNVBAoMEkluZGlhbmEgVW5pdmVyc2l0eTEPMA0GA1UECwwGU1RBUkNIMTkwNwYDVQQDDDB1cm46ZXhhbXBsZTppdS1qYXZhLWF1dGgtcGtpI1BraVByaW5jaXBhbFRlc3RfQ0EwIBcNMjQwNzA2MDEyMzM1WhgPMjEyNDA3MDcwMTIzMzVaMIGeMQswCQYDVQQGEwJVUzEQMA4GA1UECAwHSW5kaWFuYTEUMBIGA1UEBwwLQmxvb21pbmd0b24xGzAZBgNVBAoMEkluZGlhbmEgVW5pdmVyc2l0eTEPMA0GA1UECwwGU1RBUkNIMTkwNwYDVQQDDDB1cm46ZXhhbXBsZTppdS1qYXZhLWF1dGgtcGtpI1BraVByaW5jaXBhbFRlc3RfQ0EwQzAFBgMrZXEDOgBb6+Rqggvc6XDwNVAfpRuobEDslz3gq6p4X12TjpZEpHE8mfcFShNOcLWiAE8aFvw/iHaJl//4U4CjYzBhMB0GA1UdDgQWBBSfNR6bNL4nz1FqZPSwZzTIR59uDjAfBgNVHSMEGDAWgBSfNR6bNL4nz1FqZPSwZzTIR59uDjASBgNVHRMBAf8ECDAGAQH/AgEAMAsGA1UdDwQEAwIBBjAFBgMrZXEDcwANDIjFXGuOZi8T/d8FLxKn+oevuP7iahi/Voq5SZEBiKsYU7q1kO3jZjlBdkUyYGheA320iciVmQD2uqhsSTKCVK0F/etxKHJeeCrMP24Ad8zNUYLiiaobyyRXVE3oGEAJZ7Z10wJjVE8X7/P2Ge2vGQA=\"\n" //
				+ "        ],\n" //
				+ "        \"kty\": \"EC\",\n" //
				+ "        \"crv\": \"P-384\",\n" //
				+ "        \"x\": \"92GSTB4FOgFkP96Mu_SeV152lV_SowRpiZf3HUSJnoZqk4skUq19eepZZFj2P6_F\",\n" //
				+ "        \"y\": \"MMjmOpHsucmFEfVLGx6owccm2Yghj2h9LFBqS6pMl2iCl0KP_bI3yxLu4Blt8nTQ\",\n" //
				+ "        \"d\": \"IurZbxJ28TEA7C902yBr4lWX_ViUfJ3hLu4FifMyHgQ1RTT_R-f5db0-gHNPbmje\"\n" //
				+ "    }\n" //
				+ "}\n"));
		assertEquals("urn:example:iu-java-auth-pki#PkiPrincipalTest", pki.getName());
		final var sub = pki.getSubject();
		assertEquals(Set.of(pki), sub.getPrincipals());

		final var priv = sub.getPrivateCredentials();
		assertEquals(2, priv.size());
		final var verify = (WebKey) priv.stream().filter(keyWithId("verify")).findAny().get();
		final var encrypt = (WebKey) priv.stream().filter(keyWithId("encrypt")).findAny().get();

		final var pub = sub.getPublicCredentials();
		assertEquals(2, pub.size());
		assertTrue(pub.contains(verify.wellKnown()));
		assertTrue(pub.contains(encrypt.wellKnown()));

		final var now = Instant.now();
		assertTrue(now.isAfter(pki.getAuthTime()));
		assertFalse(now.isBefore(pki.getIssuedAt()));
		assertTrue(now.isBefore(pki.getExpires()));

		assertEquals(
				"Authoritative PKI Principal urn:example:iu-java-auth-pki#PkiPrincipalTest, Issued by urn:example:iu-java-auth-pki#PkiPrincipalTest_CA",
				pki.toString());
	}

	@Test
	public void testHashCodeEquals() {
		// For demonstration only, not for production use
		final var key1 = WebKey.parse("{\n" //
				+ "        \"kid\": \"urn:example:iu-java-auth-pki#PkiPrincipalTest\",\n" //
				+ "        \"x5c\": [\n" //
				+ "            \"MIIC1zCCAlegAwIBAgIUBvK+pPy+Dj/y5l8brmDRMnXGnA4wBQYDK2VxMIGeMQswCQYDVQQGEwJVUzEQMA4GA1UECAwHSW5kaWFuYTEUMBIGA1UEBwwLQmxvb21pbmd0b24xGzAZBgNVBAoMEkluZGlhbmEgVW5pdmVyc2l0eTEPMA0GA1UECwwGU1RBUkNIMTkwNwYDVQQDDDB1cm46ZXhhbXBsZTppdS1qYXZhLWF1dGgtcGtpI1BraVByaW5jaXBhbFRlc3RfQ0EwIBcNMjQwNzA2MDEyNjUxWhgPMjEyNDA3MDcwMTI2NTFaMIGbMQswCQYDVQQGEwJVUzEQMA4GA1UECAwHSW5kaWFuYTEUMBIGA1UEBwwLQmxvb21pbmd0b24xGzAZBgNVBAoMEkluZGlhbmEgVW5pdmVyc2l0eTEPMA0GA1UECwwGU1RBUkNIMTYwNAYDVQQDDC11cm46ZXhhbXBsZTppdS1qYXZhLWF1dGgtcGtpI1BraVByaW5jaXBhbFRlc3QwdjAQBgcqhkjOPQIBBgUrgQQAIgNiAAT3YZJMHgU6AWQ/3oy79J5XXnaVX9KjBGmJl/cdRImehmqTiyRSrX156llkWPY/r8UwyOY6key5yYUR9UsbHqjBxybZiCGPaH0sUGpLqkyXaIKXQo/9sjfLEu7gGW3ydNCjWjBYMAkGA1UdEwQCMAAwCwYDVR0PBAQDAgOIMB0GA1UdDgQWBBQX58Hl/1ICEebfVbCiGGZHQSFfBzAfBgNVHSMEGDAWgBSfNR6bNL4nz1FqZPSwZzTIR59uDjAFBgMrZXEDcwAw+qw6Eg2PGX0j/FfAetE/rA05a7GQ+KlZ5w3MeLWR5l8Jhl+VcDN87phNjllIlZY5MrmoU75XCYAZJ1ZDDk8cK10NdUxxHalu8ak7XIuodq+3kV0qGqgdPeiiOexwHM+1YBOBkFabcLSZ3Ex5W44kLwA=\",\n" //
				+ "            \"MIICsDCCAjCgAwIBAgIUMPncuDW4Ov8V7OpdZApQhar4J+YwBQYDK2VxMIGeMQswCQYDVQQGEwJVUzEQMA4GA1UECAwHSW5kaWFuYTEUMBIGA1UEBwwLQmxvb21pbmd0b24xGzAZBgNVBAoMEkluZGlhbmEgVW5pdmVyc2l0eTEPMA0GA1UECwwGU1RBUkNIMTkwNwYDVQQDDDB1cm46ZXhhbXBsZTppdS1qYXZhLWF1dGgtcGtpI1BraVByaW5jaXBhbFRlc3RfQ0EwIBcNMjQwNzA2MDEyMzM1WhgPMjEyNDA3MDcwMTIzMzVaMIGeMQswCQYDVQQGEwJVUzEQMA4GA1UECAwHSW5kaWFuYTEUMBIGA1UEBwwLQmxvb21pbmd0b24xGzAZBgNVBAoMEkluZGlhbmEgVW5pdmVyc2l0eTEPMA0GA1UECwwGU1RBUkNIMTkwNwYDVQQDDDB1cm46ZXhhbXBsZTppdS1qYXZhLWF1dGgtcGtpI1BraVByaW5jaXBhbFRlc3RfQ0EwQzAFBgMrZXEDOgBb6+Rqggvc6XDwNVAfpRuobEDslz3gq6p4X12TjpZEpHE8mfcFShNOcLWiAE8aFvw/iHaJl//4U4CjYzBhMB0GA1UdDgQWBBSfNR6bNL4nz1FqZPSwZzTIR59uDjAfBgNVHSMEGDAWgBSfNR6bNL4nz1FqZPSwZzTIR59uDjASBgNVHRMBAf8ECDAGAQH/AgEAMAsGA1UdDwQEAwIBBjAFBgMrZXEDcwANDIjFXGuOZi8T/d8FLxKn+oevuP7iahi/Voq5SZEBiKsYU7q1kO3jZjlBdkUyYGheA320iciVmQD2uqhsSTKCVK0F/etxKHJeeCrMP24Ad8zNUYLiiaobyyRXVE3oGEAJZ7Z10wJjVE8X7/P2Ge2vGQA=\"\n" //
				+ "        ],\n" //
				+ "        \"kty\": \"EC\",\n" //
				+ "        \"crv\": \"P-384\",\n" //
				+ "        \"x\": \"92GSTB4FOgFkP96Mu_SeV152lV_SowRpiZf3HUSJnoZqk4skUq19eepZZFj2P6_F\",\n" //
				+ "        \"y\": \"MMjmOpHsucmFEfVLGx6owccm2Yghj2h9LFBqS6pMl2iCl0KP_bI3yxLu4Blt8nTQ\",\n" //
				+ "        \"d\": \"IurZbxJ28TEA7C902yBr4lWX_ViUfJ3hLu4FifMyHgQ1RTT_R-f5db0-gHNPbmje\"\n" //
				+ "}\n");
		final var key2 = WebKey.parse("{\n" //
				+ "        \"kid\": \"urn:example:iu-java-auth-pki#PkiPrincipalTest\",\n" //
				+ "        \"x5c\": [\n" //
				+ "            \"MIIClzCCAjygAwIBAgIULDargtUW0DjpsOoPGh4ysNF+QA4wCgYIKoZIzj0EAwIwgZsxCzAJBgNVBAYTAlVTMRAwDgYDVQQIDAdJbmRpYW5hMRQwEgYDVQQHDAtCbG9vbWluZ3RvbjEbMBkGA1UECgwSSW5kaWFuYSBVbml2ZXJzaXR5MQ8wDQYDVQQLDAZTVEFSQ0gxNjA0BgNVBAMMLXVybjpleGFtcGxlOml1LWphdmEtYXV0aC1wa2kjUGtpUHJpbmNpcGFsVGVzdDAgFw0yNDA3MDYwMDIxMDlaGA8yMTI0MDcwNzAwMjEwOVowgZsxCzAJBgNVBAYTAlVTMRAwDgYDVQQIDAdJbmRpYW5hMRQwEgYDVQQHDAtCbG9vbWluZ3RvbjEbMBkGA1UECgwSSW5kaWFuYSBVbml2ZXJzaXR5MQ8wDQYDVQQLDAZTVEFSQ0gxNjA0BgNVBAMMLXVybjpleGFtcGxlOml1LWphdmEtYXV0aC1wa2kjUGtpUHJpbmNpcGFsVGVzdDBZMBMGByqGSM49AgEGCCqGSM49AwEHA0IABEEpIbyg2H6WaLGC1vv/JtdJnq7EZ+v1u/7grj2TDBkk54cCz2Vt+DT9HhntEmpSwJeoDd62L8ZVISw/7wytY7mjWjBYMB0GA1UdDgQWBBTq57y75qrVZVN5GWC8cnbDBJjLOTAfBgNVHSMEGDAWgBTq57y75qrVZVN5GWC8cnbDBJjLOTAJBgNVHRMEAjAAMAsGA1UdDwQEAwIDiDAKBggqhkjOPQQDAgNJADBGAiEA6hr6mhNrj3J+hm9DHi2j/0fV1V+o4imzxlRhgRC5P3cCIQDU08uWitXt7EiWLQds3+U0qDfFjJWtGhr6FhrEsmNYtA==\"\n" //
				+ "        ],\n" //
				+ "        \"kty\": \"EC\",\n" //
				+ "        \"crv\": \"P-256\",\n" //
				+ "        \"x\": \"QSkhvKDYfpZosYLW-_8m10mersRn6_W7_uCuPZMMGSQ\",\n" //
				+ "        \"y\": \"54cCz2Vt-DT9HhntEmpSwJeoDd62L8ZVISw_7wytY7k\"\n" //
				+ "}\n");

		final List<PkiPrincipal> principals = new ArrayList<>();
		for (final var key : IuIterable.iter(key1, key2))
			for (final var alg : IuIterable.iter(Algorithm.ES384, Algorithm.ES256)) {
				final var pkpBuilder = IuJson.object().add("type", "pki");
				IuJson.add(pkpBuilder, "alg", () -> alg, CryptJsonAdapters.ALG);
				pkpBuilder.addNull("encrypt_alg").addNull("enc");
				IuJson.add(pkpBuilder, "jwk", () -> key, CryptJsonAdapters.WEBKEY);
				principals.add(new PkiPrincipal(pkp(pkpBuilder.build().toString())));

				for (final var enc : IuIterable.iter(Encryption.A192GCM, Encryption.A256GCM))
					for (final var encryptAlg : IuIterable.iter(Algorithm.ECDH_ES, Algorithm.ECDH_ES_A128KW)) {
						final var pkpEncBuilder = IuJson.object().add("type", "pki");
						IuJson.add(pkpEncBuilder, "alg", () -> alg, CryptJsonAdapters.ALG);
						IuJson.add(pkpEncBuilder, "encrypt_alg", () -> encryptAlg, CryptJsonAdapters.ALG);
						IuJson.add(pkpEncBuilder, "enc", () -> enc, CryptJsonAdapters.ENC);
						IuJson.add(pkpEncBuilder, "jwk", () -> key, CryptJsonAdapters.WEBKEY);
						principals.add(new PkiPrincipal(pkp(pkpEncBuilder.build().toString())));
					}
			}

		for (int i = 0; i < principals.size(); i++)
			for (int j = 0; j < principals.size(); j++)
				if (i == j) {
					assertNotEquals(principals.get(i), new Object());
					assertEquals(principals.get(i), principals.get(j));
					assertEquals(principals.get(i).hashCode(), principals.get(j).hashCode());
				} else {
					assertNotEquals(principals.get(i), principals.get(j));
					assertNotEquals(principals.get(j), principals.get(i));
					assertNotEquals(principals.get(i).hashCode(), principals.get(j).hashCode());
				}
	}
}
