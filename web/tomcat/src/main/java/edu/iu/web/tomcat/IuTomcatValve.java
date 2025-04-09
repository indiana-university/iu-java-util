package edu.iu.web.tomcat;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.catalina.Wrapper;
import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.apache.catalina.valves.ValveBase;
import org.apache.coyote.ContinueResponseTiming;

import edu.iu.IuWebUtils;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletResponse;

/**
 * An implementation of the Tomcat Valve.
 */
public class IuTomcatValve extends ValveBase {

	private static final Logger LOG = Logger.getLogger(IuTomcatValve.class.getName());

	// TODO: implement request number
	private static int requestNumber = 0;

	/**
	 * Default Constructor.
	 */
	public IuTomcatValve() {
		// Default constructor.
	}

	@Override
	public void invoke(Request request, Response response) throws IOException, ServletException {

		if (IuTomcatUtil.doStaticResource(true, request, response))
			return;

		// Force encoding to UTF-8
		request.setCharacterEncoding("UTF-8");
		response.setCharacterEncoding("UTF-8");

		String pathInfo = request.getPathInfo();
		if (pathInfo == null)
			request.setAttribute("requestPath", request.getServletPath());
		else
			request.setAttribute("requestPath", request.getServletPath() + pathInfo);
		request.setAttribute("requestNumber", requestNumber++);

		Wrapper wrapper = request.getWrapper();
		if (wrapper == null || wrapper.isUnavailable()) {
			response.sendError(HttpServletResponse.SC_NOT_FOUND);
			return;
		}

		response.sendAcknowledgement(ContinueResponseTiming.ALWAYS);

		Thread current = Thread.currentThread();
		ClassLoader classLoader = current.getContextClassLoader();

		if (request.isAsyncSupported())
			request.setAsyncSupported(wrapper.getPipeline().isAsyncSupported());
		wrapper.getPipeline().getFirst().invoke(request, response);

		current.setContextClassLoader(classLoader);

		int sc = response.getStatus();
		boolean clientAbort = Boolean.TRUE.equals(request.getAttribute("iuClientAbort"));
		Throwable cause = (Throwable) request.getAttribute("iuRequestException");
		Throwable error = (Throwable) request.getAttribute(RequestDispatcher.ERROR_EXCEPTION);
		if (error != null)
			if (cause != null)
				cause.addSuppressed(error);
			else
				cause = error;
		if (cause != null)
			IuTomcatInvoker.reportError(cause);

		Level level;
		if (sc == HttpServletResponse.SC_NOT_FOUND || sc == HttpServletResponse.SC_SERVICE_UNAVAILABLE)
			level = Level.CONFIG;
		else if (sc < 400)
			level = Level.FINE;
		else if (clientAbort || sc < 500)
			level = Level.INFO;
		else
			level = Level.WARNING;

		Throwable _cause = cause;
		LOG.log(level, cause, () -> {
			StringBuilder sb = new StringBuilder("HTTP ");
			sb.append(IuWebUtils.describeStatus(sc));
			if (clientAbort)
				sb.append(" (client aborted) ");
			else
				sb.append(' ');
			sb.append(request.getRequestURI());

			Set<String> headerNames = new HashSet<>();
			for (String headerName : response.getHeaderNames())
				if (headerNames.add(headerName))
					for (String header : response.getHeaders(headerName))
						sb.append('\n').append(headerName).append(": ").append(header);

			String message = response.getMessage();
			if (message != null && (_cause == null || !message.equals(_cause.getMessage())))
				sb.append("\n").append(response.getMessage());
			return sb.toString();
		});

		if (response.isCommitted())
			try {
				response.flushBuffer();
			} catch (Throwable e) {
				LOG.log(Level.INFO, e, () -> "Failed to flush buffer setting error status on committed response");
			}
	}

}
