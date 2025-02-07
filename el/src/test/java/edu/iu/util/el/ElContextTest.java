package edu.iu.util.el;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayDeque;

import org.junit.jupiter.api.Test;

import jakarta.json.Json;
import jakarta.json.JsonObjectBuilder;

@SuppressWarnings("javadoc")
public class ElContextTest {

	@Test
	public void testContextToString() {
		JsonObjectBuilder b = Json.createObjectBuilder();
		b.add("foo", "bar");
		b.add("baz", "bif");
		b.add("bim", "bam");
		final var context = b.build();
		final var evalContext = new ElContext(null, false, null, context, "<`{$.foo} {$.baz} {$.bim}`");
		assertEquals("EL expression \"<`{$.foo} {$.baz} {$.bim}`\" at 0: \"[<]`{$.f\"", evalContext.toString());
		evalContext.advancePosition(1);
		assertEquals("EL expression \"<`{$.foo} {$.baz} {$.bim}`\" at 1: \"<[`]{$.fo\"", evalContext.toString());
		evalContext.setPositionAtEnd();
		assertEquals("EL expression \"<`{$.foo} {$.baz} {$.bim}`\" at 26: \"bim}`[]\"", evalContext.toString());

		final var evalContext2 = new ElContext(null, false, null, context, null);
		assertEquals("EL", evalContext2.toString());

		JsonObjectBuilder p = Json.createObjectBuilder();
		p.add("foo", "bar");
		final var parentContextJson = p.build();
		final var parentContext = new ElContext(null, false, null, parentContextJson, null);
		final var evalContext3 = new ElContext(parentContext, false, null, context, "<`p.foo`");
		assertEquals("EL expression \"<`p.foo`\" at 0: \"[<]`p.fo\"\nParent EL", evalContext3.toString());

		JsonObjectBuilder p2 = Json.createObjectBuilder();
		p2.add("foo", "bar");
		p2.add("baz", "bif");
		p2.add("bim", "bam");
		final var parentContextJson2 = p2.build();
		final var parentContext2 = new ElContext(null, false, null, parentContextJson2, "$.foo<`{_} {_}`");
		final var templateContext = new ElContext(parentContext2, false, null, parentContextJson2.get("foo"),
				"`{_} {_}`");
		templateContext.markAsRaw();

		parentContext2.setPositionAtEnd();
		parentContext2.markAsTemplate();
		ArrayDeque<ElContext> evalStack = new ArrayDeque<>();
		evalStack.push(parentContext2);
		templateContext.setResult(Json.createValue("`{_} {_}`"));
		templateContext.setPositionAtEnd();
		templateContext.postProcessResult(evalStack);
		assertEquals("EL expression \"`{_} {_}`\" at 9: \" {_}`[]\"\n" + //
				"Parent EL expression \"$.foo<`{_} {_}`\" at 15: \" {_}`[]\"\n   in `{_} {_}` = \" \"",
				templateContext.toString());
		final var templateContext2 = evalStack.pop();
		assertEquals("EL expression \"_\" at 0: \"[_]\" insert at 1\n" + //
				"Parent EL expression \"$.foo<`{_} {_}`\" at 15: \" {_}`[]\"\n   in `{_} {_}` = \" \"",
				templateContext2.toString());

		JsonObjectBuilder p3 = Json.createObjectBuilder();
		p3.add("foo", "bar");
		p3.add("baz", "bif");
		p3.add("bim", "bam");
		final var parentContextJson3 = p3.build();
		final var parentContext3 = new ElContext(null, false, null, parentContextJson3, "$.foo<``");
		final var templateContext3 = new ElContext(parentContext3, false, null, parentContextJson3.get("foo"), "``");
		templateContext3.markAsRaw();

		parentContext3.setPositionAtEnd();
		parentContext3.markAsTemplate();
		ArrayDeque<ElContext> evalStack2 = new ArrayDeque<>();
		evalStack2.push(parentContext3);
		templateContext3.setResult(Json.createValue("``"));
		templateContext3.setPositionAtEnd();
		templateContext3.postProcessResult(evalStack2);
		assertEquals(
				"EL expression \"``\" at 2: \"``[]\"\nParent EL expression \"$.foo<``\" at 8: \"oo<``[]\"\n   in `` = \"\"",
				templateContext3.toString());
		final var templateContext4 = evalStack2.pop();
		assertEquals("EL expression \"$.foo<``\" at 8: \"oo<``[]\"\n   in `` = \"\"", templateContext4.toString());
	}

	@Test
	public void testTemplateWithEscape() {
		JsonObjectBuilder p = Json.createObjectBuilder();
		p.add("foo", "bar");
		p.add("baz", "bif");
		p.add("bim", "bam");
		final var parentContextJson = p.build();
		final var parentContext = new ElContext(null, false, null, parentContextJson, "$.foo<`\\{{_}\\}`");
		final var templateContext = new ElContext(parentContext, false, null, parentContextJson.get("foo"),
				"`\\{{_}\\}`");
		templateContext.markAsRaw();

		parentContext.setPositionAtEnd();
		parentContext.markAsTemplate();
		ArrayDeque<ElContext> evalStack = new ArrayDeque<>();
		evalStack.push(parentContext);
		templateContext.setResult(Json.createValue("`\\{{_}\\}`"));
		templateContext.setPositionAtEnd();
		templateContext.postProcessResult(evalStack);
		assertEquals("EL expression \"`\\{{_}\\}`\" at 9: \"_}\\}`[]\"\n" + //
				"Parent EL expression \"$.foo<`\\{{_}\\}`\" at 15: \"_}\\}`[]\"\n   in `\\{{_}\\}` = \"{\\}\"",
				templateContext.toString());
		final var templateContext1 = evalStack.pop();
		assertEquals("EL expression \"_\" at 0: \"[_]\" insert at 1\n" + //
				"Parent EL expression \"$.foo<`\\{{_}\\}`\" at 15: \"_}\\}`[]\"\n   in `\\{{_}\\}` = \"{\\}\"",
				templateContext1.toString());

		JsonObjectBuilder p2 = Json.createObjectBuilder();
		p2.add("foo", "bar");
		p2.add("baz", "bif");
		p2.add("bim", "bam");
		final var parentContextJson2 = p2.build();
		final var parentContext2 = new ElContext(null, false, null, parentContextJson2, "$.foo<`${baz}`");
		final var templateContext2 = new ElContext(parentContext2, false, null, parentContextJson2.get("foo"),
				"`${baz}`");
		templateContext2.markAsRaw();

		parentContext2.setPositionAtEnd();
		parentContext2.markAsTemplate();
		ArrayDeque<ElContext> evalStack2 = new ArrayDeque<>();
		evalStack2.push(parentContext2);
		templateContext2.setResult(Json.createValue("`${baz}`"));
		templateContext2.setPositionAtEnd();
		templateContext2.postProcessResult(evalStack2);
		assertEquals("EL expression \"`${baz}`\" at 8: \"baz}`[]\"\n" + //
				"Parent EL expression \"$.foo<`${baz}`\" at 14: \"baz}`[]\"\n   in `${baz}` = \"${baz}\"",
				templateContext2.toString());
		final var templateContext3 = evalStack2.pop();
		assertEquals("EL expression \"$.foo<`${baz}`\" at 14: \"baz}`[]\"\n   in `${baz}` = \"${baz}\"",
				templateContext3.toString());
	}

	@Test
	public void testTemplateMissingEndToken() {
		JsonObjectBuilder p = Json.createObjectBuilder();
		p.add("foo", "bar");
		p.add("baz", "bif");
		p.add("bim", "bam");
		final var parentContextJson = p.build();
		final var parentContext = new ElContext(null, false, null, parentContextJson, "$.foo<`{_`");
		final var templateContext = new ElContext(parentContext, false, null, parentContextJson.get("foo"), "`{_`");
		templateContext.markAsRaw();

		parentContext.setPositionAtEnd();
		parentContext.markAsTemplate();
		ArrayDeque<ElContext> evalStack = new ArrayDeque<>();
		evalStack.push(parentContext);
		templateContext.setResult(Json.createValue("`{_`"));
		templateContext.setPositionAtEnd();
		assertEquals("Missing end token '`}': {_",
				assertThrows(IllegalStateException.class, () -> templateContext.postProcessResult(evalStack))
						.getMessage());
	}

	@Test
	public void testContextIsRaw() {
		JsonObjectBuilder b = Json.createObjectBuilder();
		b.add("foo", "bar");
		b.add("baz", "bif");
		b.add("bim", "bam");
		final var context = b.build();
		ElContext evalContext = new ElContext(null, false, null, context, "<`{$.foo} {$.baz} {$.bim}`");
		assertEquals(false, evalContext.isRaw());
	}

	@Test
	public void testContextEmptyExpression() {
		JsonObjectBuilder b = Json.createObjectBuilder();
		b.add("foo", "bar");
		b.add("baz", "bif");
		b.add("bim", "bam");
		final var context = b.build();
		ElContext evalContext = new ElContext(null, false, null, context, null);
		assertEquals("", evalContext.getExpression());
	}

}
