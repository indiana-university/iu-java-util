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

import java.util.logging.Level;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import edu.iu.test.IuTestLogger;
import edu.iu.type.IuType;
import edu.iu.type.testresources.HasAroundInvokeMethod;
import edu.iu.type.testresources.HasInterceptors;
import edu.iu.type.testresources.HasInterceptorsOnMethod;
import edu.iu.type.testresources.MethodTestSupport;
import iu.type.IuTypeTestCase;

@SuppressWarnings("javadoc")
public class MethodTests extends IuTypeTestCase {

	@BeforeEach
	public void setup() {
		IuTestLogger.allow("iu.type.ParameterizedElement", Level.FINEST, "replaced type argument .*");
	}

	@Test
	public void testInstanceInvocation() throws Exception {
		var method = IuType.of(MethodTestSupport.class).method("echo", String.class);
		assertFalse(method.isStatic());
		assertEquals("echo", method.name());
		assertSame(String.class, method.returnType().erasedClass());
		assertEquals("foobar", method.exec(new MethodTestSupport(), "foobar"));
	}

	@Test
	public void testStaticInvocation() throws Exception {
		var method = IuType.of(MethodTestSupport.class).method("add", int.class, int.class);
		assertTrue(method.isStatic());
		assertEquals("add", method.name());
		assertSame(int.class, method.returnType().erasedClass());
		assertEquals(7, method.exec(3, 4));
	}

	@Test
	public void testInterceptorsOnTypeNotSupported() throws Exception {
		var method = IuType.of(HasInterceptors.class).method("fail");
		assertEquals("@AroundInvoke not supported in this version",
				assertThrows(UnsupportedOperationException.class, () -> method.exec(new HasInterceptors()))
						.getMessage());
	}

	@Test
	public void testInterceptorsOnMethodNotSupported() throws Exception {
		var method = IuType.of(HasInterceptorsOnMethod.class).method("fail");
		assertEquals("@AroundInvoke not supported in this version",
				assertThrows(UnsupportedOperationException.class, () -> method.exec(new HasInterceptorsOnMethod()))
						.getMessage());
	}

	@Test
	public void testAroundInvokeNotSupported() throws Exception {
		var method = IuType.of(HasAroundInvokeMethod.class).method("fail");
		assertEquals("@AroundInvoke not supported in this version",
				assertThrows(UnsupportedOperationException.class, () -> method.exec(new HasAroundInvokeMethod()))
						.getMessage());
	}

}
