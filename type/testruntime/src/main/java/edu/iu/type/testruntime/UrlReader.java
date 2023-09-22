package edu.iu.type.testruntime;

import java.io.IOException;
import java.io.StringReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse.BodyHandlers;

import jakarta.json.Json;
import jakarta.json.JsonValue;

@SuppressWarnings("javadoc")
public class UrlReader {

	public JsonValue parseJson(String json) {
		return Json.createReader(new StringReader(json)).readValue();
	}

	public JsonValue get(String url) {
		try {
			return Json
					.createReader(HttpClient.newHttpClient()
							.send(HttpRequest.newBuilder(new URI(url)).build(), BodyHandlers.ofInputStream()).body())
					.readValue();
		} catch (IOException | InterruptedException | URISyntaxException e) {
			throw new IllegalStateException(e);
		}
	}

}
