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
package edu.iu.logging;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

import java.util.concurrent.ThreadLocalRandom;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import edu.iu.IdGenerator;
import edu.iu.UnsafeSupplier;
import iu.logging.boot.IuLoggingBootstrap;

@SuppressWarnings("javadoc")
public class IuLogContextTest {

	private MockedStatic<IuLoggingBootstrap> mockLoggingBootstrap;

	@BeforeEach
	public void setup() {
		mockLoggingBootstrap = mockStatic(IuLoggingBootstrap.class);
	}

	@AfterEach
	public void teardown() {
		mockLoggingBootstrap.close();
	}

	@Test
	public void testInitializeContext() {
		final var nodeId = IdGenerator.generateId();
		final var development = ThreadLocalRandom.current().nextBoolean();
		final var endpoint = IdGenerator.generateId();
		final var application = IdGenerator.generateId();
		final var environment = IdGenerator.generateId();
		final var module = IdGenerator.generateId();
		final var runtime = IdGenerator.generateId();
		final var component = IdGenerator.generateId();
		assertDoesNotThrow(() -> IuLogContext.initializeContext(nodeId, development, endpoint, application, environment,
				module, runtime, component));
		mockLoggingBootstrap.verify(() -> IuLoggingBootstrap.initializeContext(nodeId, development, endpoint,
				application, environment, module, runtime, component));
	}

	@SuppressWarnings("unchecked")
	@Test
	public void testFollow() {
		final var context = mock(IuLogContext.class);
		final var message = IdGenerator.generateId();
		final var supplier = mock(UnsafeSupplier.class);
		assertDoesNotThrow(() -> IuLogContext.follow(context, message, supplier));
		mockLoggingBootstrap.verify(() -> IuLoggingBootstrap.follow(context, message, supplier));
	}

}
