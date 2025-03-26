package edu.iu.web.tomcat;

import java.io.File;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.regex.Pattern;

import org.apache.catalina.Container;
import org.apache.catalina.Context;
import org.apache.catalina.Host;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.LifecycleState;

public class IuTomcatHost extends AbstractIuTomcatContainer implements Host {

	private Context context;
	private File appBase;

	public IuTomcatHost(Context context, File appBase) {
		context.setParent(this);
		this.context = context;
		pipeline.setBasic(new IuTomcatHostValve(context));
		this.appBase = appBase;
		setName("localhost");
		// host.setErrorReportValveClass(WebValve.class.getName());
	}

	@Override
	public Container findChild(String name) {
		if (Objects.equals(name, context.getName()))
			return context;
		else
			return null;
	}

	@Override
	public Container[] findChildren() {
		return new Container[] { context };
	}

	@Override
	public String getXmlBase() {
		return null;
	}

	@Override
	public void setXmlBase(String xmlBase) {
		throw new UnsupportedOperationException();
	}

	@Override
	public File getConfigBaseFile() {
		return null;
	}

	@Override
	public String getAppBase() {
		return appBase.toString();
	}

	@Override
	public File getAppBaseFile() {
		return appBase;
	}

	@Override
	public void setAppBase(String appBase) {
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean getAutoDeploy() {
		return false;
	}

	@Override
	public void setAutoDeploy(boolean autoDeploy) {
		throw new UnsupportedOperationException();
	}

	@Override
	public String getConfigClass() {
		return null;
	}

	@Override
	public void setConfigClass(String configClass) {
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean getDeployOnStartup() {
		return false;
	}

	@Override
	public void setDeployOnStartup(boolean deployOnStartup) {
		throw new UnsupportedOperationException();
	}

	@Override
	public String getDeployIgnore() {
		return null;
	}

	@Override
	public Pattern getDeployIgnorePattern() {
		return null;
	}

	@Override
	public void setDeployIgnore(String deployIgnore) {
		throw new UnsupportedOperationException();
	}

	@Override
	public ExecutorService getStartStopExecutor() {
		return null;
	}

	@Override
	public boolean getCreateDirs() {
		return false;
	}

	@Override
	public void setCreateDirs(boolean createDirs) {
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean getUndeployOldVersions() {
		return false;
	}

	@Override
	public void setUndeployOldVersions(boolean undeployOldVersions) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void addAlias(String alias) {
		throw new UnsupportedOperationException();
	}

	@Override
	public String[] findAliases() {
		return new String[0];
	}

	@Override
	public void removeAlias(String alias) {
		throw new UnsupportedOperationException();
	}

	@Override
	protected void initInternal() throws LifecycleException {
	}

	@Override
	protected void startInternal() throws LifecycleException {
		context.start();
		if (!context.getState().equals(LifecycleState.STARTED))
			throw new IllegalStateException("Context " + context + " " + context.getStateName());
		setState(LifecycleState.STARTING);
	}

	@Override
	protected void stopInternal() throws LifecycleException {
		setState(LifecycleState.STOPPING);
		context.stop();
	}

	@Override
	protected void destroyInternal() throws LifecycleException {
	}

	@Override
	public String toString() {
		return "IuTomcatHost[" + getName() + ']';
	}

	@Override
	public String getLegacyAppBase() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public File getLegacyAppBaseFile() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void setLegacyAppBase(String legacyAppBase) {
		// TODO Auto-generated method stub

	}

}
