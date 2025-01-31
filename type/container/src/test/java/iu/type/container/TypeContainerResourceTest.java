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
package iu.type.container;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.logging.Level;

import org.junit.jupiter.api.Test;

import edu.iu.UnsafeRunnable;
import edu.iu.test.IuTestLogger;
import edu.iu.type.IuComponent;
import edu.iu.type.IuResource;
import edu.iu.type.IuType;

@SuppressWarnings("javadoc")
public class TypeContainerResourceTest extends TypeContainerTestCase {

	@Test
	public void testNonRunnable() {
		class A {
		}
		final var resource = mock(IuResource.class);
		when(resource.type()).thenReturn(IuType.of(A.class));
		final var component = mock(IuComponent.class);
		IuTestLogger.expect(TypeContainerResource.class.getName(), Level.FINE, "init resource " + resource);
		final var containerResource = new TypeContainerResource(resource, component);
		assertThrows(IllegalStateException.class, containerResource::join);
		containerResource.asyncInit();
		assertDoesNotThrow(containerResource::asyncInit);
		assertDoesNotThrow(containerResource::join);
	}

	@Test
	public void testRunnable() {
		class A implements Runnable {
			boolean run;

			@Override
			public void run() {
				this.run = true;
			}
		}
		final var a = new A();
		final var resource = mock(IuResource.class);
		when(resource.get()).thenReturn(a);
		when(resource.type()).thenReturn(IuType.of(A.class));
		final var component = mock(IuComponent.class);

		IuTestLogger.expect(TypeContainerResource.class.getName(), Level.FINE, "init resource " + resource);

		final var containerResource = new TypeContainerResource(resource, component); // starts thread
		containerResource.asyncInit();
		assertDoesNotThrow(() -> containerResource.join());
		assertTrue(a.run);
	}

	@Test
	public void testUnsafeRunnable() {
		class A implements UnsafeRunnable {
			boolean run;

			@Override
			public void run() {
				this.run = true;
			}
		}
		final var a = new A();
		final var resource = mock(IuResource.class);
		when(resource.get()).thenReturn(a);
		when(resource.type()).thenReturn(IuType.of(A.class));
		final var component = mock(IuComponent.class);

		IuTestLogger.expect(TypeContainerResource.class.getName(), Level.FINE, "init resource " + resource);

		final var containerResource = new TypeContainerResource(resource, component); // starts thread
		containerResource.asyncInit();
		assertDoesNotThrow(() -> containerResource.join());
		assertTrue(a.run);
	}

	@Test
	public void testError() {
		final var error = new IllegalStateException();
		class A implements Runnable {
			@Override
			public void run() {
				throw error;
			}
		}
		final var a = new A();
		final var resource = mock(IuResource.class);
		when(resource.get()).thenReturn(a);
		when(resource.type()).thenReturn(IuType.of(A.class));
		final var component = mock(IuComponent.class);

		IuTestLogger.expect(TypeContainerResource.class.getName(), Level.FINE, "init resource " + resource);
		IuTestLogger.expect(TypeContainerResource.class.getName(), Level.SEVERE, "fail resource " + resource,
				IllegalStateException.class, e -> e == error);

		final var containerResource = new TypeContainerResource(resource, component);
		containerResource.asyncInit();
		assertSame(error, assertThrows(IllegalStateException.class, () -> containerResource.join()));
	}

	@Test
	public void testCompareToSamePriority() {
		class A {
			// priority == 0 -> instance creation order
		}
		final var a = new A();
		final var component = mock(IuComponent.class);
		final var aResource = mock(IuResource.class);
		when(aResource.get()).thenReturn(a);
		when(aResource.type()).thenReturn(IuType.of(A.class));

		final var acr = new TypeContainerResource(aResource, component);
		final var bcr = new TypeContainerResource(aResource, component);
		assertEquals(-1, acr.compareTo(bcr));
		assertEquals(1, bcr.compareTo(acr));
	}

	@Test
	public void testCompareToNonNegativeHigh() {
		class A {
			// non-negative n => first (higher priority)
			// priority == 0 < -1
		}
		final var component = mock(IuComponent.class);
		final var aResource = mock(IuResource.class);
		when(aResource.get()).thenReturn(new A());
		when(aResource.type()).thenReturn(IuType.of(A.class));
		when(aResource.priority()).thenReturn(-1);
		final var acr = new TypeContainerResource(aResource, component);

		final var bResource = mock(IuResource.class);
		when(bResource.get()).thenReturn(new A());
		when(bResource.type()).thenReturn(IuType.of(A.class));
		when(bResource.priority()).thenReturn(0);
		final var bcr = new TypeContainerResource(bResource, component);
		assertEquals(1, acr.compareTo(bcr));
		assertEquals(-1, bcr.compareTo(acr));
	}

	@Test
	public void testCompareToBothNonNegative() {
		class A {
			// lower abs(n) => first (higher priority)
			// priority == 0 < 1
		}
		final var component = mock(IuComponent.class);
		final var aResource = mock(IuResource.class);
		when(aResource.get()).thenReturn(new A());
		when(aResource.type()).thenReturn(IuType.of(A.class));
		when(aResource.priority()).thenReturn(1);
		final var acr = new TypeContainerResource(aResource, component);

		final var bResource = mock(IuResource.class);
		when(bResource.get()).thenReturn(new A());
		when(bResource.type()).thenReturn(IuType.of(A.class));
		when(bResource.priority()).thenReturn(0);
		final var bcr = new TypeContainerResource(bResource, component);
		assertEquals(1, acr.compareTo(bcr));
		assertEquals(-1, bcr.compareTo(acr));
	}

	@Test
	public void testCompareToBothNegative() {
		class A {
			// lower abs(n) => first (higher priority)
			// priority == -1 < -2
		}
		final var component = mock(IuComponent.class);
		final var aResource = mock(IuResource.class);
		when(aResource.get()).thenReturn(new A());
		when(aResource.type()).thenReturn(IuType.of(A.class));
		when(aResource.priority()).thenReturn(-2);
		final var acr = new TypeContainerResource(aResource, component);

		final var bResource = mock(IuResource.class);
		when(bResource.get()).thenReturn(new A());
		when(bResource.type()).thenReturn(IuType.of(A.class));
		when(bResource.priority()).thenReturn(-1);
		final var bcr = new TypeContainerResource(bResource, component);
		assertEquals(1, acr.compareTo(bcr));
		assertEquals(-1, bcr.compareTo(acr));
	}

}
