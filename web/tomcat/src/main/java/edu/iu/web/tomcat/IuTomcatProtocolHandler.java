package edu.iu.web.tomcat;

import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;

import org.apache.coyote.Adapter;
import org.apache.coyote.ProtocolHandler;
import org.apache.coyote.UpgradeProtocol;
import org.apache.tomcat.util.net.SSLHostConfig;

public class IuTomcatProtocolHandler implements ProtocolHandler {

	private Adapter adapter;
	private ScheduledExecutorService utilityExector;

	public int getPort() {
		return 0;
	}

	@Override
	public void setAdapter(Adapter adapter) {
		this.adapter = adapter;
	}

	@Override
	public Adapter getAdapter() {
		return adapter;
	}

	@Override
	public Executor getExecutor() {
		return null;
	}

	@Override
	public void init() throws Exception {
		throw new UnsupportedOperationException();
	}

	@Override
	public void start() throws Exception {
		throw new UnsupportedOperationException();
	}

	@Override
	public void pause() throws Exception {
		throw new UnsupportedOperationException();
	}

	@Override
	public void resume() throws Exception {
		throw new UnsupportedOperationException();
	}

	@Override
	public void stop() throws Exception {
		throw new UnsupportedOperationException();
	}

	@Override
	public void destroy() throws Exception {
		throw new UnsupportedOperationException();
	}

//	@Override
//	public boolean isAprRequired() {
//		return false;
//	}

	@Override
	public boolean isSendfileSupported() {
		return false;
	}

	@Override
	public void addSslHostConfig(SSLHostConfig sslHostConfig) {
		throw new UnsupportedOperationException();
	}

	@Override
	public SSLHostConfig[] findSslHostConfigs() {
		return null;
	}

	@Override
	public void addUpgradeProtocol(UpgradeProtocol upgradeProtocol) {
		throw new UnsupportedOperationException();
	}

	@Override
	public UpgradeProtocol[] findUpgradeProtocols() {
		return null;
	}

	@Override
	public void closeServerSocketGraceful() {
		throw new UnsupportedOperationException();
	}

	@Override
	public void setExecutor(Executor executor) {
		throw new UnsupportedOperationException();
	}

	@Override
	public ScheduledExecutorService getUtilityExecutor() {
		return utilityExector;
	}

	@Override
	public void setUtilityExecutor(ScheduledExecutorService utilityExecutor) {
		this.utilityExector = utilityExecutor;
	}

	@Override
	public long awaitConnectionsClose(long waitMillis) {
		return 0;
	}

	@Override
	public void addSslHostConfig(SSLHostConfig sslHostConfig, boolean replace) {
		throw new UnsupportedOperationException();
	}

}
