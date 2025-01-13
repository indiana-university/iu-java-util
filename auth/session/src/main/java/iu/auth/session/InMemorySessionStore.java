package iu.auth.session;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;

import edu.iu.IuDataStore;
import edu.iu.IuText;

/**
 * In-memory session store implementation.
 */
public class InMemorySessionStore implements IuDataStore {
	private static final Map<String, SessionToken> SESSION_TOKENS = new ConcurrentHashMap<>();
	private static final Timer PURGE_TIMER = new Timer("session-purge", true);
	static {
		PURGE_TIMER.schedule(new PurgeTask(), 15000L, 15000L);
	}
	
	/**
	 * Default constructor
	 */
	public InMemorySessionStore() {
	}

	/**
	 * Purges all expired stored sessions.
	 */
	static class PurgeTask extends TimerTask {
		/**
		 * Default constructor
		 */
		PurgeTask() {
		}

		@Override
		public void run() {
			final var purgeTime = Instant.now();
			final var i = SESSION_TOKENS.values().iterator();
			while (i.hasNext())
				if (i.next().inactivePurgeTime().isBefore(purgeTime))
					i.remove();
		}
	}

	@Override
	public Iterable<?> list() {
		return SESSION_TOKENS.keySet();
	}

	@Override
	public byte[] get(byte[] key) {
		final var session = SESSION_TOKENS.get(IuText.base64(key));
		if (session != null && session.inactivePurgeTime().isBefore(Instant.now())) {
			SESSION_TOKENS.remove(IuText.base64(key));
		}
		return session != null ? session.token() : null;
	}

	@Override
	public void put(byte[] key, byte[] data) {
		if (data == null)
			SESSION_TOKENS.remove(IuText.base64(key));
		else
			SESSION_TOKENS.put(IuText.base64(key), new SessionToken(data, Instant.now().plus(Duration.ofMinutes(15))));
	}

	@Override
	public void put(byte[] key, byte[] value, Duration ttl) {
		SESSION_TOKENS.put(IuText.base64(key), new SessionToken(value, Instant.now().plus(ttl)));

	}

}
