package edu.iu;

/**
 * Provides parameters from the HTTP Forwarded header.
 * 
 * @see <a href=
 *      "https://datatracker.ietf.org/doc/html/rfc7239#section-5">RFC-7239
 *      Forwarded HTTP Extension, Section 5</a>
 * @see IuWebUtils#parseForwardedHeader(String)
 */
public interface IuForwardedHeader {

	/**
	 * Gets the user-agent facing interface of the proxy.
	 * 
	 * @return "by" value
	 */
	String getBy();

	/**
	 * Gets the node making the request to the proxy.
	 * 
	 * @return "for" value
	 */
	String getFor();

	/**
	 * Gets the host request header field as received by the proxy.
	 * 
	 * @return "host" value
	 */
	String getHost();

	/**
	 * Gets the protocol used to make the request.
	 * 
	 * @return "proto" value
	 */
	String getProto();

}
