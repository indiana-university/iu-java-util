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
package edu.iu.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import java.io.StringReader;
import java.io.StringWriter;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

import jakarta.json.JsonValue;
import jakarta.json.bind.serializer.DeserializationContext;
import jakarta.json.stream.JsonGenerator;
import jakarta.json.stream.JsonParser;
import jakarta.json.stream.JsonParser.Event;

@SuppressWarnings({ "javadoc", "unchecked", "rawtypes" })
public class IuJsonPropertiesTest {

	/**
	 * Converts as IuJsonAdapter.of does, recording how each value converts.
	 */
	static Function<Type, IuJsonAdapter<?>> recording(List<String> calls) {
		return type -> {
			final IuJsonAdapter delegate = IuJsonAdapter.of(type);
			final var name = type instanceof Class ? ((Class<?>) type).getSimpleName()
					: ((Class<?>) ((java.lang.reflect.ParameterizedType) type).getRawType()).getSimpleName();
			return new IuJsonAdapter<Object>() {
				@Override
				public Object fromJson(JsonValue value) {
					calls.add("tree " + name);
					return delegate.fromJson(value);
				}

				@Override
				public JsonValue toJson(Object value) {
					calls.add("toJson " + name);
					return delegate.toJson(value);
				}

				@Override
				public Object read(JsonParser parser) {
					calls.add("stream " + name);
					return delegate.read(parser);
				}

				@Override
				public void write(Object value, JsonGenerator generator) {
					calls.add("write " + name);
					delegate.write(value, generator);
				}
			};
		};
	}

	static JsonParser parser(String json) {
		final var parser = IuJson.PROVIDER.createParser(new StringReader(json));
		parser.next();
		return parser;
	}

	static String written(IuJsonProperties properties) {
		final var writer = new StringWriter();
		try (final var generator = IuJson.PROVIDER.createGenerator(writer)) {
			properties.write(generator);
		}
		return writer.toString();
	}

	@Test
	public void testTreeSource() {
		final List<String> calls = new ArrayList<>();
		final var object = IuJson.parse("{\"a\":1,\"b\":[2]}").asJsonObject();
		final var properties = IuJsonProperties.of(object, recording(calls));

		assertEquals(1, (Integer) properties.get("a", Integer.class));
		assertEquals(1, (Integer) properties.get("a", Integer.class));
		assertEquals(List.of("tree Integer"), calls);

		// resolved as one type, read as another
		assertEquals(1L, (Long) properties.get("a", Long.class));

		assertTrue(properties.containsKey("b"));
		assertFalse(properties.containsKey("x"));
		assertNull(properties.get("x", String.class));
		assertEquals(0, (Integer) properties.get("y", int.class));
		assertEquals(object.keySet(), properties.names());
		assertSame(object, properties.toJsonObject());
		assertEquals(object.toString(), written(properties));
		assertEquals(object.toString(), properties.toString());
		properties.detach();
	}

	@Test
	public void testPullForward() {
		final List<String> calls = new ArrayList<>();
		final var json = "{\"a\":1,\"b\":{\"c\":2},\"d\":[3],\"e\":\"x\"}";
		final var parser = parser("[" + json + ",5]");
		parser.next();
		final var properties = IuJsonProperties.read(parser, recording(calls));

		// b streams; a, passed on the way, is captured raw without converting
		assertEquals(Map.of("c", 2), properties.get("b", new TypeRef<Map<String, Integer>>() {
		}.type()));
		assertEquals(List.of("stream Map"), calls);
		assertEquals(1, (Integer) properties.get("a", Integer.class));
		assertEquals(List.of("stream Map", "tree Integer"), calls);

		assertTrue(properties.containsKey("d"));
		assertEquals(List.of(3), properties.get("d", new TypeRef<List<Integer>>() {
		}.type()));
		assertFalse(properties.containsKey("x"));
		assertEquals(Event.END_OBJECT, parser.currentEvent());
		assertEquals(Event.VALUE_NUMBER, parser.next());

		assertEquals(List.of("a", "b", "d", "e"), new ArrayList<>(properties.names()));
		assertEquals(IuJson.parse(json), properties.toJsonObject());
		assertSame(properties.toJsonObject(), properties.toJsonObject());
		assertEquals(json, written(properties));
		assertEquals("x", properties.get("e", String.class));
	}

	@Test
	public void testDetachCapturesTheRest() {
		final List<String> calls = new ArrayList<>();
		final var parser = parser("[{\"a\":1,\"b\":{\"c\":[2]}},5]");
		parser.next();
		final var properties = IuJsonProperties.read(parser, recording(calls));
		assertEquals(1, (Integer) properties.get("a", Integer.class));
		properties.detach();
		properties.detach();
		assertEquals(Event.END_OBJECT, parser.currentEvent());
		assertEquals(Event.VALUE_NUMBER, parser.next());

		assertEquals(Map.of("c", List.of(new java.math.BigDecimal("2"))), properties.get("b", Map.class));
		assertEquals(List.of("stream Integer", "tree Map"), calls);
	}

	@Test
	public void testReadRequiresAnObject() {
		assertEquals("expected an object, found START_ARRAY",
				assertThrows(IllegalArgumentException.class,
						() -> IuJsonProperties.read(parser("[]"), IuJsonAdapter::of)).getMessage());
	}

	@Test
	public void testRequireOnly() {
		final var properties = IuJsonProperties.of(IuJson.parse("{\"a\":1,\"b\":2,\"c\":3}").asJsonObject(),
				IuJsonAdapter::of);
		assertSame(properties, properties.requireOnly(Set.of("a", "b", "c", "d")));
		assertEquals("unexpected properties [b, c]",
				assertThrows(IllegalArgumentException.class, () -> properties.requireOnly(Set.of("a")))
						.getMessage());
	}

	public enum Level {
		LOW {
			@Override
			public String toString() {
				return "low";
			}
		},
		HIGH
	}

	public interface Named {
		String getName();
	}

	@Test
	public void testBuilder() {
		final var named = IuJson.wrap(IuJson.object().add("name", "n").build(), Named.class);
		final Function<Type, IuJsonAdapter<?>> adapt = t -> IuJsonAdapter.adapt(t, IuJsonPropertyNameFormat.IDENTITY);
		final var properties = IuJsonProperties.builder(adapt) //
				.put("count", 1) //
				.put("level", Level.LOW) //
				.put("named", named) //
				.put("none", null) //
				.put("when", 2L, Long.class) //
				.putJson("raw", IuJson.parse("[true]")) //
				.put("count", 3) //
				.build();

		assertEquals(List.of("count", "level", "named", "none", "when", "raw"), new ArrayList<>(properties.names()));
		// the enum constant by name(), though its toString() is low
		final var json = "{\"count\":3,\"level\":\"LOW\",\"named\":{\"name\":\"n\"},\"none\":null,\"when\":2,"
				+ "\"raw\":[true]}";
		assertEquals(json, properties.toString());
		assertEquals(json, written(properties));
		assertEquals(3, (Integer) properties.get("count", Integer.class));
		assertEquals(List.of(true), properties.get("raw", new TypeRef<List<Boolean>>() {
		}.type()));

		final var changed = properties.with("count", 4);
		assertEquals(4, (Integer) changed.get("count", Integer.class));
		assertEquals(3, (Integer) properties.get("count", Integer.class));
		assertEquals(json.replace("3", "4"), changed.toString());

		assertThrows(NullPointerException.class, () -> IuJsonProperties.builder(null));
		assertThrows(NullPointerException.class,
				() -> IuJsonProperties.builder(IuJsonAdapter::of).put("x", 1, null));
		assertThrows(NullPointerException.class,
				() -> IuJsonProperties.builder(IuJsonAdapter::of).putJson("x", null));
		assertThrows(NullPointerException.class, () -> IuJsonProperties.builder(IuJsonAdapter::of).put(null, 1));
	}

	@Test
	public void testPutAllKeepsJsonAsRead() {
		final var source = IuJsonProperties.of(IuJson.parse("{\"n\":1.50}").asJsonObject(), IuJsonAdapter::of);
		assertEquals(new java.math.BigDecimal("1.50"), source.get("n", java.math.BigDecimal.class));
		final var copy = IuJsonProperties.builder(IuJsonAdapter::of).putAll(source).put("m", 2).build();
		assertEquals("{\"n\":1.50,\"m\":2}", copy.toString());
		assertEquals("{\"n\":1.50,\"m\":2,\"o\":3}",
				IuJsonProperties.builder(IuJsonAdapter::of).putAll(copy).put("o", 3).build().toString());
	}

	@Test
	public void testProxies() {
		final var built = IuJsonProperties.builder(IuJsonAdapter::of).put("name", "n").build();
		final var fromValues = IuJson.wrap(built, Named.class, IuJsonPropertyNameFormat.IDENTITY);
		final var fromJson = IuJson.wrap(IuJson.object().add("name", "n").build(), Named.class);
		assertEquals("n", fromValues.getName());
		assertEquals(IuJson.unwrap(fromJson), IuJson.unwrap(fromValues));
		assertEquals(fromJson, fromValues);
		assertEquals(fromJson.hashCode(), fromValues.hashCode());
		assertSame(built, iu.client.JsonProxy.properties(fromValues));
	}

	@Test
	public void testConcurrentReads() throws Exception {
		final var properties = IuJsonProperties.of(IuJson.parse("{\"a\":[1,2,3]}").asJsonObject(),
				IuJsonAdapter::of);
		final var type = new TypeRef<List<Integer>>() {
		}.type();
		final var reads = IntStream.range(0, 8)
				.mapToObj(i -> CompletableFuture.supplyAsync(() -> properties.get("a", type)))
				.collect(Collectors.toList());
		final var first = reads.get(0).get();
		for (final var read : reads)
			assertSame(first, read.get());
		assertEquals(List.of(1, 2, 3), first);
		assertEquals(Collections.emptyList(), List.of());
	}

	/**
	 * Runs a read on another thread while this thread holds the index's lock,
	 * resolves it here, then lets the other thread find it resolved.
	 */
	static <T> T raced(IuJsonProperties properties, java.util.function.Supplier<T> read) throws Exception {
		final CompletableFuture<T> other;
		final T here;
		synchronized (properties) {
			final var started = new java.util.concurrent.CountDownLatch(1);
			final var thread = new Thread[1];
			other = CompletableFuture.supplyAsync(() -> {
				thread[0] = Thread.currentThread();
				started.countDown();
				return read.get();
			});
			started.await();
			while (thread[0].getState() != Thread.State.BLOCKED)
				Thread.onSpinWait();
			here = read.get();
		}
		assertSame(here, other.get());
		return here;
	}

	@Test
	public void testResolvedWhileWaiting() throws Exception {
		final var parsed = IuJsonProperties.read(parser("{\"a\":[1]}"), IuJsonAdapter::of);
		assertEquals(List.of(new java.math.BigDecimal("1")), raced(parsed, () -> parsed.get("a", List.class)));

		final var built = IuJsonProperties.builder(IuJsonAdapter::of).put("a", 1).build();
		assertEquals("{\"a\":1}", raced(built, built::toJsonObject).toString());
	}

	@Test
	public void testMissingFromAParser() {
		final var parser = parser("{\"a\":1}");
		final var properties = IuJsonProperties.read(parser, IuJsonAdapter::of);
		assertNull(properties.get("b", String.class));
		assertEquals(Event.END_OBJECT, parser.currentEvent());
	}

	@Test
	public void testOtherProxiesConvertAsTheirClass() {
		final Named named = (Named) java.lang.reflect.Proxy.newProxyInstance(Named.class.getClassLoader(),
				new Class<?>[] { Named.class }, (proxy, method, args) -> "n");
		final List<Type> types = new ArrayList<>();
		final var properties = IuJsonProperties.builder(type -> {
			types.add(type);
			return IuJsonAdapter.<Object>from(v -> v, v -> IuJson.string("proxy"));
		}).put("named", named).build();
		assertEquals("{\"named\":\"proxy\"}", properties.toString());
		assertEquals(List.of(named.getClass()), types);
	}

	@Test
	public void testOnlyTheIuProviderSuppliesConversions() {
		final var context = mock(DeserializationContext.class);
		assertThrows(IllegalArgumentException.class, () -> IuJsonProperties.deserialize(parser("{}"), context));
	}

	static abstract class TypeRef<T> {
		Type type() {
			return ((java.lang.reflect.ParameterizedType) getClass().getGenericSuperclass())
					.getActualTypeArguments()[0];
		}
	}

	@Test
	public void testAbsentConvertsNothing() {
		// conversions that fail if looked up at all
		final Function<Type, IuJsonAdapter<?>> none = t -> {
			throw new AssertionError(t.getTypeName());
		};
		final var indexed = IuJsonProperties.of(IuJson.object().build(), none);
		assertNull(indexed.get("cert", java.security.cert.X509Certificate.class));
		assertEquals(0, (int) indexed.get("n", int.class));
		assertFalse((boolean) indexed.get("flag", boolean.class));
		assertEquals(java.util.Optional.empty(), indexed.get("o", java.util.Optional.class));
		assertEquals(java.util.OptionalInt.empty(), indexed.get("i", java.util.OptionalInt.class));
		assertEquals(java.util.OptionalLong.empty(), indexed.get("l", java.util.OptionalLong.class));
		assertEquals(java.util.OptionalDouble.empty(), indexed.get("d", java.util.OptionalDouble.class));

		try (final var parser = IuJson.PROVIDER.createParser(new StringReader("{\"a\":1}"))) {
			parser.next();
			final var read = IuJsonProperties.read(parser, none);
			assertNull(read.get("cert", java.security.cert.X509Certificate.class));
			assertEquals(Set.of("a"), read.names());
		}
	}

	@Test
	public void testBuilderState() {
		final var builder = IuJsonProperties.builder(IuJsonAdapter::of);
		assertTrue(builder.isEmpty());
		assertNull(builder.get("a"));

		builder.put("a", 1);
		assertFalse(builder.isEmpty());
		assertEquals(1, builder.get("a"));

		final var raw = IuJsonProperties.builder(IuJsonAdapter::of).putJson("b", IuJson.string("x"));
		assertFalse(raw.isEmpty());
		assertEquals(IuJson.string("x"), raw.get("b"));

		// a copy changes independently
		final var copy = builder.copy().put("c", 2);
		assertNull(builder.get("c"));
		assertEquals(1, copy.get("a"));
		assertEquals("{\"a\":1}", builder.build().toString());
		assertEquals("{\"a\":1,\"c\":2}", copy.build().toString());
	}

}
