package iu.auth.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.StringReader;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.ECGenParameterSpec;
import java.util.EnumSet;
import java.util.List;

import org.junit.jupiter.api.Test;

import edu.iu.IdGenerator;
import edu.iu.auth.session.IuSessionProviderKey;
import edu.iu.auth.session.IuSessionProviderKey.Type;
import jakarta.json.Json;

@SuppressWarnings("javadoc")
public class TokenIssuerKeySetTest {

	@Test
	public void testRsa() throws Exception {
		final var id = IdGenerator.generateId();
		final var rsaKeygen = KeyPairGenerator.getInstance("RSA");
		rsaKeygen.initialize(1024);
		final var keyPair = rsaKeygen.generateKeyPair();
		final var issuerKey = new IuSessionProviderKey() {
			@Override
			public String getId() {
				return id;
			}

			@Override
			public Usage getUsage() {
				return Usage.ENCRYPT;
			}

			@Override
			public Type getType() {
				return Type.RSA;
			}

			@Override
			public PublicKey getPublic() {
				return keyPair.getPublic();
			}

			@Override
			public PrivateKey getPrivate() {
				return keyPair.getPrivate();
			}
		};

		final var issuerKeySet = new TokenIssuerKeySet(List.of(issuerKey));
		assertThrows(NullPointerException.class, () -> issuerKeySet.getAlgorithm("foo", "bar"));
		assertThrows(UnsupportedOperationException.class, () -> issuerKeySet.getAlgorithm(id, "bar"));
		assertThrows(ClassCastException.class, () -> issuerKeySet.getAlgorithm(id, "ES256"));
		assertNotNull(issuerKeySet.getAlgorithm(id, "RS256"));
		assertNotNull(issuerKeySet.getAlgorithm(id, "RS384"));
		assertSame(issuerKeySet.getAlgorithm(id, "RS512"), issuerKeySet.getAlgorithm(id, "RS512"));
		assertEquals(id, Json.createReader(new StringReader(issuerKeySet.publish())).readObject().getJsonArray("keys")
				.getJsonObject(0).getString("kid"));
	}

	@Test
	public void testEC256() throws Exception {
		for (final var type : EnumSet.of(Type.EC_P256, Type.EC_P384, Type.EC_P521)) {
			final var id = IdGenerator.generateId();
			final var ecKeygen = KeyPairGenerator.getInstance("EC");
			ecKeygen.initialize(new ECGenParameterSpec("secp256r1"));
			final var keyPair = ecKeygen.generateKeyPair();
			final var issuerKey = new IuSessionProviderKey() {
				@Override
				public String getId() {
					return id;
				}

				@Override
				public Usage getUsage() {
					return Usage.SIGN;
				}

				@Override
				public Type getType() {
					return type;
				}

				@Override
				public PublicKey getPublic() {
					return keyPair.getPublic();
				}

				@Override
				public PrivateKey getPrivate() {
					return keyPair.getPrivate();
				}
			};

			final var issuerKeySet = new TokenIssuerKeySet(List.of(issuerKey));
			assertThrows(NullPointerException.class, () -> issuerKeySet.getAlgorithm("foo", "bar"));
			assertThrows(UnsupportedOperationException.class, () -> issuerKeySet.getAlgorithm(id, "bar"));
			assertThrows(ClassCastException.class, () -> issuerKeySet.getAlgorithm(id, "RS256"));
			assertNotNull(issuerKeySet.getAlgorithm(id, "ES256"));
			assertNotNull(issuerKeySet.getAlgorithm(id, "ES384"));
			assertSame(issuerKeySet.getAlgorithm(id, "ES512"), issuerKeySet.getAlgorithm(id, "ES512"));
			assertEquals(id, Json.createReader(new StringReader(issuerKeySet.publish())).readObject()
					.getJsonArray("keys").getJsonObject(0).getString("kid"));
		}
	}

}
