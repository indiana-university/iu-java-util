
package edu.iu.web.tomcat;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;

import org.apache.catalina.Host;
import org.apache.catalina.Pipeline;
import org.apache.catalina.Valve;
import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import jakarta.servlet.ServletException;

public class IuTomcatEngineValveTest {

	private IuTomcatEngineValve engineValve;
	private Host host;
	private Pipeline pipeline;
	private Valve nextValve;
	private Request request;
	private Response response;

	@BeforeEach
	void setUp() {
		host = mock(Host.class);
		pipeline = mock(Pipeline.class);
		nextValve = mock(Valve.class);
		request = mock(Request.class);
		response = mock(Response.class);

		when(host.getPipeline()).thenReturn(pipeline);
		when(pipeline.getFirst()).thenReturn(nextValve);

		engineValve = new IuTomcatEngineValve(host);
	}

	@Test
	void invoke_callsNextValveInPipeline() throws IOException, ServletException {
		engineValve.invoke(request, response);
		verify(nextValve).invoke(request, response);
	}

	@Test
	void invoke_withIOException_propagatesException() throws IOException, ServletException {
		doThrow(IOException.class).when(nextValve).invoke(request, response);
		assertThrows(IOException.class, () -> engineValve.invoke(request, response));
	}

	@Test
	void invoke_withServletException_propagatesException() throws IOException, ServletException {
		doThrow(ServletException.class).when(nextValve).invoke(request, response);
		assertThrows(ServletException.class, () -> engineValve.invoke(request, response));
	}

}
