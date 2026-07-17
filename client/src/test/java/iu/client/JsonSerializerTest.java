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
package iu.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.mockStatic;

import java.lang.reflect.Proxy;

import org.junit.jupiter.api.Test;

import edu.iu.IdGenerator;
import edu.iu.client.IuJson;
import edu.iu.client.IuJsonAdapter;
import edu.iu.client.IuJsonPropertyNameFormat;

@SuppressWarnings("javadoc")
public class JsonSerializerTest {

	@Test
	public void testFormatPropertyName() {
		final var name = IdGenerator.generateId();
		final var convertedName = IdGenerator.generateId();
		try (final var mockJsonProxy = mockStatic(JsonProxy.class)) {
			mockJsonProxy.when(() -> JsonProxy.convertToSnakeCase(name)).thenReturn(convertedName);

			assertEquals(convertedName,
					JsonSerializer.formatPropertyName(name, IuJsonPropertyNameFormat.LOWER_CASE_WITH_UNDERSCORES));
			assertEquals(convertedName.toUpperCase(),
					JsonSerializer.formatPropertyName(name, IuJsonPropertyNameFormat.UPPER_CASE_WITH_UNDERSCORES));
		}
	}

	@Test
	public void testSerializePlainObject() {
		assertEquals(IuJson.object().build(), JsonSerializer.serialize(Object.class, new Object(),
				IuJsonPropertyNameFormat.IDENTITY, IuJsonAdapter::of));
	}

	@Test
	public void testSerializeBean() {
		class Bean {
			String id;

			@SuppressWarnings("unused")
			public String getId() {
				return id;
			}

			@SuppressWarnings("unused")
			public void setNothing(String nothing) {
				// covers readMethod == null case
			}
		}
		final var bean = new Bean();
		bean.id = IdGenerator.generateId();
		assertEquals(IuJson.object().add("id", bean.id).build(),
				JsonSerializer.serialize(Bean.class, bean, IuJsonPropertyNameFormat.IDENTITY, IuJsonAdapter::of));
	}

	interface TestInterface {
	}

	@Test
	public void testUnwrapProxy() {
		final var wrapped = IuJson.object().build();
		final var proxy = IuJson.wrap(wrapped, TestInterface.class);
		assertSame(wrapped, JsonSerializer.serialize(TestInterface.class, proxy, IuJsonPropertyNameFormat.IDENTITY,
				IuJsonAdapter::of));
	}

	@Test
	public void testDoesntUnwrapNonJsonProxy() {
		final var value = (TestInterface) Proxy.newProxyInstance(TestInterface.class.getClassLoader(),
				new Class<?>[] { TestInterface.class }, (proxy, method, args) -> fail());
		assertEquals(IuJson.object().build(), JsonSerializer.serialize(TestInterface.class, value,
				IuJsonPropertyNameFormat.IDENTITY, IuJsonAdapter::of));
	}

	interface A {
		String getFoo();

		String getBar();
	}

	interface B extends A {
		String getFoo();
	}

	@Test
	public void testIgnoresPropertyNameConflicts() {
		final String foo = IdGenerator.generateId();
		final String bar = IdGenerator.generateId();
		final var bean = new B() {
			@Override
			public String getFoo() {
				return foo;
			}

			@Override
			public String getBar() {
				return bar;
			}
		};

		assertEquals(IuJson.object().add("foo", foo).add("bar", bar).build(),
				JsonSerializer.serialize(B.class, bean, IuJsonPropertyNameFormat.IDENTITY, IuJsonAdapter::of));
	}

}
