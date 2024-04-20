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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.Queue;
import java.util.Set;
import java.util.SimpleTimeZone;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.Spliterator;
import java.util.Spliterators;
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
import edu.iu.IuEnumerableQueue;
import edu.iu.IuException;
import edu.iu.IuIterable;
import edu.iu.IuObject;
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

	@Test
	@SuppressWarnings("unchecked")
	public void testFrom() {
		final var from = mock(Function.class);
		final var adapter = IuJsonAdapter.from(from);
		assertThrows(UnsupportedOperationException.class, () -> adapter.toJson(null));
		adapter.fromJson(null);
		verify(from).apply(null);
	}

	@Test
	@SuppressWarnings("unchecked")
	public void testTo() {
		final var to = mock(Function.class);
		final var adapter = IuJsonAdapter.to(to);
		assertThrows(UnsupportedOperationException.class, () -> adapter.fromJson(null));
		adapter.toJson(null);
		verify(to).apply(null);
	}

	@Test
	@SuppressWarnings("unchecked")
	public void testFromTo() {
		final var from = mock(Function.class);
		final var to = mock(Function.class);
		final var adapter = IuJsonAdapter.from(from, to);
		adapter.fromJson(null);
		verify(from).apply(null);
		adapter.toJson(null);
		verify(to).apply(null);
	}
	
	@Test
	@SuppressWarnings("unchecked")
	public void testText() {
		final var p = mock(Function.class);
		final var adapter = IuJsonAdapter.text(p);
		adapter.fromJson(JsonValue.NULL);
		verify(p, never()).apply(any());
		assertEquals(JsonValue.NULL, adapter.toJson(null));
		
		final var id = IdGenerator.generateId();
		adapter.fromJson(IuJson.string(id));
		verify(p).apply(id);
		assertEquals(IuJson.string(id), adapter.toJson(id));
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
		assertAdaptNumber(BigDecimal.class, null, this::randomBigDecimal, a -> a.bigDecimalValue(),
				BigDecimal::toString, BigDecimal.ZERO);
	}

	@Test
	public void testBigInteger() {
		assertAdaptNumber(BigInteger.class, null, this::randomBigInteger, a -> a.bigIntegerValue(),
				BigInteger::toString, BigInteger.ZERO);
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
	public void testUtf8Binary() {
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

		final var value = Optional.of(randomUri());
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

		final var value = randomUrl();
		final var text = IuJson.string(value.toString());
		assertEquals(text, adapter.toJson(value));
		assertEquals(value, adapter.fromJson(text));
	}

	@Test
	public void testArray() throws MalformedURLException {
		assertArray(IuJsonAdapter.of(URL[].class), IuJsonAdapter.of(URL.class), a -> a.toArray(URL[]::new),
				this::randomUrl);
	}

	@Test
	public void testAdaptedArray() throws MalformedURLException {
		class Name implements CharSequence {
			final String name;

			private Name(String name) {
				this.name = name;
			}

			@Override
			public int length() {
				return name.length();
			}

			@Override
			public char charAt(int index) {
				return name.charAt(index);
			}

			@Override
			public CharSequence subSequence(int start, int end) {
				return name.subSequence(start, end);
			}

			@Override
			public int hashCode() {
				return IuObject.hashCode(name);
			}

			@Override
			public boolean equals(Object obj) {
				if (!IuObject.typeCheck(this, obj))
					return false;
				Name other = (Name) obj;
				return Objects.equals(name, other.name);
			}
		}
		final var json = IuJsonAdapter.from(a -> new Name(((JsonString) a).getString()), a -> IuJson.string(a.name));
		assertArray(IuJsonAdapter.of(Name[].class, json), json, a -> a.toArray(Name[]::new),
				() -> new Name(IdGenerator.generateId()));
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
		assertArray(adapter, IuJsonAdapter.of(Number.class), a -> a.toArray(Number[]::new), this::randomBigDecimal);
	}

	@Test
	public void testGenericList() throws NoSuchFieldException {
		class A {
			@SuppressWarnings("unused")
			List<URI> c;
		}
		assertArray(IuJsonAdapter.<List<URI>>of(A.class.getDeclaredField("c").getGenericType()),
				IuJsonAdapter.of(URI.class), ArrayList::new, this::randomUri);
	}

	@Test
	public void testAdaptedList() {
		assertArray(IuJsonAdapter.of(List.class, IuJsonAdapter.of(URI.class)), IuJsonAdapter.of(URI.class),
				ArrayList::new, this::randomUri);
	}

	@Test
	public void testList() {
		assertStringArray(IuJsonAdapter.<ArrayList<String>>of(ArrayList.class), ArrayList::new);
	}

	@Test
	public void testGenericQueue() throws NoSuchFieldException {
		class A {
			@SuppressWarnings("unused")
			Collection<URI> c;
		}
		assertArray(IuJsonAdapter.<Collection<URI>>of(A.class.getDeclaredField("c").getGenericType()),
				IuJsonAdapter.of(URI.class), a -> a, this::randomUri);
	}

	@Test
	public void testWildcardQueue() throws NoSuchFieldException {
		class A {
			@SuppressWarnings("unused")
			Collection<? extends Number> c;
		}
		assertArray(IuJsonAdapter.<Collection<? extends Number>>of(A.class.getDeclaredField("c").getGenericType()),
				IuJsonAdapter.of(Number.class), a -> a, this::randomBigDecimal);
	}

	@Test
	public void testAdaptedQueue() {
		assertArray(IuJsonAdapter.<Queue<URI>>of(Queue.class, IuJsonAdapter.of(URI.class)), IuJsonAdapter.of(URI.class),
				a -> a, this::randomUri);
	}

	@Test
	public void testDeque() {
		assertStringArray(IuJsonAdapter.<Deque<String>>of(Deque.class), a -> a);
	}

	@Test
	public void testArrayDeque() {
		assertStringArray(IuJsonAdapter.<ArrayDeque<String>>of(ArrayDeque.class), a -> a);
	}

	@Test
	public void testIterable() {
		assertStringArray(IuJsonAdapter.<Iterable<String>>of(Iterable.class), a -> a);
	}

	@Test
	public void testSet() {
		assertStringArray(IuJsonAdapter.<Set<String>>of(Set.class), HashSet::new);
	}

	@Test
	public void testLinkedHashSet() {
		assertStringArray(IuJsonAdapter.<LinkedHashSet<String>>of(LinkedHashSet.class), LinkedHashSet::new);
	}

	@Test
	public void testHashSet() {
		assertStringArray(IuJsonAdapter.<HashSet<String>>of(HashSet.class), HashSet::new);
	}

	@Test
	public void testSortedSet() {
		assertStringArray(IuJsonAdapter.<SortedSet<String>>of(SortedSet.class), TreeSet::new);
	}

	@Test
	public void testNavigableSet() {
		assertStringArray(IuJsonAdapter.<NavigableSet<String>>of(NavigableSet.class), TreeSet::new);
	}

	@Test
	public void testTreeSet() {
		assertStringArray(IuJsonAdapter.<TreeSet<String>>of(TreeSet.class), TreeSet::new);
	}

	@Test
	public void testStream() {
		assertStringArray(IuJsonAdapter.<Stream<String>>of(Stream.class), ArrayDeque::stream);
	}

	@Test
	public void testIterator() {
		assertStringArray(IuJsonAdapter.<Iterator<String>>of(Iterator.class), ArrayDeque::iterator);
	}

	@Test
	public void testEnumeration() {
		assertStringArray(IuJsonAdapter.<Enumeration<String>>of(Enumeration.class), q -> new IuEnumerableQueue<>(q));
	}

	@Test
	public void testGenericMap() throws NoSuchFieldException {
		class A {
			@SuppressWarnings("unused")
			Map<String, URI> m;
		}
		assertMap(IuJsonAdapter.of(A.class.getDeclaredField("m").getGenericType()), LinkedHashMap::new,
				this::randomUri);
	}

	@Test
	public void testAdaptedMap() {
		assertMap(IuJsonAdapter.of(LinkedHashMap.class, IuJsonAdapter.of(URI.class)), LinkedHashMap::new,
				this::randomUri);
	}

	@Test
	public void testHashMap() {
		assertStringMap(HashMap.class, HashMap::new);
	}

	@Test
	public void testSortedMap() {
		assertStringMap(SortedMap.class, TreeMap::new);
	}

	@Test
	public void testNavigableMap() {
		assertStringMap(NavigableMap.class, TreeMap::new);
	}

	@Test
	public void testTreeMap() {
		assertStringMap(TreeMap.class, TreeMap::new);
	}

	@Test
	public void testProperties() {
		assertStringMap(Properties.class, Properties::new);
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

	private URI randomUri() {
		return URI.create("test://" + IdGenerator.generateId());
	}

	private URL randomUrl() {
		return IuException.unchecked(() -> new URL("http://localhost/" + IdGenerator.generateId()));
	}

	private int randomLength() {
		return Math.abs(ThreadLocalRandom.current().nextInt(Byte.MAX_VALUE + 1, Short.MAX_VALUE));
	}

	private BigInteger randomBigInteger() {
		final var data = new byte[randomLength()];
		ThreadLocalRandom.current().nextBytes(data);
		return new BigInteger(1, data);
	}

	private BigDecimal randomBigDecimal() {
		return new BigDecimal(randomBigInteger(), ThreadLocalRandom.current().nextInt());
	}

	private <A> void assertStringArray(IuJsonAdapter<A> adapter, Function<ArrayDeque<String>, A> factory) {
		assertArray(adapter, IuJsonAdapter.of(String.class), factory, IdGenerator::generateId);
	}

	private <A, I> void assertArray(IuJsonAdapter<A> adapter, IuJsonAdapter<I> itemAdapter,
			Function<ArrayDeque<I>, A> factory, Supplier<I> itemFactory) {
		assertEquals(JsonValue.NULL, adapter.toJson(null));
		assertNull(adapter.fromJson(JsonValue.NULL));

		final var controlArrayBuilder = IuJson.array();
		final var controlElements = new HashSet<JsonValue>();
		final var controlValues = new ArrayDeque<I>();
		final var l = ThreadLocalRandom.current().nextInt(Byte.MAX_VALUE, Short.MAX_VALUE);
		for (var i = 0; i < l; i++) {
			final var item = itemFactory.get();
			controlValues.offer(item);

			final var jsonItem = itemAdapter.toJson(item);
			controlArrayBuilder.add(jsonItem);
			controlElements.add(jsonItem);
		}

		final var controlArray = controlArrayBuilder.build();
		final var arrayToCheck = adapter.toJson(factory.apply(controlValues)).asJsonArray();
		assertEquals(controlArray.size(), arrayToCheck.size());
		assertTrue(controlElements.containsAll(arrayToCheck));

		final var a = factory.apply(controlValues);
		final var b = adapter.fromJson(controlArray);
		if (b instanceof Iterator)
			assertTrue(IuIterable.remaindersAreEqual((Iterator<?>) a, (Iterator<?>) b));
		else if (b instanceof Enumeration)
			assertTrue(IuIterable.remaindersAreEqual(((Enumeration<?>) a).asIterator(),
					((Enumeration<?>) b).asIterator()));
		else if (b instanceof Stream)
			assertTrue(IuIterable.remaindersAreEqual(((Stream<?>) a).iterator(), ((Stream<?>) b).iterator()));
		else if (b instanceof Spliterator)
			assertTrue(IuIterable.remaindersAreEqual(Spliterators.iterator((Spliterator<?>) a),
					Spliterators.iterator((Spliterator<?>) a)));
		else if (b instanceof Queue)
			assertTrue(IuIterable.remaindersAreEqual(((Queue<?>) a).iterator(), ((Queue<?>) b).iterator()));
		else
			assertTrue(IuObject.equals(a, b));
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	private void assertStringMap(Class c, Supplier s) {
		assertMap(IuJsonAdapter.of(c, IuJsonAdapter.of(String.class)), s, IdGenerator::generateId);
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	private void assertMap(IuJsonAdapter adapter, Supplier s, Supplier f) {
		assertEquals(JsonValue.NULL, adapter.toJson(null));
		assertNull(adapter.fromJson(null));
		assertNull(adapter.fromJson(JsonValue.NULL));

		final var o = IuJson.object();
		final var m = (Map) s.get();
		final var l = ThreadLocalRandom.current().nextInt(Byte.MAX_VALUE, Short.MAX_VALUE);
		for (var i = 0; i < l; i++) {
			final var k = IdGenerator.generateId();
			final var u = f.get();
			m.put(k, u);
			o.add(k, u.toString());
		}
		final var object = o.build();

		assertEquals(object, adapter.toJson(m));
		assertEquals(m, adapter.fromJson(object));
	}

}
