package iu.auth.session;

import java.time.Instant;
import java.util.Objects;

/**
 * Holds session token record
 * 
 * @param token             tokenized session
 * @param inactivePurgeTime inactive purge time
 */
public record SessionToken(String token, Instant inactivePurgeTime) {

	/**
	 * constructor
	 * 
	 * @param token             token
	 * @param inactivePurgeTime inactive purge time
	 */
	public SessionToken(String token, Instant inactivePurgeTime) {
		Objects.requireNonNull(token, "token required");
		Objects.requireNonNull(inactivePurgeTime, "inactivePurgeTime required");
		this.token = token;
		this.inactivePurgeTime = inactivePurgeTime;
	}

}
