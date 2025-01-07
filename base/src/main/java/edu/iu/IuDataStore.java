package edu.iu;

import java.time.Duration;

/**
 * Backing data storage interface for use in implementing data management
 * resources.
 */
public interface IuDataStore {
	
	/**
	 * Returns a listing of all entries in the data store (without contents).
	 * 
	 * @return data entry listing
	 */
	Iterable<?> list();

	/**
	 * Get the binary value representation from Redis stored for the given key.
	 * @param key must not be {@literal null}.
	 * @return {@literal null} if key does not exist.
	 */
	byte[] get(byte[] key);

	/**
	 * Puts or deletes data represented by a given key.
	 * 
	 * @param key  data key
	 * @param data data to assign to the key, replaces existing data. May be null to
	 *             delete existing data.
	 */
	//void put(String key, byte[] data);
	
	/**
	 * Write the given key/value pair to Redis and set the expiration time if defined.
	 *
	* @param key key for the cache entry. Must not be {@literal null}.
	 * @param value value stored for the key. Must not be {@literal null}.
	 * @param ttl optional expiration time. Can be {@literal null}.
	 */
	void put(byte[] key, byte[] value, Duration ttl);
	
}
