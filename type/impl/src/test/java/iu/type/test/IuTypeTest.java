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
package iu.type.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;

import edu.iu.type.InstanceReference;
import edu.iu.type.IuType;
import edu.iu.type.testresources.HasBadPreDestroy;
import edu.iu.type.testresources.HasPostConstructAndPreDestroy;
import iu.type.IuTypeTestCase;

@SuppressWarnings("javadoc")
public class IuTypeTest extends IuTypeTestCase {

	@Test
	public void testResolves() {
		var type = IuType.of(Object.class);
		assertNotNull(type);
		assertSame(Object.class, type.deref());
		assertEquals(Object.class.getName(), type.name());
	}

	@Test
	public void testParityWithClass() {
		assertSame(IuType.of(Object.class), IuType.of(Object.class));
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	@Test
	public void testObserveAndSubscribe() {
		InstanceReference ref = mock(InstanceReference.class);
		final IuType t = IuType.of(getClass()).referTo(Object.class);
		t.subscribe(ref);
		final var o1 = new Object();
		t.observe(o1);
		verify(ref).accept(o1);
		t.destroy(o1);
		verify(ref).clear(o1);
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	@Test
	public void testObserveAndUnsubscribe() {
		InstanceReference ref = mock(InstanceReference.class);
		final IuType t = IuType.of(getClass()).referTo(Object.class);
		final var u = t.subscribe(ref);

		final var o1 = new Object();
		t.observe(o1);
		verify(ref).accept(o1);

		u.run();

		final var o2 = new Object();
		t.observe(o2);
		verify(ref, never()).accept(o2);
	}

	@Test
	public void testPostConstructAndPreDestroy() throws Exception {
		final var type = IuType.of(HasPostConstructAndPreDestroy.class);
		final var a = type.constructor().exec();
		assertFalse(a.isInitialized()); // TODO: clarify expectations
		type.destroy(a);
		assertFalse(a.isInitialized());
	}

	@Test
	public void testBadPreDestroy() throws Exception {
		final var type = IuType.of(HasBadPreDestroy.class);
		final var a = type.constructor().exec();
		assertThrows(RuntimeException.class, () -> type.destroy(a));
	}

}
