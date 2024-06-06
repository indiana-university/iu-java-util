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

import java.util.Base64;
import java.util.Objects;
import java.util.Set;

import edu.iu.IuException;
import edu.iu.IuIterable;
import edu.iu.IuObject;
import edu.iu.IuText;
import edu.iu.auth.IuPrincipalIdentity;
import edu.iu.auth.config.AuthConfig;
import edu.iu.auth.config.IuPublicKeyPrincipalConfig;
import edu.iu.auth.jwt.IuWebToken;
import edu.iu.auth.spi.IuJwtSpi;
import edu.iu.client.IuJson;
import edu.iu.client.IuJsonAdapter;
import edu.iu.crypt.WebEncryption;
import edu.iu.crypt.WebKey;
import edu.iu.crypt.WebKey.Algorithm;
import edu.iu.crypt.WebKey.Operation;
import edu.iu.crypt.WebKey.Use;
import edu.iu.crypt.WebSignedPayload;
import jakarta.json.JsonObject;

/**
 * JWT Service Provider Implementation
 */
public class JwtSpi implements IuJwtSpi {
	static {
		IuObject.assertNotOpen(JwtSpi.class);
	}

//	private final static Map<String, IuPrincipalIdentity> ISSUER = new HashMap<>();
//	private final static Map<String, JwtVerifier> AUDIENCE = new HashMap<>();
//	private static volatile boolean sealed;

	/**
	 * Determines if a JWK may be used to sign a JWT.
	 * 
	 * @param key JWK
	 * @return true if the key may be used to sign a JWT; else false
	 */
	static boolean isSigningKey(WebKey key) {
		final var ops = key.getOps();
		return (key.getPrivateKey() != null //
				|| key.getKey() != null) //
				&& ((ops != null //
						&& ops.contains(Operation.SIGN)) //
						|| Use.SIGN.equals(key.getUse()));
	}

	/**
	 * Determines if a JWK may be used to verify a JWT.
	 * 
	 * @param key JWK
	 * @return true if the key may be used to verify a JWT; else false
	 */
	static boolean isVerifyKey(WebKey key) {
		final var ops = key.getOps();
		return (key.getPublicKey() != null //
				|| key.getKey() != null) //
				&& ((ops != null //
						&& ops.contains(Operation.VERIFY)) //
						|| Use.SIGN.equals(key.getUse()));
	}

	/**
	 * Determines if a JWK may be used to encrypt a JWT.
	 * 
	 * @param key JWK
	 * @return true if the key may be used to encrypt a JWT; else false
	 */
	static boolean isEncryptKey(WebKey key) {
		final var ops = key.getOps();
		return key.getPublicKey() != null //
				&& ((ops != null //
						&& (ops.contains(Operation.WRAP) //
								|| ops.contains(Operation.DERIVE_KEY))) //
						|| Use.ENCRYPT.equals(key.getUse()));
	}

	/**
	 * Determines if a JWK may be used to decrypt a JWT.
	 * 
	 * @param key JWK
	 * @return true if the key may be used to decrypt a JWT; else false
	 */
	static boolean isDecryptKey(WebKey key) {
		final var ops = key.getOps();
		return key.getPrivateKey() != null //
				&& ((ops != null //
						&& (ops.contains(Operation.UNWRAP) //
								|| ops.contains(Operation.DERIVE_KEY))) //
						|| Use.ENCRYPT.equals(key.getUse()));
	}

	/**
	 * Gets a JWK suitable for signing a JWT.
	 * 
	 * @param issuer Issuer principal
	 * @return signing key
	 */
	static WebKey getSigningKey(IuPrincipalIdentity issuer) {
		if (!IuException.unchecked(() -> IuPrincipalIdentity.verify(issuer, issuer.getName())))
			return null;

		return issuer.getSubject() //
				.getPrivateCredentials(WebKey.class) //
				.stream().filter(JwtSpi::isSigningKey) //
				.findFirst().get();
	}

	/**
	 * Gets a JWK suitable for verifying a JWT.
	 * 
	 * @param issuer Issuer principal
	 * @return verify key
	 */
	static WebKey getVerifyKey(IuPrincipalIdentity issuer) {
		IuException.unchecked(() -> IuPrincipalIdentity.verify(issuer, issuer.getName()));
		return issuer.getSubject() //
				.getPublicCredentials(WebKey.class) //
				.stream().filter(JwtSpi::isVerifyKey) //
				.findFirst().get();
	}

	/**
	 * Gets a JWK suitable for encrypting a JWT.
	 * 
	 * @param audience Audience principal
	 * @return encryption key
	 */
	static WebKey getEncryptKey(IuPrincipalIdentity audience) {
		return audience.getSubject() //
				.getPublicCredentials(WebKey.class) //
				.stream().filter(JwtSpi::isEncryptKey) //
				.findFirst().get();
	}

	/**
	 * Gets a JWK suitable for decrypting a JWT.
	 * 
	 * @param audience Audience principal
	 * @return decryption key
	 */
	static WebKey getDecryptKey(IuPrincipalIdentity audience) {
		return audience.getSubject() //
				.getPrivateCredentials(WebKey.class) //
				.stream().filter(JwtSpi::isDecryptKey) //
				.findFirst().orElse(null);
	}

//	@Override
//	public synchronized void register(IuPrincipalIdentity issuer) {
//		final var name = Objects.requireNonNull(issuer.getName());
//		if (ISSUER.containsKey(name) || sealed)
//			throw new IllegalStateException("issuer already registered");
//
//		Objects.requireNonNull(getVerifyKey(issuer));
//
//		ISSUER.put(name, issuer);
//	}
//
//	@Override
//	public synchronized void register(String jwtRealm, IuPrincipalIdentity audience, String realm) {
//		if (AUDIENCE.containsKey(realm) || sealed)
//			throw new IllegalStateException("audience already registered");
//
//		if (audience instanceof IuWebKey) {
//			if (!realm.equals(audience.getName()))
//				throw new IllegalArgumentException("realm mismatch");
//
//			if (audience instanceof JwkPrincipal)
//				PrincipalVerifierRegistry.registerVerifier(new JwkPrincipalVerifier(realm));
//			else if (audience instanceof JwkSecret)
//				PrincipalVerifierRegistry.registerVerifier(new JwkSecretVerifier(realm));
//			else
//				throw new IllegalArgumentException("invalid key type");
//		}
//
//		final var verifier = new JwtVerifier(jwtRealm, audience, realm);
//		PrincipalVerifierRegistry.registerVerifier(verifier);
//
//		AUDIENCE.put(jwtRealm, verifier);
//	}
//
//	/**
//	 * Reads a public key from a well-known JSON Web Key Set (JWKS).
//	 * 
//	 * <p>
//	 * Public JWK principals are not authoritative, but are available for cases
//	 * where a trusted issuer provides a JWKS URI and key ID, but does not include a
//	 * valid PKI certificate in the key set.
//	 * </p>
//	 * 
//	 * @param jwksUri Public JWKS {@link URI}
//	 * @param keyId   Key identifier (kid JOSE parameter)
//	 * @return {@link IuWebKey}
//	 */
//	static IuWebKey from(URI jwksUri, String keyId) {
//		return IuAuthSpiFactory.get(IuJwtSpi.class).getWebKey(jwksUri, keyId);
//	}
//
//	/**
//	 * Creates a secret key principal.
//	 * 
//	 * @param name Unique principal name
//	 * @param key  Secret key data; <em>must</em> contain at least 128 bits (length
//	 *             16) of securely generated psuedo-random data appropriate for the
//	 *             encryption and/or signature algorithm.
//	 * @return {@link IuWebKey}
//	 */
//	static IuWebKey from(String name, byte[] key) {
//		return IuAuthSpiFactory.get(IuJwtSpi.class).getSecretKey(name, key);
//	}
//
//	/**
//	 * Registers a trusted JWT issuer.
//	 * 
//	 * <p>
//	 * If the issuer principal includes a private key matching its certificate, its
//	 * principal name <em>may</em> be used with {@link #issue(String)} to create new
//	 * JWTs.
//	 * </p>
//	 * 
//	 * @param issuer Issuer principal; <em>must</em> include a valid certificate
//	 *               with CN matching the principal name.
//	 */
//	static void register(IuPrincipalIdentity issuer) {
//		IuAuthSpiFactory.get(IuJwtSpi.class).register(issuer);
//	}
//
//	/**
//	 * Registers an JWT authentication realm.
//	 * 
//	 * <p>
//	 * If the audience principal includes a private key, {@link #from(String)} will
//	 * <em>require</em> the JWT to be encrypted to the audience as well as signed.
//	 * </p>
//	 * 
//	 * @param jwtRealm JWT authentication realm
//	 * @param audience Audience principal; <em>must</em> include a private key and
//	 *                 valid certificate with CN matching the principal name.
//	 * @param realm    Authentication realm for verifying the audience principal
//	 */
//	static void register(String jwtRealm, IuPrincipalIdentity audience, String realm) {
//		IuAuthSpiFactory.get(IuJwtSpi.class).register(jwtRealm, audience, realm);
//	}
//
//	/**
//	 * Seals the JWT verification registry.
//	 * 
//	 * <p>
//	 * Once this method has been invoked, further calls to register
//	 * {@link #register(IuPrincipalIdentity) issuer} or
//	 * {@link #register(String, IuPrincipalIdentity, String) audience} identifying
//	 * principals will be rejected.
//	 * </p>
//	 */
//	static void seal() {
//		IuAuthSpiFactory.get(IuJwtSpi.class).seal();
//	}
//
//	/**
//	 * Issues a new JWT.
//	 * 
//	 * @param issuer Issuer principal name; <em>must</em> have be
//	 *               {@link #register(IuPrincipalIdentity) registered} with a
//	 *               private key and valid certificate.
//	 * @return {@link Builder}
//	 */
//	static Builder issue(String issuer) {
//		return IuAuthSpiFactory.get(IuJwtSpi.class).issue(issuer);
//	}
//
//	@Override
//	public synchronized void seal() {
//		sealed = true;
//	}
//
//	@Override
//	public IuWebKey getWebKey(URI jwksUri, String keyId) {
//		return new JwkPrincipal(jwksUri, keyId);
//	}
//
//	@Override
//	public IuWebKey getSecretKey(String name, byte[] key) {
//		return new JwkSecret(name, key);
//	}
//
//	@Override
//	public Builder issue(String iss) {
//		if (!sealed)
//			throw new IllegalStateException("Not sealed");
//
//		final var issuer = Objects.requireNonNull(ISSUER.get(iss));
//
//		return new JwtBuilder(issuer, Objects.requireNonNull(getSigningKey(issuer)));
//	}
//
	/**
	 * @see <a href=
	 *      "https://datatracker.ietf.org/doc/html/rfc7519#section-7.2">RFC-7519 JWT
	 *      Validation</a>
	 */
	@Override
	public IuWebToken parse(String jwt) {
		return parse(jwt, null);
	}

	private IuWebToken parse(String jwt, String realm) {
		final var jose = getHeader(jwt);
		if (!isEncrypted(jose)) {
			// 5. Verify that the resulting JOSE Header includes only parameters and values
			// whose syntax and semantics are both understood and supported or that are
			// specified as being ignored when not understood.

			// 7. If the JWT is a JWS, follow the steps specified in [JWS] for validating a
			// JWS. Let the Message be the result of base64url decoding the JWS Payload.
			final var jws = WebSignedPayload.parse(jwt);

			// 8. (iu-java-util specific) reject JWS w/ nested token
			if ("JWT".equals(IuJson.get(jose, "cty")))
				throw new IllegalArgumentException("JWT must be signed, or signed first then encrypted");

			// 9. Otherwise, base64url decode the Message following the restriction that no
			// line breaks, whitespace, or other additional characters have been used.
			//
			// 10. Verify that the resulting octet sequence is a UTF-8-encoded
			// representation of a completely valid JSON object conforming to RFC 7159
			// [RFC7159]; let the JWT Claims Set be this JSON object.
			final var claims = IuJson.parse(IuText.utf8(jws.getPayload())).asJsonObject();
			final IuPublicKeyPrincipalConfig issuerConfig = AuthConfig.get(claims.getString("iss"));
			jws.verify(getVerifyKey(issuerConfig.getIdentity()));

			AuthConfig.get(IuPublicKeyPrincipalConfig.class);
			final Set<String> audience = Objects.requireNonNull(
					IuJson.get(claims, "aud", IuJsonAdapter.of(Set.class, Jwt.STRING_OR_URI)), "missing audience");
			realm = IuObject.once(realm,
					IuIterable.filter(IuIterable.map(AuthConfig.get(IuPublicKeyPrincipalConfig.class),
							IuPublicKeyPrincipalConfig::getRealm), audience::contains).iterator().next(),
					() -> "audience mismatch");

			final var webToken = new Jwt(realm, jwt);
			IuException.unchecked(() -> IuPrincipalIdentity.verify(webToken, webToken.realm()));
			return webToken;

		} else { // Decrypt
			// Only signed then encrypted is allowed
			// decryption calls recursively with non-null realm
			IuObject.require(realm, Objects::isNull, () -> "invalid nesting");

			// 5. Verify that the resulting JOSE Header includes only parameters and values
			// whose syntax and semantics are both understood and supported or that are
			// specified as being ignored when not understood.

			// 7 * Else, if the JWT is a JWE, follow the steps specified in
			// [JWE] for validating a JWE. Let the Message be the resulting
			// plaintext.

			// 8. If the JOSE Header contains a "cty" (content type) value of "JWT", then
			// the Message is a JWT that was the subject of nested signing or encryption
			// operations. In this case, return to Step 1, using the Message as the JWT.
			IuObject.require(jose.getString("cty"), "JWT"::equals);

			class Box {
				String realm;
				String decryptedToken;
			}
			final var box = new Box();
			Throwable decryptionFailure = null;
			for (final var audience : AuthConfig.get(IuPublicKeyPrincipalConfig.class)) {
				decryptionFailure = IuException.suppress(decryptionFailure, () -> {
					IuException
							.unchecked(() -> IuPrincipalIdentity.verify(audience.getIdentity(), audience.getRealm()));
					box.decryptedToken = WebEncryption.parse(jwt).decryptText(getDecryptKey(audience.getIdentity()));
					box.realm = audience.getRealm();
				});
				if (box.decryptedToken != null)
					break;
			}

			if (box.decryptedToken == null)
				throw new IllegalArgumentException("invalid encryption", decryptionFailure);
			else
				return parse(box.decryptedToken, box.realm);
		}
	}

}
