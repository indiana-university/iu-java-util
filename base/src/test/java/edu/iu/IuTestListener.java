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
package edu.iu;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.util.ServiceLoader;

import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.mockito.MockedStatic;

@SuppressWarnings({ "javadoc", "exports" })
public class IuTestListener implements IuListener, BeforeEachCallback, AfterEachCallback {

	static UnsafeConsumer<IuObservableEvent> delegate;
	static ServiceLoader<IuListener> loader;
	
	@SuppressWarnings("rawtypes")
	MockedStatic<ServiceLoader> mockSL;

	@Override
	public void accept(IuObservableEvent argument) throws Throwable {
		if (delegate != null)
			delegate.accept(argument);
	}

	@Override
	public void afterEach(ExtensionContext context) throws Exception {
		try {
			resetServiceLoader();
		} finally {
			mockSL.close();
			delegate = null;
		}
	}

	@SuppressWarnings("unchecked")
	@Override
	public void beforeEach(ExtensionContext context) throws Exception {
		resetServiceLoader();
		final IuListener listener = new IuTestListener();
		loader = mock(ServiceLoader.class);
		when(loader.iterator()).thenReturn(IuIterable.iter(listener).iterator());
		mockSL = mockStatic(ServiceLoader.class);
		mockSL.when(() -> ServiceLoader.load(IuListener.class, IuListener.class.getClassLoader())).thenReturn(loader);
		delegate = mock(UnsafeConsumer.class);
	}

	private static void resetServiceLoader() throws ReflectiveOperationException {
		final Field serviceLoader = Class.forName("iu.ListenerSpi").getDeclaredField("serviceLoader");
		serviceLoader.setAccessible(true);
		serviceLoader.set(null, null);
	}

}
