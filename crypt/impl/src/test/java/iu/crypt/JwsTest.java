/*
 * Copyright © 2026 Indiana University
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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.util.ArrayDeque;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;

import org.junit.jupiter.api.Test;

import edu.iu.IdGenerator;
import edu.iu.IuDigest;
import edu.iu.IuException;
import edu.iu.IuIterable;
import edu.iu.IuProcess;
import edu.iu.IuText;
import edu.iu.client.IuJson;
import edu.iu.crypt.PemEncoded;
import edu.iu.crypt.WebCryptoHeader;
import edu.iu.crypt.WebCryptoHeader.Param;
import edu.iu.crypt.WebKey;
import edu.iu.crypt.WebKey.Algorithm;
import edu.iu.crypt.WebKey.Type;
import edu.iu.crypt.WebKey.Use;
import edu.iu.crypt.WebSignature;
import edu.iu.crypt.WebSignedPayload;
import edu.iu.crypt.X500Utils;
import edu.iu.test.IuTestLogger;
import iu.crypt.Jose.Extension;
import jakarta.json.bind.JsonbException;

@SuppressWarnings("javadoc")
public class JwsTest {

	@Test
	public void testRFC8037_A_4() {
		final var jwk = WebKey.parse("{\"kty\":\"OKP\",\"crv\":\"Ed25519\",\n"
				+ "   \"d\":\"nWGxne_9WmC6hEr0kuwsxERJxWl7MmkZcDusAxyuf2A\",\n"
				+ "   \"x\":\"11qYAYKxCrfVS_7TyWQHOg7hcvPapiMlrwIaaPcHURo\"}");
		assertEquals("{\"kty\":\"OKP\",\"crv\":\"Ed25519\",\"x\":\"11qYAYKxCrfVS_7TyWQHOg7hcvPapiMlrwIaaPcHURo\"}",
				jwk.wellKnown().toString());

		final var jws = WebSignature.builder(Algorithm.EDDSA).compact().key(jwk).sign("Example of Ed25519 signing");
		final var compact = jws.compact();
		assertEquals("eyJhbGciOiJFZERTQSJ9.RXhhbXBsZSBvZiBFZDI1NTE5IHNpZ25pbmc.hgyY0il_MGCj"
				+ "P0JzlnLWG1PPOt7-09PGcvMg3AIbQR6dWbhijcNR4ki4iylGjg5BhVsPt9g7sVvpAr_MuM0KAg", compact);

		final var fromCompact = WebSignedPayload.parse(compact);
		assertEquals("{\"alg\":\"EdDSA\"}", fromCompact.getSignatures().iterator().next().getHeader().toString());
		assertDoesNotThrow(() -> fromCompact.verify(jwk));

		final var wrongKey = WebKey.ephemeral(Algorithm.EDDSA);
		assertThrows(IllegalArgumentException.class, () -> fromCompact.verify(wrongKey));
	}

	@Test
	public void testMultipleSignatures() {
		assertNull(CryptJsonAdapters.JSONB.fromJson(CryptJsonAdapters.JSONB.toJson(null), WebSignedPayload.class));

		final Queue<Jwk> keys = new ArrayDeque<>();
		final var key = (Jwk) WebKey.ephemeral(Algorithm.ES256);
		keys.add(key);

		final var key2 = (Jwk) WebKey.ephemeral(Algorithm.PS384);
		keys.add(key2);

		final var jwsBuilder = WebSignature.builder(Algorithm.ES256);
		jwsBuilder.key(key);
		jwsBuilder.next(Algorithm.PS384);
		jwsBuilder.key(key2);

		final var id = IdGenerator.generateId();
		final var jws = jwsBuilder.sign(id);
		final var serialJws = CryptJsonAdapters.JSONB.fromJson(CryptJsonAdapters.JSONB.toJson(jws),
				WebSignedPayload.class);
		assertDoesNotThrow(() -> serialJws.verify(key));
		assertDoesNotThrow(() -> serialJws.verify(key2));
	}

	@Test
	public void testRequiresSignatureAlgorithm() {
		final var jose = mock(Jose.class);
		when(jose.getAlgorithm()).thenReturn(Algorithm.ECDH_ES);
		final var error = assertThrows(IllegalArgumentException.class, () -> new Jws((String) null, jose, null));
		assertEquals("Signature algorithm is required", error.getMessage());
	}

	@SuppressWarnings("unchecked")
	@Test
	public void testAllTheSignatures() {
		final var extName = IdGenerator.generateId();
		final var ext = mock(Extension.class, CALLS_REAL_METHODS);
		when(ext.type()).thenReturn(String.class);
		Jose.register(extName, ext);

		final Queue<Jwk> keys = new ArrayDeque<>();
		final var algorithmIterator = IuIterable
				.filter(IuIterable.iter(Algorithm.values()), a -> a.use.equals(Use.SIGN)).iterator();

		var algorithm = algorithmIterator.next();
		final var jwsBuilder = WebSignature.builder(algorithm);

		var first = true;
		do {
			if (first)
				first = false;
			else
				jwsBuilder.next(algorithm = algorithmIterator.next());
			final var key = (Jwk) WebKey.ephemeral(algorithm);
			keys.add(key);
			jwsBuilder.key(key);
			jwsBuilder.param(extName, IdGenerator.generateId());

			final var id = IdGenerator.generateId();
			final var compactJws = WebSignature.builder(algorithm).key(key).compact().sign(id);
			final var fromCompact = CryptJsonAdapters.JSONB.fromJson(CryptJsonAdapters.JSONB.toJson(compactJws),
					WebSignedPayload.class);
			assertEquals(id, IuText.utf8(fromCompact.getPayload()));
			fromCompact.verify(key);

			final var fromSerial = CryptJsonAdapters.JSONB.fromJson(compactJws.toString(), WebSignedPayload.class);
			assertEquals(id, IuText.utf8(fromSerial.getPayload()));
			fromSerial.verify(key);
		} while (algorithmIterator.hasNext());

		final var data = new byte[16384];
		ThreadLocalRandom.current().nextBytes(data);
		final var jws = jwsBuilder.sign(data);
		assertThrows(IllegalStateException.class, () -> jws.compact());
		final var serial = jws.toString();
		final var fromSerial = WebSignedPayload.parse(serial);
		keys.forEach(fromSerial::verify);
		fromSerial.getSignatures().forEach(a -> verify(ext).verify(a));
	}

	@Test
	public void testHeaderVerification() {
		final var p = CryptJsonAdapters.builder().put("alg", "HS256").build();
		final var jose = new Jose(CryptJsonAdapters.builder().put("alg", "HS384").build());
		assertThrows(IllegalArgumentException.class, () -> new Jws(p, jose, null));

		final var extName = IdGenerator.generateId();
		Jose.register(extName, new StringExtension());
		final var p2 = CryptJsonAdapters.builder().put("alg", "HS256").put(extName, IdGenerator.generateId()).build();
		final var jose2 = new Jose(
				CryptJsonAdapters.builder().put("alg", "HS256").put(extName, IdGenerator.generateId()).build());
		assertThrows(IllegalArgumentException.class, () -> new Jws(p2, jose2, null));
		assertDoesNotThrow(() -> new Jws(p2, new Jose(p2), null));
	}

	@Test
	public void testInvalidCompact() {
		final var key = WebKey.ephemeral(Algorithm.HS256);
		final var jws = WebSignature.builder(Algorithm.HS256).key(key).compact().sign("foo");
		assertThrows(IllegalArgumentException.class, () -> WebSignedPayload.parse(jws.compact() + ".blah"));
	}

	@Test
	public void testInvalidAlgorithm() {
		assertThrows(IllegalArgumentException.class, () -> WebSignature.builder(Algorithm.ECDH_ES));
	}

	@Test
	public void testUnprotected() {
		final var key = WebKey.ephemeral(Algorithm.HS256);
		final var jws = WebSignature.builder(Algorithm.HS256).key(key).sign("foo");
		assertNull(IuJson.parse(jws.toString()).asJsonObject().get("protected"));
		assertThrows(NullPointerException.class, () -> WebSignedPayload.parse(jws.compact()));
	}

	@Test
	public void testProtected() {
		final var key = WebKey.ephemeral(Algorithm.HS256);
		final var jws = WebSignature.builder(Algorithm.HS256).key(key).protect(Param.ALGORITHM).sign("foo");
		// RFC 7515 7.2.2 flattened: a single signature's members at the top level
		assertNotNull(IuJson.parse(jws.toString()).asJsonObject().getString("protected"));
		assertDoesNotThrow(() -> WebSignedPayload.parse(jws.compact()));
	}

	@Test
	public void testTheWorks() {
		final var id = IdGenerator.generateId();
		final var key = WebKey.builder(Algorithm.EDDSA).keyId(id).ephemeral().build();
		final var jwsBuilder = WebSignature.builder(Algorithm.EDDSA).wellKnown(key);
		final var ext = IdGenerator.generateId();
		Jose.register(ext, new StringExtension());
		jwsBuilder.protect(Param.ALGORITHM, Param.CONTENT_TYPE);
		jwsBuilder.protect(ext);
		jwsBuilder.crit(Param.ALGORITHM.name, ext);
		jwsBuilder.keyId(id);

		final var uri = URI.create(IdGenerator.generateId());
		jwsBuilder.wellKnown(uri);
		jwsBuilder.param(Param.TYPE, "baz");
		jwsBuilder.type("baz");

		jwsBuilder.contentType("foo").param(ext, "bar");
		final var jws = jwsBuilder.sign("foo");

		assertDoesNotThrow(() -> WebSignedPayload.parse(jws.compact()).verify(key));
		assertDoesNotThrow(() -> WebSignedPayload.parse(jws.toString()).verify(key));
	}

	@SuppressWarnings("deprecation")
	@Test
	public void testCertRef() {
		final var x5u = URI.create(IdGenerator.generateId());
		final var id = IdGenerator.generateId();
		final var key = WebKey.builder(Algorithm.EDDSA).keyId(id).ephemeral().build();
		final var privateKey = Objects.requireNonNull(key.getPrivateKey(), "Missing private key");
		final var privateKeyFile = IuProcess.temp(PemEncoded::print, privateKey);

		IuTestLogger.allow(IuProcess.class.getName(), Level.FINE);
		final var pemCert = IuProcess.exec( //
				"openssl", "req", "-x509", "-key", privateKeyFile.toString(), "-days", "1", //
				"-subj", "/CN=" + id.replaceAll("([+=/])", "\\\\$1"), //
				"-addext", "basicConstraints=CA:false", //
				"-addext", "keyUsage=" + X500Utils.keyUsage(key) //
		);
		IuProcess.deleteTempFiles();

		final var self = WebKey.builder(key.getType()) //
				.keyId(id) //
				.key(privateKey) //
				.key(key.getPublicKey()) // s
				.algorithm(key.getAlgorithm()) //
				.pem(pemCert) //
				.build();
		final var cert = self.getCertificateChain()[0];

		final var jwsBuilder = WebSignature.builder(Algorithm.EDDSA).compact();
		jwsBuilder.key(self);
		jwsBuilder.algorithm(key.getAlgorithm());
		jwsBuilder.cert(cert);
		jwsBuilder.cert(x5u);
		jwsBuilder.x5t(IuDigest.sha1(IuException.unchecked(cert::getEncoded)));
		jwsBuilder.x5t256(IuDigest.sha256(IuException.unchecked(cert::getEncoded)));

		final var payload = IdGenerator.generateId();
		final var jws = jwsBuilder.sign(payload);
		final var header = jws.getSignatures().iterator().next().getHeader();
		assertEquals(cert, header.getCertificateChain()[0]);
		assertEquals(x5u, header.getCertificateUri());
		assertArrayEquals(IuDigest.sha1(IuException.unchecked(cert::getEncoded)), header.getCertificateThumbprint());
		assertArrayEquals(IuDigest.sha256(IuException.unchecked(cert::getEncoded)),
				header.getCertificateSha256Thumbprint());
	}

	@Test
	public void testPem() {
		final var id = IdGenerator.generateId();
		final var key = WebKey.builder(Algorithm.EDDSA).keyId(id).ephemeral().build();
		final var privateKey = Objects.requireNonNull(key.getPrivateKey(), "Missing private key");
		final var privateKeyFile = IuProcess.temp(PemEncoded::print, privateKey);

		IuTestLogger.allow(IuProcess.class.getName(), Level.FINE);
		final var pemCert = IuProcess.exec( //
				"openssl", "req", "-x509", "-key", privateKeyFile.toString(), "-days", "1", //
				"-subj", "/CN=" + id.replaceAll("([+=/])", "\\\\$1"), //
				"-addext", "basicConstraints=CA:false", //
				"-addext", "keyUsage=" + X500Utils.keyUsage(key) //
		);
		IuProcess.deleteTempFiles();

		final var self = WebKey.builder(key.getType()) //
				.keyId(id) //
				.key(privateKey) //
				.key(key.getPublicKey()) // s
				.algorithm(key.getAlgorithm()) //
				.pem(pemCert) //
				.build();
		final var cert = self.getCertificateChain()[0];

		final var jwsBuilder = WebSignature.builder(Algorithm.EDDSA).compact();
		jwsBuilder.key(self);
		jwsBuilder.pem(pemCert);

		final var payload = IdGenerator.generateId();
		final var jws = jwsBuilder.sign(payload);
		final var header = jws.getSignatures().iterator().next().getHeader();
		assertEquals(cert, header.getCertificateChain()[0]);
	}

	@Test
	public void testPemFromStream() {
		final var id = IdGenerator.generateId();
		final var key = WebKey.builder(Algorithm.EDDSA).keyId(id).ephemeral().build();
		final var privateKey = Objects.requireNonNull(key.getPrivateKey(), "Missing private key");
		final var privateKeyFile = IuProcess.temp(PemEncoded::print, privateKey);

		IuTestLogger.allow(IuProcess.class.getName(), Level.FINE);
		final var pemCert = IuProcess.exec( //
				"openssl", "req", "-x509", "-key", privateKeyFile.toString(), "-days", "1", //
				"-subj", "/CN=" + id.replaceAll("([+=/])", "\\\\$1"), //
				"-addext", "basicConstraints=CA:false", //
				"-addext", "keyUsage=" + X500Utils.keyUsage(key) //
		);
		IuProcess.deleteTempFiles();

		final var self = WebKey.builder(key.getType()) //
				.keyId(id) //
				.key(privateKey) //
				.key(key.getPublicKey()) // s
				.algorithm(key.getAlgorithm()) //
				.pem(pemCert) //
				.build();
		final var cert = self.getCertificateChain()[0];

		final var jwsBuilder = WebSignature.builder(Algorithm.EDDSA).compact();
		jwsBuilder.key(self);
		jwsBuilder.pem(new ByteArrayInputStream(pemCert.getBytes()));

		final var payload = IdGenerator.generateId();
		final var jws = jwsBuilder.sign(payload);
		final var header = jws.getSignatures().iterator().next().getHeader();
		assertEquals(cert, header.getCertificateChain()[0]);
	}

	@Test
	public void testRFC7515_A_3() {
		final var jwk = WebKey.parse("{\"kty\":\"EC\",\n" //
				+ "      \"crv\":\"P-256\",\n" //
				+ "      \"x\":\"f83OJ3D2xF1Bg8vub9tLe1gHMzV76e8Tus9uPHvRVEU\",\n" //
				+ "      \"y\":\"x_FEzRu9m36HLN_tue659LNpXW6pCyStikYjKIWI5a0\",\n" //
				+ "      \"d\":\"jpsQnnGQmL-YBIffH1136cspYG6-0iY7X1fCE9-E9LI\"\n" //
				+ "     }");

		final var jws = WebSignedPayload.parse("eyJhbGciOiJFUzI1NiJ9." //
				+ "eyJpc3MiOiJqb2UiLA0KICJleHAiOjEzMDA4MTkzODAsDQogImh0dHA6Ly9leGFtcGxlLmNvbS9pc19yb290Ijp0cnVlfQ." //
				+ "DtEhU3ljbEg8L38VWAfUAqOyKAM6-Xx-F4GawxaepmXFCgfTjDxw5djxLa8ISlSApmWQxfKTUJqPP3-Kg6NU1Q");
		jws.verify(jwk);
	}

	@Test
	public void testRFC7515_A_4() {
		final var jwk = WebKey.parse("{\"kty\":\"EC\",\n" + "      \"crv\":\"P-521\",\n"
				+ "      \"x\":\"AekpBQ8ST8a8VcfVOTNl353vSrDCLLJXmPk06wTjxrrjcBpXp5EOnYG_NjFZ6OvLFV1jSfS9tsz4qUxcWceqwQGk\",\n"
				+ "      \"y\":\"ADSmRA43Z1DSNx_RvcLI87cdL07l6jQyyBXMoxVg_l2Th-x3S1WDhjDly79ajL4Kkd0AZMaZmh9ubmf63e3kyMj2\",\n"
				+ "      \"d\":\"AY5pb7A0UFiB3RELSD64fTLOSV_jazdF7fLYyuTw8lOfRhWg6Y6rUrPAxerEzgdRhajnu0ferB0d53vM9mE15j2C\"\n"
				+ "     }");

		final var jws = WebSignedPayload.parse("eyJhbGciOiJFUzUxMiJ9.UGF5bG9hZA."
				+ "AdwMgeerwtHoh-l192l60hp9wAHZFVJbLfD_UxMi70cwnZOYaRI1bKPWROc-mZZqwqT2SI-KGDKB34XO0aw_7Xdt"
				+ "AG8GaSwFKdCAPZgoXD2YBJZCPEX3xKpRwcdOO8KpEHwJjyqOgzDO7iKvU8vcnwNrmxYbSW9ERBXukOXolLzeO_Jn");
		jws.verify(jwk);
	}

	@Test
	public void testRFC7515_A_4_CVE2022_21449() {
		final var jwk = WebKey.parse("{\"kty\":\"EC\",\n" + "      \"crv\":\"P-521\",\n"
				+ "      \"x\":\"AekpBQ8ST8a8VcfVOTNl353vSrDCLLJXmPk06wTjxrrjcBpXp5EOnYG_NjFZ6OvLFV1jSfS9tsz4qUxcWceqwQGk\",\n"
				+ "      \"y\":\"ADSmRA43Z1DSNx_RvcLI87cdL07l6jQyyBXMoxVg_l2Th-x3S1WDhjDly79ajL4Kkd0AZMaZmh9ubmf63e3kyMj2\",\n"
				+ "      \"d\":\"AY5pb7A0UFiB3RELSD64fTLOSV_jazdF7fLYyuTw8lOfRhWg6Y6rUrPAxerEzgdRhajnu0ferB0d53vM9mE15j2C\"\n"
				+ "     }");

		final var jws = WebSignedPayload.parse("eyJhbGciOiJFUzUxMiJ9.UGF5bG9hZA."
				+ "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
				+ "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA");
		assertThrows(IllegalArgumentException.class, () -> jws.verify(jwk));

		final var jws2 = WebSignedPayload.parse("eyJhbGciOiJFUzUxMiJ9.UGF5bG9hZA."
				+ "AdwMgeerwtHoh-l192l60hp9wAHZFVJbLfD_UxMi70cwnZOYaRI1bKPWROc-mZZqwqT2SI-KGDKB34XO0aw_7Xdt"
				+ "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA");
		assertThrows(IllegalArgumentException.class, () -> jws2.verify(jwk));
	}

	@Test
	public void testFromJCE() {
		assertThrows(IllegalArgumentException.class, () -> Jws.fromJce(Type.RSA, Algorithm.ES256, new byte[0]));
		assertThrows(IllegalArgumentException.class, () -> Jws.fromJce(Type.EC_P256, Algorithm.ES256, new byte[0]));

		final var b256 = new byte[72];
		assertThrows(IllegalArgumentException.class, () -> Jws.fromJce(Type.EC_P256, Algorithm.ES256, b256));

		final var b521 = new byte[140];
		b521[0] = (byte) 0x30;
		assertThrows(IllegalArgumentException.class, () -> Jws.fromJce(Type.EC_P521, Algorithm.ES512, b521));

		b256[0] = (byte) 0x30;
		assertThrows(IllegalArgumentException.class, () -> Jws.fromJce(Type.EC_P256, Algorithm.ES256, b256));

		b256[1] = (byte) 70;
		assertThrows(IllegalArgumentException.class, () -> Jws.fromJce(Type.EC_P256, Algorithm.ES256, b256));

		b256[2] = (byte) 0x02;
		assertThrows(IllegalArgumentException.class, () -> Jws.fromJce(Type.EC_P256, Algorithm.ES256, b256));

		b256[3] = (byte) -1;
		assertThrows(IllegalArgumentException.class, () -> Jws.fromJce(Type.EC_P256, Algorithm.ES256, b256));

		b256[3] = (byte) 32;
		assertThrows(IllegalArgumentException.class, () -> Jws.fromJce(Type.EC_P256, Algorithm.ES256, b256));

		b256[36] = (byte) 0x02;
		b256[37] = (byte) 32;
		assertThrows(IllegalArgumentException.class, () -> Jws.fromJce(Type.EC_P256, Algorithm.ES256, b256));

	}

	private static final String RFC7515_A_3_KEY = "{\"kty\":\"EC\",\"crv\":\"P-256\"," //
			+ "\"x\":\"f83OJ3D2xF1Bg8vub9tLe1gHMzV76e8Tus9uPHvRVEU\"," //
			+ "\"y\":\"x_FEzRu9m36HLN_tue659LNpXW6pCyStikYjKIWI5a0\"}";

	private static final String RFC7515_A_3_PAYLOAD = "eyJpc3MiOiJqb2UiLA0KICJleHAiOjEzMDA4MTkzODAsDQogImh0dHA6Ly9leGFtcGxlLmNvbS9pc19yb290Ijp0cnVlfQ";

	private static final String RFC7515_A_3_SIGNATURE = "DtEhU3ljbEg8L38VWAfUAqOyKAM6-Xx-F4GawxaepmXFCgfTjDxw5djxLa8ISlSApmWQxfKTUJqPP3-Kg6NU1Q";

	@Test
	public void testRFC7515_A_1() {
		// the protected header's JSON text has whitespace: the signature input is
		// the encoding as received, not a re-serialization
		final var key = WebKey.parse("{\"kty\":\"oct\","
				+ "\"k\":\"AyM1SysPpbyDfgZld3umj1qzKObwVMkoqQ-EstJQLr_T-1qS0gZH75aKtMN3Yj0iPS4hcgUuTwjAzZr1Z9CAow\"}");
		final var compact = "eyJ0eXAiOiJKV1QiLA0KICJhbGciOiJIUzI1NiJ9." + RFC7515_A_3_PAYLOAD
				+ ".dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk";

		final var jws = WebSignedPayload.parse(compact);
		jws.verify(key);
		assertEquals(compact, jws.compact());
		assertEquals("JWT", jws.getSignatures().iterator().next().getHeader().getType());

		final var serialized = jws.toString();
		assertEquals("eyJ0eXAiOiJKV1QiLA0KICJhbGciOiJIUzI1NiJ9",
				IuJson.parse(serialized).asJsonObject().getString("protected"));
		WebSignedPayload.parse(serialized).verify(key);
	}

	@Test
	public void testRFC7515_A_7() {
		// flattened, with "alg" only in the protected header
		final var serialized = IuJson.object() //
				.add("payload", RFC7515_A_3_PAYLOAD) //
				.add("protected", "eyJhbGciOiJFUzI1NiJ9") //
				.add("header", IuJson.object().add("kid", "e9bc097a-ce51-4036-9562-d2ade882db0d")) //
				.add("signature", RFC7515_A_3_SIGNATURE) //
				.build();

		final var jws = WebSignedPayload.parse(serialized.toString());
		jws.verify(WebKey.parse(RFC7515_A_3_KEY));

		final var header = jws.getSignatures().iterator().next().getHeader();
		assertEquals(Algorithm.ES256, header.getAlgorithm());
		assertEquals("e9bc097a-ce51-4036-9562-d2ade882db0d", header.getKeyId());

		// writes back as it was read, protected header encoding included
		assertEquals(serialized, IuJson.parse(jws.toString()));
	}

	@Test
	public void testRFC7515_A_6() {
		// general, the RFC's second signature only
		final var serialized = IuJson.object() //
				.add("payload", RFC7515_A_3_PAYLOAD) //
				.add("signatures", IuJson.array().add(IuJson.object() //
						.add("protected", "eyJhbGciOiJFUzI1NiJ9") //
						.add("header", IuJson.object().add("kid", "e9bc097a-ce51-4036-9562-d2ade882db0d")) //
						.add("signature", RFC7515_A_3_SIGNATURE))) //
				.build();

		final var jws = WebSignedPayload.parse(serialized.toString());
		jws.verify(WebKey.parse(RFC7515_A_3_KEY));
		assertEquals(Algorithm.ES256, WebCryptoHeader.getProtectedHeader(serialized.toString()).getAlgorithm());
	}

	@Test
	public void testSerializedErrors() {
		final var payload = IuText.base64Url(IuText.utf8("foo"));
		final var signature = IuJson.object().add("protected", "eyJhbGciOiJFUzI1NiJ9").add("signature",
				RFC7515_A_3_SIGNATURE);

		assertThrows(JsonbException.class, () -> WebSignedPayload.parse(IuJson.object()
				.add("signatures", IuJson.array().add(signature)).build().toString()));
		assertThrows(JsonbException.class, () -> WebSignedPayload
				.parse(IuJson.object().add("payload", payload).add("signatures", IuJson.array()).build().toString()));
		assertThrows(JsonbException.class,
				() -> WebSignedPayload.parse(IuJson.object().add("payload", payload)
						.add("signatures", IuJson.array().add(signature)).add("signature", RFC7515_A_3_SIGNATURE)
						.build().toString()));
		assertThrows(JsonbException.class, () -> WebSignedPayload.parse(IuJson.object().add("payload", payload)
				.add("signature", RFC7515_A_3_SIGNATURE).add("foo", "bar").build().toString()));
	}

	@Test
	public void testCompactHeaderIsBase64Url() {
		// in ASCII JSON text, only a third byte of '>', '?', or '~' encodes as '-'
		// or '_'; three in a row guarantee one
		final var key = WebKey.ephemeral(Algorithm.HS256);
		final var compact = WebSignature.builder(Algorithm.HS256).compact().keyId("???").key(key).sign("foo")
				.compact();
		assertTrue(compact.substring(0, compact.indexOf('.')).matches(".*[-_].*"), compact);

		final var jws = WebSignedPayload.parse(compact);
		jws.verify(key);
		assertEquals(compact, jws.compact());
		assertEquals(Algorithm.HS256, WebCryptoHeader.getProtectedHeader(compact).getAlgorithm());
	}

	@Test
	public void testProtectedOnly() {
		final var key = WebKey.ephemeral(Algorithm.HS256);
		final var jws = WebSignature.builder(Algorithm.HS256).key(key).protect(Param.ALGORITHM).sign("foo");
		final var serialized = IuJson.parse(jws.toString()).asJsonObject();
		assertNull(serialized.get("header"));
		assertDoesNotThrow(() -> WebSignedPayload.parse(serialized.toString()).verify(key));
	}

	@Test
	public void testProtectedCrit() {
		final var ext = IdGenerator.generateId();
		Jose.register(ext, new StringExtension());
		final var ext2 = IdGenerator.generateId();
		Jose.register(ext2, new StringExtension());

		final var key = WebKey.ephemeral(Algorithm.HS256);
		final var jws = WebSignature.builder(Algorithm.HS256).key(key) //
				.protect(Param.ALGORITHM, Param.CRITICAL_PARAMS).protect(ext, ext2) //
				.crit(ext, ext2).param(ext, "foo").param(ext2, "bar").sign("baz");

		for (final var serialized : IuIterable.iter(jws.toString(), jws.compact())) {
			final var parsed = WebSignedPayload.parse(serialized);
			parsed.verify(key);
			assertEquals(Set.of(ext, ext2),
					parsed.getSignatures().iterator().next().getHeader().getCriticalParameters());
		}

		// order doesn't matter; content does
		final var p = CryptJsonAdapters.builder().put("alg", "HS256").put("crit", new String[] { ext, ext2 })
				.put(ext, "foo").put(ext2, "bar").build();
		final var reordered = CryptJsonAdapters.builder().put("alg", "HS256")
				.put("crit", new String[] { ext2, ext }).put(ext, "foo").put(ext2, "bar").build();
		assertDoesNotThrow(() -> new Jws(p, new Jose(reordered), null));

		final var fewer = CryptJsonAdapters.builder().put("alg", "HS256").put("crit", new String[] { ext })
				.put(ext, "foo").put(ext2, "bar").build();
		assertEquals("crit must match protected header",
				assertThrows(IllegalArgumentException.class, () -> new Jws(p, new Jose(fewer), null)).getMessage());
	}

	@SuppressWarnings("unchecked")
	@Test
	public void testParamValidates() {
		final var extName = IdGenerator.generateId();
		final var ext = mock(Extension.class, CALLS_REAL_METHODS);
		when(ext.type()).thenReturn(String.class);
		Jose.register(extName, ext);

		final var value = IdGenerator.generateId();
		final var builder = WebSignature.builder(Algorithm.HS256).param(extName, value);
		verify(ext).validate(eq(value), any());
		assertThrows(NullPointerException.class, () -> builder.param(IdGenerator.generateId(), value));
	}

}
