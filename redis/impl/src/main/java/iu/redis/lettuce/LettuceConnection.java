/*
 * Copyright © 2024 Indiana University
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

import java.time.Duration;
import java.util.Objects;

import org.apache.commons.pool2.impl.GenericObjectPool;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;

import edu.iu.IuException;
import edu.iu.IuText;
import edu.iu.redis.IuRedis;
import edu.iu.redis.IuRedisConfiguration;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import io.lettuce.core.support.ConnectionPoolSupport;

/**
 * Support Lettuce connection.
 */
public class LettuceConnection implements IuRedis {

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
		this.genericPool = ConnectionPoolSupport.createGenericObjectPool(() -> redisClient.connect(),
				new GenericObjectPoolConfig<StatefulRedisConnection<String, String>>());
		closed = false;
	}

	@Override
	public void put(byte[] key, byte[] data) {
		put(key, data, config.getKeyExpiration());
	}

	@Override
	public byte[] get(byte[] key) {
		Objects.requireNonNull(key, "key is required");
		try (final var connection = IuException.unchecked(() -> genericPool.borrowObject())) {
			RedisCommands<String, String> commands = connection.sync();
			String value = commands.get(IuText.utf8(key));
			return value != null ? value.getBytes() : null;
		}
	}

	@Override
	public void put(byte[] key, byte[] value, Duration ttl) {
		Objects.requireNonNull(key, "key is required");
		Objects.requireNonNull(value, "value is required");
		try (final var connection = IuException.unchecked(() -> genericPool.borrowObject())) {

			RedisCommands<String, String> commands = connection.sync();
			if (ttl != null && !ttl.isZero() && !ttl.isNegative()) {
				commands.setex(key.toString(), ttl.toMillis(), value.toString());
			}

			commands.set(IuText.utf8(key), IuText.utf8(value));
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
