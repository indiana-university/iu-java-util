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
package iu.crypt;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.URI;
import java.security.Key;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.logging.Level;

import org.jose4j.jwa.AlgorithmConstraints;
import org.jose4j.jwa.AlgorithmConstraints.ConstraintType;
import org.jose4j.jwe.ContentEncryptionAlgorithmIdentifiers;
import org.jose4j.jwe.JsonWebEncryption;
import org.jose4j.jwe.KeyManagementAlgorithmIdentifiers;
import org.jose4j.jwk.OctetKeyPairJsonWebKey;
import org.jose4j.jwk.OkpJwkGenerator;
import org.jose4j.jws.AlgorithmIdentifiers;
import org.jose4j.jws.JsonWebSignature;
import org.jose4j.jwt.JwtClaims;
import org.jose4j.jwt.MalformedClaimException;
import org.jose4j.jwt.consumer.InvalidJwtException;
import org.jose4j.jwt.consumer.JwtConsumerBuilder;
import org.jose4j.keys.AesKey;
import org.jose4j.lang.ByteUtil;
import org.jose4j.lang.JoseException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import edu.iu.IdGenerator;
import edu.iu.crypt.WebEncryption;
import edu.iu.crypt.WebEncryption.Encryption;
import edu.iu.crypt.WebKey;
import edu.iu.crypt.WebKey.Algorithm;
import edu.iu.crypt.WebKey.Type;
import edu.iu.crypt.WebToken;
import edu.iu.test.IuTestLogger;

@SuppressWarnings("javadoc")
public class Jose4JTest {

	@BeforeEach
	public void setup() {
		IuTestLogger.allow(Jwe.class.getName(), Level.FINE);
	}

	@Test
	public void testHelloFromJose4j() throws JoseException {
		Key key = new AesKey(ByteUtil.randomBytes(16));
		JsonWebEncryption jwe = new JsonWebEncryption();
		jwe.setPayload("Hello World!");
		jwe.setAlgorithmHeaderValue(KeyManagementAlgorithmIdentifiers.A128KW);
		jwe.setEncryptionMethodHeaderParameter(ContentEncryptionAlgorithmIdentifiers.AES_128_CBC_HMAC_SHA_256);
		jwe.setKey(key);
		String serializedJwe = jwe.getCompactSerialization();
		assertEquals("Hello World!", WebEncryption.parse(serializedJwe).decryptText(WebKey.builder(key).build()));
	}

	@Test
	public void testHelloToJose4j() throws JoseException {
		Key key = new AesKey(ByteUtil.randomBytes(16));

		final var serializedJwe = WebEncryption.to(Encryption.AES_128_CBC_HMAC_SHA_256, Algorithm.A128KW)
				.key(WebKey.builder(key).build()).encrypt("Hello World!").compact();

		JsonWebEncryption jwe = new JsonWebEncryption();
		jwe.setAlgorithmConstraints(
				new AlgorithmConstraints(ConstraintType.PERMIT, KeyManagementAlgorithmIdentifiers.A128KW));
		jwe.setContentEncryptionAlgorithmConstraints(new AlgorithmConstraints(ConstraintType.PERMIT,
				ContentEncryptionAlgorithmIdentifiers.AES_128_CBC_HMAC_SHA_256));
		jwe.setKey(key);
		jwe.setCompactSerialization(serializedJwe);
		assertEquals("Hello World!", jwe.getPayload());
	}

	/**
	 * @see Reduced from <a href=
	 *      "https://bitbucket.org/b_c/jose4j/wiki/JWT%20Examples#markdown-header-producing-and-consuming-signed-and-encrypted-jwt-using-rfc8037s-ed25519-eddsa-and-x25519-ecdh">Jose4J</a>
	 * @throws JoseException from Jose4J
	 */
	@Test
	public void testEd25519FromJose4j() throws JoseException {
		OctetKeyPairJsonWebKey issuerJwk = OkpJwkGenerator.generateJwk(OctetKeyPairJsonWebKey.SUBTYPE_ED25519);
		issuerJwk.setKeyId(IdGenerator.generateId());
		OctetKeyPairJsonWebKey receiverJwk = OkpJwkGenerator.generateJwk(OctetKeyPairJsonWebKey.SUBTYPE_X25519);
		receiverJwk.setKeyId(IdGenerator.generateId());

		final var issuer = URI.create(IdGenerator.generateId());
		final var audience = URI.create(IdGenerator.generateId());
		final var subject = IdGenerator.generateId();
		final var claims = new JwtClaims();
		claims.setIssuer(issuer.toString());
		claims.setAudience(audience.toString());
		claims.setExpirationTimeMinutesInTheFuture(10);
		claims.setGeneratedJwtId();
		claims.setIssuedAtToNow();
		claims.setNotBeforeMinutesInThePast(2);
		claims.setSubject(subject);

		final var jws = new JsonWebSignature();
		jws.setPayload(claims.toJson());
		jws.setKey(issuerJwk.getPrivateKey());
		jws.setKeyIdHeaderValue(issuerJwk.getKeyId());
		jws.setAlgorithmHeaderValue(AlgorithmIdentifiers.EDDSA);
		final var innerJwt = jws.getCompactSerialization();

		final var jwe = new JsonWebEncryption();
		jwe.setAlgorithmHeaderValue(KeyManagementAlgorithmIdentifiers.ECDH_ES);
		jwe.setEncryptionMethodHeaderParameter(ContentEncryptionAlgorithmIdentifiers.AES_128_CBC_HMAC_SHA_256);
		jwe.setKey(receiverJwk.getPublicKey());
		jwe.setKeyIdHeaderValue(receiverJwk.getKeyId());
		jwe.setContentTypeHeaderValue("JWT");
		jwe.setPayload(innerJwt);
		final var serializedJwt = jwe.getCompactSerialization();

		final var issuerKey = WebKey.builder(WebKey.Type.ED25519).key(issuerJwk.getPublicKey()).build();
		final var audienceKey = WebKey.builder(WebKey.Type.X25519).key(receiverJwk.getPrivateKey()).build();
		final var jwt = WebToken.decryptAndVerify(serializedJwt, issuerKey, audienceKey);
		assertEquals(issuer, jwt.getIssuer());
		assertEquals(subject, jwt.getSubject());
		assertDoesNotThrow(() -> jwt.validateClaims(audience, Duration.ofMinutes(10L)));
	}

	/**
	 * @see Reduced from <a href=
	 *      "https://bitbucket.org/b_c/jose4j/wiki/JWT%20Examples#markdown-header-producing-and-consuming-signed-and-encrypted-jwt-using-rfc8037s-ed25519-eddsa-and-x25519-ecdh">Jose4J</a>
	 * @throws JoseException           from Jose4J
	 * @throws InvalidJwtException     from Jose4J
	 * @throws MalformedClaimException from Jose4J
	 */
	@Test
	public void testEd25519ToJose4j() throws JoseException, InvalidJwtException, MalformedClaimException {
		final var issuer = URI.create(IdGenerator.generateId());
		final var audience = URI.create(IdGenerator.generateId());
		final var subject = IdGenerator.generateId();
		final var issuedAt = Instant.now();
		final var notBefore = issuedAt.minusSeconds(120L);
		final var expires = issuedAt.plusSeconds(600L);
		final var jti = IdGenerator.generateId();

		final var builder = new JwtBuilder();
		builder.setTokenId(jti);
		builder.setIssuer(issuer);
		builder.setAudience(List.of(audience));
		builder.setSubject(subject);
		builder.setIssuedAt(issuedAt);
		builder.setNotBefore(notBefore);
		builder.setExpires(expires);

		final var jwt = builder.build();
		final var issuerKey = WebKey.builder(Type.ED25519).ephemeral(Algorithm.EDDSA).build();
		final var audienceKey = WebKey.builder(Type.X25519).ephemeral(Algorithm.ECDH_ES).build();
		final var serializedJwt = jwt.signAndEncrypt(Algorithm.EDDSA, issuerKey, Algorithm.ECDH_ES,
				Encryption.AES_128_CBC_HMAC_SHA_256, audienceKey);

		final var jwtConsumer = new JwtConsumerBuilder().setRequireExpirationTime() //
				.setMaxFutureValidityInMinutes(300) //
				.setRequireSubject() //
				.setExpectedIssuer(issuer.toString()) //
				.setExpectedAudience(audience.toString()) //
				.setDecryptionKey(audienceKey.getPrivateKey()) // decrypt with the receiver's private key
				.setVerificationKey(issuerKey.getPublicKey()) // verify the signature with the sender's public key
				.setJwsAlgorithmConstraints(new AlgorithmConstraints(ConstraintType.PERMIT, AlgorithmIdentifiers.EDDSA)) //
				.setJweAlgorithmConstraints(
						new AlgorithmConstraints(ConstraintType.PERMIT, KeyManagementAlgorithmIdentifiers.ECDH_ES)) //
				.setJweContentEncryptionAlgorithmConstraints(new AlgorithmConstraints(ConstraintType.PERMIT,
						ContentEncryptionAlgorithmIdentifiers.AES_128_CBC_HMAC_SHA_256)) //
				.build(); // create the JwtConsumer instance

		final var jwtClaims = jwtConsumer.processToClaims(serializedJwt);
		assertEquals(jti, jwtClaims.getJwtId());
		assertEquals(issuer.toString(), jwtClaims.getIssuer());
		assertEquals(audience.toString(), jwtClaims.getAudience().get(0));
		assertEquals(subject, jwtClaims.getSubject());
		assertEquals(issuedAt.getEpochSecond(), jwtClaims.getIssuedAt().getValue());
		assertEquals(notBefore.getEpochSecond(), jwtClaims.getNotBefore().getValue());
		assertEquals(expires.getEpochSecond(), jwtClaims.getExpirationTime().getValue());
	}

}
