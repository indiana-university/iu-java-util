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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import edu.iu.IuException;
import edu.iu.auth.config.IuAuthenticationRealm;
import edu.iu.auth.config.IuCertificateAuthority;
import edu.iu.client.IuJson;

@SuppressWarnings("javadoc")
public class CaVerifierTest {

	@Test
	public void testConstructorSuccess() {
		final var ca = (IuCertificateAuthority) IuAuthenticationRealm.JSON.fromJson(IuJson.parse("{\"type\": \"ca\",\n"
				+ "    \"certificate\": \"MIICpDCCAiSgAwIBAgIUKF3bSJnpQo2LAF8Jp5ajwwv+Zw8wBQYDK2VxMIGYMQswCQYDVQQGEwJVUzEQMA4GA1UECAwHSW5kaWFuYTEUMBIGA1UEBwwLQmxvb21pbmd0b24xGzAZBgNVBAoMEkluZGlhbmEgVW5pdmVyc2l0eTEPMA0GA1UECwwGU1RBUkNIMTMwMQYDVQQDDCp1cm46ZXhhbXBsZTppdS1qYXZhLWF1dGgtcGtpI0NhVmVyZmllclRlc3QwIBcNMjQwNzA1MTcwMTQ5WhgPMjEyNDA3MDYxNzAxNDlaMIGYMQswCQYDVQQGEwJVUzEQMA4GA1UECAwHSW5kaWFuYTEUMBIGA1UEBwwLQmxvb21pbmd0b24xGzAZBgNVBAoMEkluZGlhbmEgVW5pdmVyc2l0eTEPMA0GA1UECwwGU1RBUkNIMTMwMQYDVQQDDCp1cm46ZXhhbXBsZTppdS1qYXZhLWF1dGgtcGtpI0NhVmVyZmllclRlc3QwQzAFBgMrZXEDOgDdKYCu0k+z9jO6oHTfzHIdriCcmwnJFauIxJiaXbEfEoMOkGh6tZm9hJjVVRiF8cOiNyMbaDb514CjYzBhMB0GA1UdDgQWBBSWXyftXtwpQcl+Vw6015ORuHNF5TAfBgNVHSMEGDAWgBSWXyftXtwpQcl+Vw6015ORuHNF5TASBgNVHRMBAf8ECDAGAQH/AgEAMAsGA1UdDwQEAwIBBjAFBgMrZXEDcwD8FkUQli9NbXQGjLaQVC0yr7ToJJM6VBEacR0deNYB2n9MtOz5dJc46OV2kUW/G5yaishopXZkPoAINs/1otsPpH8f7qzSaxAluFJynvZlaADL+tcAB29aOn0FseA84QyYTKi/ezNp6wTrGaxTQFG2EgA=\",\n"
				+ "    \"crl\": \"MIIBQTCBwjAFBgMrZXEwgZgxCzAJBgNVBAYTAlVTMRAwDgYDVQQIDAdJbmRpYW5hMRQwEgYDVQQHDAtCbG9vbWluZ3RvbjEbMBkGA1UECgwSSW5kaWFuYSBVbml2ZXJzaXR5MQ8wDQYDVQQLDAZTVEFSQ0gxMzAxBgNVBAMMKnVybjpleGFtcGxlOml1LWphdmEtYXV0aC1wa2kjQ2FWZXJmaWVyVGVzdBcNMjQwNzA1MTcwMTUwWhgPMjEyNDA3MDYxNzAxNTBaMAUGAytlcQNzANZrFP6qeOhBIUiqjAf/FG3uojCAKJ+AuPbWIumoWJL6bEnvDsO0ORBrfTSGgutOH6IzuTokStZcABnI61O9gUg3ZYCUmdn+PMZ69+4y4SszshLznVg9rH4NY/66EF1pwhsQG1kI5jpj7yKX/b+anPESAA==\"\n"
				+ "}\n"));

		final var verifier = new CaVerifier(ca);
		assertNull(verifier.getAuthScheme());
		assertNull(verifier.getAuthenticationEndpoint());
		assertSame(PkiPrincipal.class, verifier.getType());
		assertEquals("urn:example:iu-java-auth-pki#CaVerfierTest", verifier.getRealm());
		assertFalse(verifier.isAuthoritative());
	}

	@Test
	public void testConstructorInvalidCACert() {
		final var ca = (IuCertificateAuthority) IuAuthenticationRealm.JSON.fromJson(IuJson.parse("{\"type\": \"ca\",\n"
				+ "    \"certificate\": \"MIICTjCCAgCgAwIBAgIUIpAh/XoxiVfoSbow/bxASSJDipQwBQYDK2VwMIGYMQswCQYDVQQGEwJVUzEQMA4GA1UECAwHSW5kaWFuYTEUMBIGA1UEBwwLQmxvb21pbmd0b24xGzAZBgNVBAoMEkluZGlhbmEgVW5pdmVyc2l0eTEPMA0GA1UECwwGU1RBUkNIMTMwMQYDVQQDDCp1cm46ZXhhbXBsZTppdS1qYXZhLWF1dGgtcGtpI0NhVmVyZmllclRlc3QwHhcNMjQwNzA1MTcwNDI4WhcNMjYxMDEzMTcwNDI4WjCBmDELMAkGA1UEBhMCVVMxEDAOBgNVBAgMB0luZGlhbmExFDASBgNVBAcMC0Jsb29taW5ndG9uMRswGQYDVQQKDBJJbmRpYW5hIFVuaXZlcnNpdHkxDzANBgNVBAsMBlNUQVJDSDEzMDEGA1UEAwwqdXJuOmV4YW1wbGU6aXUtamF2YS1hdXRoLXBraSNDYVZlcmZpZXJUZXN0MCowBQYDK2VwAyEAHfD9VtADehLQNuFadxxMo5dI2jXv8baqWmlBwWCRjvmjWjBYMB0GA1UdDgQWBBSd2y/9Rwr5uBXQIqH/eEUbkaeCmDAfBgNVHSMEGDAWgBSd2y/9Rwr5uBXQIqH/eEUbkaeCmDAJBgNVHRMEAjAAMAsGA1UdDwQEAwIHgDAFBgMrZXADQQC7tdfqfQPVEXHECjIK/k+sQgDV1dh900F+Q691IAMVKWAjH9NfSeykjaApD/xQ6j9HEtFEZ5C7vvOL6ZRfD34F\",\n"
				+ "    \"crl\": \"MIIBPzCBwDAFBgMrZXEwgZgxCzAJBgNVBAYTAlVTMRAwDgYDVQQIDAdJbmRpYW5hMRQwEgYDVQQHDAtCbG9vbWluZ3RvbjEbMBkGA1UECgwSSW5kaWFuYSBVbml2ZXJzaXR5MQ8wDQYDVQQLDAZTVEFSQ0gxMzAxBgNVBAMMKnVybjpleGFtcGxlOml1LWphdmEtYXV0aC1wa2kjQ2FWZXJmaWVyVGVzdBcNMjQwNzA1MTY1NTU0WhcNMjcxMDEzMTY1NTU0WjAFBgMrZXEDcwBKkYopYkYOUAIVUDx7YpTpJGRB0dhdiKyrpyYOC1jSWDm5jx+Fpiug/rMeyhCs4JeUlxqTQeGtmAAbNqCAXmpZuP5jJOrDn410gFiwgw/+ohngI5J0iSBTOC0BuccTb3u8/L7UM2i0y+tiCV+Ym8mRMwA=\"\n"
				+ "}\n"));

		final var e = assertThrows(IllegalArgumentException.class, () -> new CaVerifier(ca));
		assertEquals("Not a CA certificate", e.getMessage(), () -> IuException.trace(e));
	}

	@Test
	public void testConstructorMissingKeyUsage() {
		final var ca = (IuCertificateAuthority) IuAuthenticationRealm.JSON.fromJson(IuJson.parse("{\"type\": \"ca\",\n"
				+ "    \"certificate\": \"MIIClzCCAhegAwIBAgIUVsMH6gkKSqiME24xl8l4DFZUe2IwBQYDK2VxMIGYMQswCQYDVQQGEwJVUzEQMA4GA1UECAwHSW5kaWFuYTEUMBIGA1UEBwwLQmxvb21pbmd0b24xGzAZBgNVBAoMEkluZGlhbmEgVW5pdmVyc2l0eTEPMA0GA1UECwwGU1RBUkNIMTMwMQYDVQQDDCp1cm46ZXhhbXBsZTppdS1qYXZhLWF1dGgtcGtpI0NhVmVyZmllclRlc3QwIBcNMjQwNzA1MTc0MDU2WhgPMjEyNDA3MDYxNzQwNTZaMIGYMQswCQYDVQQGEwJVUzEQMA4GA1UECAwHSW5kaWFuYTEUMBIGA1UEBwwLQmxvb21pbmd0b24xGzAZBgNVBAoMEkluZGlhbmEgVW5pdmVyc2l0eTEPMA0GA1UECwwGU1RBUkNIMTMwMQYDVQQDDCp1cm46ZXhhbXBsZTppdS1qYXZhLWF1dGgtcGtpI0NhVmVyZmllclRlc3QwQzAFBgMrZXEDOgDiChooeY9hamgcq9fyG4kUbh71af/O8Aa7azM6v6lLsQNDOI2P5jmZrEs+VLd0wjxXUJ0o+tutogCjVjBUMB0GA1UdDgQWBBQVBGPnRiNgX5ojnJM/hymX0h1jNTAfBgNVHSMEGDAWgBQVBGPnRiNgX5ojnJM/hymX0h1jNTASBgNVHRMBAf8ECDAGAQH/AgEAMAUGAytlcQNzACaHiAfwH1XPwXQX/fuU/tbkCsO8ARPqWwPJFZwd3Vry7CKt2YLoI8ryWukLcJQD301jfwChhub0ALRU7G175OzOyNi96Fy8UMVcoXNxgM05p1oeqsAr2Y9HsiAG94ciLwTGKI9UQrIZ4Nch63cckEA7AA==\",\n"
				+ "    \"crl\": \"MIIBQTCBwjAFBgMrZXEwgZgxCzAJBgNVBAYTAlVTMRAwDgYDVQQIDAdJbmRpYW5hMRQwEgYDVQQHDAtCbG9vbWluZ3RvbjEbMBkGA1UECgwSSW5kaWFuYSBVbml2ZXJzaXR5MQ8wDQYDVQQLDAZTVEFSQ0gxMzAxBgNVBAMMKnVybjpleGFtcGxlOml1LWphdmEtYXV0aC1wa2kjQ2FWZXJmaWVyVGVzdBcNMjQwNzA1MTc0MDU2WhgPMjEyNDA3MDYxNzQwNTZaMAUGAytlcQNzALA/uGLnESDli8lONcEh+tSlpNiyahEtN7GRqHKMIL0Kleg32D4FYYVdpC+kklATGAxaerZUbD9qgAJCGBeDwblRCtY7fllC0O1jK8qvihw/fTIz8I8M0quDEw3nuvnel84p9H0OXl1B3jLEMqkHcGEnAA==\"\n"
				+ "}\n"));

		final var e = assertThrows(IllegalArgumentException.class, () -> new CaVerifier(ca));
		assertEquals("Key usage doesn't permit certificate signing", e.getMessage(), () -> IuException.trace(e));
	}

	@Test
	public void testConstructorKeyUsageMissingCertSign() {
		final var ca = (IuCertificateAuthority) IuAuthenticationRealm.JSON.fromJson(IuJson.parse("{\"type\": \"ca\",\n"
				+ "    \"certificate\": \"MIICpDCCAiSgAwIBAgIUTgk+vbsdchT0s3/hy6fH07pgIc0wBQYDK2VxMIGYMQswCQYDVQQGEwJVUzEQMA4GA1UECAwHSW5kaWFuYTEUMBIGA1UEBwwLQmxvb21pbmd0b24xGzAZBgNVBAoMEkluZGlhbmEgVW5pdmVyc2l0eTEPMA0GA1UECwwGU1RBUkNIMTMwMQYDVQQDDCp1cm46ZXhhbXBsZTppdS1qYXZhLWF1dGgtcGtpI0NhVmVyZmllclRlc3QwIBcNMjQwNzA1MTc0NDQ4WhgPMjEyNDA3MDYxNzQ0NDhaMIGYMQswCQYDVQQGEwJVUzEQMA4GA1UECAwHSW5kaWFuYTEUMBIGA1UEBwwLQmxvb21pbmd0b24xGzAZBgNVBAoMEkluZGlhbmEgVW5pdmVyc2l0eTEPMA0GA1UECwwGU1RBUkNIMTMwMQYDVQQDDCp1cm46ZXhhbXBsZTppdS1qYXZhLWF1dGgtcGtpI0NhVmVyZmllclRlc3QwQzAFBgMrZXEDOgBpqshSesmM00AiG6pvEByy3X8B0yuYgTB9xsT6ZyrHFKMd12Dl5vb3vhTrRcmgXiqFCaRMUK7us4CjYzBhMB0GA1UdDgQWBBTRcV0xJr1nY5+LHGeddAuYnGEJ0zAfBgNVHSMEGDAWgBTRcV0xJr1nY5+LHGeddAuYnGEJ0zASBgNVHRMBAf8ECDAGAQH/AgEAMAsGA1UdDwQEAwIBAjAFBgMrZXEDcwAvXMV5E0c7Gn/Sg76/r1gwUDjWuCY6CYQSp92Xbz/USWjI87lNdiYuCdmxHNZ70qfe2crH/wT06QDrcHCkfNzwFvt1327v2nPH+fHbHFQ+yvcb3gNy3gTNbkvJC0QHqr5raHXR2pTvRu7CJFf7IM0OAgA=\",\n"
				+ "    \"crl\": \"MIIBQTCBwjAFBgMrZXEwgZgxCzAJBgNVBAYTAlVTMRAwDgYDVQQIDAdJbmRpYW5hMRQwEgYDVQQHDAtCbG9vbWluZ3RvbjEbMBkGA1UECgwSSW5kaWFuYSBVbml2ZXJzaXR5MQ8wDQYDVQQLDAZTVEFSQ0gxMzAxBgNVBAMMKnVybjpleGFtcGxlOml1LWphdmEtYXV0aC1wa2kjQ2FWZXJmaWVyVGVzdBcNMjQwNzA1MTc0NDQ5WhgPMjEyNDA3MDYxNzQ0NDlaMAUGAytlcQNzAF6z6CwABXhw/2JWBbWBKxQ5VLEBaOOoOglnsBALYfNJxQRoskfgy3DDPVumPUX0q4cW9s0BTU5ogAjZHVE53DFasnXsyVOkwIM40NldhPMindk6JMHdsxSIcV/8sIosmuva20K/gZuU5sywVOumzUYdAA==\"\n"
				+ "}\n"));

		final var e = assertThrows(IllegalArgumentException.class, () -> new CaVerifier(ca));
		assertEquals("Key usage doesn't permit certificate signing", e.getMessage(), () -> IuException.trace(e));
	}

	@Test
	public void testConstructorKeyUsageMissingCRLSign() {
		final var ca = (IuCertificateAuthority) IuAuthenticationRealm.JSON.fromJson(IuJson.parse("{\"type\": \"ca\",\n"
				+ "    \"certificate\": \"MIICpDCCAiSgAwIBAgIUdDqpo82uEEcupbDALjmQ6rma5kMwBQYDK2VxMIGYMQswCQYDVQQGEwJVUzEQMA4GA1UECAwHSW5kaWFuYTEUMBIGA1UEBwwLQmxvb21pbmd0b24xGzAZBgNVBAoMEkluZGlhbmEgVW5pdmVyc2l0eTEPMA0GA1UECwwGU1RBUkNIMTMwMQYDVQQDDCp1cm46ZXhhbXBsZTppdS1qYXZhLWF1dGgtcGtpI0NhVmVyZmllclRlc3QwIBcNMjQwNzA1MTc0NzM1WhgPMjEyNDA3MDYxNzQ3MzVaMIGYMQswCQYDVQQGEwJVUzEQMA4GA1UECAwHSW5kaWFuYTEUMBIGA1UEBwwLQmxvb21pbmd0b24xGzAZBgNVBAoMEkluZGlhbmEgVW5pdmVyc2l0eTEPMA0GA1UECwwGU1RBUkNIMTMwMQYDVQQDDCp1cm46ZXhhbXBsZTppdS1qYXZhLWF1dGgtcGtpI0NhVmVyZmllclRlc3QwQzAFBgMrZXEDOgCwS/o2kTxh/sKT1vLcElnevTHW7HVj86eiX454u/p8LDPRCIpszoqmjHY0x/xH7H5wEbVkQ31y1oCjYzBhMB0GA1UdDgQWBBROU/PeWnmegpYPaClj9asLKooX8jAfBgNVHSMEGDAWgBROU/PeWnmegpYPaClj9asLKooX8jASBgNVHRMBAf8ECDAGAQH/AgEAMAsGA1UdDwQEAwICBDAFBgMrZXEDcwAqGVISDEpadApnF3HmEIm76tZETFyCt1gt49PxEbIaZXvRrK5rHBRLwuutMZfiBx5SaZKwxXczf4DjELZ4Sraso4W4XsQ1NTeJtkAd6anUOzVkLPnhOiVldUS7IAC7PSsIeNlfNu7/0UrqmlSQmGFGOQA=\",\n"
				+ "    \"crl\": \"MIIBQTCBwjAFBgMrZXEwgZgxCzAJBgNVBAYTAlVTMRAwDgYDVQQIDAdJbmRpYW5hMRQwEgYDVQQHDAtCbG9vbWluZ3RvbjEbMBkGA1UECgwSSW5kaWFuYSBVbml2ZXJzaXR5MQ8wDQYDVQQLDAZTVEFSQ0gxMzAxBgNVBAMMKnVybjpleGFtcGxlOml1LWphdmEtYXV0aC1wa2kjQ2FWZXJmaWVyVGVzdBcNMjQwNzA1MTc0NzM2WhgPMjEyNDA3MDYxNzQ3MzZaMAUGAytlcQNzAObudPNYLxdDK6Xz8eFO+3Tn52a6DsA4eocSFhYYUKZhD5aoWXi8GwIexTB7KmeRrnJ15eZsQnSaAI5qUMGCVHml5M0E+l3Ued/v11YEVq5fb+62ixYucYxyWfImUCDPmIFZ9lL8oTRpjurltmHo1P8fAA==\"\n"
				+ "}\n"));

		final var e = assertThrows(IllegalArgumentException.class, () -> new CaVerifier(ca));
		assertEquals("Key usage doesn't permit CRL signing", e.getMessage(), () -> IuException.trace(e));
	}

}
