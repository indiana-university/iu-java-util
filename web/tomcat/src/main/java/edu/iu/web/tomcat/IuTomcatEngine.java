package edu.iu.web.tomcat;

import java.io.File;
import java.util.Objects;

import org.apache.catalina.Container;
import org.apache.catalina.Engine;
import org.apache.catalina.Host;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.LifecycleState;
import org.apache.catalina.Loader;
import org.apache.catalina.Service;
import org.apache.catalina.WebResourceRoot;
import org.apache.catalina.connector.Connector;
import org.apache.catalina.core.StandardContext;
import org.apache.catalina.core.StandardService;
import org.apache.catalina.startup.Catalina;
import org.apache.catalina.startup.ContextConfig;
import org.apache.catalina.startup.Tomcat;
import org.apache.coyote.Adapter;
import org.apache.tomcat.util.scan.StandardJarScanner;

import edu.iu.type.IuComponent;

public class IuTomcatEngine extends AbstractIuTomcatContainer implements Engine {

	private Host host;
	private Service service;
	private Connector connector;

	public IuTomcatEngine(IuComponent.Kind componentKind, String contextName, File catalinaBase,
			WebResourceRoot webResourceRoot, Loader webLoader) throws Throwable {
		StandardContext context = new IuTomcatContext();
		context.setName(contextName);
		context.setLoader(webLoader);
		context.setOverride(true);
		context.setUnpackWAR(false);
		context.setIgnoreAnnotations(true);
		context.setResources(webResourceRoot);
		context.setInstanceManager(new IuTomcatInstanceManager(webLoader.getClassLoader()));
		context.getPipeline().setBasic(new IuTomcatValve());

		// TODO: what's the new way to do this?
//		context.addLifecycleListener(
//				IU.SPI.doLookup("java:comp/env/" + componentKind.name().toLowerCase() + "ContextListener"));
		context.addLifecycleListener(new IuTomcatContextListener());

		Tomcat tomcat = new Tomcat();
//		tomcat.getServer().setCatalina(new Catalina());
		context.addLifecycleListener(tomcat.getDefaultWebXmlListener());
		context.setConfigFile(webResourceRoot.getResource("/WEB-INF/web.xml").getURL());

		ContextConfig config = new IuTomcatContextConfig();
		context.addLifecycleListener(config);

		// TODO: Get these the new way.
//		StringBuilder sessionCookieName = new StringBuilder(IU.SPI.getApplication());
//		String component = IU.SPI.getComponent();
		StringBuilder sessionCookieName = new StringBuilder("testApp");
		String component = "testComponent";
		if (component != null)
			sessionCookieName.append('-').append(component);
		sessionCookieName.append("-sid");
		context.setSessionCookieName(sessionCookieName.toString());

		StandardJarScanner jarScanner = new StandardJarScanner();
		jarScanner.setScanClassPath(false);
		context.setJarScanner(jarScanner);
		config.setDefaultWebXml(tomcat.noDefaultWebXmlPath());

		host = new IuTomcatHost(context, catalinaBase);
		host.setParent(this);

		setName(contextName + "-engine");

		service = new StandardService();
		service.setName(contextName + "-service");
		service.setContainer(this);
		service.setServer(tomcat.getServer());

		connector = new Connector(IuTomcatProtocolHandler.class.getName());
		connector.setPort(0);
		service.addConnector(connector);

		pipeline.setBasic(new IuTomcatEngineValve(host));
	}

	@Override
	protected void initInternal() throws LifecycleException {
	}

	@Override
	protected void startInternal() throws LifecycleException {
		host.start();
		setState(LifecycleState.STARTING);
	}

	@Override
	protected void stopInternal() throws LifecycleException {
		setState(LifecycleState.STOPPING);
		host.stop();
	}

	@Override
	protected void destroyInternal() throws LifecycleException {
	}

	public Adapter getAdapter() {
		return connector.getProtocolHandler().getAdapter();
	}

	@Override
	public String getDefaultHost() {
		return "localhost";
	}

	@Override
	public void setDefaultHost(String defaultHost) {
		throw new UnsupportedOperationException();
	}

	@Override
	public String getJvmRoute() {
		return null;
	}

	@Override
	public void setJvmRoute(String jvmRouteId) {
		throw new UnsupportedOperationException();
	}

	@Override
	public Service getService() {
		return service;
	}

	@Override
	public void setService(Service service) {
		if (service != this.service)
			throw new UnsupportedOperationException();
	}

	@Override
	public Container findChild(String name) {
		if (Objects.equals(name, host.getName()))
			return host;
		else
			return null;
	}

	@Override
	public Container[] findChildren() {
		return new Container[] { host };
	}

	@Override
	public String toString() {
		return "IuTomcatEngine[" + getName() + ']';
	}

}
