package edu.iu.util.el;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.HashMap;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import jakarta.json.Json;
import jakarta.json.JsonException;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonStructure;
import jakarta.json.JsonValue;

@SuppressWarnings("javadoc")
public class JsonPointerTest {

	private static HashMap<String, JsonValue> expectedResults;
	private static JsonStructure json;

	@BeforeAll
	static void setUpBeforeClass() throws Exception {
		JsonObjectBuilder jsonBuilder = Json.createObjectBuilder();
		jsonBuilder.add("foo", Json.createArrayBuilder().add("bar").add("baz"));
		jsonBuilder.add("", 0);
		jsonBuilder.add("a/b", 1);
		jsonBuilder.add("c%d", 2);
		jsonBuilder.add("e^f", 3);
		jsonBuilder.add("g|h", 4);
		jsonBuilder.add("i\\\\j", 5);
		jsonBuilder.add("k\\\"l", 6);
		jsonBuilder.add(" ", 7);
		jsonBuilder.add("m~n", 8);
		json = jsonBuilder.build();

		expectedResults = new HashMap<String, JsonValue>();
		expectedResults.put("", json);
		expectedResults.put("/foo", Json.createArrayBuilder().add("bar").add("baz").build());
		expectedResults.put("/foo/0", Json.createValue("bar"));
		expectedResults.put("/", Json.createValue(0));
		expectedResults.put("/a~1b", Json.createValue(1));
		expectedResults.put("/c%d", Json.createValue(2));
		expectedResults.put("/e^f", Json.createValue(3));
		expectedResults.put("/g|h", Json.createValue(4));
		expectedResults.put("/i\\\\j", Json.createValue(5));
		expectedResults.put("/k\\\"l", Json.createValue(6));
		expectedResults.put("/ ", Json.createValue(7));
		expectedResults.put("/m~0n", Json.createValue(8));
	}

	@Test
	public void testCreatePointer() {
		// examples from the spec
		assertEquals("", JsonPointerImpl.create("").toString()); // will return the whole document
		assertEquals("/foo", JsonPointerImpl.create("/foo").toString());
		assertEquals("/foo/0", JsonPointerImpl.create("/foo/0").toString());
		assertEquals("/", JsonPointerImpl.create("/").toString());
		assertEquals("/a~1b", JsonPointerImpl.create("/a~1b").toString());
		assertEquals("/c%d", JsonPointerImpl.create("/c%d").toString());
		assertEquals("/e^f", JsonPointerImpl.create("/e^f").toString());
		assertEquals("/g|h", JsonPointerImpl.create("/g|h").toString());
		assertEquals("/i\\\\j", JsonPointerImpl.create("/i\\\\j").toString());
		assertEquals("/k\\\"l", JsonPointerImpl.create("/k\\\"l").toString());
		assertEquals("/ ", JsonPointerImpl.create("/ ").toString());
		assertEquals("/m~0n", JsonPointerImpl.create("/m~0n").toString());

		// other things to try
		assertEquals("/0", JsonPointerImpl.create("/0").toString());
		assertEquals("/foo/0/bar", JsonPointerImpl.create("/foo/0/bar").toString());

		// error cases
		assertEquals("Invalid JSON Pointer: null",
				assertThrows(IllegalArgumentException.class, () -> JsonPointerImpl.create(null)).getMessage());
		assertEquals("Invalid JSON Pointer: .",
				assertThrows(IllegalArgumentException.class, () -> JsonPointerImpl.create(".")).getMessage());
		assertEquals("Invalid JSON Pointer: " + "/`!$#@%^*()_-+=`,.~`",
				assertThrows(IllegalArgumentException.class, () -> JsonPointerImpl.create("/`!$#@%^*()_-+=`,.~`"))
						.getMessage());
	}

	@Test
	public void testContainsValue() {
		final var json = Json.createObjectBuilder().add("foo", "bar").build();

		final var emptyPointer = JsonPointerImpl.create("");
		assertEquals(true, emptyPointer.containsValue(json));

		final var existingPointer = JsonPointerImpl.create("/foo");
		assertEquals(true, existingPointer.containsValue(json));

		final var nonExistingPointer = JsonPointerImpl.create("/baz");
		assertEquals(false, nonExistingPointer.containsValue(json));
	}

	@Test
	public void testGetValue() {
		final var nonExistingPointer = JsonPointerImpl.create("/baz");
		assertEquals("Value does not exist: /baz",
				assertThrows(JsonException.class, () -> nonExistingPointer.getValue(json)).getMessage());

		for (var entry : expectedResults.entrySet()) {
			assertEquals(entry.getValue(), JsonPointerImpl.create(entry.getKey()).getValue(json));
		}
	}

}
