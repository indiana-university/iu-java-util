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
package iu.logging;

import java.time.Instant;
import java.util.logging.Level;

import edu.iu.logging.IuLoggingContext;
import edu.iu.logging.IuLoggingEnvironment;

/**
 * IuLogEvent interface
 */
interface IuLogEvent extends IuLoggingContext, IuLoggingEnvironment {

//	String sourceClassName;
//	
//	String sourceMethodName;
	
	default String getLoggerName() {
		return null;
	}
	
	default Level getLevel() {
		return null;
	}
	
	default String getMessage() {
		return null;
	}
	
	default Instant getInstant() {
		return null;
	}
	
	default String getThrown() {
		return null;
	}
	
	
//	private static final Formatter MESSAGE_FORMATTER = new Formatter() {
//		@Override
//		public String format(LogRecord record) {
//			return formatMessage(record);
//		}
//	};

//	public final Level level;
//	public final String loggerName;
//	public final String reqNum = LoggingEnvironment.getRequestNumber();
//	public final String environment = LoggingEnvironment.getEnvironment();
//	public final String application = LoggingEnvironment.getApplication();
//	public final String module = LoggingEnvironment.getModule();
//	public final String component = LoggingEnvironment.getComponent();
//	public final String nodeId = LoggingEnvironment.getNodeId();
//	public final String thread = Thread.currentThread().getName();
//	public final String callerIpAddress = LoggingEnvironment.getCallerIpAddress();
//	public final String calledUrl = LoggingEnvironment.getCalledUrl();
//	public final String callerPrincipalName = LoggingEnvironment.getCallerPrincipalName();
//	public final String impersonatedPrincipalName = LoggingEnvironment.getImpersonatedPrincipalName();
//	public final long timestamp;
//	public final String sourceClassName;
//	public final String sourceMethodName;
//	public final String message;
//	public final String processLog;
//	public final String error;

//	public LogEvent(LogRecord record) {
//		level = record.getLevel();
//		loggerName = record.getLoggerName();
//		timestamp = record.getMillis();
//		sourceClassName = record.getSourceClassName();
//		sourceMethodName = record.getSourceMethodName();
//		message = MESSAGE_FORMATTER.format(record);
//		if (level.intValue() >= Level.WARNING.intValue())
//			processLog = ProcessLogger.export();
//		else
//			processLog = null;
//
//		Throwable thrown = record.getThrown();
//		if (thrown != null) {
//			StringWriter w = new StringWriter();
//			thrown.printStackTrace(new PrintWriter(w));
//			error = w.toString();
//		} else
//			error = null;
//	}
//
//	private void append(StringBuilder sb, Object value) {
//		if (value != null)
//			sb.append(value);
//		sb.append(',');
//	}
//
//	public String formatDebug() {
//		StringBuilder sb = new StringBuilder();
//		append(sb, level);
//		append(sb, reqNum);
//		append(sb, environment);
//		append(sb, application);
//		append(sb, module);
//		append(sb, component);
//		append(sb, nodeId);
//		append(sb, thread);
//		append(sb, callerIpAddress);
//		append(sb, calledUrl);
//		append(sb, callerPrincipalName);
//		append(sb, impersonatedPrincipalName);
//		append(sb, new Date(timestamp));
//		append(sb, loggerName);
//		sb.append(sourceClassName);
//		sb.append('.');
//		sb.append(sourceMethodName);
//		sb.append("()\n");
//		sb.append(message);
//
//		if (error != null) {
//			sb.append('\n');
//			sb.append(error);
//		}
//
//		sb.append('\n');
//		return sb.toString();
//	}
//
//	private static ThreadLocal<DateFormat> UTC_DATE_FORMAT = new ThreadLocal<DateFormat>() {
//		@Override
//		protected DateFormat initialValue() {
//			DateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
//			format.setTimeZone(TimeZone.getTimeZone("UTC"));
//			format.setLenient(false);
//			return format;
//		}
//	};
//
//	private void append(JsonObjectBuilder json, String name, Object value) {
//		if (value instanceof Throwable) {
//			Throwable thrown = (Throwable) value;
//			StringWriter w = new StringWriter();
//			thrown.printStackTrace(new PrintWriter(w));
//			json.add(name, w.toString());
//		} else if (value != null)
//			json.add(name, value.toString());
//	}
//
//	public String formatJson() {
//		JsonObjectBuilder json = Json.createObjectBuilder();
//		append(json, "level", level);
//		append(json, "reqNum", reqNum);
//		append(json, "environment", environment);
//		append(json, "application", application);
//		append(json, "module", module);
//		append(json, "component", component);
//		append(json, "nodeId", nodeId);
//		append(json, "thread", thread);
//		append(json, "callerIpAddress", callerIpAddress);
//		append(json, "calledUrl", calledUrl);
//		append(json, "callerPrincipalName", callerPrincipalName);
//		append(json, "impersonatedPrincipalName", impersonatedPrincipalName);
//		append(json, "timestamp", UTC_DATE_FORMAT.get().format(new Date(timestamp)));
//		append(json, "loggerName", loggerName);
//		append(json, "sourceClassName", sourceClassName);
//		append(json, "sourceMethodName", sourceMethodName);
//		append(json, "message", message);
//		append(json, "error", error);
//		return json.build().toString();
//	}
//
//	public String formatError() {
//		StringBuilder sb = new StringBuilder();
//		if (application != null) {
//			sb.append(application);
//			if (module != null) {
//				sb.append("/");
//				sb.append(module);
//				if (component != null) {
//					sb.append("/");
//					sb.append(component);
//				}
//			}
//			sb.append(" ");
//		}
//		if (environment != null)
//			sb.append(environment).append(" ");
//
//		if (sb.length() > 0)
//			sb.append(' ');
//		sb.append(level);
//		sb.append(" event at ");
//		sb.append(new Date(timestamp));
//		sb.append("\nMethod: ");
//		sb.append(',');
//		sb.append(sourceClassName);
//		sb.append('.');
//		sb.append(sourceMethodName);
//		sb.append("\nThread: ");
//		sb.append(thread);
//
//		if (reqNum != null) {
//			sb.append("\nRequest ID: ");
//			sb.append(reqNum);
//		}
//
//		if (callerIpAddress != null) {
//			sb.append("\nCaller IP Address: ");
//			sb.append(callerIpAddress);
//		}
//
//		if (calledUrl != null) {
//			sb.append("\nCalled URL: ");
//			sb.append(calledUrl);
//		}
//
//		if (callerPrincipalName != null) {
//			sb.append("\nCaller Principal: ");
//			sb.append(callerPrincipalName);
//		}
//
//		if (impersonatedPrincipalName != null) {
//			sb.append("\nImpersonated Principal: ");
//			sb.append(impersonatedPrincipalName);
//		}
//
//		sb.append("\n");
//		sb.append(message);
//
//		if (processLog != null)
//			sb.append("\nProcess Log: ").append(processLog);
//
//		if (error != null) {
//			sb.append('\n');
//			sb.append(error);
//		}
//
//		sb.append('\n');
//		return sb.toString();
//	}

}