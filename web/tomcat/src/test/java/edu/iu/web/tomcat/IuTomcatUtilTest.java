package edu.iu.web.tomcat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.logging.Level;

import org.junit.jupiter.api.Test;

import edu.iu.IuStream;
import edu.iu.test.IuTestLogger;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@SuppressWarnings("javadoc")
public class IuTomcatUtilTest {

	@Test
	void doStaticResource_withValidResource_returnsTrue() throws IOException, ServletException {
		HttpServletRequest request = mock(HttpServletRequest.class);
		HttpServletResponse response = mock(HttpServletResponse.class);
		ServletContext servletContext = mock(ServletContext.class);
		URL resourceUrl = new URL("file:/test/resource");
		when(request.getServletContext()).thenReturn(servletContext);
		when(servletContext.getResource("/test/resource")).thenReturn(resourceUrl);
		when(servletContext.getMimeType("/test/resource")).thenReturn("text/plain");
		when(request.getServletPath()).thenReturn("/test/resource");
		try (final var iuStream = mockStatic(IuStream.class)) {
			assertTrue(IuTomcatUtil.doStaticResource(true, request, response));
		}
	}

	@Test
	void doStaticResource_withValidResourceNoContentType_returnsFalse() throws IOException, ServletException {
		HttpServletRequest request = mock(HttpServletRequest.class);
		HttpServletResponse response = mock(HttpServletResponse.class);
		ServletContext servletContext = mock(ServletContext.class);
		URL resourceUrl = new URL("file:/test/resource");
		when(request.getServletContext()).thenReturn(servletContext);
		when(servletContext.getResource("/test/resource")).thenReturn(resourceUrl);
		when(servletContext.getMimeType("/test/resource")).thenReturn(null);
		when(request.getServletPath()).thenReturn("/test/resource");
		try (final var iuStream = mockStatic(IuStream.class)) {
			assertFalse(IuTomcatUtil.doStaticResource(true, request, response));
		}
	}

	@Test
	void doStaticResource_withValidResourceAndPathNoCache_returnsTrueAndSetsCacheHeaders()
			throws IOException, ServletException {
		IuTestLogger.expect("edu.iu.web.tomcat.IuTomcatUtil", Level.FINE, "Accept-Encoding:*");
		HttpServletRequest request = mock(HttpServletRequest.class);
		HttpServletResponse response = mock(HttpServletResponse.class);
		ServletContext servletContext = mock(ServletContext.class);
		URL resourceUrl = mock(URL.class);
		URLConnection urlConnection = mock(URLConnection.class);
		when(resourceUrl.openConnection()).thenReturn(urlConnection);
		InputStream inputStream = mock(InputStream.class, CALLS_REAL_METHODS);
		when(resourceUrl.openStream()).thenReturn(inputStream);
		when(request.getPathInfo()).thenReturn("/path");
		when(request.getServletContext()).thenReturn(servletContext);
		when(servletContext.getResource("/test/resource/path")).thenReturn(resourceUrl);
		when(servletContext.getMimeType("/test/resource/path")).thenReturn("text/plain");
		when(request.getServletPath()).thenReturn("/test/resource");
		when(request.getHeader("Accept-Encoding")).thenReturn("*");
		when(response.getOutputStream()).thenReturn(mock(ServletOutputStream.class));

		try (final var iuStream = mockStatic(IuStream.class)) {
			assertTrue(IuTomcatUtil.doStaticResource(false, request, response));
		}
		verify(response).setHeader("Cache-Control", "must-revalidate, post-check=0, pre-check=0");
		verify(response).setHeader("Pragma", "public");
		verify(response).setHeader("Content-Encoding", "gzip");
	}

	@Test
	void doStaticResource_withValidResourceAndPathCacheFound_returnsTrue() throws IOException, ServletException {
		HttpServletRequest request = mock(HttpServletRequest.class);
		HttpServletResponse response = mock(HttpServletResponse.class);
		ServletContext servletContext = mock(ServletContext.class);
		URL resourceUrl = mock(URL.class);
		URLConnection urlConnection = mock(URLConnection.class);
		when(resourceUrl.openConnection()).thenReturn(urlConnection);
		InputStream inputStream = mock(InputStream.class, CALLS_REAL_METHODS);
		when(resourceUrl.openStream()).thenReturn(inputStream);
		when(request.getPathInfo()).thenReturn("/path");
		when(request.getServletContext()).thenReturn(servletContext);
		when(servletContext.getResource("/test/resource/path")).thenReturn(resourceUrl);
		when(request.getServletPath()).thenReturn("/test/resource");
		when(servletContext.getMimeType("/test/resource/path")).thenReturn("text/plain");
		// for send
		when(request.getHeader("Accept-Encoding")).thenReturn("*");
		when(response.getOutputStream()).thenReturn(mock(ServletOutputStream.class));

		when(request.getHeader("If-None-Match")).thenReturn("W/\"0-0\"");

		try (final var iuStream = mockStatic(IuStream.class)) {
			assertTrue(IuTomcatUtil.doStaticResource(true, request, response));
		}
	}

	@Test
	void doStaticResource_withValidResourceAndPathPragmaNoCache_returnsTrueAndDoesNotSetCacheHeaders()
			throws IOException, ServletException {
		IuTestLogger.expect("edu.iu.web.tomcat.IuTomcatUtil", Level.FINE, "Accept-Encoding:*");
		HttpServletRequest request = mock(HttpServletRequest.class);
		HttpServletResponse response = mock(HttpServletResponse.class);
		ServletContext servletContext = mock(ServletContext.class);
		URL resourceUrl = mock(URL.class);
		URLConnection urlConnection = mock(URLConnection.class);
		when(resourceUrl.openConnection()).thenReturn(urlConnection);
		InputStream inputStream = mock(InputStream.class, CALLS_REAL_METHODS);
		when(resourceUrl.openStream()).thenReturn(inputStream);
		when(request.getPathInfo()).thenReturn("/path");
		when(request.getServletContext()).thenReturn(servletContext);
		when(servletContext.getResource("/test/resource/path")).thenReturn(resourceUrl);
		when(request.getServletPath()).thenReturn("/test/resource");
		when(servletContext.getMimeType("/test/resource/path")).thenReturn("text/plain");
		when(request.getHeader("Pragma")).thenReturn("no-cache");
		// for send
		when(request.getHeader("Accept-Encoding")).thenReturn("*");
		when(response.getOutputStream()).thenReturn(mock(ServletOutputStream.class));

		try (final var iuStream = mockStatic(IuStream.class)) {
			assertTrue(IuTomcatUtil.doStaticResource(true, request, response));
		}
		verify(response, never()).setHeader("Cache-Control", "must-revalidate, post-check=0, pre-check=0");
		verify(response, never()).setHeader("Pragma", "public");
		verify(response).setHeader("Content-Encoding", "gzip");
	}

	@Test
	void doStaticResource_withValidResourceAndPathCacheControlNoCache_returnsTrueAndDoesNotSetCacheHeaders()
			throws IOException, ServletException {
		IuTestLogger.expect("edu.iu.web.tomcat.IuTomcatUtil", Level.FINE, "Accept-Encoding:*");
		HttpServletRequest request = mock(HttpServletRequest.class);
		HttpServletResponse response = mock(HttpServletResponse.class);
		ServletContext servletContext = mock(ServletContext.class);
		URL resourceUrl = mock(URL.class);
		URLConnection urlConnection = mock(URLConnection.class);
		when(resourceUrl.openConnection()).thenReturn(urlConnection);
		InputStream inputStream = mock(InputStream.class, CALLS_REAL_METHODS);
		when(resourceUrl.openStream()).thenReturn(inputStream);
		when(request.getPathInfo()).thenReturn("/path");
		when(request.getServletContext()).thenReturn(servletContext);
		when(servletContext.getResource("/test/resource/path")).thenReturn(resourceUrl);
		when(request.getServletPath()).thenReturn("/test/resource");
		when(servletContext.getMimeType("/test/resource/path")).thenReturn("text/plain");
		when(request.getHeader("Cache-Control")).thenReturn("no-cache");
		// for send
		when(request.getHeader("Accept-Encoding")).thenReturn("*");
		when(response.getOutputStream()).thenReturn(mock(ServletOutputStream.class));

		try (final var iuStream = mockStatic(IuStream.class)) {
			assertTrue(IuTomcatUtil.doStaticResource(true, request, response));
		}
		verify(response, never()).setHeader("Cache-Control", "must-revalidate, post-check=0, pre-check=0");
		verify(response, never()).setHeader("Pragma", "public");
		verify(response).setHeader("Content-Encoding", "gzip");
	}

	@Test
	void doStaticResource_withValidResourceAndPathCacheControlNoCacheIllegalModified_LogsException()
			throws IOException, ServletException {
		IuTestLogger.expect("edu.iu.web.tomcat.IuTomcatUtil", Level.FINE, "Accept-Encoding:*");
		IuTestLogger.expect("edu.iu.web.tomcat.IuTomcatUtil", Level.INFO, "Invalid date header invalid-date",
				IllegalArgumentException.class);
		HttpServletRequest request = mock(HttpServletRequest.class);
		HttpServletResponse response = mock(HttpServletResponse.class);
		ServletContext servletContext = mock(ServletContext.class);
		URL resourceUrl = mock(URL.class);
		URLConnection urlConnection = mock(URLConnection.class);
		when(resourceUrl.openConnection()).thenReturn(urlConnection);
		InputStream inputStream = mock(InputStream.class, CALLS_REAL_METHODS);
		when(resourceUrl.openStream()).thenReturn(inputStream);
		when(request.getPathInfo()).thenReturn("/path");
		when(request.getServletContext()).thenReturn(servletContext);
		when(servletContext.getResource("/test/resource/path")).thenReturn(resourceUrl);
		when(request.getServletPath()).thenReturn("/test/resource");
		when(servletContext.getMimeType("/test/resource/path")).thenReturn("text/plain");
		when(request.getDateHeader("If-Modified-Since")).thenThrow(IllegalArgumentException.class);
		when(request.getHeader("If-Modified-Since")).thenReturn("invalid-date");
		// for send
		when(request.getHeader("Accept-Encoding")).thenReturn("*");
		when(response.getOutputStream()).thenReturn(mock(ServletOutputStream.class));

		try (final var iuStream = mockStatic(IuStream.class)) {
			assertTrue(IuTomcatUtil.doStaticResource(true, request, response));
		}
		verify(response).setHeader("Cache-Control", "public");
		verify(response).setHeader("Pragma", "public");
		verify(response).setHeader("Content-Encoding", "gzip");
	}

	@Test
	void doStaticResource_withInvalidResource_returnsFalse() throws IOException, ServletException {
		HttpServletRequest request = mock(HttpServletRequest.class);
		HttpServletResponse response = mock(HttpServletResponse.class);
		ServletContext servletContext = mock(ServletContext.class);
		when(request.getServletContext()).thenReturn(servletContext);
		when(servletContext.getResource("/invalid/resource")).thenReturn(null);
		when(request.getServletPath()).thenReturn("/invalid/resource");
		try (final var iuStream = mockStatic(IuStream.class)) {
			assertFalse(IuTomcatUtil.doStaticResource(true, request, response));
		}
	}

	@Test
	void doStaticResource_withWebInfResource_returnsFalse() throws IOException, ServletException {
		HttpServletRequest request = mock(HttpServletRequest.class);
		HttpServletResponse response = mock(HttpServletResponse.class);
		when(request.getServletPath()).thenReturn("/WEB-INF/resource");
		try (final var iuStream = mockStatic(IuStream.class)) {
			assertFalse(IuTomcatUtil.doStaticResource(true, request, response));
		}
	}

	@Test
	void doStaticResource_withMetaInfResource_returnsFalse() throws IOException, ServletException {
		HttpServletRequest request = mock(HttpServletRequest.class);
		HttpServletResponse response = mock(HttpServletResponse.class);
		when(request.getServletPath()).thenReturn("/META-INF/resource");
		try (final var iuStream = mockStatic(IuStream.class)) {
			assertFalse(IuTomcatUtil.doStaticResource(true, request, response));
		}
	}

	@Test
	void doStaticResource_withJspResource_returnsFalse() throws IOException, ServletException {
		HttpServletRequest request = mock(HttpServletRequest.class);
		HttpServletResponse response = mock(HttpServletResponse.class);
		when(request.getServletPath()).thenReturn("/_jsp/resource");
		try (final var iuStream = mockStatic(IuStream.class)) {
			assertFalse(IuTomcatUtil.doStaticResource(true, request, response));
		}
	}

	@Test
	void doStaticResource_withTrailingSlash_returnsFalse() throws IOException, ServletException {
		HttpServletRequest request = mock(HttpServletRequest.class);
		HttpServletResponse response = mock(HttpServletResponse.class);
		when(request.getServletPath()).thenReturn("/something/");
		try (final var iuStream = mockStatic(IuStream.class)) {
			assertFalse(IuTomcatUtil.doStaticResource(true, request, response));
		}
	}

	@Test
	void doStaticResource_withParentResource_returnsFalse() throws IOException, ServletException {
		HttpServletRequest request = mock(HttpServletRequest.class);
		HttpServletResponse response = mock(HttpServletResponse.class);
		when(request.getServletPath()).thenReturn("/../resource");
		try (final var iuStream = mockStatic(IuStream.class)) {
			assertFalse(IuTomcatUtil.doStaticResource(true, request, response));
		}
	}

	@Test
	void getRequestedContentEncoding_withGzipEncoding_returnsGzip() {
		IuTestLogger.expect("edu.iu.web.tomcat.IuTomcatUtil", Level.FINE, "Accept-Encoding:gzip");
		HttpServletRequest request = mock(HttpServletRequest.class);
		when(request.getHeader("Accept-Encoding")).thenReturn("gzip");
		assertEquals("gzip", IuTomcatUtil.getRequestedContentEncoding(request));
	}

	@Test
	void getRequestedContentEncoding_withDeflateEncoding_returnsDeflate() {
		IuTestLogger.expect("edu.iu.web.tomcat.IuTomcatUtil", Level.FINE, "Accept-Encoding:deflate");
		HttpServletRequest request = mock(HttpServletRequest.class);
		when(request.getHeader("Accept-Encoding")).thenReturn("deflate");
		assertEquals("deflate", IuTomcatUtil.getRequestedContentEncoding(request));
	}

	@Test
	void getRequestedContentEncoding_withNoEncoding_returnsNull() {
		HttpServletRequest request = mock(HttpServletRequest.class);
		when(request.getHeader("Accept-Encoding")).thenReturn(null);
		assertNull(IuTomcatUtil.getRequestedContentEncoding(request));
	}

	@Test
	void getRequestedContentEncoding_withMultipleEncodingAndQValues_returnsHighestQ() {
		final var acceptEncoding = "gzip;q=0.0, gzip;q=x, gzip;g=1.0, compress, deflate;q=1.5";
		IuTestLogger.expect("edu.iu.web.tomcat.IuTomcatUtil", Level.FINE, "Accept-Encoding:" + acceptEncoding);
		IuTestLogger.expect("edu.iu.web.tomcat.IuTomcatUtil", Level.WARNING,
				"Invalid q value q=x in Accept-encoding header " + acceptEncoding, NumberFormatException.class);
		IuTestLogger.expect("edu.iu.web.tomcat.IuTomcatUtil", Level.WARNING,
				"Invalid q value g=1.0 in Accept-encoding header " + acceptEncoding);
		HttpServletRequest request = mock(HttpServletRequest.class);
		when(request.getHeader("Accept-Encoding")).thenReturn(acceptEncoding);
		assertEquals(";q=1.5", IuTomcatUtil.getRequestedContentEncoding(request));
	}

	@Test
	void send_withGzipEncoding_sendsCompressedContent() throws IOException {
		IuTestLogger.expect("edu.iu.web.tomcat.IuTomcatUtil", Level.FINE, "Accept-Encoding:gzip");
		HttpServletRequest request = mock(HttpServletRequest.class);
		HttpServletResponse response = mock(HttpServletResponse.class);
		when(request.getHeader("Accept-Encoding")).thenReturn("gzip");
		ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
		when(response.getOutputStream()).thenReturn(new ServletOutputStream() {
			@Override
			public void write(int b) throws IOException {
				outputStream.write(b);
			}

			@Override
			public boolean isReady() {
				// TODO Auto-generated method stub
				return false;
			}

			@Override
			public void setWriteListener(WriteListener listener) {
				// TODO Auto-generated method stub

			}
		});
		IuTomcatUtil.send(request, response, () -> new ByteArrayInputStream("test content".getBytes()), "text/plain");
		assertTrue(outputStream.size() > 0);
	}

	@Test
	void send_withDeflateEncoding_sendsCompressedContent() throws IOException {
		IuTestLogger.expect("edu.iu.web.tomcat.IuTomcatUtil", Level.FINE, "Accept-Encoding:deflate");
		HttpServletRequest request = mock(HttpServletRequest.class);
		HttpServletResponse response = mock(HttpServletResponse.class);
		when(request.getHeader("Accept-Encoding")).thenReturn("deflate");
		ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
		when(response.getOutputStream()).thenReturn(new ServletOutputStream() {
			@Override
			public void write(int b) throws IOException {
				outputStream.write(b);
			}

			@Override
			public boolean isReady() {
				// TODO Auto-generated method stub
				return false;
			}

			@Override
			public void setWriteListener(WriteListener listener) {
				// TODO Auto-generated method stub

			}
		});
		IuTomcatUtil.send(request, response, () -> new ByteArrayInputStream("test content".getBytes()), "text/plain");
		assertTrue(outputStream.size() > 0);
	}

	@Test
	void send_withNoEncoding_sendsUnCompressedContent() throws IOException {
		HttpServletRequest request = mock(HttpServletRequest.class);
		HttpServletResponse response = mock(HttpServletResponse.class);
		ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
		when(response.getOutputStream()).thenReturn(new ServletOutputStream() {
			@Override
			public void write(int b) throws IOException {
				outputStream.write(b);
			}

			@Override
			public boolean isReady() {
				// TODO Auto-generated method stub
				return false;
			}

			@Override
			public void setWriteListener(WriteListener listener) {
				// TODO Auto-generated method stub

			}
		});
		IuTomcatUtil.send(request, response, () -> new ByteArrayInputStream("test content".getBytes()), "text/plain");
		assertTrue(outputStream.size() > 0);
	}

	@Test
	void defaultConstructor_createsInstance() {
		IuTomcatUtil iuTomcatUtil = new IuTomcatUtil();
		assertNotNull(iuTomcatUtil);
	}
}
