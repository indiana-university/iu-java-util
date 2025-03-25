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
package edu.iu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.concurrent.TimeoutException;

import org.junit.jupiter.api.Test;

@SuppressWarnings("javadoc")
public class IuUtilityTaskControllerTest {

	@Test
	public void testTask() throws Throwable {
		assertEquals("foo", new IuUtilityTaskController<>(() -> "foo", Instant.now().plusSeconds(1L)).get());
	}

	@Test
	public void testGetBefore() throws Throwable {
		assertEquals("foo", IuUtilityTaskController.getBefore(() -> "foo", Instant.now().plusSeconds(100L)));
	}

	@Test
	public void testDoBefore() throws Throwable {
		class Box {
			boolean done;
		}
		final var box = new Box();
		IuUtilityTaskController.doBefore(() -> {
			box.done = true;
		}, Instant.now().plusSeconds(1L));
		assertTrue(box.done);
	}

	@Test
	public void testError() throws Throwable {
		final var e = new Exception();
		assertSame(e, assertThrows(Exception.class, () -> IuUtilityTaskController.getBefore(() -> {
			throw e;
		}, Instant.now().plusSeconds(1L))));
	}

	@Test
	public void testTimeout() throws Throwable {
		final var t = System.currentTimeMillis();
		assertThrows(TimeoutException.class, () -> IuUtilityTaskController.doBefore(() -> {
			Thread.sleep(2000L);
		}, Instant.now().plusSeconds(1L)));
		assertTrue(System.currentTimeMillis() - t < 1500L, Long.toString(t - System.currentTimeMillis()));
	}

}
