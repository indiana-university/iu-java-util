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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import edu.iu.type.IuType;
import jakarta.annotation.Resource;
import jakarta.json.bind.Jsonb;

@SuppressWarnings("javadoc")
@ExtendWith(LegacyContextSupport.class)
public class BackwardsCompatiblityTest extends IuTypeTestCase {

	@Test
	public void testNonLegacyReturnsSameType() throws ClassNotFoundException {
		assertSame(Resource.class, BackwardsCompatibility.getCompatibleClass(Resource.class));
	}

	@Test
	public void testLegacyReturnsSameType() throws ClassNotFoundException {
		assertSame(Resource.class, BackwardsCompatibility.getCompatibleClass(Resource.class));
	}

	@Test
	public void testConvertsJavaxToJakarta() throws ClassNotFoundException {
		assertSame(Resource.class, BackwardsCompatibility
				.getCompatibleClass(LegacyContextSupport.get().loadClass("javax.annotation.Resource")));
	}

	@Test
	public void testConvertsJakartaToJavax() throws Throwable {
		TypeUtils.callWithContext(LegacyContextSupport.get(), () -> {
			assertSame(LegacyContextSupport.get().loadClass("javax.annotation.Resource"),
					BackwardsCompatibility.getCompatibleClass(Resource.class));
			return null;
		});
	}

	@Test
	public void testTriesJakartaAndJavax() throws Throwable {
		TypeUtils.callWithContext(LegacyContextSupport.get(), () -> {
			var classNotFound = assertThrows(ClassNotFoundException.class,
					() -> BackwardsCompatibility.getCompatibleClass(Jsonb.class));
			assertEquals("javax" + Jsonb.class.getName().substring(7), classNotFound.getMessage());
			assertEquals(Jsonb.class.getName(), classNotFound.getSuppressed()[0].getMessage());
			return null;
		});
	}

	@Test
	public void testTriesIuType() throws Throwable {
		TypeUtils.callWithContext(LegacyContextSupport.get(), () -> {
			var classNotFound = assertThrows(ClassNotFoundException.class,
					() -> BackwardsCompatibility.getCompatibleClass(IuType.class));
			assertEquals(IuType.class.getName(), classNotFound.getMessage());
			return null;
		});
	}

}
