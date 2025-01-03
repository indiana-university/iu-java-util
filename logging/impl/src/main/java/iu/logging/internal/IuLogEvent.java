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

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Instant;
import java.util.logging.Formatter;
import java.util.logging.Level;
import java.util.logging.LogRecord;

import edu.iu.client.IuJson;
import iu.logging.Bootstrap;

/**
 * Fully resolved buffered log event holder.
 */
class IuLogEvent {

	private static final Formatter MESSAGE_FORMATTER = new Formatter() {
		@Override
		public String format(LogRecord record) {
			return formatMessage(record);
		}
	};

	private final Level level;
	private final String loggerName;
	private final String requestId;
	private final String application;
	private final String environment;
	private final String module;
	private final String runtime;
	private final String component;
	private final String nodeId;
	private final String thread = Thread.currentThread().getName();
	private final String callerIpAddress;
	private final String calledUrl;
	private final String callerPrincipalName;
	private final String impersonatedPrincipalName;
	private final Instant timestamp;
	private final String sourceClassName;
	private final String sourceMethodName;
	private final String message;
	private final String processLog;
	private final String error;

	/**
	 * Constructor.
	 * 
	 * @param record {@link LogRecord}
	 */
	IuLogEvent(LogRecord record) {
		final var env = Bootstrap.getEnvironment();
		final var context = ProcessLogger.getActiveContext();

		level = record.getLevel();
		loggerName = record.getLoggerName();

		application = env.getApplication();
		environment = env.getEnvironment();
		module = env.getModule();
		component = env.getComponent();
		runtime = env.getRuntime();
		nodeId = env.getNodeId();

		if (context != null) {
			requestId = context.getRequestId();
			callerIpAddress = context.getCallerIpAddress();
			calledUrl = context.getCalledUrl();
			callerPrincipalName = context.getCallerPrincipalName();
			impersonatedPrincipalName = context.getImpersonatedPrincipalName();
		} else {
			requestId = null;
			callerIpAddress = null;
			calledUrl = null;
			callerPrincipalName = null;
			impersonatedPrincipalName = null;
		}

		timestamp = record.getInstant();
		sourceClassName = record.getSourceClassName();
		sourceMethodName = record.getSourceMethodName();
		message = MESSAGE_FORMATTER.format(record);
		if (level.intValue() >= Level.WARNING.intValue())
			processLog = ProcessLogger.export();
		else
			processLog = null;

		Throwable thrown = record.getThrown();
		if (thrown != null) {
			StringWriter w = new StringWriter();
			thrown.printStackTrace(new PrintWriter(w));
			error = w.toString();
		} else
			error = null;
	}

	private void append(StringBuilder sb, Object value) {
		if (value != null)
			sb.append(value);
		sb.append(',');
	}

	/**
	 * Exports the log message as JSON.
	 * 
	 * @return JSON formatted log message
	 */
	String export() {
		final var builder = IuJson.object();
		IuJson.add(builder, "level", level.getName());
		IuJson.add(builder, "requestId", requestId);
		IuJson.add(builder, "application", application);
		IuJson.add(builder, "environment", environment);
		IuJson.add(builder, "module", module);
		IuJson.add(builder, "runtime", runtime);
		IuJson.add(builder, "component", component);
		IuJson.add(builder, "nodeId", nodeId);
		IuJson.add(builder, "thread", thread);
		IuJson.add(builder, "callerIpAddress", callerIpAddress);
		IuJson.add(builder, "calledUrl", calledUrl);
		IuJson.add(builder, "callerPrincipalName", callerPrincipalName);
		IuJson.add(builder, "impersonatedPrincipalName", impersonatedPrincipalName);
		IuJson.add(builder, "timestamp", timestamp.toString());
		IuJson.add(builder, "loggerName", loggerName);
		IuJson.add(builder, "sourceClassName", sourceClassName);
		IuJson.add(builder, "sourceMethodName", sourceMethodName);
		IuJson.add(builder, "message", message);
		IuJson.add(builder, "processLog", processLog);
		IuJson.add(builder, "error", error);
		return builder.build().toString();
	}

	/**
	 * Formats the log message as human-readable.
	 * 
	 * @return human-readable log message
	 */
	String format() {
		StringBuilder sb = new StringBuilder();
		append(sb, level);
		append(sb, requestId);
		append(sb, application);
		append(sb, environment);
		append(sb, module);
		append(sb, runtime);
		append(sb, component);
		append(sb, nodeId);
		append(sb, thread);
		append(sb, callerIpAddress);
		append(sb, calledUrl);
		append(sb, callerPrincipalName);
		append(sb, impersonatedPrincipalName);
		append(sb, timestamp);
		append(sb, loggerName);
		if (sourceClassName != null)
			sb.append(sourceClassName);
		if (sourceMethodName != null) {
			sb.append('.');
			sb.append(sourceMethodName);
			sb.append("()");
		}
		sb.append(System.lineSeparator());
		sb.append(message);

		if (processLog != null) {
			sb.append(System.lineSeparator());
			sb.append(processLog);
		}

		if (error != null) {
			sb.append(System.lineSeparator());
			sb.append(error);
		}

		sb.append(System.lineSeparator());
		return sb.toString();
	}

	/**
	 * Gets {@link #level}
	 * 
	 * @return {@link #level}
	 */
	public Level getLevel() {
		return level;
	}

	/**
	 * Gets {@link #loggerName}
	 * 
	 * @return {@link #loggerName}
	 */
	public String getLoggerName() {
		return loggerName;
	}

	/**
	 * Gets {@link #requestId}
	 * 
	 * @return {@link #requestId}
	 */
	public String getRequestId() {
		return requestId;
	}

	/**
	 * Gets {@link #environment}
	 * 
	 * @return {@link #environment}
	 */
	public String getEnvironment() {
		return environment;
	}

	/**
	 * Gets {@link #application}
	 * 
	 * @return {@link #application}
	 */
	public String getApplication() {
		return application;
	}

	/**
	 * Gets {@link #module}
	 * 
	 * @return {@link #module}
	 */
	public String getModule() {
		return module;
	}

	/**
	 * Gets {@link #component}
	 * 
	 * @return {@link #component}
	 */
	public String getComponent() {
		return component;
	}

	/**
	 * Gets {@link #nodeId}
	 * 
	 * @return {@link #nodeId}
	 */
	public String getNodeId() {
		return nodeId;
	}

	/**
	 * Gets {@link #thread}
	 * 
	 * @return {@link #thread}
	 */
	public String getThread() {
		return thread;
	}

	/**
	 * Gets {@link #callerIpAddress}
	 * 
	 * @return {@link #callerIpAddress}
	 */
	public String getCallerIpAddress() {
		return callerIpAddress;
	}

	/**
	 * Gets {@link #calledUrl}
	 * 
	 * @return {@link #calledUrl}
	 */
	public String getCalledUrl() {
		return calledUrl;
	}

	/**
	 * Gets {@link #callerPrincipalName}
	 * 
	 * @return {@link #callerPrincipalName}
	 */
	public String getCallerPrincipalName() {
		return callerPrincipalName;
	}

	/**
	 * Gets {@link #impersonatedPrincipalName}
	 * 
	 * @return {@link #impersonatedPrincipalName}
	 */
	public String getImpersonatedPrincipalName() {
		return impersonatedPrincipalName;
	}

	/**
	 * Gets {@link #timestamp}
	 * 
	 * @return {@link #timestamp}
	 */
	public Instant getTimestamp() {
		return timestamp;
	}

	/**
	 * Gets {@link #sourceClassName}
	 * 
	 * @return {@link #sourceClassName}
	 */
	public String getSourceClassName() {
		return sourceClassName;
	}

	/**
	 * Gets {@link #sourceMethodName}
	 * 
	 * @return {@link #sourceMethodName}
	 */
	public String getSourceMethodName() {
		return sourceMethodName;
	}

	/**
	 * Gets {@link #message}
	 * 
	 * @return {@link #message}
	 */
	public String getMessage() {
		return message;
	}

	/**
	 * Gets {@link #processLog}
	 * 
	 * @return {@link #processLog}
	 */
	public String getProcessLog() {
		return processLog;
	}

	/**
	 * Gets {@link #error}
	 * 
	 * @return {@link #error}
	 */
	public String getError() {
		return error;
	}

	@Override
	public String toString() {
		return format();
	}

}