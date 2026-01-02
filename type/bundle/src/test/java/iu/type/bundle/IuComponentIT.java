/*
 * Copyright © 2026 Indiana University
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

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.logging.Level;

import org.junit.jupiter.api.Test;

import edu.iu.test.IuTestLogger;
import edu.iu.type.IuComponent;
import edu.iu.type.IuComponent.Kind;

@SuppressWarnings("javadoc")
public class IuComponentIT {

	@Test
	public void testSanity() throws Exception {
		IuTestLogger.allow("iu.type.ParameterizedElement", Level.FINEST, "replaced type argument.*");
		try (final var runtime = IuComponent.of(TestBundleArchives.getComponentArchive("testruntime"),
				TestBundleArchives.getProvidedDependencyArchives("testruntime"))) {
			assertEquals("edu.iu.type.testruntime.TestRuntime", runtime.interfaces().iterator().next().name());
		}
	}

	@Test
	public void testScan() {
		IuComponent component = IuComponent.scan(getClass());
		assertEquals(Kind.ENTRY, component.kind());
	}

}
