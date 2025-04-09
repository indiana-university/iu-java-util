package edu.iu.web.tomcat;

import java.io.IOException;

import edu.iu.web.WebUpgradeConnection;
import edu.iu.web.WebUpgradeReadListener;
import edu.iu.web.WebUpgradeWriteListener;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.WebConnection;

/**
 * An implementation of the Jakarta WebConnection interface.
 */
public class IuTomcatWebConnection implements WebConnection, AutoCloseable {

	private final WebUpgradeConnection connection;

	/**
	 * Creates a new instance of the IuTomcatWebConnection class.
	 * 
	 * @param connection The WebUpgradeConnection to wrap.
	 */
	public IuTomcatWebConnection(WebUpgradeConnection connection) {
		this.connection = connection;
	}

	@Override
	public void close() throws Exception {
		connection.close();
	}

	@Override
	public ServletInputStream getInputStream() throws IOException {
		return new ServletInputStream() {

			@Override
			public int read() throws IOException {
				return connection.read();
			}

			@Override
			public void setReadListener(ReadListener listener) {
				connection.setReadListener(new WebUpgradeReadListener() {
					@Override
					public void onError(Throwable t) {
						listener.onError(t);
					}

					@Override
					public void onDataAvailable() throws IOException {
						listener.onDataAvailable();
					}

					@Override
					public void onAllDataRead() throws IOException {
						listener.onAllDataRead();
					}
				});
			}

			@Override
			public boolean isReady() {
				return connection.isReadReady();
			}

			@Override
			public boolean isFinished() {
				return connection.isFinished();
			}
		};
	}

	@Override
	public ServletOutputStream getOutputStream() throws IOException {
		return new ServletOutputStream() {
			@Override
			public void write(int b) throws IOException {
				connection.write(b);
			}

			@Override
			public void setWriteListener(WriteListener listener) {
				connection.setWriteListener(new WebUpgradeWriteListener() {
					@Override
					public void onWritePossible() throws IOException {
						listener.onWritePossible();
					}

					@Override
					public void onError(Throwable t) {
						listener.onError(t);
					}
				});
			}

			@Override
			public boolean isReady() {
				return connection.isWriteReady();
			}
		};
	}

}
