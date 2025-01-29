package iu.web.server;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.UnaryOperator;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpHandlers;
import com.sun.net.httpserver.HttpServer;

import edu.iu.IuException;
import edu.iu.IuStream;
import edu.iu.UnsafeFunction;

/**
 * {@link HttpServer} configuration wrapper.
 */
public final class IuHttpListener implements AutoCloseable {

	private static final Logger LOG = Logger.getLogger(IuHttpListener.class.getName());

	// Default for unknown content types, as per RFC 2046
//	private static final String DEFAULT_CONTENT_TYPE = "application/octet-stream";

	private static final Map<String, String> MIME_TABLE = new ConcurrentHashMap<>();
	private static final Map<String, Path> FILE_MAP = new ConcurrentHashMap<>();
//	private static final String FILES_ZIP = "/ess-frontend-template-bundle.zip";

	static {
		String[] mimeTypes = { "html", "text/html", "js", "application/javascript", "js.map", "application/json", "css",
				"text/css", "png", "image/png", "jpg", "image/jpeg", "jpeg", "image/jpeg", "gif", "image/gif", "svg",
				"image/svg+xml" };
		for (int i = 0; i < mimeTypes.length; i += 2) {
			MIME_TABLE.put(mimeTypes[i], mimeTypes[i + 1]);
		}
		
		// Get the list of zip files in the directory /opt/starch/resources
		// TODO: This should be configurable
        Path dir = Paths.get("/opt/starch/resources");

        DirectoryStream<Path> stream = IuException.unchecked(() -> Files.newDirectoryStream(dir, "*.zip"));
        for (Path entry : stream) {
            URL fileUrl = IuException.unchecked(() -> entry.toUri().toURL());
            FILE_MAP.putAll(bootstrapStaticFileServer(fileUrl));
        }
        IuException.unchecked(() -> stream.close());
	}

	private static final UnaryOperator<String> GET_MIME_TYPE = extension -> {
		return MIME_TABLE.getOrDefault(extension, URLConnection.getFileNameMap().getContentTypeFor(extension));
	};

	private final int stopDelay;
	private volatile HttpServer server;

	/**
	 * Constructor.
	 * 
	 * @param localAddress local address
	 * @param backlog      {@link HttpServer#bind(InetSocketAddress, int) backlog}
	 * @param stopDelay    seconds to wait for all request to complete on close
	 * @return {@link IuHttpListener}
	 * @throws IOException If an error occurs binding to server socket
	 */
	public static IuHttpListener create(InetSocketAddress localAddress, int backlog, int stopDelay) throws IOException {
		final var server = HttpServer.create(localAddress, backlog);
		server.createContext("/", HttpHandlers.handleOrElse((req) -> {
			if (!req.getRequestMethod().equals("GET")) {
				LOG.warning("files context method not allowed: " + req.getRequestMethod());
				return false;
			}
			String reqPath = req.getRequestURI().getPath();
			LOG.info("path: " + reqPath);
			return true;
		},
				new HttpHandler() {
					@Override
					public void handle(HttpExchange exchange) throws IOException {
						LOG.info("file handler handling request URI: " + exchange.getRequestURI().toString());
						// get the content type from the file extension in the request URI
						String reqPath = exchange.getRequestURI().getPath();
						String assetPath = reqPath.substring(1); // remove leading /
						// if assetPath is empty or no filename is given, assume the request is for an
						// index.html file
						if (assetPath.isEmpty() //
								|| assetPath.endsWith("/")) {
							assetPath += "index.html";
						}
						// special handling for .js.map files
						int extIndex = 0;
						if (assetPath.endsWith(".js.map")) {
							extIndex = assetPath.length() - 7;
						} else {
							extIndex = assetPath.lastIndexOf('.');
						}
						String ext = assetPath.substring(extIndex + 1);
						String contentType = GET_MIME_TYPE.apply(ext);
						exchange.getResponseHeaders().add("content-type", contentType);

						Path file = FILE_MAP.get(assetPath);
						if (file == null //
								|| !Files.exists(file)) {
							LOG.warning("file not found: " + reqPath);
							exchange.sendResponseHeaders(HttpURLConnection.HTTP_NOT_FOUND, 0);
							return;
						}
						// get the file from the map of jar file entries
						final var contents = Files.readAllBytes(file);
						exchange.sendResponseHeaders(HttpURLConnection.HTTP_OK, 0);
						try (final var body = exchange.getResponseBody()) {
							body.write(contents);
						}
					}
				}, (exchange) -> {
					// TODO: fallback handler
					LOG.info("files context fallback. request URI: " + exchange.getRequestURI().toString());
				}));
		server.setExecutor(null); // creates a default executor
		server.start();

		final var listener = new IuHttpListener(server, stopDelay);
		LOG.fine(() -> "started " + listener);
		return listener;
	}

	/**
	 * @param bundleResource
	 * @return a map of file paths to temporary files
	 */
	public static Map<String, Path> bootstrapStaticFileServer(URL bundleResource) {
		Map<String, Path> fileMap = new ConcurrentHashMap<>();
		with(bundleResource, o -> {
			try (final var in = Files.newInputStream((Path) o); //
					final var bundleZip = new ZipInputStream(in)) {
				
				ZipEntry entry;
				while ((entry = bundleZip.getNextEntry()) != null) {
					final var name = entry.getName();
					if (!entry.isDirectory()) {
						String fileNameWithExt = name.substring(name.lastIndexOf('/') + 1);
						// special handling for .js.map files
						int extIndex = 0;
						if (fileNameWithExt.endsWith(".js.map")) {
							extIndex = fileNameWithExt.length() - 7;
						} else {
							extIndex = fileNameWithExt.lastIndexOf('.');
						}
						
						String fileName = fileNameWithExt.substring(0, extIndex) + "-";
						String ext = fileNameWithExt.substring(extIndex);
						// Simplistic way to create a temporary file to hold the entry, rather than working around the iu-type....jar naming
						Path temp = Files.createTempFile(fileName, ext);
						try (final var out = Files.newOutputStream(temp)) {
							IuStream.copy(bundleZip, out);
						}
						fileMap.put(name, temp);
					}
				}
				bundleZip.closeEntry();
			}
			return null;
		});
		return fileMap;
	}

//	public static Iterable<Path> readBundle(URL bundleResource) {
//		final Deque<Path> libs = new ArrayDeque<>();
//		with(bundleResource, o -> {
//			try (final var in = (o instanceof Path) //
//					? Files.newInputStream((Path) o) //
//					: (InputStream) o; //
//					final var bundleJar = new JarInputStream(in)) {
//				JarEntry entry;
//				while ((entry = bundleJar.getNextJarEntry()) != null) {
//					final var name = entry.getName();
//					if (name.endsWith(".jar")) {
//						final var lib = name.startsWith("lib/");
//						final var bundledLib = TemporaryFile.init(path -> {
//							try (final var out = Files.newOutputStream(path)) {
//								IuStream.copy(bundleJar, out);
//							}
//							return path;
//						});
//						if (lib)
//							libs.offer(bundledLib);
//						else
//							libs.offerFirst(bundledLib);
//					}
//				}
//				bundleJar.closeEntry();
//			}
//			return null;
//		});
//		return libs;
//	}

	private static <T> T with(URL url, UnsafeFunction<Object, T> then) {
		return IuException.unchecked(() -> {
			final var uri = url.toURI();
			final var scheme = uri.getScheme();

			if ("file".equals(scheme))
				return then.apply(Path.of(uri).toRealPath());

			throw new IllegalArgumentException();
		});
	}

	private IuHttpListener(HttpServer server, int stopDelay) {
		this.server = server;
		this.stopDelay = stopDelay;
	}

	@Override
	public synchronized void close() throws Exception {
		final var server = this.server;
		if (server != null) {
			this.server = null;
			server.stop(stopDelay);
			LOG.fine(() -> "stopped " + this + "; " + server);
		}
	}

	@Override
	public String toString() {
		return "IuHttpListener [stopDelay=" + stopDelay + ", server=" + server + "]";
	}

}
