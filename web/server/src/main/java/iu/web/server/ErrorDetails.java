package iu.web.server;

import edu.iu.IuWebUtils;
import edu.iu.client.IuJson;
import edu.iu.web.IuWebContext;

/**
 * Captures error details for reporting back to the client as a JSON object.
 */
class ErrorDetails {

	private final String nodeId;
	private final String requestNumber;
	private final IuWebContext webContext;
	private final int status;
	private final boolean severe;

	/**
	 * Constructor.
	 * 
	 * @param nodeId
	 * @param requestNumber
	 * @param webContext
	 * @param status
	 */
	ErrorDetails(String nodeId, String requestNumber, IuWebContext webContext, int status) {
		this.nodeId = nodeId;
		this.requestNumber = requestNumber;
		this.webContext = webContext;
		this.status = status;
		severe = status == 400 || (status != 503 && status >= 500);
	}

	@Override
	public String toString() {
		final var b = IuJson.object();
		IuJson.add(b, "nodeId", nodeId);
		IuJson.add(b, "requestNumber", requestNumber);
		if (status != 0) {
			b.add("status", status);
			b.add("message", IuWebUtils.describeStatus(status));
			b.add("severe", severe);
		}

		if (webContext != null) {
			IuJson.add(b, "application", webContext.getApplication());
			IuJson.add(b, "environment", webContext.getEnvironment());
			IuJson.add(b, "module", webContext.getModule());
			IuJson.add(b, "runtime", webContext.getRuntime());
			IuJson.add(b, "component", webContext.getComponent());
			IuJson.add(b, "supportPreText", webContext.getSupportPreText());
			IuJson.add(b, "supportUrl", webContext.getSupportUrl());
			IuJson.add(b, "supportLabel", webContext.getSupportLabel());
		}

		return b.build().toString();
	}

}
