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

import edu.iu.IuWebUtils;
import edu.iu.client.IuJson;
import edu.iu.web.IuWebContext;

/**
 * Captures error details for reporting back to the client as a JSON object.
 */
class ErrorDetails {

	private final String nodeId;
	private final String requestNumber;
	private final IuWebContext webContext;
	private final int status;
	private final boolean severe;

	/**
	 * Constructor.
	 * 
	 * @param nodeId
	 * @param requestNumber
	 * @param webContext
	 * @param status
	 */
	ErrorDetails(String nodeId, String requestNumber, IuWebContext webContext, int status) {
		this.nodeId = nodeId;
		this.requestNumber = requestNumber;
		this.webContext = webContext;
		this.status = status;
		severe = status == 400 || (status != 503 && status >= 500);
	}

	@Override
	public String toString() {
		final var b = IuJson.object();
		IuJson.add(b, "nodeId", nodeId);
		IuJson.add(b, "requestNumber", requestNumber);
		if (status != 0) {
			b.add("status", status);
			b.add("message", IuWebUtils.describeStatus(status));
			b.add("severe", severe);
		}

		if (webContext != null) {
			IuJson.add(b, "application", webContext.getApplication());
			IuJson.add(b, "environment", webContext.getEnvironment());
			IuJson.add(b, "module", webContext.getModule());
			IuJson.add(b, "runtime", webContext.getRuntime());
			IuJson.add(b, "component", webContext.getComponent());
			IuJson.add(b, "supportPreText", webContext.getSupportPreText());
			IuJson.add(b, "supportUrl", webContext.getSupportUrl());
			IuJson.add(b, "supportLabel", webContext.getSupportLabel());
		}

		return b.build().toString();
	}

}
