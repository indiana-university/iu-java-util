/*
 * Copyright © 2023 Indiana University
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
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.logging.Level;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import edu.iu.test.IuTestLogger;
import edu.iu.type.IuType;
import edu.iu.type.testresources.ConstructorTestSupport;

@SuppressWarnings("javadoc")
public class ConstructorTests {

	@BeforeEach
	public void setup() {
		IuTestLogger.allow("iu.type.ParameterizedElement", Level.FINEST, "replaced type argument .*");
	}
	
	@Test
	public void testDirect() throws Exception {
		var type = IuType.of(ConstructorTestSupport.class);
		var con = type.constructor();
		assertEquals("ConstructorTestSupport()", con.toString());
		assertSame(type, ((TypeFacade<?,?>) con.declaringType()).template);
		assertEquals(0, con.parameters().size());
		assertInstanceOf(ConstructorTestSupport.class, con.exec());
	}

	@Test
	public void testNestedParam() throws Exception {
		var type = IuType.of(ConstructorTestSupport.class);
		var con = type.constructor(Class.class);
		assertEquals("ConstructorTestSupport(Class<A>)", con.toString());
		assertSame(Number.class, con.parameter(0).type().referTo(Class.class).typeParameter("T").erasedClass());
	}

}
