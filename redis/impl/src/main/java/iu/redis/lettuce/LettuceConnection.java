/*
 * Copyright © 2026 Indiana University
 * All rights reserved.
 *
 * BSD 3-Clause License
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 
 * - Redistributions of source code must retain the above copyright notice, this
 *   list of conditions and the following disclaimer.
 * 
 * - Redistributions in binary form must reproduce the above copyright notice,
 *   this list of conditions and the following disclaimer in the documentation
 *   and/or other materials provided with the distribution.
 * 
 * - Neither the name of the copyright holder nor the names of its
 *   contributors may be used to endorse or promote products derived from
 *   this software without specific prior written permission.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package iu.redis.lettuce;

import java.io.PrintStream;
import java.nio.file.Files;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.pool2.impl.GenericObjectPool;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;

import edu.iu.IuDataStoreEntry;
import edu.iu.IuException;
import edu.iu.IuProcess;
import edu.iu.IuText;
import edu.iu.crypt.PemEncoded;
import edu.iu.redis.IuRedis;
import edu.iu.redis.IuRedisConfiguration;
import io.lettuce.core.ClientOptions;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.ScanArgs;
import io.lettuce.core.SocketOptions;
import io.lettuce.core.SslOptions;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.codec.ByteArrayCodec;
import io.lettuce.core.codec.RedisCodec;
import io.lettuce.core.codec.StringCodec;
import io.lettuce.core.support.ConnectionPoolSupport;

/**
 * Support Lettuce connection.
 */
public class LettuceConnection implements IuRedis {

	private static final Logger LOG = Logger.getLogger(LettuceConnection.class.getName());

	/** Interval between idle-connection health sweeps. */
	private static final Duration EVICTION_INTERVAL = Duration.ofSeconds(30);

	/**
	 * Bound on how long a health-check {@code PING} may take before the connection
	 * under test is treated as unusable, so a half-open socket -- exactly the case
	 * this check exists to catch -- cannot itself hang the sweep.
	 */
	private static final Duration PING_TIMEOUT = Duration.ofSeconds(2);

	/**
	 * Key segment naming a stored value.
	 *
	 * @see #key(String, String)
	 */
	private static final String DATA = ":d:";

	/**
	 * Key segment naming a stored write time.
	 *
	 * <p>
	 * Redis keeps no modification time of its own. {@code OBJECT IDLETIME} reports
	 * last <em>access</em>, which every {@link #get(byte[])} resets, and
	 * {@code PTTL} only reveals a write time to a reader that already knows the
	 * interval the key was written with -- which a caller passing its own
	 * {@link #put(byte[], byte[], Duration) ttl} does not share. So the write time
	 * is only available to {@link #lastModified(byte[])} if it is stored.
	 * </p>
	 *
	 * <p>
	 * It is kept beside the value rather than folded into it, so that
	 * {@link #get(byte[])} returns exactly the bytes it was given and any other
	 * reader of the same Redis sees the value it expects. A write time carries the
	 * same expiration as the value it describes, so it cannot outlive it.
	 * </p>
	 *
	 * @see #key(String, String)
	 */
	private static final String MODIFIED = ":m:";

	/**
	 * Keys transferred per {@code SCAN} round trip.
	 *
	 * <p>
	 * {@code SCAN} filters by pattern only after walking the keyspace, so a listing
	 * costs a pass over every key in the database, not only this store's own.
	 * Lettuce would otherwise send no {@code COUNT} at all and take Redis's default
	 * of ten, which turns that one pass into thousands of round trips on a shared
	 * instance.
	 * </p>
	 */
	private static final long SCAN_COUNT = 1000L;

	/**
	 * Codec pairing readable text keys with binary-safe values.
	 *
	 * <p>
	 * Keys are named by this class and are always printable
	 * ({@link #key(String, String)}), so a text codec is exact for them and leaves
	 * a key legible in {@code redis-cli} and in a log. Values are whatever a caller
	 * stored, so they must not be decoded at all: a {@link StringCodec} replaces
	 * every byte sequence that is not valid UTF-8 with U+FFFD, which silently
	 * corrupts any value that is not already text.
	 * </p>
	 */
	private static final RedisCodec<String, byte[]> CODEC = RedisCodec.of(StringCodec.UTF8, ByteArrayCodec.INSTANCE);

	/**
	 * Validates a pooled connection with a real, bounded-time {@code PING}, run by
	 * the pool's idle-connection sweep rather than on every borrow, since
	 * validating on every borrow would double the Redis round trips a session-bound
	 * request already makes.
	 *
	 * <p>
	 * The default validation {@link ConnectionPoolSupport} would otherwise use is
	 * {@link StatefulRedisConnection#isOpen()}, which only reflects that nothing
	 * has yet observed the socket as closed -- exactly the state a connection an
	 * intermediary (firewall, load balancer, or Kubernetes conntrack) silently
	 * dropped while idle is left in. A real command is the only way to find out.
	 * </p>
	 *
	 * <p>
	 * Package-private for direct unit testing.
	 * </p>
	 *
	 * @param connection pooled connection under test; {@code null} if the pool's
	 *                   factory could not create one
	 * @return {@code true} if the connection answers {@code PING} within
	 *         {@link #PING_TIMEOUT}
	 */
	static boolean isHealthy(StatefulRedisConnection<String, byte[]> connection) {
		if (connection == null)
			return false;

		final var ping = connection.async().ping();
		try {
			return "PONG".equals(ping.get(PING_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS));
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return false;
		} catch (Exception e) {
			return false;
		} finally {
			ping.cancel(true);
		}
	}

	private final GenericObjectPool<StatefulRedisConnection<String, byte[]>> genericPool;
	private final RedisClient redisClient;
	private final IuRedisConfiguration config;
	private final String keyPrefix;
	private volatile boolean closed;

	/**
	 * constructor.
	 *
	 * @param config redis configuration
	 */
	public LettuceConnection(IuRedisConfiguration config) {
		Objects.requireNonNull(config, "config is required");
		String host = Objects.requireNonNull(config.getHost(), "host is required");
		String port = Objects.requireNonNull(config.getPort(), "port is required");
		String password = Objects.requireNonNull(config.getPassword(), "password is required");

		// read once: the prefix names every key this connection writes, so a change
		// under a running connection would strand everything already stored
		this.keyPrefix = Objects.requireNonNull(config.getKeyPrefix(), "key prefix is required");
		RedisURI redisUri = RedisURI.Builder.redis(host, Integer.parseInt(port)) //
				.withPassword(password.toCharArray()) //
				.withSsl(true) //
				.build();
		this.config = config;
		this.redisClient = RedisClient.create(redisUri);

		// TCP keepalive is defense in depth: it lets the OS eventually notice a
		// connection an intermediary dropped while idle, but OS keepalive timers
		// default to hours, far past the pool's own eviction sweep below -- which is
		// what actually catches this within the few minutes the failure allows
		final var clientOptions = ClientOptions.builder() //
				.socketOptions(SocketOptions.builder().keepAlive(true).build());

		final var cert = config.getTrustedCert();
		if (cert != null) {
			final var caCertFile = IuProcess.createTempFile();
			IuException.unchecked(() -> {
				try (final var caCertOut = Files.newOutputStream(caCertFile); //
						final var out = new PrintStream(caCertOut)) {
					PemEncoded.print(out, cert);
				}
			});
			clientOptions.sslOptions(SslOptions.builder().trustManager(caCertFile.toFile()).build());
		}
		redisClient.setOptions(clientOptions.build());

		final var poolConfig = new GenericObjectPoolConfig<StatefulRedisConnection<String, byte[]>>();
		// a connection an intermediary silently drops while idle looks healthy until
		// something tries to use it; testWhileIdle runs a real PING against every idle
		// connection each EVICTION_INTERVAL, so a stale one is replaced before a
		// request ever borrows it, instead of hanging that request for a minute
		poolConfig.setTestWhileIdle(true);
		poolConfig.setTimeBetweenEvictionRuns(EVICTION_INTERVAL);

		this.genericPool = ConnectionPoolSupport.createGenericObjectPool(() -> redisClient.connect(CODEC), poolConfig,
				LettuceConnection::isHealthy);
		closed = false;
	}

	/**
	 * Names a Redis key.
	 *
	 * <p>
	 * A key is {@code <prefix>:<segment>:{<name>}}, where the name is the store key
	 * as {@link IuDataStoreEntry#getName() listed} -- Base64 URL-encoded, so it is
	 * printable, exact, and contains no character {@code SCAN MATCH} would read as
	 * a wildcard. The prefix scopes the store within a shared Redis, and the
	 * segment separates a value from its write time, so a listing can ask for one
	 * without the other.
	 * </p>
	 *
	 * <p>
	 * The name is wrapped in a hash tag, which Redis Cluster hashes in place of the
	 * whole key. That keeps a value and its write time in one slot, so the
	 * two-key {@code DEL} in {@link #put(byte[], byte[], Duration)} stays legal if
	 * this is ever pointed at a cluster, while still spreading distinct keys across
	 * slots. On a standalone instance it is an inert pair of characters.
	 * </p>
	 *
	 * @param segment {@link #DATA} or {@link #MODIFIED}
	 * @param name    store key, Base64 URL-encoded
	 * @return Redis key
	 */
	private String key(String segment, String name) {
		return keyPrefix + segment + '{' + name + '}';
	}

	@Override
	public void put(byte[] key, byte[] data) {
		put(key, data, config.getKeyExpiration());
	}

	/**
	 * Reads the value stored under a listing name.
	 *
	 * @param name store key, Base64 URL-encoded
	 * @return stored value; null if nothing is stored under {@code name}
	 */
	private byte[] get(String name) {
		final byte[] result;
		try (final var connection = IuException.unchecked(() -> genericPool.borrowObject())) {
			final var value = connection.sync().get(key(DATA, name));

			if (value == null) {
				LOG.fine(() -> "redis:get:" + name + ":" + config.getHost() + ":" + config.getPort() + " (empty)");
				result = null;
			} else {
				LOG.fine(() -> "redis:get:" + name + ":" + config.getHost() + ":" + config.getPort() + " "
						+ value.length);
				result = value;
			}
		}
		return result;
	}

	@Override
	public byte[] get(byte[] key) {
		Objects.requireNonNull(key, "key is required");
		return get(IuText.base64Url(key));
	}

	@Override
	public void put(byte[] key, byte[] value, Duration ttl) {
		Objects.requireNonNull(key, "key is required");
		try (final var connection = IuException.unchecked(() -> genericPool.borrowObject())) {
			final var name = IuText.base64Url(key);
			final var datakey = key(DATA, name);
			final var modifiedkey = key(MODIFIED, name);
			final var commands = connection.sync();
			if (value == null) {
				// both in one command, so a write time can never survive the value it
				// describes and be read as that of a value written later
				commands.del(datakey, modifiedkey);
				LOG.fine(() -> "redis:del:" + name + ":" + config.getHost() + ":" + config.getPort());
			} else {
				// the value is written first: a write time lost to a connection that
				// drops between the two commands reads as unknown, and an older one left
				// in place reads as staler than the value actually is. Either understates
				// freshness, which costs a caller a re-read; the reverse order would
				// overstate it, which would have a caller keep data it should replace
				final var modified = IuText.ascii(Long.toString(System.currentTimeMillis()));
				if (ttl != null && !ttl.isZero() && !ttl.isNegative()) {
					commands.setex(datakey, ttl.toSeconds(), value);
					commands.setex(modifiedkey, ttl.toSeconds(), modified);
				} else {
					commands.set(datakey, value);
					commands.set(modifiedkey, modified);
				}
				LOG.fine(() -> "redis:put:" + name + ":" + config.getHost() + ":" + config.getPort() + ":" + ttl + " "
						+ value.length);
			}
		}
	}

	/**
	 * Reads the write time named by the text stored beside a value.
	 *
	 * <p>
	 * Kept out of {@link #lastModified(String)} so that method returns from a
	 * single exit point, the shape {@link #get(String)} already uses: each
	 * additional return from inside a try-with-resources compiles to a close path
	 * of its own, carrying branches no test reaches, which the coverage gate reads
	 * as missed.
	 * </p>
	 *
	 * @param name     store key, as logged
	 * @param modified stored write time
	 * @return instant the value was written; null if the stored text does not name
	 *         one
	 */
	private Instant writeTime(String name, byte[] modified) {
		try {
			final var writeTime = Instant.ofEpochMilli(Long.parseLong(IuText.ascii(modified)));
			LOG.fine(() -> "redis:mtime:" + name + ":" + config.getHost() + ":" + config.getPort() + " " + writeTime);
			return writeTime;
		} catch (NumberFormatException e) {
			// only reachable if something other than this store wrote the key; an
			// unknown write time is the documented degradation, and is a far better
			// answer than failing a freshness check outright
			LOG.log(Level.FINE, e,
					() -> "redis:mtime:" + name + ":" + config.getHost() + ":" + config.getPort() + " (invalid)");
			return null;
		}
	}

	/**
	 * Reads the write time stored for a listing name.
	 *
	 * @param name store key, Base64 URL-encoded
	 * @return instant the value was written; null if no write time is stored
	 */
	private Instant lastModified(String name) {
		final Instant result;
		try (final var connection = IuException.unchecked(() -> genericPool.borrowObject())) {
			final var modified = connection.sync().get(key(MODIFIED, name));

			if (modified == null) {
				LOG.fine(() -> "redis:mtime:" + name + ":" + config.getHost() + ":" + config.getPort() + " (empty)");
				result = null;
			} else
				result = writeTime(name, modified);
		}
		return result;
	}

	@Override
	public Instant lastModified(byte[] key) {
		Objects.requireNonNull(key, "key is required");
		return lastModified(IuText.base64Url(key));
	}

	/**
	 * Listing entry, resolving its attributes as they are read.
	 *
	 * <p>
	 * Holds only the name a scan reported. Both other attributes cost a round trip
	 * here, so neither is read until it is asked for, and each is read afresh: a
	 * listing of a store this size would otherwise be a bulk transfer of everything
	 * it holds, and the entries would be stale before the caller reached the end of
	 * them.
	 * </p>
	 */
	private class Entry implements IuDataStoreEntry {
		private final String name;

		private Entry(String name) {
			this.name = name;
		}

		@Override
		public String getName() {
			return name;
		}

		@Override
		public Instant getModified() {
			return lastModified(name);
		}

		@Override
		public byte[] getData() {
			return get(name);
		}
	}

	@Override
	public Iterable<IuDataStoreEntry> list() {
		// pattern-matched server-side, so the write times never reach the client and
		// neither does anything else sharing the database. SCAN still walks every key
		// in it: this is an operator's view of the store, not a request-path call
		final var args = new ScanArgs().match(key(DATA, "*")).limit(SCAN_COUNT);

		// SCAN guarantees only that a key present throughout is returned at least
		// once, so the same key can arrive on two pages; keyed by name to collapse
		// that, and ordered so a listing reads the way the scan ran
		final Map<String, IuDataStoreEntry> entries = new LinkedHashMap<>();
		try (final var connection = IuException.unchecked(() -> genericPool.borrowObject())) {
			final var commands = connection.sync();

			// an empty name gives the affixes alone; the name in a scanned key starts
			// one character back from the end of those, inside the opening brace
			final var nameAt = key(DATA, "").length() - 1;

			var cursor = commands.scan(args);
			while (true) {
				for (final var key : cursor.getKeys()) {
					// the MATCH pattern admitted only {name}, so the braces are known to
					// be there and the name between them is taken as scanned
					final var name = key.substring(nameAt, key.length() - 1);
					entries.putIfAbsent(name, new Entry(name));
				}

				if (cursor.isFinished())
					break;

				cursor = commands.scan(cursor, args);
			}
		}

		LOG.fine(() -> "redis:list:" + keyPrefix + ":" + config.getHost() + ":" + config.getPort() + " "
				+ entries.size());

		return entries.values();
	}

	@Override
	public synchronized void close() {
		Throwable error = null;
		if (!closed) {
			closed = true;
			error = IuException.suppress(error, genericPool::close);
			error = IuException.suppress(error, redisClient::shutdown);
		}

		if (error != null)
			throw IuException.unchecked(error);
	}

}
