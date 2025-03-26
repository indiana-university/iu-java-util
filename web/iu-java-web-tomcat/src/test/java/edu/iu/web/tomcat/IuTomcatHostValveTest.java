
package edu.iu.web.tomcat;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;

import org.apache.catalina.Context;
import org.apache.catalina.Pipeline;
import org.apache.catalina.Valve;
import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import jakarta.servlet.ServletException;

public class IuTomcatHostValveTest {

	private IuTomcatHostValve hostValve;
	private Context context;
	private Pipeline pipeline;
	private Valve nextValve;
	private Request request;
	private Response response;

	@BeforeEach
	void setUp() {
		context = mock(Context.class);
		pipeline = mock(Pipeline.class);
		nextValve = mock(Valve.class);
		request = mock(Request.class);
		response = mock(Response.class);

		when(context.getPipeline()).thenReturn(pipeline);
		when(pipeline.getFirst()).thenReturn(nextValve);

		hostValve = new IuTomcatHostValve(context);
	}

	@Test
	void invoke_setsResponseSuspendedToFalse() throws IOException, ServletException {
		hostValve.invoke(request, response);
		verify(response).setSuspended(false);
	}

	@Test
	void invoke_withAsyncRequest_dispatchesToNextValve() throws IOException, ServletException {
		when(request.isAsync()).thenReturn(true);
		when(request.isAsyncDispatching()).thenReturn(true);

		hostValve.invoke(request, response);
		verify(nextValve).invoke(request, response);
	}

	@Test
	void invoke_withAsyncRequestAndErrorResponse_throwsIllegalStateException() throws IOException, ServletException {
		when(request.isAsync()).thenReturn(true);
		when(request.isAsyncDispatching()).thenReturn(false);
		when(response.isErrorReportRequired()).thenReturn(false);

		assertThrows(IllegalStateException.class, () -> hostValve.invoke(request, response));
	}

	@Test
	void invoke_withNonAsyncRequest_firesRequestInitEvent_and_firesRequestDestroyEvent()
			throws IOException, ServletException {
		when(request.isAsync()).thenReturn(false);
		when(context.fireRequestInitEvent(request.getRequest())).thenReturn(true);

		hostValve.invoke(request, response);
		verify(nextValve).invoke(request, response);
		verify(context).fireRequestDestroyEvent(request.getRequest());
	}

	@Test
	void invoke_withNonAsyncRequestModifiedToAsync_firesRequestInitEvent_andNot_firesRequestDestroyEvent()
			throws IOException, ServletException {
		when(request.isAsync()).thenReturn(false).thenReturn(true);
		when(context.fireRequestInitEvent(request.getRequest())).thenReturn(true);
		
		hostValve.invoke(request, response);
		verify(nextValve).invoke(request, response);
		verify(context, never()).fireRequestDestroyEvent(request.getRequest());
	}

//	@Test
//	void invoke_withNonAsyncRequest_firesRequestDestroyEvent() throws IOException, ServletException {
//		when(request.isAsync()).thenReturn(false);
//		when(context.fireRequestInitEvent(request.getRequest())).thenReturn(true);
//
//		hostValve.invoke(request, response);
//		verify(context).fireRequestDestroyEvent(request.getRequest());
//	}
//
//	@Test
//	void invoke_setsResponseSuspendedToFalse() throws IOException, ServletException {
//		hostValve.invoke(request, response);
//		verify(response).setSuspended(false);
//	}
}
