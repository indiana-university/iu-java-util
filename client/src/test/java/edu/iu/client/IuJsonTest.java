package edu.iu.client;

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
import java.net.URI;
import java.net.http.HttpResponse;

import org.junit.jupiter.api.Test;

import edu.iu.IdGenerator;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.json.JsonString;
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
	public void testToJava() {
		final var id = IdGenerator.generateId();
		assertEquals(id, IuJson.toJava(IuJson.PROVIDER.createValue(id)));
		assertEquals(34, IuJson.<Number>toJava(IuJson.PROVIDER.createValue(new BigDecimal("34"))).intValue());
		assertTrue(IuJson.<Boolean>toJava(JsonValue.TRUE));
		assertFalse(IuJson.<Boolean>toJava(JsonValue.FALSE));
		assertNull(IuJson.toJava(JsonValue.NULL));
		assertThrows(IllegalArgumentException.class, () -> IuJson.toJava(null));
	}

	@Test
	public void testAsText() {
		final var id = IdGenerator.generateId();
		assertEquals(id, IuJson.asText(IuJson.PROVIDER.createValue(id)));
		assertEquals("34", IuJson.asText(IuJson.PROVIDER.createValue(new BigDecimal("34"))));
		assertEquals("true", IuJson.asText(JsonValue.TRUE));
		assertEquals("false", IuJson.asText(JsonValue.FALSE));
		assertNull(IuJson.asText(JsonValue.NULL));
		assertThrows(IllegalArgumentException.class, () -> IuJson.asText(null));
	}

	@Test
	public void testToJson() {
		final var id = IdGenerator.generateId();
		assertEquals(IuJson.PROVIDER.createValue(id), IuJson.toJson(id));
		assertEquals(IuJson.PROVIDER.createValue(new BigDecimal("34")), IuJson.toJson(34));
		assertEquals(JsonValue.TRUE, IuJson.toJson(true));
		assertEquals(JsonValue.FALSE, IuJson.toJson(false));
		assertEquals(JsonValue.NULL, IuJson.toJson(null));
		assertThrows(IllegalArgumentException.class, () -> IuJson.toJson(this));
		assertSame(JsonValue.NULL, IuJson.toJson(JsonValue.NULL));
		assertInstanceOf(JsonArray.class, IuJson.toJson(IuJson.PROVIDER.createArrayBuilder()));
		assertInstanceOf(JsonObject.class, IuJson.toJson(IuJson.PROVIDER.createObjectBuilder()));
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
		IuJson.add(b, "id", IdGenerator::generateId);
		IdGenerator.verifyId(b.build().getString("id"), 100L);
	}

	@Test
	public void testAddBySupplierWithFilter() {
		final var b = IuJson.PROVIDER.createObjectBuilder();
		IuJson.add(b, n -> n.equals("foo"), "foo", () -> "bar");
		IuJson.add(b, n -> n.equals("foo"), "bar", () -> "baz");
		final var o = b.build();
		assertEquals(1, o.size());
		assertEquals("bar", IuJson.text(o, "foo"));
		assertNull(IuJson.text(o, "bar"));
	}

	@Test
	public void testAddBySupplierWithFilterAndTransform() {
		final var b = IuJson.PROVIDER.createObjectBuilder();
		IuJson.add(b, n -> true, "foo", () -> URI.create("foo://bar"), u -> IuJson.PROVIDER.createValue(u.toString()));
		IuJson.add(b, n -> true, "bar", () -> null, u -> IuJson.PROVIDER.createValue(u.toString()));
		final var o = b.build();
		assertEquals(1, o.size());
		assertEquals("foo://bar", IuJson.text(o, "foo"));
		assertNull(IuJson.text(o, "bar"));
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
		IuJson.add(b, IdGenerator::generateId);
		IdGenerator.verifyId(b.build().getString(0), 100L);
	}

	@Test
	public void testAddToArrayBySupplierWithTransform() {
		final var b = IuJson.PROVIDER.createArrayBuilder();
		IuJson.add(b, () -> URI.create("foo://bar"), u -> IuJson.PROVIDER.createValue(u.toString()));
		IuJson.add(b, () -> null, u -> IuJson.PROVIDER.createValue(u.toString()));
		final var a = b.build();
		assertEquals(1, a.size());
		assertEquals("foo://bar", a.getString(0));
	}

	@Test
	public void testTextDefault() {
		final var id = IdGenerator.generateId();
		assertEquals(id, IuJson.text(IuJson.PROVIDER.createObjectBuilder().build(), "id", id));
	}

	@Test
	public void testTextConversion() {
		final var s = "foo://bar";
		final var u = URI.create(s);
		final var o = IuJson.PROVIDER.createObjectBuilder().add("foo", s).build();
		assertEquals(u, IuJson.text(o, "foo", URI::create));
		assertEquals(u, IuJson.text(o, "foo", URI.create("foo://baz"), URI::create));
		assertEquals(u,
				IuJson.text(IuJson.PROVIDER.createObjectBuilder().addNull("foo").build(), "foo", u, URI::create));
		assertEquals(u, IuJson.text(IuJson.PROVIDER.createObjectBuilder().build(), "foo", u, URI::create));
	}

	@Test
	public void testGetConversion() {
		final var s = "foo://bar";
		final var u = URI.create(s);
		final var o = IuJson.PROVIDER.createObjectBuilder().add("foo", s).build();
		assertEquals(u, IuJson.get(o, "foo", v -> URI.create(((JsonString) v).getString())));
		assertEquals(s, IuJson.get(o, "foo", (String) null));
		assertEquals("baz", IuJson.get(o, "bar", "baz"));
	}

}
