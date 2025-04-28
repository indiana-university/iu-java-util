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
package iu.type;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.util.jar.JarInputStream;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import edu.iu.type.base.TemporaryFile;

@SuppressWarnings("javadoc")
public class ComponentTargetTest extends IuTypeTestCase {

	@BeforeAll
	public static void setupClass() throws ClassNotFoundException {
		// prevents mockStatic(Files.class) from interfering with class loader
		Class.forName(ComponentTarget.class.getName());
	}

	@Test
	public void testItWorks() throws IOException {
		var path = TemporaryFile.init(temp -> {
			try (var target = new ComponentTarget(temp)) {
				target.put("foo", new ByteArrayInputStream("bar".getBytes()));
			}
			return temp;
		});
		try ( //
				var in = Files.newInputStream(path); //
				var jar = new JarInputStream(in)) {
			assertNotNull(jar.getNextEntry());
			assertEquals("bar", new String(jar.readAllBytes()));
			jar.closeEntry();
			assertNull(jar.getNextEntry());
		}
		Files.delete(path);
	}

}
