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

import java.util.Objects;
import java.util.logging.Logger;

import org.apache.commons.pool2.impl.GenericObjectPoolConfig;


import edu.iu.redis.IuRedisConfiguration;
import edu.iu.redis.IuRedis;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulRedisConnection;

/**
 * Support Lettuce connection.
 */
public class LettuceConnection implements IuRedis {
	//private final GenericObjectPoolConfig poolConfig;
	private static final Logger LOG = Logger.getLogger(LettuceConnection.class.getName());
	
	
	@SuppressWarnings("unused")
	private final StatefulRedisConnection<String, String> connection;
	
	/**
	 * constructor.
	 * @param config redis configuration
	 */
	public LettuceConnection(IuRedisConfiguration config) {
		Objects.requireNonNull(config, "config is required");
		String host = Objects.requireNonNull(config.getHost(), "host is required");
		String port = Objects.requireNonNull(config.getPort(), "port is required");
		String password = Objects.requireNonNull(config.getPassword(), "password is required");
		LOG.finer("Lettuce connection created");
		//this.poolConfig = new GenericObjectPoolConfig();
		RedisURI redisUri = RedisURI.Builder.redis(host, Integer.parseInt(port)) //
					.withPassword(password.toCharArray()) //
					.withSsl(true) //
					.build();
			RedisClient redisClient = RedisClient.create(redisUri);
			this.connection = redisClient.connect();
    }

}
