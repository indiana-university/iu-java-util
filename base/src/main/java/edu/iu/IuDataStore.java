package edu.iu;

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
	 * Gets the data represented by the given key.
	 * 
	 * @param key data key
	 * @return data
	 */
	byte[] get(String key);

	/**
	 * Puts or deletes data represented by a given key.
	 * 
	 * @param key  data key
	 * @param data data to assign to the key, replaces existing data. May be null to
	 *             delete existing data.
	 */
	void put(String key, byte[] data);
}
