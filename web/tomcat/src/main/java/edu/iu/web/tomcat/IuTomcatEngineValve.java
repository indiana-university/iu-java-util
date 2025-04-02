package edu.iu.web.tomcat;

import java.io.IOException;

import org.apache.catalina.Host;
import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.apache.catalina.valves.ValveBase;

import jakarta.servlet.ServletException;

class IuTomcatEngineValve extends ValveBase {

	private Host host;

	IuTomcatEngineValve(Host host) {
		this.host = host;
	}

	@Override
	public final void invoke(Request request, Response response) throws IOException, ServletException {
		host.getPipeline().getFirst().invoke(request, response);
	}
}
