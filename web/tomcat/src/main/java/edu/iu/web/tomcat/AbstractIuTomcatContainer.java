package edu.iu.web.tomcat;

import java.beans.PropertyChangeListener;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

import javax.management.ObjectName;

import org.apache.catalina.AccessLog;
import org.apache.catalina.Cluster;
import org.apache.catalina.Container;
import org.apache.catalina.ContainerListener;
import org.apache.catalina.Pipeline;
import org.apache.catalina.Realm;
import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.apache.catalina.core.StandardPipeline;
import org.apache.catalina.realm.NullRealm;
import org.apache.catalina.util.LifecycleBase;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;

import edu.iu.IuException;

public abstract class AbstractIuTomcatContainer extends LifecycleBase implements Container {

	private static final ContainerListener[] CL0 = new ContainerListener[0];

	private final Log log = LogFactory.getLog(getClass());

	protected Pipeline pipeline = new StandardPipeline(this);

	private String name;
	private Container parent;
	private Realm realm = new NullRealm();
	private List<ContainerListener> containerListeners = new ArrayList<>();

	@Override
	public Log getLogger() {
		return log;
	}

	@Override
	public String getLogName() {
		return getClass().getName();
	}

	@Override
	public ObjectName getObjectName() {
		return null;
	}

	@Override
	public String getDomain() {
		return null;
	}

	@Override
	public String getMBeanKeyProperties() {
		return null;
	}

	@Override
	public Pipeline getPipeline() {
		return pipeline;
	}

	@Override
	public Cluster getCluster() {
		return null;
	}

	@Override
	public void setCluster(Cluster cluster) {
		throw new UnsupportedOperationException();
	}

	@Override
	public int getBackgroundProcessorDelay() {
		return 0;
	}

	@Override
	public void setBackgroundProcessorDelay(int delay) {
		throw new UnsupportedOperationException();
	}

	@Override
	public String getName() {
		return name;
	}

	@Override
	public void setName(String name) {
		this.name = name;
	}

	@Override
	public Container getParent() {
		return parent;
	}

	@Override
	public void setParent(Container container) {
		this.parent = container;
	}

	@Override
	public ClassLoader getParentClassLoader() {
		return getClass().getClassLoader();
	}

	@Override
	public void setParentClassLoader(ClassLoader parent) {
		throw new UnsupportedOperationException();
	}

	@Override
	public Realm getRealm() {
		return realm;
	}

	@Override
	public void setRealm(Realm realm) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void backgroundProcess() {
		throw new UnsupportedOperationException();
	}

	@Override
	public void addChild(Container child) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void addContainerListener(ContainerListener listener) {
		synchronized (containerListeners) {
			containerListeners.add(listener);
		}
	}

	@Override
	public void addPropertyChangeListener(PropertyChangeListener listener) {
		throw new UnsupportedOperationException();
	}

	@Override
	public ContainerListener[] findContainerListeners() {
		synchronized (containerListeners) {
			return containerListeners.toArray(CL0);
		}
	}

	@Override
	public void removeChild(Container child) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void removeContainerListener(ContainerListener listener) {
		synchronized (containerListeners) {
			containerListeners.remove(listener);
		}
	}

	@Override
	public void removePropertyChangeListener(PropertyChangeListener listener) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void fireContainerEvent(String type, Object data) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void logAccess(Request request, Response response, long time, boolean useDefault) {
		throw new UnsupportedOperationException();
	}

	@Override
	public AccessLog getAccessLog() {
		return null;
	}

	@Override
	public int getStartStopThreads() {
		return 0;
	}

	@Override
	public void setStartStopThreads(int startStopThreads) {
		throw new UnsupportedOperationException();
	}

	@Override
	public File getCatalinaBase() {
		return IuException.unchecked(() -> File.createTempFile("test", ""));
//		return null;
		// TODO: implement
//		return IU.SPI.getTempDir();
	}

	@Override
	public File getCatalinaHome() {
		return null;
	}

}
