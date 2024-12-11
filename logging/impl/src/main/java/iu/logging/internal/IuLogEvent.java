package iu.logging.internal;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Instant;
import java.util.Objects;
import java.util.logging.Formatter;
import java.util.logging.Level;
import java.util.logging.LogRecord;

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
	private final String environment;
	private final String application;
	private final String module;
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
		final var context = Objects.requireNonNullElse(ProcessLogger.getActiveContext(), Bootstrap.getDefaultContext());

		level = record.getLevel();
		loggerName = record.getLoggerName();

		requestId = context.getRequestId();
		environment = context.getEnvironment();
		application = context.getApplication();
		module = context.getModule();
		component = context.getComponent();
		nodeId = context.getNodeId();
		callerIpAddress = context.getCallerIpAddress();
		calledUrl = context.getCalledUrl();
		callerPrincipalName = context.getCallerPrincipalName();
		impersonatedPrincipalName = context.getImpersonatedPrincipalName();

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
	 * Formats the log message as human-readable.
	 * 
	 * @return human-readable log message
	 */
	String format() {
		StringBuilder sb = new StringBuilder();
		append(sb, level);
		append(sb, requestId);
		append(sb, environment);
		append(sb, application);
		append(sb, module);
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
	Level getLevel() {
		return level;
	}

	/**
	 * Gets {@link #loggerName}
	 * 
	 * @return {@link #loggerName}
	 */
	String getLoggerName() {
		return loggerName;
	}

	/**
	 * Gets {@link #requestId}
	 * 
	 * @return {@link #requestId}
	 */
	String getRequestId() {
		return requestId;
	}

	/**
	 * Gets {@link #environment}
	 * 
	 * @return {@link #environment}
	 */
	String getEnvironment() {
		return environment;
	}

	/**
	 * Gets {@link #application}
	 * 
	 * @return {@link #application}
	 */
	String getApplication() {
		return application;
	}

	/**
	 * Gets {@link #module}
	 * 
	 * @return {@link #module}
	 */
	String getModule() {
		return module;
	}

	/**
	 * Gets {@link #component}
	 * 
	 * @return {@link #component}
	 */
	String getComponent() {
		return component;
	}

	/**
	 * Gets {@link #nodeId}
	 * 
	 * @return {@link #nodeId}
	 */
	String getNodeId() {
		return nodeId;
	}

	/**
	 * Gets {@link #thread}
	 * 
	 * @return {@link #thread}
	 */
	String getThread() {
		return thread;
	}

	/**
	 * Gets {@link #callerIpAddress}
	 * 
	 * @return {@link #callerIpAddress}
	 */
	String getCallerIpAddress() {
		return callerIpAddress;
	}

	/**
	 * Gets {@link #calledUrl}
	 * 
	 * @return {@link #calledUrl}
	 */
	String getCalledUrl() {
		return calledUrl;
	}

	/**
	 * Gets {@link #callerPrincipalName}
	 * 
	 * @return {@link #callerPrincipalName}
	 */
	String getCallerPrincipalName() {
		return callerPrincipalName;
	}

	/**
	 * Gets {@link #impersonatedPrincipalName}
	 * 
	 * @return {@link #impersonatedPrincipalName}
	 */
	String getImpersonatedPrincipalName() {
		return impersonatedPrincipalName;
	}

	/**
	 * Gets {@link #timestamp}
	 * 
	 * @return {@link #timestamp}
	 */
	Instant getTimestamp() {
		return timestamp;
	}

	/**
	 * Gets {@link #sourceClassName}
	 * 
	 * @return {@link #sourceClassName}
	 */
	String getSourceClassName() {
		return sourceClassName;
	}

	/**
	 * Gets {@link #sourceMethodName}
	 * 
	 * @return {@link #sourceMethodName}
	 */
	String getSourceMethodName() {
		return sourceMethodName;
	}

	/**
	 * Gets {@link #message}
	 * 
	 * @return {@link #message}
	 */
	String getMessage() {
		return message;
	}

	/**
	 * Gets {@link #processLog}
	 * 
	 * @return {@link #processLog}
	 */
	String getProcessLog() {
		return processLog;
	}

	/**
	 * Gets {@link #error}
	 * 
	 * @return {@link #error}
	 */
	String getError() {
		return error;
	}

}