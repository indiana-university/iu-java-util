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
package iu.web.server;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.RequestDispatcher;
import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.WriteListener;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletResponseWrapper;

import edu.iu.IuAuthorizationFailedException;
import edu.iu.IuBadRequestException;
import edu.iu.IuNotFoundException;
import edu.iu.IuOutOfServiceException;
import edu.iu.IuWebUtils;

/**
 * 
 */
public class ResponseWrapper {
	private final static Logger LOG = Logger.getLogger(ResponseWrapper.class.getName());

	// TODO: gzip?
	// TODO: remove application logic; buffer and compress only
	
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
	private Throwable contentTypeSet;

	static void handleError(HttpServletRequest hreq, HttpServletResponse hresp, Throwable e) {
		handleError(hreq, hresp, 0, null, e);
	}

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
	 * @param request
	 * @param response
	 */
	public ResponseWrapper(HttpServletRequest request, HttpServletResponse response) {
		super(response);
		this.req = request;
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

		handleError(req, (HttpServletResponse) getResponse(), sc, msg,
				(Throwable) req.getAttribute(RequestDispatcher.ERROR_EXCEPTION));

		req.setAttribute("iu.endpoint.statusTrace",
				new Throwable("HTTP (error) " + IuWebUtils.describeStatus(status) + (msg == null ? "" : " " + msg)));
	}

	@Override
	public void sendRedirect(String location) throws IOException {
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
	public void addHeader(String name, String value) {
		if (value != null)
			super.addHeader(name, value);
		if ("cache-control".equalsIgnoreCase(name) || "expires".equalsIgnoreCase(name))
			cacheControlSeen = true;
		if ("x-frame-options".equalsIgnoreCase(name))
			frameOptionsSeen = true;
		if ("content-security-policy".equalsIgnoreCase(name))
			contentSecurityPolicySeen = true;
	}

	@Override
	public void flushBuffer() throws IOException {
		writer.flush();
		try {
			outputStream.flush();
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
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

	void finish() throws IOException {
		writer.flush();
		try {
			outputStream.flush();
			outputBuffer.flush();
		} catch (IOException e) {
			throw new IllegalStateException("Error retrieving servlet output buffer", e);
		}

		HttpServletResponse response = (HttpServletResponse) getResponse();

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

			try {
				ServletOutputStream out = response.getOutputStream();
				out.write(content);
				out.flush();
			} catch (Throwable e) {
				LOG.log(Level.INFO, "Client abort detected on final write", e);
			}

		} else if (status == 0)
			sendError(HttpServletResponse.SC_NOT_IMPLEMENTED,
					"No status reported and or content buffered - it appears that the servlet did nothing");
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

		super.setStatus(sc);
		if (status != 200)
			req.setAttribute("iu.endpoint.statusTrace", new Throwable("HTTP " + IuWebUtils.describeStatus(status)));
	}

}
