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
package iu;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.util.List;
import java.util.ServiceLoader;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import edu.iu.IuListener;
import edu.iu.IuObservableEvent;

@SuppressWarnings("javadoc")
public class ListenerSpiTest {

	private Field serviceLoaderField;
	private Object savedServiceLoader;

	@BeforeEach
	public void saveServiceLoader() throws ReflectiveOperationException {
		serviceLoaderField = ListenerSpi.class.getDeclaredField("serviceLoader");
		serviceLoaderField.setAccessible(true);
		savedServiceLoader = serviceLoaderField.get(null);
	}

	@AfterEach
	public void restoreServiceLoader() throws ReflectiveOperationException {
		serviceLoaderField.set(null, savedServiceLoader);
	}

	@Test
	public void testObserveRejectsNullEvent() {
		final var error = assertThrows(NullPointerException.class, () -> ListenerSpi.observe(null));
		assertEquals("event", error.getMessage());
	}

	@Test
	public void testObserveNotifiesListenersInServiceLoaderOrder() throws Throwable {
		final var event = mock(IuObservableEvent.class);
		final var first = mock(IuListener.class);
		final var second = mock(IuListener.class);
		setServiceLoader(first, second);

		ListenerSpi.observe(event);

		final var order = inOrder(first, second);
		order.verify(first).accept(event);
		order.verify(second).accept(event);
	}

	@Test
	public void testObserveContinuesAfterListenerFailure() throws Throwable {
		final var event = mock(IuObservableEvent.class);
		final var first = mock(IuListener.class);
		final var second = mock(IuListener.class);
		doThrow(new RuntimeException("listener failure")).when(first).accept(event);
		setServiceLoader(first, second);

		assertDoesNotThrow(() -> ListenerSpi.observe(event));

		verify(second).accept(event);
	}

	@SuppressWarnings("unchecked")
	private void setServiceLoader(IuListener... listeners) throws ReflectiveOperationException {
		final ServiceLoader<IuListener> loader = mock(ServiceLoader.class);
		when(loader.iterator()).thenReturn(List.of(listeners).iterator());
		serviceLoaderField.set(null, loader);
	}

}
