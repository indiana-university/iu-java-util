package edu.iu.web.tomcat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URL;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;

import org.apache.coyote.Adapter;
import org.apache.coyote.InputBuffer;
import org.apache.coyote.OutputBuffer;
import org.apache.coyote.Request;
import org.apache.coyote.Response;
import org.apache.tomcat.util.http.MimeHeaders;
import org.apache.tomcat.util.net.ApplicationBufferHandler;

import edu.iu.IuWebUtils;
import edu.iu.web.WebInvoker;
import edu.iu.web.WebRequest;
import edu.iu.web.WebResponse;
import edu.iu.web.WebUpgradeHandler;

/**
 * Implementation of the Tomcat Web Invoker.
 */
public class IuTomcatInvoker implements WebInvoker {

	private static final ThreadLocal<Throwable> ERROR = new ThreadLocal<>();
	private static final ThreadLocal<WebUpgradeHandler> UPGRADE = new ThreadLocal<>();

	// TODO: Borrowed from 6.1 StringUtil.
	/**
	 * Determine if a string is either null or an empty string.
	 * 
	 * @param s The string.
	 * @return True if string is empty (null or zero length when trimmed). False
	 *         otherwise
	 */
	public static boolean isEmpty(String s) {
		if (s == null || s.trim().length() == 0) {
			return true;
		} else {
			return false;
		}
	}

	/**
	 * Determine if a string is neither null nor the empty string.
	 * 
	 * @param s The string.
	 * @return True if the string is neither null nor the empty string.
	 */
	public static boolean hasValue(String s) {
		return !isEmpty(s);
	}

	// End borrowed code.
	
	/**
	 * Reports an error that occurred during the request processing.
	 * 
	 * @param error the error that occurred
	 */
	static void reportError(Throwable error) {
		ERROR.set(error);
	}

	/**
	 * Reports an upgrade handler for the request.
	 * @param handler the upgrade handler
	 */
	static void reportUpgrade(WebUpgradeHandler handler) {
		UPGRADE.set(handler);
	}

	private final ClassLoader classLoader;
	private final Adapter adapter;

	/**
	 * Constructs an instance of the Tomcat Invoker.
	 * 
	 * @param classLoader the class loader
	 * @param adapter the adapter
	 */
	public IuTomcatInvoker(ClassLoader classLoader, Adapter adapter) {
		this.classLoader = classLoader;
		this.adapter = adapter;
	}

	@Override
	public ClassLoader getClassLoader() {
		return classLoader;
	}

	@SuppressWarnings("deprecation")
	@Override
	public WebResponse invoke(WebRequest request) throws Exception {
		URL calledUrl = request.getCalledUrl();
		String serverName = calledUrl.getHost();
		String serverAddr = IuWebUtils.getInetAddress(calledUrl.getHost()).getHostAddress();
		int serverPort = calledUrl.getPort();

		Request req = new Request();
		req.protocol().setString("HTTP/1.1");
		req.scheme().setString(calledUrl.toURI().getScheme());
		req.localName().setString(serverName);
		req.localAddr().setString(serverAddr);
		req.setLocalPort(serverPort);
		req.serverName().setString(serverName);
		req.setServerPort(serverPort);
		req.remoteHost().setString(request.getCallerHostName());
		req.remoteAddr().setString(request.getCallerIpAddress());
		req.setRemotePort(request.getCallerPort());
		req.method().setString(request.getMethod());
		req.decodedURI().setString(calledUrl.getPath());
		req.requestURI().setString(calledUrl.getPath());
		req.queryString().setString(request.getCalledUrl().getQuery());

		// TODO: Implement this
//		if (IuResources.SPI.isActive())
//			IU.SPI.setAttribute(IuSessionLoaderProxy.WEB_REQUEST_KEY, request);

		MimeHeaders requestHeaders = req.getMimeHeaders();
		request.getHeaders().forEach((name, values) -> {
			if (!name.equalsIgnoreCase("Content-Type") && !name.equalsIgnoreCase("Content-Length"))
				values.forEach(value -> requestHeaders.addValue(name).setString(value));
		});
		if (request.getContentType() != null)
			requestHeaders.addValue("Content-Type").setString(request.getContentType());

		byte[] b = request.getContent();
		if (b != null) {
			requestHeaders.addValue("Content-Length").setString(Integer.toString(b.length));
			req.setInputBuffer(new InputBuffer() {
				private int offset;

				@Override
				public int doRead(ApplicationBufferHandler handler) throws IOException {
					if (offset >= b.length)
						return -1;

					Buffer buffer = handler.getByteBuffer();
					buffer.rewind();

					int remaining = b.length - offset;
					int length = Math.min(remaining, buffer.capacity());

					buffer.limit(length);
					((ByteBuffer) buffer).put(b, offset, length);
					buffer.rewind();
					offset += length;

					return length;
				}

				@Override
				public int available() {
					return Math.max(0, b.length - offset);
				}
			});
		} else
			req.setContentLength(0L);

		Response resp = new Response();
		resp.setCharacterEncoding("UTF-8");

		WebResponse webResponse = new WebResponse() {

			private final ByteArrayOutputStream outputBuffer = new ByteArrayOutputStream();
			private final Map<String, Iterable<String>> headers = new LinkedHashMap<>();
			private int status;
			private String message;
			private WebUpgradeHandler upgradeHandler;

			public void setStatus(int status) {
				this.status = status;
			}

			public void setMessage(String message) {
				this.message = message;
			}

			public void setUpgradeHandler(WebUpgradeHandler upgradeHandler) {
				this.upgradeHandler = upgradeHandler;
			}

			public void addHeader(String name, String value) {
				name = IuWebUtils.normalizeHeaderName(name);
				Queue<String> values = (Queue<String>) headers.get(name);
				if (values == null)
					headers.put(name, values = new LinkedList<>());
				values.offer(value);
			}

			public OutputStream getOutputStream() {
				return outputBuffer;
			}

			@Override
			public int getStatus() {
				return status;
			}

			@Override
			public String getMessage() {
				return message;
			}

			@Override
			public Map<String, Iterable<String>> getHeaders() {
				return headers;
			}

			@Override
			public byte[] getContent() {
				return outputBuffer.toByteArray();
			}

			@Override
			public WebUpgradeHandler getUpgradeHandler() {
				return upgradeHandler;
			}
		};
		resp.setOutputBuffer(new OutputBuffer() {

			@Override
			public int doWrite(ByteBuffer chunk) throws IOException {
				int i = 0;
				while (chunk.hasRemaining()) {
					webResponse.getOutputStream().write(chunk.get());
					i++;
				}
				return i;
			}

			@Override
			public long getBytesWritten() {
				return webResponse.getContent().length;
			}
		});
		req.setResponse(resp);

		resp.setCharacterEncoding("UTF-8");

		WebUpgradeHandler upgradeHandler;
		Throwable error;
		assert ERROR.get() == null : ERROR.get();
		assert UPGRADE.get() == null : UPGRADE.get();
		try {
			adapter.service(req, resp);
			error = ERROR.get();
			upgradeHandler = UPGRADE.get();
		} finally {
			ERROR.remove();
			UPGRADE.remove();
		}

		webResponse.setStatus(resp.getStatus());
		webResponse.setMessage(resp.getMessage());
		String contentType = resp.getContentType();
		if (hasValue(contentType))
			webResponse.addHeader("Content-Type", contentType);
		MimeHeaders responseHeaders = resp.getMimeHeaders();
		Enumeration<String> headerNames = responseHeaders.names();
		while (headerNames.hasMoreElements()) {
			String name = headerNames.nextElement();
			Enumeration<String> values = responseHeaders.values(name);
			while (values.hasMoreElements())
				webResponse.addHeader(name, values.nextElement());
		}

		if (upgradeHandler != null)
			webResponse.setUpgradeHandler(upgradeHandler);

		if (error instanceof Exception)
			throw (Exception) error;
		if (error instanceof Error)
			throw (Error) error;
		if (error != null)
			throw new IllegalStateException(error);

		return webResponse;
	}

}
