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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.logging.Level;


import org.junit.jupiter.api.Test;

import edu.iu.IuException;
import edu.iu.client.IuJson;
import edu.iu.client.IuJsonAdapter;
import edu.iu.test.IuTestLogger;
import jakarta.json.Json;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonValue;

@SuppressWarnings("javadoc")
public class ElTest {

	private static final Path TEST_RESOURCES = IuException
			.unchecked(() -> Path.of(ElTest.class.getProtectionDomain().getCodeSource().getLocation().toURI()));

	private static String readResource(String resource) {
		return IuException.unchecked(() -> {
			final var resourcePath = TEST_RESOURCES.resolve(resource).normalize();
			if (!resourcePath.startsWith(TEST_RESOURCES) //
					|| !Files.exists(resourcePath) //
					|| Files.isDirectory(resourcePath))
				throw new IllegalArgumentException(resource);

			return Files.readString(resourcePath);
		});
	}

	@Test
	public void testEmptyExpression() {
		assertNull(El.eval(null));
		assertNull(El.eval(""));
		final var o = JsonValue.EMPTY_JSON_OBJECT;
		assertEquals(o, El.eval(o, null));
		assertEquals(o, El.eval(o, ""));
	}

	@Test
	public void testQuote() {
		assertEquals("", IuJsonAdapter.of(String.class).fromJson(El.eval("'")));
		assertEquals("Some stuff", IuJsonAdapter.of(String.class).fromJson(El.eval("'Some stuff")));
	}

	@Test
	public void testComment() {
		assertEquals("", IuJsonAdapter.of(String.class).fromJson(El.eval("* a comment")));
		assertEquals("", IuJsonAdapter.of(String.class).fromJson(El.eval("'* a comment")));
		assertEquals("* not a comment", IuJsonAdapter.of(String.class).fromJson(El.eval("'\\* not a comment")));
		assertEquals("Some stuff", IuJsonAdapter.of(String.class).fromJson(El.eval("'Some stuff* with a comment")));
		assertEquals("Some stuff* with a comment",
				IuJsonAdapter.of(String.class).fromJson(El.eval("'Some stuff\\* with a comment")));
	}

	@Test
	public void testRaw() {
		assertEquals("&lt;b&gt;Hello&lt;/b&gt; &quot;World&quot;",
				IuJsonAdapter.of(String.class).fromJson(El.eval("'<b>Hello</b> \"World\"")));
		assertEquals("<b>Hello</b> \"World\"",
				IuJsonAdapter.of(String.class).fromJson(El.eval("@'<b>Hello</b> \"World\"")));
	}

	@Test
	public void testResource() {
		assertEquals("Hello World",
				IuJsonAdapter.of(String.class).fromJson(El.eval(null, "<'el/hello.txt", ElTest::readResource)));
	}

	// Try this test with a JsonObject instead of ExprTestBean
	@Test
	public void testBean() {
		JsonObjectBuilder b = Json.createObjectBuilder();
		b.add("foo", "bar");
		assertEquals("bar", IuJsonAdapter.of(String.class).fromJson(El.eval(b.build(), "$.foo")));
		b.add("foo", "bar");
		assertEquals("bar", IuJsonAdapter.of(String.class).fromJson(El.eval(b.build(), ".foo")));
		b.add("foo", "bar");
		assertEquals("bar", IuJsonAdapter.of(String.class).fromJson(El.eval(b.build(), "foo")));
		b.add("foo", "bar");
		assertEquals("bar", IuJsonAdapter.of(String.class).fromJson(El.eval(b.build(), "foo*bar")));

		b.add("fooList", Json.createArrayBuilder().add("foo").add("bar").add("baz"));
		assertEquals("bar", IuJsonAdapter.of(String.class).fromJson(El.eval(b.build(), "$.fooList.1")));

		JsonObjectBuilder b1 = Json.createObjectBuilder();
		b1.add("foo", "bar");
		b1.add("bar", "bam");
		b1.add("bam", "foo");
		b.add("fooMap", b1);

		final var c = b.build();
		assertEquals("bar", IuJsonAdapter.of(String.class).fromJson(El.eval(c, "$.fooMap.foo")));
		assertEquals("bar", IuJsonAdapter.of(String.class).fromJson(El.eval(c, "$.fooMap?_.foo")));
		assertEquals("bar", IuJsonAdapter.of(String.class).fromJson(El.eval(c, "$.fooMap[$.fooMap[_.bar]]")));
	}

	@Test
	public void testMisplacedMacros() {
		JsonObjectBuilder b = Json.createObjectBuilder();
		b.add("foo", "bar");
		final var c = b.build();
		assertEquals("unexpected ' EL expression \"foo'bar\" at 3: \"foo[']bar\"",
				assertThrows(IllegalArgumentException.class, () -> El.eval(c, "foo'bar")).getMessage());
		assertEquals("unexpected @ EL expression \"foo@bar\" at 3: \"foo[@]bar\"",
				assertThrows(IllegalArgumentException.class, () -> El.eval(c, "foo@bar")).getMessage());
		assertEquals("bar", IuJsonAdapter.of(String.class).fromJson(El.eval(c, "foo[bar]")));
	}

	@Test
	public void testInvalidAmpersandDoesNotHang() {
		JsonObjectBuilder b = Json.createObjectBuilder();
		b.add("foo", "bar");
		final var c = b.build();
		assertEquals("unexpected & EL expression \"foo&bar\" at 3: \"foo[&]bar\"",
				assertTimeoutPreemptively(Duration.ofSeconds(5),
						() -> assertThrows(IllegalArgumentException.class, () -> El.eval(c, "foo&bar"))).getMessage());
	}

	@Test
	public void testBadTemplates() {
		assertEquals("inline template doesn't end with '`'",
				assertThrows(IllegalArgumentException.class, () -> El.eval("<`")).getMessage());
		assertEquals("invalid template name {}, expected a string",
				assertThrows(IllegalArgumentException.class, () -> El.eval(IuJson.object().build(), "<_")).getMessage());
	}

	@Test
	public void testBadSubExpression() {
		JsonObjectBuilder b = Json.createObjectBuilder();
		b.add("foo", "bar");
		final var c = b.build();
		assertEquals("missing close bracket ']'",
				assertThrows(IllegalArgumentException.class, () -> El.eval(c, "['foo")).getMessage());
	}

	@Test
	public void testJsonPointer() {
		final var context = Json.createObjectBuilder() //
				.add("foo", Json.createObjectBuilder() //
						.add("bar", Json.createArrayBuilder().add("baz").add("bim"))) //
				.add("array", Json.createArrayBuilder().add("first").add("second")) //
				.add("a/b", "slash") //
				.add("m~n", "tilde") //
				.build();

		assertEquals("bim", IuJsonAdapter.of(String.class).fromJson(El.eval(context, "$./foo/bar/1")));
		assertEquals("second", IuJsonAdapter.of(String.class).fromJson(El.eval(context, "$.array./1")));
		assertEquals("second", IuJsonAdapter.of(String.class).fromJson(El.eval(context.getJsonArray("array"), "$./1")));
		assertEquals("slash", IuJsonAdapter.of(String.class).fromJson(El.eval(context, "$./a~1b")));
		assertEquals("tilde", IuJsonAdapter.of(String.class).fromJson(El.eval(context, "$./m~0n")));
	}

	@Test
	public void testTemplateExpr() {
		JsonObjectBuilder b = Json.createObjectBuilder();
		JsonArrayBuilder arr = Json.createArrayBuilder();
		arr.add("foo");
		arr.add("bar");
		arr.add("baz");
		b.add("fooList", arr);
		b.add("fool", "el/-list");
		assertEquals("foo,bar,baz",
				IuJsonAdapter.of(String.class).fromJson(El.eval(b.build(), "$.fooList<p.fool", ElTest::readResource)));
	}

	@Test
	public void testTemplateExprWithHead() {
		JsonObjectBuilder b = Json.createObjectBuilder();
		JsonArrayBuilder arr = Json.createArrayBuilder();
		arr.add("foo");
		arr.add("bar");
		arr.add("baz");
		b.add("fooList", arr);
		b.add("fool", "el/-list-head");
		assertEquals("0: foo, 1: bar, 2: baz",
				IuJsonAdapter.of(String.class).fromJson(El.eval(b.build(), "$.fooList<p.fool", ElTest::readResource)));
	}

	@Test
	public void testTemplateIntrospectWithHead() {
		JsonObjectBuilder b = Json.createObjectBuilder();
		JsonObjectBuilder arr = Json.createObjectBuilder();
		arr.add("foo", "bar");
		arr.add("bar", "baz");
		arr.add("baz", "foo");
		b.add("fooList", arr);
		b.add("fool", "el/-list-head");
		final var c = b.build();
		assertEquals("foo: bar, bar: baz, baz: foo",
				IuJsonAdapter.of(String.class).fromJson(El.eval(c, "$.fooList&<p.fool", ElTest::readResource)));
	}

	@Test
	public void testInlineTemplateAppliedToArray() {
		JsonArrayBuilder arr = Json.createArrayBuilder();
		arr.add("foo");
		arr.add("bar");
		arr.add("baz");
		assertEquals("0: foo, 1: bar, 2: baz",
				IuJsonAdapter.of(String.class).fromJson(El.eval(arr.build(), "$<`{head?'!', }{i}{': }{$}`")));
	}

	@Test
	public void testNestedInlineTemplateReusedAcrossArrayItems() {
		// the nested inline template "<`{$}`" is identical text evaluated once per
		// array item; this also exercises reuse of its parsed representation instead
		// of re-parsing it for every item
		JsonArrayBuilder arr = Json.createArrayBuilder();
		arr.add("foo");
		arr.add("bar");
		assertEquals("0:foo1:bar",
				IuJsonAdapter.of(String.class).fromJson(El.eval(arr.build(), "$<`{i}:{<`{$}`}`")));
	}

	@Test
	public void testNestedTemplateExpr() {
		JsonObjectBuilder b = Json.createObjectBuilder();
		JsonObjectBuilder success = Json.createObjectBuilder().add("success", JsonValue.TRUE).add("message",
				"success message");
		JsonObjectBuilder failure = Json.createObjectBuilder().add("success", JsonValue.FALSE).add("message",
				"failure message");
		b.add("foo", Json.createObjectBuilder().add("baz", success).add("bim", failure));
		assertEquals("Here it is a success success message!" + System.lineSeparator() + //
				"Here it is a failure failure message!" + System.lineSeparator(), IuJsonAdapter.of(String.class)
						.fromJson(El.eval(b.build(), "$.foo<'el/testTemplate", ElTest::readResource)));
	}

	@Test
	public void testTemplateExprResourceNotFound() {
		IuTestLogger.allow("edu.iu.util.el.ElContext", Level.FINE);
		JsonObjectBuilder b = Json.createObjectBuilder();
		b.add("foo", Json.createObjectBuilder().add("bar", "baz"));
		b.add("fool", "el/-not-found");
		final var err = assertThrows(IllegalArgumentException.class,
				() -> El.eval(b.build(), "$.foo<p.fool", ElTest::readResource));
		assertEquals("el/-not-found", err.getMessage());
	}

	@Test
	public void testTemplateExprParentDirWithColon() {
		JsonObjectBuilder b = Json.createObjectBuilder();
		JsonObjectBuilder success = Json.createObjectBuilder().add("success", JsonValue.TRUE).add("message",
				"success message");
		JsonObjectBuilder failure = Json.createObjectBuilder().add("success", JsonValue.FALSE).add("message",
				"failure message");
		b.add("foo", Json.createObjectBuilder().add("bar", success).add("bim", failure));

		assertEquals("What do we have here? a success success message!", IuJsonAdapter.of(String.class)
				.fromJson(El.eval(b.build(), "$.foo<'el/parentTemplate", ElTest::readResource)));
	}

	@Test
	public void testTemplateExprReadError() {
		JsonObjectBuilder b = Json.createObjectBuilder();
		JsonArrayBuilder arr = Json.createArrayBuilder();
		arr.add("foo");
		arr.add("bar");
		arr.add("baz");
		b.add("fooList", arr);
		b.add("fool", "el/-list");
		assertEquals("java.io.IOException: test",
				assertThrows(IllegalStateException.class, () -> El.eval(b.build(), "$.fooList<p.fool", resource -> {
					throw new IllegalStateException(new IOException("test"));
				})).getMessage());
	}

	@Test
	public void testJson() {
		JsonObjectBuilder b = Json.createObjectBuilder();
		b.add("foo", "bar");
		b.add("baz", "bif");
		b.add("bim", "bam");
		final var context = b.build();
		assertEquals("bar bif bam",
				IuJsonAdapter.of(String.class).fromJson(El.eval(context, "<`{$.foo} {$.baz} {$.bim}`")));
		assertEquals("inline template doesn't end with '`'",
				assertThrows(IllegalArgumentException.class, () -> El.eval(context, "<`{$.foo} {$.baz} {$.bim}"))
						.getMessage());
		assertEquals("inline template doesn't end with '`'",
				assertThrows(IllegalArgumentException.class, () -> El.eval(context, "<`")).getMessage());

		JsonObjectBuilder b1 = Json.createObjectBuilder();
		b1.add("foo", JsonValue.TRUE);
		b1.add("baz", JsonValue.FALSE);
		b1.add("bim", JsonValue.NULL);
		final var context1 = b1.build();
		assertEquals("  ", IuJsonAdapter.of(String.class).fromJson(El.eval(context1, "<`{$.foo} {$.baz} {$.bim}`")));

		JsonObjectBuilder b2 = Json.createObjectBuilder();
		b2.add("foo", Json.createObjectBuilder().add("bar", "baz"));
		b2.add("baz", JsonValue.FALSE);
		final var context2 = b2.build();
		assertEquals("false", IuJsonAdapter.of(String.class).fromJson(El.eval(context2, "$.foo.bar?root.baz")));
	}

	@Test
	public void testTemplateWithinTemplateWithContext() {
		JsonObjectBuilder b = Json.createObjectBuilder();
		b.add("foo", Json.createObjectBuilder().add("bar", "baz").add("baz", "bar"));
		final var context = b.build();
		assertEquals("baz's bar",
				IuJsonAdapter.of(String.class).fromJson(El.eval(context, "$.foo<`{$.bar}{<`\'s{<` {$.baz}`}`}`")));
	}

	@Test
	public void testTemplateWithinTemplateWithinConditionalWithContext() {
		JsonObjectBuilder b = Json.createObjectBuilder();
		b.add("foo", Json.createObjectBuilder().add("bar", "baz").add("baz", "bar"));
		final var context = b.build();
		assertEquals("baz's bar", IuJsonAdapter.of(String.class)
				.fromJson(El.eval(context, "$.foo<`{$.bar?<`{$.bar}\'s{<` {$.baz}`}`}`")));
	}

	@Test
	public void testEvalP() {
		JsonObjectBuilder b = Json.createObjectBuilder();
		b.add("foo", Json.createObjectBuilder().add("bar", "baz").add("baz", "bar"));
		final var context = b.build();

		final var error = assertThrows(IllegalArgumentException.class,
				() -> IuJsonAdapter.of(String.class).fromJson(El.eval(context, "$.foo.bar<`{p}`")));
		assertEquals("invalid result " + context + ", expected a single value from p", error.getMessage(), () -> {
			throw error;
		});

		final var error2 = assertThrows(IllegalArgumentException.class,
				() -> IuJsonAdapter.of(String.class).fromJson(El.eval(context, "$.foo.bar<`{p-}`")));
		assertEquals("expected object or array for property 'p-', found \"baz\"", error2.getMessage(), () -> {
			throw error2;
		});

		final var error3 = assertThrows(IllegalArgumentException.class,
				() -> IuJsonAdapter.of(String.class).fromJson(El.eval(context, "$.foo.bar<`{\\p}`")));
		assertEquals("expected object or array for property '\\p', found \"baz\"", error3.getMessage(), () -> {
			throw error3;
		});
		assertEquals("p", IuJsonAdapter.of(String.class).fromJson(El.eval(context, "$.foo.bar<`{'p}`")));
		assertEquals("baz", IuJsonAdapter.of(String.class).fromJson(El.eval(context, "$.foo.bar<`{_}`")));
		assertEquals("baz", IuJsonAdapter.of(String.class).fromJson(El.eval(context, "$.foo.bar<`{p.foo.bar}`")));
	}

	@Test
	public void testIfConditional() {
		JsonObjectBuilder b = Json.createObjectBuilder();
		b.add("foo", true);
		b.add("baz", false);
		b.add("bim", "bam");
		b.add("zero", 0);
		b.add("one", 1);
		final var context = b.build();
		assertEquals("foo is true", IuJsonAdapter.of(String.class).fromJson(El.eval(context, "$.foo?'foo is true")));
		assertEquals("foo is true",
				IuJsonAdapter.of(String.class).fromJson(El.eval(context, "$.foo?'foo is true!'foo is false")));
		assertEquals("bim exists",
				IuJsonAdapter.of(String.class).fromJson(El.eval(context, "$.bim?'bim exists!'bim does not exist")));
		assertEquals("one is true",
				IuJsonAdapter.of(String.class).fromJson(El.eval(context, "$.one?'one is true!'one is false")));
		assertEquals("zero is false",
				IuJsonAdapter.of(String.class).fromJson(El.eval(context, "$.zero?'zero is true!'zero is false")));
		assertEquals(JsonValue.FALSE, El.eval(context, "$.baz?'baz is true"));
		assertEquals(JsonValue.TRUE, El.eval(context, "$.foo!'foo is false"));
		assertNull(El.eval("$?'no context"));
	}

	@Test
	public void testUnlessConditional() {
		JsonObjectBuilder b = Json.createObjectBuilder();
		b.add("foo", true);
		b.add("baz", false);
		b.add("bim", "bam");
		b.add("bum", JsonValue.NULL);
		b.add("zero", 0);
		b.add("one", 1);
		final var context = b.build();
		assertEquals("baz is false",
				IuJsonAdapter.of(String.class).fromJson(El.eval(context, "$.baz?'baz is true!'baz is false")));
		assertEquals("baz is false", IuJsonAdapter.of(String.class).fromJson(El.eval(context, "$.baz!'baz is false")));
		assertEquals("bum is false",
				IuJsonAdapter.of(String.class).fromJson(El.eval(context, "$.bum?'bum is true!'bum is false")));
		assertEquals("1", IuJsonAdapter.of(String.class).fromJson(El.eval(context, "$.one!'one is false")));
		assertEquals("zero is false",
				IuJsonAdapter.of(String.class).fromJson(El.eval(context, "$.zero!'zero is false")));
		assertEquals("bif is null",
				IuJsonAdapter.of(String.class).fromJson(El.eval(context, "$.bif?'bif is not null!'bif is null")));
		assertEquals("bif is null", IuJsonAdapter.of(String.class).fromJson(El.eval(context, "$.bif!'bif is null")));
	}

	@Test
	public void testMatchConditional() {
		JsonObjectBuilder b = Json.createObjectBuilder();
		b.add("foo", true);
		b.add("baz", false);
		b.add("bim", "bam");
		b.add("bar", JsonValue.NULL);
		final var context = b.build();
		assertEquals(JsonValue.TRUE, El.eval(context, "$.bim='bam"));
		assertEquals(JsonValue.FALSE, El.eval(context, "$.bim='baz"));
		assertEquals(JsonValue.FALSE, El.eval(context, "$.bim=$.p"));
		assertEquals(JsonValue.FALSE, El.eval(context, "$.bim=$.bar"));
	}

	@Test
	public void testFormat() {
		JsonObjectBuilder b = Json.createObjectBuilder();
		b.add("int", 1);
		b.add("string_int", "0123");
		b.add("bigger_int", 1234567890);
		b.add("pi", 3.14159);
		b.add("money", "3.50");
		b.add("bigger_money", "1234567890.97");
		b.add("date", "2025-02-03T22:23:24Z");
		b.add("string", "foo");
		final var context = b.build();
		// numbers
		assertEquals("1", IuJsonAdapter.of(String.class).fromJson(El.eval(context, "$.int#0")));
		assertEquals("1", IuJsonAdapter.of(String.class).fromJson(El.eval(context, "$.int##")));
		assertEquals("01", IuJsonAdapter.of(String.class).fromJson(El.eval(context, "$.int#00")));

		assertEquals("0123", IuJsonAdapter.of(String.class).fromJson(El.eval(context, "$.string_int#00")));

		assertEquals("1234567890",
				IuJsonAdapter.of(String.class).fromJson(El.eval(context, "$.bigger_int#0000000000")));
		assertEquals("1,234,567,890",
				IuJsonAdapter.of(String.class).fromJson(El.eval(context, "$.bigger_int#0,000,000,000")));
		assertEquals("1234567890", IuJsonAdapter.of(String.class).fromJson(El.eval(context, "$.bigger_int##")));
		assertEquals("0,12,34,56,78,90",
				IuJsonAdapter.of(String.class).fromJson(El.eval(context, "$.bigger_int#0,00,00,00,00,00")));
		assertEquals("1,234,567,890",
				IuJsonAdapter.of(String.class).fromJson(El.eval(context, "$.bigger_int####,###,###,###")));

		assertEquals("3", IuJsonAdapter.of(String.class).fromJson(El.eval(context, "$.pi#0")));
		assertEquals("3", IuJsonAdapter.of(String.class).fromJson(El.eval(context, "$.pi##")));
		assertEquals("03", IuJsonAdapter.of(String.class).fromJson(El.eval(context, "$.pi#00")));
		assertEquals("3.14", IuJsonAdapter.of(String.class).fromJson(El.eval(context, "$.pi#0.00")));
		assertEquals("3.14", IuJsonAdapter.of(String.class).fromJson(El.eval(context, "$.pi####.0#")));
		assertEquals("03.14", IuJsonAdapter.of(String.class).fromJson(El.eval(context, "$.pi#00.00")));
		assertEquals("3.14159", IuJsonAdapter.of(String.class).fromJson(El.eval(context, "$.pi##.#####")));
		assertEquals("3.141590", IuJsonAdapter.of(String.class).fromJson(El.eval(context, "$.pi##.000000")));

		// dates
		assertEquals("02/03/2025", IuJsonAdapter.of(String.class).fromJson(El.eval(context, "$.date#MM/dd/yyyy")));
		assertEquals("3 Feb 2025", IuJsonAdapter.of(String.class).fromJson(El.eval(context, "$.date#d MMM yyyy")));
		assertEquals("02/03/2025 5:23 PM",
				IuJsonAdapter.of(String.class).fromJson(El.eval(context, "$.date#MM/dd/yyyy h:mm a")));
		assertEquals("3 Feb 2025 17:23:24",
				IuJsonAdapter.of(String.class).fromJson(El.eval(context, "$.date#d MMM yyyy HH:mm:ss")));

		// ignored
		assertEquals("foo", IuJsonAdapter.of(String.class).fromJson(El.eval(context, "$.string#0")));
		assertEquals("foo", IuJsonAdapter.of(String.class).fromJson(El.eval(context, "$.string#MM/dd/yyyy")));
	}

	@Test
	public void testExecutionContext() {
		// last result
		JsonArrayBuilder arr = Json.createArrayBuilder();
		arr.add("foo");
		arr.add("bar");
		arr.add("baz");

		JsonObjectBuilder b = Json.createObjectBuilder();
		b.add("arr", arr);
		final var context = b.build();

		assertEquals("baz", IuJsonAdapter.of(String.class).fromJson(El.eval(context, "$.arr.2?_!$.arr.1")));
		assertEquals("bar", IuJsonAdapter.of(String.class).fromJson(El.eval(context, "$.arr?_.1!'arr is falsy")));
	}
}
