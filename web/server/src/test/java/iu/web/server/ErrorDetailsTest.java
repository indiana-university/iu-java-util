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
package iu.web.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.util.concurrent.ThreadLocalRandom;

import org.junit.jupiter.api.Test;

import edu.iu.IdGenerator;
import edu.iu.IuWebUtils;
import edu.iu.client.IuJson;
import edu.iu.web.IuWebContext;

@SuppressWarnings("javadoc")
public class ErrorDetailsTest {

	@Test
	public void testJson() {
		final var application = IdGenerator.generateId();
		final var environment = IdGenerator.generateId();
		final var module = IdGenerator.generateId();
		final var runtime = IdGenerator.generateId();
		final var component = IdGenerator.generateId();
		final var supportPreText = IdGenerator.generateId();
		final var supportUrl = IdGenerator.generateId();
		final var supportLabel = IdGenerator.generateId();

		final var webContext = mock(IuWebContext.class);
		when(webContext.getApplication()).thenReturn(application);
		when(webContext.getEnvironment()).thenReturn(environment);
		when(webContext.getModule()).thenReturn(module);
		when(webContext.getRuntime()).thenReturn(runtime);
		when(webContext.getComponent()).thenReturn(component);
		when(webContext.getSupportPreText()).thenReturn(supportPreText);
		when(webContext.getSupportUrl()).thenReturn(supportUrl);
		when(webContext.getSupportLabel()).thenReturn(supportLabel);

		final var nodeId = IdGenerator.generateId();
		final var requestNumber = IdGenerator.generateId();
		final int status;
		{
			int s;
			do
				s = ThreadLocalRandom.current().nextInt();
			while (s == 0);
			status = s;
		}
		final var errorDetails = new ErrorDetails(nodeId, requestNumber, webContext, status);

		final var statusDescr = IdGenerator.generateId();
		try (final var mockIuWebUtils = mockStatic(IuWebUtils.class)) {
			mockIuWebUtils.when(() -> IuWebUtils.describeStatus(status)).thenReturn(statusDescr);
			final var json = IuJson.parse(errorDetails.toString()).asJsonObject();
			assertEquals(nodeId, json.getString("nodeId"));
			assertEquals(requestNumber, json.getString("requestNumber"));
			assertEquals(application, json.getString("application"));
			assertEquals(environment, json.getString("environment"));
			assertEquals(module, json.getString("module"));
			assertEquals(runtime, json.getString("runtime"));
			assertEquals(component, json.getString("component"));
			assertEquals(supportPreText, json.getString("supportPreText"));
			assertEquals(supportUrl, json.getString("supportUrl"));
			assertEquals(supportLabel, json.getString("supportLabel"));
			assertEquals(status, json.getInt("status"), Integer.toString(status));
			assertEquals(statusDescr, json.getString("message"));
			assertEquals(status == 400 || status != 503 && status >= 500, json.getBoolean("severe"));
		}
	}

	@Test
	public void testSevere() {
		assertFalse(
				IuJson.parse(new ErrorDetails(null, null, null, 0).toString()).asJsonObject().containsKey("severe"));
		assertTrue(
				IuJson.parse(new ErrorDetails(null, null, null, 400).toString()).asJsonObject().getBoolean("severe"));
		assertFalse(
				IuJson.parse(new ErrorDetails(null, null, null, 403).toString()).asJsonObject().getBoolean("severe"));
		assertFalse(
				IuJson.parse(new ErrorDetails(null, null, null, 404).toString()).asJsonObject().getBoolean("severe"));
		assertTrue(
				IuJson.parse(new ErrorDetails(null, null, null, 500).toString()).asJsonObject().getBoolean("severe"));
		assertTrue(
				IuJson.parse(new ErrorDetails(null, null, null, 501).toString()).asJsonObject().getBoolean("severe"));
		assertFalse(
				IuJson.parse(new ErrorDetails(null, null, null, 503).toString()).asJsonObject().getBoolean("severe"));
	}

}
