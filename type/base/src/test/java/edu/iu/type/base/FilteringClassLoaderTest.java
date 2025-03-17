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
package edu.iu.type.base;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import edu.iu.IuIterable;
import jakarta.annotation.Resource;

@SuppressWarnings("javadoc")
public class FilteringClassLoaderTest {

	@Test
	public void testPlatform() throws Throwable {
		final var loader = new FilteringClassLoader(IuIterable.empty(), getClass().getClassLoader());
		assertThrows(ClassNotFoundException.class, () -> loader.loadClass(getClass().getName()));
		assertDoesNotThrow(() -> getClass().getClassLoader().loadClass("javax.sql.DataSource"));
		assertThrows(ClassNotFoundException.class, () -> loader.loadClass("javax.sql.DataSource"));
		assertThrows(ClassNotFoundException.class, () -> loader.loadClass(Resource.class.getName()));
		assertSame(Object.class, loader.loadClass(Object.class.getName()));
	}

	@Test
	public void testAllowed() throws Throwable {
		final var loader = new FilteringClassLoader( // full class name isn't a package --v
				IuIterable.iter("javax.sql", getClass().getPackageName(), Resource.class.getName()),
				getClass().getClassLoader());
		assertSame(getClass(), loader.loadClass(getClass().getName()));
		assertSame(getClass().getClassLoader().loadClass("javax.sql.DataSource"),
				loader.loadClass("javax.sql.DataSource"));
		assertThrows(ClassNotFoundException.class, () -> loader.loadClass(Resource.class.getName()));
	}

}
