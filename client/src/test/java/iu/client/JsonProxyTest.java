/*
 * Copyright © 2024 Indiana University
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
package iu.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;

import java.lang.reflect.Proxy;

import org.junit.jupiter.api.Test;

import edu.iu.client.IuJson;
import edu.iu.client.IuJsonAdapter;

@SuppressWarnings({ "javadoc" })
public class JsonProxyTest {

	class Opaque {
	}

	interface JsonBackedInterface {
		String getFoo();

		String getLowerSnakeFoo();

		String getUpperSnakeFoo();

		boolean isNotThere();

		int getNumber();

		void unsupported();

		Opaque getOpaque();

		default String getBar() {
			return "baz";
		}

		default String addToBar(String s) {
			return s + getBar();
		}
	}

	interface JsonBackedInterfaceReference {
		JsonBackedInterface getData();
	}

	@Test
	public void testProxyMethods() {
		final var foobar = IuJson.object().add("foo", "bar").build();
		final var data = IuJson.wrap(foobar, JsonBackedInterface.class);
		assertSame(foobar, IuJson.unwrap(data));
		final var data2 = IuJson.wrap(IuJson.object().add("foo", "baz").build(), JsonBackedInterface.class);
		final var data3 = IuJson.wrap(IuJson.object().add("foo", "baz").build(), JsonBackedInterface.class);
		assertEquals("bar", data.getFoo());
		assertEquals(foobar.toString(), data.toString());
		assertEquals(foobar.hashCode(), data.hashCode());
		assertNotEquals(data, data2);
		assertNotEquals(data, null);
		assertNotEquals(data, new Object());
		assertNotEquals(data, Proxy.newProxyInstance(JsonBackedInterface.class.getClassLoader(),
				new Class<?>[] { JsonBackedInterface.class }, (proxy, method, args) -> {
					fail();
					return null;
				}));
		assertEquals(data3, data2);
		assertFalse(data.isNotThere());
		assertThrows(UnsupportedOperationException.class, data::unsupported);

		assertEquals(data,
				IuJson.wrap(IuJson.object().add("data", foobar).build(), JsonBackedInterfaceReference.class, t -> {
					if (t == JsonBackedInterface.class)
						return IuJsonAdapter.from(v -> IuJson.wrap(v.asJsonObject(), JsonBackedInterface.class),
								IuJson::unwrap);
					else
						return IuJsonAdapter.of(t);
				}).getData());

		final var data4 = IuJson.wrap(IuJson.object().add("lower_snake_foo", "little snek").build(),
				JsonBackedInterface.class);
		assertEquals("little snek", data4.getLowerSnakeFoo());
		final var data5 = IuJson.wrap(IuJson.object().add("UPPER_SNAKE_FOO", "BIG SNEK").build(),
				JsonBackedInterface.class);
		assertEquals("BIG SNEK", data5.getUpperSnakeFoo());
	}

	@Test
	public void testDefault() {
		final var data = IuJson.wrap(IuJson.object().build(), JsonBackedInterface.class);
		assertEquals("baz", data.getBar());
		assertEquals("shabaz", data.addToBar("sha"));
		final var data2 = IuJson.wrap(IuJson.object().add("bar", "foo").build(), JsonBackedInterface.class);
		assertEquals("foo", data2.getBar());
		assertEquals("snafoo", data2.addToBar("sna"));
	}

	@Test
	public void testOpaque() {
		final var data = IuJson.wrap(IuJson.object().build(), JsonBackedInterface.class);
		assertNull(data.getOpaque());
		final var data2 = IuJson.wrap(IuJson.object().add("opaque", "foo").build(), JsonBackedInterface.class);
		assertThrows(UnsupportedOperationException.class, data2::getOpaque);
	}

	@Test
	public void testInvalid() {
		final var data = IuJson.wrap(IuJson.object().add("number", "foobar").build(), JsonBackedInterface.class);
		assertThrows(IllegalArgumentException.class, data::getNumber);
	}

}
