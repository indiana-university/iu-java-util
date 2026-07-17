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
package iu.logging.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.lang.reflect.Proxy;

import org.junit.jupiter.api.Test;

import edu.iu.IdGenerator;

@SuppressWarnings("javadoc")
public class IuLoggingProxyTest {

	interface I {
		String getValue();
	}

	interface O {
		String getValue();
	}

	class Impl {
		final private String value;

		Impl(String value) {
			this.value = value;
		}

		public String getValue() {
			return value;
		}
	}

	@Test
	public void testPassThroughNull() {
		assertNull(IuLoggingProxy.adapt(Object.class, null));
	}

	@Test
	public void testPassThroughSame() {
		final var o = new Object();
		assertSame(o, IuLoggingProxy.adapt(Object.class, o));
	}

	@Test
	public void testProxy() {
		final var value = IdGenerator.generateId();
		final var impl = new Impl(value);
		final var i = IuLoggingProxy.adapt(I.class, impl);
		assertEquals(value, i.getValue());
	}

	@Test
	public void testProxySwap() {
		final var value = IdGenerator.generateId();
		final var impl = new Impl(value);
		final var o = IuLoggingProxy.adapt(O.class, IuLoggingProxy.adapt(I.class, impl));
		assertEquals(value, o.getValue());
	}

	@Test
	public void testProxyImpl() {
		final var value = IdGenerator.generateId();
		final var impl = new Impl(value);
		final var o = IuLoggingProxy.adapt(O.class,
				Proxy.newProxyInstance(I.class.getClassLoader(), new Class<?>[] { I.class },
						(proxy, method, args) -> Impl.class.getMethod(method.getName(), method.getParameterTypes())
								.invoke(impl, args)));
		assertEquals(value, o.getValue());
	}

}
