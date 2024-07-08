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
package iu.auth.pki;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.security.cert.CertPathValidatorException;
import java.util.logging.Level;

import javax.security.auth.Subject;

import org.junit.jupiter.api.Test;

import edu.iu.IuException;
import edu.iu.auth.IuAuthenticationException;
import edu.iu.test.IuTestLogger;

@SuppressWarnings("javadoc")
public class CaVerifierTest extends PkiTestCase {

	@Test
	public void testSuccess() {
		final var ca = ca("{\"type\": \"ca\",\n"
				+ "    \"certificate\": \"MIICrDCCAiygAwIBAgIUHEiVzV965l0nHVAYi1dIeKmML1kwBQYDK2VxMIGcMQswCQYDVQQGEwJVUzEQMA4GA1UECAwHSW5kaWFuYTEUMBIGA1UEBwwLQmxvb21pbmd0b24xGzAZBgNVBAoMEkluZGlhbmEgVW5pdmVyc2l0eTEPMA0GA1UECwwGU1RBUkNIMTcwNQYDVQQDDC51cm46ZXhhbXBsZTppdS1qYXZhLWF1dGgtcGtpI0NhVmVyaWZpZXJUZXN0X0NBMCAXDTI0MDcwNjE0MDIzOFoYDzIxMjQwNzA3MTQwMjM4WjCBnDELMAkGA1UEBhMCVVMxEDAOBgNVBAgMB0luZGlhbmExFDASBgNVBAcMC0Jsb29taW5ndG9uMRswGQYDVQQKDBJJbmRpYW5hIFVuaXZlcnNpdHkxDzANBgNVBAsMBlNUQVJDSDE3MDUGA1UEAwwudXJuOmV4YW1wbGU6aXUtamF2YS1hdXRoLXBraSNDYVZlcmlmaWVyVGVzdF9DQTBDMAUGAytlcQM6ACSeF7pPWBC07nyuojAY0vlUsuG6hJUaOohXat0Y23uRYbmrQ22r8DfeYDd5JZ3cP/zzQJsvuHqHgKNjMGEwHQYDVR0OBBYEFB6mrTyspoYDGzxzaaX3f5VMo68rMB8GA1UdIwQYMBaAFB6mrTyspoYDGzxzaaX3f5VMo68rMBIGA1UdEwEB/wQIMAYBAf8CAQAwCwYDVR0PBAQDAgEGMAUGAytlcQNzAENFYR0QX22gpx9kzCFHLQ0tliVarWJy2b3BG2EmiJNV1M+asylmYTZTnqOIiDYHAFSnAmnjwKbRgN8gxHN8S8b2hl7/HKJ4Z7/PckH/vv2drAUB/rjxU2Z1OGkIILDzGvZVGpfUJJQvFfD2gUCKV+YJAA==\",\n"
				+ "    \"crl\": \"MIIBRTCBxjAFBgMrZXEwgZwxCzAJBgNVBAYTAlVTMRAwDgYDVQQIDAdJbmRpYW5hMRQwEgYDVQQHDAtCbG9vbWluZ3RvbjEbMBkGA1UECgwSSW5kaWFuYSBVbml2ZXJzaXR5MQ8wDQYDVQQLDAZTVEFSQ0gxNzA1BgNVBAMMLnVybjpleGFtcGxlOml1LWphdmEtYXV0aC1wa2kjQ2FWZXJpZmllclRlc3RfQ0EXDTI0MDcwNjE0MDMyMloYDzIxMjQwNzA2MTQwMzIyWjAFBgMrZXEDcwD+QFjJG69admRUQRuur67VR8+jpYPLlllYcTti9ZtIl4ZgyaQCNl3NHcvvRU3uS7IlB5UI+dsJvICkFg4gQNwBSEX7+9Poi8X45EUx7r3ncSCV5Lo6f+dnpjj3wj8KOMqU4wY3nI/MMM8dRH9UWblDAAA=\"\n"
				+ "}\n");

		final var pkp = pkp("{\n" //
				+ "    \"type\": \"pki\",\n" //
				+ "    \"alg\": \"ES384\",\n" //
				+ "    \"encrypt_alg\": \"ECDH-ES\",\n" //
				+ "    \"enc\": \"A192GCM\",\n" //
				+ "    \"jwk\": {\n" //
				+ "        \"kid\": \"urn:example:iu-java-auth-pki#CaVerifierTest\",\n" //
				+ "        \"x5c\": [\n" //
				+ "            \"MIIC0zCCAlOgAwIBAgIUfyo/6D3TdVk78rEIXbWGBVJPanswBQYDK2VxMIGcMQswCQYDVQQGEwJVUzEQMA4GA1UECAwHSW5kaWFuYTEUMBIGA1UEBwwLQmxvb21pbmd0b24xGzAZBgNVBAoMEkluZGlhbmEgVW5pdmVyc2l0eTEPMA0GA1UECwwGU1RBUkNIMTcwNQYDVQQDDC51cm46ZXhhbXBsZTppdS1qYXZhLWF1dGgtcGtpI0NhVmVyaWZpZXJUZXN0X0NBMCAXDTI0MDcwNjE0MDMyMVoYDzIxMjQwNzA3MTQwMzIxWjCBmTELMAkGA1UEBhMCVVMxEDAOBgNVBAgMB0luZGlhbmExFDASBgNVBAcMC0Jsb29taW5ndG9uMRswGQYDVQQKDBJJbmRpYW5hIFVuaXZlcnNpdHkxDzANBgNVBAsMBlNUQVJDSDE0MDIGA1UEAwwrdXJuOmV4YW1wbGU6aXUtamF2YS1hdXRoLXBraSNDYVZlcmlmaWVyVGVzdDB2MBAGByqGSM49AgEGBSuBBAAiA2IABJOVWqtwN4pcqFqHrmMRhg73zplKoE9S5M3rPhj5SUbdneqLeWweWOJxsWCCvfXQ5qzBP8tyd46j0TS0l4GmVHD3mwIthqgyvCVODGpbS0CNqHXr+WKoaJPpFRWVoWhQ5qNaMFgwCQYDVR0TBAIwADALBgNVHQ8EBAMCA4gwHQYDVR0OBBYEFHtr7wckQYMHOg8yxbdQnvxAOIaIMB8GA1UdIwQYMBaAFB6mrTyspoYDGzxzaaX3f5VMo68rMAUGAytlcQNzAL7fpNmJZjBmMknGbC5TX4uDBGLrBysUSU0gGJu5SrFYFCb0nbdospenBSji9Cc65aXADrrQldKGAIhOKbCDDc6pt7/32ub0oqJNXKm/pHQvJeRHJVWoLPYL1CURusVwdQxDzWgl7/0X2W39Su7qJcoiAA==\",\n" //
				+ "            \"MIICrDCCAiygAwIBAgIUHEiVzV965l0nHVAYi1dIeKmML1kwBQYDK2VxMIGcMQswCQYDVQQGEwJVUzEQMA4GA1UECAwHSW5kaWFuYTEUMBIGA1UEBwwLQmxvb21pbmd0b24xGzAZBgNVBAoMEkluZGlhbmEgVW5pdmVyc2l0eTEPMA0GA1UECwwGU1RBUkNIMTcwNQYDVQQDDC51cm46ZXhhbXBsZTppdS1qYXZhLWF1dGgtcGtpI0NhVmVyaWZpZXJUZXN0X0NBMCAXDTI0MDcwNjE0MDIzOFoYDzIxMjQwNzA3MTQwMjM4WjCBnDELMAkGA1UEBhMCVVMxEDAOBgNVBAgMB0luZGlhbmExFDASBgNVBAcMC0Jsb29taW5ndG9uMRswGQYDVQQKDBJJbmRpYW5hIFVuaXZlcnNpdHkxDzANBgNVBAsMBlNUQVJDSDE3MDUGA1UEAwwudXJuOmV4YW1wbGU6aXUtamF2YS1hdXRoLXBraSNDYVZlcmlmaWVyVGVzdF9DQTBDMAUGAytlcQM6ACSeF7pPWBC07nyuojAY0vlUsuG6hJUaOohXat0Y23uRYbmrQ22r8DfeYDd5JZ3cP/zzQJsvuHqHgKNjMGEwHQYDVR0OBBYEFB6mrTyspoYDGzxzaaX3f5VMo68rMB8GA1UdIwQYMBaAFB6mrTyspoYDGzxzaaX3f5VMo68rMBIGA1UdEwEB/wQIMAYBAf8CAQAwCwYDVR0PBAQDAgEGMAUGAytlcQNzAENFYR0QX22gpx9kzCFHLQ0tliVarWJy2b3BG2EmiJNV1M+asylmYTZTnqOIiDYHAFSnAmnjwKbRgN8gxHN8S8b2hl7/HKJ4Z7/PckH/vv2drAUB/rjxU2Z1OGkIILDzGvZVGpfUJJQvFfD2gUCKV+YJAA==\"\n" //
				+ "        ],\n" //
				+ "        \"kty\": \"EC\",\n" //
				+ "        \"crv\": \"P-384\",\n" //
				+ "        \"x\": \"k5Vaq3A3ilyoWoeuYxGGDvfOmUqgT1Lkzes-GPlJRt2d6ot5bB5Y4nGxYIK99dDm\",\n" //
				+ "        \"y\": \"rME_y3J3jqPRNLSXgaZUcPebAi2GqDK8JU4MaltLQI2odev5Yqhok-kVFZWhaFDm\",\n" //
				+ "        \"d\": \"BqSM0CUELcknfXL9IyP5oOjoFApwMdthfHrEdtkGFowzFq5rhqGrseSyfnCpU2Qw\"\n" //
				+ "    }\n" //
				+ "}");

		final var verifier = new CaVerifier(ca);
		assertNull(verifier.getAuthScheme());
		assertNull(verifier.getAuthenticationEndpoint());
		assertSame(PkiPrincipal.class, verifier.getType());
		assertEquals("urn:example:iu-java-auth-pki#CaVerifierTest_CA", verifier.getRealm());
		assertFalse(verifier.isAuthoritative());

		final var pki = new PkiPrincipal(pkp);
		assertEquals(pki, verifier.getPrincipal(pkp));

		IuTestLogger.expect(CaVerifier.class.getName(), Level.INFO,
				"ca:verify:urn:example:iu-java-auth-pki#CaVerifierTest; trustAnchor: urn:example:iu-java-auth-pki#CaVerifierTest_CA");
		assertDoesNotThrow(() -> verifier.verify(pki));
	}

	@Test
	public void testConstructorInvalidCACert() {
		final var ca = ca("{\"type\": \"ca\",\n"
				+ "    \"certificate\": \"MIICTjCCAgCgAwIBAgIUIpAh/XoxiVfoSbow/bxASSJDipQwBQYDK2VwMIGYMQswCQYDVQQGEwJVUzEQMA4GA1UECAwHSW5kaWFuYTEUMBIGA1UEBwwLQmxvb21pbmd0b24xGzAZBgNVBAoMEkluZGlhbmEgVW5pdmVyc2l0eTEPMA0GA1UECwwGU1RBUkNIMTMwMQYDVQQDDCp1cm46ZXhhbXBsZTppdS1qYXZhLWF1dGgtcGtpI0NhVmVyZmllclRlc3QwHhcNMjQwNzA1MTcwNDI4WhcNMjYxMDEzMTcwNDI4WjCBmDELMAkGA1UEBhMCVVMxEDAOBgNVBAgMB0luZGlhbmExFDASBgNVBAcMC0Jsb29taW5ndG9uMRswGQYDVQQKDBJJbmRpYW5hIFVuaXZlcnNpdHkxDzANBgNVBAsMBlNUQVJDSDEzMDEGA1UEAwwqdXJuOmV4YW1wbGU6aXUtamF2YS1hdXRoLXBraSNDYVZlcmZpZXJUZXN0MCowBQYDK2VwAyEAHfD9VtADehLQNuFadxxMo5dI2jXv8baqWmlBwWCRjvmjWjBYMB0GA1UdDgQWBBSd2y/9Rwr5uBXQIqH/eEUbkaeCmDAfBgNVHSMEGDAWgBSd2y/9Rwr5uBXQIqH/eEUbkaeCmDAJBgNVHRMEAjAAMAsGA1UdDwQEAwIHgDAFBgMrZXADQQC7tdfqfQPVEXHECjIK/k+sQgDV1dh900F+Q691IAMVKWAjH9NfSeykjaApD/xQ6j9HEtFEZ5C7vvOL6ZRfD34F\",\n"
				+ "    \"crl\": \"MIIBPzCBwDAFBgMrZXEwgZgxCzAJBgNVBAYTAlVTMRAwDgYDVQQIDAdJbmRpYW5hMRQwEgYDVQQHDAtCbG9vbWluZ3RvbjEbMBkGA1UECgwSSW5kaWFuYSBVbml2ZXJzaXR5MQ8wDQYDVQQLDAZTVEFSQ0gxMzAxBgNVBAMMKnVybjpleGFtcGxlOml1LWphdmEtYXV0aC1wa2kjQ2FWZXJmaWVyVGVzdBcNMjQwNzA1MTY1NTU0WhcNMjcxMDEzMTY1NTU0WjAFBgMrZXEDcwBKkYopYkYOUAIVUDx7YpTpJGRB0dhdiKyrpyYOC1jSWDm5jx+Fpiug/rMeyhCs4JeUlxqTQeGtmAAbNqCAXmpZuP5jJOrDn410gFiwgw/+ohngI5J0iSBTOC0BuccTb3u8/L7UM2i0y+tiCV+Ym8mRMwA=\"\n"
				+ "}\n");

		final var e = assertThrows(IllegalArgumentException.class, () -> new CaVerifier(ca));
		assertEquals("X.509 certificate is not a valid CA signing cert", e.getMessage(), () -> IuException.trace(e));
	}

	@Test
	public void testRejectsInvalid() {
		final var ca = ca("{\"type\": \"ca\",\n"
				+ "    \"certificate\": \"MIICpDCCAiSgAwIBAgIUKF3bSJnpQo2LAF8Jp5ajwwv+Zw8wBQYDK2VxMIGYMQswCQYDVQQGEwJVUzEQMA4GA1UECAwHSW5kaWFuYTEUMBIGA1UEBwwLQmxvb21pbmd0b24xGzAZBgNVBAoMEkluZGlhbmEgVW5pdmVyc2l0eTEPMA0GA1UECwwGU1RBUkNIMTMwMQYDVQQDDCp1cm46ZXhhbXBsZTppdS1qYXZhLWF1dGgtcGtpI0NhVmVyZmllclRlc3QwIBcNMjQwNzA1MTcwMTQ5WhgPMjEyNDA3MDYxNzAxNDlaMIGYMQswCQYDVQQGEwJVUzEQMA4GA1UECAwHSW5kaWFuYTEUMBIGA1UEBwwLQmxvb21pbmd0b24xGzAZBgNVBAoMEkluZGlhbmEgVW5pdmVyc2l0eTEPMA0GA1UECwwGU1RBUkNIMTMwMQYDVQQDDCp1cm46ZXhhbXBsZTppdS1qYXZhLWF1dGgtcGtpI0NhVmVyZmllclRlc3QwQzAFBgMrZXEDOgDdKYCu0k+z9jO6oHTfzHIdriCcmwnJFauIxJiaXbEfEoMOkGh6tZm9hJjVVRiF8cOiNyMbaDb514CjYzBhMB0GA1UdDgQWBBSWXyftXtwpQcl+Vw6015ORuHNF5TAfBgNVHSMEGDAWgBSWXyftXtwpQcl+Vw6015ORuHNF5TASBgNVHRMBAf8ECDAGAQH/AgEAMAsGA1UdDwQEAwIBBjAFBgMrZXEDcwD8FkUQli9NbXQGjLaQVC0yr7ToJJM6VBEacR0deNYB2n9MtOz5dJc46OV2kUW/G5yaishopXZkPoAINs/1otsPpH8f7qzSaxAluFJynvZlaADL+tcAB29aOn0FseA84QyYTKi/ezNp6wTrGaxTQFG2EgA=\",\n"
				+ "    \"crl\": \"MIIBQTCBwjAFBgMrZXEwgZgxCzAJBgNVBAYTAlVTMRAwDgYDVQQIDAdJbmRpYW5hMRQwEgYDVQQHDAtCbG9vbWluZ3RvbjEbMBkGA1UECgwSSW5kaWFuYSBVbml2ZXJzaXR5MQ8wDQYDVQQLDAZTVEFSQ0gxMzAxBgNVBAMMKnVybjpleGFtcGxlOml1LWphdmEtYXV0aC1wa2kjQ2FWZXJmaWVyVGVzdBcNMjQwNzA1MTcwMTUwWhgPMjEyNDA3MDYxNzAxNTBaMAUGAytlcQNzANZrFP6qeOhBIUiqjAf/FG3uojCAKJ+AuPbWIumoWJL6bEnvDsO0ORBrfTSGgutOH6IzuTokStZcABnI61O9gUg3ZYCUmdn+PMZ69+4y4SszshLznVg9rH4NY/66EF1pwhsQG1kI5jpj7yKX/b+anPESAA==\"\n"
				+ "}\n");

		final var verifier = new CaVerifier(ca);
		final var ipki = mock(PkiPrincipal.class);
		final var sub = new Subject();
		when(ipki.getSubject()).thenReturn(sub);
		var e = assertThrows(IllegalArgumentException.class, () -> verifier.verify(ipki));
		assertEquals("missing public key", e.getMessage());
	}

	@Test
	public void testRejectSelfSigned() {
		final var ca = ca("{\"type\": \"ca\",\n"
				+ "    \"certificate\": \"MIICpDCCAiSgAwIBAgIUKF3bSJnpQo2LAF8Jp5ajwwv+Zw8wBQYDK2VxMIGYMQswCQYDVQQGEwJVUzEQMA4GA1UECAwHSW5kaWFuYTEUMBIGA1UEBwwLQmxvb21pbmd0b24xGzAZBgNVBAoMEkluZGlhbmEgVW5pdmVyc2l0eTEPMA0GA1UECwwGU1RBUkNIMTMwMQYDVQQDDCp1cm46ZXhhbXBsZTppdS1qYXZhLWF1dGgtcGtpI0NhVmVyZmllclRlc3QwIBcNMjQwNzA1MTcwMTQ5WhgPMjEyNDA3MDYxNzAxNDlaMIGYMQswCQYDVQQGEwJVUzEQMA4GA1UECAwHSW5kaWFuYTEUMBIGA1UEBwwLQmxvb21pbmd0b24xGzAZBgNVBAoMEkluZGlhbmEgVW5pdmVyc2l0eTEPMA0GA1UECwwGU1RBUkNIMTMwMQYDVQQDDCp1cm46ZXhhbXBsZTppdS1qYXZhLWF1dGgtcGtpI0NhVmVyZmllclRlc3QwQzAFBgMrZXEDOgDdKYCu0k+z9jO6oHTfzHIdriCcmwnJFauIxJiaXbEfEoMOkGh6tZm9hJjVVRiF8cOiNyMbaDb514CjYzBhMB0GA1UdDgQWBBSWXyftXtwpQcl+Vw6015ORuHNF5TAfBgNVHSMEGDAWgBSWXyftXtwpQcl+Vw6015ORuHNF5TASBgNVHRMBAf8ECDAGAQH/AgEAMAsGA1UdDwQEAwIBBjAFBgMrZXEDcwD8FkUQli9NbXQGjLaQVC0yr7ToJJM6VBEacR0deNYB2n9MtOz5dJc46OV2kUW/G5yaishopXZkPoAINs/1otsPpH8f7qzSaxAluFJynvZlaADL+tcAB29aOn0FseA84QyYTKi/ezNp6wTrGaxTQFG2EgA=\",\n"
				+ "    \"crl\": \"MIIBQTCBwjAFBgMrZXEwgZgxCzAJBgNVBAYTAlVTMRAwDgYDVQQIDAdJbmRpYW5hMRQwEgYDVQQHDAtCbG9vbWluZ3RvbjEbMBkGA1UECgwSSW5kaWFuYSBVbml2ZXJzaXR5MQ8wDQYDVQQLDAZTVEFSQ0gxMzAxBgNVBAMMKnVybjpleGFtcGxlOml1LWphdmEtYXV0aC1wa2kjQ2FWZXJmaWVyVGVzdBcNMjQwNzA1MTcwMTUwWhgPMjEyNDA3MDYxNzAxNTBaMAUGAytlcQNzANZrFP6qeOhBIUiqjAf/FG3uojCAKJ+AuPbWIumoWJL6bEnvDsO0ORBrfTSGgutOH6IzuTokStZcABnI61O9gUg3ZYCUmdn+PMZ69+4y4SszshLznVg9rH4NY/66EF1pwhsQG1kI5jpj7yKX/b+anPESAA==\"\n"
				+ "}\n");

		final var verifier = new CaVerifier(ca);
		final var pkp = pkp("{\n" //
				+ "    \"type\": \"pki\",\n" //
				+ "    \"alg\": \"ES256\",\n" //
				+ "    \"encrypt_alg\": \"ECDH-ES\",\n" //
				+ "    \"enc\": \"A128GCM\",\n" //
				+ "    \"jwk\": {\n" //
				+ "        \"kid\": \"urn:example:iu-java-auth-pki#PkiVerifierTest\",\n" //
				+ "        \"x5c\": [\n" //
				+ "            \"MIICkzCCAjqgAwIBAgIUeLMwRUBjDv+RACyG/1fmX6io5LAwCgYIKoZIzj0EAwIwgZoxCzAJBgNVBAYTAlVTMRAwDgYDVQQIDAdJbmRpYW5hMRQwEgYDVQQHDAtCbG9vbWluZ3RvbjEbMBkGA1UECgwSSW5kaWFuYSBVbml2ZXJzaXR5MQ8wDQYDVQQLDAZTVEFSQ0gxNTAzBgNVBAMMLHVybjpleGFtcGxlOml1LWphdmEtYXV0aC1wa2kjUGtpVmVyaWZpZXJUZXN0MCAXDTI0MDcwNjEzNDkxNloYDzIxMjQwNzA3MTM0OTE2WjCBmjELMAkGA1UEBhMCVVMxEDAOBgNVBAgMB0luZGlhbmExFDASBgNVBAcMC0Jsb29taW5ndG9uMRswGQYDVQQKDBJJbmRpYW5hIFVuaXZlcnNpdHkxDzANBgNVBAsMBlNUQVJDSDE1MDMGA1UEAwwsdXJuOmV4YW1wbGU6aXUtamF2YS1hdXRoLXBraSNQa2lWZXJpZmllclRlc3QwWTATBgcqhkjOPQIBBggqhkjOPQMBBwNCAARJGytDL2FVBEPXlxj13r796S2WBw8BdpF/VsoOcx4WRJ/HxjAOlG37AtcsDPpLSFtZOcVW7udsL0tavTBBcH42o1owWDAdBgNVHQ4EFgQU17DMbu87jzO5/D7rOQYGkwDE9RwwHwYDVR0jBBgwFoAU17DMbu87jzO5/D7rOQYGkwDE9RwwCQYDVR0TBAIwADALBgNVHQ8EBAMCA4gwCgYIKoZIzj0EAwIDRwAwRAIgRwJofcxQy+2OXm/37icJRM75Cw13FBZeka2nc2fNe20CICwo+Ep0uNF0/5HcLmgrGVPo8ty9NAROa+tnS8WGhFef\"\n" //
				+ "        ],\n" //
				+ "        \"kty\": \"EC\",\n" //
				+ "        \"crv\": \"P-256\",\n" //
				+ "        \"x\": \"SRsrQy9hVQRD15cY9d6-_ektlgcPAXaRf1bKDnMeFkQ\",\n" //
				+ "        \"y\": \"n8fGMA6UbfsC1ywM-ktIW1k5xVbu52wvS1q9MEFwfjY\",\n" //
				+ "        \"d\": \"SjdbMcwnvr21XYPbz0Vd81siAvT-6VShEfo5_MacEQ8\"\n" //
				+ "    }\n" //
				+ "}");
		assertNull(verifier.getPrincipal(pkp));
		final var e = assertThrows(IllegalArgumentException.class, () -> verifier.verify(new PkiPrincipal(pkp)));
		assertEquals("issuer not trusted", e.getMessage(), () -> IuException.trace(e));
	}

	@Test
	public void testRejectRevoked() {
		final var ca = ca("{\"type\": \"ca\",\n"
				+ "    \"certificate\": \"MIICrDCCAiygAwIBAgIUHEiVzV965l0nHVAYi1dIeKmML1kwBQYDK2VxMIGcMQswCQYDVQQGEwJVUzEQMA4GA1UECAwHSW5kaWFuYTEUMBIGA1UEBwwLQmxvb21pbmd0b24xGzAZBgNVBAoMEkluZGlhbmEgVW5pdmVyc2l0eTEPMA0GA1UECwwGU1RBUkNIMTcwNQYDVQQDDC51cm46ZXhhbXBsZTppdS1qYXZhLWF1dGgtcGtpI0NhVmVyaWZpZXJUZXN0X0NBMCAXDTI0MDcwNjE0MDIzOFoYDzIxMjQwNzA3MTQwMjM4WjCBnDELMAkGA1UEBhMCVVMxEDAOBgNVBAgMB0luZGlhbmExFDASBgNVBAcMC0Jsb29taW5ndG9uMRswGQYDVQQKDBJJbmRpYW5hIFVuaXZlcnNpdHkxDzANBgNVBAsMBlNUQVJDSDE3MDUGA1UEAwwudXJuOmV4YW1wbGU6aXUtamF2YS1hdXRoLXBraSNDYVZlcmlmaWVyVGVzdF9DQTBDMAUGAytlcQM6ACSeF7pPWBC07nyuojAY0vlUsuG6hJUaOohXat0Y23uRYbmrQ22r8DfeYDd5JZ3cP/zzQJsvuHqHgKNjMGEwHQYDVR0OBBYEFB6mrTyspoYDGzxzaaX3f5VMo68rMB8GA1UdIwQYMBaAFB6mrTyspoYDGzxzaaX3f5VMo68rMBIGA1UdEwEB/wQIMAYBAf8CAQAwCwYDVR0PBAQDAgEGMAUGAytlcQNzAENFYR0QX22gpx9kzCFHLQ0tliVarWJy2b3BG2EmiJNV1M+asylmYTZTnqOIiDYHAFSnAmnjwKbRgN8gxHN8S8b2hl7/HKJ4Z7/PckH/vv2drAUB/rjxU2Z1OGkIILDzGvZVGpfUJJQvFfD2gUCKV+YJAA==\",\n"
				+ "    \"crl\": \"MIIBbjCB7zAFBgMrZXEwgZwxCzAJBgNVBAYTAlVTMRAwDgYDVQQIDAdJbmRpYW5hMRQwEgYDVQQHDAtCbG9vbWluZ3RvbjEbMBkGA1UECgwSSW5kaWFuYSBVbml2ZXJzaXR5MQ8wDQYDVQQLDAZTVEFSQ0gxNzA1BgNVBAMMLnVybjpleGFtcGxlOml1LWphdmEtYXV0aC1wa2kjQ2FWZXJpZmllclRlc3RfQ0EXDTI0MDcwNjE0MTUxMFoYDzIxMjQwNzA3MTQxNTEwWjAnMCUCFH8qP+g903VZO/KxCF21hgVST2p7Fw0yNDA3MDYxNDE1MDlaMAUGAytlcQNzADGENm8t2GDOIPjg9tAwim3X7aKXNln7zEzlyw4sYChBZAKoCrjbscC9WBoP8sy2EJIO0yDoRsHFAOxBjFpSk33gTRqzNxPvdrRqgqEY0by6jFVyKEKlUIdlcIRjU0bguVS6oFHRaMyQ9zfeTi7SRtwUAA==\"\n"
				+ "}\n");

		final var pkp = pkp("{\n" //
				+ "    \"type\": \"pki\",\n" //
				+ "    \"alg\": \"ES384\",\n" //
				+ "    \"encrypt_alg\": \"ECDH-ES\",\n" //
				+ "    \"enc\": \"A192GCM\",\n" //
				+ "    \"jwk\": {\n" //
				+ "        \"kid\": \"urn:example:iu-java-auth-pki#CaVerifierTest\",\n" //
				+ "        \"x5c\": [\n" //
				+ "            \"MIIC0zCCAlOgAwIBAgIUfyo/6D3TdVk78rEIXbWGBVJPanswBQYDK2VxMIGcMQswCQYDVQQGEwJVUzEQMA4GA1UECAwHSW5kaWFuYTEUMBIGA1UEBwwLQmxvb21pbmd0b24xGzAZBgNVBAoMEkluZGlhbmEgVW5pdmVyc2l0eTEPMA0GA1UECwwGU1RBUkNIMTcwNQYDVQQDDC51cm46ZXhhbXBsZTppdS1qYXZhLWF1dGgtcGtpI0NhVmVyaWZpZXJUZXN0X0NBMCAXDTI0MDcwNjE0MDMyMVoYDzIxMjQwNzA3MTQwMzIxWjCBmTELMAkGA1UEBhMCVVMxEDAOBgNVBAgMB0luZGlhbmExFDASBgNVBAcMC0Jsb29taW5ndG9uMRswGQYDVQQKDBJJbmRpYW5hIFVuaXZlcnNpdHkxDzANBgNVBAsMBlNUQVJDSDE0MDIGA1UEAwwrdXJuOmV4YW1wbGU6aXUtamF2YS1hdXRoLXBraSNDYVZlcmlmaWVyVGVzdDB2MBAGByqGSM49AgEGBSuBBAAiA2IABJOVWqtwN4pcqFqHrmMRhg73zplKoE9S5M3rPhj5SUbdneqLeWweWOJxsWCCvfXQ5qzBP8tyd46j0TS0l4GmVHD3mwIthqgyvCVODGpbS0CNqHXr+WKoaJPpFRWVoWhQ5qNaMFgwCQYDVR0TBAIwADALBgNVHQ8EBAMCA4gwHQYDVR0OBBYEFHtr7wckQYMHOg8yxbdQnvxAOIaIMB8GA1UdIwQYMBaAFB6mrTyspoYDGzxzaaX3f5VMo68rMAUGAytlcQNzAL7fpNmJZjBmMknGbC5TX4uDBGLrBysUSU0gGJu5SrFYFCb0nbdospenBSji9Cc65aXADrrQldKGAIhOKbCDDc6pt7/32ub0oqJNXKm/pHQvJeRHJVWoLPYL1CURusVwdQxDzWgl7/0X2W39Su7qJcoiAA==\",\n" //
				+ "            \"MIICrDCCAiygAwIBAgIUHEiVzV965l0nHVAYi1dIeKmML1kwBQYDK2VxMIGcMQswCQYDVQQGEwJVUzEQMA4GA1UECAwHSW5kaWFuYTEUMBIGA1UEBwwLQmxvb21pbmd0b24xGzAZBgNVBAoMEkluZGlhbmEgVW5pdmVyc2l0eTEPMA0GA1UECwwGU1RBUkNIMTcwNQYDVQQDDC51cm46ZXhhbXBsZTppdS1qYXZhLWF1dGgtcGtpI0NhVmVyaWZpZXJUZXN0X0NBMCAXDTI0MDcwNjE0MDIzOFoYDzIxMjQwNzA3MTQwMjM4WjCBnDELMAkGA1UEBhMCVVMxEDAOBgNVBAgMB0luZGlhbmExFDASBgNVBAcMC0Jsb29taW5ndG9uMRswGQYDVQQKDBJJbmRpYW5hIFVuaXZlcnNpdHkxDzANBgNVBAsMBlNUQVJDSDE3MDUGA1UEAwwudXJuOmV4YW1wbGU6aXUtamF2YS1hdXRoLXBraSNDYVZlcmlmaWVyVGVzdF9DQTBDMAUGAytlcQM6ACSeF7pPWBC07nyuojAY0vlUsuG6hJUaOohXat0Y23uRYbmrQ22r8DfeYDd5JZ3cP/zzQJsvuHqHgKNjMGEwHQYDVR0OBBYEFB6mrTyspoYDGzxzaaX3f5VMo68rMB8GA1UdIwQYMBaAFB6mrTyspoYDGzxzaaX3f5VMo68rMBIGA1UdEwEB/wQIMAYBAf8CAQAwCwYDVR0PBAQDAgEGMAUGAytlcQNzAENFYR0QX22gpx9kzCFHLQ0tliVarWJy2b3BG2EmiJNV1M+asylmYTZTnqOIiDYHAFSnAmnjwKbRgN8gxHN8S8b2hl7/HKJ4Z7/PckH/vv2drAUB/rjxU2Z1OGkIILDzGvZVGpfUJJQvFfD2gUCKV+YJAA==\"\n" //
				+ "        ],\n" //
				+ "        \"kty\": \"EC\",\n" //
				+ "        \"crv\": \"P-384\",\n" //
				+ "        \"x\": \"k5Vaq3A3ilyoWoeuYxGGDvfOmUqgT1Lkzes-GPlJRt2d6ot5bB5Y4nGxYIK99dDm\",\n" //
				+ "        \"y\": \"rME_y3J3jqPRNLSXgaZUcPebAi2GqDK8JU4MaltLQI2odev5Yqhok-kVFZWhaFDm\",\n" //
				+ "        \"d\": \"BqSM0CUELcknfXL9IyP5oOjoFApwMdthfHrEdtkGFowzFq5rhqGrseSyfnCpU2Qw\"\n" //
				+ "    }\n" //
				+ "}");

		final var verifier = new CaVerifier(ca);
		IuTestLogger.expect(CaVerifier.class.getName(), Level.INFO,
				"ca:invalid:urn:example:iu-java-auth-pki#CaVerifierTest_CA rejected urn:example:iu-java-auth-pki#CaVerifierTest",
				CertPathValidatorException.class);
		assertNull(verifier.getPrincipal(pkp));
		final var e = assertThrows(IuAuthenticationException.class, () -> verifier.verify(new PkiPrincipal(pkp)));
		assertInstanceOf(CertPathValidatorException.class, e.getCause(), () -> IuException.trace(e));
	}

	@Test
	public void testRejectUntrustedCa() {
		final var ca = ca("{\"type\": \"ca\",\n"
				+ "    \"certificate\": \"MIICrDCCAiygAwIBAgIUHEiVzV965l0nHVAYi1dIeKmML1kwBQYDK2VxMIGcMQswCQYDVQQGEwJVUzEQMA4GA1UECAwHSW5kaWFuYTEUMBIGA1UEBwwLQmxvb21pbmd0b24xGzAZBgNVBAoMEkluZGlhbmEgVW5pdmVyc2l0eTEPMA0GA1UECwwGU1RBUkNIMTcwNQYDVQQDDC51cm46ZXhhbXBsZTppdS1qYXZhLWF1dGgtcGtpI0NhVmVyaWZpZXJUZXN0X0NBMCAXDTI0MDcwNjE0MDIzOFoYDzIxMjQwNzA3MTQwMjM4WjCBnDELMAkGA1UEBhMCVVMxEDAOBgNVBAgMB0luZGlhbmExFDASBgNVBAcMC0Jsb29taW5ndG9uMRswGQYDVQQKDBJJbmRpYW5hIFVuaXZlcnNpdHkxDzANBgNVBAsMBlNUQVJDSDE3MDUGA1UEAwwudXJuOmV4YW1wbGU6aXUtamF2YS1hdXRoLXBraSNDYVZlcmlmaWVyVGVzdF9DQTBDMAUGAytlcQM6ACSeF7pPWBC07nyuojAY0vlUsuG6hJUaOohXat0Y23uRYbmrQ22r8DfeYDd5JZ3cP/zzQJsvuHqHgKNjMGEwHQYDVR0OBBYEFB6mrTyspoYDGzxzaaX3f5VMo68rMB8GA1UdIwQYMBaAFB6mrTyspoYDGzxzaaX3f5VMo68rMBIGA1UdEwEB/wQIMAYBAf8CAQAwCwYDVR0PBAQDAgEGMAUGAytlcQNzAENFYR0QX22gpx9kzCFHLQ0tliVarWJy2b3BG2EmiJNV1M+asylmYTZTnqOIiDYHAFSnAmnjwKbRgN8gxHN8S8b2hl7/HKJ4Z7/PckH/vv2drAUB/rjxU2Z1OGkIILDzGvZVGpfUJJQvFfD2gUCKV+YJAA==\",\n"
				+ "    \"crl\": \"MIIBbjCB7zAFBgMrZXEwgZwxCzAJBgNVBAYTAlVTMRAwDgYDVQQIDAdJbmRpYW5hMRQwEgYDVQQHDAtCbG9vbWluZ3RvbjEbMBkGA1UECgwSSW5kaWFuYSBVbml2ZXJzaXR5MQ8wDQYDVQQLDAZTVEFSQ0gxNzA1BgNVBAMMLnVybjpleGFtcGxlOml1LWphdmEtYXV0aC1wa2kjQ2FWZXJpZmllclRlc3RfQ0EXDTI0MDcwNjE0MTUxMFoYDzIxMjQwNzA3MTQxNTEwWjAnMCUCFH8qP+g903VZO/KxCF21hgVST2p7Fw0yNDA3MDYxNDE1MDlaMAUGAytlcQNzADGENm8t2GDOIPjg9tAwim3X7aKXNln7zEzlyw4sYChBZAKoCrjbscC9WBoP8sy2EJIO0yDoRsHFAOxBjFpSk33gTRqzNxPvdrRqgqEY0by6jFVyKEKlUIdlcIRjU0bguVS6oFHRaMyQ9zfeTi7SRtwUAA==\"\n"
				+ "}\n");

		final var pkp = pkp("{\n" //
				+ "    \"type\": \"pki\",\n" //
				+ "    \"alg\": \"ES384\",\n" //
				+ "    \"encrypt_alg\": \"ECDH-ES\",\n" //
				+ "    \"enc\": \"A192GCM\",\n" //
				+ "    \"jwk\": {\n" //
				+ "        \"kid\": \"urn:example:iu-java-auth-pki#CaVerifierTest_B\",\n" //
				+ "        \"x5c\": [\n" //
				+ "            \"MIIC1zCCAlegAwIBAgIUPeiEBh1w/2HuLcE9TDx/ecLN9j8wBQYDK2VxMIGeMQswCQYDVQQGEwJVUzEQMA4GA1UECAwHSW5kaWFuYTEUMBIGA1UEBwwLQmxvb21pbmd0b24xGzAZBgNVBAoMEkluZGlhbmEgVW5pdmVyc2l0eTEPMA0GA1UECwwGU1RBUkNIMTkwNwYDVQQDDDB1cm46ZXhhbXBsZTppdS1qYXZhLWF1dGgtcGtpI0NhVmVyaWZpZXJUZXN0X0NBX0IwIBcNMjQwNzA2MTQzOTQ0WhgPMjEyNDA3MDcxNDM5NDRaMIGbMQswCQYDVQQGEwJVUzEQMA4GA1UECAwHSW5kaWFuYTEUMBIGA1UEBwwLQmxvb21pbmd0b24xGzAZBgNVBAoMEkluZGlhbmEgVW5pdmVyc2l0eTEPMA0GA1UECwwGU1RBUkNIMTYwNAYDVQQDDC11cm46ZXhhbXBsZTppdS1qYXZhLWF1dGgtcGtpI0NhVmVyaWZpZXJUZXN0X0IwdjAQBgcqhkjOPQIBBgUrgQQAIgNiAAS2C8ksCyq8YpaiexaDWkSIBRU0LYfq2BlruMvu9hqLQytI6vmKkwt6aWYObKQuBKRpTvMomiOASRCOIW7nZJXVwS5l2Wc+GJztQlZ50RO56u96yo+ygVYnSfCO483vLJOjWjBYMAkGA1UdEwQCMAAwCwYDVR0PBAQDAgOIMB0GA1UdDgQWBBQsKwOFH1dIajWggpYB66kk1IWGxjAfBgNVHSMEGDAWgBR9AgDXu/eG6Np0xYjrDMe3uUTOmjAFBgMrZXEDcwC38l9b+AkNfyEGiNGK6jL8JyRt4ZmUiWa9D64a3fybWthnQnalBai7GNksO/cdZf4R8OtCBtG8vgAxLHpXyO7yXIze62I2v+8h/dLjPRt9OmfAyf5+hLl0kSocYdtEBeORydQX9I2e9cjNwxBtNIkWCwA=\",\n" //
				+ "            \"MIICsDCCAjCgAwIBAgIUY6KJx/0N3ViN83E76F3ZqlmxDc4wBQYDK2VxMIGeMQswCQYDVQQGEwJVUzEQMA4GA1UECAwHSW5kaWFuYTEUMBIGA1UEBwwLQmxvb21pbmd0b24xGzAZBgNVBAoMEkluZGlhbmEgVW5pdmVyc2l0eTEPMA0GA1UECwwGU1RBUkNIMTkwNwYDVQQDDDB1cm46ZXhhbXBsZTppdS1qYXZhLWF1dGgtcGtpI0NhVmVyaWZpZXJUZXN0X0NBX0IwIBcNMjQwNzA2MTQzOTMwWhgPMjEyNDA3MDcxNDM5MzBaMIGeMQswCQYDVQQGEwJVUzEQMA4GA1UECAwHSW5kaWFuYTEUMBIGA1UEBwwLQmxvb21pbmd0b24xGzAZBgNVBAoMEkluZGlhbmEgVW5pdmVyc2l0eTEPMA0GA1UECwwGU1RBUkNIMTkwNwYDVQQDDDB1cm46ZXhhbXBsZTppdS1qYXZhLWF1dGgtcGtpI0NhVmVyaWZpZXJUZXN0X0NBX0IwQzAFBgMrZXEDOgDmALMq6ppWlKwVLxynTb8kRyr0cJ1oodNnqxLI5OutBKhZU2GiEDzMMJJJ4r4067ro+ZVcYFLXSQCjYzBhMB0GA1UdDgQWBBR9AgDXu/eG6Np0xYjrDMe3uUTOmjAfBgNVHSMEGDAWgBR9AgDXu/eG6Np0xYjrDMe3uUTOmjASBgNVHRMBAf8ECDAGAQH/AgEAMAsGA1UdDwQEAwIBBjAFBgMrZXEDcwBOwtlxXND8AiGkKoMOnOkg/wcro61M3M2eGcYvINzpW069BzzVaGQFTSfbE37E8WOqGsiotsrAJAAwehOgcAvZzwfYAufVAklf6vhGQXUZrTSwZLxm2KisheGgncuibfBaRxhSAWGaffteKmpW6nzgPwA=\"\n" //
				+ "        ],\n" //
				+ "        \"kty\": \"EC\",\n" //
				+ "        \"crv\": \"P-384\",\n" //
				+ "        \"x\": \"tgvJLAsqvGKWonsWg1pEiAUVNC2H6tgZa7jL7vYai0MrSOr5ipMLemlmDmykLgSk\",\n" //
				+ "        \"y\": \"aU7zKJojgEkQjiFu52SV1cEuZdlnPhic7UJWedETuervesqPsoFWJ0nwjuPN7yyT\",\n" //
				+ "        \"d\": \"OJ_0goHBYHE5h3-FhLUEW6DszMC-gRzlv4QQkuI8vf--OA7UYVotK-8pkOiXE3dJ\"\n" //
				+ "    }\n" //
				+ "}");

		final var verifier = new CaVerifier(ca);
		assertNull(verifier.getPrincipal(pkp));
		final var e = assertThrows(IllegalArgumentException.class, () -> verifier.verify(new PkiPrincipal(pkp)));
		assertEquals("issuer not trusted", e.getMessage(), () -> IuException.trace(e));
	}

}
