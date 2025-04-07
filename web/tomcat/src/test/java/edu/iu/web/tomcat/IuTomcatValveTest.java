package edu.iu.web.tomcat;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.Arrays;
import java.util.logging.Level;

import org.apache.catalina.Pipeline;
import org.apache.catalina.Wrapper;
import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.apache.catalina.valves.ValveBase;
import org.junit.jupiter.api.Test;

import edu.iu.IuNotFoundException;
import edu.iu.test.IuTestLogger;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletResponse;

public class IuTomcatValveTest {

	@Test
	void invoke_withStaticResource_returnsImmediately() throws IOException, ServletException {
		Request request = mock(Request.class);
		Response response = mock(Response.class);
		try (final var iuTomcatUtil = mockStatic(IuTomcatUtil.class)) {
			iuTomcatUtil.when(() -> IuTomcatUtil.doStaticResource(true, request, response)).thenReturn(true);
			IuTomcatValve valve = new IuTomcatValve();
			assertDoesNotThrow(() -> valve.invoke(request, response));
		}
	}

	@Test
	void invoke_withNullWrapper_sendsNotFoundError() throws IOException, ServletException {
		Request request = mock(Request.class);
		when(request.getPathInfo()).thenReturn("/test-path");
		when(request.getServletPath()).thenReturn("/test-servlet");
		when(request.getWrapper()).thenReturn(null);
		when(request.getAttribute("requestPath")).thenReturn("/test-servlet/test-path");
		Response response = mock(Response.class);
		try (final var iuTomcatUtil = mockStatic(IuTomcatUtil.class)) {
			iuTomcatUtil.when(() -> IuTomcatUtil.doStaticResource(true, request, response)).thenReturn(false);
			IuTomcatValve valve = new IuTomcatValve();
			assertDoesNotThrow(() -> valve.invoke(request, response));
			verify(response).sendError(HttpServletResponse.SC_NOT_FOUND);
		}
	}

	@Test
	void invoke_withUnavailableWrapper_sendsNotFoundError() throws IOException, ServletException {
		Request request = mock(Request.class);
		when(request.getPathInfo()).thenReturn("/test-path");
		when(request.getServletPath()).thenReturn("/test-servlet");
		when(request.getWrapper()).thenReturn(null);
		when(request.getAttribute("requestPath")).thenReturn("/test-servlet/test-path");
		Response response = mock(Response.class);
		Wrapper wrapper = mock(Wrapper.class);
		when(request.getWrapper()).thenReturn(wrapper);
		when(wrapper.isUnavailable()).thenReturn(true);
		try (final var iuTomcatUtil = mockStatic(IuTomcatUtil.class)) {
			iuTomcatUtil.when(() -> IuTomcatUtil.doStaticResource(true, request, response)).thenReturn(false);
			IuTomcatValve valve = new IuTomcatValve();
			assertDoesNotThrow(() -> valve.invoke(request, response));
			verify(response).sendError(HttpServletResponse.SC_NOT_FOUND);
		}
	}

	@Test
	void invoke_withValidRequestNotFoundAndAborted_invokesWrapperPipelineAndLogsConfig()
			throws IOException, ServletException {
		Request request = mock(Request.class);
		when(request.getPathInfo()).thenReturn("/test-path");
		when(request.getServletPath()).thenReturn("/test-servlet");
		when(request.getWrapper()).thenReturn(null);
		when(request.getAttribute("requestPath")).thenReturn("/test-servlet/test-path");
		when(request.isAsyncSupported()).thenReturn(true);
		Response response = mock(Response.class);
		Wrapper wrapper = mock(Wrapper.class);
		when(request.getWrapper()).thenReturn(wrapper);
		when(wrapper.isUnavailable()).thenReturn(false);
		Pipeline pipeline = mock(Pipeline.class);
		when(wrapper.getPipeline()).thenReturn(pipeline);
		when(pipeline.isAsyncSupported()).thenReturn(true);
		when(pipeline.getFirst()).thenReturn(mock(ValveBase.class));
		when(response.getStatus()).thenReturn(HttpServletResponse.SC_NOT_FOUND);
		when(request.getAttribute("iuClientAbort")).thenReturn(true);
		when(request.getAttribute("iuRequestException")).thenReturn(new IuNotFoundException("test-exception"));
		when(request.getAttribute(RequestDispatcher.ERROR_EXCEPTION))
				.thenReturn(new IllegalArgumentException("test-error"));
		when(request.getRequestURI()).thenReturn("http://localhost/test-uri");
		when(response.isCommitted()).thenReturn(true);
		when(response.getHeaderNames()).thenReturn(Arrays.asList("test-header"));
		when(response.getHeaders("test-header")).thenReturn(Arrays.asList("test-value"));
		when(response.getMessage()).thenReturn("test-message");
		IuTestLogger.expect("edu.iu.web.tomcat.IuTomcatValve", Level.CONFIG,
				"HTTP 404 NOT FOUND (client aborted) http://localhost/test-uri\ntest-header: test-value\ntest-message",
				IuNotFoundException.class);
		try (final var iuTomcatUtil = mockStatic(IuTomcatUtil.class)) {
			iuTomcatUtil.when(() -> IuTomcatUtil.doStaticResource(true, request, response)).thenReturn(false);
			IuTomcatValve valve = new IuTomcatValve();
			assertDoesNotThrow(() -> valve.invoke(request, response));
			verify(wrapper.getPipeline().getFirst()).invoke(request, response);
		}
	}

	@Test
	void invoke_withValidRequestServiceUnavailableAndNotAborted_invokesWrapperPipelineAndLogsConfig()
			throws IOException, ServletException {
		Request request = mock(Request.class);
		when(request.getPathInfo()).thenReturn("/test-path");
		when(request.getServletPath()).thenReturn("/test-servlet");
		when(request.getWrapper()).thenReturn(null);
		when(request.getAttribute("requestPath")).thenReturn("/test-servlet/test-path");
		when(request.isAsyncSupported()).thenReturn(true);
		Response response = mock(Response.class);
		Wrapper wrapper = mock(Wrapper.class);
		when(request.getWrapper()).thenReturn(wrapper);
		when(wrapper.isUnavailable()).thenReturn(false);
		Pipeline pipeline = mock(Pipeline.class);
		when(wrapper.getPipeline()).thenReturn(pipeline);
		when(pipeline.isAsyncSupported()).thenReturn(true);
		when(pipeline.getFirst()).thenReturn(mock(ValveBase.class));
		when(response.getStatus()).thenReturn(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
		when(request.getAttribute("iuClientAbort")).thenReturn(false);
		when(request.getAttribute("iuRequestException")).thenReturn(null);
		when(request.getAttribute(RequestDispatcher.ERROR_EXCEPTION))
				.thenReturn(new IllegalArgumentException("test-error"));
		when(request.getRequestURI()).thenReturn("http://localhost/test-uri");
		when(response.isCommitted()).thenReturn(true);
		when(response.getHeaderNames()).thenReturn(Arrays.asList("test-header"));
		when(response.getHeaders("test-header")).thenReturn(Arrays.asList("test-value"));
		when(response.getMessage()).thenReturn("test-message");
		IuTestLogger.expect("edu.iu.web.tomcat.IuTomcatValve", Level.CONFIG,
				"HTTP 503 SERVICE UNAVAILABLE http://localhost/test-uri\ntest-header: test-value\ntest-message",
				IllegalArgumentException.class);
		try (final var iuTomcatUtil = mockStatic(IuTomcatUtil.class)) {
			iuTomcatUtil.when(() -> IuTomcatUtil.doStaticResource(true, request, response)).thenReturn(false);
			IuTomcatValve valve = new IuTomcatValve();
			assertDoesNotThrow(() -> valve.invoke(request, response));
			verify(wrapper.getPipeline().getFirst()).invoke(request, response);
		}
	}

	@Test
	void invoke_withValidRequestFoundAndNotAbortedWithDuplicateHeader_invokesWrapperPipelineAndLogsInfo()
			throws IOException, ServletException {
		Request request = mock(Request.class);
		when(request.getPathInfo()).thenReturn("/test-path");
		when(request.getServletPath()).thenReturn("/test-servlet");
		when(request.getWrapper()).thenReturn(null);
		when(request.getAttribute("requestPath")).thenReturn("/test-servlet/test-path");
		when(request.isAsyncSupported()).thenReturn(true);
		Response response = mock(Response.class);
		Wrapper wrapper = mock(Wrapper.class);
		when(request.getWrapper()).thenReturn(wrapper);
		when(wrapper.isUnavailable()).thenReturn(false);
		Pipeline pipeline = mock(Pipeline.class);
		when(wrapper.getPipeline()).thenReturn(pipeline);
		when(pipeline.isAsyncSupported()).thenReturn(true);
		when(pipeline.getFirst()).thenReturn(mock(ValveBase.class));
		when(response.getStatus()).thenReturn(HttpServletResponse.SC_FOUND);
		when(request.getAttribute("iuClientAbort")).thenReturn(false);
		when(request.getAttribute("iuRequestException")).thenReturn(null);
		when(request.getAttribute(RequestDispatcher.ERROR_EXCEPTION)).thenReturn(null);
		when(request.getRequestURI()).thenReturn("http://localhost/test-uri");
		when(response.isCommitted()).thenReturn(true);
		when(response.getHeaderNames()).thenReturn(Arrays.asList("test-header", "test-header"));
		when(response.getHeaders("test-header")).thenReturn(Arrays.asList("test-value"));
		when(response.getMessage()).thenReturn("test-message");
		IuTestLogger.expect("edu.iu.web.tomcat.IuTomcatValve", Level.FINE,
				"HTTP 302 FOUND http://localhost/test-uri\ntest-header: test-value\ntest-message");
		try (final var iuTomcatUtil = mockStatic(IuTomcatUtil.class)) {
			iuTomcatUtil.when(() -> IuTomcatUtil.doStaticResource(true, request, response)).thenReturn(false);
			IuTomcatValve valve = new IuTomcatValve();
			assertDoesNotThrow(() -> valve.invoke(request, response));
			verify(wrapper.getPipeline().getFirst()).invoke(request, response);
		}
	}

	@Test
	void invoke_withValidRequestUnauthorizedAndAbortedWithNullPathNoAsync_invokesWrapperPipelineAndLogsInfo()
			throws IOException, ServletException {
		Request request = mock(Request.class);
		when(request.getPathInfo()).thenReturn(null);
		when(request.getServletPath()).thenReturn("/test-servlet");
		when(request.getWrapper()).thenReturn(null);
		when(request.getAttribute("requestPath")).thenReturn("/test-servlet");
		when(request.isAsyncSupported()).thenReturn(false);
		Response response = mock(Response.class);
		Wrapper wrapper = mock(Wrapper.class);
		when(request.getWrapper()).thenReturn(wrapper);
		when(wrapper.isUnavailable()).thenReturn(false);
		Pipeline pipeline = mock(Pipeline.class);
		when(wrapper.getPipeline()).thenReturn(pipeline);
		when(pipeline.isAsyncSupported()).thenReturn(false);
		when(pipeline.getFirst()).thenReturn(mock(ValveBase.class));
		when(response.getStatus()).thenReturn(HttpServletResponse.SC_UNAUTHORIZED);
		when(request.getAttribute("iuClientAbort")).thenReturn(true);
		when(request.getAttribute("iuRequestException")).thenReturn(null);
		when(request.getAttribute(RequestDispatcher.ERROR_EXCEPTION)).thenReturn(null);
		when(request.getRequestURI()).thenReturn("http://localhost/test-uri");
		when(response.isCommitted()).thenReturn(false);
		when(response.getHeaderNames()).thenReturn(Arrays.asList("test-header"));
		when(response.getHeaders("test-header")).thenReturn(Arrays.asList("test-value"));
		when(response.getMessage()).thenReturn("test-message");
		IuTestLogger.expect("edu.iu.web.tomcat.IuTomcatValve", Level.INFO,
				"HTTP 401 UNAUTHORIZED (client aborted) http://localhost/test-uri\ntest-header: test-value\ntest-message");
		try (final var iuTomcatUtil = mockStatic(IuTomcatUtil.class)) {
			iuTomcatUtil.when(() -> IuTomcatUtil.doStaticResource(true, request, response)).thenReturn(false);
			IuTomcatValve valve = new IuTomcatValve();
			assertDoesNotThrow(() -> valve.invoke(request, response));
			verify(wrapper.getPipeline().getFirst()).invoke(request, response);
		}
	}

	@Test
	void invoke_withValidRequestUnauthorizedAndNotAbortedWithNullPathNoAsyncNullMessage_invokesWrapperPipelineAndLogsInfo()
			throws IOException, ServletException {
		Request request = mock(Request.class);
		when(request.getPathInfo()).thenReturn(null);
		when(request.getServletPath()).thenReturn("/test-servlet");
		when(request.getWrapper()).thenReturn(null);
		when(request.getAttribute("requestPath")).thenReturn("/test-servlet");
		when(request.isAsyncSupported()).thenReturn(false);
		Response response = mock(Response.class);
		Wrapper wrapper = mock(Wrapper.class);
		when(request.getWrapper()).thenReturn(wrapper);
		when(wrapper.isUnavailable()).thenReturn(false);
		Pipeline pipeline = mock(Pipeline.class);
		when(wrapper.getPipeline()).thenReturn(pipeline);
		when(pipeline.isAsyncSupported()).thenReturn(false);
		when(pipeline.getFirst()).thenReturn(mock(ValveBase.class));
		when(response.getStatus()).thenReturn(HttpServletResponse.SC_UNAUTHORIZED);
		when(request.getAttribute("iuClientAbort")).thenReturn(false);
		when(request.getAttribute("iuRequestException")).thenReturn(null);
		when(request.getAttribute(RequestDispatcher.ERROR_EXCEPTION)).thenReturn(null);
		when(request.getRequestURI()).thenReturn("http://localhost/test-uri");
		when(response.isCommitted()).thenReturn(false);
		when(response.getHeaderNames()).thenReturn(Arrays.asList("test-header"));
		when(response.getHeaders("test-header")).thenReturn(Arrays.asList("test-value"));
		when(response.getMessage()).thenReturn(null);
		IuTestLogger.expect("edu.iu.web.tomcat.IuTomcatValve", Level.INFO,
				"HTTP 401 UNAUTHORIZED http://localhost/test-uri\ntest-header: test-value");
		try (final var iuTomcatUtil = mockStatic(IuTomcatUtil.class)) {
			iuTomcatUtil.when(() -> IuTomcatUtil.doStaticResource(true, request, response)).thenReturn(false);
			IuTomcatValve valve = new IuTomcatValve();
			assertDoesNotThrow(() -> valve.invoke(request, response));
			verify(wrapper.getPipeline().getFirst()).invoke(request, response);
		}
	}

	@Test
	void invoke_withValidRequestInternalServerErrorAndFlushBufferError_invokesWrapperPipelineAndLogsWarning()
			throws IOException, ServletException {
		Request request = mock(Request.class);
		when(request.getPathInfo()).thenReturn(null);
		when(request.getServletPath()).thenReturn("/test-servlet");
		when(request.getWrapper()).thenReturn(null);
		when(request.getAttribute("requestPath")).thenReturn("/test-servlet");
		when(request.isAsyncSupported()).thenReturn(false);
		Response response = mock(Response.class);
		Wrapper wrapper = mock(Wrapper.class);
		when(request.getWrapper()).thenReturn(wrapper);
		when(wrapper.isUnavailable()).thenReturn(false);
		Pipeline pipeline = mock(Pipeline.class);
		when(wrapper.getPipeline()).thenReturn(pipeline);
		when(pipeline.isAsyncSupported()).thenReturn(false);
		when(pipeline.getFirst()).thenReturn(mock(ValveBase.class));
		when(response.getStatus()).thenReturn(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
		when(request.getAttribute("iuClientAbort")).thenReturn(false);
		when(request.getAttribute("iuRequestException")).thenReturn(null);
		when(request.getAttribute(RequestDispatcher.ERROR_EXCEPTION))
				.thenReturn(new IllegalStateException("test-error"));
		when(request.getRequestURI()).thenReturn("http://localhost/test-uri");
		when(response.isCommitted()).thenReturn(true);
		when(response.getHeaderNames()).thenReturn(Arrays.asList("test-header"));
		when(response.getHeaders("test-header")).thenReturn(Arrays.asList("test-value"));
		when(response.getMessage()).thenReturn(null);
		doThrow(new IOException("test-io")).when(response).flushBuffer();
		IuTestLogger.expect("edu.iu.web.tomcat.IuTomcatValve", Level.WARNING,
				"HTTP 500 INTERNAL SERVER ERROR http://localhost/test-uri\ntest-header: test-value",
				IllegalStateException.class);
		IuTestLogger.expect("edu.iu.web.tomcat.IuTomcatValve", Level.INFO,
				"Failed to flush buffer setting error status on committed response", IOException.class);
		try (final var iuTomcatUtil = mockStatic(IuTomcatUtil.class)) {
			iuTomcatUtil.when(() -> IuTomcatUtil.doStaticResource(true, request, response)).thenReturn(false);
			IuTomcatValve valve = new IuTomcatValve();
			assertDoesNotThrow(() -> valve.invoke(request, response));
			verify(wrapper.getPipeline().getFirst()).invoke(request, response);
		}
	}

}
