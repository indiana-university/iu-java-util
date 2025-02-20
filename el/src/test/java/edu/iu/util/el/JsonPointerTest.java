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

	private static HashMap<String, JsonValue> specExpectedResults;
	private static JsonStructure specJson;

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
		specJson = jsonBuilder.build();

		specExpectedResults = new HashMap<String, JsonValue>();
		specExpectedResults.put("", specJson);
		specExpectedResults.put("/foo", Json.createArrayBuilder().add("bar").add("baz").build());
		specExpectedResults.put("/foo/0", Json.createValue("bar"));
		specExpectedResults.put("/", Json.createValue(0));
		specExpectedResults.put("/a~1b", Json.createValue(1));
		specExpectedResults.put("/c%d", Json.createValue(2));
		specExpectedResults.put("/e^f", Json.createValue(3));
		specExpectedResults.put("/g|h", Json.createValue(4));
		specExpectedResults.put("/i\\\\j", Json.createValue(5));
		specExpectedResults.put("/k\\\"l", Json.createValue(6));
		specExpectedResults.put("/ ", Json.createValue(7));
		specExpectedResults.put("/m~0n", Json.createValue(8));
	}

	@Test
	public void testCreatePointer() {
		// examples from the spec
		assertEquals("", Json.createPointer("").toString()); // will return the whole document
		assertEquals("/foo", Json.createPointer("/foo").toString());
		assertEquals("/foo/0", Json.createPointer("/foo/0").toString());
		assertEquals("/", Json.createPointer("/").toString());
		assertEquals("/a~1b", Json.createPointer("/a~1b").toString());
		assertEquals("/c%d", Json.createPointer("/c%d").toString());
		assertEquals("/e^f", Json.createPointer("/e^f").toString());
		assertEquals("/g|h", Json.createPointer("/g|h").toString());
		assertEquals("/i\\\\j", Json.createPointer("/i\\\\j").toString());
		assertEquals("/k\\\"l", Json.createPointer("/k\\\"l").toString());
		assertEquals("/ ", Json.createPointer("/ ").toString());
		assertEquals("/m~0n", Json.createPointer("/m~0n").toString());

		// other things to try
		assertEquals("/0", Json.createPointer("/0").toString());
		assertEquals("/foo/0/bar", Json.createPointer("/foo/0/bar").toString());
		assertEquals("/0000", Json.createPointer("/0000").toString());

		// error cases
		assertEquals("Cannot invoke \"String.split(String, int)\" because \"jsonPointer\" is null",
				assertThrows(NullPointerException.class, () -> Json.createPointer(null)).getMessage());
		assertEquals("A non-empty JSON Pointer must begin with a '/'",
				assertThrows(JsonException.class, () -> Json.createPointer(".")).getMessage());
	}

	@Test
	public void testSpecialPointerTilde() {
		// According to the spec, using a literal '~' or '/' in a pointer requires
		// escaping
		// The built-in implementation doesn't seem to care about the ~, but it does
		// care about the /
		final var json = Json.createObjectBuilder().add("`!$#@%^*\\()_-+=`,.~`", JsonValue.TRUE).build();
		final var pointer = Json.createPointer("/`!$#@%^*\\()_-+=`,.~`");
		assertEquals(true, pointer.containsValue(json));
	}

	@Test
	public void testSpecialPointerSlash() {
		// According to the spec, using a literal '~' or '/' in a pointer requires
		// escaping
		// The built-in implementation doesn't seem to care about the ~, but it does
		// care about the /
		final var json = Json.createObjectBuilder().add("`!$#@%^*\\/()_-+=`,.~`", JsonValue.TRUE).build();
		final var pointer = Json.createPointer("/`!$#@%^*\\/()_-+=`,.~`");
		assertEquals(
				"The JSON Object '{\"`!$#@%^*\\\\/()_-+=`,.~`\":true}' contains no mapping for the name '`!$#@%^*\\'",
				assertThrows(JsonException.class, () -> pointer.containsValue(json)).getMessage());
	}

	@Test
	public void testContainsValueEmptyPointer() {
		final var json = Json.createObjectBuilder().add("foo", "bar").build();
		final var emptyPointer = Json.createPointer("");
		assertEquals(true, emptyPointer.containsValue(json));
	}

	@Test
	public void testContainsValueExistingPointer() {
		final var json = Json.createObjectBuilder().add("foo", "bar").build();
		final var existingPointer = Json.createPointer("/foo");
		assertEquals(true, existingPointer.containsValue(json));
	}

	@Test
	public void testContainsValueNonExistingPointer() {
		final var json = Json.createObjectBuilder().add("foo", "bar").build();
		final var nonExistingPointer = Json.createPointer("/baz");
		assertEquals(false, nonExistingPointer.containsValue(json));
	}

	@Test
	public void testGetValue() {
		// Spec-based valid pointer tests
		for (var entry : specExpectedResults.entrySet()) {
			assertEquals(entry.getValue(), Json.createPointer(entry.getKey()).getValue(specJson));
		}
	}

	@Test
	public void testGetValueNonExistingPointer() {
		final var nonExistingPointer = Json.createPointer("/baz");
		assertEquals("Non-existing name/value pair in the object for key baz",
				assertThrows(JsonException.class, () -> nonExistingPointer.getValue(specJson)).getMessage());
	}

	@Test
	public void testGetValueNonExistingArrayPointer() {
		final var nonExistingArrayPointer = Json.createPointer("/foo/2");
		assertEquals("An array item index is out of range. Index: 2, Size: 2",
				assertThrows(JsonException.class, () -> nonExistingArrayPointer.getValue(specJson)).getMessage());
	}

	@Test
	public void testGetValueArrayPointerStartsWithZeros() {
		final var invalidArrayPointerStartsWithZeros = Json.createPointer("/foo/00001");
		assertEquals(Json.createValue("baz"), invalidArrayPointerStartsWithZeros.getValue(specJson));
	}

	@Test
	public void testGetValueObjectPointerStartsWithZeros() {
		final var pointer = Json.createPointer("/foo/00001");
		final var json = Json.createObjectBuilder().add("foo", Json.createObjectBuilder().add("00001", "baz")).build();
		assertEquals(Json.createValue("baz"), pointer.getValue(json));
	}

	@Test
	public void testGetValueArrayPointerValueTooBig() {
		final var pointerString = "/foo/" + Long.MAX_VALUE;
		final var invalidArrayPointerValueTooBig = Json.createPointer(pointerString);
		assertEquals("Illegal integer format, was '" + Long.MAX_VALUE + "'",
				assertThrows(JsonException.class, () -> invalidArrayPointerValueTooBig.getValue(specJson))
						.getMessage());
	}

	@Test
	public void testGetValueInvalidContext() {
		final var validPointer = Json.createPointer("/foo/0/");
		assertEquals("The reference value in a JSON Pointer must be a JSON Object or a JSON Array, was 'STRING'",
				assertThrows(JsonException.class, () -> validPointer.getValue(specJson)).getMessage());
	}

	@Test
	public void testGetValueNullTarget() {
		final var pointer = Json.createPointer("/foo");
		assertEquals("Cannot invoke \"jakarta.json.JsonValue.getValueType()\" because \"value\" is null",
				assertThrows(NullPointerException.class, () -> pointer.getValue(null)).getMessage());
	}

	@Test
	public void testGetValueDeeplyNestedWithReplaceSequences() {
		final var pointer = Json.createPointer("/foo/~0~1/0/~1bar~01");
		final var json = Json.createObjectBuilder()
				.add("foo", Json.createObjectBuilder().add("~/",
						Json.createArrayBuilder().add(Json.createObjectBuilder().add("/bar~1", JsonValue.NULL))))
				.build();
		assertEquals(JsonValue.NULL, pointer.getValue(json));
	}

	@Test
	public void testReplaceNullTarget() {
		final var pointer = Json.createPointer("");
		final var value = Json.createValue("qux");
		assertEquals("The root value only allows adding a JSON object or array",
				assertThrows(JsonException.class, () -> pointer.replace(null, value)).getMessage());
	}

	@Test
	public void testReplaceValueDoesNotExist() {
		final var pointer = Json.createPointer("/baz");
		final var value = Json.createValue("qux");
		assertEquals("Non-existing name/value pair in the object for key baz",
				assertThrows(JsonException.class, () -> pointer.replace(specJson, value)).getMessage());
	}

	@Test
	public void testReplaceValueIsTarget() {
		final var pointer = Json.createPointer("");
		final var value = Json.createValue("qux");
		assertEquals("The root value only allows adding a JSON object or array",
				assertThrows(JsonException.class, () -> pointer.replace(specJson, value)).getMessage());
	}

	@Test
	public void testReplaceTopLevelStringKeyWithStringValue() {
		final var pointer = Json.createPointer("/foo");
		final var json = Json.createObjectBuilder().add("foo", "bar").build();
		final var expectedJson = Json.createObjectBuilder().add("foo", "qux").build();
		final var value = Json.createValue("qux");
		final var result = pointer.replace(json, value);
		assertEquals(expectedJson, result);
	}

	@Test
	public void testReplaceSubLevelArrayKeyWithStringValue() {
		final var pointer = Json.createPointer("/foo/0");
		final var json = Json.createObjectBuilder().add("foo", Json.createArrayBuilder().add("bar").add("baz")).build();
		final var expectedJson = Json.createObjectBuilder().add("foo", Json.createArrayBuilder().add("qux").add("baz"))
				.build();
		final var value = Json.createValue("qux");
		final var result = pointer.replace(json, value);
		assertEquals(expectedJson, result);
	}

	@Test
	public void testReplaceSubLevelArrayKeyWithNumberValue() {
		final var pointer = Json.createPointer("/foo/1");
		final var json = Json.createObjectBuilder().add("foo", Json.createArrayBuilder().add("bar").add("baz")).build();
		final var expectedJson = Json.createObjectBuilder()
				.add("foo", Json.createArrayBuilder().add("bar").add(Json.createValue(42))).build();
		final var value = Json.createValue(42);
		final var result = pointer.replace(json, value);
		assertEquals(expectedJson, result);
	}

	@Test
	public void testReplaceTopLevelStringValueWithStringValue() {
		final var pointer = Json.createPointer("/foo/");
		final var json = Json.createObjectBuilder().add("foo", "bar").build();
		final var value = Json.createValue("qux");
		assertEquals("The reference value in a JSON Pointer must be a JSON Object or a JSON Array, was 'STRING'",
				assertThrows(JsonException.class, () -> pointer.replace(json, value)).getMessage());
	}

	@Test
	public void testReplacesubLevelObjectEmptyStringValueWithStringValue() {
		final var pointer = Json.createPointer("/foo/");
		final var json = Json.createObjectBuilder().add("foo", Json.createObjectBuilder().add("", "bar")).build();
		final var expectedJson = Json.createObjectBuilder().add("foo", Json.createObjectBuilder().add("", "qux")).build();
		final var value = Json.createValue("qux");
		assertEquals(expectedJson, pointer.replace(json, value));
	}

	@Test
	public void testReplaceTopLevelStringValueWithBooleanValue() {
		final var pointer = Json.createPointer("/foo");
		final var json = Json.createObjectBuilder().add("foo", "bar").build();
		final var expectedJson = Json.createObjectBuilder().add("foo", JsonValue.TRUE).build();
		final var value = JsonValue.TRUE;
		assertEquals(expectedJson, pointer.replace(json, value));
	}

	@Test
	public void testReplaceSubLevelArrayValueWithArrayValue() {
		final var pointer = Json.createPointer("/foo/0");
		final var json = Json.createObjectBuilder().add("foo", Json.createArrayBuilder().add("bar").add("baz")).build();
		final var expectedJson = Json.createObjectBuilder()
				.add("foo", Json.createArrayBuilder().add(Json.createArrayBuilder().add("qux")).add("baz")).build();
		final var value = Json.createArrayBuilder().add("qux").build();
		final var result = pointer.replace(json, value);
		assertEquals(expectedJson, result);
	}

	@Test
	public void testReplaceSubLevelObjectValueWithObjectValue() {
		final var pointer = Json.createPointer("/foo/bar");
		final var json = Json.createObjectBuilder().add("foo", Json.createObjectBuilder().add("bar", "baz")).build();
		final var expectedJson = Json.createObjectBuilder()
				.add("foo", Json.createObjectBuilder().add("bar", Json.createObjectBuilder().add("bif", "bam")))
				.build();
		final var value = Json.createObjectBuilder().add("bif", "bam").build();
		final var result = pointer.replace(json, value);
		assertEquals(expectedJson, result);
	}

	@Test
	public void testAddNullTarget() {
		final var pointer = Json.createPointer("");
		final var value = Json.createValue("qux");
		assertEquals("The root value only allows adding a JSON object or array",
				assertThrows(JsonException.class, () -> pointer.add(null, value)).getMessage());
	}

	@Test
	public void testAddValueDoesNotExist() {
		final var pointer = Json.createPointer("/baz");
		final var json = Json.createObjectBuilder().add("foo", "bar").build();
		final var expectedJson = Json.createObjectBuilder().add("foo", "bar").add("baz", "qux").build();
		final var value = Json.createValue("qux");
		assertEquals(expectedJson, pointer.add(json, value));
	}

	@Test
	public void testAddValueIsTarget() {
		final var pointer = Json.createPointer("");
		final var value = Json.createValue("qux");
		assertEquals("The root value only allows adding a JSON object or array",
				assertThrows(JsonException.class, () -> pointer.add(specJson, value)).getMessage());
	}

	@Test
	public void testAddReplaceTopLevelStringKeyWithStringValue() {
		final var pointer = Json.createPointer("/foo");
		final var json = Json.createObjectBuilder().add("foo", "bar").build();
		final var expectedJson = Json.createObjectBuilder().add("foo", "qux").build();
		final var value = Json.createValue("qux");
		final var result = pointer.add(json, value);
		assertEquals(expectedJson, result);
	}

	@Test
	public void testAddToBeginningSubLevelArrayKeyWithStringValue() {
		final var pointer = Json.createPointer("/foo/0");
		final var json = Json.createObjectBuilder().add("foo", Json.createArrayBuilder().add("bar").add("baz")).build();
		final var expectedJson = Json.createObjectBuilder()
				.add("foo", Json.createArrayBuilder().add("qux").add("bar").add("baz")).build();
		final var value = Json.createValue("qux");
		final var result = pointer.add(json, value);
		assertEquals(expectedJson, result);
	}

	@Test
	public void testAddToEndSubLevelArrayKeyWithStringValue() {
		final var pointer = Json.createPointer("/foo/2");
		final var json = Json.createObjectBuilder().add("foo", Json.createArrayBuilder().add("bar").add("baz")).build();
		final var expectedJson = Json.createObjectBuilder()
				.add("foo", Json.createArrayBuilder().add("bar").add("baz").add("qux")).build();
		final var value = Json.createValue("qux");
		final var result = pointer.add(json, value);
		assertEquals(expectedJson, result);
	}

	@Test
	public void testAddToMiddleSubLevelArrayKeyWithNumberValue() {
		final var pointer = Json.createPointer("/foo/1");
		final var json = Json.createObjectBuilder().add("foo", Json.createArrayBuilder().add("bar").add("baz")).build();
		final var expectedJson = Json.createObjectBuilder()
				.add("foo", Json.createArrayBuilder().add("bar").add(Json.createValue(42)).add("baz")).build();
		final var value = Json.createValue(42);
		final var result = pointer.add(json, value);
		assertEquals(expectedJson, result);
	}

	@Test
	public void testAddNoReplaceSubLevelArrayKeyWithNumberValue() {
		final var pointer = Json.createPointer("/foo/2");
		final var json = Json.createObjectBuilder().add("foo", Json.createArrayBuilder().add("bar").add("baz")).build();
		final var expectedJson = Json.createObjectBuilder()
				.add("foo", Json.createArrayBuilder().add("bar").add("baz").add(Json.createValue(42))).build();
		final var value = Json.createValue(42);
		final var result = pointer.add(json, value);
		assertEquals(expectedJson, result);
	}

	@Test
	public void testAddReplaceTopLevelStringValueWithStringValue() {
		final var pointer = Json.createPointer("/foo/");
		final var json = Json.createObjectBuilder().add("foo", "bar").build();
		final var value = Json.createValue("qux");
		assertEquals("The reference value in a JSON Pointer must be a JSON Object or a JSON Array, was 'STRING'",
				assertThrows(JsonException.class, () -> pointer.add(json, value)).getMessage());
	}

	@Test
	public void testAddReplaceTopLevelObjectValueWithStringValue() {
		final var pointer = Json.createPointer("/foo/");
		final var json = Json.createObjectBuilder().add("foo", Json.createObjectBuilder().add("bar", "baz")).build();
		final var expectedJson = Json.createObjectBuilder()
				.add("foo", Json.createObjectBuilder().add("bar", "baz").add("", "qux")).build();
		final var value = Json.createValue("qux");
		assertEquals(expectedJson, pointer.add(json, value));
	}

	@Test
	public void testAddReplaceTopLevelArrayValueNoIndex() {
		final var pointer = Json.createPointer("/foo/");
		final var json = Json.createObjectBuilder().add("foo", Json.createArrayBuilder().add("bar").add("baz")).build();
		final var expectedJson = Json.createObjectBuilder()
				.add("foo", Json.createArrayBuilder().add("bar").add("baz").add("qux")).build();
		final var value = Json.createValue("qux");
		assertEquals("Array index format error, was ''",
				assertThrows(JsonException.class, () -> pointer.add(json, value)).getMessage());
	}

	@Test
	public void testAddReplaceTopLevelArrayValueWithStringValueUsingDash() {
		final var pointer = Json.createPointer("/foo/-");
		final var json = Json.createObjectBuilder().add("foo", Json.createArrayBuilder().add("bar").add("baz")).build();
		final var expectedJson = Json.createObjectBuilder()
				.add("foo", Json.createArrayBuilder().add("bar").add("baz").add("qux")).build();
		final var value = Json.createValue("qux");
		assertEquals(expectedJson, pointer.add(json, value));
	}

	@Test
	public void testAddReplaceTopLevelStringValueWithBooleanValue() {
		final var pointer = Json.createPointer("/foo");
		final var json = Json.createObjectBuilder().add("foo", "bar").build();
		final var expectedJson = Json.createObjectBuilder().add("foo", JsonValue.TRUE).build();
		final var value = JsonValue.TRUE;
		assertEquals(expectedJson, pointer.add(json, value));
	}

	@Test
	public void testAddNoReplaceTopLevelStringValueWithBooleanValue() {
		final var pointer = Json.createPointer("/baz");
		final var json = Json.createObjectBuilder().add("foo", "bar").build();
		final var expectedJson = Json.createObjectBuilder().add("foo", "bar").add("baz", JsonValue.TRUE).build();
		final var value = JsonValue.TRUE;
		assertEquals(expectedJson, pointer.add(json, value));
	}

	@Test
	public void testAddToMiddleSubLevelArrayValueWithArrayValue() {
		final var pointer = Json.createPointer("/foo/1");
		final var json = Json.createObjectBuilder().add("foo", Json.createArrayBuilder().add("bar").add("baz")).build();
		final var expectedJson = Json.createObjectBuilder()
				.add("foo", Json.createArrayBuilder().add("bar").add(Json.createArrayBuilder().add("qux")).add("baz"))
				.build();
		final var value = Json.createArrayBuilder().add("qux").build();
		final var result = pointer.add(json, value);
		assertEquals(expectedJson, result);
	}

	@Test
	public void testAddReplaceArrayValueWithArrayValue() {
		final var pointer = Json.createPointer("/foo");
		final var json = Json.createObjectBuilder().add("foo", Json.createArrayBuilder().add("bar").add("baz")).build();
		final var expectedJson = Json.createObjectBuilder().add("foo", Json.createArrayBuilder().add("qux")).build();
		final var value = Json.createArrayBuilder().add("qux").build();
		final var result = pointer.add(json, value);
		assertEquals(expectedJson, result);
	}

	@Test
	public void testAddReplaceSubLevelObjectValueWithObjectValue() {
		final var pointer = Json.createPointer("/foo/bar");
		final var json = Json.createObjectBuilder().add("foo", Json.createObjectBuilder().add("bar", "baz")).build();
		final var expectedJson = Json.createObjectBuilder()
				.add("foo", Json.createObjectBuilder().add("bar", Json.createObjectBuilder().add("bif", "bam")))
				.build();
		final var value = Json.createObjectBuilder().add("bif", "bam").build();
		final var result = pointer.add(json, value);
		assertEquals(expectedJson, result);
	}

	@Test
	public void testAddNoReplaceSubLevelObjectValueWithObjectValue() {
		final var pointer = Json.createPointer("/foo/bim");
		final var json = Json.createObjectBuilder().add("foo", Json.createObjectBuilder().add("bar", "baz")).build();
		final var expectedJson = Json.createObjectBuilder().add("foo",
				Json.createObjectBuilder().add("bar", "baz").add("bim", Json.createObjectBuilder().add("bif", "bam")))
				.build();
		final var value = Json.createObjectBuilder().add("bif", "bam").build();
		final var result = pointer.add(json, value);
		assertEquals(expectedJson, result);
	}

	@Test
	public void testRemoveNullTargetRootReference() {
		final var pointer = Json.createPointer("");
		assertEquals("The JSON value at the root cannot be removed",
				assertThrows(JsonException.class, () -> pointer.remove(null)).getMessage());
	}

	@Test
	public void testRemoveNullTargetValidPointer() {
		final var pointer = Json.createPointer("/foo");
		assertEquals("Cannot invoke \"jakarta.json.JsonValue.getValueType()\" because \"value\" is null",
				assertThrows(NullPointerException.class, () -> pointer.remove(null)).getMessage());
	}

	@Test
	public void testRemoveValueDoesNotExist() {
		final var pointer = Json.createPointer("/baz");
		assertEquals("Non-existing name/value pair in the object for key baz",
				assertThrows(JsonException.class, () -> pointer.remove(specJson)).getMessage());
	}

	@Test
	public void testRemoveReferenceIsTarget() {
		final var pointer = Json.createPointer("");
		assertEquals("The JSON value at the root cannot be removed",
				assertThrows(JsonException.class, () -> pointer.remove(specJson)).getMessage());
	}

	@Test
	public void testRemoveTopLevelStringKey() {
		final var pointer = Json.createPointer("/foo");
		final var json = Json.createObjectBuilder().add("foo", "bar").build();
		final var expectedJson = Json.createObjectBuilder().build();
		assertEquals(expectedJson, pointer.remove(json));
	}

	@Test
	public void testRemoveSubLevelArrayKeyFirst() {
		final var pointer = Json.createPointer("/foo/0");
		final var json = Json.createObjectBuilder().add("foo", Json.createArrayBuilder().add("bar").add("baz")).build();
		final var expectedJson = Json.createObjectBuilder().add("foo", Json.createArrayBuilder().add("baz")).build();
		assertEquals(expectedJson, pointer.remove(json));
	}

	@Test
	public void testRemoveSubLevelArrayKeyLast() {
		final var pointer = Json.createPointer("/foo/1");
		final var json = Json.createObjectBuilder().add("foo", Json.createArrayBuilder().add("bar").add("baz")).build();
		final var expectedJson = Json.createObjectBuilder().add("foo", Json.createArrayBuilder().add("bar")).build();
		assertEquals(expectedJson, pointer.remove(json));
	}

	@Test
	public void testRemoveSubLevelArrayKeyDash() {
		final var pointer = Json.createPointer("/foo/-");
		final var json = Json.createObjectBuilder().add("foo", Json.createArrayBuilder().add("bar").add("baz")).build();
		assertEquals("An array item index is out of range. Index: -1, Size: 2",
				assertThrows(JsonException.class, () -> pointer.remove(json)).getMessage());
	}

	@Test
	public void testRemoveStringReference() {
		final var pointer = Json.createPointer("/foo/");
		final var json = Json.createObjectBuilder().add("foo", "bar").build();
		assertEquals("The reference value in a JSON Pointer must be a JSON Object or a JSON Array, was 'STRING'",
				assertThrows(JsonException.class, () -> pointer.remove(json)).getMessage());
	}

	@Test
	public void testRemoveSubLevelObjectValue() {
		final var pointer = Json.createPointer("/foo/bar");
		final var json = Json.createObjectBuilder().add("foo", Json.createObjectBuilder().add("bar", "baz")).build();
		final var expectedJson = Json.createObjectBuilder().add("foo", Json.createObjectBuilder()).build();
		final var result = pointer.remove(json);
		assertEquals(expectedJson, result);
	}

	@Test
	public void testRemoveDeeplyNestedWithReplaceSequences() {
		final var pointer = Json.createPointer("/foo/~0~1/0/~1bar~01");
		final var json = Json.createObjectBuilder()
				.add("foo", Json.createObjectBuilder().add("~/",
						Json.createArrayBuilder().add(Json.createObjectBuilder().add("/bar~1", JsonValue.NULL))))
				.build();
		final var expectedJson = Json.createObjectBuilder()
				.add("foo",
						Json.createObjectBuilder().add("~/", Json.createArrayBuilder().add(Json.createObjectBuilder())))
				.build();
		final var result = pointer.remove(json);
		assertEquals(expectedJson, result);
	}

}
