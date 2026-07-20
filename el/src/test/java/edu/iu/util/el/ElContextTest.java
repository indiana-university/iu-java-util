package edu.iu.util.el;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import jakarta.json.Json;
import jakarta.json.JsonNumber;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;

@SuppressWarnings("javadoc")
public class ElContextTest {

	@Test
	public void testRootContext() {
		final var value = Json.createObjectBuilder().add("name", "root").build();
		final var context = new ElContext(value, "foo.bar");

		assertNull(context.getParent());
		assertSame(value, context.getRoot());
		assertSame(value, context.getContext());
		assertSame(value, context.getResult());
		assertSame(value, context.getThis());
		assertNull(context.getIndex());
		assertFalse(context.isHead());
		assertFalse(context.isRaw());
		assertFalse(context.isEmpty());
		assertEquals(0, context.getPosition());
		assertEquals("foo.bar", context.getExpression());
	}

	@Test
	public void testChildContext() {
		final var rootValue = Json.createObjectBuilder().add("root", true).build();
		final var parent = new ElContext(rootValue, "parent");
		final var value = Json.createValue("item");
		final var index = Json.createValue(2);
		final var child = new ElContext(parent, true, index, value, "child", null);

		assertSame(parent, child.getParent());
		assertSame(rootValue, child.getRoot());
		assertSame(value, child.getContext());
		assertSame(value, child.getThis());
		assertSame(index, child.getIndex());
		assertTrue(child.isHead());
	}

	@Test
	public void testExpressionPositionAndTrimming() {
		final var context = new ElContext(JsonValue.NULL, "abcdef");

		context.trim(2);
		assertEquals("cdef", context.getExpression());

		context.advancePosition(1);
		assertEquals(1, context.getPosition());
		assertEquals("def", context.getExpression());

		context.trim(2);
		assertEquals("f", context.getExpression());

		context.setPositionAtEnd();
		assertTrue(context.isEmpty());
		assertEquals("", context.getExpression());
	}

	@Test
	public void testNullExpressionIsEmpty() {
		final var context = new ElContext(JsonValue.NULL, null);

		assertTrue(context.isEmpty());
		assertEquals("", context.getExpression());
		assertEquals("EL", context.toString());
	}

	@Test
	public void testResultAndMatching() {
		final var context = new ElContext(JsonValue.NULL, "match");
		final var expected = Json.createValue("expected");

		context.setResult(expected);
		assertSame(expected, context.getResult());
		assertSame(expected, context.getThis());

		context.setMatchResult(expected);
		assertSame(JsonValue.TRUE, context.getResult());

		final var different = Json.createValue("different");
		context.setResult(different);
		assertSame(JsonValue.FALSE, context.getResult());
		assertSame(different, context.getThis());
	}

	@Test
	public void testMissingMatchOperandsDoNotMatch() {
		final var missingExpected = new ElContext(JsonValue.TRUE, "match");
		missingExpected.setMatchResult(null);
		assertSame(JsonValue.FALSE, missingExpected.getResult());

		final var missingResult = new ElContext(JsonValue.TRUE, "match");
		missingResult.setResult(null);
		missingResult.setMatchResult(JsonValue.TRUE);
		assertSame(JsonValue.FALSE, missingResult.getResult());
	}

	@Test
	public void testCompleteInvokesCallbackThenEscapesResult() {
		final var completed = new AtomicReference<JsonValue>();
		final var context = new ElContext(null, JsonValue.NULL, "expression", completed::set);
		final var result = Json.createValue("<strong>value</strong>");
		context.setResult(result);

		context.complete();

		assertSame(result, completed.get());
		assertEquals("&lt;strong&gt;value&lt;/strong&gt;", ((JsonString) context.getResult()).getString());
	}

	@Test
	public void testCompleteInvokesCallbackThenDoesntEscapeIntResult() {
		final var completed = new AtomicReference<JsonValue>();
		final var context = new ElContext(null, JsonValue.NULL, "expression", completed::set);
		final var result = Json.createValue(34);
		context.setResult(result);

		context.complete();

		assertSame(result, completed.get());
		assertEquals(34, ((JsonNumber) context.getResult()).intValue());
	}

	@Test
	public void testRawCompletionDoesNotEscapeResult() {
		final var context = new ElContext(JsonValue.NULL, "expression");
		final var result = Json.createValue("<strong>value</strong>");
		context.setResult(result);
		context.markAsRaw();

		context.complete();

		assertTrue(context.isRaw());
		assertSame(result, context.getResult());
	}

	@Test
	public void testToStringIncludesPositionAndParent() {
		final var parent = new ElContext(JsonValue.NULL, "parent");
		final var context = new ElContext(parent, JsonValue.NULL, "0123456789", null);
		context.advancePosition(6);

		assertEquals("EL expression \"0123456789\" at 6: \"12345[6]789\"\n"
				+ "Parent EL expression \"parent\" at 0: \"[p]arent\"", context.toString());
	}

	@Test
	public void testToStringAtEndPosition() {
		final var parent = new ElContext(JsonValue.NULL, "parent");
		final var context = new ElContext(parent, JsonValue.NULL, "0123456789", null);
		context.setPositionAtEnd();

		assertEquals("EL expression \"0123456789\" at 10: \"56789[]\"\n"
				+ "Parent EL expression \"parent\" at 0: \"[p]arent\"", context.toString());
	}
}
