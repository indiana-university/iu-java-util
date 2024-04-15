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
package iu.auth.jwt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.security.InvalidAlgorithmParameterException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertPathBuilder;
import java.security.cert.CertPathBuilderException;
import java.security.cert.CertStore;
import java.security.cert.CertStoreParameters;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.CollectionCertStoreParameters;
import java.security.cert.PKIXBuilderParameters;
import java.security.cert.PKIXParameters;
import java.security.cert.TrustAnchor;
import java.security.cert.X509CertSelector;
import java.util.Arrays;
import java.util.Set;

import javax.security.auth.Subject;

import org.junit.jupiter.api.Test;

import edu.iu.IdGenerator;
import edu.iu.auth.jwt.IuWebToken;
import edu.iu.auth.jwt.IuWebTokenIssuer;
import edu.iu.crypt.WebKey;
import edu.iu.crypt.WebKey.Algorithm;
import edu.iu.crypt.WebKey.Operation;
import edu.iu.crypt.WebKey.Use;

@SuppressWarnings("javadoc")
public class IuWebTokenTest {

	/**
	 * <p>
	 * For verification and demonstration purposes only. NOT FOR PRODUCTION USE.
	 * 
	 * <pre>
	$ openssl genpkey -algorithm ed448 | tee /tmp/k
	$ openssl req -x509 -key /tmp/k -addext basicConstraints=pathlen:-1 -days 430
	 * </pre>
	 */
	private static final String SELF_SIGNED_EE = "-----BEGIN PRIVATE KEY-----\n" //
			+ "MEcCAQAwBQYDK2VxBDsEOTJhHRjuRVDowCMKWslwironn8lKFWPw5ShatWk8vjgB\n" //
			+ "C4xaM8unbSd02KYIjhisyRIyQX++Ph2QOA==\n" //
			+ "-----END PRIVATE KEY-----\n" //
			+ "-----BEGIN CERTIFICATE-----\n" //
			+ "MIIClTCCAhWgAwIBAgIUJjqZEiIJdjw83v+bpZ+F9wM5OOwwBQYDK2VxMIGaMQsw\n" //
			+ "CQYDVQQGEwJVUzEQMA4GA1UECAwHSW5kaWFuYTEUMBIGA1UEBwwLQmxvb21pbmd0\n" //
			+ "b24xGzAZBgNVBAoMEkluZGlhbmEgVW5pdmVyc2l0eTEPMA0GA1UECwwGU1RBUkNI\n" //
			+ "MTUwMwYDVQQDDCx1cm46ZXhhbXBsZTppdS1qYXZhLWF1dGgtand0Ly9JdVdlYlRv\n" //
			+ "a2VuVGVzdDAgFw0yNDA0MTQxMjAwMjlaGA8yMTI0MDQxNTEyMDAyOVowgZoxCzAJ\n" //
			+ "BgNVBAYTAlVTMRAwDgYDVQQIDAdJbmRpYW5hMRQwEgYDVQQHDAtCbG9vbWluZ3Rv\n" //
			+ "bjEbMBkGA1UECgwSSW5kaWFuYSBVbml2ZXJzaXR5MQ8wDQYDVQQLDAZTVEFSQ0gx\n" //
			+ "NTAzBgNVBAMMLHVybjpleGFtcGxlOml1LWphdmEtYXV0aC1qd3QvL0l1V2ViVG9r\n" //
			+ "ZW5UZXN0MEMwBQYDK2VxAzoAQiBG7vOAhJFDUM1lSoR9k1XrQRUfuGu60+FxVwKu\n" //
			+ "lhooFdSxz9ffZk7dOpMGybAHWiK6cBKAM0OAo1AwTjAdBgNVHQ4EFgQUag2+Gg9U\n" //
			+ "TkObj0vyxu547xLv3/gwHwYDVR0jBBgwFoAUag2+Gg9UTkObj0vyxu547xLv3/gw\n" //
			+ "DAYDVR0TBAUwAwIB/zAFBgMrZXEDcwAf1ssHfi72SIQ5dFAckHOEB5ydk/CDRagn\n" //
			+ "K07IJCEWqpUrPfzhxQXKUn9a91kJ4H5uUu2Hw26NJID3WYKXF3ZcxfM8vdO0pjeY\n" //
			+ "ERurPnNq5Skp7RT7Yth65C0djgHzFZE8aUFxHZUg0OevupCLlv9qJQA=\n" //
			+ "-----END CERTIFICATE-----\n";

//	   The contents of the JOSE Header describe the cryptographic operations
//	   applied to the JWT Claims Set.  If the JOSE Header is for a JWS, the
//	   JWT is represented as a JWS and the claims are digitally signed or
//	   MACed, with the JWT Claims Set being the JWS Payload.  If the JOSE
//	   Header is for a JWE, the JWT is represented as a JWE and the claims
//	   are encrypted, with the JWT Claims Set being the plaintext encrypted
//	   by the JWE.  A JWT may be enclosed in another JWE or JWS structure to
//	   create a Nested JWT, enabling nested signing and encryption to be
//	   performed.
//
//	   A JWT is represented as a sequence of URL-safe parts separated by
//	   period ('.') characters.  Each part contains a base64url-encoded
//	   value.  The number of parts in the JWT is dependent upon the
//	   representation of the resulting JWS using the JWS Compact
//	   Serialization or JWE using the JWE Compact Serialization.
	@Test
	public void testHeaderVerification() throws Exception {
		final var iss = IdGenerator.generateId();
		assertThrows(NullPointerException.class, () -> IuWebToken.issue(iss).build());

//		final var encKey = WebKey.builder(WebKey.Type.X448).pem(ID_PEM).use(Use.ENCRYPT).ops(Operation.DERIVE_KEY);
		final var sigKey = WebKey.builder(WebKey.Type.ED448).algorithm(Algorithm.EDDSA).pem(SELF_SIGNED_EE)
				.use(Use.SIGN).ops(Operation.SIGN, Operation.VERIFY).keyId("iu-java-auth-jwt-test").build();
		final var certChain = sigKey.getCertificateChain();
		final var cert = certChain[0];

		final var issuer = mock(IuWebTokenIssuer.class);

		// TODO: WebKey factory method
		final var subject = new Subject();
		subject.getPrincipals().add(cert.getSubjectX500Principal());
		subject.getPrivateCredentials().add(sigKey);
		subject.getPublicCredentials().add(sigKey.wellKnown());
		when(issuer.getIssuer()).thenReturn(subject);

		final var anchor = new TrustAnchor(cert, null);
		when(issuer.getCertPathParameters()).thenReturn(new PKIXParameters(Set.of(anchor)));
		IuWebTokenIssuer.register(issuer);

		// TODO: Test CA cert

		final var token = IuWebToken.issue(iss).build();
		assertEquals("", token.toString());
	}

}
