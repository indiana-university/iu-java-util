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
package iu.logging.internal;

import java.net.InetAddress;
import java.util.logging.Level;

import edu.iu.IuException;
import iu.logging.LogContext;

/**
 * {@link LogContext} to fall back on, by context, when none is active on the
 * current thread.
 */
public class DefaultLogContext implements LogContext {

	private final String nodeId = IuException.unchecked(() -> InetAddress.getLocalHost().getHostName());
	private final String endpoint;
	private final String application;
	private final String environment;

	/**
	 * Constructor
	 * 
	 * @param endpoint    {@link #endpoint}
	 * @param application {@link #application}
	 * @param environment {@link #environment}
	 */
	public DefaultLogContext(String endpoint, String application, String environment) {
		this.endpoint = endpoint;
		this.application = application;
		this.environment = environment;
	}

	@Override
	public String getRequestId() {
		return null;
	}

	@Override
	public String getNodeId() {
		return nodeId;
	}

	@Override
	public String getEndpoint() {
		return endpoint;
	}

	@Override
	public String getApplication() {
		return application;
	}

	@Override
	public String getEnvironment() {
		return environment;
	}

	@Override
	public String getModule() {
		return null;
	}

	@Override
	public String getComponent() {
		return null;
	}

	@Override
	public Level getLevel() {
		return null;
	}

	@Override
	public boolean isDevelopment() {
		return false;
	}

	@Override
	public String getCallerIpAddress() {
		return null;
	}

	@Override
	public String getCalledUrl() {
		return null;
	}

	@Override
	public String getCallerPrincipalName() {
		return null;
	}

	@Override
	public String getImpersonatedPrincipalName() {
		return null;
	}

	@Override
	public String toString() {
		return "DefaultLogContext [nodeId=" + nodeId + ", endpoint=" + endpoint + ", application=" + application
				+ ", environment=" + environment + "]";
	}

}
