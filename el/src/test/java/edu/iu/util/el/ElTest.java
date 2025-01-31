package edu.iu.util.el;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

//import java.util.logging.Logger;

import org.junit.jupiter.api.Test;

import edu.iu.client.IuJsonAdapter;
import jakarta.json.Json;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonValue;

@SuppressWarnings("javadoc")
public class ElTest {

//	private static final Logger LOG = Logger.getLogger(ElTest.class.getName());

	@Test
	public void testEmptyExpression() {
		assertNull(El.eval(null));
		assertNull(El.eval(""));
		assertEquals("Non-atmoic result", assertThrows(IllegalStateException.class, () -> El.eval(JsonValue.EMPTY_JSON_OBJECT, null)).getMessage());
		assertEquals("Non-atmoic result", assertThrows(IllegalStateException.class, () -> El.eval(JsonValue.EMPTY_JSON_OBJECT, "")).getMessage());
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
		assertEquals("Some stuff* with a comment", IuJsonAdapter.of(String.class).fromJson(El.eval("'Some stuff\\* with a comment")));
	}

	@Test
	public void testRaw() {
		assertEquals("&lt;b&gt;Hello&lt;/b&gt; &quot;World&quot;", IuJsonAdapter.of(String.class).fromJson(El.eval("'<b>Hello</b> \"World\"")));
		assertEquals("<b>Hello</b> \"World\"", IuJsonAdapter.of(String.class).fromJson(El.eval("@'<b>Hello</b> \"World\"")));
	}

	@Test
	public void testResource() {
		assertEquals("Hello World", IuJsonAdapter.of(String.class).fromJson(El.eval("<'el/hello.txt")));
		assertEquals("Hello World", IuJsonAdapter.of(String.class).fromJson(El.eval("<'/el/hello.txt")));
	}

//	public static class BazBean {
//
//		private boolean success;
//		private String message;
//
//		public boolean isSuccess() {
//			return success;
//		}
//
//		public void setSuccess(boolean success) {
//			this.success = success;
//		}
//
//		public String getMessage() {
//			return message;
//		}
//
//		public void setMessage(String message) {
//			this.message = message;
//		}
//	}
//
//	public static class ExprTestBean {
//
//		private String foo;
//		private boolean foot;
//		private boolean foof;
//		private boolean foob;
//		private List<String> fooList;
//		private Map<String, String> fooMap;
//		private String fool;
//		private float num;
//		private Date now;
//		private BazBean baz;
//
//		public String getFoo() {
//			return foo;
//		}
//
//		public void setFoo(String foo) {
//			this.foo = foo;
//		}
//
//		public boolean isFoot() {
//			return foot;
//		}
//
//		public void setFoot(boolean foot) {
//			this.foot = foot;
//		}
//
//		public boolean isFoof() {
//			return foof;
//		}
//
//		public void setFoof(boolean foof) {
//			this.foof = foof;
//		}
//
//		public boolean isFoob() {
//			return foob;
//		}
//
//		public void setFoob(boolean foob) {
//			this.foob = foob;
//		}
//
//		public List<String> getFooList() {
//			return fooList;
//		}
//
//		public void setFooList(List<String> fooList) {
//			this.fooList = fooList;
//		}
//
//		public Map<String, String> getFooMap() {
//			return fooMap;
//		}
//
//		public void setFooMap(Map<String, String> fooMap) {
//			this.fooMap = fooMap;
//		}
//
//		public String getFool() {
//			return fool;
//		}
//
//		public void setFool(String fool) {
//			this.fool = fool;
//		}
//
//		public float getNum() {
//			return num;
//		}
//
//		public void setNum(float num) {
//			this.num = num;
//		}
//
//		public Date getNow() {
//			return now;
//		}
//
//		public void setNow(Date now) {
//			this.now = now;
//		}
//
//		public BazBean getBaz() {
//			return baz;
//		}
//
//		public void setBaz(BazBean baz) {
//			this.baz = baz;
//		}
//	}

//	@Ignore // TODO: REMOVE or allow beans in El.eval
//	@Test
//	public void testBean() {
//		ExprTestBean tb = new ExprTestBean();
//
//		tb.setFoo("bar");
//		assertEquals("bar", El.eval(tb, "$.foo"));
//
//		tb.setFooList(Arrays.asList("foo", "bar", "baz"));
//		assertEquals("bar", El.eval(tb, "$.fooList[1]"));
//
//		Map<String, String> fooMap = new LinkedHashMap<>();
//		fooMap.put("foo", "bar");
//		fooMap.put("bar", "bam");
//		fooMap.put("bam", "foo");
//		tb.setFooMap(fooMap);
//		assertEquals("bar", El.eval(tb, "$.fooMap[\"foo\"]"));
//		assertEquals("bam", El.eval(tb, "$.fooMap[$.foo]"));
//		assertEquals("bar", El.eval(tb, "$.fooMap[$.fooMap[bam]]"));
//	}
	
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
		assertEquals("bar", IuJsonAdapter.of(String.class).fromJson(El.eval(b.build(), "$.fooMap.foo")));
		// TODO: Is there a way to access a value of the JsonObject (equivalent to a Map) using an expression as a key?
		// Current El doesn't like the brackets. Should it be able to interpret the brackets as a new expression?
//		assertEquals("bam", IuJsonAdapter.of(String.class).fromJson(El.eval(b.build(), "$.fooMap?_.foo")));
//		assertEquals("bar", IuJsonAdapter.of(String.class).fromJson(El.eval(b.build(), "$.fooMap[$.fooMap[bam]]")));
	}


//	@Ignore // TODO: REMOVE or allow beans in El.eval
//	@Test
//	public void testIntrospect() {
//		ExprTestBean tb = new ExprTestBean();
//		tb.setFoo("bar");
//
//		Map<String, Object> im = El.eval(tb, "@$&");
//		LOG.info("im: " + im);
//		assertEquals("bar", im.get("foo"));
//		assertEquals(10, im.size());
//		assertFalse(im.keySet().contains("class"));
//		assertTrue(im.keySet().contains("fool"));
//
//		im = El.eval("@e&");
//		LOG.info("im: " + im);
//		assertEquals(2, im.size());
//		assertFalse(im.keySet().contains("class"));
//		assertTrue(im.keySet().contains("applicationUrl"));
//		assertEquals("sisjee", im.get("applicationName"));
//	}

//	@Ignore // TODO: REMOVE or allow beans in El.eval
//	@Test
//	public void testConditional() {
//		ExprTestBean tb = new ExprTestBean();
//		tb.setFoof(true);
//		assertEquals("bar", El.eval(tb, "$.foof?'bar!'foo"));
//		assertEquals("bar", El.eval(tb, "$.foob?'baz!$.foof?'bar!'foo"));
//	}
//
//	@Ignore // TODO: REMOVE or allow beans in El.eval
//	@Test
//	public void testTemplate() {
//		ExprTestBean tb = new ExprTestBean();
//		assertEquals("Here it is ", El.eval(tb, "<'el/testTemplate"));
//
//		BazBean baz = new BazBean();
//		tb.setBaz(baz);
//		assertEquals("Here it is a failure !", El.eval(tb, "<'el/testTemplate"));
//
//		baz.setMessage("to communicate");
//		assertEquals("Here it is a failure to communicate!", El.eval(tb, "<'el/testTemplate"));
//
//		baz.setSuccess(true);
//		baz.setMessage("- hooray");
//		assertEquals("Here it is a success - hooray!", El.eval(tb, "<'el/testTemplate"));
//	}
//	
//	@Ignore // TODO: REMOVE or allow beans in El.eval
//	@Test
//	public void testInlineTemplate() {
//		String expr = "<`Here it is {$.baz?_<`a {$.success?'success!'`failure} {$.message}!`}`";
//		ExprTestBean tb = new ExprTestBean();
//		assertEquals("Here it is ", El.eval(tb, expr));
//
//		BazBean baz = new BazBean();
//		tb.setBaz(baz);
//		assertEquals("Here it is a `failure !", El.eval(tb, expr));
//
//		baz.setMessage("to communicate");
//		assertEquals("Here it is a `failure to communicate!", El.eval(tb, expr));
//
//		baz.setSuccess(true);
//		baz.setMessage("- hooray");
//		assertEquals("Here it is a success - hooray!", El.eval(tb, expr));
//	}
//
//	@Ignore // TODO: REMOVE or allow beans in El.eval
//	@Test
//	public void testFormat() {
//		ExprTestBean tb = new ExprTestBean();
//		tb.setNum(3.45f);
//		assertEquals("3.450", El.eval(tb, "$.num##.000"));
//		assertEquals("3.45", El.eval(tb, "$.num##.###"));
//		tb.setNum(3.456f);
//		assertEquals("3.456", El.eval(tb, "$.num##.###"));
//		tb.setNum(3.456f);
//		assertEquals("003.46", El.eval(tb, "$.num#000.00"));
//
//		Date now = new Date();
//		tb.setNow(now);
//		assertEquals(new SimpleDateFormat("MM/dd/yyyy HH:mm").format(now), El.eval(tb, "$.now#MM/dd/yyyy HH:mm"));
//	}
//
//	@Ignore // TODO: REMOVE or allow beans in El.eval
//	@Test
//	public void testMatch() {
//		ExprTestBean tb = new ExprTestBean();
//
//		tb.setFoo("bar");
//		assertEquals(true, El.eval(tb, "$.foo='bar"));
//
//		tb.setNum(12.34f);
//		assertEquals(true, El.eval(tb, "$.num='12.34"));
//	}
//
//	@Ignore // TODO: REMOVE or allow beans in El.eval
//	@Test
//	public void testList() {
//		ExprTestBean tb = new ExprTestBean();
//
//		tb.setFooList(Arrays.asList("foo", "bar", "baz"));
//		assertEquals(tb.getFooList(), El.eval(tb, "@$.fooList"));
//		assertEquals("[foo, bar, baz]", El.eval(tb, "$.fooList"));
//		assertEquals("foo,bar,baz", El.eval(tb, "$.fooList<'el/-list"));
//		assertEquals(" 0: foo\r\n 1: bar\r\n 2: baz", El.eval(tb, "$.fooList<'el/-hash"));
//	}
//
//	@Ignore // TODO: REMOVE
//	@Test
//	public void testMap() {
//		ExprTestBean tb = new ExprTestBean();
//		Map<String, String> fooMap = new LinkedHashMap<>();
//		fooMap.put("foo", "bar");
//		fooMap.put("baz", "bif");
//		fooMap.put("bim", "bam");
//		tb.setFooMap(fooMap);
//		assertEquals(" foo: bar\r\n baz: bif\r\n bim: bam", El.eval(tb, "$.fooMap<'el/-hash"));
//	}
//
//	@Ignore // TODO: REMOVE or allow beans in El.eval
//	@Test
//	public void testTemplateExpr() {
//		ExprTestBean tb = new ExprTestBean();
//		tb.setFool("el/-list");
//		tb.setFooList(Arrays.asList("foo", "bar", "baz"));
//		assertEquals("foo,bar,baz", El.eval(tb, "$.fooList<p.$.fool"));
//	}

	@Test
	public void testTemplateExpr() {
		JsonObjectBuilder b = Json.createObjectBuilder();
		JsonArrayBuilder arr = Json.createArrayBuilder();
		arr.add("foo");
		arr.add("bar");
		arr.add("baz");
		b.add("fooList", arr);
		b.add("fool", "el/-list");
		assertEquals("foo,bar,baz", IuJsonAdapter.of(String.class).fromJson(El.eval(b.build(), "$.fooList<p.$.fool")));
	}
	
	@Test
	public void testJson() {
		JsonObjectBuilder b = Json.createObjectBuilder();
		b.add("foo", "bar");
		b.add("baz", "bif");
		b.add("bim", "bam");
		final var context = b.build();
		assertEquals("bar bif bam", IuJsonAdapter.of(String.class).fromJson(El.eval(context, "<`{$.foo} {$.baz} {$.bim}`")));
		assertEquals("inline template doesn't end with '`'", assertThrows(IllegalArgumentException.class, () -> El.eval(context, "<`{$.foo} {$.baz} {$.bim}")).getMessage());
		assertEquals("inline template doesn't end with '`'", assertThrows(IllegalArgumentException.class, () -> El.eval(context, "<`")).getMessage());

		JsonObjectBuilder b1 = Json.createObjectBuilder();
		b1.add("foo", JsonValue.TRUE);
		b1.add("baz", JsonValue.FALSE);
		b1.add("bim", JsonValue.NULL);
		final var context1 = b1.build();
		assertEquals("true false ", IuJsonAdapter.of(String.class).fromJson(El.eval(context1, "<`{$.foo} {$.baz} {$.bim}`")));
		
		JsonObjectBuilder b2 = Json.createObjectBuilder();
		b2.add("foo", Json.createObjectBuilder().add("bar", "baz"));
		b2.add("baz", JsonValue.FALSE);
		final var context2 = b2.build();
		assertEquals("false", IuJsonAdapter.of(String.class).fromJson(El.eval(context2, "$.foo.bar?root.baz")));
		
		// head is useless because nowhere in the code does the value get set to true
		
//		JsonObjectBuilder b3 = Json.createObjectBuilder();
//		b3.add("foo", Json.createArrayBuilder().add("bar").add("bif").add("bam"));
//		final var context3 = b3.build();
//		assertEquals("true", IuJsonAdapter.of(String.class).fromJson(El.eval(context3, "$.foo?head")));
	}

	@Test
	public void testIfConditional() {
		JsonObjectBuilder b = Json.createObjectBuilder();
		b.add("foo", true);
		b.add("baz", false);
		b.add("bim", "bam");
		b.add("bum", JsonValue.NULL);
		final var context = b.build();
		assertEquals("foo is true", IuJsonAdapter.of(String.class).fromJson(El.eval(context, "$.foo?'foo is true")));
		assertEquals("foo is true", IuJsonAdapter.of(String.class).fromJson(El.eval(context, "$.foo?'foo is true!'foo is false")));
		// TODO: if we decide JsonValue.NULL is false, this should move to testUnlessConditional
		assertEquals("bum is true", IuJsonAdapter.of(String.class).fromJson(El.eval(context, "$.bum?'bum is true!'bum is false")));
		assertEquals("bim exists", IuJsonAdapter.of(String.class).fromJson(El.eval(context, "$.bim?'bim exists!'bim does not exist")));
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
		final var context = b.build();
		assertEquals("baz is false", IuJsonAdapter.of(String.class).fromJson(El.eval(context, "$.baz?'baz is true!'baz is false")));
		assertEquals("baz is false", IuJsonAdapter.of(String.class).fromJson(El.eval(context, "$.baz!'baz is false")));
		assertEquals("bif is null", IuJsonAdapter.of(String.class).fromJson(El.eval(context, "$.bif?'bif is not null!'bif is null")));
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
		final var context = b.build();
		// numbers
		assertEquals("1", IuJsonAdapter.of(String.class).fromJson(El.eval(context, "$.int#0")));
		assertEquals("1", IuJsonAdapter.of(String.class).fromJson(El.eval(context, "$.int##")));
		assertEquals("01", IuJsonAdapter.of(String.class).fromJson(El.eval(context, "$.int#00")));

		assertEquals("0123", IuJsonAdapter.of(String.class).fromJson(El.eval(context, "$.string_int#00")));

		assertEquals("1234567890", IuJsonAdapter.of(String.class).fromJson(El.eval(context, "$.bigger_int#0000000000")));
		assertEquals("1,234,567,890", IuJsonAdapter.of(String.class).fromJson(El.eval(context, "$.bigger_int#0,000,000,000")));
		assertEquals("1234567890", IuJsonAdapter.of(String.class).fromJson(El.eval(context, "$.bigger_int##")));
		assertEquals("0,12,34,56,78,90", IuJsonAdapter.of(String.class).fromJson(El.eval(context, "$.bigger_int#0,00,00,00,00,00")));
		assertEquals("1,234,567,890", IuJsonAdapter.of(String.class).fromJson(El.eval(context, "$.bigger_int####,###,###,###")));

		assertEquals("3", IuJsonAdapter.of(String.class).fromJson(El.eval(context, "$.pi#0")));
		assertEquals("3", IuJsonAdapter.of(String.class).fromJson(El.eval(context, "$.pi##")));
		assertEquals("03", IuJsonAdapter.of(String.class).fromJson(El.eval(context, "$.pi#00")));
		assertEquals("3.14", IuJsonAdapter.of(String.class).fromJson(El.eval(context, "$.pi#0.00")));
		assertEquals("3.14", IuJsonAdapter.of(String.class).fromJson(El.eval(context, "$.pi####.0#")));
		assertEquals("03.14", IuJsonAdapter.of(String.class).fromJson(El.eval(context, "$.pi#00.00")));
		assertEquals("3.14159", IuJsonAdapter.of(String.class).fromJson(El.eval(context, "$.pi##.#####")));
		assertEquals("3.141590", IuJsonAdapter.of(String.class).fromJson(El.eval(context, "$.pi##.000000")));

		// dates
		
		// ignored
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
//		assertEquals(JsonValue.FALSE, El.eval(context, "$.arr?_.3")); // out of bounds
	}
}
