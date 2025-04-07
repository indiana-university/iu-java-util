package edu.iu.web.tomcat;

import java.net.URI;

import org.apache.catalina.Loader;
import org.apache.catalina.core.StandardContext;
import org.apache.catalina.loader.WebappClassLoader;
import org.apache.catalina.loader.WebappLoader;

import com.sun.net.httpserver.HttpHandler;

import edu.iu.web.IuWebContext;

public class IuTomcatContext extends StandardContext implements IuWebContext {

	private final String application;
	private final String environment;
	private final String module;
	private final String runtime;
	private final String component;
	private final String supportPreText;
	private final String supportUrl;

	public IuTomcatContext() {
		super();
		this.application = null;
		this.environment = null;
		this.module = null;
		this.runtime = null;
		this.component = null;
		this.supportPreText = null;
		this.supportUrl = null;
	}

	public IuTomcatContext(String application, String environment, String module, String runtime, String component,
			String supportPreText, String supportUrl) {
		this.application = application;
		this.environment = environment;
		this.module = module;
		this.runtime = runtime;
		this.component = component;
		this.supportPreText = supportPreText;
		this.supportUrl = supportUrl;
	}

	@Override
	public String getApplication() {
		return application;
	}

	@Override
	public String getEnvironment() {
		return environment;
	}

	@Override
	public String getModule() {
		return module;
	}

	@Override
	public String getRuntime() {
		return runtime;
	}

	@Override
	public String getComponent() {
		return component;
	}

	@Override
	public String getSupportPreText() {
		return supportPreText;
	}

	@Override
	public String getSupportUrl() {
		return supportUrl;
	}

	@Override
	public String getSupportLabel() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public URI getRootUri() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Iterable<String> getOriginAllow() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public HttpHandler getHandler() {
		// TODO Auto-generated method stub
		return null;
	}

//	@Override
	public Loader getLoader() {
		if (super.getLoader() != null) {
			return super.getLoader();
		}
		WebappLoader loader = new WebappLoader();
		loader.setContext(this);
		return loader;
	}

}
