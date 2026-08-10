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
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import org.apache.commons.pool2.impl.GenericObjectPool;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;

import edu.iu.IuException;
import edu.iu.IuProcess;
import edu.iu.IuText;
import edu.iu.crypt.PemEncoded;
import edu.iu.redis.IuRedis;
import edu.iu.redis.IuRedisConfiguration;
import io.lettuce.core.ClientOptions;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.SocketOptions;
import io.lettuce.core.SslOptions;
import io.lettuce.core.api.StatefulRedisConnection;
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
	static boolean isHealthy(StatefulRedisConnection<String, String> connection) {
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

	private final GenericObjectPool<StatefulRedisConnection<String, String>> genericPool;
	private final RedisClient redisClient;
	private final IuRedisConfiguration config;
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

		final var poolConfig = new GenericObjectPoolConfig<StatefulRedisConnection<String, String>>();
		// a connection an intermediary silently drops while idle looks healthy until
		// something tries to use it; testWhileIdle runs a real PING against every idle
		// connection each EVICTION_INTERVAL, so a stale one is replaced before a
		// request ever borrows it, instead of hanging that request for a minute
		poolConfig.setTestWhileIdle(true);
		poolConfig.setTimeBetweenEvictionRuns(EVICTION_INTERVAL);

		this.genericPool = ConnectionPoolSupport.createGenericObjectPool(() -> redisClient.connect(), poolConfig,
				LettuceConnection::isHealthy);
		closed = false;
	}

	@Override
	public void put(byte[] key, byte[] data) {
		put(key, data, config.getKeyExpiration());
	}

	@Override
	public byte[] get(byte[] key) {
		Objects.requireNonNull(key, "key is required");
		final byte[] result;
		try (final var connection = IuException.unchecked(() -> genericPool.borrowObject())) {
			final var textkey = IuText.utf8(key);
			final var b64key = IuText.base64(key);
			final var commands = connection.sync();
			final var value = commands.get(textkey);

			if (value == null) {
				LOG.fine(() -> "redis:get:" + b64key + ":" + config.getHost() + ":" + config.getPort() + " (empty)");
				result = null;
			} else {
				final var bytes = IuText.utf8(value);
				LOG.fine(() -> "redis:get:" + b64key + ":" + config.getHost() + ":" + config.getPort() + " "
						+ bytes.length);
				result = bytes;
			}
		}
		return result;
	}

	@Override
	public void put(byte[] key, byte[] value, Duration ttl) {
		Objects.requireNonNull(key, "key is required");
		try (final var connection = IuException.unchecked(() -> genericPool.borrowObject())) {
			final var textkey = IuText.utf8(key);
			final var b64key = IuText.base64(key);
			final var commands = connection.sync();
			if (value == null) {
				commands.del(IuText.utf8(key));
				LOG.fine(() -> "redis:del:" + b64key + ":" + config.getHost() + ":" + config.getPort());
			} else {
				if (ttl != null && !ttl.isZero() && !ttl.isNegative())
					commands.setex(textkey, ttl.toSeconds(), IuText.utf8(value));
				else
					commands.set(textkey, IuText.utf8(value));
				LOG.fine(() -> "redis:put:" + b64key + ":" + config.getHost() + ":" + config.getPort() + ":" + ttl + " "
						+ value.length);
			}
		}
	}

	@Override
	public Iterable<?> list() {
		// key
		// expiration time, size of the value if we can't return size without loading
		// data then return size 0
		throw new UnsupportedOperationException();
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
