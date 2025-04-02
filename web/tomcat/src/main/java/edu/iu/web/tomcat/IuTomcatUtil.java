package edu.iu.web.tomcat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLConnection;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.TimeZone;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.GZIPOutputStream;

import edu.iu.IuStream;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

class IuTomcatUtil {

	private static final Logger LOG = Logger.getLogger(IuTomcatUtil.class.getName());

	@FunctionalInterface
	static interface IOSupplier<T> {
		T get() throws IOException;
	}

	/**
	 * Determine the requested content encoding based on the Accept-encoding header.
	 * 
	 * <p>
	 * See RFC2616, sections 3.5 and 14.3:
	 * </p>
	 * <ul>
	 * <li>http://www.w3.org/Protocols/rfc2616/rfc2616-sec3.html</li>
	 * <li>http://www.w3.org/Protocols/rfc2616/rfc2616-sec14.html</li>
	 * </ul>
	 * 
	 * @param hreq servlet request
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
	public static String getRequestedContentEncoding(HttpServletRequest hreq) {
		String aeh = hreq.getHeader("Accept-Encoding");
		String contentEncoding = null;
		float q = 0.0f;
		if (aeh != null) {
			LOG.fine(() -> "Accept-Encoding:" + aeh);
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
							LOG.log(Level.WARNING, e,
									() -> "Invalid q value " + qv + " in Accept-encoding header " + aeh);
						}
					} else
						LOG.warning(() -> "Invalid q value " + qv + " in Accept-encoding header " + aeh);
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

	static boolean doStaticResource(boolean cache, HttpServletRequest request, HttpServletResponse response)
			throws ServletException, IOException {

		String pathInfo = request.getPathInfo();
		String requestPath;
		if (pathInfo == null)
			requestPath = request.getServletPath();
		else
			requestPath = request.getServletPath() + pathInfo;

		// SCT-5299 restrict access to protected resources
		if (requestPath.contains("/WEB-INF/"))
			return false;
		if (requestPath.startsWith("/META-INF/"))
			return false;
		if (requestPath.startsWith("/_jsp/"))
			return false;
		if (requestPath.endsWith("/"))
			return false;
		if (requestPath.contains("/../"))
			return false;
		// End SCT-5299 restrictions

		ServletContext servletContext = request.getServletContext();
		URL resourceUrl = servletContext.getResource(requestPath);
		if (resourceUrl == null)
			return false;

		String contentType = servletContext.getMimeType(requestPath);
		if (contentType == null)
			return false;

		URLConnection resourceConnection = resourceUrl.openConnection();

		if (!cache) {
			response.setHeader("Cache-Control", "must-revalidate, post-check=0, pre-check=0");
			response.setHeader("Pragma", "public");
		} else if (!"no-cache".equals(request.getHeader("Cache-Control"))
				&& !"no-cache".equals(request.getHeader("Pragma"))) {
			long lastModified = resourceConnection.getLastModified();
			String etag = "W/\"" + resourceConnection.getContentLengthLong() + '-' + lastModified + '\"';
			response.setHeader("ETag", etag);

			String noneMatch = request.getHeader("If-None-Match");
			if (etag.equals(noneMatch)) {
				response.setStatus(HttpServletResponse.SC_NOT_MODIFIED);
				return true;
			} else if (noneMatch == null)
				try {
					long ifModified = request.getDateHeader("If-Modified-Since");
					if (ifModified != -1 && lastModified <= ifModified) {
						response.setStatus(HttpServletResponse.SC_NOT_MODIFIED);
						return true;
					}
				} catch (IllegalArgumentException e) {
					LOG.log(Level.INFO, e, () -> "Invalid date header " + request.getHeader("If-Modified-Since"));
				}

			Calendar c = Calendar.getInstance();
			c.add(Calendar.HOUR_OF_DAY, -5);
			c.add(Calendar.DATE, 1);
			c.set(Calendar.HOUR_OF_DAY, 5);
			c.set(Calendar.MINUTE, 0);
			c.set(Calendar.SECOND, 0);
			c.set(Calendar.MILLISECOND, 0);
			response.setHeader("Cache-Control", "public");
			SimpleDateFormat df = new SimpleDateFormat("E, dd MMM yyyy HH:mm:ss z");
			df.setTimeZone(TimeZone.getTimeZone("GMT"));
			response.setHeader("Expires", df.format(c.getTime()));
			response.setHeader("Pragma", "public");
		}

		send(request, response, resourceUrl::openStream, contentType);

		return true;
	}

	static void send(HttpServletRequest request, HttpServletResponse response, IOSupplier<InputStream> input,
			String contentType) throws IOException {
		if (contentType != null)
			response.setContentType(contentType);

		ByteArrayOutputStream contentBuffer = new ByteArrayOutputStream();
		try (InputStream in = input.get()) {
			String contentEncoding = getRequestedContentEncoding(request);
			if ("gzip".equals(contentEncoding) || "deflate".equals(contentEncoding)) {
				response.setHeader("Content-Encoding", contentEncoding);
				try (DeflaterOutputStream out = "gzip".equals(contentEncoding) ? new GZIPOutputStream(contentBuffer)
						: new DeflaterOutputStream(contentBuffer)) {
					IuStream.copy(in, out);
					out.finish();
				}
			} else
				IuStream.copy(in, contentBuffer);
		}

		response.setContentLength(contentBuffer.size());
		OutputStream out = response.getOutputStream();
		out.write(contentBuffer.toByteArray());
		out.flush();
	}

}
