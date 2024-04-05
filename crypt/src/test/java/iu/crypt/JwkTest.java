package iu.crypt;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.RSAPrivateKeySpec;
import java.util.Set;
import java.util.function.Supplier;

import org.junit.jupiter.api.Test;

import edu.iu.IdGenerator;
import edu.iu.IuIterable;
import edu.iu.IuText;
import edu.iu.client.IuJson;
import edu.iu.crypt.EphemeralKeys;
import edu.iu.crypt.IuCryptTestCase;
import edu.iu.crypt.PemEncoded;
import edu.iu.crypt.WebCertificateReference;
import edu.iu.crypt.WebEncryption.Encryption;
import edu.iu.crypt.WebKey;
import edu.iu.crypt.WebKey.Algorithm;
import edu.iu.crypt.WebKey.Operation;
import edu.iu.crypt.WebKey.Type;
import edu.iu.crypt.WebKey.Use;
import edu.iu.test.IuTest;

@SuppressWarnings("javadoc")
public class JwkTest extends IuCryptTestCase {

	@Test
	public void testUse() {
		assertEquals(Use.SIGN,
				WebKey.builder(Type.EC_P256).algorithm(Algorithm.ES256).ephemeral().use(Use.SIGN).build().getUse());
	}

	@Test
	public void testOps() {
		assertEquals(Operation.DERIVE_KEY, WebKey.builder(Type.EC_P256).algorithm(Algorithm.ES256).ephemeral()
				.ops(Operation.DERIVE_KEY).build().getOps().iterator().next());
	}

	@Test
	public void testBadKeys() {
		final var pub = mock(PublicKey.class);
		assertThrows(UnsupportedOperationException.class, () -> new JwkBuilder(Type.RAW).key(pub));
		final var priv = mock(PrivateKey.class);
		assertThrows(UnsupportedOperationException.class, () -> new JwkBuilder(Type.RAW).key(priv));
	}

	@Test
	public void testRsaNoCrt() throws InvalidKeySpecException, NoSuchAlgorithmException {
		final var rsa = (RSAPrivateCrtKey) EphemeralKeys.rsa("RSA", 2048).getPrivate();
		final var noCrt = KeyFactory.getInstance("RSA")
				.generatePrivate(new RSAPrivateKeySpec(rsa.getModulus(), rsa.getPrivateExponent()));
		assertEquals(noCrt, new JwkBuilder(Type.RSA).key(noCrt).build().getPrivateKey());
	}

	@Test
	public void testRsaNoPrivate() throws InvalidKeySpecException, NoSuchAlgorithmException {
		final var rsa = EphemeralKeys.rsa("RSA", 2048).getPublic();
		assertEquals(rsa, new JwkBuilder(Type.RSA).key(rsa).build().getPublicKey());
	}

	@Test
	public void testRsaMultiCrtUnsupported() throws InvalidKeySpecException, NoSuchAlgorithmException {
		final var json = IuJson.object();
		((Jwk) WebKey.ephemeral(Algorithm.PS256)).serializeTo(json);
		json.add("oth", true);
		assertThrows(UnsupportedOperationException.class, () -> new Jwk(json.build()));
	}

	@Test
	public void testECNoPub() throws InvalidKeySpecException, NoSuchAlgorithmException {
		final var key = EphemeralKeys.ec(Type.EC_P256.ecParam).getPrivate();
		assertEquals(key, new JwkBuilder(Type.EC_P256).key(key).build().getPrivateKey());
	}

	@Test
	public void testWellKnownSameSame() throws InvalidKeySpecException, NoSuchAlgorithmException {
		final var key = WebKey.builder(Type.EC_P384).cert(uri(ANOTHER_CERT_TEXT)).build();
		final var wellKnown = key.wellKnown();
		assertEquals(WebKey.verify(key), wellKnown.getPublicKey());
		assertEquals(wellKnown, wellKnown.wellKnown());
	}

	@Test
	public void testPem() {
		final var priv = PemEncoded.parse(EC_PRIVATE_KEY).next().asPrivate(Type.EC_P384);
		final var cert = PemEncoded.parse(ANOTHER_CERT_TEXT).next().asCertificate();
		final var pub = cert.getPublicKey();
		final var text = new StringBuilder();
		PemEncoded.serialize(new KeyPair(pub, null)).forEachRemaining(text::append);
		assertEquals(pub, WebKey.verify(new JwkBuilder(Type.EC_P384).pem(text.toString()).build()));

		PemEncoded.serialize(new KeyPair(pub, priv), cert).forEachRemaining(text::append);
		final var jwk = new JwkBuilder(Type.EC_P384).pem(text.toString()).build();
		assertEquals(jwk,
				new JwkBuilder(Type.EC_P384).pem(new ByteArrayInputStream(IuText.utf8(text.toString()))).build());
	}

	@Test
	public void testEqualsHashCode() {
		for (final var alg : Set.of(Algorithm.DIRECT, Algorithm.RSA_OAEP_256, Algorithm.ECDH_ES_A192KW)) {
			final var ao = (Jwk) (alg.equals(Algorithm.DIRECT)
					? WebKey.builder(alg.type).ephemeral(IuTest.rand(Encryption.class)).use(Use.ENCRYPT)
							.ops(Operation.ENCRYPT)
					: WebKey.builder(alg.type).ephemeral(alg).use(alg.use).ops(IuTest.rand(Operation.class),
							IuTest.rand(Operation.class)))
					.build();
			final var type = ao.getType();

			final var ab = IuJson.object();
			ao.serializeTo(ab);
			final var a = ab.build();

			final var bo = (Jwk) (alg.equals(Algorithm.DIRECT)
					? WebKey.builder(alg.type).ephemeral(IuTest.rand(Encryption.class)).use(Use.ENCRYPT)
							.ops(Operation.ENCRYPT)
					: WebKey.builder(alg.type).ephemeral(alg).use(alg.use).ops(IuTest.rand(Operation.class),
							IuTest.rand(Operation.class)))
					.build();

			final var bb = IuJson.object();
			bo.serializeTo(bb);
			final var b = bb.build();

			assertNotEquals(ao, null);
			assertNotEquals(ao.hashCode(), bo.hashCode());

			assertFalse(ao.represents(
					(Jwk) WebKey.builder(alg.type).use(alg.use == Use.ENCRYPT ? Use.SIGN : Use.ENCRYPT).build()));
			assertFalse(((Jwk) WebKey.builder(alg.type).id(IdGenerator.generateId()).build())
					.represents((Jwk) WebKey.builder(alg.type).id(IdGenerator.generateId()).build()));

			final var altRsa = (Jwk) WebKey.ephemeral(Algorithm.RSA_OAEP);
			final var altEc = (Jwk) WebKey.ephemeral(Algorithm.ES384);
			for (var i = 1; i < 16; i++)
				for (var j = 1; j < 16; j++)
					if (i != j) {
						final var differentType = ((Supplier<Type>) () -> {
							Type t;
							do
								t = IuTest.rand(Type.class);
							while (t.equals(type));
							return t;
						}).get();

						final Jwk keyWithDifferentType;
						switch (differentType) {
						case EC_P256:
						case EC_P384:
						case EC_P521:
							keyWithDifferentType = altRsa;
							break;
						case RSA:
						case RSASSA_PSS:
						case RAW:
						default:
							keyWithDifferentType = altEc;
							break;
						}
						assertNotEquals(ao, keyWithDifferentType);
						assertNotEquals(keyWithDifferentType, ao);
						assertNotEquals(bo, keyWithDifferentType);
						assertNotEquals(keyWithDifferentType, bo);
						assertFalse(ao.represents(keyWithDifferentType));
						assertFalse(bo.represents(keyWithDifferentType));

						final var ai = IuJson.object();
						ai.add("kty", type.kty);
						IuJson.add(ai, "crv", type.crv);
						if ((i & 1) == 1)
							IuJson.add(ai, "use", a.get("use"));
						if ((i & 2) == 2)
							IuJson.add(ai, "key_ops", a.get("key_ops"));
						if ((i & 4) == 4) {
							IuJson.add(ai, "k", a.get("k"));
							IuJson.add(ai, "n", a.get("n"));
							IuJson.add(ai, "e", a.get("e"));
							IuJson.add(ai, "d", a.get("d"));
							IuJson.add(ai, "p", a.get("p"));
							IuJson.add(ai, "q", a.get("q"));
							IuJson.add(ai, "dp", a.get("dp"));
							IuJson.add(ai, "dq", a.get("dq"));
							IuJson.add(ai, "qi", a.get("qi"));
						}
						if ((i & 8) == 8) {
							if ((i & 4) != 4) {
								IuJson.add(ai, "n", a.get("n"));
								IuJson.add(ai, "e", a.get("e"));
							}
							IuJson.add(ai, "x", a.get("x"));
							IuJson.add(ai, "y", a.get("y"));
						}
						final var ac = new Jwk(ai.build());

						final var bj = IuJson.object();
						bj.add("kty", type.kty);
						IuJson.add(bj, "crv", type.crv);
						if ((j & 1) == 1)
							IuJson.add(bj, "use", b.get("use"));
						if ((j & 2) == 2)
							IuJson.add(bj, "key_ops", b.get("key_ops"));
						if ((j & 4) == 4) {
							IuJson.add(bj, "k", b.get("k"));
							IuJson.add(bj, "n", b.get("n"));
							IuJson.add(bj, "e", b.get("e"));
							IuJson.add(bj, "d", b.get("d"));
							IuJson.add(bj, "p", b.get("p"));
							IuJson.add(bj, "q", b.get("q"));
							IuJson.add(bj, "dp", b.get("dp"));
							IuJson.add(bj, "dq", b.get("dq"));
							IuJson.add(bj, "qi", b.get("qi"));
						}
						if ((j & 8) == 8) {
							IuJson.add(bj, "x", b.get("x"));
							IuJson.add(bj, "y", b.get("y"));
							if ((j & 4) != 4) {
								IuJson.add(bj, "n", b.get("n"));
								IuJson.add(bj, "e", b.get("e"));
							}
						}
						final var bc = new Jwk(bj.build());

						assertEquals(ac, new Jwk(IuJson.parse(ac.toString()).asJsonObject()));
						assertEquals(bc, new Jwk(IuJson.parse(bc.toString()).asJsonObject()));
						assertEquals(ac.equals(bc), bc.equals(ac));
						assertTrue(ac.represents(ao));
						assertTrue(bc.represents(bo), bc + " " + bo);
						assertEquals(ac.represents(bc), bc.represents(ac));
					}
		}
	}

	@Test
	public void testEphemerals() {
		assertThrows(UnsupportedOperationException.class, () -> new JwkBuilder(Type.RAW).ephemeral(Algorithm.DIRECT));
		for (Algorithm algorithm : Algorithm.values()) {
			if (algorithm.equals(Algorithm.DIRECT))
				for (Encryption encryption : Encryption.values())
					assertEphemeral(
							new JwkBuilder(algorithm.type).id(IdGenerator.generateId()).ephemeral(encryption).build());
			else
				assertEphemeral(
						new JwkBuilder(algorithm.type).id(IdGenerator.generateId()).ephemeral(algorithm).build());
		}
	}

	private void assertEphemeral(Jwk jwk) {
		assertEquals(jwk, new Jwk(IuJson.parse(jwk.toString()).asJsonObject()));

		final var wellKnown = jwk.wellKnown();
		assertNull(wellKnown.getKey());
		assertNull(wellKnown.getPrivateKey());
		assertEquals(wellKnown.getPublicKey(), WebKey.verify(jwk));
		assertArrayEquals(wellKnown.getCertificateChain(), WebCertificateReference.verify(jwk));

		final var jwksText = Jwk.asJwks(IuIterable.iter(jwk)).toString();
		final var fromInput = Jwk.readJwks(new ByteArrayInputStream(IuText.utf8(jwksText)));
		final var fromParse = Jwk.parseJwks(IuJson.parse(jwksText).asJsonObject());
		assertTrue(IuIterable.remaindersAreEqual(fromInput.iterator(), fromParse.iterator()));

		final var out = new ByteArrayOutputStream();
		Jwk.writeJwks(fromInput, out);
		assertEquals(jwksText, IuText.utf8(out.toByteArray()));

		final var jwks = uri(jwksText);
		final var fromJwks = Jwk.readJwks(jwks).iterator().next();
		assertEquals(jwk, fromJwks);
		assertSame(fromJwks, Jwk.readJwks(jwks).iterator().next());
	}

}
