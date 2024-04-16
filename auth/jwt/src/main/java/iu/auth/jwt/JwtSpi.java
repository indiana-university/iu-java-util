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

import java.lang.reflect.Type;
import java.security.cert.CertPathValidator;
import java.security.cert.CertificateFactory;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import javax.security.auth.x500.X500Principal;

import edu.iu.IuException;
import edu.iu.IuObject;
import edu.iu.IuText;
import edu.iu.auth.IuApiCredentials;
import edu.iu.auth.IuPrincipalIdentity;
import edu.iu.auth.jwt.IuWebToken;
import edu.iu.auth.jwt.IuWebToken.Builder;
import edu.iu.auth.jwt.IuWebTokenIssuer;
import edu.iu.auth.jwt.IuWebTokenIssuer.ClaimDefinition;
import edu.iu.auth.spi.IuJwtSpi;
import edu.iu.client.IuJson;
import edu.iu.client.IuJsonAdapter;
import edu.iu.crypt.WebSignedPayload;
import jakarta.json.JsonObject;

/**
 * {@link IuJwtSpi} service provider implementation
 */
public class JwtSpi implements IuJwtSpi {

	private final Map<String, JwtIssuer> ISSUERS = new HashMap<>();

	@Override
	public IuWebToken parse(String jwt) {
		// TODO Auto-generated method stub
		throw new UnsupportedOperationException("TODO");
	}

	@Override
	public synchronized void register(IuWebTokenIssuer issuer) {
		final var subject = Objects.requireNonNull(issuer.getIssuer());

		final Set<IuApiCredentials> credentials = subject.getPrincipals(IuApiCredentials.class);
		final Set<X500Principal> cert = subject.getPrincipals(X500Principal.class);
		
		final var principal = subject.getPrincipals().stream() //
				.filter(p -> (p instanceof IuPrincipalIdentity) //
						|| (p instanceof X500Principal))
				.findFirst().orElseThrow(() -> new IllegalArgumentException("Missing principal"));

		final var name = Objects.requireNonNull(principal.getName(), "missing principal name");
		if (ISSUERS.containsKey(name))
			throw new IllegalStateException("JWT issuer already registered for " + name);

//		final var certChain = subject.getPublicCredentials(X509Certificate.class).toArray(X509Certificate[]::new);
//
		if (principal instanceof IuPrincipalIdentity) {
			final var principalId = (IuPrincipalIdentity) principal;
			IuPrincipalIdentity.verify(principalId, issuer.getRealm());
		} else {
			final var x500 = (X500Principal) principal;
			final var certPathParams = Objects.requireNonNull(issuer.getCertPathParameters(),
					"Missing cert path parameters");
//			if (certChain.length < 1)
//				throw new IllegalArgumentException("Missing certificate chain for X500Principal");
//			if (!x500.equals(certChain[0].getSubjectX500Principal()))
//				throw new IllegalArgumentException("Certificate subject principal mismatch");

			IuException.unchecked(() -> {
				final var validator = CertPathValidator.getInstance("PKIX");
				final var certPath = CertificateFactory.getInstance("X.509").generateCertPath(Arrays.asList(certChain));
				validator.validate(certPath, certPathParams);
			});

		}

//			credentials.authorize()

		// TODO Auto-generated method stub
		throw new UnsupportedOperationException("TODO");
	}

	@Override
	public ClaimDefinition<String> stringOrUri(Consumer<IuWebToken> verifier) {
		// TODO Auto-generated method stub
		throw new UnsupportedOperationException("TODO");
	}

	@Override
	public ClaimDefinition<Instant> numericDate(Consumer<IuWebToken> verifier) {
		// TODO Auto-generated method stub
		throw new UnsupportedOperationException("TODO");
	}

	@Override
	public <T> ClaimDefinition<T> claimDefinition(Type type, ClaimDefinition<?> valueDefinition,
			BiConsumer<T, IuWebToken> verifier) {
		// TODO Auto-generated method stub
		throw new UnsupportedOperationException("TODO");
	}

	@Override
	public Builder issue(String issuer) {
		final var registeredIssuer = Objects.requireNonNull(ISSUERS.get(issuer), "issuer not registered " + issuer);
		// TODO Auto-generated method stub
		throw new UnsupportedOperationException("TODO");
	}

}

// TODO REVIEW LINE
///**
// * Verifies a JWT access token.
// * 
// * <p>
// * Verifies:
// * </p>
// * <ul>
// * <li>The use of a strong signature algorithm: RSA or ECDSA</li>
// * <li>The RSA or ECDSA signature is valid</li>
// * <li>The iss claim matches the configured issuer</li>
// * <li>The aud claim includes the audience</li>
// * <li>The current time is within between not before (iat) and not after (exp)
// * claims, with 15 seconds of leeway for clock drift</li>
// * </ul>
// * 
// * @param audience expected audience claim
// * @param token    JWT access token
// * @return Parsed JWT, can be used to perform additional verification
// */
//public JsonObject verify(String audience, String token) {
//	final var jws = WebSignedPayload.parse(token);
//
//	// Token must be a valid compact JWS signature
//	// Extract header and verify format
//	final var sigIter = jws.getSignatures().iterator();
//	final var sig = sigIter.next();
//	if (sigIter.hasNext())
//		throw new IllegalStateException("Invalid JWT");
//
//	final var header = sig.getHeader();
//
//	var key = Objects.requireNonNull(keyFactory.apply(header), "no matching key");
//	jws.verify(key);
//
//	final var jwt = IuJson.parse(IuText.utf8(jws.getPayload())).asJsonObject();
//	IuObject.once(issuer, IuJson.get(jwt, "iss"));
//
//	if (!IuJson.get(jwt, "aud", IuJsonAdapter.of(Set.class)).contains(audience))
//		throw new IllegalArgumentException("audience mismatch");
//
//	final var now = Instant.now().plus(Duration.ofSeconds(15L));
//	final var iat = Objects.requireNonNull(IuJson.get(jwt, "iat", IuJsonAdapter.of(Instant.class)));
//	if (now.isBefore(iat))
//		throw new IllegalArgumentException("iat must not be more than 15 seconds from now");
//
//	final var exp = Objects.requireNonNull(IuJson.get(jwt, "exp", IuJsonAdapter.of(Instant.class)));
//	if (now.isAfter(exp))
//		throw new IllegalArgumentException("exp must be after now");
//
//	return jwt;
//}
