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
package edu.iu.logging;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.Queue;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;

import edu.iu.logging.IuLoggingEnvironment.RuntimeMode;
import iu.logging.IuLogHandler;

/**
 * Bootstrap logging environment
 */
public final class LoggingEnvironment {

	private static final Map<String, String> PROPERTIES;

	static {
		StringBuilder sb = new StringBuilder("Logging properties");
		try {
			Enumeration<URL> iuLogProps = LoggingEnvironment.class.getClassLoader()
					.getResources("META-INF/iu-logging.properties");
			Map<String, String> properties = new LinkedHashMap<>();
			while (iuLogProps.hasMoreElements()) {
				URL u = iuLogProps.nextElement();
				sb.append("\nRead ").append(u);
				try (InputStream in = u.openStream()) {
					Properties p = new Properties();
					try {
						p.load(in);
					} catch (IOException e) {
						throw new IllegalStateException(e);
					}
					for (String k : p.stringPropertyNames()) {
						String o = properties.get(k);
						String n = p.getProperty(k);
						sb.append("\n  ").append(k).append(':');
						if (o == null) {
							sb.append(" set to ").append(n);
							properties.put(k, n);
						} else {
							sb.append(" kept ").append(o);
							if (n != null)
								sb.append(", discarded ").append(n);
						}
					}
				}
			}

			sb.append("\nFinal:");
			properties.forEach((n, v) -> sb.append("\n  ").append(n).append(": ").append(v));

			if (System.getProperty("iu.debug") != null)
				System.err.println(sb);

			PROPERTIES = properties;

		} catch (IOException e) {
			System.err.println(sb);
			e.printStackTrace();
			throw new ExceptionInInitializerError(e);
		}
	}

	private static IuLoggingEnvironment TODO_ENV_PROPS = new IuLoggingEnvironment() {
	};

	private static IuLoggingContext TODO_CALL_CONTEXT_PROPS = new IuLoggingContext() {

//		@Override
//		public String getRequestNumber() {
//			return null;
//		}
//
//		@Override
//		public String getImpersonatedPrincipalName() {
//			return null;
//		}
//
//		@Override
//		public String getCallerPrincipalName() {
//			return null;
//		}
//
//		@Override
//		public String getCallerIpAddress() {
//			return null;
//		}
//
//		@Override
//		public String getCalledUrl() {
//			return null;
//		}
	};

	private static IuLoggingEnvironment envProps;
	private static IuLoggingContext callProps;

	private static <T> T loadService(Class<T> serviceInterface) {
		System.err.println("LoggingEnvironment.loadService. serviceInterface. package: "
				+ serviceInterface.getPackageName() + " simpleName: " + serviceInterface.getSimpleName());
		URL implResource = serviceInterface.getClassLoader()
				.getResource("META-INF/services/" + serviceInterface.getName());
		if (implResource != null)
			try (InputStream a = implResource.openStream();
					InputStreamReader b = new InputStreamReader(a);
					BufferedReader c = new BufferedReader(b)) {
				return serviceInterface.cast(Class.forName(c.readLine().trim()).newInstance());
			} catch (Throwable e) {
				IuLogHandler.handleFileWriteError("Environment service failure for " + serviceInterface, e);
			}
		return null;
	}

	private static IuLoggingEnvironment getEnvironmentProperties() {
		if (envProps != null)
			return envProps;
		envProps = loadService(IuLoggingEnvironment.class);
		if (envProps == null)
			return TODO_ENV_PROPS;
		else
			return envProps;
	}

	private static IuLoggingContext getCallProperties() {
		if (callProps != null)
			return callProps;
		callProps = loadService(IuLoggingContext.class);
		if (callProps == null)
			return TODO_CALL_CONTEXT_PROPS;
		else
			return callProps;
	}

	private static Iterable<String> split(String value) {
		if (value == null)
			return Collections.emptySet();

		int c = 0;
		Queue<String> rv = new ArrayDeque<>();
		while (c < value.length()) {
			int pos = c;
			while (pos < value.length()) {
				if (Character.isWhitespace(value.charAt(pos)) || value.charAt(pos) == ',') {
					if (pos == c)
						c++;
					else
						break;
				}
				pos++;
			}
			if (c != pos) {
				String e = value.substring(c, pos);
				assert !e.isEmpty() && e.equals(e.trim()) : '\'' + e + "' ... '" + value + '\'';
				rv.add(e);
				c = pos + 1;
			}
		}
		return rv;
	}

	/**
	 * Get current loggers for a given ClassLoader.
	 * 
	 * @param loader ClassLoader for which to retrieve Loggers.
	 * @return Queue&lt;Logger&gt; for the given ClassLoader.
	 */
	public static Queue<Logger> getCurrentLoggers(ClassLoader loader) {
		System.err.println("LoggingEnvironment.getCurrentLoggers(loader)");
		Thread current = Thread.currentThread();
		ClassLoader currentLoader = current.getContextClassLoader();
		try {
			current.setContextClassLoader(loader);
			Enumeration<String> loggerNames = LogManager.getLogManager().getLoggerNames();
			Set<String> sortedNames = new TreeSet<>();
			while (loggerNames.hasMoreElements()) {
				String loggerName = loggerNames.nextElement();
				sortedNames.add(loggerName);
			}
			Queue<Logger> rv = new ArrayDeque<>();
			for (String loggerName : sortedNames) {
				Logger logger = Logger.getLogger(loggerName);
				rv.offer(logger);
			}

			return rv;
		} finally {
			current.setContextClassLoader(currentLoader);
		}
	}

	/**
	 * Register IuLogHandler as root logger. Pre-initialize loggers to fine-tune
	 * levels, once per class loader.
	 * 
	 * @param loader. The ClassLoader for which we want to bootstrap logging.
	 */
	public static void bootstrap(ClassLoader loader) {
		System.err.println("LoggingEnvironment.bootstrap(loader)");
		Thread current = Thread.currentThread();
		ClassLoader currentLoader = current.getContextClassLoader();
		try {
			current.setContextClassLoader(loader);
			StringBuilder sb = new StringBuilder("Logging bootstrap ").append(LogManager.getLogManager());
			boolean oneChange = false;

			Map<String, Handler> handlers = new HashMap<>();
			Function<String, Handler> getHandler = hc -> {
				Handler h = handlers.get(hc);
				if (h == null)
					try {
						handlers.put(hc, h = (Handler) Class.forName(hc).getConstructor().newInstance());
						sb.append("\nRegister handler ").append(hc);
					} catch (InstantiationException | IllegalAccessException | InvocationTargetException
							| NoSuchMethodException | ClassNotFoundException ex) {
						throw new ExceptionInInitializerError(ex);
					}
				return h;
			};

			for (Entry<String, String> e : PROPERTIES.entrySet()) {
				String k = e.getKey().trim();
				if (k.equals("handlers") || k.endsWith(".handlers")) {
					String name = k.equals("handlers") ? "" : k.substring(0, k.length() - 9);
					Logger l = Logger.getLogger(name);
					for (String hc : split(e.getValue())) {
						Handler h = getHandler.apply(hc);
						boolean hasHandler = false;
						for (Handler lh : l.getHandlers())
							if (lh.getClass() == h.getClass()) {
								hasHandler = true;
								sb.append("\nKept handler ").append(name.equals("") ? "(root)" : name).append(" = ")
										.append(hc);
								break;
							}
						if (!hasHandler) {
							l.addHandler(h);
							sb.append("\nAdded handler ").append(name.equals("") ? "(root)" : name).append(" <- ")
									.append(hc);
						}
					}
				}
			}

			Set<String> configuredNames = new HashSet<>();
			for (Entry<String, String> e : PROPERTIES.entrySet()) {
				String k = e.getKey().trim();
				if (k.endsWith(".level")
						|| (Float.parseFloat(System.getProperty("java.specification.version")) < 9.0f
								&& k.endsWith(".level-jdk8"))
						|| (Float.parseFloat(System.getProperty("java.specification.version")) >= 9.0f
								&& k.endsWith(".level-jdk9"))) {
					Level level = Level.parse(e.getValue().trim());
					String name = k.substring(0, k.lastIndexOf('.'));
					configuredNames.add(name);

					Handler h = handlers.get(name);
					if (h != null) {
						if (!level.equals(h.getLevel())) {
							h.setLevel(level);
							sb.append("\nSet handler ").append(name.equals("") ? "(root)" : name).append(" level to ")
									.append(level);
							oneChange = true;
						}
					} else {
						Logger l = Logger.getLogger(name);
						Level oldLevel = l.getLevel();
						if (!level.equals(oldLevel)) {
							sb.append("\nSet logger ").append(name.equals("") ? "(root)" : name).append(" level to ")
									.append(level);
							if (oldLevel != null)
								sb.append(", was ").append(oldLevel);
							else {
								oldLevel = Level.INFO;
								sb.append(", was default (INFO)");
							}
							for (Handler lh : l.getHandlers())
								if (!handlers.containsValue(lh) && !lh.getClass().getName().startsWith("iu.")) {
									l.removeHandler(lh);
									sb.append("\nRemoved non-local handler ").append(lh.getClass().getName());
								}
							l.setLevel(level);
							oneChange = true;
						}
					}
				} else if (k.endsWith(".useParentHandlers")) {
					String name = k.substring(0, k.length() - 18);
					configuredNames.add(name);

					boolean value = Boolean.valueOf(e.getValue());
					Logger l = Logger.getLogger(name);
					if (value != l.getUseParentHandlers()) {
						l.setUseParentHandlers(value);
						sb.append("\nSet logger ").append(name).append(" to ").append(value ? "use" : "skip")
								.append(" parent handlers");
						oneChange = true;
					}
				} else if (k.endsWith(".formatter")) {
					Handler handler = getHandler.apply(k.substring(0, k.length() - 10));
					String formatterName = e.getValue().trim();
					try {
						handler.setFormatter((Formatter) Class.forName(formatterName).getConstructor().newInstance());
						sb.append("\nSet handler ").append(handler.getClass()).append(" formatter to ")
								.append(formatterName);
					} catch (InstantiationException | IllegalAccessException | InvocationTargetException
							| NoSuchMethodException | ClassNotFoundException ex) {
						throw new ExceptionInInitializerError(ex);
					}
					oneChange = true;
				}
			}

			Enumeration<String> loggerNames = LogManager.getLogManager().getLoggerNames();
			Set<String> sortedNames = new TreeSet<>();
			while (loggerNames.hasMoreElements()) {
				String loggerName = loggerNames.nextElement();
				sortedNames.add(loggerName);
				if (configuredNames.contains(loggerName))
					continue;

				Logger logger = Logger.getLogger(loggerName);
				Level level = logger.getLevel();
				if (logger.getLevel() != null) {
					logger.setLevel(null);
					sb.append("\nCleared logger ").append(loggerName).append(" level from ").append(level);
					oneChange = true;
				}

				if (logger.getFilter() != null) {
					logger.setFilter(null);
					sb.append("\nCleared logger ").append(loggerName).append(" filter from ")
							.append(logger.getFilter().getClass().getName());
					oneChange = true;
				}

				if (!logger.getUseParentHandlers()) {
					logger.setUseParentHandlers(true);
					sb.append("\nRestored use parent handlers for logger ").append(loggerName);
					oneChange = true;
				}

				Handler[] handlersToRemove = logger.getHandlers();
				if (handlersToRemove != null)
					for (Handler handler : handlersToRemove)
						if (handler != null) {
							logger.removeHandler(handler);
							sb.append("\nRemoved handler " + handler.getClass().getName() + " from logger ")
									.append(loggerName);
							oneChange = true;
						}
			}

			Logger logger = Logger.getGlobal();
			Level level = logger.getLevel();
			sb.append("\nGlobal Logger ").append(logger.getName()).append(" ").append(level).append(" ")
					.append(logger.getUseParentHandlers());
			Handler[] l = logger.getHandlers();
			if (l != null)
				for (Handler h : l)
					sb.append("\n  ").append(h.getClass().getName());

			if (oneChange)
				Logger.getLogger(LoggingEnvironment.class.getName()).info(sb::toString);

		} finally {
			current.setContextClassLoader(currentLoader);
		}
	}

//	public static String getLogPath() {
//		return getEnvironmentProperties().getLogPath();
//	}

	/**
	 * Get the application.
	 * 
	 * @return String representing the application.
	 */
	public static String getApplication() {
		return getEnvironmentProperties().getApplication();
	}

	/**
	 * Get the component.
	 * 
	 * @return String representing the component.
	 */
	public static String getComponent() {
		return getEnvironmentProperties().getComponent();
	}

	/**
	 * Get the endpoint.
	 * 
	 * @return String representing the endpoint.
	 */
	public static String getEndpoint() {
		return getEnvironmentProperties().getEndpoint();
	}

	/**
	 * Get the environment.
	 * 
	 * @return String representing the environment.
	 */
	public static String getEnvironment() {
		return getEnvironmentProperties().getEnvironment();
	}

	/**
	 * Get the module.
	 * 
	 * @return String representing the module.
	 */
	public static String getModule() {
		return getEnvironmentProperties().getModule();
	}

//	public static String getVersion() {
//		return getEnvironmentProperties().getVersion();
//	}

	/**
	 * Get the node id.
	 * 
	 * @return String representing the node id.
	 */
	public static String getNodeId() {
		return getEnvironmentProperties().getNodeId();
	}

	/**
	 * Convenience method to determine if this is a development environment.
	 * 
	 * @return boolean representing whether this is a development environment.
	 */
	public static boolean isDevelopment() {
		return RuntimeMode.DEVELOPMENT == getEnvironmentProperties().getMode();
//		return getEnvironmentProperties().isDevelopment();
	}

//	public static Level getLogLevel() {
//		return getEnvironmentProperties().getLogLevel();
//	}
//
//	public static Level getConsoleLogLevel() {
//		return getEnvironmentProperties().getConsoleLogLevel();
//	}
//
//	public static long getSevereInterval() {
//		return getEnvironmentProperties().getSevereInterval();
//	}
//
//	public static long getWarningInterval() {
//		return getEnvironmentProperties().getWarningInterval();
//	}
//
//	public static long getInfoInterval() {
//		return getEnvironmentProperties().getInfoInterval();
//	}
//
//	public static String getRequestNumber() {
//		return getCallProperties().getRequestNumber();
//	}
//
//	public static String getCallerIpAddress() {
//		return getCallProperties().getCallerIpAddress();
//	}
//
//	public static String getCalledUrl() {
//		return getCallProperties().getCalledUrl();
//	}
//
//	public static String getCallerPrincipalName() {
//		return getCallProperties().getCallerPrincipalName();
//	}
//
//	public static String getImpersonatedPrincipalName() {
//		return getCallProperties().getImpersonatedPrincipalName();
//	}

//	public static Level getLogLevel(String loggerName) {
//		return getEnvironmentProperties().getLogLevel(loggerName);
//	}
//
//	public static int getLogEventBufferSize() {
//		return getEnvironmentProperties().getLogEventBufferSize();
//	}

	private LoggingEnvironment() {
	}

}
