package iu.auth.bundle;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.lang.reflect.Proxy;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.util.ArrayDeque;
import java.util.Objects;
import java.util.Queue;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;

import edu.iu.IuException;
import edu.iu.IuStream;
import edu.iu.UnsafeRunnable;
import edu.iu.type.base.TemporaryFile;
import edu.iu.type.loader.IuComponentLoader;

/**
 * Bootstrap SPI instance.
 */
public class Bootstrap {

	private static IuComponentLoader impl;
	private static UnsafeRunnable shutdown;

	private static IuComponentLoader loadImpl() throws Throwable {
		final var url = Bootstrap.class.getClassLoader().getResource("iu-java-auth-impl-bundle.jar");
		final var uc = url.openConnection();
		uc.setUseCaches(false);

		class Box {
			InputStream primary = null;
			final Queue<URL> utilDeps = new ArrayDeque<>();
			final Queue<InputStream> moduleDeps = new ArrayDeque<>();
		}
		final var box = new Box();
		final var cleanUtils = TemporaryFile.init(() -> {
			try (final var in = uc.getInputStream(); final var jar = new JarInputStream(in)) {
				JarEntry entry;
				while ((entry = jar.getNextJarEntry()) != null) {
					final var name = entry.getName();
					if ("iu-java-auth-impl.jar".equals(name))
						box.primary = new ByteArrayInputStream(IuStream.read(jar));
					else // if (name.endsWith(".jar"))
					if (name.startsWith("jakarta.") //
							|| name.startsWith("iu-java-") //
							|| name.equals("parsson.jar"))
						box.moduleDeps.add(new ByteArrayInputStream(IuStream.read(jar)));
					else
						box.utilDeps.add(TemporaryFile.init(t -> {
							try (final var out = Files.newOutputStream(t)) {
								IuStream.copy(jar, out);
							}
							return t.toUri().toURL();
						}));
				}
			}
		});
		final var parent = new URLClassLoader(box.utilDeps.toArray(URL[]::new), Bootstrap.class.getClassLoader());

		final var impl = new IuComponentLoader(parent, Set.of("edu.iu", "edu.iu.auth", "edu.iu.auth.basic",
				"edu.iu.auth.oauth", "edu.iu.auth.oidc", "edu.iu.auth.spi"), controller -> {
				}, Objects.requireNonNull(box.primary, "primary"), box.moduleDeps.toArray(a -> new InputStream[a]));

		shutdown = () -> {
			impl.close();
			parent.close();
			cleanUtils.run();
			Bootstrap.impl = null;
		};

		return impl;
	}

	static {
		impl = IuException.unchecked(Bootstrap::loadImpl);
	}

	/**
	 * Loads a service interface using bootstrapped class loader.
	 * 
	 * @param <T>              service type
	 * @param serviceInterface service interface
	 * @return SPI implementation
	 */
	static <T> T load(Class<T> serviceInterface) {
		final var delegate = ServiceLoader.load(serviceInterface, Objects.requireNonNull(impl).getLoader()).findFirst()
				.get();
		return serviceInterface.cast(Proxy.newProxyInstance(serviceInterface.getClassLoader(),
				new Class<?>[] { serviceInterface }, (proxy, method, args) -> {
					if (impl == null)
						throw new IllegalStateException("iu.util.auth.impl closed");
					return IuException.checkedInvocation(() -> method.invoke(delegate, args));
				}));
	}

	/**
	 * Tears down the auth implementation module.
	 * <p>
	 * <em>May</em> only be accessed programmatically or by JVM args, by the
	 * component in control of bootstrapping authentication.
	 * </p>
	 */
	static synchronized void shutdown() {
		if (shutdown != null) {
			IuException.unchecked(shutdown);
			shutdown = null;
		}
	}

	private Bootstrap() {
	}

}
