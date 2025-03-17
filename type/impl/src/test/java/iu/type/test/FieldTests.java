/*
 * Copyright © 2025 Indiana University
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
package iu.type.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import edu.iu.type.IuType;
import edu.iu.type.testresources.FieldTestSupport;
import edu.iu.type.testresources.InheritedFieldTestSupport;
import iu.type.IuTypeTestCase;

@SuppressWarnings({ "javadoc" })
public class FieldTests extends IuTypeTestCase {

	@Test
	public void testGet() {
		final var messageField = IuType.of(FieldTestSupport.class).field("message");
		assertTrue(messageField.serializable());
		final var support = new FieldTestSupport();
		assertEquals("foo", messageField.get(support));
		messageField.set(support, "bar");
		assertEquals("bar", messageField.get(support));
		assertEquals("message", messageField.name());
		assertEquals("FieldTestSupport#message:String", messageField.toString());
		assertSame(FieldTestSupport.class, messageField.declaringType().erasedClass());
	}

	@Test
	public void testGetSet() {
		final var valueField = IuType.of(FieldTestSupport.class).field("value");
		assertFalse(valueField.serializable());
	}

	@Test
	public void testStatic() throws IllegalAccessException, NoSuchFieldException {
		final var timestampField = IuType.of(FieldTestSupport.class).field("timestamp");
		assertFalse(timestampField.serializable());
		final var controlField = FieldTestSupport.class.getDeclaredField("timestamp");
		controlField.setAccessible(true);
		assertEquals(controlField.get(null), timestampField.get(null));
	}

	@Test
	public void testInheritance() {
		final var type = IuType.of(InheritedFieldTestSupport.class);
		assertEquals("bar", type.field("message").get(new InheritedFieldTestSupport()));
		
		final var i = type.fields().iterator();
		assertTrue(i.hasNext());
		var n = i.next();
		assertEquals("message", n.name());
		assertEquals(InheritedFieldTestSupport.class, n.declaringType().erasedClass());
		assertEquals(String.class, n.type().erasedClass());
		
		assertTrue(i.hasNext());
		n = i.next();
		assertEquals("stringValue", n.name());
		assertEquals(InheritedFieldTestSupport.class, n.declaringType().erasedClass());
		assertEquals(String.class, n.type().erasedClass());

		assertTrue(i.hasNext());
		n = i.next();
		assertEquals("timestamp", n.name());
		assertEquals(FieldTestSupport.class, n.declaringType().erasedClass());
		assertEquals(long.class, n.type().erasedClass());

		assertTrue(i.hasNext());
		n = i.next();
		assertEquals("message", n.name());
		assertEquals(FieldTestSupport.class, n.declaringType().erasedClass());
		assertEquals(String.class, n.type().erasedClass());

		assertTrue(i.hasNext());
		n = i.next();
		assertEquals("value", n.name());
		assertEquals(FieldTestSupport.class, n.declaringType().erasedClass());
		assertEquals(int.class, n.type().erasedClass());

		assertFalse(i.hasNext(), i::toString);
	}
	
}
