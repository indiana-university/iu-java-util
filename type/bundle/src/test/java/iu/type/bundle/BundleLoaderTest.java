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
package iu.type.bundle;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.reflect.Type;

import org.junit.jupiter.api.Test;

import edu.iu.IuVisitor;
import edu.iu.type.spi.IuTypeSpi;

@SuppressWarnings("javadoc")
public class BundleLoaderTest {

	@Test
	public void testClassLoadingFilters() throws ClassNotFoundException {
		final var loader = new BundleClassLoader(getClass().getClassLoader());
		assertSame(IuTypeSpi.class, loader.loadClass(IuTypeSpi.class.getName()));
		assertSame(IuVisitor.class, loader.loadClass(IuVisitor.class.getName()));
		assertSame(Type.class, loader.loadClass(Type.class.getName()));
		assertThrows(ClassNotFoundException.class, () -> loader.loadClass(jakarta.annotation.Resource.class.getName()));
		assertThrows(ClassNotFoundException.class, () -> loader.loadClass(javax.annotation.Resource.class.getName()));
		assertThrows(ClassNotFoundException.class, () -> loader.loadClass(Test.class.getName()));
	}

}
