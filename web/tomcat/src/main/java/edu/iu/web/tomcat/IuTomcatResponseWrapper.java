package edu.iu.web.tomcat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.util.logging.Level;
import java.util.logging.Logger;

import edu.iu.IuWebUtils;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;

/**
 * Response wrapper for Tomcat.
 */
class IuTomcatResponseWrapper extends HttpServletResponseWrapper {

	private final static Logger LOG = Logger.getLogger(IuTomcatResponseWrapper.class.getName());

	private final HttpServletRequest req;
	private final ByteArrayOutputStream outputBuffer;
	private final ServletOutputStream outputStream;
	private final PrintWriter writer;
	private int status;
	private String contentType;
	private Throwable firstSetStatus;
	private boolean cacheControlSeen;
	private boolean frameOptionsSeen;
	private boolean contentSecurityPolicySeen;
	private boolean authCookiesAdded;
	private Throwable contentTypeSet;

	/**
	 * Constructor.
	 * 
	 * @param req The request.
	 * @param resp The response.
	 */
	IuTomcatResponseWrapper(HttpServletRequest req, HttpServletResponse resp) {
		super(resp);
		this.req = req;
		this.outputBuffer = new ByteArrayOutputStream();
		this.outputStream = new ServletOutputStream() {
			boolean written = false;

			@Override
			public void write(int b) throws IOException {
				if (!written)
					written = true;
				outputBuffer.write(b);
			}

			@Override
			public boolean isReady() {
				return true;
			}

			@Override
			public void setWriteListener(WriteListener listener) {
				try {
					listener.onWritePossible();
				} catch (IOException e) {
					throw new IllegalStateException(e);
				}
			}
		};

		try {
			this.writer = new PrintWriter(new OutputStreamWriter(outputStream, "UTF-8"));
		} catch (UnsupportedEncodingException e) {
			throw new IllegalStateException(e);
		}
	}

	private void addAuthCookies() {
		if (authCookiesAdded)
			return;
		// TODO: Implement without deprecated SPI and IuIdentityManager methods
//		if (IuResources.SPI.isActive()) {
//			IuIdentityManager iuIdentityManager = IU.getResource(IuIdentityManager.class);
//			Map<String, ClientVerification> tokens = iuIdentityManager.getForwardAuthClientVerification();
//			if (tokens != null)
//				for (Entry<String, ClientVerification> tokenEntry : tokens.entrySet()) {
//					String accessToken = tokenEntry.getKey();
//					ClientVerification clientVerification = tokenEntry.getValue();
//					String secret = Base64.getUrlEncoder().encodeToString(clientVerification.getSignature());
//
//					StringBuilder setCookie = new StringBuilder("iuid");
//					setCookie.append(Long.toString(accessToken.hashCode(), Character.MAX_RADIX));
//					setCookie.append('=');
//					setCookie.append(secret);
//					setCookie.append("; Path=/; Max-Age=");
//					setCookie.append(clientVerification.getMaxAge());
//					if (!IU.SPI.isDevelopment())
//						setCookie.append("; Secure");
//					setCookie.append("; HttpOnly");
//					setCookie.append("; SameSite=Lax");
//					addHeader("Set-Cookie", setCookie.toString());
//					LOG.fine(() -> "Added token cookie " + setCookie);
//				}
//		}
		authCookiesAdded = true;
	}

	@Override
	public void setStatus(int sc) {
		if (status > 0 && status != sc) {
			LOG.log(Level.INFO, new Throwable(firstSetStatus),
					() -> "Status was already set, attempting to set status to " + IuWebUtils.describeStatus(sc)
							+ ", discarding new status");
			return;
		}

		if (sc == 0)
			return;

		status = sc;

		if (firstSetStatus == null)
			firstSetStatus = new Throwable("Status already set " + IuWebUtils.describeStatus(sc));
		else
			firstSetStatus.addSuppressed(new Throwable());

		super.setStatus(sc);
	}

	@Override
	public void sendError(int sc) throws IOException {
		this.sendError(sc, null);
	}

	@Override
	public void sendError(int sc, String msg) throws IOException {
		if (status > 0 && status != sc)
			LOG.log(Level.INFO, new Throwable(firstSetStatus),
					() -> "Status was already set, setting error status to " + IuWebUtils.describeStatus(sc));
		if (sc < 400)
			LOG.log(Level.INFO, new Throwable(firstSetStatus),
					() -> "Sending error with non-error status " + IuWebUtils.describeStatus(sc));
		else
			LOG.log(Level.FINE, new Throwable(firstSetStatus),
					() -> "Error status set " + IuWebUtils.describeStatus(sc));

		status = sc;

		if (firstSetStatus == null)
			firstSetStatus = new Throwable("Status already set " + IuWebUtils.describeStatus(sc));
		else
			firstSetStatus.addSuppressed(new Throwable());

		IuTomcatRequestFilter.handleError(req, (HttpServletResponse) getResponse(), sc, msg,
				(Throwable) req.getAttribute(RequestDispatcher.ERROR_EXCEPTION));
	}

	@Override
	public void sendRedirect(String location) throws IOException {
		addAuthCookies();
		setStatus(HttpServletResponse.SC_FOUND);
		setHeader("Location", location);
	}

	@Override
	public String getContentType() {
		return contentType;
	}

	@Override
	public void setContentType(String type) {
		if (type == null)
			return;

		if (contentType != null && !contentType.equals(type))
			LOG.log(Level.INFO, new Throwable(contentTypeSet),
					() -> "Content type was already set to " + contentType + " setting to " + type);

		contentType = type;
		super.setContentType(type);
	}

	@Override
	public void setHeader(String name, String value) {
		if (value != null)
			super.setHeader(name, value);
		if ("cache-control".equalsIgnoreCase(name) || "expires".equalsIgnoreCase(name))
			cacheControlSeen = true;
		if ("x-frame-options".equalsIgnoreCase(name))
			frameOptionsSeen = true;
		if ("content-security-policy".equalsIgnoreCase(name))
			contentSecurityPolicySeen = true;
	}

	@Override
	public ServletOutputStream getOutputStream() throws IOException {
		return outputStream;
	}

	@Override
	public PrintWriter getWriter() throws IOException {
		return writer;
	}

	@Override
	public void reset() {
		writer.flush();
		try {
			outputStream.flush();
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
		outputBuffer.reset();
		contentType = null;
		status = 0;
		super.reset();
	}

	@Override
	public void resetBuffer() {
		writer.flush();
		try {
			outputStream.flush();
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
		outputBuffer.reset();
		contentType = null;
		super.resetBuffer();
	}

	/**
	 * Finish the response.
	 * 
	 * @throws IOException If an error occurs.
	 */
	void finish() throws IOException {
		writer.flush();
		try {
			outputStream.flush();
			outputBuffer.flush();
		} catch (IOException e) {
			throw new IllegalStateException("Error retrieving servlet output buffer", e);
		}

		HttpServletResponse response = (HttpServletResponse) getResponse();

		addAuthCookies();

		if (!frameOptionsSeen && !contentSecurityPolicySeen)
			response.setHeader("Content-Security-Policy", "frame-ancestors 'self';");

		byte[] content = outputBuffer.toByteArray();
		if (content.length > 0) {
			if (status == 0)
				setStatus(HttpServletResponse.SC_OK);
			if (!cacheControlSeen) {
				response.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
				response.setHeader("Pragma", "no-cache");
				response.setHeader("Expires", "0");
			}

			ServletOutputStream out = response.getOutputStream();
			out.write(content);
			out.flush();

		} else if (status == 0)
			sendError(HttpServletResponse.SC_NOT_IMPLEMENTED,
					"No status reported and or content buffered - it appears that the servlet did nothing");
	}

}
