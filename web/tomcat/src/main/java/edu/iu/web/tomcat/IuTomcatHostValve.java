package edu.iu.web.tomcat;

import java.io.IOException;

import org.apache.catalina.Context;
import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.apache.catalina.valves.ValveBase;

import jakarta.servlet.ServletException;

/**
 * Valve that handles Tomcat requests and responses.
 */
public class IuTomcatHostValve extends ValveBase {

	private Context context;

	/**
	 * Constructs an instance of the Tomcat Host Valve.
	 * 
	 * @param context the context
	 */
	public IuTomcatHostValve(Context context) {
		super(true);
		this.context = context;
	}

	@Override
	public final void invoke(Request request, Response response) throws IOException, ServletException {
		try {
			if (request.isAsync()) {
				if (request.isAsyncDispatching())
					context.getPipeline().getFirst().invoke(request, response);
				else if (!response.isErrorReportRequired())
					throw new IllegalStateException();
			} else {
				if (!context.fireRequestInitEvent(request.getRequest()))
					return;

				try {
					context.getPipeline().getFirst().invoke(request, response);
				} finally {
					if (!request.isAsync())
						context.fireRequestDestroyEvent(request.getRequest());
				}
			}
		} finally {
			response.setSuspended(false);
		}
	}

}
