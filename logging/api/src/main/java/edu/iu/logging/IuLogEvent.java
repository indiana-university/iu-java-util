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
package edu.iu.logging;

import java.net.URI;
import java.time.Instant;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.stream.Stream;

import iu.logging.boot.IuLoggingBootstrap;

/**
 * Encapsulates a fully serialized log event, processed from {@link LogRecord}
 * with {@link IuLogContext} attributes applied.
 */
public interface IuLogEvent {

	/**
	 * Subscribes to all log events at this node.
	 * 
	 * @return {@link Stream} of log events.
	 */
	static Stream<IuLogEvent> subscribe() {
		return IuLoggingBootstrap.subscribe();
	}

	/**
	 * Gets the log level
	 * 
	 * @return {@link Level}
	 */
	Level getLevel();

	/**
	 * Gets the logger name
	 * 
	 * @return logger name
	 */
	String getLoggerName();

	/**
	 * Gets the process identifier
	 * 
	 * @return process identifier
	 */
	String getProcessId();

	/**
	 * Gets the application code
	 * 
	 * @return application code
	 */
	String getApplication();

	/**
	 * Gets the environment code
	 * 
	 * @return environment code
	 */
	String getEnvironment();

	/**
	 * Gets the module code
	 * 
	 * @return module code
	 */
	String getModule();

	/**
	 * Gets the runtime configuration code
	 * 
	 * @return runtime configuration code
	 */
	String getRuntime();

	/**
	 * Gets the component code
	 * 
	 * @return component code
	 */
	String getComponent();

	/**
	 * Gets the node identifier
	 * 
	 * @return node identifier
	 */
	String getNodeId();

	/**
	 * Gets the thread name
	 * 
	 * @return thread name
	 */
	String getThread();

	/**
	 * Gets the caller IP address
	 * 
	 * @return caller IP address
	 */
	String getCallerIpAddress();

	/**
	 * Gets the called URL
	 * 
	 * @return called URL
	 */
	URI getCalledUrl();

	/**
	 * Gets the caller principal name
	 * 
	 * @return caller principal name
	 */
	String getCallerPrincipalName();

	/**
	 * Gets the impersonated principal name
	 * 
	 * @return impersonated principal name
	 */
	String getImpersonatedPrincipalName();

	/**
	 * Gets the timestamp
	 * 
	 * @return timestamp
	 */
	Instant getTimestamp();

	/**
	 * Gets the source class name
	 * 
	 * @return source class name
	 */
	String getSourceClassName();

	/**
	 * Gets the source method name
	 * 
	 * @return source method name
	 */
	String getSourceMethodName();

	/**
	 * Gets the log message
	 * 
	 * @return log message
	 */
	String getMessage();

	/**
	 * Gets the process log
	 * 
	 * @return process log
	 */
	String getProcessLog();

	/**
	 * Gets the error message
	 * 
	 * @return error message
	 */
	String getError();

}