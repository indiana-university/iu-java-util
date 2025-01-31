/**
 * HTTP Server Module.
 */
module iu.util.web.server {
	exports edu.iu.web.server;
	opens edu.iu.web.server;

	requires iu.util;
	requires iu.util.client;
	requires iu.util.logging;
	requires iu.util.web;
	requires jakarta.annotation;
	requires java.logging;
	requires jdk.httpserver;
}
