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
package iu.client.jsonb;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.util.List;

import org.junit.jupiter.api.Test;

import edu.iu.client.IuJson;
import edu.iu.client.IuJsonPropertyNameFormat;
import edu.iu.client.IuJsonSerializationOptions;
import jakarta.json.JsonValue;
import jakarta.json.bind.JsonbConfig;
import jakarta.json.bind.JsonbException;

@SuppressWarnings("javadoc")
public class IuJsonbAdapterTest {

	public interface Named {
		String getName();

		default String getNickname() {
			return "nick";
		}
	}

	public static class Bean {
		public String value;
		public Named named;
		public Bean child;
		public List<Bean> children;
	}

	static IuJsonb jsonb() {
		return IuJsonbTest.jsonb(new JsonbConfig());
	}

	@Test
	public void testSkipsUnknownProperties() {
		final var json = "{\"x\":{\"a\":[1,{\"b\":2}]},\"y\":[1,{}],\"z\":1,\"value\":\"v\",\"w\":null}";
		assertEquals("v", jsonb().fromJson(json, Bean.class).value);
		assertEquals("v", ((Bean) jsonb().adapt(Bean.class).fromJson(IuJson.parse(json))).value);
	}

	@Test
	public void testNull() {
		final var jsonb = jsonb();
		final var adapter = jsonb.adapt(Bean.class);
		assertNull(adapter.fromJson(null));
		assertNull(adapter.fromJson(JsonValue.NULL));
		assertNull(jsonb.fromJson("null", Bean.class));
		assertEquals(JsonValue.NULL, adapter.toJson(null));

		final var withNulls = IuJsonbTest.jsonb(new JsonbConfig().withNullValues(true));
		assertEquals("{\"child\":null,\"children\":null,\"named\":null,\"value\":null}", withNulls.toJson(new Bean()));
		assertNull(withNulls.fromJson("{\"child\":null}", Bean.class).child);
	}

	@Test
	public void testRequiresAnObject() {
		final var jsonb = jsonb();
		assertEquals("expected object for " + Bean.class.getName() + ", found STRING",
				assertThrows(JsonbException.class, () -> jsonb.adapt(Bean.class).fromJson(IuJson.string("x")))
						.getMessage());
		assertTrue(assertThrows(JsonbException.class, () -> jsonb.fromJson("[]", Bean.class)).getMessage()
				.endsWith("expected START_OBJECT for " + Bean.class.getName() + ", found START_ARRAY"));
	}

	@Test
	public void testNested() {
		final var json = "{\"child\":{\"value\":\"c\"},\"children\":[{\"value\":\"a\"},{\"value\":\"b\"}],"
				+ "\"value\":\"p\"}";
		final var jsonb = jsonb();
		final var bean = jsonb.fromJson(json, Bean.class);
		assertEquals("c", bean.child.value);
		assertEquals("b", bean.children.get(1).value);
		assertEquals(json, jsonb.toJson(bean));
		assertEquals(json, jsonb.adapt(Bean.class).toJson(bean).toString());
		assertEquals("a", ((Bean) jsonb.adapt(Bean.class).fromJson(IuJson.parse(json))).children.get(0).value);
	}

	@Test
	public void testInterfaceProxy() {
		final var json = "{\"named\":{\"name\":\"n\",\"extra\":true}}";
		final var jsonb = jsonb();
		for (final var bean : new Bean[] { jsonb.fromJson(json, Bean.class),
				(Bean) jsonb.adapt(Bean.class).fromJson(IuJson.parse(json)) }) {
			assertEquals("n", bean.named.getName());
			assertEquals("nick", bean.named.getNickname());
			// written verbatim, keeping the property the interface doesn't declare
			assertEquals(json, jsonb.toJson(bean));
			assertEquals(json, jsonb.adapt(Bean.class).toJson(bean).toString());
		}

		final var named = (Named) jsonb.fromJson("{\"name\":\"top\"}", Named.class);
		assertEquals("top", named.getName());
		assertEquals("{\"name\":\"top\"}", jsonb.toJson(named));
	}

	@Test
	public void testInterfaceProxyReadsTheCallsPropertyNameFormat() {
		final var jsonb = IuJsonbTest.jsonb(new JsonbConfig().setProperty(IuJsonb.SERIALIZATION_OPTIONS,
				(java.util.function.Supplier<IuJsonSerializationOptions>) () -> IuJsonSerializationOptions
						.of(IuJsonPropertyNameFormat.UPPER_CASE_WITH_UNDERSCORES)));
		assertEquals("n", jsonb.fromJson("{\"NAMED\":{\"NAME\":\"n\"}}", Bean.class).named.getName());
	}

	@Test
	public void testOtherProxiesConvertByGetters() {
		final var bean = new Bean();
		bean.named = (Named) Proxy.newProxyInstance(Named.class.getClassLoader(), new Class<?>[] { Named.class },
				(proxy, method, args) -> "proxied " + method.getName());
		assertEquals("{\"named\":{\"name\":\"proxied getName\",\"nickname\":\"proxied getNickname\"}}",
				jsonb().toJson(bean));
	}

	@Test
	public void testRecursiveReference() {
		final var bean = new Bean();
		bean.child = bean;
		final var jsonb = jsonb();
		assertTrue(assertThrows(JsonbException.class, () -> jsonb.toJson(bean)).getMessage()
				.startsWith("failed to write Bean.child: recursive reference"));
		assertTrue(assertThrows(JsonbException.class, () -> jsonb.adapt(Bean.class).toJson(bean)).getMessage()
				.startsWith("failed to write Bean.child: recursive reference"));

		// the same value twice, not nested, is not a cycle
		final var shared = new Bean();
		final var parent = new Bean();
		parent.children = List.of(shared, shared);
		assertEquals("{\"children\":[{},{}]}", jsonb.toJson(parent));
	}

	@Test
	public void testRequiresACall() {
		final var adapter = new IuJsonbAdapter<>(Bean.class, jsonb());
		assertThrows(NullPointerException.class, () -> adapter.toJson(new Bean()));
		assertSame(JsonValue.NULL, adapter.toJson(null));
	}

}
