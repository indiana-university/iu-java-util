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
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import edu.iu.IdGenerator;
import edu.iu.client.IuJson;
import edu.iu.client.IuJsonAdapter;

@SuppressWarnings("javadoc")
public class JsonDeserializerTest {

	public interface Wrapped {
		String getFoo();
	}

	@Test
	public void testWrapsInterface() {
		final var value = IuJson.object().add("foo", "bar").build();
		final var deserialized = JsonDeserializer.deserialize(Wrapped.class, value, IuJsonAdapter::of);
		assertSame(value, IuJson.unwrap(deserialized));
		assertEquals("bar", deserialized.getFoo());
	}

	public static class Bean {
		private String id;
		private int count;
		private String readOnly = "read only";
		private String writeOnly;
		private String notInJson;

		public String getId() {
			return id;
		}

		public void setId(String id) {
			this.id = id;
		}

		public int getCount() {
			return count;
		}

		public void setCount(int count) {
			this.count = count;
		}

		public String getReadOnly() {
			return readOnly;
		}

		public void setWriteOnly(String writeOnly) {
			this.writeOnly = writeOnly;
		}

		public String getNotInJson() {
			return notInJson;
		}

		public void setNotInJson(String notInJson) {
			this.notInJson = notInJson;
		}
	}

	@Test
	public void testDeserializesBeanProperties() {
		final var id = IdGenerator.generateId();
		final var writeOnly = IdGenerator.generateId();
		final var value = IuJson.object() //
				.add("id", id) //
				.add("count", 34) //
				.add("readOnly", IdGenerator.generateId()) //
				.add("writeOnly", writeOnly) //
				.add("notAProperty", IdGenerator.generateId()) //
				.build();

		final var deserialized = JsonDeserializer.deserialize(Bean.class, value, IuJsonAdapter::of);
		assertEquals(id, deserialized.getId());
		assertEquals(34, deserialized.getCount());
		assertEquals(writeOnly, deserialized.writeOnly);
		assertEquals("read only", deserialized.getReadOnly());
		assertNull(deserialized.getNotInJson());
	}

	@Test
	public void testConvertsPropertyNameCase() {
		final var lower = IdGenerator.generateId();
		final var upper = IdGenerator.generateId();

		assertEquals(lower, JsonDeserializer
				.deserialize(Bean.class, IuJson.object().add("not_in_json", lower).build(), IuJsonAdapter::of)
				.getNotInJson());
		assertEquals(upper, JsonDeserializer
				.deserialize(Bean.class, IuJson.object().add("NOT_IN_JSON", upper).build(), IuJsonAdapter::of)
				.getNotInJson());
	}

	public static class NoDefaultConstructor {
		public NoDefaultConstructor(String id) {
		}

		public void setId(String id) {
		}
	}

	@Test
	public void testRequiresNoArgConstructor() {
		final var value = IuJson.object().add("id", IdGenerator.generateId()).build();
		final var error = assertThrows(IllegalStateException.class,
				() -> JsonDeserializer.deserialize(NoDefaultConstructor.class, value, IuJsonAdapter::of));
		assertInstanceOf(NoSuchMethodException.class, error.getCause());
	}

}
