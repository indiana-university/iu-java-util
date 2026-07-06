package iu.client;

import java.net.URI;
import java.time.Instant;

import edu.iu.IdGenerator;
import edu.iu.IuClassLoaderContext;
import edu.iu.IuObservableEvent;

/**
 * Observable HTTP client event.
 */
public class IuHttpClientEvent implements IuObservableEvent {

	private final String id;
	private final Instant startTime;
	private final URI uri;
	private final String context;
	private final Instant responseTime;
	private final int statusCode;

	/**
	 * Constructor.
	 * 
	 * @param uri outbound request URI
	 */
	public IuHttpClientEvent(URI uri) {
		this.id = IdGenerator.generateId();
		this.startTime = Instant.now();
		this.uri = uri;
		this.context = IuClassLoaderContext.getContext().getName();
		this.responseTime = null;
		this.statusCode = 0;
	}

	private IuHttpClientEvent(IuHttpClientEvent startEvent, int statusCode) {
		this.id = startEvent.id;
		this.startTime = startEvent.startTime;
		this.uri = startEvent.uri;
		this.context = startEvent.context;
		this.responseTime = Instant.now();
		this.statusCode = statusCode;
	}

	/**
	 * Updates the event to indicate a response was received
	 * 
	 * @param statusCode HTTP status code
	 * @return updated event
	 */
	public IuHttpClientEvent received(int statusCode) {
		return new IuHttpClientEvent(this, statusCode);
	}

	@Override
	public String getId() {
		return id;
	}

	@Override
	public Instant getTime() {
		return responseTime == null //
				? startTime
				: responseTime;
	}

	@Override
	public Instant getStartTime() {
		return startTime;
	}

	@Override
	public String getType() {
		return "http.client";
	}

	@Override
	public URI getUri() {
		return uri;
	}

	@Override
	public String getContext() {
		return context;
	}

	@Override
	public String getAction() {
		if (responseTime == null)
			return "send";
		else
			return statusCode < 400 ? "receive" : "error";
	}

}
