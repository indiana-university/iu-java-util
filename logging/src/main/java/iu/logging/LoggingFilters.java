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

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Class containing logging filters.
 */
public class LoggingFilters {

	private static final Map<ClassLoader, Class<?>> LOGGABLE_HANDLERS = new WeakHashMap<>();

	private LoggingFilters() {}
	/**
	 * Determine whether a log message is local.
	 * 
	 * @return boolean Indicate whether a log message is local.
	 */
	public static boolean isLocal() {
		ClassLoader loader = Thread.currentThread().getContextClassLoader();
		if (loader != null) {
			Class<?> loggableHandler = LOGGABLE_HANDLERS.get(loader);
			if (loggableHandler == null)
				try {
					loggableHandler = loader.loadClass(LoggingFilters.class.getName());
					synchronized (LOGGABLE_HANDLERS) {
						LOGGABLE_HANDLERS.put(loader, loggableHandler);
					}
				} catch (ClassNotFoundException e) {
					loggableHandler = Object.class;
				}
			if (loggableHandler != LoggingFilters.class)
				return false;
		}
		return true;
	}

//	public static boolean isLoggable(String loggerName, Level logLevel) {
//		int levelValue = LoggingEnvironment.getLogLevel(loggerName).intValue();
//		return levelValue != Level.OFF.intValue() && logLevel.intValue() >= levelValue;
//	}

//	/**
//	 * Determine whether a given LogRecord is loggable for the given Handler.
//	 * 
//	 * @param record
//	 * @param handler
//	 * @return boolean Indicate whether the LogRecord is loggable for the given
//	 *         Handler.
//	 */
//	public static boolean isLoggable(LogRecord record, Handler handler) {
//		return handler.isLoggable(record);
//	}

//	public static boolean isSql(String loggerName, Level logLevel) {
//		return logLevel.intValue() >= Level.FINER.intValue() && loggerName != null
//				&& (loggerName.contains("iu.sql") || loggerName.contains("iu.jdbc"));
//	}

}