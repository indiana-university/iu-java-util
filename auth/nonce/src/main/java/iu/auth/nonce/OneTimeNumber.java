package iu.auth.nonce;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import edu.iu.IdGenerator;
import edu.iu.auth.IuAuthorizationChallenge;
import edu.iu.auth.IuOneTimeNumber;
import edu.iu.auth.IuOneTimeNumberConfig;

/**
 * {@link IuOneTimeNumber} implementation class.
 */
final class OneTimeNumber implements IuOneTimeNumber, AutoCloseable {

	private static class ActiveCount {
		private volatile int count;
	}

	private final Logger LOG = Logger.getLogger(OneTimeNumber.class.getName());

	private IuOneTimeNumberConfig config;
	private Map<String, IuAuthorizationChallenge> challenge;
	private Timer purge;

	private int maxConcurrency = 5;
	private transient Map<String, ActiveCount> activeCount = new HashMap<>();

	/**
	 * Constructor
	 * 
	 * @param config {@link IuOneTimeNumber}
	 */
	OneTimeNumber(IuOneTimeNumberConfig config) {
		this.config = config;
		challenge = new ConcurrentHashMap<>();

		purge = new Timer();
		purge.schedule(new TimerTask() {
			@Override
			public void run() {
				final var i = challenge.keySet().iterator();
				while (i.hasNext())
					try {
						IdGenerator.verifyId(i.next(), config.getTimeToLive().toMillis());
					} catch (Throwable e) {
						i.remove();
					}
			}
		}, 500L, 500L);

		config.subscribe(this::receive);
	}

	@Override
	public String create(String remoteAddress, String userAgent) {
		if (challenge == null)
			return IdGenerator.generateId();

		ActiveCount activeCount;
		synchronized (this.activeCount) {
			activeCount = this.activeCount.compute(remoteAddress, (k, v) -> v == null ? new ActiveCount() : v);
			synchronized (activeCount) {
				activeCount.count++;
			}
		}

		try {
			if (activeCount.count > maxConcurrency) {
				LOG.info("Blocking excessive nonce generation; " + remoteAddress);
				return IdGenerator.generateId();
			}

			try {
				final var challenge = new IssuedChallenge(remoteAddress, userAgent);
				prune(challenge.getClientThumbprint());

				final var nonce = challenge.getNonce();
				this.challenge.put(nonce, challenge);

				config.publish(challenge);

				return nonce;
			} catch (Throwable e) {
				LOG.log(Level.INFO, "Discarding nonce", e);
				return IdGenerator.generateId();
			}

		} finally {
			synchronized (this.activeCount) {
				synchronized (activeCount) {
					activeCount.count--;
				}
				if (activeCount.count <= 0)
					this.activeCount.remove(remoteAddress);
			}
		}

	}

	@Override
	public void validate(String remoteAddress, String userAgent, String nonce) {
		if (challenge == null)
			throw new IllegalStateException();

		IdGenerator.verifyId(nonce, config.getTimeToLive().toMillis());
		config.publish(new UsedChallenge(nonce));

		if (!Arrays.equals(IssuedChallenge.thumbprint(remoteAddress, userAgent),
				Objects.requireNonNull(this.challenge.remove(nonce)).getClientThumbprint()))
			throw new IllegalArgumentException();
	}

	@Override
	public void close() {
		final var purge = this.purge;
		this.purge = null;
		if (purge != null)
			purge.cancel();

		challenge = null;
	}

	private void receive(IuAuthorizationChallenge message) {
		if (challenge == null)
			throw new IllegalStateException();

		final var nonce = message.getNonce();
		IdGenerator.verifyId(nonce, config.getTimeToLive().toMillis());

		final var thumbprint = message.getClientThumbprint();
		if (thumbprint == null)
			challenge.remove(message.getNonce());
		else {
			prune(thumbprint);
			challenge.put(message.getNonce(), message);
		}
	}

	private void prune(byte[] thumbprint) {
		final var i = challenge.entrySet().iterator();

		Queue<String> toPrune = null;
		while (i.hasNext()) {
			final var entry = i.next();
			final var verifyThumbprint = entry.getValue().getClientThumbprint();
			if (Arrays.equals(thumbprint, verifyThumbprint)) {
				final var nonce = entry.getKey();
				try {
					IdGenerator.verifyId(nonce, 250L);
					// artificial delay prevents excessive requests
					Thread.sleep(25L);
				} catch (Throwable e) {
					if (toPrune == null)
						toPrune = new ArrayDeque<>();
					toPrune.add(nonce);
				}
			}
		}

		if (toPrune != null)
			toPrune.forEach(challenge::remove);
	}

}
