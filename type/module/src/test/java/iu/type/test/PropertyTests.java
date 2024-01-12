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
package iu.type.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import edu.iu.type.IuType;
import iu.type.IuTypeTestCase;

@SuppressWarnings({ "javadoc", "unused" })
public class PropertyTests extends IuTypeTestCase {

	private static class TestBean {
		private String foo;
		private int bar;

		public void setFoo(String foo) {
			this.foo = foo;
		}
		
		public int getBar() {
			return bar;
		}
		
		public TestBean getSelf() {
			return this;
		}
	}

	@Test
	public void testSelfIsSafe() {
		var testBean = new TestBean();
		var bar = IuType.of(TestBean.class).property("self");
		assertSame(testBean, bar.get(testBean));
	}

	@Test
	public void testReadOnly() {
		var testBean = new TestBean();
		var bar = IuType.of(TestBean.class).property("bar");
		assertTrue(bar.canRead());
		assertFalse(bar.canWrite());
		assertSame(TestBean.class, bar.declaringType().erasedClass());
		assertSame(int.class, bar.type().erasedClass());

		testBean.bar = 34;
		assertEquals(34, bar.get(testBean));

		assertEquals("Property bar is not writable for IuType[int PROPERTY(bar) TestBean.bar:int]",
				assertThrows(IllegalStateException.class, () -> bar.set(testBean, 0)).getMessage());
	}


	@Test
	public void testWriteOnly() {
		var testBean = new TestBean();
		var foo = IuType.of(TestBean.class).property("foo");
		assertFalse(foo.canRead());
		assertTrue(foo.canWrite());
		assertSame(TestBean.class, foo.declaringType().erasedClass());
		assertSame(String.class, foo.type().erasedClass());

		foo.set(testBean, "bar");
		assertEquals("bar", testBean.foo);

		assertEquals("Property foo is not readable for IuType[String PROPERTY(foo) TestBean.foo:String]",
				assertThrows(IllegalStateException.class, () -> foo.get(testBean)).getMessage());
	}

}
