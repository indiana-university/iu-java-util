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
package edu.iu.client;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.time.Period;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.Deque;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.NavigableSet;
import java.util.Optional;
import java.util.Properties;
import java.util.Queue;
import java.util.Set;
import java.util.SimpleTimeZone;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TimeZone;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import edu.iu.IdGenerator;
import edu.iu.IuIterable;
import edu.iu.IuText;
import jakarta.json.JsonArray;
import jakarta.json.JsonNumber;
import jakarta.json.JsonObject;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;

@SuppressWarnings("javadoc")
public class IuJsonAdapterTest {

	@Test
	public void testDoesntSupportEverything() {
		assertThrows(UnsupportedOperationException.class, () -> IuJsonAdapter.of(getClass()));
		assertThrows(UnsupportedOperationException.class, () -> IuJsonAdapter.of(ConcurrentHashMap.class));
		assertThrows(UnsupportedOperationException.class, () -> IuJsonAdapter.of(ConcurrentLinkedQueue.class));
	}

	@SuppressWarnings("unchecked")
	@Test
	public void testParse() {
		final var response = mock(HttpResponse.class);
		when(response.body()).thenReturn(new ByteArrayInputStream("null".getBytes()));
		assertEquals(JsonValue.NULL, IuJson.parse(response));
		assertEquals(JsonValue.NULL, IuJson.parse("null"));
	}

	@Test
	public void testToJava() {
		final var id = IdGenerator.generateId();
		assertEquals(id, IuJsonAdapter.basic().fromJson(IuJson.string(id)));
		assertEquals(34, IuJsonAdapter.<Number>basic().fromJson(IuJson.number(new BigDecimal("34"))).intValue());
		assertTrue(IuJsonAdapter.<Boolean>basic().fromJson(JsonValue.TRUE));
		assertFalse(IuJsonAdapter.<Boolean>basic().fromJson(JsonValue.FALSE));
		assertNull(IuJsonAdapter.basic().fromJson(JsonValue.NULL));
		assertNull(IuJsonAdapter.basic().fromJson(null));

		assertEquals(id,
				IuJsonAdapter.<Map<String, ?>>basic().fromJson(IuJson.object().add("id", id).build()).get("id"));
		assertEquals(id, IuJsonAdapter.<List<?>>basic().fromJson(IuJson.array().add(id).build()).get(0));
	}

	@Test
	public void testAsText() {
		final var id = IdGenerator.generateId();
		assertEquals(id, IuJsonAdapter.of(String.class).fromJson(IuJson.string(id)));
		assertEquals("34", IuJsonAdapter.of(String.class).fromJson(IuJson.number(new BigDecimal("34"))));
		assertEquals("true", IuJsonAdapter.of(String.class).fromJson(JsonValue.TRUE));
		assertEquals("false", IuJsonAdapter.of(String.class).fromJson(JsonValue.FALSE));
		assertNull(IuJsonAdapter.of(String.class).fromJson(JsonValue.NULL));
		assertThrows(IllegalArgumentException.class, () -> IuJsonAdapter.of(String.class).fromJson(null));
		assertEquals("a,1,true",
				IuJsonAdapter.of(String.class).fromJson(IuJson.array().add("a").add(1).add(true).build()));
	}

	@Test
	public void testToJson() {
		final var id = IdGenerator.generateId();
		assertEquals(IuJson.string(id), IuJsonAdapter.basic().toJson(id));
		assertEquals(IuJson.number(new BigDecimal("34")), IuJsonAdapter.basic().toJson(34));
		assertEquals(JsonValue.TRUE, IuJsonAdapter.basic().toJson(true));
		assertEquals(JsonValue.FALSE, IuJsonAdapter.basic().toJson(false));
		assertEquals(JsonValue.NULL, IuJsonAdapter.basic().toJson(null));
		assertThrows(IllegalArgumentException.class, () -> IuJsonAdapter.basic().toJson(this));
		assertSame(JsonValue.NULL, IuJsonAdapter.basic().toJson(JsonValue.NULL));
		assertInstanceOf(JsonArray.class, IuJsonAdapter.basic().toJson(IuJson.PROVIDER.createArrayBuilder()));
		assertInstanceOf(JsonObject.class, IuJsonAdapter.basic().toJson(IuJson.PROVIDER.createObjectBuilder()));
		assertEquals(id, IuJsonAdapter.basic().toJson(Map.of("id", id)).asJsonObject().getString("id"));
		assertEquals(id, IuJsonAdapter.basic().toJson(List.of(id)).asJsonArray().getString(0));
		assertEquals(id, IuJsonAdapter.basic().toJson(Stream.of(id)).asJsonArray().getString(0));
		assertEquals(id, IuJsonAdapter.basic().toJson(new String[] { id }).asJsonArray().getString(0));
		assertEquals(id, IuJsonAdapter.basic().toJson(IuIterable.iter(id)).asJsonArray().getString(0));
		assertEquals(id, IuJsonAdapter.basic().toJson(IuIterable.iter(id).iterator()).asJsonArray().getString(0));
		final var q = new ArrayDeque<>();
		q.add(id);
		assertEquals(id, IuJsonAdapter.basic().toJson(q).asJsonArray().getString(0));
		assertEquals(id, IuJsonAdapter.basic().toJson(new Enumeration<>() {
			Iterator<?> i = q.iterator();

			@Override
			public boolean hasMoreElements() {
				return i.hasNext();
			}

			@Override
			public Object nextElement() {
				return i.next();
			}
		}).asJsonArray().getString(0));
	}

	@Test
	public void testBoolean() {
		final var adapter = IuJsonAdapter.of(Boolean.class);
		assertTrue(adapter.fromJson(JsonValue.TRUE));
		assertFalse(adapter.fromJson(JsonValue.FALSE));
		assertTrue(adapter.fromJson(IuJson.string("true")));
		assertFalse(adapter.fromJson(IuJson.string("false")));
		assertTrue(adapter.fromJson(IuJson.number(1)));
		assertFalse(adapter.fromJson(IuJson.number(0)));
		assertTrue(adapter.fromJson(IuJson.array().build()));
		assertTrue(adapter.fromJson(IuJson.object().build()));
		assertNull(adapter.fromJson(JsonValue.NULL));
		assertNull(adapter.fromJson(null));
		assertEquals(JsonValue.TRUE, adapter.toJson(true));
		assertEquals(JsonValue.FALSE, adapter.toJson(false));
		assertEquals(JsonValue.NULL, adapter.toJson(null));

		final var primitive = IuJsonAdapter.of(boolean.class);
		assertTrue(primitive.fromJson(JsonValue.TRUE));
		assertFalse(primitive.fromJson(JsonValue.FALSE));
		assertFalse(primitive.fromJson(JsonValue.NULL));
		assertEquals(JsonValue.TRUE, primitive.toJson(true));
		assertEquals(JsonValue.FALSE, primitive.toJson(false));
	}

	@Test
	public void testNumber() {
		final var adapter = IuJsonAdapter.of(Number.class);
		final var n = new BigDecimal(Long.toString(ThreadLocalRandom.current().nextLong())
				+ Long.toString(Math.abs(ThreadLocalRandom.current().nextLong())) + '.'
				+ Long.toString(Math.abs(ThreadLocalRandom.current().nextLong())));

		assertEquals(n, adapter.fromJson(IuJson.number(n)));
		assertNull(adapter.fromJson(JsonValue.NULL));
		assertEquals(n, ((JsonNumber) adapter.toJson(n)).bigDecimalValue());
		assertEquals(JsonValue.NULL, adapter.toJson(null));
	}

	@Test
	public void testBigDecimal() {
		assertAdaptNumber(BigDecimal.class, null,
				() -> new BigDecimal(Long.toString(ThreadLocalRandom.current().nextLong())
						+ Long.toString(Math.abs(ThreadLocalRandom.current().nextLong())) + '.'
						+ Long.toString(Math.abs(ThreadLocalRandom.current().nextLong()))),
				a -> a.bigDecimalValue(), BigDecimal::toString, BigDecimal.ZERO);
	}

	@Test
	public void testBigInteger() {
		assertAdaptNumber(BigInteger.class, null, () -> {
			final var data = new byte[Math
					.abs(ThreadLocalRandom.current().nextInt(Byte.MAX_VALUE + 1, Short.MAX_VALUE))];
			ThreadLocalRandom.current().nextBytes(data);
			return new BigInteger(1, data);
		}, a -> a.bigIntegerValue(), BigInteger::toString, BigInteger.ZERO);
	}

	@Test
	public void testByte() {
		assertAdaptNumber(Byte.class, byte.class, () -> (byte) ThreadLocalRandom.current().nextInt(),
				a -> (byte) a.intValueExact(), a -> Byte.toString(a), (byte) 0);
	}

	@Test
	public void testShort() {
		assertAdaptNumber(Short.class, short.class, () -> (short) ThreadLocalRandom.current().nextInt(),
				a -> (short) a.intValueExact(), a -> Short.toString(a), (short) 0);
	}

	@Test
	public void testInt() {
		assertAdaptNumber(Integer.class, int.class, () -> ThreadLocalRandom.current().nextInt(),
				JsonNumber::intValueExact, a -> Integer.toString(a), 0);
	}

	@Test
	public void testLong() {
		assertAdaptNumber(Long.class, long.class, () -> ThreadLocalRandom.current().nextLong(),
				JsonNumber::longValueExact, a -> Long.toString(a), 0L);
	}

	@Test
	public void testFloat() {
		assertAdaptNumber(Float.class, float.class, () -> (float) ThreadLocalRandom.current().nextDouble(),
				a -> (float) a.doubleValue(), a -> Float.toString(a), 0.0f);
	}

	@Test
	public void testDouble() {
		assertAdaptNumber(Double.class, double.class, () -> ThreadLocalRandom.current().nextDouble(),
				JsonNumber::doubleValue, a -> Double.toString(a), 0.0);
	}

	private <T extends Number> void assertAdaptNumber(Class<T> c, Class<?> pc, Supplier<T> rand,
			Function<JsonNumber, T> fromJson, Function<T, String> toString, T def) {
		final var adapter = IuJsonAdapter.of(c);
		assertNull(adapter.fromJson(JsonValue.NULL));
		assertEquals(JsonValue.NULL, adapter.toJson(null));

		final var n = rand.get();
		assertEquals(n, adapter.fromJson(IuJson.number(n)));
		assertEquals(n, adapter.fromJson(IuJson.string(toString.apply(n))));
		assertEquals(n, fromJson.apply((JsonNumber) adapter.toJson(n)));

		if (pc == null)
			return;

		final var primitive = IuJsonAdapter.<T>of(pc);
		assertEquals(def, primitive.fromJson(JsonValue.NULL));
		assertEquals(def, primitive.fromJson(null));
		assertEquals(n, primitive.fromJson(IuJson.number(n)));
		assertEquals(n, primitive.fromJson(IuJson.string(toString.apply(n))));
		assertEquals(n, fromJson.apply((JsonNumber) primitive.toJson(n)));
	}

	@Test
	public void testBinary() {
		final var adapter = IuJsonAdapter.of(byte[].class);
		assertEquals(JsonValue.NULL, adapter.toJson(null));
		assertEquals(IuJson.string(""), adapter.toJson(new byte[0]));
		assertNull(adapter.fromJson(JsonValue.NULL));

		final var data = new byte[Math.abs(ThreadLocalRandom.current().nextInt(Byte.MAX_VALUE + 1, Short.MAX_VALUE))];
		ThreadLocalRandom.current().nextBytes(data);
		final var text = IuText.base64(data);
		assertEquals(IuJson.string(text), adapter.toJson(data));
		assertArrayEquals(data, adapter.fromJson(IuJson.string(text)));
	}

	@Test
	public void testRFC7517BigIntAdapter() {
		final var adapter = IuJsonAdapter.from(v -> IuText.utf8(((JsonString) v).getString()),
				b -> IuJson.string(IuText.utf8(b)));

		final var text = IdGenerator.generateId();
		final var data = IuText.utf8(text);
		assertEquals(IuJson.string(text), adapter.toJson(data));
		assertArrayEquals(data, adapter.fromJson(IuJson.string(text)));
	}

	@Test
	public void testCharSequence() {
		final var adapter = IuJsonAdapter.of(CharSequence.class);
		assertEquals(JsonValue.NULL, adapter.toJson(null));
		assertNull(adapter.fromJson(JsonValue.NULL));

		final var id = IdGenerator.generateId();
		assertEquals(IuJson.string(id), adapter.toJson(new StringBuilder(id)));
		assertEquals(id, adapter.fromJson(IuJson.string(id)));
	}

	@Test
	public void testCalendar() {
		final var adapter = IuJsonAdapter.of(Calendar.class);
		assertEquals(JsonValue.NULL, adapter.toJson(null));
		assertNull(adapter.fromJson(JsonValue.NULL));

		final var value = Calendar.getInstance();
		final var text = IuJson
				.string(DateTimeFormatter.ISO_DATE_TIME.withZone(ZoneId.of("UTC")).format(value.getTime().toInstant()));
		assertEquals(text, adapter.toJson(value));
		assertEquals(value, adapter.fromJson(text));

		value.set(Calendar.HOUR_OF_DAY, 0);
		value.set(Calendar.MINUTE, 0);
		value.set(Calendar.SECOND, 0);
		value.set(Calendar.MILLISECOND, 0);
		final var datetext = IuJson
				.string(DateTimeFormatter.ISO_DATE.withZone(ZoneId.of("UTC")).format(value.getTime().toInstant()));
		assertEquals(datetext, adapter.toJson(value));
		assertEquals(value, adapter.fromJson(datetext));
	}

	@Test
	public void testDate() {
		final var adapter = IuJsonAdapter.of(Date.class);
		assertEquals(JsonValue.NULL, adapter.toJson(null));
		assertNull(adapter.fromJson(JsonValue.NULL));

		final var value = new Date();
		final var text = IuJson
				.string(DateTimeFormatter.ISO_DATE_TIME.withZone(ZoneId.of("UTC")).format(value.toInstant()));
		assertEquals(text, adapter.toJson(value));
		assertEquals(value, adapter.fromJson(text));
	}

	@Test
	public void testInstant() {
		final var adapter = IuJsonAdapter.of(Instant.class);
		assertEquals(JsonValue.NULL, adapter.toJson(null));
		assertNull(adapter.fromJson(JsonValue.NULL));

		final var value = Instant.now();
		final var text = IuJson.string(value.toString());
		assertEquals(text, adapter.toJson(value));
		assertEquals(value, adapter.fromJson(text));
	}

	@Test
	public void testDuration() {
		final var adapter = IuJsonAdapter.of(Duration.class);
		assertEquals(JsonValue.NULL, adapter.toJson(null));
		assertNull(adapter.fromJson(JsonValue.NULL));

		final var value = Duration.ofNanos(ThreadLocalRandom.current().nextLong());
		final var text = IuJson.string(value.toString());
		assertEquals(text, adapter.toJson(value));
		assertEquals(value, adapter.fromJson(text));
	}

	enum TestEnum {
		A, B, C;
	}

	@Test
	public void testEnum() {
		final var adapter = IuJsonAdapter.of(TestEnum.class);
		assertEquals(JsonValue.NULL, adapter.toJson(null));
		assertNull(adapter.fromJson(JsonValue.NULL));

		final var value = TestEnum.B;
		final var text = IuJson.string(value.toString());
		assertEquals(text, adapter.toJson(value));
		assertEquals(value, adapter.fromJson(text));
	}

	@Test
	public void testLocalDate() {
		final var adapter = IuJsonAdapter.of(LocalDate.class);
		assertEquals(JsonValue.NULL, adapter.toJson(null));
		assertNull(adapter.fromJson(JsonValue.NULL));

		final var value = LocalDate.now();
		final var text = IuJson.string(value.toString());
		assertEquals(text, adapter.toJson(value));
		assertEquals(value, adapter.fromJson(text));
	}

	@Test
	public void testLocalTime() {
		final var adapter = IuJsonAdapter.of(LocalTime.class);
		assertEquals(JsonValue.NULL, adapter.toJson(null));
		assertNull(adapter.fromJson(JsonValue.NULL));

		final var value = LocalTime.now();
		final var text = IuJson.string(value.toString());
		assertEquals(text, adapter.toJson(value));
		assertEquals(value, adapter.fromJson(text));
	}

	@Test
	public void testLocalDateTime() {
		final var adapter = IuJsonAdapter.of(LocalDateTime.class);
		assertEquals(JsonValue.NULL, adapter.toJson(null));
		assertNull(adapter.fromJson(JsonValue.NULL));

		final var value = LocalDateTime.now();
		final var text = IuJson.string(value.toString());
		assertEquals(text, adapter.toJson(value));
		assertEquals(value, adapter.fromJson(text));
	}

	@Test
	public void testOffsetDateTime() {
		final var adapter = IuJsonAdapter.of(OffsetDateTime.class);
		assertEquals(JsonValue.NULL, adapter.toJson(null));
		assertNull(adapter.fromJson(JsonValue.NULL));

		final var value = OffsetDateTime.now();
		final var text = IuJson.string(value.toString());
		assertEquals(text, adapter.toJson(value));
		assertEquals(value, adapter.fromJson(text));
	}

	@Test
	public void testOffsetTime() {
		final var adapter = IuJsonAdapter.of(OffsetTime.class);
		assertEquals(JsonValue.NULL, adapter.toJson(null));
		assertNull(adapter.fromJson(JsonValue.NULL));

		final var value = OffsetTime.now();
		final var text = IuJson.string(value.toString());
		assertEquals(text, adapter.toJson(value));
		assertEquals(value, adapter.fromJson(text));
	}

	@Test
	public void testPeriod() {
		final var adapter = IuJsonAdapter.of(Period.class);
		assertEquals(JsonValue.NULL, adapter.toJson(null));
		assertNull(adapter.fromJson(JsonValue.NULL));

		final var value = Period.ofDays(ThreadLocalRandom.current().nextInt());
		final var text = IuJson.string(value.toString());
		assertEquals(text, adapter.toJson(value));
		assertEquals(value, adapter.fromJson(text));
	}

	@Test
	public void testZonedDateTime() {
		final var adapter = IuJsonAdapter.of(ZonedDateTime.class);
		assertEquals(JsonValue.NULL, adapter.toJson(null));
		assertNull(adapter.fromJson(JsonValue.NULL));

		final var value = ZonedDateTime.now();
		final var text = IuJson.string(value.toString());
		assertEquals(text, adapter.toJson(value));
		assertEquals(value, adapter.fromJson(text));
	}

	@Test
	public void testSimpleTimeZone() {
		final var adapter = IuJsonAdapter.of(SimpleTimeZone.class);
		assertEquals(JsonValue.NULL, adapter.toJson(null));
		assertNull(adapter.fromJson(JsonValue.NULL));

		final var id = ZoneId.systemDefault();
		final var text = IuJson.string(id.toString());
		final var time = LocalDateTime.now().atZone(id);
		final var value = new SimpleTimeZone(time.getOffset().getTotalSeconds() * 1000, id.getId());
		assertEquals(text, adapter.toJson(value));
		assertEquals(value, adapter.fromJson(text));
	}

	@Test
	public void testTimeZone() {
		final var adapter = IuJsonAdapter.of(TimeZone.class);
		assertEquals(JsonValue.NULL, adapter.toJson(null));
		assertNull(adapter.fromJson(JsonValue.NULL));

		final var id = ZoneId.systemDefault();
		final var text = IuJson.string(id.toString());
		final var time = LocalDateTime.now().atZone(id);
		final var value = new SimpleTimeZone(time.getOffset().getTotalSeconds() * 1000, id.getId());
		assertEquals(text, adapter.toJson(value));
		assertEquals(value, adapter.fromJson(text));
	}

	@Test
	public void testZoneId() {
		final var adapter = IuJsonAdapter.of(ZoneId.class);
		assertEquals(JsonValue.NULL, adapter.toJson(null));
		assertNull(adapter.fromJson(JsonValue.NULL));

		final var value = ZoneId.systemDefault();
		final var text = IuJson.string(value.toString());
		assertEquals(text, adapter.toJson(value));
		assertEquals(value, adapter.fromJson(text));
	}

	@Test
	public void testZoneOffset() {
		final var adapter = IuJsonAdapter.of(ZoneOffset.class);
		assertEquals(JsonValue.NULL, adapter.toJson(null));
		assertNull(adapter.fromJson(JsonValue.NULL));

		final var value = ZoneOffset.of("+5");
		final var text = IuJson.string(value.toString());
		assertEquals(text, adapter.toJson(value));
		assertEquals(value, adapter.fromJson(text));
	}

	@Test
	public void testGenericOptional() throws NoSuchFieldException {
		class A {
			@SuppressWarnings("unused")
			Optional<URI> a;
		}
		final var adapter = IuJsonAdapter.<Optional<URI>>of(A.class.getDeclaredField("a").getGenericType());
		assertEquals(JsonValue.NULL, adapter.toJson(null));
		assertTrue(adapter.fromJson(JsonValue.NULL).isEmpty());

		final var value = Optional.of(URI.create("test://" + IdGenerator.generateId()));
		final var text = IuJson.string(value.get().toString());
		assertEquals(text, adapter.toJson(value));
		assertEquals(value, adapter.fromJson(text));
	}

	@Test
	public void testAdaptedOptional() {
		final var adapter = IuJsonAdapter.of(Optional.class, IuJsonAdapter.of(URI.class));
		assertEquals(JsonValue.NULL, adapter.toJson(null));
		assertTrue(adapter.fromJson(JsonValue.NULL).isEmpty());

		final var value = Optional.of(URI.create("test://" + IdGenerator.generateId()));
		final var text = IuJson.string(value.get().toString());
		assertEquals(text, adapter.toJson(value));
		assertEquals(value, adapter.fromJson(text));
	}

	@Test
	public void testOptional() {
		final var adapter = IuJsonAdapter.of(Optional.class);
		assertEquals(JsonValue.NULL, adapter.toJson(null));
		assertTrue(adapter.fromJson(JsonValue.NULL).isEmpty());

		final var value = Optional.of(IdGenerator.generateId());
		final var text = IuJson.string(value.get().toString());
		assertEquals(text, adapter.toJson(value));
		assertEquals(value, adapter.fromJson(text));
	}

	@Test
	public void testPattern() {
		final var adapter = IuJsonAdapter.of(Pattern.class);
		assertEquals(JsonValue.NULL, adapter.toJson(null));
		assertNull(adapter.fromJson(JsonValue.NULL));

		final var value = Pattern.compile("^$");
		final var text = IuJson.string(value.toString());
		assertEquals(text, adapter.toJson(value));
		assertEquals(value.toString(), adapter.fromJson(text).toString());
	}

	@Test
	public void testURL() throws MalformedURLException {
		final var adapter = IuJsonAdapter.of(URL.class);
		assertEquals(JsonValue.NULL, adapter.toJson(null));
		assertNull(adapter.fromJson(JsonValue.NULL));

		final var value = new URL("http://localhost");
		final var text = IuJson.string(value.toString());
		assertEquals(text, adapter.toJson(value));
		assertEquals(value, adapter.fromJson(text));
	}

	@Test
	public void testArray() throws MalformedURLException {
		final var adapter = IuJsonAdapter.of(URL[].class);
		assertEquals(JsonValue.NULL, adapter.toJson(null));
		assertNull(adapter.fromJson(JsonValue.NULL));

		final var value = new URL[] { new URL("http://localhost/a"), new URL("http://localhost/b") };
		final var array = IuJson.array().add("http://localhost/a").add("http://localhost/b").build();
		assertEquals(array, adapter.toJson(value));
		assertArrayEquals(value, adapter.fromJson(array));
	}

	@Test
	public void testGenericArray() throws NoSuchFieldException {
		class A<B extends Number> {
			@SuppressWarnings("unused")
			B[] c;
		}
		final var type = A.class.getDeclaredField("c").getGenericType();
		final var adapter = IuJsonAdapter.<Number[]>of(type);
		IuJsonAdapter.<Number[]>of(type); // covers generic array type cache
		assertEquals(JsonValue.NULL, adapter.toJson(null));
		assertNull(adapter.fromJson(JsonValue.NULL));

		final var value = new Number[] { 1, 2.0, new BigDecimal("3") };
		final var array = IuJson.array().add(1).add(2.0).add(new BigDecimal("3")).build();
		assertEquals(array, adapter.toJson(value));
		assertArrayEquals(new Number[] { new BigDecimal("1"), new BigDecimal("2.0"), new BigDecimal("3") },
				adapter.fromJson(array));
	}

	@Test
	public void testGenericList() throws NoSuchFieldException {
		class A {
			@SuppressWarnings("unused")
			List<URI> c;
		}
		final var adapter = IuJsonAdapter.<List<URI>>of(A.class.getDeclaredField("c").getGenericType());
		assertEquals(JsonValue.NULL, adapter.toJson(null));
		assertNull(adapter.fromJson(JsonValue.NULL));

		final var value = List.of(URI.create("http://localhost/a"), URI.create("http://localhost/b"));
		final var array = IuJson.array().add("http://localhost/a").add("http://localhost/b").build();
		assertEquals(array, adapter.toJson(value));
		assertEquals(value, adapter.fromJson(array));
	}

	@Test
	public void testAdaptedList() {
		final var adapter = IuJsonAdapter.of(List.class, IuJsonAdapter.of(URI.class));
		assertEquals(JsonValue.NULL, adapter.toJson(null));
		assertNull(adapter.fromJson(JsonValue.NULL));

		final var value = List.of(URI.create("http://localhost/a"), URI.create("http://localhost/b"));
		final var array = IuJson.array().add("http://localhost/a").add("http://localhost/b").build();
		assertEquals(array, adapter.toJson(value));
		assertEquals(value, adapter.fromJson(array));
	}

	@Test
	public void testList() {
		final var adapter = IuJsonAdapter.of(ArrayList.class);
		assertEquals(JsonValue.NULL, adapter.toJson(null));
		assertNull(adapter.fromJson(JsonValue.NULL));

		final var value = new ArrayList<>(List.of("http://localhost/a", "http://localhost/b"));
		final var array = IuJson.array().add("http://localhost/a").add("http://localhost/b").build();
		assertEquals(array, adapter.toJson(value));
		assertEquals(value, adapter.fromJson(array));
	}

	@Test
	public void testGenericQueue() throws NoSuchFieldException {
		class A {
			@SuppressWarnings("unused")
			Collection<URI> c;
		}
		final var adapter = IuJsonAdapter.<Collection<URI>>of(A.class.getDeclaredField("c").getGenericType());
		assertEquals(JsonValue.NULL, adapter.toJson(null));
		assertNull(adapter.fromJson(JsonValue.NULL));

		final var a = IuJson.array();
		final var q = new ArrayDeque<URI>();
		final var l = ThreadLocalRandom.current().nextInt(Byte.MAX_VALUE, Short.MAX_VALUE);
		for (var i = 0; i < l; i++) {
			final var u = URI.create("test://" + IdGenerator.generateId());
			q.offer(u);
			a.add(u.toString());
		}
		final var array = a.build();

		assertEquals(array, adapter.toJson(q));
		assertArrayEquals(q.toArray(), adapter.fromJson(array).toArray());
	}

	@Test
	public void testWildcardQueue() throws NoSuchFieldException {
		class A {
			@SuppressWarnings("unused")
			Collection<? extends Number> c;
		}
		final var adapter = IuJsonAdapter
				.<Collection<? extends Number>>of(A.class.getDeclaredField("c").getGenericType());
		assertEquals(JsonValue.NULL, adapter.toJson(null));
		assertNull(adapter.fromJson(JsonValue.NULL));

		final var a = IuJson.array();
		final var q = new ArrayDeque<BigDecimal>();
		final var l = ThreadLocalRandom.current().nextInt(Byte.MAX_VALUE, Short.MAX_VALUE);
		for (var i = 0; i < l; i++) {
			final var u = new BigDecimal(Long.toString(ThreadLocalRandom.current().nextLong())
					+ Long.toString(Math.abs(ThreadLocalRandom.current().nextLong())) + '.'
					+ Long.toString(Math.abs(ThreadLocalRandom.current().nextLong())));
			q.offer(u);
			a.add(u);
		}
		final var array = a.build();

		assertEquals(array, adapter.toJson(q));
		assertArrayEquals(q.toArray(), adapter.fromJson(array).toArray());
	}

	@Test
	public void testAdaptedQueue() {
		final var adapter = IuJsonAdapter.of(Queue.class, IuJsonAdapter.of(URI.class));
		assertEquals(JsonValue.NULL, adapter.toJson(null));
		assertNull(adapter.fromJson(JsonValue.NULL));

		final var a = IuJson.array();
		final var q = new ArrayDeque<URI>();
		final var l = ThreadLocalRandom.current().nextInt(Byte.MAX_VALUE, Short.MAX_VALUE);
		for (var i = 0; i < l; i++) {
			final var u = URI.create("test://" + IdGenerator.generateId());
			q.offer(u);
			a.add(u.toString());
		}
		final var array = a.build();
		assertEquals(array, adapter.toJson(q));
		assertArrayEquals(q.toArray(), adapter.fromJson(array).toArray());
	}

	@Test
	public void testDeque() {
		final var adapter = IuJsonAdapter.of(Deque.class);
		assertEquals(JsonValue.NULL, adapter.toJson(null));
		assertNull(adapter.fromJson(JsonValue.NULL));

		final var a = IuJson.array();
		final var q = new ArrayDeque<>();
		final var l = ThreadLocalRandom.current().nextInt(Byte.MAX_VALUE, Short.MAX_VALUE);
		for (var i = 0; i < l; i++) {
			final var s = IdGenerator.generateId();
			q.offer(s);
			a.add(s);
		}
		final var array = a.build();
		assertEquals(array, adapter.toJson(q));
		assertArrayEquals(q.toArray(), adapter.fromJson(array).toArray());
	}

	@Test
	public void testArrayDeque() {
		final var adapter = IuJsonAdapter.of(ArrayDeque.class);
		assertEquals(JsonValue.NULL, adapter.toJson(null));
		assertNull(adapter.fromJson(JsonValue.NULL));

		final var a = IuJson.array();
		final var q = new ArrayDeque<>();
		final var l = ThreadLocalRandom.current().nextInt(Byte.MAX_VALUE, Short.MAX_VALUE);
		for (var i = 0; i < l; i++) {
			final var s = IdGenerator.generateId();
			q.offer(s);
			a.add(s);
		}
		final var array = a.build();
		assertEquals(array, adapter.toJson(q));
		assertArrayEquals(q.toArray(), adapter.fromJson(array).toArray());
	}

	@Test
	public void testIterable() {
		final var adapter = IuJsonAdapter.of(Iterable.class);
		assertEquals(JsonValue.NULL, adapter.toJson(null));
		assertNull(adapter.fromJson(JsonValue.NULL));

		final var a = IuJson.array();
		final var q = new ArrayDeque<>();
		final var l = ThreadLocalRandom.current().nextInt(Byte.MAX_VALUE, Short.MAX_VALUE);
		for (var i = 0; i < l; i++) {
			final var s = IdGenerator.generateId();
			q.offer(s);
			a.add(s);
		}
		final var array = a.build();
		assertEquals(array, adapter.toJson(q));
		final var qi = q.iterator();
		for (final var b : adapter.fromJson(array)) {
			assertTrue(qi.hasNext());
			assertEquals(b, qi.next());
		}
		assertFalse(qi.hasNext());
	}

	@Test
	public void testSet() {
		final var adapter = IuJsonAdapter.of(Set.class);
		assertEquals(JsonValue.NULL, adapter.toJson(null));
		assertNull(adapter.fromJson(JsonValue.NULL));

		final var a = IuJson.array();
		final var c = new LinkedHashSet<>();
		final var l = ThreadLocalRandom.current().nextInt(Byte.MAX_VALUE, Short.MAX_VALUE);
		for (var i = 0; i < l; i++) {
			final var s = IdGenerator.generateId();
			c.add(s);
			a.add(s);
		}
		final var array = a.build();
		assertEquals(array, adapter.toJson(c));
		final var qi = c.iterator();
		for (final var b : adapter.fromJson(array)) {
			assertTrue(qi.hasNext());
			assertEquals(b, qi.next());
		}
		assertFalse(qi.hasNext());
	}

	@Test
	public void testLinkedHashSet() {
		final var adapter = IuJsonAdapter.of(LinkedHashSet.class);
		assertEquals(JsonValue.NULL, adapter.toJson(null));
		assertNull(adapter.fromJson(JsonValue.NULL));

		final var a = IuJson.array();
		final var c = new LinkedHashSet<>();
		final var l = ThreadLocalRandom.current().nextInt(Byte.MAX_VALUE, Short.MAX_VALUE);
		for (var i = 0; i < l; i++) {
			final var s = IdGenerator.generateId();
			c.add(s);
			a.add(s);
		}
		final var array = a.build();
		assertEquals(array, adapter.toJson(c));
		final var qi = c.iterator();
		for (final var b : adapter.fromJson(array)) {
			assertTrue(qi.hasNext());
			assertEquals(b, qi.next());
		}
		assertFalse(qi.hasNext());
	}

	@Test
	public void testHashSet() {
		final var adapter = IuJsonAdapter.of(HashSet.class);
		assertEquals(JsonValue.NULL, adapter.toJson(null));
		assertNull(adapter.fromJson(JsonValue.NULL));

		final var a = IuJson.array();
		final var c = new LinkedHashSet<>();
		final var l = ThreadLocalRandom.current().nextInt(Byte.MAX_VALUE, Short.MAX_VALUE);
		for (var i = 0; i < l; i++) {
			final var s = IdGenerator.generateId();
			c.add(s);
			a.add(s);
		}
		final var array = a.build();
		assertEquals(array, adapter.toJson(c));
		assertEquals(c, adapter.fromJson(array));
	}

	@Test
	public void testSortedSet() {
		final var adapter = IuJsonAdapter.of(SortedSet.class);
		assertEquals(JsonValue.NULL, adapter.toJson(null));
		assertNull(adapter.fromJson(JsonValue.NULL));

		final var c = new TreeSet<String>();
		final var l = ThreadLocalRandom.current().nextInt(Byte.MAX_VALUE, Short.MAX_VALUE);
		for (var i = 0; i < l; i++) {
			final var s = IdGenerator.generateId();
			c.add(s);
		}
		final var a = IuJson.array();
		c.forEach(a::add);
		final var array = a.build();
		assertEquals(array, adapter.toJson(c));
		assertEquals(c, adapter.fromJson(array));
	}

	@Test
	public void testNavigableSet() {
		final var adapter = IuJsonAdapter.of(NavigableSet.class);
		assertEquals(JsonValue.NULL, adapter.toJson(null));
		assertNull(adapter.fromJson(JsonValue.NULL));

		final var c = new TreeSet<String>();
		final var l = ThreadLocalRandom.current().nextInt(Byte.MAX_VALUE, Short.MAX_VALUE);
		for (var i = 0; i < l; i++) {
			final var s = IdGenerator.generateId();
			c.add(s);
		}
		final var a = IuJson.array();
		c.forEach(a::add);
		final var array = a.build();
		assertEquals(array, adapter.toJson(c));
		assertEquals(c, adapter.fromJson(array));
	}

	@Test
	public void testTreeSet() {
		final var adapter = IuJsonAdapter.of(TreeSet.class);
		assertEquals(JsonValue.NULL, adapter.toJson(null));
		assertNull(adapter.fromJson(JsonValue.NULL));

		final var c = new TreeSet<String>();
		final var l = ThreadLocalRandom.current().nextInt(Byte.MAX_VALUE, Short.MAX_VALUE);
		for (var i = 0; i < l; i++) {
			final var s = IdGenerator.generateId();
			c.add(s);
		}
		final var a = IuJson.array();
		c.forEach(a::add);
		final var array = a.build();
		assertEquals(array, adapter.toJson(c));
		assertEquals(c, adapter.fromJson(array));
	}

	@Test
	public void testStream() {
		final var adapter = IuJsonAdapter.<Stream<String>>of(Stream.class);
		assertEquals(JsonValue.NULL, adapter.toJson(null));
		assertNull(adapter.fromJson(JsonValue.NULL));

		final var a = IuJson.array();
		final var q = new ArrayDeque<String>();
		final var l = ThreadLocalRandom.current().nextInt(Byte.MAX_VALUE, Short.MAX_VALUE);
		for (var i = 0; i < l; i++) {
			final var s = IdGenerator.generateId();
			q.add(s);
			a.add(s);
		}
		final var array = a.build();
		assertEquals(array, adapter.toJson(q.stream()));
		final var qi = q.iterator();
		adapter.fromJson(array).forEach(b -> {
			assertTrue(qi.hasNext());
			assertEquals(b, qi.next());
		});
		assertFalse(qi.hasNext());
	}

	@Test
	public void testIterator() {
		final var adapter = IuJsonAdapter.<Iterator<String>>of(Iterator.class);
		assertEquals(JsonValue.NULL, adapter.toJson(null));
		assertNull(adapter.fromJson(JsonValue.NULL));

		final var a = IuJson.array();
		final var q = new ArrayDeque<String>();
		final var l = ThreadLocalRandom.current().nextInt(Byte.MAX_VALUE, Short.MAX_VALUE);
		for (var i = 0; i < l; i++) {
			final var s = IdGenerator.generateId();
			q.add(s);
			a.add(s);
		}
		final var array = a.build();
		assertEquals(array, adapter.toJson(q.iterator()));
		final var qi = q.iterator();
		final var ai = adapter.fromJson(array);
		while (ai.hasNext()) {
			assertTrue(qi.hasNext());
			assertEquals(ai.next(), qi.next());
		}
		assertFalse(qi.hasNext());
	}

	@Test
	public void testEnumeration() {
		final var adapter = IuJsonAdapter.<Enumeration<String>>of(Enumeration.class);
		assertEquals(JsonValue.NULL, adapter.toJson(null));
		assertNull(adapter.fromJson(JsonValue.NULL));

		final var a = IuJson.array();
		final var q = new ArrayDeque<String>();
		final var l = ThreadLocalRandom.current().nextInt(Byte.MAX_VALUE, Short.MAX_VALUE);
		for (var i = 0; i < l; i++) {
			final var s = IdGenerator.generateId();
			q.add(s);
			a.add(s);
		}
		final var array = a.build();
		final var qe = q.iterator();
		assertEquals(array, adapter.toJson(new Enumeration<>() {
			@Override
			public boolean hasMoreElements() {
				return qe.hasNext();
			}

			@Override
			public String nextElement() {
				return qe.next();
			}
		}));

		final var qi = q.iterator();
		final var ai = adapter.fromJson(array);
		while (ai.hasMoreElements()) {
			assertTrue(qi.hasNext());
			assertEquals(ai.nextElement(), qi.next());
		}
		assertFalse(qi.hasNext());
	}

	@Test
	public void testGenericMap() throws NoSuchFieldException {
		class A {
			@SuppressWarnings("unused")
			Map<String, URI> m;
		}
		final var adapter = IuJsonAdapter.<Map<String, URI>>of(A.class.getDeclaredField("m").getGenericType());
		assertEquals(JsonValue.NULL, adapter.toJson(null));
		assertNull(adapter.fromJson(null));
		assertNull(adapter.fromJson(JsonValue.NULL));

		final var a = IuJson.object();
		final var m = new LinkedHashMap<String, URI>();
		final var l = ThreadLocalRandom.current().nextInt(Byte.MAX_VALUE, Short.MAX_VALUE);
		for (var i = 0; i < l; i++) {
			final var k = IdGenerator.generateId();
			final var u = URI.create("test://" + IdGenerator.generateId());
			m.put(k, u);
			a.add(k, u.toString());
		}
		final var object = a.build();

		assertEquals(object, adapter.toJson(m));
		assertEquals(m, adapter.fromJson(object));
	}

	@Test
	public void testAdaptedMap() {
		final var adapter = IuJsonAdapter.of(LinkedHashMap.class, IuJsonAdapter.of(URI.class));
		assertEquals(JsonValue.NULL, adapter.toJson(null));
		assertNull(adapter.fromJson(null));
		assertNull(adapter.fromJson(JsonValue.NULL));

		final var a = IuJson.object();
		final var m = new LinkedHashMap<String, URI>();
		final var l = ThreadLocalRandom.current().nextInt(Byte.MAX_VALUE, Short.MAX_VALUE);
		for (var i = 0; i < l; i++) {
			final var k = IdGenerator.generateId();
			final var u = URI.create("test://" + IdGenerator.generateId());
			m.put(k, u);
			a.add(k, u.toString());
		}
		final var object = a.build();

		assertEquals(object, adapter.toJson(m));
		assertEquals(m, adapter.fromJson(object));
	}

	@Test
	public void testHashMap() {
		final var adapter = IuJsonAdapter.of(HashMap.class);
		assertEquals(JsonValue.NULL, adapter.toJson(null));
		assertNull(adapter.fromJson(null));
		assertNull(adapter.fromJson(JsonValue.NULL));

		final var a = IuJson.object();
		final var m = new LinkedHashMap<String, String>();
		final var l = ThreadLocalRandom.current().nextInt(Byte.MAX_VALUE, Short.MAX_VALUE);
		for (var i = 0; i < l; i++) {
			final var k = IdGenerator.generateId();
			final var s = IdGenerator.generateId();
			m.put(k, s);
			a.add(k, s);
		}
		final var object = a.build();

		assertEquals(object, adapter.toJson(m));
		assertEquals(m, adapter.fromJson(object));
	}

	@Test
	public void testSortedMap() {
		final var adapter = IuJsonAdapter.of(SortedMap.class);
		assertEquals(JsonValue.NULL, adapter.toJson(null));
		assertNull(adapter.fromJson(null));
		assertNull(adapter.fromJson(JsonValue.NULL));

		final var a = IuJson.object();
		final var m = new TreeMap<String, String>();
		final var l = ThreadLocalRandom.current().nextInt(Byte.MAX_VALUE, Short.MAX_VALUE);
		for (var i = 0; i < l; i++) {
			final var k = IdGenerator.generateId();
			final var s = IdGenerator.generateId();
			m.put(k, s);
			a.add(k, s);
		}
		final var object = a.build();

		assertEquals(object, adapter.toJson(m));
		assertEquals(m, adapter.fromJson(object));
	}

	@Test
	public void testNavigableMap() {
		final var adapter = IuJsonAdapter.of(NavigableMap.class);
		assertEquals(JsonValue.NULL, adapter.toJson(null));
		assertNull(adapter.fromJson(null));
		assertNull(adapter.fromJson(JsonValue.NULL));

		final var a = IuJson.object();
		final var m = new TreeMap<String, String>();
		final var l = ThreadLocalRandom.current().nextInt(Byte.MAX_VALUE, Short.MAX_VALUE);
		for (var i = 0; i < l; i++) {
			final var k = IdGenerator.generateId();
			final var s = IdGenerator.generateId();
			m.put(k, s);
			a.add(k, s);
		}
		final var object = a.build();

		assertEquals(object, adapter.toJson(m));
		assertEquals(m, adapter.fromJson(object));
	}

	@Test
	public void testTreeMap() {
		final var adapter = IuJsonAdapter.of(TreeMap.class);
		assertEquals(JsonValue.NULL, adapter.toJson(null));
		assertNull(adapter.fromJson(null));
		assertNull(adapter.fromJson(JsonValue.NULL));

		final var a = IuJson.object();
		final var m = new TreeMap<String, String>();
		final var l = ThreadLocalRandom.current().nextInt(Byte.MAX_VALUE, Short.MAX_VALUE);
		for (var i = 0; i < l; i++) {
			final var k = IdGenerator.generateId();
			final var s = IdGenerator.generateId();
			m.put(k, s);
			a.add(k, s);
		}
		final var object = a.build();

		assertEquals(object, adapter.toJson(m));
		assertEquals(m, adapter.fromJson(object));
	}

	@Test
	public void testProperties() {
		final var adapter = IuJsonAdapter.of(Properties.class);
		assertEquals(JsonValue.NULL, adapter.toJson(null));
		assertNull(adapter.fromJson(null));
		assertNull(adapter.fromJson(JsonValue.NULL));

		final var a = IuJson.object();
		final var m = new Properties();
		final var l = ThreadLocalRandom.current().nextInt(Byte.MAX_VALUE, Short.MAX_VALUE);
		for (var i = 0; i < l; i++) {
			final var k = IdGenerator.generateId();
			final var s = IdGenerator.generateId();
			m.put(k, s);
			a.add(k, s);
		}
		final var object = a.build();

		assertEquals(object, adapter.toJson(m));
		assertEquals(m, adapter.fromJson(object));
	}

}
