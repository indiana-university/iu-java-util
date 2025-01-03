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
import java.util.function.Function;

import edu.iu.IuException;
import edu.iu.IuRuntimeEnvironment;
import iu.logging.LogContext;
import iu.logging.LogEnvironment;

/**
 * {@link LogContext} to fall back on, by context, when none is active on the
 * current thread.
 */
public class LogEnvironmentImpl implements LogEnvironment {

	private final LogEnvironmentImpl defaults;
	private final String nodeId;
	private final boolean development;
	private final String endpoint;
	private final String application;
	private final String environment;
	private final String module;
	private final String runtime;
	private final String component;

	/**
	 * Default constructor
	 */
	public LogEnvironmentImpl() {
		this(null, //
				IuException.unchecked(() -> InetAddress.getLocalHost().getHostName()), //
				"true".equals(IuRuntimeEnvironment.envOptional("iu.development")), //
				IuRuntimeEnvironment.envOptional("iu.endpoint"), //
				IuRuntimeEnvironment.envOptional("iu.application"), //
				IuRuntimeEnvironment.envOptional("iu.environment"), //
				IuRuntimeEnvironment.envOptional("iu.module"), //
				IuRuntimeEnvironment.envOptional("iu.runtime"), //
				IuRuntimeEnvironment.envOptional("iu.component") //
		);
	}

	/**
	 * Constructor
	 *
	 * @param defaults    {@link defaults}
	 * @param nodeId      {@link #nodeId}
	 * @param development {@link #development}
	 * @param endpoint    {@link #endpoint}
	 * @param application {@link #application}
	 * @param environment {@link #environment}
	 * @param module      {@link #module}
	 * @param runtime     {@link #runtime}
	 * @param component   {@link #component}
	 */
	public LogEnvironmentImpl(LogEnvironmentImpl defaults, String nodeId, boolean development, String endpoint,
			String application, String environment, String module, String runtime, String component) {
		this.defaults = defaults;
		this.nodeId = nodeId;
		this.development = development;
		this.endpoint = endpoint;
		this.application = application;
		this.environment = environment;
		this.module = module;
		this.runtime = runtime;
		this.component = component;
	}

	private <T> T get(T value, Function<LogEnvironmentImpl, T> defaultFunction) {
		if (value != null)
			return value;
		if (defaults == null)
			return null;
		return defaultFunction.apply(defaults);
	}

	@Override
	public boolean isDevelopment() {
		return development || Boolean.TRUE.equals(get(null, LogEnvironmentImpl::isDevelopment));
	}

	@Override
	public String getNodeId() {
		return get(nodeId, LogEnvironmentImpl::getNodeId);
	}

	@Override
	public String getEndpoint() {
		return get(endpoint, LogEnvironmentImpl::getEndpoint);
	}

	@Override
	public String getApplication() {
		return get(application, LogEnvironmentImpl::getApplication);
	}

	@Override
	public String getEnvironment() {
		return get(environment, LogEnvironmentImpl::getEnvironment);
	}

	@Override
	public String getModule() {
		return get(module, LogEnvironmentImpl::getModule);
	}

	@Override
	public String getRuntime() {
		return get(runtime, LogEnvironmentImpl::getRuntime);
	}

	@Override
	public String getComponent() {
		return get(component, LogEnvironmentImpl::getComponent);
	}

	private static void append(StringBuilder sb, String label, Object value) {
		if (value == null)
			return;
		if (sb.charAt(sb.length() - 1) != '[')
			sb.append(", ");
		sb.append(label);
		sb.append('=');
		sb.append(value);
	}

	@Override
	public String toString() {
		final var sb = new StringBuilder("LogEnvironment [");
		append(sb, "nodeId", nodeId);
		append(sb, "development", development);
		append(sb, "endpoint", endpoint);
		append(sb, "application", application);
		append(sb, "environment", environment);
		append(sb, "module", module);
		append(sb, "runtime", runtime);
		append(sb, "component", component);
		append(sb, "defaults", defaults);
		sb.append(']');
		return sb.toString();
	}

}
