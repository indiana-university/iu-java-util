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
package edu.iu;

import static org.mockito.ArgumentMatchers.notNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.function.Function;

import org.junit.jupiter.api.Test;

@SuppressWarnings("javadoc")
public class IuVisitorTest {

	@Test
	@SuppressWarnings({ "rawtypes", "unchecked" })
	public void testEmpty() {
		final var visitor = new IuVisitor();
		final Function f = mock(Function.class);
		visitor.visit(f);
		verify(f).apply(null);
	}

	@Test
	@SuppressWarnings({ "rawtypes", "unchecked" })
	public void testVisitOne() {
		final var visitor = new IuVisitor();
		final var one = new Object();
		visitor.accept(one);
		final Function f = mock(Function.class);
		visitor.visit(f);
		verify(f).apply(one);
		verify(f).apply(null);
	}

	@Test
	@SuppressWarnings({ "rawtypes", "unchecked" })
	public void testPicksOne() {
		final var visitor = new IuVisitor();
		final var one = new Object();
		visitor.accept(one);
		final Function f = mock(Function.class);
		when(f.apply(one)).thenReturn(Optional.empty());
		visitor.visit(f);
		verify(f).apply(one);
		verify(f, never()).apply(null);
	}

	@Test
	@SuppressWarnings({ "rawtypes", "unchecked" })
	public void testPrunesClearedRefs() throws InterruptedException {
		final var visitor = new IuVisitor();
		visitor.accept(new Object());
		final Function f = mock(Function.class);
		System.gc();
		Thread.sleep(100L);
		visitor.visit(f);
		verify(f, never()).apply(notNull());
		verify(f).apply(null);
	}

}