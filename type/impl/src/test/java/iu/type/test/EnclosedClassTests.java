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
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;

import edu.iu.type.IuType;
import edu.iu.type.testresources.EnclosedClassesSupport;
import iu.type.IuTypeTestCase;

@SuppressWarnings({ "javadoc" })
public class EnclosedClassTests extends IuTypeTestCase {

	@Test
	public void testEnclosedDeclaringRefs() {
		var type = IuType.of(EnclosedClassesSupport.class);
		var enclosed = type.enclosedTypes().iterator().next();
		assertEquals("IuType[EnclosedClass ENCLOSING_TYPE EnclosedClassesSupport]", enclosed.toString());
		assertSame(EnclosedClassesSupport.class.getDeclaredClasses()[0], enclosed.erasedClass());
	}

	@Test
	public void testEnclosedConstructor() throws Exception {
		var type = IuType.of(EnclosedClassesSupport.class).enclosedTypes().iterator().next();
		var con = type.constructor(EnclosedClassesSupport.class);
		assertEquals("EnclosedClass(EnclosedClassesSupport)", con.toString());
		con = type.constructor(EnclosedClassesSupport.class, Object.class);
		assertEquals("EnclosedClass(EnclosedClassesSupport,Object)", con.toString());
		assertSame(Object.class, con.parameter(1).type().erasedClass());
	}

	@Test
	public void testEnclosedByMethod() {
		var type = IuType.of(new EnclosedClassesSupport().getMethodLevelClass());
		assertEquals("A", type.constructor(EnclosedClassesSupport.class, Class.class).typeParameters().keySet().iterator().next());
	}
}
