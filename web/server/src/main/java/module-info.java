/**
 * HTTP Server Module.
 */
module iu.util.web.server {
	exports iu.web.server to java.logging;
	
	requires iu.util;
	requires java.logging;
	requires jdk.httpserver;
}
