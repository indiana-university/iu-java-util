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
package iu.logging;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Instant;
import java.util.logging.Formatter;
import java.util.logging.Level;
import java.util.logging.LogRecord;

import edu.iu.logging.IuLogEvent;
import edu.iu.logging.IuLoggingContext;
import edu.iu.logging.IuLoggingEnvironment;

/**
 * Implementation of IuLogEvent interface.
 */
public class LogEvent implements IuLogEvent {

	private static final Formatter MESSAGE_FORMATTER = new Formatter() {
		@Override
		public String format(LogRecord record) {
			return formatMessage(record);
		}
	};

	// Values from LogRecord
	private Instant instant;
	private Level level;
	private String loggerName;
	private String message;
	private String sourceClassName;
	private String sourceMethodName;
	private String thrown;

	// Values from IuLoggingContext
	private String authenticatedPrincipal;
	private String calledUrl;
	private String remoteAddr;
	private String reqNum;
	private String userPrincipal;

	// Values from IuLoggingEnvironment
	private String application;
	private String component;
	private String environment;
	private String hostname;
	private RuntimeMode mode;
	private String module;
	private String nodeId;
	private String runtime;

	private String thread = Thread.currentThread().getName();

	/**
	 * Default constructor.
	 */
	public LogEvent() {

	}

	/**
	 * Create a LogEvent from the given LogRecord, IuLoggingContext, and
	 * IuLoggingEnvironment.
	 * 
	 * @param record      The LogRecord.
	 * @param context     The IuLoggingContext.
	 * @param environment The IuLoggingEnvironment.
	 */
	public LogEvent(LogRecord record, IuLoggingContext context, IuLoggingEnvironment environment) {
		this.instant = record.getInstant();
		this.level = record.getLevel();
		this.loggerName = record.getLoggerName();
		this.message = MESSAGE_FORMATTER.format(record);
		this.sourceClassName = record.getSourceClassName();
		this.sourceMethodName = record.getSourceMethodName();
		Throwable thrown = record.getThrown();
		if (thrown != null) {
			StringWriter w = new StringWriter();
			thrown.printStackTrace(new PrintWriter(w));
			this.thrown = w.toString();
		} else
			this.thrown = null;

		this.authenticatedPrincipal = context.getAuthenticatedPrincipal();
		this.calledUrl = context.getCalledUrl();
		this.remoteAddr = context.getRemoteAddr();
		this.reqNum = context.getReqNum();
		this.userPrincipal = context.getUserPrincipal();

		this.application = environment.getApplication();
		this.component = environment.getComponent();
		this.environment = environment.getEnvironment();
		this.hostname = environment.getHostname();
		this.mode = environment.getMode();
		this.module = environment.getModule();
		this.nodeId = environment.getNodeId();
		this.runtime = environment.getRuntime();
	}

	/**
	 * Return the Instant associated with this LogEvent.
	 */
	public Instant getInstant() {
		return instant;
	}

	/**
	 * Set the Instant associated with this LogEvent.
	 * 
	 * @param instant The Instant to associate with this LogEvent.
	 */
	public void setInstant(Instant instant) {
		this.instant = instant;
	}

	/**
	 * Return the log Level associated with this LogEvent.
	 */
	public Level getLevel() {
		return level;
	}

	/**
	 * Set the Level associated with this LogEvent.
	 * 
	 * @param level The Level to associate with this LogEvent.
	 */
	public void setLevel(Level level) {
		this.level = level;
	}

	/**
	 * Return the String representation of the Logger name associated with this
	 * LogEvent.
	 */
	public String getLoggerName() {
		return loggerName;
	}

	/**
	 * Set the Logger name associated with this LogEvent.
	 * 
	 * @param loggerName The Logger name to associate with this LogEvent.
	 */
	public void setLoggerName(String loggerName) {
		this.loggerName = loggerName;
	}

	/**
	 * Return the String representation of the message associated with this
	 * LogEvent.
	 */
	public String getMessage() {
		return message;
	}

	/**
	 * Set the message associated with this LogEvent.
	 * 
	 * @param message The String message to associate with this LogEvent.
	 */
	public void setMessage(String message) {
		this.message = message;
	}

	/**
	 * Return the String representation of the source class name associated with
	 * this LogEvent.
	 */
	public String getSourceClassName() {
		return sourceClassName;
	}

	/**
	 * Set the source class name associated with this LogEvent.
	 * 
	 * @param sourceClassName The String source class name to associate with this
	 *                        LogEvent.
	 */
	public void setSourceClassName(String sourceClassName) {
		this.sourceClassName = sourceClassName;
	}

	/**
	 * Return the String representation of the source method name associated with
	 * this LogEvent.
	 */
	public String getSourceMethodName() {
		return sourceMethodName;
	}

	/**
	 * Set the source method name associated with this LogEvent.
	 * 
	 * @param sourceMethodName The String source method name to associate with this
	 *                         LogEvent.
	 */
	public void setSourceMethodName(String sourceMethodName) {
		this.sourceMethodName = sourceMethodName;
	}

	/**
	 * Return the String representation of the thrown error associated with this
	 * LogEvent.
	 */
	public String getThrown() {
		return thrown;
	}

	/**
	 * Set the thrown value associated with this LogEvent. The thrown value will be
	 * the stack trace as a string.
	 * 
	 * @param thrown The String of the stack trace to associate with this LogEvent.
	 */
	public void setThrown(String thrown) {
		this.thrown = thrown;
	}

	/**
	 * Return the String representation of the authenticated principal associated
	 * with this LogEvent.
	 */
	public String getAuthenticatedPrincipal() {
		return authenticatedPrincipal;
	}

	/**
	 * Set the String representation of the authenticated principal associated with
	 * this LogEvent.
	 * 
	 * @param authenticatedPrincipal The String of the authenticated principal to
	 *                               associate with this LogEvent.
	 */
	public void setAuthenticatedPrincipal(String authenticatedPrincipal) {
		this.authenticatedPrincipal = authenticatedPrincipal;
	}

	/**
	 * Return the String representation of the called URL associated with this
	 * LogEvent.
	 */
	public String getCalledUrl() {
		return calledUrl;
	}

	/**
	 * Set the called URL associated with this LogEvent.
	 * 
	 * @param calledUrl The String of the called URL to associate with this
	 *                  LogEvent.
	 */
	public void setCalledUrl(String calledUrl) {
		this.calledUrl = calledUrl;
	}

	/**
	 * Return the String representation of the remote address associated with this
	 * LogEvent.
	 */
	public String getRemoteAddr() {
		return remoteAddr;
	}

	/**
	 * Set the remote address associated with this LogEvent.
	 * 
	 * @param remoteAddr The String of the remote address to associate with this
	 *                   LogEvent.
	 */
	public void setRemoteAddr(String remoteAddr) {
		this.remoteAddr = remoteAddr;
	}

	/**
	 * Return the String representation of the request number associated with this
	 * LogEvent.
	 */
	public String getReqNum() {
		return reqNum;
	}

	/**
	 * Set the request number associated with this LogEvent.
	 * 
	 * @param reqNum The String of the request number to associate with this
	 *               LogEvent.
	 */
	public void setReqNum(String reqNum) {
		this.reqNum = reqNum;
	}

	/**
	 * Return the String representation of the user principal associated with this
	 * LogEvent.
	 */
	public String getUserPrincipal() {
		return userPrincipal;
	}

	/**
	 * Set the user principal associated with this LogEvent.
	 * 
	 * @param userPrincipal The String of the user principal to associate with this
	 *                      LogEvent.
	 */
	public void setUserPrincipal(String userPrincipal) {
		this.userPrincipal = userPrincipal;
	}

	/**
	 * Return the String representation of the application associated with this
	 * LogEvent.
	 */
	public String getApplication() {
		return application;
	}

	/**
	 * Set the application associated with this LogEvent.
	 * 
	 * @param application The String of the application to associate with this
	 *                    LogEvent.
	 */
	public void setApplication(String application) {
		this.application = application;
	}

	/**
	 * Return the String representation of the component associated with this
	 * LogEvent.
	 */
	public String getComponent() {
		return component;
	}

	/**
	 * Set the component associated with this LogEvent.
	 * 
	 * @param component The String of the component to associate with this LogEvent.
	 */
	public void setComponent(String component) {
		this.component = component;
	}

	/**
	 * Return the String representation of the environment associated with this
	 * LogEvent.
	 */
	public String getEnvironment() {
		return environment;
	}

	/**
	 * Set the environment associated with this LogEvent.
	 * 
	 * @param environment The String of the environment to associate with this
	 *                    LogEvent.
	 */
	public void setEnvironment(String environment) {
		this.environment = environment;
	}

	/**
	 * Return the String representation of the host name associated with this
	 * LogEvent.
	 */
	public String getHostname() {
		return hostname;
	}

	/**
	 * Set the hostname associated with this LogEvent.
	 * 
	 * @param hostname The String of the hostname to associate with this LogEvent.
	 */
	public void setHostname(String hostname) {
		this.hostname = hostname;
	}

	/**
	 * Return the RuntimeMode enum value associated with this LogEvent.
	 */
	public RuntimeMode getMode() {
		return mode;
	}

	/**
	 * Set the RuntimeMode associated with this LogEvent.
	 * 
	 * @param mode The RuntimeMode to associate with this LogEvent.
	 */
	public void setMode(RuntimeMode mode) {
		this.mode = mode;
	}

	/**
	 * Return the String representation of the module associated with this LogEvent.
	 */
	public String getModule() {
		return module;
	}

	/**
	 * Set the module associated with this LogEvent.
	 * 
	 * @param module The String of the module to associate with this LogEvent.
	 */
	public void setModule(String module) {
		this.module = module;
	}

	/**
	 * Return the String representation of the node id associated with this
	 * LogEvent.
	 */
	public String getNodeId() {
		return nodeId;
	}

	/**
	 * Set the node Id associated with this LogEvent.
	 * 
	 * @param nodeId The String of the node Id to associate with this LogEvent.
	 */
	public void setNodeId(String nodeId) {
		this.nodeId = nodeId;
	}

	/**
	 * Return the String representation of the runtime associated with this
	 * LogEvent.
	 */
	public String getRuntime() {
		return runtime;
	}

	/**
	 * Set the runtime associated with this LogEvent.
	 * 
	 * @param runtime The String of the runtime to associate with this LogEvent.
	 */
	public void setRuntime(String runtime) {
		this.runtime = runtime;
	}

	/**
	 * Return name of the current thread
	 * 
	 * @return String representation of the current thread's name
	 */
	public String getThread() {
		return thread;
	}

	/**
	 * Set the thread associated with this LogEvent.
	 * 
	 * @param thread The String of the thread to associate with this LogEvent.
	 */
	public void setThread(String thread) {
		this.thread = thread;
	}
}
