package edu.iu.client;

import java.io.InputStream;
import java.net.http.HttpResponse;

import edu.iu.UnsafeConsumer;

/**
 * Validates HTTP response status code and headers.
 */
@FunctionalInterface
public interface HttpResponseValidator extends UnsafeConsumer<HttpResponse<InputStream>> {

	@Override
	void accept(HttpResponse<InputStream> argument) throws HttpException;

}
