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
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.SimpleTimeZone;
import java.util.TimeZone;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.mockito.stubbing.Answer;

import edu.iu.IdGenerator;
import edu.iu.IuText;
import jakarta.json.JsonArray;
import jakarta.json.JsonNumber;
import jakarta.json.JsonObject;
import jakarta.json.JsonValue;

@SuppressWarnings("javadoc")
public class IuJsonAdapterTest {

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
	}

	@Test
	public void testBoolean() {
		final var adapter = IuJsonAdapter.of(Boolean.class);
		assertTrue(adapter.fromJson(JsonValue.TRUE));
		assertFalse(adapter.fromJson(JsonValue.FALSE));
		assertNull(adapter.fromJson(JsonValue.NULL));
		assertEquals(JsonValue.TRUE, adapter.toJson(true));
		assertEquals(JsonValue.FALSE, adapter.toJson(false));
		assertEquals(JsonValue.NULL, adapter.toJson(null));

		final var primitive = IuJsonAdapter.of(boolean.class);
		assertTrue(primitive.fromJson(JsonValue.TRUE));
		assertFalse(primitive.fromJson(JsonValue.FALSE));
		assertThrows(NullPointerException.class, () -> primitive.fromJson(JsonValue.NULL));
		assertEquals(JsonValue.TRUE, primitive.toJson(true));
		assertEquals(JsonValue.FALSE, primitive.toJson(false));
		assertThrows(NullPointerException.class, () -> primitive.toJson(null));
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
		final var adapter = IuJsonAdapter.of(BigDecimal.class);
		final var n = new BigDecimal(Long.toString(ThreadLocalRandom.current().nextLong())
				+ Long.toString(Math.abs(ThreadLocalRandom.current().nextLong())) + '.'
				+ Long.toString(Math.abs(ThreadLocalRandom.current().nextLong())));

		assertEquals(n, adapter.fromJson(IuJson.number(n)));
		assertNull(adapter.fromJson(JsonValue.NULL));
		assertEquals(n, ((JsonNumber) adapter.toJson(n)).bigDecimalValue());
		assertEquals(JsonValue.NULL, adapter.toJson(null));
	}

	@Test
	public void testByte() {
		final var adapter = IuJsonAdapter.of(Byte.class);
		final var n = (byte) ThreadLocalRandom.current().nextInt();

		assertEquals(n, adapter.fromJson(IuJson.number(n)));
		assertNull(adapter.fromJson(JsonValue.NULL));
		assertEquals(n, ((JsonNumber) adapter.toJson(n)).intValueExact());
		assertEquals(JsonValue.NULL, adapter.toJson(null));

		final var primitive = IuJsonAdapter.of(byte.class);
		assertEquals(n, adapter.fromJson(IuJson.number(n)));
		assertThrows(NullPointerException.class, () -> primitive.fromJson(JsonValue.NULL));
		assertEquals(n, ((JsonNumber) adapter.toJson(n)).intValueExact());
		assertThrows(NullPointerException.class, () -> primitive.toJson(null));
	}

	@Test
	public void testShort() {
		final var adapter = IuJsonAdapter.of(Short.class);
		final var n = (short) ThreadLocalRandom.current().nextInt();

		assertEquals(n, adapter.fromJson(IuJson.number(n)));
		assertNull(adapter.fromJson(JsonValue.NULL));
		assertEquals(n, ((JsonNumber) adapter.toJson(n)).intValueExact());
		assertEquals(JsonValue.NULL, adapter.toJson(null));

		final var primitive = IuJsonAdapter.of(short.class);
		assertEquals(n, adapter.fromJson(IuJson.number(n)));
		assertThrows(NullPointerException.class, () -> primitive.fromJson(JsonValue.NULL));
		assertEquals(n, ((JsonNumber) adapter.toJson(n)).intValueExact());
		assertThrows(NullPointerException.class, () -> primitive.toJson(null));
	}

	@Test
	public void testInt() {
		final var adapter = IuJsonAdapter.of(Integer.class);
		final var n = ThreadLocalRandom.current().nextInt();

		assertEquals(n, adapter.fromJson(IuJson.number(n)));
		assertNull(adapter.fromJson(JsonValue.NULL));
		assertEquals(n, ((JsonNumber) adapter.toJson(n)).intValueExact());
		assertEquals(JsonValue.NULL, adapter.toJson(null));

		final var primitive = IuJsonAdapter.of(int.class);
		assertEquals(n, adapter.fromJson(IuJson.number(n)));
		assertThrows(NullPointerException.class, () -> primitive.fromJson(JsonValue.NULL));
		assertEquals(n, ((JsonNumber) adapter.toJson(n)).intValueExact());
		assertThrows(NullPointerException.class, () -> primitive.toJson(null));
	}

	@Test
	public void testLong() {
		final var adapter = IuJsonAdapter.of(Long.class);
		final var n = ThreadLocalRandom.current().nextLong();

		assertEquals(n, adapter.fromJson(IuJson.number(n)));
		assertNull(adapter.fromJson(JsonValue.NULL));
		assertEquals(n, ((JsonNumber) adapter.toJson(n)).longValueExact());
		assertEquals(JsonValue.NULL, adapter.toJson(null));

		final var primitive = IuJsonAdapter.of(long.class);
		assertEquals(n, adapter.fromJson(IuJson.number(n)));
		assertThrows(NullPointerException.class, () -> primitive.fromJson(JsonValue.NULL));
		assertEquals(n, ((JsonNumber) adapter.toJson(n)).longValueExact());
		assertThrows(NullPointerException.class, () -> primitive.toJson(null));
	}

	@Test
	public void testFloat() {
		final var adapter = IuJsonAdapter.of(Float.class);
		final var n = ThreadLocalRandom.current().nextFloat();

		assertEquals(n, adapter.fromJson(IuJson.number(n)));
		assertNull(adapter.fromJson(JsonValue.NULL));
		assertEquals(n, (float) ((JsonNumber) adapter.toJson(n)).doubleValue());
		assertEquals(JsonValue.NULL, adapter.toJson(null));

		final var primitive = IuJsonAdapter.of(float.class);
		assertEquals(n, adapter.fromJson(IuJson.number(n)));
		assertThrows(NullPointerException.class, () -> primitive.fromJson(JsonValue.NULL));
		assertEquals(n, (float) ((JsonNumber) adapter.toJson(n)).doubleValue());
		assertThrows(NullPointerException.class, () -> primitive.toJson(null));
	}

	@Test
	public void testDouble() {
		final var adapter = IuJsonAdapter.of(Double.class);
		final var n = (double) ThreadLocalRandom.current().nextDouble();

		assertEquals(n, adapter.fromJson(IuJson.number(n)));
		assertNull(adapter.fromJson(JsonValue.NULL));
		assertEquals(n, ((JsonNumber) adapter.toJson(n)).doubleValue());
		assertEquals(JsonValue.NULL, adapter.toJson(null));

		final var primitive = IuJsonAdapter.of(double.class);
		assertEquals(n, adapter.fromJson(IuJson.number(n)));
		assertThrows(NullPointerException.class, () -> primitive.fromJson(JsonValue.NULL));
		assertEquals(n, ((JsonNumber) adapter.toJson(n)).doubleValue());
		assertThrows(NullPointerException.class, () -> primitive.toJson(null));
	}

	@Test
	public void testBinary() {
		final var adapter = IuJsonAdapter.of(byte[].class);
		assertEquals(JsonValue.NULL, adapter.toJson(null));
		assertEquals(JsonValue.NULL, adapter.toJson(new byte[0]));
		assertNull(adapter.fromJson(JsonValue.NULL));

		final var data = new byte[Math.abs(ThreadLocalRandom.current().nextInt(Byte.MAX_VALUE + 1, Short.MAX_VALUE))];
		ThreadLocalRandom.current().nextBytes(data);
		final var text = IuText.base64Url(data);
		assertEquals(IuJson.string(text), adapter.toJson(data));
		assertArrayEquals(data, adapter.fromJson(IuJson.string(text)));
	}

	@Test
	public void testBigInteger() {
		final var adapter = IuJsonAdapter.of(BigInteger.class);
		assertEquals(JsonValue.NULL, adapter.toJson(null));
		assertNull(adapter.fromJson(JsonValue.NULL));

		final var data = new byte[Math.abs(ThreadLocalRandom.current().nextInt(Byte.MAX_VALUE + 1, Short.MAX_VALUE))];
		ThreadLocalRandom.current().nextBytes(data);
		final var bigint = new BigInteger(1, data);
		final var text = IuText.base64Url(data);
		assertEquals(IuJson.string(text), adapter.toJson(bigint));
		assertEquals(bigint, adapter.fromJson(IuJson.string(text)));
	}

	@Test
	public void testByteBuffer() {
		final var adapter = IuJsonAdapter.of(ByteBuffer.class);
		assertEquals(JsonValue.NULL, adapter.toJson(null));
		assertEquals(JsonValue.NULL, adapter.toJson(ByteBuffer.allocate(0)));
		assertNull(adapter.fromJson(JsonValue.NULL));

		final var data = new byte[Math.abs(ThreadLocalRandom.current().nextInt(Byte.MAX_VALUE + 1, Short.MAX_VALUE))];
		ThreadLocalRandom.current().nextBytes(data);

		final var arraybuf = ByteBuffer.wrap(data);
		final var text = IuText.base64Url(data);
		assertEquals(IuJson.string(text), adapter.toJson(arraybuf));
		assertEquals(JsonValue.NULL, adapter.toJson(arraybuf));
		assertArrayEquals(data, adapter.fromJson(IuJson.string(text)).array());

		final Answer<?> copy = a -> {
			System.arraycopy(data, 0, a.getArgument(0), 0, data.length);
			return null;
		};
		final var nonarray = mock(ByteBuffer.class);
		doAnswer(copy).when(nonarray).get(any(byte[].class));
		when(nonarray.hasRemaining()).thenReturn(true);
		when(nonarray.remaining()).thenReturn(data.length);
		assertEquals(IuJson.string(text), adapter.toJson(nonarray));

		final var offset = mock(ByteBuffer.class);
		doAnswer(copy).when(offset).get(any(byte[].class));
		when(offset.hasArray()).thenReturn(true);
		when(offset.arrayOffset()).thenReturn(1);
		when(offset.hasRemaining()).thenReturn(true);
		when(offset.remaining()).thenReturn(data.length);
		assertEquals(IuJson.string(text), adapter.toJson(offset));

		final var capped = mock(ByteBuffer.class);
		doAnswer(copy).when(capped).get(any(byte[].class));
		when(capped.hasArray()).thenReturn(true);
		when(capped.limit()).thenReturn(1);
		when(capped.hasRemaining()).thenReturn(true);
		when(capped.remaining()).thenReturn(data.length);
		assertEquals(IuJson.string(text), adapter.toJson(capped));
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
			B[] c;
		}
		final var adapter = IuJsonAdapter.<Number[]>of(A.class.getDeclaredField("c").getGenericType());
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

//	 * <li>{@link Enumeration}</li>
//	 * <li>{@link HashSet}</li>
//	 * <li>{@link Iterator}</li>
//	 * <li>{@link LinkedHashSet}</li>
//	 * <li>{@link NavigableSet}, as {@link TreeSet}</li>
//	 * <li>{@link Set}, as {@link LinkedHashSet}</li>
//	 * <li>{@link SortedSet}, as {@link NavigableSet}</li>
//	 * <li>{@link TreeSet}</li>
//	 * <li>{@link Stream}</li>
//	 * </ul>
//	 * </li>
//	 * <li>{@link #toJson(Object)} as {@link JsonObject}:
//	 * <ul>
//	 * <li>{@link LinkedHashMap}</li>
//	 * <li>{@link HashMap}</li>
//	 * <li>{@link Map}, as {@link LinkedHashMap}</li>
//	 * <li>{@link SortedMap}, as {@link TreeMap}</li>
//	 * <li>{@link Properties}, enforces values as {@link String}</li>
//	 * <li>{@link TreeMap}</li>
//	 * </ul>
//	 * </li>

}
