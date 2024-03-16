/**
 * Thin client wrapper utilities supporting JSON over HTTP.
 */
module iu.util.client {
	exports edu.iu.client;

	requires iu.util;
	requires transitive jakarta.json;
	requires transitive java.net.http;
}
