package iu.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.net.URI;
import java.util.ArrayDeque;
import java.util.Queue;

import org.junit.jupiter.api.Test;

import edu.iu.IdGenerator;
import edu.iu.client.IuVaultKeyedValue;

@SuppressWarnings("javadoc")
public class VaultKeyedValueTest {

	@Test
	public void testProperties() {
		Queue<IuVaultKeyedValue<?>> q = new ArrayDeque<>();
		for (int i = 0; i < 2; i++) {
			final var key = IdGenerator.generateId();
			for (int j = 0; j < 2; j++) {
				final var uri = URI.create("test:" + IdGenerator.generateId());
				final var secret = new VaultSecret(null, uri, null, null, null, null);
				for (int k = 0; k < 2; k++) {
					final var value = IdGenerator.generateId();

					final var vkv = new VaultKeyedValue<>(secret, key, value, String.class);
					assertSame(secret, vkv.getSecret());
					assertEquals(key, vkv.getKey());
					assertEquals(value, vkv.getValue());
					assertEquals(String.class, vkv.getType());
					assertEquals("VaultKeyedValue [" + key + "@VaultSecret [" + uri + "]]", vkv.toString());

					q.offer(vkv);
					q.offer(new VaultKeyedValue<>(secret, key, value, Object.class));
				}
			}
		}

		for (final var a : q)
			for (final var b : q)
				if (a == b) {
					assertNotEquals(a, new Object());
					assertEquals(a, b);
					assertEquals(a.hashCode(), b.hashCode());
				} else {
					assertNotEquals(a, b);
					assertNotEquals(b, a);
					assertNotEquals(a.hashCode(), b.hashCode());
				}
	}

}
