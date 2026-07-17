package edu.iu.util.el;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Level;

//import java.util.logging.Logger;

import org.junit.jupiter.api.Test;

import edu.iu.IuException;
import edu.iu.client.IuJsonAdapter;
import edu.iu.test.IuTestLogger;
import jakarta.json.Json;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonValue;

@SuppressWarnings("javadoc")
public class ElTest {

//	private static final Logger LOG = Logger.getLogger(ElTest.class.getName());
	private static final Path TEST_RESOURCES = IuException
			.unchecked(() -> Path.of(ElTest.class.getProtectionDomain().getCodeSource().getLocation().toURI()));

	private static String readResource(String resource) {
		return IuException.unchecked(() -> {
			String resourceName = resource.replace('\\', '/');
			if (resourceName.startsWith("C://"))
				resourceName = "el/" + resourceName.substring(4);
			else if (resourceName.startsWith("C:/"))
				resourceName = "el/" + resourceName.substring(3);

			final var resourcePath = TEST_RESOURCES.resolve(resourceName).normalize();
			if (!resourcePath.startsWith(TEST_RESOURCES) || !Files.exists(resourcePath))
				throw new IllegalArgumentException(resource);

			if (!Files.isDirectory(resourcePath))
				return Files.readString(resourcePath);

			return "";
//			try (final var entries = Files.list(resourcePath)) {
//				return entries.map(path -> path.getFileName().toString())
//						.sorted(String.CASE_INSENSITIVE_ORDER)
//						.collect(Collectors.joining("\n", "", "\n"));
//			}
		});
	}

	@Test
	public void testGetIndexFromFindsChar() {
		assertEquals(3, El.getIndexFrom("abc*def", '*', 0));
	}

	@Test
	public void testGetIndexFromStartsFromOffset() {
		assertEquals(7, El.getIndexFrom("abc*def*ghi", '*', 4));
	}

	@Test
	public void testGetIndexFromReturnsMinusOneWhenNotFound() {
		assertEquals(-1, El.getIndexFrom("abcdef", '*', 0));
	}

	@Test
	public void testGetIndexFromReturnsMinusOneWhenBeyondEnd() {
		assertEquals(-1, El.getIndexFrom("abc*def", '*', 4));
	}

	@Test
	public void testGetIndexFromAnyFindsFirstControlChar() {
		// 'a' at 0, '@' at 3 is first control char
		assertEquals(3, El.getIndexFrom("abc@def", El.ANY, 0));
	}

	@Test
	public void testGetIndexFromSkipsInsideInlineTemplate() {
		// "<`*`}" — the '*' at index 2 is inside an inline template block; no match at
		// depth 0
		assertEquals(-1, El.getIndexFrom("<`*`}", '*', 0));
	}

	@Test
	public void testGetIndexFromSkipsEolComment() {
		// "<`*`foo" — the '*' at index 2 is inside an inline template block; no match
		// at depth 0
		assertEquals(-1, El.getIndexFrom("<`*`foo", '*', 0));
	}

	@Test
	public void testGetIndexFromLeadingTick() {
		assertEquals(1, El.getIndexFrom("`*", '*', 0));
	}

	@Test
	public void testGetIndexFromLazyTick() {
		assertEquals(2, El.getIndexFrom("a`*", '*', 0));
	}

	@Test
	public void testGetIndexFromFindsCharAfterInlineTemplate() {
		// "<`inner`}*" — '*' at index 9 is outside the inline template block
		assertEquals(9, El.getIndexFrom("<`inner`}*", '*', 0));
	}

	@Test
	public void testGetIndexFromHandlesNestedInlineTemplates() {
		// "<`<`inner`}`}*rest" — nested templates both close properly; '*' at index 13
		// is found
		assertEquals(13, El.getIndexFrom("<`<`inner`}`}*rest", '*', 0));
	}

	@Test
	public void testGetIndexFromTerminalBacktickClosesDepth() {
		// "<`*`" — '`' at end of string is terminal and closes the block; '*' inside is
		// skipped
		assertEquals(-1, El.getIndexFrom("<`*`", '*', 0));
	}

	@Test
	public void testEmptyExpression() {
		assertNull(El.eval(null));
		assertNull(El.eval(""));
		assertEquals("Non-atmoic result",
				assertThrows(IllegalStateException.class, () -> El.eval(JsonValue.EMPTY_JSON_OBJECT, null))
						.getMessage());
		assertEquals("Non-atmoic result",
				assertThrows(IllegalStateException.class, () -> El.eval(JsonValue.EMPTY_JSON_OBJECT, "")).getMessage());
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
		assertEquals("Hello World",
				IuJsonAdapter.of(String.class).fromJson(El.eval(null, "<'/el/hello.txt", ElTest::readResource)));
	}

	// Try this test with a JsonObject instead of ExprTestBean
	@Test
	public void testBean() {
		JsonObjectBuilder b = Json.createObjectBuilder();
		b.add("foo", "bar");
		assertEquals("bar", IuJsonAdapter.of(String.class).fromJson(El.eval(b.build(), "$.foo")));

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
		// TODO: Is there a way to access a value of the JsonObject (equivalent to a
		// Map) using an expression as a key?
		// Current El doesn't like the brackets. Should it be able to interpret the
		// brackets as a new expression?
		//assertEquals("bar", IuJsonAdapter.of(String.class).fromJson(El.eval(c, "$.fooMap[$.fooMap[bam]]")));
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
		assertEquals("foo,bar,baz", IuJsonAdapter.of(String.class)
				.fromJson(El.eval(b.build(), "$.fooList<p.$.fool", ElTest::readResource)));
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
		assertEquals("0: foo, 1: bar, 2: baz", IuJsonAdapter.of(String.class)
				.fromJson(El.eval(b.build(), "$.fooList<p.$.fool", ElTest::readResource)));
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
				"Here it is a failure failure message!" + System.lineSeparator() + //
				"List test-classes dir " + System.lineSeparator() + //
				"No resource path lists test resources ", IuJsonAdapter.of(String.class)
						.fromJson(El.eval(b.build(), "$.foo<'el/testTemplate", ElTest::readResource)));
	}

	@Test
	public void testTemplateEmptyResource() {
		JsonObjectBuilder b = Json.createObjectBuilder();
		b.add("foo", Json.createObjectBuilder().add("bar", "baz"));
		final var context = b.build();
		assertEquals("",
				IuJsonAdapter.of(String.class).fromJson(El.eval(context, "$.foo.bar<'", ElTest::readResource)));
		assertEquals("",
				IuJsonAdapter.of(String.class).fromJson(El.eval(context, "$.foo.bar<'/", ElTest::readResource)));
		assertEquals("",
				IuJsonAdapter.of(String.class).fromJson(El.eval(context, "$.foo.bar<'.", ElTest::readResource)));
	}

	@Test
	public void testTemplateExprResourceNotFound() {
		IuTestLogger.allow("edu.iu.util.el.ElContext", Level.FINE);
		JsonObjectBuilder b = Json.createObjectBuilder();
		b.add("foo", Json.createObjectBuilder().add("bar", "baz"));
		b.add("fool", "el/-not-found");
		final var err = assertThrows(IllegalArgumentException.class,
				() -> El.eval(b.build(), "$.foo<p.$.fool", ElTest::readResource));
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
				.fromJson(El.eval(b.build(), "$.foo<'C:\\\\parentTemplate", ElTest::readResource)));
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
				assertThrows(IllegalStateException.class, () -> El.eval(b.build(), "$.fooList<p.$.fool", resource -> {
					throw new IllegalStateException(new IOException("test"));
				})).getMessage());
	}

	@Test
	public void testTemplateExprNoParent() {
		JsonObjectBuilder b = Json.createObjectBuilder();
		b.add("fool", "el/-list");
		final var err = assertThrows(IllegalArgumentException.class, () -> El.eval(b.build(), "p.$.fool"));
		assertEquals("no parent context", err.getMessage());
	}

	@Test
	public void testTemplateExprUnexpectedFirstSymbol() {
		JsonObjectBuilder b = Json.createObjectBuilder();
		b.add("fool", "el/-list");
		final var err = assertThrows(IllegalArgumentException.class, () -> El.eval(b.build(), "Z.fool"));
		assertEquals("unexpected Z", err.getMessage());
	}

	@Test
	public void testTemplateExprExpectedObjectOrArray() {
		JsonObjectBuilder b = Json.createObjectBuilder();
		b.add("fool", "el/-list");
		final var err = assertThrows(IllegalArgumentException.class, () -> El.eval(b.build(), "_.fool"));
		assertEquals("expected object or array for null", err.getMessage());
	}

	@Test
	public void testTemplateExprExpectedObjectOrArrayPointer() {
		JsonObjectBuilder b = Json.createObjectBuilder();
		b.add("fool", "el/-list");
		final var err = assertThrows(IllegalArgumentException.class, () -> El.eval(b.build(), "_./fool"));
		assertEquals("expected object or array for null", err.getMessage());
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
		assertEquals("true false ",
				IuJsonAdapter.of(String.class).fromJson(El.eval(context1, "<`{$.foo} {$.baz} {$.bim}`")));

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
		assertEquals("expected '.' after 'p'",
				assertThrows(IllegalArgumentException.class,
						() -> IuJsonAdapter.of(String.class).fromJson(El.eval(context, "$.foo.bar<`{p}`")))
						.getMessage());
		assertEquals("expected '.' after 'p'",
				assertThrows(IllegalArgumentException.class,
						() -> IuJsonAdapter.of(String.class).fromJson(El.eval(context, "$.foo.bar<`{p-}`")))
						.getMessage());
		assertEquals("unexpected \\p",
				assertThrows(IllegalArgumentException.class,
						() -> IuJsonAdapter.of(String.class).fromJson(El.eval(context, "$.foo.bar<`{\\p}`")))
						.getMessage());
		assertEquals("p", IuJsonAdapter.of(String.class).fromJson(El.eval(context, "$.foo.bar<`{'p}`")));
		assertEquals("baz", IuJsonAdapter.of(String.class).fromJson(El.eval(context, "$.foo.bar<`{p._}`")));
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
		assertEquals("1",
				IuJsonAdapter.of(String.class).fromJson(El.eval(context, "$.one!'one is false")));
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
