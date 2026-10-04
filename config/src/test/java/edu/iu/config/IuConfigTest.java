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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.lang.reflect.Type;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import edu.iu.IdGenerator;
import edu.iu.IuIterable;
import edu.iu.client.IuJson;
import edu.iu.client.IuJsonAdapter;
import edu.iu.client.IuVault;
import edu.iu.client.IuVaultKeyedValue;
import edu.iu.crypt.Init;
import edu.iu.crypt.WebKey;
import edu.iu.crypt.WebKey.Algorithm;
import jakarta.json.bind.JsonbConfig;
import jakarta.json.bind.JsonbException;
import jakarta.json.bind.config.PropertyNamingStrategy;

@SuppressWarnings("javadoc")
public class IuConfigTest {
	
	static {
		Init.init();
	}

	public interface LoadableConfig {
		String getValue();
	}

	public interface LoadableRef {
		LoadableConfig getConfig();
	}

	public interface UnloadableConfig {
	}

	public interface IterableConfig {
		Iterable<String> getValues();

		Iterable<LoadableConfig> getConfigs();

		List<String> getList();

		Set<String> getSet();

		Collection<String> getCollection();
	}

	public static final class Token {
		final String value;

		Token(String value) {
			this.value = value;
		}
	}

	public interface TokenConfig {
		Token getToken();
	}

	public interface NamedConfig {
		String getSomeValue();
	}

	public static final class Pooled {
	}

	public interface PooledRef {
		Pooled getPooled();
	}

	private static Map<String, Object> jsonbProperties;

	private static JsonbConfig jsonbConfig() throws ReflectiveOperationException {
		final var field = IuConfig.class.getDeclaredField("jsonbConfig");
		field.setAccessible(true);
		return (JsonbConfig) field.get(null);
	}

	@BeforeAll
	public static void snapshot() throws Exception {
		// registration and configureJsonb change the JSON-B configuration; each
		// test starts from the configuration as initialized
		jsonbProperties = new HashMap<>(jsonbConfig().getAsMap());
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

		f = IuConfig.class.getDeclaredField("CONFIG");
		f.setAccessible(true);
		((Map<?, ?>) f.get(null)).clear();

		final var jsonbConfig = jsonbConfig();
		for (final var name : Set.copyOf(jsonbConfig.getAsMap().keySet()))
			if (!jsonbProperties.containsKey(name))
				jsonbConfig.setProperty(name, null);
		jsonbProperties.forEach(jsonbConfig::setProperty);
	}

	@SuppressWarnings("unchecked")
	private static IuVault vault(Map<String, String> values) {
		final var vault = mock(IuVault.class);
		values.forEach((key, value) -> {
			final var vkv = mock(IuVaultKeyedValue.class);
			when(vkv.getValue()).thenReturn(value);
			when(vault.get(key)).thenReturn(vkv);
		});
		return vault;
	}

	private static Type iterableType(String property) throws ReflectiveOperationException {
		return IterableConfig.class.getMethod(property).getGenericReturnType();
	}

	@Test
	public void testVault() {
		final var key = IdGenerator.generateId();
		final var invalidKey = IdGenerator.generateId();
		assertThrows(IllegalStateException.class, () -> IuConfig.load(LoadableConfig.class, key));

		final var cacheTtl = Duration.ofSeconds(1L);
		final var vault = vault(Map.of("loadable/" + key, "{}"));
		when(vault.get("loadable/" + invalidKey)).thenThrow(IllegalArgumentException.class);
		assertDoesNotThrow(() -> IuConfig.registerInterface("loadable", LoadableConfig.class, cacheTtl, vault));
		assertThrows(IllegalArgumentException.class,
				() -> IuConfig.registerInterface("loadable", LoadableConfig.class, vault));
		assertThrows(IllegalArgumentException.class,
				() -> IuConfig.registerInterface("Invalid", UnloadableConfig.class, vault));

		assertThrows(IllegalStateException.class, () -> IuConfig.load(LoadableConfig.class, key));
		IuConfig.seal();
		final var config = IuConfig.load(LoadableConfig.class, key);
		assertInstanceOf(LoadableConfig.class, config);
		assertSame(config, IuConfig.load(LoadableConfig.class, key));
		verify(vault).get("loadable/" + key);
		assertThrows(IllegalArgumentException.class, () -> IuConfig.load(LoadableConfig.class, invalidKey));

		// sealing prevents later registration
		assertThrows(IllegalStateException.class,
				() -> IuConfig.registerInterface("unloadable", UnloadableConfig.class, vault));

		assertDoesNotThrow(() -> Thread.sleep(1000L)); // expires cache
		assertInstanceOf(LoadableConfig.class, IuConfig.load(LoadableConfig.class, key));
		verify(vault, times(2)).get("loadable/" + key); // returned to vault after cache expired
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
		IuConfig.seal();
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
		IuConfig.seal();

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
	public void testRegisterFactoryWithCacheTtl() throws ReflectiveOperationException {
		final var key = IdGenerator.generateId();
		final var factory = mock(Function.class);
		final var config = mock(UnloadableConfig.class);
		when(factory.apply(key)).thenReturn(config);

		final var deserializers = ((Object[]) jsonbConfig().getProperty(JsonbConfig.DESERIALIZERS).get()).length;
		assertThrows(NullPointerException.class, () -> IuConfig.registerFactory(LoadableConfig.class, null));
		// a failed registration leaves no components behind
		assertEquals(deserializers, ((Object[]) jsonbConfig().getProperty(JsonbConfig.DESERIALIZERS).get()).length);
		assertDoesNotThrow(() -> IuConfig.registerFactory(UnloadableConfig.class, factory, Duration.ofMinutes(1L)));
		IuConfig.seal();
		assertSame(config, IuConfig.load(UnloadableConfig.class, key));
		verify(factory).apply(key);

		assertThrows(IllegalStateException.class,
				() -> IuConfig.registerFactory(LoadableConfig.class, ignored -> mock(LoadableConfig.class)));
	}

	@Test
	public void testSealed() {
		IuConfig.seal();
		assertThrows(IllegalStateException.class,
				() -> IuConfig.registerFactory(LoadableConfig.class, ignored -> mock(LoadableConfig.class)));
		assertThrows(IllegalStateException.class, IuConfig::seal);
	}

	@Test
	public void testKeyReference() {
		// a type with its own conversion, such as WebKey, may be stored by reference
		final var name = IdGenerator.generateId();
		final var key = WebKey.ephemeral(Algorithm.ES256);
		final var vault = vault(Map.of("key/" + name, key.toString()));
		IuConfig.registerInterface("key", WebKey.class, vault);

		IuConfig.seal();
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
		IuConfig.seal();

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
		IuConfig.seal();

		assertEquals(value, IuConfig.load(LoadableRef.class, key).getConfig().getValue());
	}

	@Test
	public void testNullIterableBinding() throws ReflectiveOperationException {
		IuConfig.seal();

		assertNull(IuConfig.jsonb().fromJson("null", iterableType("getValues")));
		assertNull(IuConfig.jsonb().fromJson("null", iterableType("getConfigs")));
	}

	@Test
	@SuppressWarnings("unchecked")
	public void testSingletonIterableBinding() throws ReflectiveOperationException {
		final var value = IdGenerator.generateId();
		IuConfig.seal();

		final var values = (Iterable<String>) IuConfig.jsonb().fromJson("\"" + value + "\"", iterableType("getValues"));
		final var configs = (Iterable<LoadableConfig>) IuConfig.jsonb()
				.fromJson(IuJson.object().add("value", value).build().toString(), iterableType("getConfigs"));
		assertEquals(List.of(value), IuIterable.stream(values).toList());
		assertEquals(List.of(value), IuIterable.stream(configs).map(LoadableConfig::getValue).toList());
	}

	@Test
	@SuppressWarnings("unchecked")
	public void testArrayIterableBinding() throws ReflectiveOperationException {
		final var first = IdGenerator.generateId();
		final var second = IdGenerator.generateId();
		IuConfig.seal();

		final var values = (Iterable<String>) IuConfig.jsonb()
				.fromJson(IuJson.array().add(first).add(second).build().toString(), iterableType("getValues"));
		final var configs = (Iterable<LoadableConfig>) IuConfig.jsonb().fromJson(IuJson.array()
				.add(IuJson.object().add("value", first)).add(IuJson.object().add("value", second)).build().toString(),
				iterableType("getConfigs"));
		assertEquals(List.of(first, second), IuIterable.stream(values).toList());
		assertEquals(List.of(first, second),
				IuIterable.stream(configs).map(LoadableConfig::getValue).toList());
	}

	@Test
	public void testNotSealed() {
		final var key = IdGenerator.generateId();
		IuConfig.registerFactory(LoadableConfig.class, ignored -> mock(LoadableConfig.class));
		assertEquals("not sealed",
				assertThrows(IllegalStateException.class, () -> IuConfig.load(LoadableConfig.class, key)).getMessage());
	}

	@Test
	public void testNotConfigured() {
		final var key = IdGenerator.generateId();
		IuConfig.seal();
		assertEquals("not configured",
				assertThrows(NullPointerException.class, () -> IuConfig.load(UnloadableConfig.class, key))
						.getMessage());
	}

	@Test
	public void testNullPrefix() {
		final var vault = vault(Map.of());
		assertThrows(NullPointerException.class, () -> IuConfig.registerInterface(null, LoadableConfig.class, vault));
	}

	@Test
	public void testVaultFallback() {
		final var key = IdGenerator.generateId();
		final var failingKey = IdGenerator.generateId();
		final var first = vault(Map.of());
		final var second = vault(Map.of("loadable/" + key, "{}"));
		final var firstError = new IllegalArgumentException();
		final var secondError = new IllegalStateException();
		when(first.get("loadable/" + key)).thenThrow(new IllegalArgumentException());
		when(first.get("loadable/" + failingKey)).thenThrow(firstError);
		when(second.get("loadable/" + failingKey)).thenThrow(secondError);
		IuConfig.registerInterface("loadable", LoadableConfig.class, first, second);
		IuConfig.seal();

		// the first vault that loads the key supplies it
		assertInstanceOf(LoadableConfig.class, IuConfig.load(LoadableConfig.class, key));
		verify(first).get("loadable/" + key);
		verify(second).get("loadable/" + key);

		// when none does, the first failure is thrown, the rest suppressed
		final var error = assertThrows(IllegalArgumentException.class,
				() -> IuConfig.load(LoadableConfig.class, failingKey));
		assertSame(firstError, error);
		assertArrayEquals(new Throwable[] { secondError }, error.getSuppressed());
	}

	@Test
	public void testReferenceRequiresRegistration() {
		final var key = IdGenerator.generateId();
		final var vault = vault(Map.of("loadable/" + key,
				IuJson.object().add("config", IdGenerator.generateId()).build().toString()));
		IuConfig.registerInterface("loadable", LoadableRef.class, vault);
		IuConfig.seal();

		// a string isn't a reference to an unregistered type: it fails to bind as
		// one, rather than attempting to load it
		final var error = assertThrows(RuntimeException.class,
				() -> IuConfig.load(LoadableRef.class, key).getConfig());
		for (Throwable cause = error; cause != null; cause = cause.getCause())
			assertFalse(cause instanceof NullPointerException && "not configured".equals(cause.getMessage()),
					() -> "attempted to load an unregistered reference");
	}

	@Test
	@SuppressWarnings("unchecked")
	public void testSingletonCollectionBinding() throws ReflectiveOperationException {
		final var value = IdGenerator.generateId();
		final var json = "\"" + value + "\"";
		IuConfig.seal();

		final var list = (List<String>) IuConfig.jsonb().fromJson(json, iterableType("getList"));
		assertInstanceOf(ArrayList.class, list);
		assertEquals(List.of(value), list);

		final var set = (Set<String>) IuConfig.jsonb().fromJson(json, iterableType("getSet"));
		assertInstanceOf(LinkedHashSet.class, set);
		assertEquals(Set.of(value), set);

		final var collection = (Collection<String>) IuConfig.jsonb().fromJson(json, iterableType("getCollection"));
		assertInstanceOf(ArrayDeque.class, collection);
		assertEquals(List.of(value), List.copyOf(collection));
	}

	@Test
	public void testJsonbNotSealed() {
		assertEquals("not sealed", assertThrows(IllegalStateException.class, IuConfig::jsonb).getMessage());
		IuConfig.seal();
		assertSame(IuConfig.jsonb(), IuConfig.jsonb());
	}

	@Test
	public void testConfigureJsonb() {
		final var key = IdGenerator.generateId();
		final var value = IdGenerator.generateId();
		final var vault = vault(Map.of("token/" + key, IuJson.object().add("token", value).build().toString()));

		assertThrows(NullPointerException.class, () -> IuConfig.configureJsonb(null));
		IuConfig.configureJsonb(c -> c.withDeserializers(
				IuJsonAdapter.<Token>typedDeserializer(Token.class, (parser, ctx, type) -> new Token(parser.getString()))));
		IuConfig.registerInterface("token", TokenConfig.class, vault);
		IuConfig.seal();

		assertEquals(value, IuConfig.load(TokenConfig.class, key).getToken().value);
		assertEquals("already sealed",
				assertThrows(IllegalStateException.class, () -> IuConfig.configureJsonb(c -> {
				})).getMessage());
	}

	@Test
	public void testConfigureJsonbReplaces() {
		final var value = IdGenerator.generateId();

		// the application may replace the defaults, here snake_case names
		IuConfig.configureJsonb(c -> c.withPropertyNamingStrategy(PropertyNamingStrategy.IDENTITY));
		IuConfig.seal();

		assertEquals(value, IuConfig.jsonb()
				.fromJson(IuJson.object().add("someValue", value).build().toString(), NamedConfig.class).getSomeValue());
	}

	@Test
	public void testConfigureJsonbAfterSealIgnored() {
		final var value = IdGenerator.generateId();
		final var kept = new AtomicReference<JsonbConfig>();
		IuConfig.configureJsonb(kept::set);
		IuConfig.seal();

		// a reference kept past sealing changes nothing
		kept.get().withPropertyNamingStrategy(PropertyNamingStrategy.IDENTITY);
		assertEquals(value, IuConfig.jsonb()
				.fromJson(IuJson.object().add("some_value", value).build().toString(), NamedConfig.class).getSomeValue());
	}

	private static void assertRejectedJson(Throwable error) {
		for (Throwable cause = error; cause != null; cause = cause.getCause())
			if (cause instanceof JsonbException && cause.getMessage().contains(Pooled.class.getName())
					&& cause.getMessage().contains("key string"))
				return;
		throw new AssertionError("expected a factory type rejection", error);
	}

	@Test
	@SuppressWarnings("unchecked")
	public void testFactoryReference() {
		final var key = IdGenerator.generateId();
		final var refKey = IdGenerator.generateId();
		final var nullKey = IdGenerator.generateId();
		final var pooled = new Pooled();
		final Function<String, Pooled> factory = mock(Function.class);
		when(factory.apply(key)).thenReturn(pooled);
		final var vault = vault(Map.of( //
				"pooled/" + refKey, IuJson.object().add("pooled", key).build().toString(), //
				"pooled/" + nullKey, "{\"pooled\":null}"));

		IuConfig.registerFactory(Pooled.class, factory);
		IuConfig.registerInterface("pooled", PooledRef.class, vault);
		IuConfig.seal();

		assertSame(pooled, IuConfig.load(PooledRef.class, refKey).getPooled());
		assertNull(IuConfig.load(PooledRef.class, nullKey).getPooled());
		verify(factory).apply(key);
	}

	@Test
	@SuppressWarnings("unchecked")
	public void testFactoryRejectsJson() {
		final var objectKey = IdGenerator.generateId();
		final var numberKey = IdGenerator.generateId();
		final Function<String, Pooled> factory = mock(Function.class);
		final var vault = vault(Map.of( //
				"pooled/" + objectKey, "{\"pooled\":{}}", //
				"pooled/" + numberKey, "{\"pooled\":1}"));

		IuConfig.registerFactory(Pooled.class, factory);
		IuConfig.registerInterface("pooled", PooledRef.class, vault);
		IuConfig.seal();

		assertRejectedJson(
				assertThrows(RuntimeException.class, () -> IuConfig.load(PooledRef.class, objectKey).getPooled()));
		assertRejectedJson(
				assertThrows(RuntimeException.class, () -> IuConfig.load(PooledRef.class, numberKey).getPooled()));
		verify(factory, never()).apply(any());
	}

	@Test
	public void testFactoryNotSerialized() {
		IuConfig.registerFactory(Pooled.class, key -> new Pooled());
		IuConfig.seal();

		final var error = assertThrows(JsonbException.class, () -> IuConfig.jsonb().toJson(new Pooled()));
		for (Throwable cause = error; cause != null; cause = cause.getCause())
			if (cause.getMessage() != null && cause.getMessage().contains("doesn't convert to JSON"))
				return;
		throw new AssertionError("expected a factory type serialization rejection", error);
	}

}
