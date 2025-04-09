package edu.iu.web.tomcat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.security.Principal;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Enumeration;
import java.util.Map;
import java.util.TimeZone;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.GZIPOutputStream;

import edu.iu.IuAuthorizationFailedException;
import edu.iu.IuBadRequestException;
import edu.iu.IuNotFoundException;
import edu.iu.IuOutOfServiceException;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpUpgradeHandler;

/**
 * A servlet filter that processes incoming HTTP requests.
 */
public class IuTomcatRequestFilter implements Filter {

	private static final Logger LOG = Logger.getLogger(IuTomcatRequestFilter.class.getName());

	// UTILS from 6.1 StringUtil
	/**
	 * The steps for printing sizes.
	 */
	private static final String[] SIZE_INTERVALS = new String[] { "k", "M", "G", "T", "E", };

	/**
	 * Print a human readable size.
	 * 
	 * @param bytes The number of bytes.
	 * @return A human readable representation of the size.
	 */
	public static String sizeToString(long bytes) {
		DecimalFormat df = new DecimalFormat("000");
		StringBuilder sb = new StringBuilder();
		int i = -1;
		int mod = 0;
		if (bytes < 0) {
			sb.append('-');
			bytes = Math.abs(bytes);
		}
		while (bytes / 1024 > 0 && i < SIZE_INTERVALS.length) {
			i++;
			mod = (int) (bytes % 1024);
			bytes /= 1024;
		}
		sb.append(bytes);
		if (mod > 0) {
			sb.append('.');
			sb.append(df.format(mod * 1000 / 1024));
		}
		if (i >= 0) {
			sb.append(SIZE_INTERVALS[i]);
		}
		return sb.toString();
	}

	/**
	 * Convert an object to a string, catching any exceptions that occur.
	 * 
	 * @param b The object to convert.
	 * @return The string representation of the object, or "ERROR" if an exception
	 *         occurs.
	 */
	public static String safeToString(Object b) {
		try {
			return b == null ? "" : b.toString();
		} catch (Throwable t) {
			return "ERROR";
		}
	}

	// End of UTILS from 6.1 StringUtil

	private static String describeRequest(HttpServletRequest hreq) {
		StringBuilder sb = new StringBuilder("HTTP request");
		sb.append("\n  Method: ");
		sb.append(hreq.getMethod());
		sb.append("\n  Auth Type: ");
		sb.append(hreq.getAuthType());
		sb.append("\n  Remote User: ");
		sb.append(hreq.getRemoteUser());
		sb.append("\n  Remote Host: ");
		sb.append(hreq.getRemoteHost());
		sb.append("\n  Remote Addr: ");
		sb.append(hreq.getRemoteAddr());
		sb.append("\n  Remote Port: ");
		sb.append(hreq.getRemotePort());
		sb.append("\n  Local Name: ");
		sb.append(hreq.getLocalName());
		sb.append("\n  Local Addr: ");
		sb.append(hreq.getLocalAddr());
		sb.append("\n  Local Port: ");
		sb.append(hreq.getLocalPort());
		sb.append("\n  Dispatcher Type: ");
		sb.append(hreq.getDispatcherType());
		sb.append("\n  Context Path: ");
		sb.append(hreq.getContextPath());
		sb.append("\n  Servlet Path: ");
		sb.append(hreq.getServletPath());
		sb.append("\n  Path Info: ");
		sb.append(hreq.getPathInfo());
		sb.append("\n  Path Translated: ");
		sb.append(hreq.getPathTranslated());
		sb.append("\n  Request URI: ");
		sb.append(hreq.getRequestURI());
		sb.append("\n  Request URL: ");
		sb.append(hreq.getRequestURL());
		sb.append("\n  Query String: ");
		sb.append(hreq.getQueryString());
		sb.append("\n  Cookies:");
		Cookie[] cookies = hreq.getCookies();
		if (cookies != null)
			for (Cookie cookie : cookies) {
				sb.append("\n  ").append(cookie.getName()).append(" = ").append(cookie.getValue());
				sb.append(" (").append(cookie.getSecure() ? "https" : "http");
				sb.append(" v").append(cookie.getVersion());
				sb.append(" max-age ").append(cookie.getMaxAge()).append(")");
				String domain = cookie.getDomain();
				if (domain != null)
					sb.append("\n    ").append("Domain: ").append(domain);
				String path = cookie.getPath();
				if (path != null)
					sb.append("\n    ").append("Path: ").append(path);
				String comment = cookie.getComment();
				if (comment != null)
					sb.append("\n    ").append("Comment: ").append(comment);
			}
		sb.append("\n  Headers:");
		Enumeration<?> rhe = hreq.getHeaderNames();
		while (rhe.hasMoreElements()) {
			String rh = (String) rhe.nextElement();
			sb.append("\n    ");
			sb.append(rh);
			sb.append(" = ");
			sb.append(hreq.getHeader(rh));
		}
		sb.append("\n  Parameters:");
		for (Object ope : hreq.getParameterMap().entrySet()) {
			@SuppressWarnings("unchecked")
			Map.Entry<String, String[]> pe = (Map.Entry<String, String[]>) ope;
			if (pe.getValue() == null || pe.getValue().length == 0) {
				sb.append("\n    ");
				sb.append(pe.getKey());
				sb.append(" (empty)");
			} else {
				for (String pv : pe.getValue()) {
					sb.append("\n    ");
					sb.append(pe.getKey());
					sb.append(" = ");
					sb.append(pv);
				}
			}
		}
		sb.append("\n  Encoding: ").append(hreq.getCharacterEncoding());
		sb.append("\n  Content: ").append(sizeToString(hreq.getContentLength())).append("B ")
				.append(hreq.getContentType());
		sb.append("\n  Attributes:");
		Enumeration<String> ane = hreq.getAttributeNames();
		while (ane.hasMoreElements()) {
			String k = ane.nextElement();
			sb.append("\n    ");
			sb.append(k);
			sb.append(" = ");
			sb.append(safeToString(hreq.getAttribute(k)));
		}
		return sb.toString();
	}

	/**
	 * Default constructor.
	 */
	public IuTomcatRequestFilter() {
		// Default constructor
	}

	@Override
	public void init(FilterConfig fc) throws ServletException {
	}

	@Override
	public void destroy() {
	}

	/**
	 * Handle an error that occurred during request processing.
	 * 
	 * @param hreq  The servlet request.
	 * @param hresp The servlet response.
	 * @param e     The exception that occurred.
	 */
	static void handleError(HttpServletRequest hreq, HttpServletResponse hresp, Throwable e) {
		handleError(hreq, hresp, 0, null, e);
	}

	/**
	 * Handle an error that occurred during request processing.
	 * 
	 * @param hreq    The servlet request.
	 * @param hresp   The servlet response.
	 * @param status  The HTTP status code to send.
	 * @param message The error message to send.
	 * @param e       The exception that occurred.
	 */
	static void handleError(HttpServletRequest hreq, HttpServletResponse hresp, int status, String message,
			Throwable e) {
		if (status == 0)
			if (e instanceof IuNotFoundException)
				status = HttpServletResponse.SC_NOT_FOUND;
			else if (e instanceof IuOutOfServiceException)
				status = HttpServletResponse.SC_SERVICE_UNAVAILABLE;
			else if (e instanceof IuAuthorizationFailedException)
				status = HttpServletResponse.SC_FORBIDDEN;
			else if (e instanceof IuBadRequestException)
				status = HttpServletResponse.SC_BAD_REQUEST;
			else if (e instanceof SecurityException)
				status = HttpServletResponse.SC_UNAUTHORIZED;
			else if (e != null)
				status = HttpServletResponse.SC_INTERNAL_SERVER_ERROR;

		if (message == null && e != null)
			message = e.getMessage();

		try {
			if ((e instanceof ServletException) && (e.getCause() instanceof Error))
				e = e.getCause();
			hreq.setAttribute("iuRequestException", e);
			hresp.sendError(status, message);
		} catch (Throwable clientAbort) {
			LOG.log(Level.FINE, "Client abort detected", clientAbort);
			if (e != null)
				e.addSuppressed(clientAbort);
			hreq.setAttribute("iuClientAbort", true);
		}
	}

	/**
	 * Determine the requested content encoding based on the Accept-encoding header.
	 * 
	 * <p>
	 * See RFC2616, sections 3.5 and 14.3:
	 * <ul>
	 * <li>http://www.w3.org/Protocols/rfc2616/rfc2616-sec3.html</li>
	 * <li>http://www.w3.org/Protocols/rfc2616/rfc2616-sec14.html</li>
	 * </ul>
	 * </p>
	 * 
	 * @return The content encoding listed in the Accept-encoding header, with the
	 *         highest qvalue:
	 *         <ul>
	 *         <li><em>gzip</em> is preferred and will be used when '*' is
	 *         found.</li>
	 *         <li><em>deflate</em> is also supported and will be used when gzip is
	 *         not accepted, when gzip has a lower qvalue than deflate, or when
	 *         deflate is listed first..</li>
	 *         <li><em>identity</em> will not be returned, and will not be preferred
	 *         even if the qvalue is higher than another encoding.</li>
	 *         <li>A return value of null means that content should not be
	 *         compressed. A return value other than <em>gzip</em> or
	 *         <em>deflate</em> will also be ignored in the current version.</li>
	 *         </ul>
	 */
	private static String getRequestedContentEncoding(HttpServletRequest hreq) {
		String aeh = hreq.getHeader("Accept-encoding");
		String contentEncoding = null;
		float q = 0.0f;
		if (aeh != null) {
			LOG.finer(() -> "accept:" + aeh);
			for (String hv : aeh.split(",")) {
				hv = hv.trim();
				if ("*".equals(hv)) {
					contentEncoding = "gzip";
					continue;
				}
				float tq = 1.0f;
				int iosc = hv.indexOf(';');
				if (iosc != -1) {
					String qv = hv.substring(iosc + 1).trim();
					if (qv.startsWith("q=")) {
						try {
							tq = Float.parseFloat(qv.substring(2));
						} catch (NumberFormatException e) {
							LOG.log(Level.INFO, e, () -> "Invalid q value " + qv + " in Accept-encoding header " + aeh);
						}
					} else
						LOG.info(() -> "Invalid q value " + qv + " in Accept-encoding header " + aeh);
					if (tq == 0.0f)
						continue;
					hv = hv.substring(iosc).trim();
				}
				// compress is not supported
				if ("compress".equals(hv))
					continue;
				if (tq > q) {
					contentEncoding = hv;
					q = tq;
				}
			}
		}
		return contentEncoding;
	}

	private static Date expireDate;
	private static String expires;

	private static Date getExpireDate() {
		if (expireDate == null || expireDate.getTime() < System.currentTimeMillis()) {
			Calendar c = Calendar.getInstance();
			c.add(Calendar.HOUR_OF_DAY, -5);
			c.add(Calendar.DATE, 1);
			c.set(Calendar.HOUR_OF_DAY, 5);
			c.set(Calendar.MINUTE, 0);
			c.set(Calendar.SECOND, 0);
			c.set(Calendar.MILLISECOND, 0);
			expireDate = c.getTime();

			SimpleDateFormat df = new SimpleDateFormat("E, dd MMM yyyy HH:mm:ss z");
			df.setTimeZone(TimeZone.getTimeZone("GMT"));
			expires = df.format(c.getTime());
		}
		return expireDate;
	}

	private static String getExpires() {
		getExpireDate(); // generates expires as a side-effect
		return expires;
	}

	private static void getStaticResource(URL resourceUrl, String requestPath, HttpServletRequest hreq,
			HttpServletResponse hresp) throws ServletException, IOException {

		hresp.setContentType(hreq.getServletContext().getMimeType(requestPath));

		hresp.setHeader("Cache-Control", "public");
		hresp.setHeader("Expires", expires);
		hresp.setHeader("Pragma", "public");

		Logger requestLogger = Logger.getLogger(requestPath);
		String contentEncoding = getRequestedContentEncoding(hreq);
		int rawLength = 0;
		byte[] data;
		try (InputStream res = resourceUrl.openStream()) {
			if ("gzip".equals(contentEncoding) || "deflate".equals(contentEncoding)) {
				hresp.setHeader("Content-encoding", contentEncoding);
				try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
						DeflaterOutputStream zos = "gzip".equals(contentEncoding) ? new GZIPOutputStream(baos)
								: new DeflaterOutputStream(baos)) {
					byte[] buf = new byte[16384];
					int r;
					while ((r = res.read(buf)) > 0) {
						rawLength += r;
						zos.write(buf, 0, r);
					}
					zos.finish();
					baos.flush();
					data = baos.toByteArray();
				}
			} else
				try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
					byte[] buf = new byte[16384];
					int r;
					while ((r = res.read(buf)) > 0) {
						rawLength += r;
						baos.write(buf, 0, r);
					}
					baos.flush();
					data = baos.toByteArray();
				}
		}

		hresp.setStatus(HttpServletResponse.SC_OK);
		hresp.setContentLength(data.length);
		OutputStream out = hresp.getOutputStream();
		out.write(data);
		out.flush();
		requestLogger.info("200 OK " + sizeToString(rawLength) + "B"
				+ (rawLength != data.length ? " (" + sizeToString(data.length) + "B " + contentEncoding + ")" : "")
				+ ", cache until " + getExpires() + " " + ", from " + resourceUrl);
	}

	@Override
	public void doFilter(ServletRequest req, ServletResponse resp, FilterChain fc)
			throws IOException, ServletException {
		HttpServletRequest hreq = (HttpServletRequest) req;
		HttpServletResponse hresp = (HttpServletResponse) resp;

		String requestPath = hreq.getServletPath();
		String pathInfo = hreq.getPathInfo();
		if (pathInfo != null)
			requestPath += pathInfo;

		if (DispatcherType.REQUEST.equals(hreq.getDispatcherType())) {
			// Restrict access to protected resources and dynamic content
			if (requestPath.contains("/WEB-INF/") || requestPath.startsWith("/META-INF/")
					|| requestPath.startsWith("/_jsp/") || requestPath.contains("/../"))
				hresp.sendError(HttpServletResponse.SC_NOT_FOUND);
		}

		if (!requestPath.endsWith("/")) {
			URL resourceUrl = hreq.getServletContext().getResource(requestPath);
			if (resourceUrl != null)
				getStaticResource(resourceUrl, requestPath, hreq, hresp);
		}

		// TODO: Implement without IuIdentityManager and AuthenticatedPrincipal
//		IuIdentityManager iuIdentityManager;
//		{
//			IuIdentityManager a = null;
//			try {
//				a = IU.getResource(IuIdentityManager.class);
//			} catch (IuIncompleteBindingException e) {
//				LOG.log(Level.CONFIG, e, () -> "Missing IuIdentityManager resource");
//			}
//			iuIdentityManager = a;
//		}
//
//		AuthenticatedPrincipal authPrincipal;
//		{
//			AuthenticatedPrincipal a = null;
//			if (iuIdentityManager != null)
//				try {
//					a = iuIdentityManager.getAuthenticatedPrincipal();
//				} catch (IllegalStateException e) {
//					LOG.log(Level.FINER, e, () -> "Not authenticated");
//				}
//			authPrincipal = a;
//		}

		HttpServletRequest wreq = new HttpServletRequestWrapper(hreq) {
			@Override
			public String getAuthType() {
				return null;
//				if (authPrincipal == null)
//					return null;
//				return authPrincipal.getAuthType();
			}

			@Override
			public String getRemoteUser() {
				return null;
//				Principal userPrincipal = getUserPrincipal();
//				if (userPrincipal == null)
//					return null;
//				return userPrincipal.getName();
			}

			@Override
			public Principal getUserPrincipal() {
				return null;
//				if (authPrincipal == null)
//					return null;
//				IuPrincipal userPrincipal = authPrincipal.getImpersonatedPrincipal();
//				if (userPrincipal == null)
//					userPrincipal = authPrincipal.getAuthPrincipal();
//				return userPrincipal;
			}

			@Override
			public boolean isUserInRole(String role) {
				return false;
//				if (iuIdentityManager == null)
//					return false;
//				return iuIdentityManager.isInRole(role);
			}

			@Override
			public <T extends HttpUpgradeHandler> T upgrade(Class<T> httpUpgradeHandlerClass)
					throws IOException, ServletException {
				// TODO: implement upgrade capability.
//				T handler;
//				try {
//					try {
//						handler = httpUpgradeHandlerClass.getConstructor().newInstance();
//					} catch (InvocationTargetException e) {
//						throw e.getCause();
//					}
//				} catch (IOException | ServletException | RuntimeException | Error e) {
//					throw e;
//				} catch (Throwable e) {
//					throw new IllegalStateException(e);
//				}

				String requestPath = getServletPath();
				if (getPathInfo() != null)
					requestPath += getPathInfo();

//				IuTomcatInvoker.reportUpgrade(new IuTomcatUpgradeHandler(authPrincipal, requestPath, handler));
//				hresp.setStatus(HttpServletResponse.SC_SWITCHING_PROTOCOLS);
//				return handler;
				IuTomcatInvoker.reportUpgrade(null);
				return null;
			}
		};

		LOG.fine(() -> "Incoming request " + describeRequest(wreq));

		// TODO: Is overriding current date for test environments going to be part of
		// 7.0?
//		if (!IU.SPI.isProduction()) {
//			String currentDateTime = wreq.getParameter(CurrentDateTimeUtil.CURRENT_DATE_TIME_URL_PARAM);
//			if (StringUtil.hasValue(currentDateTime))
//				CurrentDateTimeUtil.setCurrentDateString(currentDateTime);
//			LOG.fine(() -> "Overriding current date and time with " + currentDateTime + " "
//					+ CurrentDateTimeUtil.getCurrentDate());
//		}
		try {
			IuTomcatResponseWrapper wresp = new IuTomcatResponseWrapper(wreq, hresp);

			fc.doFilter(wreq, wresp);

			if (hresp.getStatus() != HttpServletResponse.SC_SWITCHING_PROTOCOLS)
				wresp.finish();

		} catch (Throwable e) {
			handleError(wreq, hresp, e);
		}
	}

}
