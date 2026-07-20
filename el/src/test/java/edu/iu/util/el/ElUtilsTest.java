package edu.iu.util.el;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Modifier;

import org.junit.jupiter.api.Test;

import jakarta.json.Json;
import jakarta.json.JsonValue;

@SuppressWarnings("javadoc")
public class ElUtilsTest {

	@Test
	public void testUtilityClass() throws Exception {
		final var constructor = ElUtils.class.getDeclaredConstructor();
		assertTrue(Modifier.isPrivate(constructor.getModifiers()));
		constructor.setAccessible(true);
		constructor.newInstance();

		assertEquals('\0', ElUtils.ANY);
		assertEquals('\\', ElUtils.ESC_TOKEN);
		assertEquals("", ElUtils.EMPTY.getString());
		for (int i = 1; i < ElUtils.CONTROL_CHARS.length; i++)
			assertTrue(ElUtils.CONTROL_CHARS[i - 1] < ElUtils.CONTROL_CHARS[i]);
	}

	@Test
	public void testGetIndexFromFindsChar() {
		assertEquals(3, ElUtils.getIndexFrom("abc*def", '*', 0));
	}

	@Test
	public void testGetIndexFromStartsFromOffset() {
		assertEquals(7, ElUtils.getIndexFrom("abc*def*ghi", '*', 4));
	}

	@Test
	public void testGetIndexFromReturnsMinusOneWhenNotFound() {
		assertEquals(-1, ElUtils.getIndexFrom("abcdef", '*', 0));
	}

	@Test
	public void testGetIndexFromReturnsMinusOneWhenBeyondEnd() {
		assertEquals(-1, ElUtils.getIndexFrom("abc*def", '*', 4));
	}

	@Test
	public void testGetIndexFromAnyFindsFirstControlChar() {
		// 'a' at 0, '@' at 3 is first control char
		assertEquals(3, ElUtils.getIndexFrom("abc@def", ElUtils.ANY, 0));
	}

	@Test
	public void testGetIndexFromAnyReturnsMinusOneWithoutControlCharacter() {
		assertEquals(-1, ElUtils.getIndexFrom("abcdef", ElUtils.ANY, 0));
	}

	@Test
	public void testGetIndexFromSkipsInsideInlineTemplate() {
		// "<`*`}" — the '*' at index 2 is inside an inline template block; no match at
		// depth 0
		assertEquals(-1, ElUtils.getIndexFrom("<`*`}", '*', 0));
	}

	@Test
	public void testGetIndexFromSkipsEolComment() {
		// "<`*`foo" — the '*' at index 2 is inside an inline template block; no match
		// at depth 0
		assertEquals(-1, ElUtils.getIndexFrom("<`*`foo", '*', 0));
	}

	@Test
	public void testGetIndexFromLeadingTick() {
		assertEquals(1, ElUtils.getIndexFrom("`*", '*', 0));
	}

	@Test
	public void testGetIndexFromLazyTick() {
		assertEquals(2, ElUtils.getIndexFrom("a`*", '*', 0));
	}

	@Test
	public void testGetIndexFromFindsCharAfterInlineTemplate() {
		// "<`inner`}*" — '*' at index 9 is outside the inline template block
		assertEquals(9, ElUtils.getIndexFrom("<`inner`}*", '*', 0));
	}

	@Test
	public void testGetIndexFromHandlesNestedInlineTemplates() {
		// "<`<`inner`}`}*rest" — nested templates both close properly; '*' at index 13
		// is found
		assertEquals(13, ElUtils.getIndexFrom("<`<`inner`}`}*rest", '*', 0));
	}

	@Test
	public void testGetIndexFromTerminalBacktickClosesDepth() {
		// "<`*`" — '`' at end of string is terminal and closes the block; '*' inside is
		// skipped
		assertEquals(-1, ElUtils.getIndexFrom("<`*`", '*', 0));
	}

	@Test
	public void testGetCloseBracket() {
		assertEquals(3, ElUtils.getCloseBracket("abc]def", 0));
		assertEquals(7, ElUtils.getCloseBracket("a[b[c]d]e", 2));
		assertEquals(-1, ElUtils.getCloseBracket("a[b[c]d", 2));
		assertEquals(-1, ElUtils.getCloseBracket("abcdef", 0));
	}

	@Test
	public void testSelectWithNullStringAndNumberPathElements() {
		final var selected = Json.createArrayBuilder().add("zero").add("one").build();

		assertSame(selected, ElUtils.select(selected, (JsonValue) null));
		assertEquals(Json.createValue("one"), ElUtils.select(selected, Json.createValue("1")));
		assertEquals(Json.createValue("one"), ElUtils.select(selected, Json.createValue(1)));
	}

	@Test
	public void testSelectRejectsNonAtomicPathElement() {
		final var pathElement = Json.createObjectBuilder().add("path", "value").build();
		assertEquals("invalid subexpression result {\"path\":\"value\"}, expected a single value",
				assertThrows(IllegalArgumentException.class,
						() -> ElUtils.select(JsonValue.EMPTY_JSON_OBJECT, pathElement)).getMessage());
	}

	@Test
	public void testSelectPropertyFromObjectAndArray() {
		final var object = Json.createObjectBuilder().add("name", "value").build();
		final var array = Json.createArrayBuilder().add("zero").add(object).build();

		assertSame(object, ElUtils.select(object, ""));
		assertEquals(Json.createValue("value"), ElUtils.select(object, "name"));
		assertNull(ElUtils.select(object, "missing"));
		assertSame(object, ElUtils.select(array, "1"));
	}

	@Test
	public void testSelectJsonPointerFromObjectAndArray() {
		final var nested = Json.createObjectBuilder().add("name", "value").build();
		final var object = Json.createObjectBuilder().add("nested", nested).build();
		final var array = Json.createArrayBuilder().add(JsonValue.NULL).add(nested).build();

		assertEquals(Json.createValue("value"), ElUtils.select(object, "/nested/name"));
		assertEquals(Json.createValue("value"), ElUtils.select(array, "/1/name"));
	}

	@Test
	public void testSelectRejectsScalarSelection() {
		final var selected = Json.createValue("scalar");

		assertEquals("expected object or array for property 'name', found \"scalar\"",
				assertThrows(IllegalArgumentException.class, () -> ElUtils.select(selected, "name")).getMessage());
		assertEquals("expected object or array for pointer /name, found \"scalar\"",
				assertThrows(IllegalArgumentException.class, () -> ElUtils.select(selected, "/name")).getMessage());
	}

}
