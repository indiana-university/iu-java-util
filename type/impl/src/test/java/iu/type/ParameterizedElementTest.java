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
package iu.type;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import edu.iu.type.IuType;
import edu.iu.type.testresources.HasConstructorParams;
import edu.iu.type.testresources.HasMethodParams;

@SuppressWarnings({ "javadoc", "unused" })
public class ParameterizedElementTest extends IuTypeTestCase {

	@Test
	public void testSealGuards() {
		var parameterizedElement = new ParameterizedElement();
		assertEquals("not sealed",
				assertThrows(IllegalStateException.class, () -> parameterizedElement.typeParameters()).getMessage());

		parameterizedElement.seal(getClass(), TypeFactory.resolveRawClass(getClass()));

		assertEquals("sealed",
				assertThrows(IllegalStateException.class, () -> parameterizedElement.apply(null)).getMessage());
		assertEquals("sealed",
				assertThrows(IllegalStateException.class, () -> parameterizedElement.apply(null, null, null))
						.getMessage());
		assertEquals("already sealed",
				assertThrows(IllegalStateException.class,
						() -> parameterizedElement.seal(getClass(), TypeFactory.resolveRawClass(getClass())))
						.getMessage());
	}

	@Test
	public void testTypeParams() {
		class HasTypeParams<A, B extends Number> {
		}
		var parameterizedElement = IuType.of(HasTypeParams.class);
		assertEquals(Object.class, parameterizedElement.typeParameter("A").erasedClass());
		assertEquals(Number.class, parameterizedElement.typeParameter("B").erasedClass());
		assertNull(parameterizedElement.typeParameter("C"));
	}

	@Test
	public void testConstructorParams() {
		var parameterizedElement = IuType.of(HasConstructorParams.class).constructor();
		assertEquals(Object.class, parameterizedElement.typeParameter("A").erasedClass());
		assertEquals(Number.class, parameterizedElement.typeParameter("B").erasedClass());
		assertNull(parameterizedElement.typeParameter("C"));
	}

	@Test
	public void testMethodParams() {
		var parameterizedElement = IuType.of(HasMethodParams.class).method("methodWithParams");
		assertEquals(Object.class, parameterizedElement.typeParameter("A").erasedClass());
		assertEquals(Number.class, parameterizedElement.typeParameter("B").erasedClass());
		assertNull(parameterizedElement.typeParameter("C"));
	}
}
