package edu.iu.web.tomcat;

import java.util.logging.Logger;

import org.apache.coyote.http11.upgrade.InternalHttpUpgradeHandler;
import org.apache.tomcat.util.net.SocketEvent;

import edu.iu.UnsafeRunnable;
import edu.iu.web.WebUpgradeConnection;
import edu.iu.web.WebUpgradeHandler;
import edu.iu.web.WebUpgradeSSLSupport;
import edu.iu.web.WebUpgradeSocketWrapper;
import jakarta.servlet.http.HttpUpgradeHandler;

public class IuTomcatUpgradeHandler implements WebUpgradeHandler {

	private static final Logger LOG = Logger.getLogger(IuTomcatUpgradeHandler.class.getName());

	// TODO: remove or implement binding
//	private final AuthenticatedPrincipal authPrincipal;
	private final String requestPath;
	private final HttpUpgradeHandler upgradeHandler;
	private final ClassLoader loader;

//	IuTomcatUpgradeHandler(AuthenticatedPrincipal authPrincipal, String requestPath,
//			HttpUpgradeHandler upgradeHandler) {
//		this.authPrincipal = authPrincipal;
//		this.requestPath = requestPath;
//		this.upgradeHandler = upgradeHandler;
//		this.loader = ReflectionUtil.getContextLoader();
//	}
	IuTomcatUpgradeHandler(String requestPath, HttpUpgradeHandler upgradeHandler) {
		this.requestPath = requestPath;
		this.upgradeHandler = upgradeHandler;
		this.loader = Thread.currentThread().getContextClassLoader();
	}

	private void bind(boolean auth, UnsafeRunnable task) {
		try {
//			IuResources.SPI.bound(new IuCallHeader() {
//				@Override
//				public String getDescription() {
//					return task.toString();
//				}
//
//				@Override
//				public ClassLoader getClassLoader() {
//					return loader;
//				}
//			}, () -> {
//				if (auth && authPrincipal != null)
//					IU.getResource(IuIdentityManager.class).doAuthenticated(new AuthRequest() {
//						@Override
//						public String getContextPath() {
//							return requestPath;
//						}
//
//						@Override
//						public String getCallerIpAddress() {
//							return authPrincipal.getCallerIpAddress();
//						}
//
//						@Override
//						public String getCallerUserAgent() {
//							return authPrincipal.getCallerUserAgent();
//						}
//
//						@Override
//						public URL getCalledUrl() {
//							return authPrincipal.getCalledUrl();
//						}
//
//						@Override
//						public String getAuthType() {
//							return authPrincipal.getAuthType();
//						}
//
//						@Override
//						public String getCallerPrincipalName() {
//							return authPrincipal.getAuthPrincipal().getName();
//						}
//
//						@Override
//						public String getImpersonatedPrincipalName() {
//							IuPrincipal impersonated = authPrincipal.getImpersonatedPrincipal();
//							if (impersonated == null)
//								return null;
//							else
//								return impersonated.getName();
//						}
//
//						@Override
//						public String getAccessToken() {
//							return authPrincipal.getAccessToken();
//						}
//
//						@Override
//						public Object getAttributes() {
//							return authPrincipal.getAttributes(JsonValue.class);
//						}
//					}, task);
//				else
			task.run();
//			});
		} catch (RuntimeException | Error e) {
			throw e;
		} catch (Throwable e) {
			throw new IllegalStateException(e);
		}
	}

	@Override
	public boolean isInternal() {
		return upgradeHandler instanceof InternalHttpUpgradeHandler;
	}

	@Override
	public void init(WebUpgradeConnection connection) {
		bind(true, new UnsafeRunnable() {
			@Override
			public String toString() {
				return upgradeHandler.getClass().getSimpleName() + " init " + requestPath;
			}

			@Override
			public void run() throws Throwable {
				upgradeHandler.init(new IuTomcatWebConnection(connection));
			}
		});
	}

	@Override
	public void destroy() {
		bind(false, new UnsafeRunnable() {
			@Override
			public String toString() {
				return upgradeHandler.getClass().getSimpleName() + " destroy " + requestPath;
			}

			@Override
			public void run() throws Throwable {
				upgradeHandler.destroy();
			}
		});
	}

	@Override
	public String upgradeDispatch(String status) {
		if (!isInternal())
			return WebUpgradeHandler.super.upgradeDispatch(status);

		String[] rv = new String[1];
		bind(true, new UnsafeRunnable() {
			@Override
			public String toString() {
				return upgradeHandler.getClass().getSimpleName() + " dispatch " + status + " " + requestPath;
			}

			@Override
			public void run() throws Throwable {
				rv[0] = ((InternalHttpUpgradeHandler) upgradeHandler).upgradeDispatch(SocketEvent.valueOf(status))
						.name();
				LOG.fine(() -> "socket state " + rv[0]);
			}
		});
		return rv[0];
	}

	@Override
	public void setSocketWrapper(WebUpgradeSocketWrapper wrapper) {
		if (!isInternal())
			WebUpgradeHandler.super.setSocketWrapper(wrapper);
		else
			((InternalHttpUpgradeHandler) upgradeHandler).setSocketWrapper(new IuTomcatSocketWrapper(wrapper));
	}

	@Override
	public void setSslSupport(WebUpgradeSSLSupport sslSupport) {
		if (!isInternal())
			WebUpgradeHandler.super.setSslSupport(sslSupport);
		else
			((InternalHttpUpgradeHandler) upgradeHandler).setSslSupport(new IuTomcatSslSupport(sslSupport));
	}

	@Override
	public void pause() {
		if (!isInternal())
			WebUpgradeHandler.super.pause();
		else
			((InternalHttpUpgradeHandler) upgradeHandler).pause();
	}

	@Override
	public void timeoutAsync(long now) {
		if (!isInternal())
			WebUpgradeHandler.super.timeoutAsync(now);
		else
			((InternalHttpUpgradeHandler) upgradeHandler).timeoutAsync(now);
	}

}
