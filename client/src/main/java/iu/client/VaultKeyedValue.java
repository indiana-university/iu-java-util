package iu.client;

import java.util.Objects;

import edu.iu.IuObject;
import edu.iu.client.IuVaultKeyedValue;
import edu.iu.client.IuVaultSecret;

/**
 * Basic {@link IuVaultKeyedValue} implementation.
 * 
 * @param <T> value type
 */
final class VaultKeyedValue<T> implements IuVaultKeyedValue<T> {

	private final VaultSecret secret;
	private final String key;
	private final T value;
	private final Class<T> type;

	/**
	 * Constructor.
	 * 
	 * @param secret {@link VaultSecret}
	 * @param key    key
	 * @param value  value
	 * @param type   type
	 */
	VaultKeyedValue(VaultSecret secret, String key, T value, Class<T> type) {
		this.secret = secret;
		this.key = key;
		this.value = value;
		this.type = type;
	}

	@Override
	public IuVaultSecret getSecret() {
		return secret;
	}

	@Override
	public String getKey() {
		return key;
	}

	@Override
	public T getValue() {
		return value;
	}

	@Override
	public Class<T> getType() {
		return type;
	}

	@Override
	public String toString() {
		return "VaultKeyedValue [" + key + "@" + secret + "]";
	}

	@Override
	public int hashCode() {
		return IuObject.hashCode(key, secret, type, value);
	}

	@Override
	public boolean equals(Object obj) {
		if (!IuObject.typeCheck(this, obj))
			return false;
		final var other = (VaultKeyedValue<?>) obj;
		return IuObject.equals(key, other.key) //
				&& Objects.equals(secret, other.secret) //
				&& Objects.equals(type, other.type) //
				&& Objects.equals(value, other.value);
	}

}
