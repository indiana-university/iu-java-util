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
package edu.iu.config;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.PrintStream;
import java.lang.reflect.Field;
import java.security.cert.X509CRL;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.logging.Level;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import edu.iu.IdGenerator;
import edu.iu.IuProcess;
import edu.iu.client.IuJson;
import edu.iu.client.IuJsonAdapter;
import edu.iu.client.IuVault;
import edu.iu.client.IuVaultKeyedValue;
import edu.iu.crypt.PemEncoded;
import edu.iu.crypt.WebEncryption.Encryption;
import edu.iu.crypt.WebKey;
import edu.iu.crypt.WebKey.Algorithm;
import edu.iu.test.IuTestLogger;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.json.JsonValue;
import jakarta.json.bind.adapter.JsonbAdapter;
import jakarta.json.bind.serializer.JsonbDeserializer;
import jakarta.json.bind.serializer.JsonbSerializer;

@SuppressWarnings("javadoc")
public class IuConfigTest {

	public interface LoadableConfig {
		String getValue();
	}

	public interface LoadableRef {
		LoadableConfig getConfig();
	}

	public interface UnloadableConfig {
	}

	public interface VerifiableConfig {
	}

	public interface KeyRef {
		WebKey getKey();
	}

	public enum Color {
		RED, GREEN
	}

	public interface Formats {
		Color getColor();

		Instant getWhen();

		Duration getTtl();

		Algorithm getAlg();

		Encryption getEnc();

		byte[] getData();

		String getSnakeCaseName();
	}

	public static final class Custom {
		private final String value;

		private Custom(String value) {
			this.value = value;
		}
	}

	public interface CustomConfig {
		Custom getCustom();
	}

	@BeforeEach
	public void setup() throws Exception {
		teardown();
	}

	@AfterEach
	public void teardown() throws Exception {
		Field f;

		f = IuConfig.class.getDeclaredField("sealed");
		f.setAccessible(true);
		f.set(null, false);

		f = IuConfig.class.getDeclaredField("jsonb");
		f.setAccessible(true);
		f.set(null, null);

		for (final var name : List.of("ADAPTERS", "SERIALIZERS", "DESERIALIZERS")) {
			f = IuConfig.class.getDeclaredField(name);
			f.setAccessible(true);
			((List<?>) f.get(null)).clear();
		}

		f = IuConfig.class.getDeclaredField("CONFIG");
		f.setAccessible(true);
		((Map<?, ?>) f.get(null)).clear();
	}

	private static IuVault vault(Map<String, String> values) {
		final var vault = mock(IuVault.class);
		values.forEach((key, value) -> {
			final var vkv = mock(IuVaultKeyedValue.class);
			when(vkv.getValue()).thenReturn(value);
			when(vault.get(key)).thenReturn(vkv);
		});
		return vault;
	}

	@Test
	public void testVault() {
		final var key = IdGenerator.generateId();
		final var invalidKey = IdGenerator.generateId();
		assertThrows(NullPointerException.class, () -> IuConfig.load(LoadableConfig.class, key));

		final var cacheTtl = Duration.ofSeconds(1L);
		final var vault = vault(Map.of("loadable/" + key, "{}"));
		when(vault.get("loadable/" + invalidKey)).thenThrow(IllegalArgumentException.class);
		assertDoesNotThrow(() -> IuConfig.registerInterface("loadable", LoadableConfig.class, cacheTtl, vault));
		assertThrows(IllegalArgumentException.class,
				() -> IuConfig.registerInterface("loadable", LoadableConfig.class, vault));
		assertThrows(IllegalArgumentException.class,
				() -> IuConfig.registerInterface("Invalid", UnloadableConfig.class, vault));

		assertInstanceOf(LoadableConfig.class, IuConfig.load(LoadableConfig.class, key));
		verify(vault).get("loadable/" + key);

		// a string refers to a stored value by key
		assertInstanceOf(LoadableConfig.class, IuConfig.jsonb().fromJson("\"" + key + "\"", LoadableConfig.class));
		verify(vault).get("loadable/" + key); // cached by key
		assertThrows(IllegalArgumentException.class, () -> IuConfig.load(LoadableConfig.class, invalidKey));

		// first use seals registration
		assertThrows(IllegalStateException.class,
				() -> IuConfig.registerInterface("unloadable", UnloadableConfig.class, vault));

		assertDoesNotThrow(() -> Thread.sleep(1000L)); // expires cache
		assertInstanceOf(LoadableConfig.class, IuConfig.load(LoadableConfig.class, key));
		verify(vault, times(2)).get("loadable/" + key); // returned to vault after cache expired
	}

	@Test
	public void testJsonbCreatedOnce() {
		final var jsonb = IuConfig.jsonb();
		assertSame(jsonb, IuConfig.jsonb());
	}

	@SuppressWarnings("unchecked")
	@Test
	public void testRegisterFactory() {
		final var key = IdGenerator.generateId();
		final var factory = mock(Function.class);
		final var config = mock(LoadableConfig.class);
		when(factory.apply(key)).thenReturn(config);

		assertDoesNotThrow(() -> IuConfig.registerFactory(LoadableConfig.class, factory));
		assertThrows(IllegalArgumentException.class, () -> IuConfig.registerFactory(LoadableConfig.class, factory));
		assertSame(config, IuConfig.load(LoadableConfig.class, key));
		assertSame(config, IuConfig.load(LoadableConfig.class, key));
		verify(factory).apply(key);
	}

	@Test
	public void testRegisterFactoryUsesCachedValueForConcurrentLoad() throws Exception {
		final var key = IdGenerator.generateId();
		final var config = mock(LoadableConfig.class);
		final var factoryCalls = new AtomicInteger();
		final var factoryStarted = new CountDownLatch(1);
		final var completeFactory = new CountDownLatch(1);
		IuConfig.registerFactory(LoadableConfig.class, ignored -> {
			factoryCalls.incrementAndGet();
			factoryStarted.countDown();
			try {
				if (!completeFactory.await(5L, TimeUnit.SECONDS))
					throw new IllegalStateException("timed out waiting for concurrent load");
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				throw new IllegalStateException(e);
			}
			return config;
		});

		final var executor = Executors.newFixedThreadPool(2);
		try {
			final var first = executor.submit(() -> IuConfig.load(LoadableConfig.class, key));
			assertTrue(factoryStarted.await(5L, TimeUnit.SECONDS));

			final var secondThread = new AtomicReference<Thread>();
			final var second = executor.submit(() -> {
				secondThread.set(Thread.currentThread());
				return IuConfig.load(LoadableConfig.class, key);
			});
			for (var i = 0; i < 100; i++) {
				final var thread = secondThread.get();
				if (thread != null && thread.getState() == Thread.State.BLOCKED)
					break;
				Thread.sleep(10L);
			}
			final var thread = secondThread.get();
			assertTrue(thread != null && thread.getState() == Thread.State.BLOCKED);

			completeFactory.countDown();
			assertSame(config, first.get(5L, TimeUnit.SECONDS));
			assertSame(config, second.get(5L, TimeUnit.SECONDS));
			assertEquals(1, factoryCalls.get());
		} finally {
			completeFactory.countDown();
			executor.shutdownNow();
		}
	}

	@SuppressWarnings("unchecked")
	@Test
	public void testRegisterFactoryWithCacheTtl() {
		final var key = IdGenerator.generateId();
		final var factory = mock(Function.class);
		final var config = mock(UnloadableConfig.class);
		when(factory.apply(key)).thenReturn(config);

		assertThrows(NullPointerException.class, () -> IuConfig.registerFactory(LoadableConfig.class, null));
		assertDoesNotThrow(() -> IuConfig.registerFactory(UnloadableConfig.class, factory, Duration.ofMinutes(1L)));
		assertSame(config, IuConfig.load(UnloadableConfig.class, key));
		verify(factory).apply(key);

		IuConfig.seal();
		assertThrows(IllegalStateException.class,
				() -> IuConfig.registerFactory(LoadableConfig.class, ignored -> mock(LoadableConfig.class)));
	}

	@Test
	public void testSealed() {
		assertThrows(NullPointerException.class, () -> IuConfig.registerAdapter(null));
		assertThrows(NullPointerException.class, () -> IuConfig.registerSerializer(null));
		assertThrows(NullPointerException.class, () -> IuConfig.registerDeserializer(null));

		IuConfig.seal();
		assertThrows(IllegalStateException.class, () -> IuConfig.registerAdapter(mock(JsonbAdapter.class)));
		assertThrows(IllegalStateException.class, () -> IuConfig.registerSerializer(mock(JsonbSerializer.class)));
		assertThrows(IllegalStateException.class,
				() -> IuConfig.registerDeserializer(mock(JsonbDeserializer.class)));
	}

	@Test
	public void testComponents() {
		IuConfig.registerAdapter(IuJsonAdapter.typedAdapter(Custom.class, String.class, new JsonbAdapter<Custom, String>() {
			@Override
			public String adaptToJson(Custom obj) {
				return obj.value;
			}

			@Override
			public Custom adaptFromJson(String obj) {
				return new Custom(obj);
			}
		}));
		IuConfig.registerSerializer(IuJsonAdapter.<Color>typedSerializer(Color.class,
				(color, generator, context) -> generator.write(color.name().toLowerCase())));
		IuConfig.registerDeserializer(IuJsonAdapter.<Color>typedDeserializer(Color.class,
				(parser, context, type) -> Color.valueOf(parser.getString().toUpperCase())));

		final var value = IdGenerator.generateId();
		final var config = IuConfig.jsonb().fromJson("{\"custom\":\"" + value + "\"}", CustomConfig.class);
		assertEquals(value, config.getCustom().value);
		assertEquals("\"" + value + "\"", IuConfig.jsonb().toJson(config.getCustom()));

		assertSame(Color.GREEN, IuConfig.jsonb().fromJson("\"green\"", Color.class));
		assertEquals("\"red\"", IuConfig.jsonb().toJson(Color.RED));
	}

	@Test
	public void testFormats() {
		// JSON-B defaults, with snake_case property names and web crypto values
		final var config = IuConfig.jsonb().fromJson("{" //
				+ "\"color\":\"GREEN\"," //
				+ "\"when\":\"2026-09-29T12:34:56Z\"," //
				+ "\"ttl\":\"PT15M\"," //
				+ "\"alg\":\"RSA-OAEP\"," //
				+ "\"enc\":\"A128CBC-HS256\"," //
				+ "\"data\":\"AQID\"," //
				+ "\"snake_case_name\":\"foo\"" //
				+ "}", Formats.class);
		assertSame(Color.GREEN, config.getColor());
		assertEquals(Instant.parse("2026-09-29T12:34:56Z"), config.getWhen());
		assertEquals(Duration.ofMinutes(15L), config.getTtl());
		assertSame(Algorithm.RSA_OAEP, config.getAlg());
		assertSame(Encryption.AES_128_CBC_HMAC_SHA_256, config.getEnc());
		assertEquals(3, config.getData().length);
		assertEquals("foo", config.getSnakeCaseName());
	}

	@Test
	void testJsonValues() {
		final var jsonb = IuConfig.jsonb();
		final var object = IuJson.object().add("a", 1).build();
		assertEquals(object, jsonb.fromJson(jsonb.toJson(object), JsonObject.class));
		final var array = IuJson.array().add("a").build();
		assertEquals(array, jsonb.fromJson(jsonb.toJson(array), JsonArray.class));
		assertEquals(IuJson.string("a"), jsonb.fromJson("\"a\"", JsonValue.class));
	}

	@Test
	void testJsonKeyAndCertAdapters() {
		IuTestLogger.allow("edu.iu.crypt", Level.CONFIG);
		final var kid = IdGenerator.generateId();
		final var jwk = WebKey.builder(Algorithm.EDDSA).keyId(kid).ephemeral().build();
		final var privateKey = Objects.requireNonNull(jwk.getPrivateKey(), "Missing private key");
		final var privateKeyFile = IuProcess.temp(PemEncoded::print, privateKey);

		IuTestLogger.allow(IuProcess.class.getName(), Level.FINE);
		final var pemCert = IuProcess.exec( //
				"openssl", "req", "-x509", "-key", privateKeyFile.toString(), "-days", "1", //
				"-subj", "/CN=" + jwk.getKeyId().replaceAll("([+=/])", "\\\\$1"), //
				"-addext", "basicConstraints=critical,CA:true,pathlen:0", //
				"-addext", "keyUsage=keyCertSign,cRLSign" //
		);

		final var jsonb = IuConfig.jsonb();
		final var signedKey = WebKey.builder(Algorithm.EDDSA).keyId(kid).key(privateKey).pem(pemCert).build();
		assertEquals(signedKey, jsonb.fromJson(jsonb.toJson(signedKey, WebKey.class), WebKey.class));
		assertNull(jsonb.fromJson("null", WebKey.class));

		final var cert = signedKey.getCertificateChain()[0];
		assertEquals(cert,
				jsonb.fromJson(jsonb.toJson(cert, X509Certificate.class), X509Certificate.class));

		final var databaseFile = IuProcess.temp(PrintStream::print, "");
		final var newCertsDir = IuProcess.createTempDirectory();
		final var certificateFile = IuProcess.temp(PrintStream::println, pemCert);
		var caConfigContents = "[ ca ]" + System.lineSeparator() //
				+ "default_ca = a" + System.lineSeparator() //
				+ System.lineSeparator() //
				+ "[ a ]" + System.lineSeparator() //
				+ "private_key = " + privateKeyFile.toString().replace('\\', '/') + System.lineSeparator() //
				+ "certificate = " + certificateFile.toString().replace('\\', '/') + System.lineSeparator() //
				+ "database = " + databaseFile.toString().replace('\\', '/') + System.lineSeparator() //
				+ "new_certs_dir = " + newCertsDir.toString().replace('\\', '/') + System.lineSeparator() // //
				+ "copy_extensions = copyall" + System.lineSeparator() //
				+ "rand_serial = yes" + System.lineSeparator() //
				+ "policy = b" + System.lineSeparator() //
				+ System.lineSeparator() //
				+ "[ b ]" + System.lineSeparator() //
				+ "countryName = optional" + System.lineSeparator() //
				+ "stateOrProvinceName = optional" + System.lineSeparator() //
				+ "localityName = optional" + System.lineSeparator() //
				+ "organizationName = optional" + System.lineSeparator() //
				+ "organizationalUnitName = optional" + System.lineSeparator() //
				+ "commonName = supplied" + System.lineSeparator() //
				+ "emailAddress = optional" + System.lineSeparator();
		final var caConfig = IuProcess.temp(PrintStream::print, caConfigContents);

		final var crl = PemEncoded.parse(IuProcess.exec( //
				"openssl", "ca", "-gencrl", "-config", caConfig.toString(), "-crldays", "1" //
		)).next().asCRL();
		assertEquals(crl, jsonb.fromJson(jsonb.toJson(crl, X509CRL.class), X509CRL.class));

		IuProcess.deleteTempFiles();
	}

	@Test
	public void testKeyReference() {
		// a type with its own conversion, such as WebKey, may be stored by reference
		final var name = IdGenerator.generateId();
		final var key = WebKey.ephemeral(Algorithm.ES256);
		final var vault = vault(Map.of("key/" + name, key.toString()));
		IuConfig.registerInterface("key", WebKey.class, vault);

		assertEquals(key, IuConfig.jsonb().fromJson("{\"key\":\"" + name + "\"}", KeyRef.class).getKey());
		assertEquals(key, IuConfig.jsonb().fromJson("{\"key\":" + key + "}", KeyRef.class).getKey());
		assertEquals(key, IuConfig.load(WebKey.class, name));
	}

	@Test
	public void testLoadable() {
		final var key = IdGenerator.generateId();
		final var configKey = IdGenerator.generateId();
		final var refKey = IdGenerator.generateId();
		final var value = IdGenerator.generateId();
		final var vault = vault(Map.of( //
				"loadable/" + key, IuJson.object().add("config", IuJson.object().add("value", value)).build().toString(), //
				"loadable/" + configKey, IuJson.object().add("value", value).build().toString(), //
				"loadable/" + refKey, IuJson.object().add("config", configKey).build().toString()));

		assertDoesNotThrow(() -> IuConfig.registerInterface("loadable", LoadableConfig.class, vault));
		assertDoesNotThrow(() -> IuConfig.registerInterface("loadable", LoadableRef.class, vault));

		// nested object, bound inline
		assertEquals(value, IuConfig.load(LoadableRef.class, key).getConfig().getValue());

		// nested string, a reference resolved by the nested type's registration
		assertEquals(value, IuConfig.load(LoadableRef.class, refKey).getConfig().getValue());
	}

	@Test
	public void testLoadableNoVault() {
		final var key = IdGenerator.generateId();
		final var value = IdGenerator.generateId();
		final var vault = vault(Map.of("loadable/" + key,
				IuJson.object().add("config", IuJson.object().add("value", value)).build().toString()));

		assertDoesNotThrow(() -> IuConfig.registerInterface("loadable", LoadableRef.class, vault));

		assertEquals(value, IuConfig.load(LoadableRef.class, key).getConfig().getValue());
	}

	@Test
	public void testLoadWithVerifier() {
		final var key = IdGenerator.generateId();
		final var vault = vault(Map.of("verifiable/" + key, "{}"));

		assertDoesNotThrow(() -> IuConfig.registerInterface("verifiable", VerifiableConfig.class, vault));

		VerifiableConfig config = IuConfig.load(VerifiableConfig.class, key);
		assertInstanceOf(VerifiableConfig.class, config);
	}

}
