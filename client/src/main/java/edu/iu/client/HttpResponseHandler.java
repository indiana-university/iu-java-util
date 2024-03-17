package edu.iu.client;

import java.io.InputStream;
import java.net.http.HttpResponse;

import edu.iu.UnsafeFunction;

/**
 * Functional interface for converting a validated HTTP response.
 * 
 * @param <T> response type
 */
@FunctionalInterface
public interface HttpResponseHandler<T> extends UnsafeFunction<HttpResponse<InputStream>, T> {

	@Override
	T apply(HttpResponse<InputStream> argument) throws HttpException;

}
