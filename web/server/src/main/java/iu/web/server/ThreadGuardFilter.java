package iu.web.server;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Map;
import java.util.Queue;
import java.util.Timer;
import java.util.TimerTask;
import java.util.WeakHashMap;
import java.util.logging.Logger;

import com.sun.net.httpserver.Filter;
import com.sun.net.httpserver.HttpExchange;

import edu.iu.IuOutOfServiceException;
import edu.iu.client.IuJson;
import jakarta.annotation.Priority;
import jakarta.annotation.Resource;
import jakarta.json.JsonObject;

/**
 * Provides fast-failure for requests exceeding a configured thread limit.
 */
@Resource
@Priority(-4000)
class ThreadGuardFilter extends Filter implements Runnable, AutoCloseable {

	private static final Logger LOG = Logger.getLogger(ThreadGuardFilter.class.getName());

	private static final Map<ClassLoader, ThreadGuard> COMP_GUARD = new WeakHashMap<>();

	@Resource
	private int threadLimit = 100;
	@Resource
	private int perComponentThreadLimit = 75;
	@Resource
	private Duration reportingInterval = Duration.ofMinutes(5L);

	private volatile Timer threadGuardReportingTimer;

	/**
	 * Observes thread state and generates a JSON object of captured metadata.
	 * 
	 * @param quiet true to retain state after capture; false to reset thread state
	 *              for tracking state between captures
	 * @return captured metadata
	 */
	JsonObject monitorThreads(boolean quiet) {
		final var b = IuJson.object();
		IuJson.add(b, "threadLimit", threadLimit);
		IuJson.add(b, "perComponentThreadLimit", perComponentThreadLimit);

		final var a = IuJson.array();
		a.add(ThreadGuard.GLOBAL_GUARD.toJson());
		if (!quiet)
			ThreadGuard.GLOBAL_GUARD.clearUnmonitored();
		synchronized (COMP_GUARD) {
			for (ThreadGuard comp : COMP_GUARD.values()) {
				a.add(comp.toJson());
				if (!quiet)
					comp.clearUnmonitored();
			}
		}

		return b.build();
	}

	@Override
	public String description() {
		return getClass().getName();
	}

	@Override
	public void run() {
		Timer timer = new Timer("thread-guard-monitor", true);
		timer.schedule(new TimerTask() {
			@Override
			public void run() {
				final var globalFailures = ThreadGuard.GLOBAL_GUARD.reportFailures();
				var totalFailures = globalFailures;

				class Failure {
					final String name;
					final int count;

					Failure(String name, int count) {
						this.name = name;
						this.count = count;
					}
				}
				final Queue<Failure> failures = new ArrayDeque<>();

				synchronized (COMP_GUARD) {
					for (ThreadGuard comp : COMP_GUARD.values()) {
						int compFailures = comp.reportFailures();
						totalFailures += compFailures;
						if (compFailures > 0)
							failures.offer(new Failure(comp.name(), compFailures));
					}
				}

				final var b = IuJson.object();
				b.add("monitor", monitorThreads(false));
				b.add("globalFailures", globalFailures);
				if (!failures.isEmpty()) {
					final var f = IuJson.object();
					failures.forEach(failure -> f.add(failure.name, failure.count));
				}

				final var msg = "Thread Guard Monitor " + b.build().toString();
				if (totalFailures > 0)
					LOG.warning(msg);
				else
					LOG.info(msg);
			}
		}, reportingInterval.toMillis(), reportingInterval.toMillis());
		LOG.config("Thread Guard Monitor initialized");
		threadGuardReportingTimer = timer;
	}

	@Override
	public void doFilter(HttpExchange exchange, Chain chain) throws IOException {
		final var webContext = ContextFilter.getActiveWebContext();

		final var active = ThreadGuard.GLOBAL_GUARD.active();
		int available = threadLimit - active;
		if (available <= 0) {
			ThreadGuard.GLOBAL_GUARD.fail();
			throw new IuOutOfServiceException("global thread exhaustion " + active + "/" + threadLimit);
		}

		ThreadGuard comp;
		if (webContext == null) {
			comp = ThreadGuard.GLOBAL_GUARD;
		} else
			synchronized (COMP_GUARD) {
				comp = COMP_GUARD.computeIfAbsent(webContext.getLoader(), a -> new ThreadGuard(webContext.getPath()));
			}

		final var compActive = comp.active();
		final var usedElsewhere = Integer.min(threadLimit, Integer.max(0, active - compActive));
		final var limit = (threadLimit - usedElsewhere) * perComponentThreadLimit;

		if (compActive >= limit) {
			comp.fail();
			throw new IuOutOfServiceException(
					"Component thread exhaustion " + comp.name() + " " + compActive + "/" + threadLimit);
		}

		comp.activate();
		final var start = Instant.now();
		try {
			chain.doFilter(exchange);
		} finally {
			comp.deactivate(Duration.between(start, Instant.now()));
		}
	}

	@Override
	public synchronized void close() {
		final var threadGuardReportingTimer = this.threadGuardReportingTimer;
		if (threadGuardReportingTimer != null) {
			this.threadGuardReportingTimer = null;
			threadGuardReportingTimer.cancel();
		}
	}

//	@Override
//	public void doFilter(ServletRequest req, ServletResponse resp, FilterChain fc)
//			throws IOException, ServletException {
//		HttpServletRequest hreq = (HttpServletRequest) req;
//		String originalUrl = hreq.getRequestURL().toString();
//		String forwardedHost = hreq.getHeader("x-forwarded-host");
//		String requestUrl;
//		if (forwardedHost != null) {
//			requestUrl = replaceHost(originalUrl, forwardedHost, -1);
//			LOG.info(
//					"Replaced host in " + originalUrl + " with value from x-forwarded-host header" + ": " + requestUrl);
//		} else
//			requestUrl = originalUrl;
//
//		boolean acceptUrl = false;
//		for (String u : acceptUrls)
//			if (requestUrl.startsWith(u)) {
//				LOG.fine("Accepting " + requestUrl + "; matched " + u);
//				acceptUrl = true;
//				break;
//			}
//
//		if (!acceptUrl) {
//			LOG.info("Rejecting " + requestUrl + ", not in acceptable URL list " + acceptUrls);
//			((HttpServletResponse) resp).sendError(HttpServletResponse.SC_NOT_FOUND, requestUrl.toString());
//			return;
//		}
//
//		// Note that preloading the request content stream will corrupt
//		// post params and multipart file uploads. This is only an issue
//		// for the iu-boot module since the preloaded stream is forwarded
//		// in turn to iu-web for normal application processing.
//		byte[] requestPayload = getBinaryContents(req.getInputStream());
//		req.setAttribute("iu.endpoint.request.payload", requestPayload);
//
//		// Parse request parameters for form post
//		final Map<String, String[]> params = new LinkedHashMap<>();
//		params.putAll(req.getParameterMap());
//		String contentType = req.getContentType();
//		if (contentType != null) {
//			int semi = contentType.indexOf(';');
//			if (semi != -1)
//				contentType = contentType.substring(0, semi);
//			if (contentType.equals("application/x-www-form-urlencoded")) {
//				String query = new String(requestPayload,
//						req.getCharacterEncoding() == null ? "UTF-8" : req.getCharacterEncoding());
//
//				int lastToken = 0;
//				int token = query.indexOf('=');
//				while (token >= 0) {
//					String name = query.substring(lastToken, token);
//					String[] values = params.get(name);
//					if (values == null)
//						values = new String[1];
//					else
//						values = Arrays.copyOf(values, values.length + 1);
//					params.put(name, values);
//
//					lastToken = token + 1;
//					token = query.indexOf('&', lastToken);
//					if (token >= 0) {
//						try {
//							values[values.length - 1] = URLDecoder.decode(query.substring(lastToken, token), "UTF-8");
//						} catch (UnsupportedEncodingException e) {
//							throw new IllegalStateException(e);
//						}
//						lastToken = token + 1;
//						token = query.indexOf('=', lastToken);
//					} else
//						try {
//							values[values.length - 1] = URLDecoder.decode(query.substring(lastToken), "UTF-8");
//						} catch (UnsupportedEncodingException e) {
//							throw new IllegalStateException(e);
//						}
//				}
//			}
//
//			// TODO: Parse multipart request if needed by boot module.
//		}
//
//		class Request extends HttpServletRequestWrapper {
//			ReadListener readListener;
//
//			Request() {
//				super(hreq);
//			}
//
//			private String getXClusterClientIp() {
//				return getHeader("X-Cluster-Client-Ip");
//			}
//
//			@Override
//			public StringBuffer getRequestURL() {
//				return new StringBuffer(requestUrl);
//			}
//
//			@Override
//			public String getHeader(String name) {
//				if ("host".equalsIgnoreCase(name) && forwardedHost != null)
//					return forwardedHost;
//				else
//					return super.getHeader(name);
//			}
//
//			@Override
//			public Enumeration<String> getHeaders(String name) {
//				if ("host".equalsIgnoreCase(name) && forwardedHost != null)
//					return new Enumeration<String>() {
//						boolean a = true;
//
//						@Override
//						public boolean hasMoreElements() {
//							return a;
//						}
//
//						@Override
//						public String nextElement() {
//							if (a) {
//								a = false;
//								return forwardedHost;
//							} else
//								throw new NoSuchElementException();
//						}
//					};
//				else
//					return super.getHeaders(name);
//			}
//
//			@Override
//			public String getRemoteAddr() {
//				String remoteAddr = getXClusterClientIp();
//				if (remoteAddr != null && !remoteAddr.equals(""))
//					return remoteAddr;
//				else
//					return super.getRemoteAddr();
//			}
//
//			@Override
//			public String getRemoteHost() {
//				String remoteHost = getXClusterClientIp();
//				if (remoteHost != null && !remoteHost.equals(""))
//					return remoteHost;
//				else
//					return super.getRemoteHost();
//			}
//
//			@Override
//			public String getParameter(String name) {
//				String[] parameterValues = params.get(name);
//				if (parameterValues == null || parameterValues.length == 0)
//					return null;
//				return parameterValues[0];
//			}
//
//			@Override
//			public Map<String, String[]> getParameterMap() {
//				return Collections.unmodifiableMap(params);
//			}
//
//			@Override
//			public Enumeration<String> getParameterNames() {
//				Iterator<String> parameterNames = params.keySet().iterator();
//				return new Enumeration<String>() {
//					@Override
//					public boolean hasMoreElements() {
//						return parameterNames.hasNext();
//					}
//
//					@Override
//					public String nextElement() {
//						return parameterNames.next();
//					}
//				};
//			}
//
//			@Override
//			public String[] getParameterValues(String name) {
//				return params.get(name);
//			}
//
//			@Override
//			public ServletInputStream getInputStream() throws IOException {
//				InputStream buffer = new ByteArrayInputStream(requestPayload);
//				return new ServletInputStream() {
//					@Override
//					public int read() throws IOException {
//						int b = buffer.read();
//						if (readListener != null && isFinished())
//							readListener.onAllDataRead();
//						return b;
//					}
//
//					@Override
//					public void setReadListener(ReadListener readListener) {
//						Request.this.readListener = readListener;
//						if (readListener != null)
//							try {
//								if (isReady())
//									readListener.onDataAvailable();
//								else
//									readListener.onAllDataRead();
//							} catch (IOException e) {
//								throw new IllegalStateException(e);
//							}
//					}
//
//					@Override
//					public int available() throws IOException {
//						return buffer.available();
//					}
//
//					@Override
//					public boolean isReady() {
//						try {
//							return buffer.available() > 0;
//						} catch (IOException e) {
//							throw new IllegalStateException(e);
//						}
//					}
//
//					@Override
//					public boolean isFinished() {
//						try {
//							return buffer.available() <= 0;
//						} catch (IOException e) {
//							throw new IllegalStateException(e);
//						}
//					}
//				};
//			}
//
//			@Override
//			public BufferedReader getReader() throws IOException {
//				return new BufferedReader(new InputStreamReader(new ByteArrayInputStream(requestPayload),
//						req.getCharacterEncoding() == null ? "UTF-8" : req.getCharacterEncoding()));
//			}
//
//		}
//		Request request = new Request();
//
//		/*
//		 * class Response extends HttpServletResponseWrapper { Response() {
//		 * super((HttpServletResponse) resp); }
//		 * 
//		 * @Override public void setStatus(int status) { super.setStatus(status); if
//		 * (status != 200) req.setAttribute("iu.endpoint.statusTrace", new
//		 * Throwable("HTTP " + EndpointUtil.describeStatus(status))); }
//		 * 
//		 * @Override public void sendError(int status) throws IOException {
//		 * super.sendError(status); req.setAttribute("iu.endpoint.statusTrace", new
//		 * Throwable("HTTP (error) " + EndpointUtil.describeStatus(status))); }
//		 * 
//		 * @Override public void sendError(int status, String message) throws
//		 * IOException { super.sendError(status, message);
//		 * req.setAttribute("iu.endpoint.statusTrace", new Throwable("HTTP (error) " +
//		 * EndpointUtil.describeStatus(status) + (message == null ? "" : " " +
//		 * message))); } }
//		 */
//
//		if (EndpointUtil.doStaticResource(true, request, (HttpServletResponse) resp))
//			return;
//
//		ClassLoader classLoader;
//		String servletPath = request.getServletPath();
//		if ("/soap".equals(servletPath) || "/wsdl".equals(servletPath))
//			classLoader = EndpointListener.getWebServiceInvoker().getClassLoader(request.getPathInfo());
//		else if ("/rest".equals(servletPath))
//			classLoader = EndpointListener.getRestWebServiceInvoker().getClassLoader(request.getPathInfo());
//		else if ("/web".equals(servletPath))
//			classLoader = EndpointListener.getWebRequestHandler().getClassLoader(request.getPathInfo());
//		else if (servletPath.startsWith("/websocket/"))
//			classLoader = EndpointListener.getWebSocketConnector().getClassLoader(servletPath);
//		else
//			classLoader = EndpointListener.getControl().getEndpointClassLoader();
//
//		ResponseWrapper response = new ResponseWrapper(request, (HttpServletResponse) resp);
//
//		try {
//
//			// Don't throttle endpoint control requests
//			ThreadGuard comp;
//			if (classLoader != EndpointListener.getControl().getEndpointClassLoader()) {
//				int globalLimit = EndpointListener.getControl().getThreadLimit();
//				int available = globalLimit - GLOBAL_GUARD.active;
//				if (available <= 0) {
//					// LOG: circuit breaker logic - global exhaustion
//					GLOBAL_GUARD.fail();
//					response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE,
//							"Global thread exhaustion " + GLOBAL_GUARD.active + "/" + globalLimit);
//					return;
//				}
//
//				synchronized (COMP_GUARD) {
//					comp = COMP_GUARD.get(classLoader);
//					if (comp == null) {
//						comp = new ThreadGuard(EndpointListener.getControl().getNodeName(classLoader));
//						COMP_GUARD.put(classLoader, comp);
//					}
//				}
//				assert comp != null;
//
//				int usedElsewhere = Integer.min(globalLimit, Integer.max(0, GLOBAL_GUARD.active - comp.active));
//				int limit = ((int) ((globalLimit - usedElsewhere)
//						* EndpointListener.getControl().getPerComponentThreadLimit()));
//
//				if (comp.active < limit)
//					comp.activate();
//				else {
//					// LOG: circuit breaker logic - component limit exhaustion
//					comp.fail();
//					response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE,
//							"Component thread exhaustion " + comp.descr + " " + comp.active + "/" + limit);
//					return;
//				}
//			} else
//				comp = null;
//
//			long now = System.currentTimeMillis();
//			try { // active thread counter activated
//
//				// Force encoding to UTF-8
//				request.setCharacterEncoding("UTF-8");
//				response.setCharacterEncoding("UTF-8");
//
//				request.setAttribute("requestPath", request.getRequestURI());
//
//				EndpointUtil.service(request, response, classLoader, () -> {
//					fc.doFilter(request, response);
//					return null;
//				});
//
//			} finally {
//				// deactivate active thread counter
//				if (comp != null)
//					comp.deactivate(System.currentTimeMillis() - now);
//			}
//
//		} finally {
//			if (response.getStatus() != HttpServletResponse.SC_SWITCHING_PROTOCOLS)
//				response.finish();
//		}
//	}

}
