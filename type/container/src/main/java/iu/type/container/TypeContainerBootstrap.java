package iu.type.container;

import java.io.InputStream;
import java.lang.ModuleLayer.Controller;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.function.Consumer;
import java.util.logging.Logger;
import java.util.stream.Stream;

import edu.iu.IuException;
import edu.iu.IuIterable;
import edu.iu.IuObject;
import edu.iu.IuRuntimeEnvironment;
import edu.iu.UnsafeRunnable;
import edu.iu.type.IuComponent;
import edu.iu.type.base.FilteringClassLoader;
import edu.iu.type.base.ModularClassLoader;

/**
 * Authentication and authorization bootstrap.
 */
public class TypeContainerBootstrap implements UnsafeRunnable, AutoCloseable {
	static {
		IuObject.assertNotOpen(TypeContainerBootstrap.class);
	}

	private static final Logger LOG = Logger.getLogger(TypeContainerBootstrap.class.getName());

	private final TypeContainerArchive[] archives;
	private final ClassLoader parentLoader;
	private final ModuleLayer parentLayer;

	private ModularClassLoader base;
	private ModularClassLoader support;
	private Deque<IuComponent> initializedComponents;
	private boolean closed;

	/**
	 * Constructor
	 */
	public TypeContainerBootstrap() {
		final var componentsPropertyValue = IuRuntimeEnvironment.envOptional("iu.boot.components");
		if (componentsPropertyValue == null) {
			archives = null;
			parentLoader = null;
			parentLayer = null;
		} else {
			final var archivePathNames = componentsPropertyValue.split(",");
			archives = new TypeContainerArchive[archivePathNames.length];
			for (var i = 0; i < archives.length; i++)
				archives[i] = IuException.unchecked(Path.of(archivePathNames[i]), TypeContainerArchive::new);

			parentLoader = IuObject.class.getClassLoader();
			parentLayer = IuObject.class.getModule().getLayer();
		}
	}

	@Override
	public void run() throws Throwable {
		if (archives == null)
			return;

		LOG.fine("before init container");
		final var filter = new FilteringClassLoader(IuIterable.iter(IuObject.class.getPackageName()), parentLoader);
		final var api = IuIterable.of(() -> Stream.of(archives).flatMap(a -> Stream.of(a.api())).iterator());

		final var baseInit = new Consumer<Controller>() {
			Controller controller;
			Module cryptModule;

			@Override
			public void accept(Controller c) {
				controller = c;
				cryptModule = c.layer().findModule("iu.util.crypt").orElse(null);
			}
		};
		base = new ModularClassLoader(false, api, parentLayer, filter, baseInit);

		try {
			LOG.fine("after init base " + base.getModuleLayer());

			final Deque<IuComponent> initializedComponents = new ArrayDeque<>();
			for (final var archive : archives) {
				final ClassLoader parent;
				if (archive.support().length == 0)
					parent = base;
				else {
					support = new ModularClassLoader(false, IuIterable.iter(archive.support()), base.getModuleLayer(),
							base, c -> {
							});
					LOG.fine("after init support " + support.getModuleLayer());
					parent = support;
				}

				final List<InputStream> sources = new ArrayList<>(1 + archive.lib().length);
				sources.add(Files.newInputStream(archive.primary()));
				for (final var lib : archive.lib())
					sources.add(Files.newInputStream(lib));

				final var compInit = new Consumer<Controller>() {
					Module cryptImpl;

					@Override
					public void accept(Controller c) {
						cryptImpl = c.layer().findModule("iu.util.crypt.impl").orElse(null);
						if (cryptImpl != null)
							baseInit.controller.addExports(baseInit.cryptModule, "iu.crypt.spi", cryptImpl);
					}
				};
				final var component = IuComponent.of(parent, base.getModuleLayer(), compInit, sources.get(0),
						sources.subList(1, sources.size()).toArray(InputStream[]::new));

				final var current = Thread.currentThread();
				final var restore = current.getContextClassLoader();
				try {
					current.setContextClassLoader(component.classLoader());
					if (compInit.cryptImpl != null)
						base.loadClass("edu.iu.crypt.Init");
				} finally {
					current.setContextClassLoader(restore);
				}

				for (final var source : sources)
					source.close();

				LOG.fine("after create " + component);
				initializedComponents.push(component);
			}

			this.initializedComponents = initializedComponents;

			// TODO: run and await completion for all Runnable and UnsafeRunnable resources

		} catch (Throwable e) {
			if (support != null)
				IuException.suppress(e, support::close);
			IuException.suppress(e, base::close);
			throw e;
		}
	}

	@Override
	public void close() throws Exception {
		if (archives == null)
			return;

		Throwable error = null;

		final var initializedComponents = this.initializedComponents;
		if (initializedComponents != null) {
			this.initializedComponents = null;
			synchronized (initializedComponents) {
				while (!initializedComponents.isEmpty()) {
					final var component = initializedComponents.pop();
					LOG.fine("before destroy " + component);
					error = IuException.suppress(error, component::close);
				}
			}
		}

		final var support = this.support;
		if (support != null) {
			this.support = null;
			LOG.fine("before destroy support " + support.getModuleLayer());
			error = IuException.suppress(error, support::close);
		}

		final var base = this.base;
		if (base != null) {
			this.base = null;
			LOG.fine("before destroy base " + base.getModuleLayer());
			error = IuException.suppress(error, base::close);
		}

		for (var i = 0; i < archives.length; i++)
			if (archives[i] != null) {
				error = IuException.suppress(error, archives[i]::close);
				archives[i] = null;
			}

		if (error != null)
			throw IuException.checked(error);
		else if (!closed) {
			closed = true;
			LOG.fine("after destroy container");
		}
	}

}
