package iu.redis.lettuce;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.util.logging.Level;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.stubbing.OngoingStubbing;

import edu.iu.redis.IuRedisConfiguration;
import edu.iu.test.IuTestLogger;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.RedisURI.Builder;

@SuppressWarnings("javadoc")
public class LettuceConnectionTest {

	private MockedStatic<RedisURI.Builder> builder;
	
	@BeforeEach
	void setUp() {
       builder = mockStatic(RedisURI.Builder.class);
       Builder mockBuilder = mock(Builder.class);
		//when(mockBuilder.withPort(any(Integer.class))).thenReturn(mockBuilder);
		when(mockBuilder.withPassword(any(char[].class))).thenReturn(mockBuilder);
		when(mockBuilder.withSsl(any(Boolean.class))).thenReturn(mockBuilder);
		when(mockBuilder.build()).thenReturn(mock(RedisURI.class));
       builder.when(() -> RedisURI.Builder.redis(any(String.class), any(Integer.class))).thenReturn(mockBuilder);
      
    }
	
	@AfterEach
	void tearDown() {
		builder.close();
	}
	@Test
	public void testConfigurationRequired() {
		assertThrows(NullPointerException.class, () -> new LettuceConnection(null));
	}
	
	@Test
	public void testHostRequired() {
        assertThrows(NullPointerException.class, () -> new LettuceConnection(new IuRedisConfiguration() {
            @Override
            public String getHost() {
                return null;
            }
            @Override
            public String getPort() {
                return "1234";
            }
            @Override
            public String getPassword() {
                return "password";
            }
            @Override
            public String getUsername() {
                return "username";
            }
        }));
    }
	
	@Test
	public void testPortRequired() {
		assertThrows(NullPointerException.class, () -> new LettuceConnection(new IuRedisConfiguration() {
			@Override
			public String getHost() {
				return "localhost";
			}

			@Override
			public String getPort() {
				return null;
			}

			@Override
			public String getPassword() {
				return "password";
			}

			@Override
			public String getUsername() {
				return "username";
			}
		}));
	}
	
	@Test
	public void testPasswordRequired() {
		assertThrows(NullPointerException.class, () -> new LettuceConnection(new IuRedisConfiguration() {
			@Override
			public String getHost() {
				return "localhost";
			}

			@Override
			public String getPort() {
				return "1234";
			}

			@Override
			public String getPassword() {
				return null;
			}

			@Override
			public String getUsername() {
				return "username";
			}
		}));
	}
	
	@Test
	@Disabled
	public void testConnection() {
		IuTestLogger.allow("", Level.FINE);
		final var config = mock(IuRedisConfiguration.class);
        when(config.getHost()).thenReturn("localhost");
        when(config.getPort()).thenReturn("1234");
        when(config.getPassword()).thenReturn("password");
        when(config.getUsername()).thenReturn("username");
		
		try (final var redisClientStaticMock = mockStatic(RedisClient.class)) {
			final RedisURI redisUriMock = mock(RedisURI.class);
		
			final var mockClient = mock(RedisClient.class);
		
			String mockHost = "localhost";
	        String mockPort = "6379";
	        String mockPassword = "securePassword";
	     // Parse the port
	        int port = Integer.parseInt(mockPort);
			
			//RedisURI.Builder mockBuilder = mock(Builder.class);
	        RedisURI mockRedisURI = mock(RedisURI.class);
			/*when(mockBuilder.withPort(port)).thenReturn(mockBuilder);
	        when(mockBuilder.withPassword(mockPassword.toCharArray())).thenReturn(mockBuilder);
	        when(mockBuilder.withSsl(true)).thenReturn(mockBuilder);
	        when(mockBuilder.build()).thenReturn(mockRedisURI);*/

	        // Stub the builder behavior
	        //when(mockBuilder.redis(mockHost, port)).thenReturn(mockBuilder);
	    	//builder.when(() -> RedisURI.Builder.redis(mockHost, port)).thenReturn(mockBuilder);
			
	    
					
			redisClientStaticMock.when(() -> RedisClient.create(redisUriMock)).thenReturn(mockClient);
			final var connection = new LettuceConnection(config);
			assert (connection != null);
		}
		
		
		
	}
}
