package edu.iu.util.el;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
		ElContext evalContext = new ElContext(null, false, null, context, "<`{$.foo} {$.baz} {$.bim}`");
		assertEquals("EL expression \"<`{$.foo} {$.baz} {$.bim}`\" at 0: \"[<]`{$.f\"", evalContext.toString());
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

}
