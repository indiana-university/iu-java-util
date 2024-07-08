package iu.auth.pki;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.security.cert.CertPathValidatorException;
import java.util.logging.Level;

import javax.security.auth.Subject;

import org.junit.jupiter.api.Test;

import edu.iu.auth.IuAuthenticationException;
import edu.iu.test.IuTestLogger;

@SuppressWarnings("javadoc")
public class PkiVerifierTest extends PkiTestCase {

	@Test
	public void testRequiresSigningCert() {
		final var pkp = pkp("{\n" //
				+ "    \"type\": \"pki\",\n" //
				+ "    \"alg\": \"ES256\",\n" //
				+ "    \"encrypt_alg\": \"ECDH-ES\",\n" //
				+ "    \"enc\": \"A128GCM\",\n" //
				+ "    \"jwk\": {\n" //
				+ "        \"kid\": \"urn:example:iu-java-auth-pki#PkiVerifierTest\",\n" //
				+ "        \"x5c\": [\n" //
				+ "            \"MIIClDCCAjqgAwIBAgIUZHvlPWPMC8PKo3hCNrMa4DvYWxIwCgYIKoZIzj0EAwIwgZoxCzAJBgNVBAYTAlVTMRAwDgYDVQQIDAdJbmRpYW5hMRQwEgYDVQQHDAtCbG9vbWluZ3RvbjEbMBkGA1UECgwSSW5kaWFuYSBVbml2ZXJzaXR5MQ8wDQYDVQQLDAZTVEFSQ0gxNTAzBgNVBAMMLHVybjpleGFtcGxlOml1LWphdmEtYXV0aC1wa2kjUGtpVmVyaWZpZXJUZXN0MCAXDTI0MDcwNjEyMjgyNVoYDzIxMjQwNzA3MTIyODI1WjCBmjELMAkGA1UEBhMCVVMxEDAOBgNVBAgMB0luZGlhbmExFDASBgNVBAcMC0Jsb29taW5ndG9uMRswGQYDVQQKDBJJbmRpYW5hIFVuaXZlcnNpdHkxDzANBgNVBAsMBlNUQVJDSDE1MDMGA1UEAwwsdXJuOmV4YW1wbGU6aXUtamF2YS1hdXRoLXBraSNQa2lWZXJpZmllclRlc3QwWTATBgcqhkjOPQIBBggqhkjOPQMBBwNCAATfEBAGPI8Fb7kSrPAPKS3qSwGSFdwaiV15lGNoBvhgkjm68bBtJbGU9PSvUxpqI8hgRC9MoCXudm+kqbbGBlIIo1owWDAdBgNVHQ4EFgQUSRmiIixUX4WWjzD68BS+7Jgkfz8wHwYDVR0jBBgwFoAUSRmiIixUX4WWjzD68BS+7Jgkfz8wCQYDVR0TBAIwADALBgNVHQ8EBAMCAwgwCgYIKoZIzj0EAwIDSAAwRQIhAKY+1O94j5U9uQiLydIj7iMRHtid2VHMueol753sjgTkAiA5/XuTRUCWiY8RHhXLsxI0Ro4EBSGnCgEGK+ciU9JOAA==\"\n" //
				+ "        ],\n" //
				+ "        \"kty\": \"EC\",\n" //
				+ "        \"crv\": \"P-256\",\n" //
				+ "        \"x\": \"3xAQBjyPBW-5EqzwDykt6ksBkhXcGoldeZRjaAb4YJI\",\n" //
				+ "        \"y\": \"ObrxsG0lsZT09K9TGmojyGBEL0ygJe52b6SptsYGUgg\",\n" //
				+ "        \"d\": \"NLiu0Xg20a1RiRB4mC7mvC_wpHYWfPBoWntNR_lKSWo\"\n" //
				+ "    }\n" //
				+ "}");
		assertThrows(IllegalArgumentException.class, () -> new PkiVerifier(pkp));
	}

	@Test
	public void testSelfSignedAuthoritativeSuccess() {
		final var pkp = pkp("{\n" //
				+ "    \"type\": \"pki\",\n" //
				+ "    \"alg\": \"ES256\",\n" //
				+ "    \"encrypt_alg\": \"ECDH-ES\",\n" //
				+ "    \"enc\": \"A128GCM\",\n" //
				+ "    \"jwk\": {\n" //
				+ "        \"kid\": \"urn:example:iu-java-auth-pki#PkiVerifierTest\",\n" //
				+ "        \"x5c\": [\n" //
				+ "            \"MIIClTCCAjqgAwIBAgIUTMb4E2FxDpH+VMla9HScWgZ/aSwwCgYIKoZIzj0EAwIwgZoxCzAJBgNVBAYTAlVTMRAwDgYDVQQIDAdJbmRpYW5hMRQwEgYDVQQHDAtCbG9vbWluZ3RvbjEbMBkGA1UECgwSSW5kaWFuYSBVbml2ZXJzaXR5MQ8wDQYDVQQLDAZTVEFSQ0gxNTAzBgNVBAMMLHVybjpleGFtcGxlOml1LWphdmEtYXV0aC1wa2kjUGtpVmVyaWZpZXJUZXN0MCAXDTI0MDcwNjEyMzI0NFoYDzIxMjQwNzA3MTIzMjQ0WjCBmjELMAkGA1UEBhMCVVMxEDAOBgNVBAgMB0luZGlhbmExFDASBgNVBAcMC0Jsb29taW5ndG9uMRswGQYDVQQKDBJJbmRpYW5hIFVuaXZlcnNpdHkxDzANBgNVBAsMBlNUQVJDSDE1MDMGA1UEAwwsdXJuOmV4YW1wbGU6aXUtamF2YS1hdXRoLXBraSNQa2lWZXJpZmllclRlc3QwWTATBgcqhkjOPQIBBggqhkjOPQMBBwNCAAQYN+d358sffBBzEX6qr9S3Hg9NW5+1ZGAwbSEqV8bsGLcxEk+juXL6/E7W7gOsiGzXXbg9OkynFWUoqDA3QCw3o1owWDAdBgNVHQ4EFgQUeUvKOABAMkeEE/x/d98txXHPBbUwHwYDVR0jBBgwFoAUeUvKOABAMkeEE/x/d98txXHPBbUwCQYDVR0TBAIwADALBgNVHQ8EBAMCA4gwCgYIKoZIzj0EAwIDSQAwRgIhAM5wCSgV7hcSGjSsU6HYTHjG00oZ/m/p+jZUS1pZsNADAiEA/2W4xONR8pl+XnPNiiVhtX2nO1K/JMmobxH3eGpxrXk=\"\n" //
				+ "        ],\n" //
				+ "        \"kty\": \"EC\",\n" //
				+ "        \"crv\": \"P-256\",\n" //
				+ "        \"x\": \"GDfnd-fLH3wQcxF-qq_Utx4PTVuftWRgMG0hKlfG7Bg\",\n" //
				+ "        \"y\": \"tzEST6O5cvr8TtbuA6yIbNdduD06TKcVZSioMDdALDc\",\n" //
				+ "        \"d\": \"8Na8mz1EhITNe9GaYJC7_k4d50ap_lh1Th-wUleTdoc\"\n" //
				+ "    }\n" //
				+ "}");
		final var pkiv = new PkiVerifier(pkp);
		assertNull(pkiv.getAuthScheme());
		assertNull(pkiv.getAuthenticationEndpoint());
		assertEquals(PkiPrincipal.class, pkiv.getType());
		assertEquals("urn:example:iu-java-auth-pki#PkiVerifierTest", pkiv.getRealm());
		assertTrue(pkiv.isAuthoritative());

		final var pki = new PkiPrincipal(pkp);
		IuTestLogger.expect(PkiVerifier.class.getName(), Level.INFO,
				"pki:auth:urn:example:iu-java-auth-pki#PkiVerifierTest; trustAnchor: urn:example:iu-java-auth-pki#PkiVerifierTest");
		assertDoesNotThrow(() -> pkiv.verify(pki));
		assertEquals(pki, pkiv.getPrincipal(pkp));
	}

	@Test
	public void testSelfSignedRejectsInvalid() {
		final var pkiv = new PkiVerifier(pkp("{\n" //
				+ "    \"type\": \"pki\",\n" //
				+ "    \"alg\": \"ES256\",\n" //
				+ "    \"encrypt_alg\": \"ECDH-ES\",\n" //
				+ "    \"enc\": \"A128GCM\",\n" //
				+ "    \"jwk\": {\n" //
				+ "        \"kid\": \"urn:example:iu-java-auth-pki#PkiVerifierTest\",\n" //
				+ "        \"x5c\": [\n" //
				+ "            \"MIIClTCCAjqgAwIBAgIUTMb4E2FxDpH+VMla9HScWgZ/aSwwCgYIKoZIzj0EAwIwgZoxCzAJBgNVBAYTAlVTMRAwDgYDVQQIDAdJbmRpYW5hMRQwEgYDVQQHDAtCbG9vbWluZ3RvbjEbMBkGA1UECgwSSW5kaWFuYSBVbml2ZXJzaXR5MQ8wDQYDVQQLDAZTVEFSQ0gxNTAzBgNVBAMMLHVybjpleGFtcGxlOml1LWphdmEtYXV0aC1wa2kjUGtpVmVyaWZpZXJUZXN0MCAXDTI0MDcwNjEyMzI0NFoYDzIxMjQwNzA3MTIzMjQ0WjCBmjELMAkGA1UEBhMCVVMxEDAOBgNVBAgMB0luZGlhbmExFDASBgNVBAcMC0Jsb29taW5ndG9uMRswGQYDVQQKDBJJbmRpYW5hIFVuaXZlcnNpdHkxDzANBgNVBAsMBlNUQVJDSDE1MDMGA1UEAwwsdXJuOmV4YW1wbGU6aXUtamF2YS1hdXRoLXBraSNQa2lWZXJpZmllclRlc3QwWTATBgcqhkjOPQIBBggqhkjOPQMBBwNCAAQYN+d358sffBBzEX6qr9S3Hg9NW5+1ZGAwbSEqV8bsGLcxEk+juXL6/E7W7gOsiGzXXbg9OkynFWUoqDA3QCw3o1owWDAdBgNVHQ4EFgQUeUvKOABAMkeEE/x/d98txXHPBbUwHwYDVR0jBBgwFoAUeUvKOABAMkeEE/x/d98txXHPBbUwCQYDVR0TBAIwADALBgNVHQ8EBAMCA4gwCgYIKoZIzj0EAwIDSQAwRgIhAM5wCSgV7hcSGjSsU6HYTHjG00oZ/m/p+jZUS1pZsNADAiEA/2W4xONR8pl+XnPNiiVhtX2nO1K/JMmobxH3eGpxrXk=\"\n" //
				+ "        ],\n" //
				+ "        \"kty\": \"EC\",\n" //
				+ "        \"crv\": \"P-256\",\n" //
				+ "        \"x\": \"GDfnd-fLH3wQcxF-qq_Utx4PTVuftWRgMG0hKlfG7Bg\",\n" //
				+ "        \"y\": \"tzEST6O5cvr8TtbuA6yIbNdduD06TKcVZSioMDdALDc\"\n" //
				+ "    }\n" //
				+ "}"));

		final var ipki = mock(PkiPrincipal.class);
		final var sub = new Subject();
		when(ipki.getSubject()).thenReturn(sub);
		var e = assertThrows(IllegalArgumentException.class, () -> pkiv.verify(ipki));
		assertEquals("missing public key", e.getMessage());
	}

	@Test
	public void testSelfSignedAuthoritativeRejectsWellKnown() {
		final var pkiv = new PkiVerifier(pkp("{\n" //
				+ "    \"type\": \"pki\",\n" //
				+ "    \"alg\": \"ES256\",\n" //
				+ "    \"encrypt_alg\": \"ECDH-ES\",\n" //
				+ "    \"enc\": \"A128GCM\",\n" //
				+ "    \"jwk\": {\n" //
				+ "        \"kid\": \"urn:example:iu-java-auth-pki#PkiVerifierTest\",\n" //
				+ "        \"x5c\": [\n" //
				+ "            \"MIIClTCCAjqgAwIBAgIUTMb4E2FxDpH+VMla9HScWgZ/aSwwCgYIKoZIzj0EAwIwgZoxCzAJBgNVBAYTAlVTMRAwDgYDVQQIDAdJbmRpYW5hMRQwEgYDVQQHDAtCbG9vbWluZ3RvbjEbMBkGA1UECgwSSW5kaWFuYSBVbml2ZXJzaXR5MQ8wDQYDVQQLDAZTVEFSQ0gxNTAzBgNVBAMMLHVybjpleGFtcGxlOml1LWphdmEtYXV0aC1wa2kjUGtpVmVyaWZpZXJUZXN0MCAXDTI0MDcwNjEyMzI0NFoYDzIxMjQwNzA3MTIzMjQ0WjCBmjELMAkGA1UEBhMCVVMxEDAOBgNVBAgMB0luZGlhbmExFDASBgNVBAcMC0Jsb29taW5ndG9uMRswGQYDVQQKDBJJbmRpYW5hIFVuaXZlcnNpdHkxDzANBgNVBAsMBlNUQVJDSDE1MDMGA1UEAwwsdXJuOmV4YW1wbGU6aXUtamF2YS1hdXRoLXBraSNQa2lWZXJpZmllclRlc3QwWTATBgcqhkjOPQIBBggqhkjOPQMBBwNCAAQYN+d358sffBBzEX6qr9S3Hg9NW5+1ZGAwbSEqV8bsGLcxEk+juXL6/E7W7gOsiGzXXbg9OkynFWUoqDA3QCw3o1owWDAdBgNVHQ4EFgQUeUvKOABAMkeEE/x/d98txXHPBbUwHwYDVR0jBBgwFoAUeUvKOABAMkeEE/x/d98txXHPBbUwCQYDVR0TBAIwADALBgNVHQ8EBAMCA4gwCgYIKoZIzj0EAwIDSQAwRgIhAM5wCSgV7hcSGjSsU6HYTHjG00oZ/m/p+jZUS1pZsNADAiEA/2W4xONR8pl+XnPNiiVhtX2nO1K/JMmobxH3eGpxrXk=\"\n" //
				+ "        ],\n" //
				+ "        \"kty\": \"EC\",\n" //
				+ "        \"crv\": \"P-256\",\n" //
				+ "        \"x\": \"GDfnd-fLH3wQcxF-qq_Utx4PTVuftWRgMG0hKlfG7Bg\",\n" //
				+ "        \"y\": \"tzEST6O5cvr8TtbuA6yIbNdduD06TKcVZSioMDdALDc\",\n" //
				+ "        \"d\": \"8Na8mz1EhITNe9GaYJC7_k4d50ap_lh1Th-wUleTdoc\"\n" //
				+ "    }\n" //
				+ "}"));

		final var wellKnown = pkp("{\n" //
				+ "    \"type\": \"pki\",\n" //
				+ "    \"alg\": \"ES256\",\n" //
				+ "    \"encrypt_alg\": \"ECDH-ES\",\n" //
				+ "    \"enc\": \"A128GCM\",\n" //
				+ "    \"jwk\": {\n" //
				+ "        \"kid\": \"urn:example:iu-java-auth-pki#PkiVerifierTest\",\n" //
				+ "        \"x5c\": [\n" //
				+ "            \"MIIClTCCAjqgAwIBAgIUTMb4E2FxDpH+VMla9HScWgZ/aSwwCgYIKoZIzj0EAwIwgZoxCzAJBgNVBAYTAlVTMRAwDgYDVQQIDAdJbmRpYW5hMRQwEgYDVQQHDAtCbG9vbWluZ3RvbjEbMBkGA1UECgwSSW5kaWFuYSBVbml2ZXJzaXR5MQ8wDQYDVQQLDAZTVEFSQ0gxNTAzBgNVBAMMLHVybjpleGFtcGxlOml1LWphdmEtYXV0aC1wa2kjUGtpVmVyaWZpZXJUZXN0MCAXDTI0MDcwNjEyMzI0NFoYDzIxMjQwNzA3MTIzMjQ0WjCBmjELMAkGA1UEBhMCVVMxEDAOBgNVBAgMB0luZGlhbmExFDASBgNVBAcMC0Jsb29taW5ndG9uMRswGQYDVQQKDBJJbmRpYW5hIFVuaXZlcnNpdHkxDzANBgNVBAsMBlNUQVJDSDE1MDMGA1UEAwwsdXJuOmV4YW1wbGU6aXUtamF2YS1hdXRoLXBraSNQa2lWZXJpZmllclRlc3QwWTATBgcqhkjOPQIBBggqhkjOPQMBBwNCAAQYN+d358sffBBzEX6qr9S3Hg9NW5+1ZGAwbSEqV8bsGLcxEk+juXL6/E7W7gOsiGzXXbg9OkynFWUoqDA3QCw3o1owWDAdBgNVHQ4EFgQUeUvKOABAMkeEE/x/d98txXHPBbUwHwYDVR0jBBgwFoAUeUvKOABAMkeEE/x/d98txXHPBbUwCQYDVR0TBAIwADALBgNVHQ8EBAMCA4gwCgYIKoZIzj0EAwIDSQAwRgIhAM5wCSgV7hcSGjSsU6HYTHjG00oZ/m/p+jZUS1pZsNADAiEA/2W4xONR8pl+XnPNiiVhtX2nO1K/JMmobxH3eGpxrXk=\"\n" //
				+ "        ],\n" //
				+ "        \"kty\": \"EC\",\n" //
				+ "        \"crv\": \"P-256\",\n" //
				+ "        \"x\": \"GDfnd-fLH3wQcxF-qq_Utx4PTVuftWRgMG0hKlfG7Bg\",\n" //
				+ "        \"y\": \"tzEST6O5cvr8TtbuA6yIbNdduD06TKcVZSioMDdALDc\"\n" //
				+ "    }\n" //
				+ "}");

		IuTestLogger.expect(PkiVerifier.class.getName(), Level.FINE,
				"pki:invalid:urn:example:iu-java-auth-pki#PkiVerifierTest", IllegalArgumentException.class,
				e -> "private key mismatch".equals(e.getMessage()));
		assertNull(pkiv.getPrincipal(wellKnown));

		final var wpki = new PkiPrincipal(wellKnown);
		var e = assertThrows(IllegalArgumentException.class, () -> pkiv.verify(wpki));
		assertEquals("missing private key", e.getMessage());
	}

	@Test
	public void testSelfSignedWellKnownAcceptsPrivateKeyHolder() {
		final var pki = new PkiPrincipal(pkp("{\n" //
				+ "    \"type\": \"pki\",\n" //
				+ "    \"alg\": \"ES256\",\n" //
				+ "    \"encrypt_alg\": \"ECDH-ES\",\n" //
				+ "    \"enc\": \"A128GCM\",\n" //
				+ "    \"jwk\": {\n" //
				+ "        \"kid\": \"urn:example:iu-java-auth-pki#PkiVerifierTest\",\n" //
				+ "        \"x5c\": [\n" //
				+ "            \"MIIClTCCAjqgAwIBAgIUTMb4E2FxDpH+VMla9HScWgZ/aSwwCgYIKoZIzj0EAwIwgZoxCzAJBgNVBAYTAlVTMRAwDgYDVQQIDAdJbmRpYW5hMRQwEgYDVQQHDAtCbG9vbWluZ3RvbjEbMBkGA1UECgwSSW5kaWFuYSBVbml2ZXJzaXR5MQ8wDQYDVQQLDAZTVEFSQ0gxNTAzBgNVBAMMLHVybjpleGFtcGxlOml1LWphdmEtYXV0aC1wa2kjUGtpVmVyaWZpZXJUZXN0MCAXDTI0MDcwNjEyMzI0NFoYDzIxMjQwNzA3MTIzMjQ0WjCBmjELMAkGA1UEBhMCVVMxEDAOBgNVBAgMB0luZGlhbmExFDASBgNVBAcMC0Jsb29taW5ndG9uMRswGQYDVQQKDBJJbmRpYW5hIFVuaXZlcnNpdHkxDzANBgNVBAsMBlNUQVJDSDE1MDMGA1UEAwwsdXJuOmV4YW1wbGU6aXUtamF2YS1hdXRoLXBraSNQa2lWZXJpZmllclRlc3QwWTATBgcqhkjOPQIBBggqhkjOPQMBBwNCAAQYN+d358sffBBzEX6qr9S3Hg9NW5+1ZGAwbSEqV8bsGLcxEk+juXL6/E7W7gOsiGzXXbg9OkynFWUoqDA3QCw3o1owWDAdBgNVHQ4EFgQUeUvKOABAMkeEE/x/d98txXHPBbUwHwYDVR0jBBgwFoAUeUvKOABAMkeEE/x/d98txXHPBbUwCQYDVR0TBAIwADALBgNVHQ8EBAMCA4gwCgYIKoZIzj0EAwIDSQAwRgIhAM5wCSgV7hcSGjSsU6HYTHjG00oZ/m/p+jZUS1pZsNADAiEA/2W4xONR8pl+XnPNiiVhtX2nO1K/JMmobxH3eGpxrXk=\"\n" //
				+ "        ],\n" //
				+ "        \"kty\": \"EC\",\n" //
				+ "        \"crv\": \"P-256\",\n" //
				+ "        \"x\": \"GDfnd-fLH3wQcxF-qq_Utx4PTVuftWRgMG0hKlfG7Bg\",\n" //
				+ "        \"y\": \"tzEST6O5cvr8TtbuA6yIbNdduD06TKcVZSioMDdALDc\",\n" //
				+ "        \"d\": \"8Na8mz1EhITNe9GaYJC7_k4d50ap_lh1Th-wUleTdoc\"\n" //
				+ "    }\n" //
				+ "}"));

		final var wellKnown = pkp("{\n" //
				+ "    \"type\": \"pki\",\n" //
				+ "    \"alg\": \"ES256\",\n" //
				+ "    \"encrypt_alg\": \"ECDH-ES\",\n" //
				+ "    \"enc\": \"A128GCM\",\n" //
				+ "    \"jwk\": {\n" //
				+ "        \"kid\": \"urn:example:iu-java-auth-pki#PkiVerifierTest\",\n" //
				+ "        \"x5c\": [\n" //
				+ "            \"MIIClTCCAjqgAwIBAgIUTMb4E2FxDpH+VMla9HScWgZ/aSwwCgYIKoZIzj0EAwIwgZoxCzAJBgNVBAYTAlVTMRAwDgYDVQQIDAdJbmRpYW5hMRQwEgYDVQQHDAtCbG9vbWluZ3RvbjEbMBkGA1UECgwSSW5kaWFuYSBVbml2ZXJzaXR5MQ8wDQYDVQQLDAZTVEFSQ0gxNTAzBgNVBAMMLHVybjpleGFtcGxlOml1LWphdmEtYXV0aC1wa2kjUGtpVmVyaWZpZXJUZXN0MCAXDTI0MDcwNjEyMzI0NFoYDzIxMjQwNzA3MTIzMjQ0WjCBmjELMAkGA1UEBhMCVVMxEDAOBgNVBAgMB0luZGlhbmExFDASBgNVBAcMC0Jsb29taW5ndG9uMRswGQYDVQQKDBJJbmRpYW5hIFVuaXZlcnNpdHkxDzANBgNVBAsMBlNUQVJDSDE1MDMGA1UEAwwsdXJuOmV4YW1wbGU6aXUtamF2YS1hdXRoLXBraSNQa2lWZXJpZmllclRlc3QwWTATBgcqhkjOPQIBBggqhkjOPQMBBwNCAAQYN+d358sffBBzEX6qr9S3Hg9NW5+1ZGAwbSEqV8bsGLcxEk+juXL6/E7W7gOsiGzXXbg9OkynFWUoqDA3QCw3o1owWDAdBgNVHQ4EFgQUeUvKOABAMkeEE/x/d98txXHPBbUwHwYDVR0jBBgwFoAUeUvKOABAMkeEE/x/d98txXHPBbUwCQYDVR0TBAIwADALBgNVHQ8EBAMCA4gwCgYIKoZIzj0EAwIDSQAwRgIhAM5wCSgV7hcSGjSsU6HYTHjG00oZ/m/p+jZUS1pZsNADAiEA/2W4xONR8pl+XnPNiiVhtX2nO1K/JMmobxH3eGpxrXk=\"\n" //
				+ "        ],\n" //
				+ "        \"kty\": \"EC\",\n" //
				+ "        \"crv\": \"P-256\",\n" //
				+ "        \"x\": \"GDfnd-fLH3wQcxF-qq_Utx4PTVuftWRgMG0hKlfG7Bg\",\n" //
				+ "        \"y\": \"tzEST6O5cvr8TtbuA6yIbNdduD06TKcVZSioMDdALDc\"\n" //
				+ "    }\n" //
				+ "}");

		final var wpkiv = new PkiVerifier(wellKnown);
		assertNull(wpkiv.getAuthScheme());
		assertNull(wpkiv.getAuthenticationEndpoint());
		assertEquals(PkiPrincipal.class, wpkiv.getType());
		assertEquals("urn:example:iu-java-auth-pki#PkiVerifierTest", wpkiv.getRealm());
		assertFalse(wpkiv.isAuthoritative());

		IuTestLogger.expect(PkiVerifier.class.getName(), Level.INFO,
				"pki:verify:urn:example:iu-java-auth-pki#PkiVerifierTest; trustAnchor: urn:example:iu-java-auth-pki#PkiVerifierTest");
		assertDoesNotThrow(() -> wpkiv.verify(pki));
	}

	@Test
	public void testSelfSignedWellKnownGeneratesNonPrivateId() {
		final var pkp = pkp("{\n" //
				+ "    \"type\": \"pki\",\n" //
				+ "    \"alg\": \"ES256\",\n" //
				+ "    \"encrypt_alg\": \"ECDH-ES\",\n" //
				+ "    \"enc\": \"A128GCM\",\n" //
				+ "    \"jwk\": {\n" //
				+ "        \"kid\": \"urn:example:iu-java-auth-pki#PkiVerifierTest\",\n" //
				+ "        \"x5c\": [\n" //
				+ "            \"MIIClTCCAjqgAwIBAgIUTMb4E2FxDpH+VMla9HScWgZ/aSwwCgYIKoZIzj0EAwIwgZoxCzAJBgNVBAYTAlVTMRAwDgYDVQQIDAdJbmRpYW5hMRQwEgYDVQQHDAtCbG9vbWluZ3RvbjEbMBkGA1UECgwSSW5kaWFuYSBVbml2ZXJzaXR5MQ8wDQYDVQQLDAZTVEFSQ0gxNTAzBgNVBAMMLHVybjpleGFtcGxlOml1LWphdmEtYXV0aC1wa2kjUGtpVmVyaWZpZXJUZXN0MCAXDTI0MDcwNjEyMzI0NFoYDzIxMjQwNzA3MTIzMjQ0WjCBmjELMAkGA1UEBhMCVVMxEDAOBgNVBAgMB0luZGlhbmExFDASBgNVBAcMC0Jsb29taW5ndG9uMRswGQYDVQQKDBJJbmRpYW5hIFVuaXZlcnNpdHkxDzANBgNVBAsMBlNUQVJDSDE1MDMGA1UEAwwsdXJuOmV4YW1wbGU6aXUtamF2YS1hdXRoLXBraSNQa2lWZXJpZmllclRlc3QwWTATBgcqhkjOPQIBBggqhkjOPQMBBwNCAAQYN+d358sffBBzEX6qr9S3Hg9NW5+1ZGAwbSEqV8bsGLcxEk+juXL6/E7W7gOsiGzXXbg9OkynFWUoqDA3QCw3o1owWDAdBgNVHQ4EFgQUeUvKOABAMkeEE/x/d98txXHPBbUwHwYDVR0jBBgwFoAUeUvKOABAMkeEE/x/d98txXHPBbUwCQYDVR0TBAIwADALBgNVHQ8EBAMCA4gwCgYIKoZIzj0EAwIDSQAwRgIhAM5wCSgV7hcSGjSsU6HYTHjG00oZ/m/p+jZUS1pZsNADAiEA/2W4xONR8pl+XnPNiiVhtX2nO1K/JMmobxH3eGpxrXk=\"\n" //
				+ "        ],\n" //
				+ "        \"kty\": \"EC\",\n" //
				+ "        \"crv\": \"P-256\",\n" //
				+ "        \"x\": \"GDfnd-fLH3wQcxF-qq_Utx4PTVuftWRgMG0hKlfG7Bg\",\n" //
				+ "        \"y\": \"tzEST6O5cvr8TtbuA6yIbNdduD06TKcVZSioMDdALDc\",\n" //
				+ "        \"d\": \"8Na8mz1EhITNe9GaYJC7_k4d50ap_lh1Th-wUleTdoc\"\n" //
				+ "    }\n" //
				+ "}");

		final var wellKnown = pkp("{\n" //
				+ "    \"type\": \"pki\",\n" //
				+ "    \"alg\": \"ES256\",\n" //
				+ "    \"encrypt_alg\": \"ECDH-ES\",\n" //
				+ "    \"enc\": \"A128GCM\",\n" //
				+ "    \"jwk\": {\n" //
				+ "        \"kid\": \"urn:example:iu-java-auth-pki#PkiVerifierTest\",\n" //
				+ "        \"x5c\": [\n" //
				+ "            \"MIIClTCCAjqgAwIBAgIUTMb4E2FxDpH+VMla9HScWgZ/aSwwCgYIKoZIzj0EAwIwgZoxCzAJBgNVBAYTAlVTMRAwDgYDVQQIDAdJbmRpYW5hMRQwEgYDVQQHDAtCbG9vbWluZ3RvbjEbMBkGA1UECgwSSW5kaWFuYSBVbml2ZXJzaXR5MQ8wDQYDVQQLDAZTVEFSQ0gxNTAzBgNVBAMMLHVybjpleGFtcGxlOml1LWphdmEtYXV0aC1wa2kjUGtpVmVyaWZpZXJUZXN0MCAXDTI0MDcwNjEyMzI0NFoYDzIxMjQwNzA3MTIzMjQ0WjCBmjELMAkGA1UEBhMCVVMxEDAOBgNVBAgMB0luZGlhbmExFDASBgNVBAcMC0Jsb29taW5ndG9uMRswGQYDVQQKDBJJbmRpYW5hIFVuaXZlcnNpdHkxDzANBgNVBAsMBlNUQVJDSDE1MDMGA1UEAwwsdXJuOmV4YW1wbGU6aXUtamF2YS1hdXRoLXBraSNQa2lWZXJpZmllclRlc3QwWTATBgcqhkjOPQIBBggqhkjOPQMBBwNCAAQYN+d358sffBBzEX6qr9S3Hg9NW5+1ZGAwbSEqV8bsGLcxEk+juXL6/E7W7gOsiGzXXbg9OkynFWUoqDA3QCw3o1owWDAdBgNVHQ4EFgQUeUvKOABAMkeEE/x/d98txXHPBbUwHwYDVR0jBBgwFoAUeUvKOABAMkeEE/x/d98txXHPBbUwCQYDVR0TBAIwADALBgNVHQ8EBAMCA4gwCgYIKoZIzj0EAwIDSQAwRgIhAM5wCSgV7hcSGjSsU6HYTHjG00oZ/m/p+jZUS1pZsNADAiEA/2W4xONR8pl+XnPNiiVhtX2nO1K/JMmobxH3eGpxrXk=\"\n" //
				+ "        ],\n" //
				+ "        \"kty\": \"EC\",\n" //
				+ "        \"crv\": \"P-256\",\n" //
				+ "        \"x\": \"GDfnd-fLH3wQcxF-qq_Utx4PTVuftWRgMG0hKlfG7Bg\",\n" //
				+ "        \"y\": \"tzEST6O5cvr8TtbuA6yIbNdduD06TKcVZSioMDdALDc\"\n" //
				+ "    }\n" //
				+ "}");

		final var wpkiv = new PkiVerifier(wellKnown);
		final var apki = wpkiv.getPrincipal(pkp);
		IuTestLogger.expect(PkiVerifier.class.getName(), Level.INFO,
				"pki:verify:urn:example:iu-java-auth-pki#PkiVerifierTest; trustAnchor: urn:example:iu-java-auth-pki#PkiVerifierTest");
		assertDoesNotThrow(() -> wpkiv.verify(apki));

		final var pkiv = new PkiVerifier(pkp);
		final var e = assertThrows(IllegalArgumentException.class, () -> pkiv.verify(apki));
		assertEquals("missing private key", e.getMessage());
	}

	@Test
	public void testSelfSignedWellKnownSuccess() {
		final var wellKnown = pkp("{\n" //
				+ "    \"type\": \"pki\",\n" //
				+ "    \"alg\": \"ES256\",\n" //
				+ "    \"encrypt_alg\": \"ECDH-ES\",\n" //
				+ "    \"enc\": \"A128GCM\",\n" //
				+ "    \"jwk\": {\n" //
				+ "        \"kid\": \"urn:example:iu-java-auth-pki#PkiVerifierTest\",\n" //
				+ "        \"x5c\": [\n" //
				+ "            \"MIIClTCCAjqgAwIBAgIUTMb4E2FxDpH+VMla9HScWgZ/aSwwCgYIKoZIzj0EAwIwgZoxCzAJBgNVBAYTAlVTMRAwDgYDVQQIDAdJbmRpYW5hMRQwEgYDVQQHDAtCbG9vbWluZ3RvbjEbMBkGA1UECgwSSW5kaWFuYSBVbml2ZXJzaXR5MQ8wDQYDVQQLDAZTVEFSQ0gxNTAzBgNVBAMMLHVybjpleGFtcGxlOml1LWphdmEtYXV0aC1wa2kjUGtpVmVyaWZpZXJUZXN0MCAXDTI0MDcwNjEyMzI0NFoYDzIxMjQwNzA3MTIzMjQ0WjCBmjELMAkGA1UEBhMCVVMxEDAOBgNVBAgMB0luZGlhbmExFDASBgNVBAcMC0Jsb29taW5ndG9uMRswGQYDVQQKDBJJbmRpYW5hIFVuaXZlcnNpdHkxDzANBgNVBAsMBlNUQVJDSDE1MDMGA1UEAwwsdXJuOmV4YW1wbGU6aXUtamF2YS1hdXRoLXBraSNQa2lWZXJpZmllclRlc3QwWTATBgcqhkjOPQIBBggqhkjOPQMBBwNCAAQYN+d358sffBBzEX6qr9S3Hg9NW5+1ZGAwbSEqV8bsGLcxEk+juXL6/E7W7gOsiGzXXbg9OkynFWUoqDA3QCw3o1owWDAdBgNVHQ4EFgQUeUvKOABAMkeEE/x/d98txXHPBbUwHwYDVR0jBBgwFoAUeUvKOABAMkeEE/x/d98txXHPBbUwCQYDVR0TBAIwADALBgNVHQ8EBAMCA4gwCgYIKoZIzj0EAwIDSQAwRgIhAM5wCSgV7hcSGjSsU6HYTHjG00oZ/m/p+jZUS1pZsNADAiEA/2W4xONR8pl+XnPNiiVhtX2nO1K/JMmobxH3eGpxrXk=\"\n" //
				+ "        ],\n" //
				+ "        \"kty\": \"EC\",\n" //
				+ "        \"crv\": \"P-256\",\n" //
				+ "        \"x\": \"GDfnd-fLH3wQcxF-qq_Utx4PTVuftWRgMG0hKlfG7Bg\",\n" //
				+ "        \"y\": \"tzEST6O5cvr8TtbuA6yIbNdduD06TKcVZSioMDdALDc\"\n" //
				+ "    }\n" //
				+ "}");

		final var wpki = new PkiPrincipal(wellKnown);
		final var wpkiv = new PkiVerifier(wellKnown);
		IuTestLogger.expect(PkiVerifier.class.getName(), Level.INFO,
				"pki:verify:urn:example:iu-java-auth-pki#PkiVerifierTest; trustAnchor: urn:example:iu-java-auth-pki#PkiVerifierTest");
		assertDoesNotThrow(() -> wpkiv.verify(wpki));
		assertEquals(wpki, wpkiv.getPrincipal(wellKnown));
	}

	@Test
	public void testKeyIdMismatch() {
		final var pkp = pkp("{\n" //
				+ "    \"type\": \"pki\",\n" //
				+ "    \"alg\": \"ES256\",\n" //
				+ "    \"encrypt_alg\": \"ECDH-ES\",\n" //
				+ "    \"enc\": \"A128GCM\",\n" //
				+ "    \"jwk\": {\n" //
				+ "        \"kid\": \"urn:example:iu-java-auth-pki#PkiVerifierTest\",\n" //
				+ "        \"x5c\": [\n" //
				+ "            \"MIIClTCCAjqgAwIBAgIUTMb4E2FxDpH+VMla9HScWgZ/aSwwCgYIKoZIzj0EAwIwgZoxCzAJBgNVBAYTAlVTMRAwDgYDVQQIDAdJbmRpYW5hMRQwEgYDVQQHDAtCbG9vbWluZ3RvbjEbMBkGA1UECgwSSW5kaWFuYSBVbml2ZXJzaXR5MQ8wDQYDVQQLDAZTVEFSQ0gxNTAzBgNVBAMMLHVybjpleGFtcGxlOml1LWphdmEtYXV0aC1wa2kjUGtpVmVyaWZpZXJUZXN0MCAXDTI0MDcwNjEyMzI0NFoYDzIxMjQwNzA3MTIzMjQ0WjCBmjELMAkGA1UEBhMCVVMxEDAOBgNVBAgMB0luZGlhbmExFDASBgNVBAcMC0Jsb29taW5ndG9uMRswGQYDVQQKDBJJbmRpYW5hIFVuaXZlcnNpdHkxDzANBgNVBAsMBlNUQVJDSDE1MDMGA1UEAwwsdXJuOmV4YW1wbGU6aXUtamF2YS1hdXRoLXBraSNQa2lWZXJpZmllclRlc3QwWTATBgcqhkjOPQIBBggqhkjOPQMBBwNCAAQYN+d358sffBBzEX6qr9S3Hg9NW5+1ZGAwbSEqV8bsGLcxEk+juXL6/E7W7gOsiGzXXbg9OkynFWUoqDA3QCw3o1owWDAdBgNVHQ4EFgQUeUvKOABAMkeEE/x/d98txXHPBbUwHwYDVR0jBBgwFoAUeUvKOABAMkeEE/x/d98txXHPBbUwCQYDVR0TBAIwADALBgNVHQ8EBAMCA4gwCgYIKoZIzj0EAwIDSQAwRgIhAM5wCSgV7hcSGjSsU6HYTHjG00oZ/m/p+jZUS1pZsNADAiEA/2W4xONR8pl+XnPNiiVhtX2nO1K/JMmobxH3eGpxrXk=\"\n" //
				+ "        ],\n" //
				+ "        \"kty\": \"EC\",\n" //
				+ "        \"crv\": \"P-256\",\n" //
				+ "        \"x\": \"GDfnd-fLH3wQcxF-qq_Utx4PTVuftWRgMG0hKlfG7Bg\",\n" //
				+ "        \"y\": \"tzEST6O5cvr8TtbuA6yIbNdduD06TKcVZSioMDdALDc\",\n" //
				+ "        \"d\": \"8Na8mz1EhITNe9GaYJC7_k4d50ap_lh1Th-wUleTdoc\"\n" //
				+ "    }\n" //
				+ "}");
		final var pkiv = new PkiVerifier(pkp);

		final var badPkp = pkp("{\n" //
				+ "    \"type\": \"pki\",\n" //
				+ "    \"alg\": \"ES256\",\n" //
				+ "    \"encrypt_alg\": \"ECDH-ES\",\n" //
				+ "    \"enc\": \"A128GCM\",\n" //
				+ "    \"jwk\": {\n" //
				+ "        \"kid\": \"urn:example:iu-java-auth-pki#PkiVerifierTest_B\",\n" //
				+ "        \"x5c\": [\n" //
				+ "            \"MIIClTCCAjqgAwIBAgIUTMb4E2FxDpH+VMla9HScWgZ/aSwwCgYIKoZIzj0EAwIwgZoxCzAJBgNVBAYTAlVTMRAwDgYDVQQIDAdJbmRpYW5hMRQwEgYDVQQHDAtCbG9vbWluZ3RvbjEbMBkGA1UECgwSSW5kaWFuYSBVbml2ZXJzaXR5MQ8wDQYDVQQLDAZTVEFSQ0gxNTAzBgNVBAMMLHVybjpleGFtcGxlOml1LWphdmEtYXV0aC1wa2kjUGtpVmVyaWZpZXJUZXN0MCAXDTI0MDcwNjEyMzI0NFoYDzIxMjQwNzA3MTIzMjQ0WjCBmjELMAkGA1UEBhMCVVMxEDAOBgNVBAgMB0luZGlhbmExFDASBgNVBAcMC0Jsb29taW5ndG9uMRswGQYDVQQKDBJJbmRpYW5hIFVuaXZlcnNpdHkxDzANBgNVBAsMBlNUQVJDSDE1MDMGA1UEAwwsdXJuOmV4YW1wbGU6aXUtamF2YS1hdXRoLXBraSNQa2lWZXJpZmllclRlc3QwWTATBgcqhkjOPQIBBggqhkjOPQMBBwNCAAQYN+d358sffBBzEX6qr9S3Hg9NW5+1ZGAwbSEqV8bsGLcxEk+juXL6/E7W7gOsiGzXXbg9OkynFWUoqDA3QCw3o1owWDAdBgNVHQ4EFgQUeUvKOABAMkeEE/x/d98txXHPBbUwHwYDVR0jBBgwFoAUeUvKOABAMkeEE/x/d98txXHPBbUwCQYDVR0TBAIwADALBgNVHQ8EBAMCA4gwCgYIKoZIzj0EAwIDSQAwRgIhAM5wCSgV7hcSGjSsU6HYTHjG00oZ/m/p+jZUS1pZsNADAiEA/2W4xONR8pl+XnPNiiVhtX2nO1K/JMmobxH3eGpxrXk=\"\n" //
				+ "        ],\n" //
				+ "        \"kty\": \"EC\",\n" //
				+ "        \"crv\": \"P-256\",\n" //
				+ "        \"x\": \"GDfnd-fLH3wQcxF-qq_Utx4PTVuftWRgMG0hKlfG7Bg\",\n" //
				+ "        \"y\": \"tzEST6O5cvr8TtbuA6yIbNdduD06TKcVZSioMDdALDc\",\n" //
				+ "        \"d\": \"8Na8mz1EhITNe9GaYJC7_k4d50ap_lh1Th-wUleTdoc\"\n" //
				+ "    }\n" //
				+ "}");
		assertNull(pkiv.getPrincipal(badPkp));
		final var e = assertThrows(IllegalArgumentException.class, () -> new PkiPrincipal(badPkp));
		assertEquals("Key ID doesn't match CN", e.getMessage());
		final var e2 = assertThrows(IllegalArgumentException.class, () -> new PkiVerifier(badPkp));
		assertEquals("Key ID doesn't match CN", e2.getMessage());
	}

	@Test
	public void testPublicKeyMismatch() {
		final var pkp = pkp("{\n" //
				+ "    \"type\": \"pki\",\n" //
				+ "    \"alg\": \"ES256\",\n" //
				+ "    \"encrypt_alg\": \"ECDH-ES\",\n" //
				+ "    \"enc\": \"A128GCM\",\n" //
				+ "    \"jwk\": {\n" //
				+ "        \"kid\": \"urn:example:iu-java-auth-pki#PkiVerifierTest\",\n" //
				+ "        \"x5c\": [\n" //
				+ "            \"MIIClTCCAjqgAwIBAgIUTMb4E2FxDpH+VMla9HScWgZ/aSwwCgYIKoZIzj0EAwIwgZoxCzAJBgNVBAYTAlVTMRAwDgYDVQQIDAdJbmRpYW5hMRQwEgYDVQQHDAtCbG9vbWluZ3RvbjEbMBkGA1UECgwSSW5kaWFuYSBVbml2ZXJzaXR5MQ8wDQYDVQQLDAZTVEFSQ0gxNTAzBgNVBAMMLHVybjpleGFtcGxlOml1LWphdmEtYXV0aC1wa2kjUGtpVmVyaWZpZXJUZXN0MCAXDTI0MDcwNjEyMzI0NFoYDzIxMjQwNzA3MTIzMjQ0WjCBmjELMAkGA1UEBhMCVVMxEDAOBgNVBAgMB0luZGlhbmExFDASBgNVBAcMC0Jsb29taW5ndG9uMRswGQYDVQQKDBJJbmRpYW5hIFVuaXZlcnNpdHkxDzANBgNVBAsMBlNUQVJDSDE1MDMGA1UEAwwsdXJuOmV4YW1wbGU6aXUtamF2YS1hdXRoLXBraSNQa2lWZXJpZmllclRlc3QwWTATBgcqhkjOPQIBBggqhkjOPQMBBwNCAAQYN+d358sffBBzEX6qr9S3Hg9NW5+1ZGAwbSEqV8bsGLcxEk+juXL6/E7W7gOsiGzXXbg9OkynFWUoqDA3QCw3o1owWDAdBgNVHQ4EFgQUeUvKOABAMkeEE/x/d98txXHPBbUwHwYDVR0jBBgwFoAUeUvKOABAMkeEE/x/d98txXHPBbUwCQYDVR0TBAIwADALBgNVHQ8EBAMCA4gwCgYIKoZIzj0EAwIDSQAwRgIhAM5wCSgV7hcSGjSsU6HYTHjG00oZ/m/p+jZUS1pZsNADAiEA/2W4xONR8pl+XnPNiiVhtX2nO1K/JMmobxH3eGpxrXk=\"\n" //
				+ "        ],\n" //
				+ "        \"kty\": \"EC\",\n" //
				+ "        \"crv\": \"P-256\",\n" //
				+ "        \"x\": \"GDfnd-fLH3wQcxF-qq_Utx4PTVuftWRgMG0hKlfG7Bg\",\n" //
				+ "        \"y\": \"tzEST6O5cvr8TtbuA6yIbNdduD06TKcVZSioMDdALDc\",\n" //
				+ "        \"d\": \"8Na8mz1EhITNe9GaYJC7_k4d50ap_lh1Th-wUleTdoc\"\n" //
				+ "    }\n" //
				+ "}");
		final var pkiv = new PkiVerifier(pkp);

		final var badPkp = pkp("{\n" //
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
				+ "        \"d\": \"8Na8mz1EhITNe9GaYJC7_k4d50ap_lh1Th-wUleTdoc\"\n" //
				+ "    }\n" //
				+ "}");
		IuTestLogger.expect(PkiVerifier.class.getName(), Level.FINE,
				"pki:invalid:urn:example:iu-java-auth-pki#PkiVerifierTest", CertPathValidatorException.class);
		assertNull(pkiv.getPrincipal(badPkp));
		final var e = assertThrows(IuAuthenticationException.class, () -> pkiv.verify(new PkiPrincipal(badPkp)));
		final var certPathException = assertInstanceOf(CertPathValidatorException.class, e.getCause());
		assertEquals("Path does not chain with any of the trust anchors", certPathException.getMessage());
	}

	@Test
	public void testPrivateKeyMismatch() {
		final var pkp = pkp("{\n" //
				+ "    \"type\": \"pki\",\n" //
				+ "    \"alg\": \"ES256\",\n" //
				+ "    \"encrypt_alg\": \"ECDH-ES\",\n" //
				+ "    \"enc\": \"A128GCM\",\n" //
				+ "    \"jwk\": {\n" //
				+ "        \"kid\": \"urn:example:iu-java-auth-pki#PkiVerifierTest\",\n" //
				+ "        \"x5c\": [\n" //
				+ "            \"MIIClTCCAjqgAwIBAgIUTMb4E2FxDpH+VMla9HScWgZ/aSwwCgYIKoZIzj0EAwIwgZoxCzAJBgNVBAYTAlVTMRAwDgYDVQQIDAdJbmRpYW5hMRQwEgYDVQQHDAtCbG9vbWluZ3RvbjEbMBkGA1UECgwSSW5kaWFuYSBVbml2ZXJzaXR5MQ8wDQYDVQQLDAZTVEFSQ0gxNTAzBgNVBAMMLHVybjpleGFtcGxlOml1LWphdmEtYXV0aC1wa2kjUGtpVmVyaWZpZXJUZXN0MCAXDTI0MDcwNjEyMzI0NFoYDzIxMjQwNzA3MTIzMjQ0WjCBmjELMAkGA1UEBhMCVVMxEDAOBgNVBAgMB0luZGlhbmExFDASBgNVBAcMC0Jsb29taW5ndG9uMRswGQYDVQQKDBJJbmRpYW5hIFVuaXZlcnNpdHkxDzANBgNVBAsMBlNUQVJDSDE1MDMGA1UEAwwsdXJuOmV4YW1wbGU6aXUtamF2YS1hdXRoLXBraSNQa2lWZXJpZmllclRlc3QwWTATBgcqhkjOPQIBBggqhkjOPQMBBwNCAAQYN+d358sffBBzEX6qr9S3Hg9NW5+1ZGAwbSEqV8bsGLcxEk+juXL6/E7W7gOsiGzXXbg9OkynFWUoqDA3QCw3o1owWDAdBgNVHQ4EFgQUeUvKOABAMkeEE/x/d98txXHPBbUwHwYDVR0jBBgwFoAUeUvKOABAMkeEE/x/d98txXHPBbUwCQYDVR0TBAIwADALBgNVHQ8EBAMCA4gwCgYIKoZIzj0EAwIDSQAwRgIhAM5wCSgV7hcSGjSsU6HYTHjG00oZ/m/p+jZUS1pZsNADAiEA/2W4xONR8pl+XnPNiiVhtX2nO1K/JMmobxH3eGpxrXk=\"\n" //
				+ "        ],\n" //
				+ "        \"kty\": \"EC\",\n" //
				+ "        \"crv\": \"P-256\",\n" //
				+ "        \"x\": \"GDfnd-fLH3wQcxF-qq_Utx4PTVuftWRgMG0hKlfG7Bg\",\n" //
				+ "        \"y\": \"tzEST6O5cvr8TtbuA6yIbNdduD06TKcVZSioMDdALDc\",\n" //
				+ "        \"d\": \"8Na8mz1EhITNe9GaYJC7_k4d50ap_lh1Th-wUleTdoc\"\n" //
				+ "    }\n" //
				+ "}");
		final var pkiv = new PkiVerifier(pkp);

		final var badPkp = pkp("{\n" //
				+ "    \"type\": \"pki\",\n" //
				+ "    \"alg\": \"ES256\",\n" //
				+ "    \"encrypt_alg\": \"ECDH-ES\",\n" //
				+ "    \"enc\": \"A128GCM\",\n" //
				+ "    \"jwk\": {\n" //
				+ "        \"kid\": \"urn:example:iu-java-auth-pki#PkiVerifierTest\",\n" //
				+ "        \"x5c\": [\n" //
				+ "            \"MIIClTCCAjqgAwIBAgIUTMb4E2FxDpH+VMla9HScWgZ/aSwwCgYIKoZIzj0EAwIwgZoxCzAJBgNVBAYTAlVTMRAwDgYDVQQIDAdJbmRpYW5hMRQwEgYDVQQHDAtCbG9vbWluZ3RvbjEbMBkGA1UECgwSSW5kaWFuYSBVbml2ZXJzaXR5MQ8wDQYDVQQLDAZTVEFSQ0gxNTAzBgNVBAMMLHVybjpleGFtcGxlOml1LWphdmEtYXV0aC1wa2kjUGtpVmVyaWZpZXJUZXN0MCAXDTI0MDcwNjEyMzI0NFoYDzIxMjQwNzA3MTIzMjQ0WjCBmjELMAkGA1UEBhMCVVMxEDAOBgNVBAgMB0luZGlhbmExFDASBgNVBAcMC0Jsb29taW5ndG9uMRswGQYDVQQKDBJJbmRpYW5hIFVuaXZlcnNpdHkxDzANBgNVBAsMBlNUQVJDSDE1MDMGA1UEAwwsdXJuOmV4YW1wbGU6aXUtamF2YS1hdXRoLXBraSNQa2lWZXJpZmllclRlc3QwWTATBgcqhkjOPQIBBggqhkjOPQMBBwNCAAQYN+d358sffBBzEX6qr9S3Hg9NW5+1ZGAwbSEqV8bsGLcxEk+juXL6/E7W7gOsiGzXXbg9OkynFWUoqDA3QCw3o1owWDAdBgNVHQ4EFgQUeUvKOABAMkeEE/x/d98txXHPBbUwHwYDVR0jBBgwFoAUeUvKOABAMkeEE/x/d98txXHPBbUwCQYDVR0TBAIwADALBgNVHQ8EBAMCA4gwCgYIKoZIzj0EAwIDSQAwRgIhAM5wCSgV7hcSGjSsU6HYTHjG00oZ/m/p+jZUS1pZsNADAiEA/2W4xONR8pl+XnPNiiVhtX2nO1K/JMmobxH3eGpxrXk=\"\n" //
				+ "        ],\n" //
				+ "        \"kty\": \"EC\",\n" //
				+ "        \"crv\": \"P-256\",\n" //
				+ "        \"x\": \"GDfnd-fLH3wQcxF-qq_Utx4PTVuftWRgMG0hKlfG7Bg\",\n" //
				+ "        \"y\": \"tzEST6O5cvr8TtbuA6yIbNdduD06TKcVZSioMDdALDc\",\n" //
				+ "        \"d\": \"n8fGMA6UbfsC1ywM-ktIW1k5xVbu52wvS1q9MEFwfjY\"\n" //
				+ "    }\n" //
				+ "}");
		IuTestLogger.expect(PkiVerifier.class.getName(), Level.FINE,
				"pki:invalid:urn:example:iu-java-auth-pki#PkiVerifierTest", IllegalArgumentException.class,
				e -> "private key mismatch".equals(e.getMessage()));
		assertNull(pkiv.getPrincipal(badPkp));

		final var e = assertThrows(IllegalArgumentException.class, () -> pkiv.verify(new PkiPrincipal(badPkp)));
		assertEquals("private key mismatch", e.getMessage());
	}

}
