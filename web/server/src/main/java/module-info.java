/**
 * HTTP Server Module.
 */
module iu.util.web.server {
	exports edu.iu.web.server;
	opens edu.iu.web.server;

	requires iu.util;
	requires jakarta.annotation;
	requires java.logging;
	requires jdk.httpserver;
	requires static org.apache.commons.text;
}
