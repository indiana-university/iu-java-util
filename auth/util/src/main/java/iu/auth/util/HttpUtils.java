package iu.auth.util;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.Map;
import java.util.logging.Logger;

import edu.iu.IuException;
import edu.iu.IuStream;
import jakarta.json.Json;
import jakarta.json.JsonValue;

/**
 * Provides minimal utilities for internal HTTP-based interactions specific to
 * authorization server interactions.
 */
public class HttpUtils {

	private static final Logger LOG = Logger.getLogger(HttpUtils.class.getName());

	/**
	 * Reads a JSON value from a public URI.
	 * 
	 * @param uri {@link URI}
	 * @return {@link JsonValue}
	 */
	public static JsonValue read(URI uri) {
		return read(HttpRequest.newBuilder(uri).build());
	}

	/**
	 * Reads a JSON value from an HTTP request.
	 * 
	 * @param request {@link HttpRequest}
	 * @return {@link JsonValue}
	 */
	public static JsonValue read(HttpRequest request) {
		return IuException.unchecked(() -> Json
				.createReader(
						new StringReader(read(HttpClient.newHttpClient().send(request, BodyHandlers.ofInputStream()))))
				.readValue());
	}

	/**
	 * Creates an authentication challenge sending to a client via the
	 * <strong>WWW-Authenticate</strong> header.
	 * 
	 * @param scheme     authentication scheme to request
	 * @param attributes challenge attributes for informing the client of how to
	 *                   authenticate
	 * @return authentication challenge
	 */
	public static String createChallenge(String scheme, Map<String, String> attributes) {
		final var sb = new StringBuilder();
		sb.append(scheme);
		if (attributes != null && !attributes.isEmpty()) {
			sb.append(' ');
			var first = true;
			for (final var attributeEntry : attributes.entrySet()) {
				if (first)
					first = false;
				else
					sb.append(", ");
				sb.append(attributeEntry.getKey()).append("=\"")
						.append(attributeEntry.getValue().replace("\\", "\\\\").replace("\"", "\\\""));
			}
		}
		return sb.toString();
	}

	private static String read(HttpResponse<InputStream> response) {
		final var status = response.statusCode();
		final var headers = response.headers();

		String content = null;
		Throwable error = null;
		try (final var in = response.body(); //
				final var r = new InputStreamReader(in)) {
			content = IuStream.read(r);
		} catch (Throwable e) {
			error = e;
		}

		if (status != 200 || error != null)
			throw new IllegalStateException("Failed to read from " + response.uri() + "; status=" + status + " headers="
					+ headers.map() + " content=" + content, error);

		LOG.fine(() -> "Read from " + response.uri() + "; status=" + status + " headers=" + headers.map());

		return content;
	}

	private HttpUtils() {
	}

}
