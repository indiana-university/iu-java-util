package iu.web.server;

import edu.iu.client.IuJson;

public class ErrorState {

	private int status;
	private String nodeId;
	private String requestNumber;
	private String supportPreText;
	private String supportUrl;
	private String supportLabel;

	public int getStatus() {
		return status;
	}

	public void setStatus(int status) {
		this.status = status;
	}

	public String getNodeId() {
		return nodeId;
	}

	public void setNodeId(String nodeId) {
		this.nodeId = nodeId;
	}

	public String getRequestNumber() {
		return requestNumber;
	}

	public void setRequestNumber(String requestNumber) {
		this.requestNumber = requestNumber;
	}

	public String getSupportPreText() {
		return supportPreText;
	}

	public void setSupportPreText(String supportPreText) {
		this.supportPreText = supportPreText;
	}

	public String getSupportUrl() {
		return supportUrl;
	}

	public void setSupportUrl(String supportUrl) {
		this.supportUrl = supportUrl;
	}

	public String getSupportLabel() {
		return supportLabel;
	}

	public void setSupportLabel(String supportLabel) {
		this.supportLabel = supportLabel;
	}

	public String getMessage() {
		switch (status) {
		case 400:
			return "Bad request";

		case 404:
			return "Not found";

		case 403:
			return "Access denied";
			
		case 503:
			return "Service unavailable";

		default:
			if (status >= 500)
				return "A system error has occurred, please try your request again later.";
			break;
		}

		return null;
	}

	public boolean isSevere() {
		return status == 400 || (status != 503 && status >= 500);
	}

	// TODO: convert to interface, use IuJsonAdapter.of()
	@Override
	public String toString() {
		final var jb = IuJson.object();
		IuJson.add(jb, "nodeId", nodeId);
		IuJson.add(jb, "requestNumber", requestNumber);
		if (status > 0)
			jb.add("status", status);
		IuJson.add(jb, "supportPreText", supportPreText);
		IuJson.add(jb, "supportUrl", supportUrl);
		IuJson.add(jb, "supportLabel", supportLabel);
		jb.add("severe", isSevere());
		IuJson.add(jb, "message", getMessage());
		return jb.build().toString();
	}

}
