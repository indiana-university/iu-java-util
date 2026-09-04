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
package edu.iu.util.el;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayDeque;

import org.junit.jupiter.api.Test;

import jakarta.json.Json;
import jakarta.json.JsonValue;

@SuppressWarnings("javadoc")
public class ElTemplateTest {

	private static String render(JsonValue result) {
		final var buffer = new StringBuilder();
		final var evalStack = new ArrayDeque<ElContext>();
		final var context = new ElContext(JsonValue.EMPTY_JSON_OBJECT, "");
		new ElTemplate("before {value} after").apply(buffer, false, null, JsonValue.NULL, context, evalStack);

		final var expressionContext = evalStack.pop();
		expressionContext.setResult(result);
		expressionContext.complete();
		assertTrue(evalStack.isEmpty());
		return buffer.toString();
	}

	@Test
	public void testEmptyAndPlainContent() {
		final var buffer = new StringBuilder("prefix:");
		final var evalStack = new ArrayDeque<ElContext>();
		final var context = new ElContext(JsonValue.EMPTY_JSON_OBJECT, "");

		new ElTemplate(null).apply(buffer, false, null, JsonValue.NULL, context, evalStack);
		new ElTemplate("plain").apply(buffer, false, null, JsonValue.NULL, context, evalStack);

		assertEquals("prefix:plain", buffer.toString());
		assertTrue(evalStack.isEmpty());
	}

	@Test
	public void testEscapedAndNestedExpressions() {
		final var buffer = new StringBuilder();
		final var evalStack = new ArrayDeque<ElContext>();
		final var context = new ElContext(JsonValue.EMPTY_JSON_OBJECT, "");

		new ElTemplate("A \\{literal} {foo{bar}} B {<`{$.value}`} C") //
				.apply(buffer, false, null, JsonValue.NULL, context, evalStack);

		assertEquals("A {literal}  B  C", buffer.toString());
		assertEquals(2, evalStack.size());
		assertEquals("<`{$.value}`", evalStack.pop().getExpression());
		assertEquals("foo{bar}", evalStack.pop().getExpression());
	}

	@Test
	public void testApplySchedulesExpressionsInReverseOrder() {
		final var buffer = new StringBuilder("prefix:");
		final var evalStack = new ArrayDeque<ElContext>();
		final var context = new ElContext(JsonValue.EMPTY_JSON_OBJECT, "");
		final var key = Json.createValue(7);
		final var value = Json.createValue("context");

		new ElTemplate("{first}-{second}").apply(buffer, true, key, value, context, evalStack);

		final var second = evalStack.pop();
		assertEquals("second", second.getExpression());
		assertSame(context, second.getParent());
		assertEquals(true, second.isHead());
		assertEquals(key, second.getIndex());
		assertEquals(value, second.getContext());
		second.setResult(Json.createValue("two"));
		second.complete();

		final var first = evalStack.pop();
		assertEquals("first", first.getExpression());
		first.setResult(Json.createValue("one"));
		first.complete();

		assertEquals("prefix:one-two", buffer.toString());
	}

	@Test
	public void testApplyHandlesAtomicResults() {
		assertEquals("before text after", render(Json.createValue("text")));
		assertEquals("before 42 after", render(Json.createValue(42)));
		assertEquals("before  after", render(null));
		assertEquals("before  after", render(JsonValue.NULL));
		assertEquals("before  after", render(JsonValue.TRUE));
	}

	@Test
	public void testApplyRejectsStructuralResult() {
		final var result = Json.createObjectBuilder().add("foo", "bar").build();
		assertEquals("invalid result {\"foo\":\"bar\"}, expected a single value from value",
				assertThrows(IllegalArgumentException.class, () -> render(result)).getMessage());
	}

	@Test
	public void testMissingEndToken() {
		assertEquals("Missing end token '}': {value",
				assertThrows(IllegalStateException.class, () -> new ElTemplate("before {value")).getMessage());
		assertEquals("Missing end token '}': {$.foo?<`value`",
				assertThrows(IllegalStateException.class, () -> new ElTemplate("before {$.foo?<`value`")).getMessage());
		assertEquals("Missing end token '}': {$.foo?<`value` ",
				assertThrows(IllegalStateException.class, () -> new ElTemplate("before {$.foo?<`value` ")).getMessage());
		assertEquals("Missing closing '`': {$.foo?<`value}",
				assertThrows(IllegalStateException.class, () -> new ElTemplate("before {$.foo?<`value}")).getMessage());
		assertEquals("Missing end token '}': {value` ",
				assertThrows(IllegalStateException.class, () -> new ElTemplate("before {value` ")).getMessage());
	}
}
