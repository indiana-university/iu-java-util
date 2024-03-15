package edu.iu.client;

import java.io.InputStream;
import java.net.http.HttpResponse;

/**
 * Thrown by {@link IuHttp} when an error response is received from an HTTP
 * request.
 */
public class HttpException extends Exception {
	private static final long serialVersionUID = 1L;

	private final transient HttpResponse<InputStream> response;

	/**
	 * Constructor.
	 * 
	 * @param response error response, status code >= 400
	 * @param message detailed error message
	 */
	HttpException(HttpResponse<InputStream> response, String message) {
		super(message);
		this.response = response;
	}

	/**
	 * Gets the HTTP response that failed.
	 * 
	 * @return {@link HttpResponse}
	 */
	public HttpResponse<InputStream> getResponse() {
		return response;
	}

}
