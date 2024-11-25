package iu.auth.component;

import java.util.logging.Logger;

import jakarta.annotation.Resource;

/**
 * Authentication and authorization bootstrap.
 */
@Resource
public class AuthComponentBootstrap {

	private static final Logger LOG = Logger.getLogger(AuthComponentBootstrap.class.getName());

	/**
	 * Default Constructor
	 */
	AuthComponentBootstrap() {
		LOG.config("TODO: initialize authentication");
		throw new UnsupportedOperationException("TODO");
	}

}
