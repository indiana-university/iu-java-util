package edu.iu.web.tomcat;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.catalina.Context;
import org.apache.catalina.LifecycleEvent;
import org.apache.catalina.LifecycleListener;
import org.apache.tomcat.util.descriptor.web.FilterDef;
import org.apache.tomcat.util.descriptor.web.FilterMap;

import jakarta.annotation.Resource;
import jakarta.annotation.Resources;

// TODO: is this supposed to be annotated like this if IuTomcatEngine sets the LifecycleListener 
// the same way it did in the previous version?
//@Resources({ @Resource(name = "webContextListener"), @Resource(name = "standaloneContextListener") })
@Resources({ @Resource(name = "modular_entryContextListener"), @Resource(name = "modular_jarContextListener"),
		@Resource(name = "modular_warContextListener") })
public class IuTomcatContextListener implements LifecycleListener {

	private static final Logger LOG = Logger.getLogger(IuTomcatContextListener.class.getName());

	@Override
	public void lifecycleEvent(LifecycleEvent event) {
		switch (event.getType()) {
		case "before_start":
			Context context = (Context) event.getLifecycle();

			URL endpointUrl;
			try {
				// TODO: Implement something equivalent to what IU.SPI does
//				endpointUrl = new URL(IU.SPI.getProperty("url/external", "http://localhost"));
				endpointUrl = URI.create("http://localhost").toURL();
			} catch (IllegalStateException | MalformedURLException e) {
				throw new IllegalStateException(e);
			}

			StringBuilder contextPath = new StringBuilder(endpointUrl.getPath());
			// TODO: Implement something equivalent to what IU.SPI does
//			contextPath.append(IU.SPI.getProperty("iu.tomcat.webContext", "")).append('/')
//					.append(IU.SPI.getApplication());
//			String component = IU.SPI.getComponent();
//			if (component != null)
//				contextPath.append('/').append(component);
			context.setPath(contextPath.toString());

			FilterDef requestFilterDef = new FilterDef();
			requestFilterDef.setFilterName("iu-request");
			requestFilterDef.setFilterClass(IuTomcatRequestFilter.class.getName());
			context.addFilterDef(requestFilterDef);

			FilterMap requestFilterMap = new FilterMap();
			requestFilterMap.setFilterName("iu-request");
			requestFilterMap.addURLPattern("/*");
			context.addFilterMapBefore(requestFilterMap);
			break;

		default:
			LOG.log("periodic".equals(event.getType()) ? Level.FINEST : Level.CONFIG, () -> {
				StringBuilder msg = new StringBuilder();
				msg.append(event.getClass().getSimpleName());
				msg.append(" ").append(event.getType());
				msg.append(" ").append(event.getLifecycle());
				return msg.toString();
			});
		}
	}

}
