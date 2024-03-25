package edu.iu.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.http.HttpResponse;

import org.junit.jupiter.api.Test;

import edu.iu.IdGenerator;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonValue;

@SuppressWarnings("javadoc")
public class IuJsonTest {

	@SuppressWarnings("unchecked")
	@Test
	public void testParse() {
		final var response = mock(HttpResponse.class);
		when(response.body()).thenReturn(new ByteArrayInputStream("null".getBytes()));
		assertEquals(JsonValue.NULL, IuJson.parse(response));
		assertEquals(JsonValue.NULL, IuJson.parse("null"));
	}

	@Test
	public void testAddByValue() {
		final var b = IuJson.PROVIDER.createObjectBuilder();
		final var id = IdGenerator.generateId();
		IuJson.add(b, "id", id);
		assertEquals(id, IuJson.get(b.build(), "id"));
	}

	@Test
	public void testAddBySupplier() {
		final var b = IuJson.PROVIDER.createObjectBuilder();
		IuJson.add(b, "id", IdGenerator::generateId, IuJsonAdapter.basic());
		IdGenerator.verifyId(b.build().getString("id"), 100L);
	}

	@Test
	public void testAddBySupplierWithFilter() {
		final var b = IuJson.PROVIDER.createObjectBuilder();
		IuJson.add(b, "foo", "bar", () -> true);
		IuJson.add(b, "bar", "baz", () -> false);
		final var o = b.build();
		assertEquals(1, o.size());
		assertEquals("bar", IuJson.get(o, "foo"));
		assertNull(IuJson.get(o, "bar"));
	}

	@Test
	public void testAddBySupplierWithFilterAndTransform() {
		final var b = IuJson.PROVIDER.createObjectBuilder();
		IuJson.add(b, "foo", () -> URI.create("foo://bar"), () -> true, IuJsonAdapter.of(URI.class));
		IuJson.add(b, "bar", () -> null, () -> true, IuJsonAdapter.of(URI.class));
		final var o = b.build();
		assertEquals(1, o.size());
		assertEquals("foo://bar", IuJson.get(o, "foo"));
		assertNull(IuJson.get(o, "bar"));
	}

	@Test
	public void testAddToArrayByValue() {
		final var b = IuJson.PROVIDER.createArrayBuilder();
		final var id = IdGenerator.generateId();
		IuJson.add(b, id);
		assertEquals(id, b.build().getString(0));
	}

	@Test
	public void testAddToArrayBySupplier() {
		final var b = IuJson.PROVIDER.createArrayBuilder();
		IuJson.add(b, IdGenerator::generateId, IuJsonAdapter.basic());
		IdGenerator.verifyId(b.build().getString(0), 100L);
	}

	@Test
	public void testAddToArrayBySupplierWithTransform() {
		final var b = IuJson.PROVIDER.createArrayBuilder();
		IuJson.add(b, () -> URI.create("foo://bar"), IuJsonAdapter.of(URI.class));
		IuJson.add(b, () -> null, IuJsonAdapter.of(URI.class));
		final var a = b.build();
		assertEquals(1, a.size());
		assertEquals("foo://bar", a.getString(0));
	}

	@Test
	public void testSerialize() throws UnsupportedEncodingException {
		final var out = new ByteArrayOutputStream();
		IuJson.serialize(JsonValue.NULL, out);
		assertEquals("null", new String(out.toByteArray(), "UTF-8"));
	}

	@Test
	public void testArrayFactory() {
		final var a = IuJson.array();
		assertInstanceOf(JsonArrayBuilder.class, a);
		a.add(1);
		final var a2 = IuJson.array(a.build());
		assertInstanceOf(JsonArrayBuilder.class, a);
		assertEquals(1, a2.build().getInt(0));
	}

	@Test
	public void testObjectFactory() {
		final var o = IuJson.object();
		assertInstanceOf(JsonObjectBuilder.class, o);
		o.add("a", 1);
		final var o2 = IuJson.object(o.build());
		assertInstanceOf(JsonObjectBuilder.class, o);
		assertEquals(1, o2.build().getInt("a"));
	}
}
