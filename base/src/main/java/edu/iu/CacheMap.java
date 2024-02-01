package edu.iu;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.SoftReference;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;

/**
 * Caching {@link Map} implementation backed by {@link SoftReference soft
 * references} with configuration expiration time.
 * 
 * @param <K> key type
 * @param <V> value type
 */
public class CacheMap<K, V> implements Map<K, V> {

	private static final Object NULL = new Object();
	private static final Object[] O0 = new Object[0];

	private static class CacheReference<V> extends SoftReference<V> {

		private final Object key;
		private final Instant expires;

		private CacheReference(Object key, V referent, Instant expires, ReferenceQueue<? super V> q) {
			super(referent, q);
			this.key = key;
			this.expires = expires;
		}

		private boolean isExpired() {
			return get() == null || Instant.now().isAfter(expires);
		}
	}

	private abstract class CacheCollection<T> implements Collection<T> {

		@Override
		public int size() {
			return CacheMap.this.size();
		}

		@Override
		public boolean isEmpty() {
			return CacheMap.this.isEmpty();
		}

		@Override
		public boolean add(T e) {
			throw new UnsupportedOperationException();
		}

		@Override
		public boolean addAll(Collection<? extends T> c) {
			throw new UnsupportedOperationException();
		}

		@Override
		public void clear() {
			CacheMap.this.clear();
		}
	}

	private class CacheEntry implements Entry<K, V> {

		private final Entry<Object, CacheReference<V>> entry;
		@SuppressWarnings("unused")
		private V value; // hard ref

		private CacheEntry(Entry<Object, CacheReference<V>> entry) {
			this.entry = entry;
			this.value = entry.getValue().get();
		}

		@SuppressWarnings("unchecked")
		@Override
		public K getKey() {
			return (K) entry.getKey();
		}

		@Override
		public V getValue() {
			V value = entry.getValue().get();
			if (value == NULL)
				return null;
			else
				return value;
		}

		@SuppressWarnings("unchecked")
		@Override
		public V setValue(V value) {
			V oldValue = getValue();
			entry.setValue(new CacheReference<>(getKey(), value == null ? (V) NULL : value,
					Instant.now().plus(cacheTimeToLive), refQ));
			this.value = value;
			return oldValue;
		}

		@Override
		public int hashCode() {
			// matches HashMap.Entry#hashCode
			return Objects.hashCode(getKey()) ^ Objects.hashCode(getValue());
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (!(obj instanceof Entry))
				return false;
			Entry<?, ?> other = (Entry<?, ?>) obj;
			return Objects.equals(getKey(), other.getKey()) && Objects.equals(getValue(), other.getValue());
		}
	}

	private class CacheEntrySet extends CacheCollection<Entry<K, V>> implements Set<Entry<K, V>> {

		@Override
		public boolean contains(Object o) {
			if (!(o instanceof Entry))
				return false;

			Entry<?, ?> e = (Entry<?, ?>) o;
			if (!containsKey(e.getKey()))
				return false;

			Object value = get(e.getKey());
			Object otherValue = e.getValue();
			return value == otherValue || (value != null && value.equals(otherValue));
		}

		@Override
		public Iterator<Entry<K, V>> iterator() {
			return new CacheIterator<Entry<K, V>>() {
				@Override
				protected Entry<K, V> resolve(Entry<Object, CacheReference<V>> entry) {
					return new CacheEntry(entry);
				}
			};
		}

		@Override
		public Object[] toArray() {
			return toArray(O0);
		}

		@SuppressWarnings("unchecked")
		@Override
		public <T> T[] toArray(T[] a) {
			List<T> l = new ArrayList<T>();
			synchronized (cache) {
				Iterator<Entry<K, V>> i = iterator();
				while (i.hasNext())
					l.add((T) i.next());
			}
			return l.toArray(a);
		}

		@Override
		public boolean remove(Object o) {
			if (o == null)
				return false;
			boolean changed = false;
			synchronized (cache) {
				Iterator<Entry<K, V>> i = iterator();
				while (i.hasNext())
					if (i.next().equals(o)) {
						i.remove();
						changed = true;
					}
			}
			return changed;
		}

		@Override
		public boolean containsAll(Collection<?> c) {
			if (c == null || c.isEmpty())
				return true;
			Set<Object> s = new HashSet<>(c);
			synchronized (cache) {
				Iterator<Entry<K, V>> i = iterator();
				while (i.hasNext())
					s.remove(i.next());
			}
			return s.isEmpty();
		}

		@Override
		public boolean removeAll(Collection<?> c) {
			if (c == null)
				return false;
			boolean changed = false;
			synchronized (cache) {
				Iterator<Entry<K, V>> i = iterator();
				while (i.hasNext())
					if (c.contains(i.next())) {
						i.remove();
						changed = true;
					}
			}
			return changed;
		}

		@Override
		public boolean retainAll(Collection<?> c) {
			if (c == null)
				return false;
			boolean changed = false;
			synchronized (cache) {
				Iterator<Entry<K, V>> i = iterator();
				while (i.hasNext())
					if (!c.contains(i.next())) {
						i.remove();
						changed = true;
					}
			}
			return changed;
		}
	}

	private abstract class CacheIterator<T> implements Iterator<T> {

		private final Iterator<Entry<Object, CacheReference<V>>> i;
		private Entry<Object, CacheReference<V>> current;
		@SuppressWarnings("unused")
		private V value; // hard ref to prevent GC from between hasNext() and next()

		private CacheIterator() {
			purge();
			i = cache.entrySet().iterator();
		}

		protected abstract T resolve(Entry<Object, CacheReference<V>> ref);

		@Override
		public boolean hasNext() {
			if (!i.hasNext())
				return current != null && !current.getValue().isExpired();
			do {
				if (current != null && !current.getValue().isExpired())
					return true;
				current = i.next();
				value = current.getValue().get();
			} while (i.hasNext());
			return current != null && !current.getValue().isExpired();
		}

		@Override
		public T next() {
			if (hasNext()) {
				T rv = resolve(current);
				value = null;
				current = null;
				return rv;
			} else
				throw new NoSuchElementException();
		}

		@Override
		public void remove() {
			i.remove();
		}
	}

	private class CacheKeySet extends CacheCollection<K> implements Set<K> {

		@Override
		public boolean contains(Object o) {
			return CacheMap.this.containsKey(o);
		}

		@Override
		public Iterator<K> iterator() {
			return new CacheIterator<K>() {
				@SuppressWarnings("unchecked")
				@Override
				protected K resolve(Entry<Object, CacheReference<V>> entry) {
					return (K) entry.getKey();
				}
			};
		}

		@Override
		public Object[] toArray() {
			purge();
			synchronized (cache) {
				return cache.keySet().toArray();
			}
		}

		@Override
		public <T> T[] toArray(T[] a) {
			purge();
			synchronized (cache) {
				return cache.keySet().toArray(a);
			}
		}

		@Override
		public boolean remove(Object o) {
			purge();
			return cache.keySet().remove(o);
		}

		@Override
		public boolean containsAll(Collection<?> c) {
			purge();
			return cache.keySet().containsAll(c);
		}

		@Override
		public boolean retainAll(Collection<?> c) {
			purge();
			return cache.keySet().retainAll(c);
		}

		@Override
		public boolean removeAll(Collection<?> c) {
			purge();
			return cache.keySet().removeAll(c);
		}
	}

	private class CacheValues extends CacheCollection<V> implements Collection<V> {

		@Override
		public boolean contains(Object o) {
			if (o == null)
				return false;
			synchronized (cache) {
				Iterator<V> i = iterator();
				while (i.hasNext())
					if (o.equals(i.next()))
						return true;
			}
			return false;
		}

		@Override
		public Iterator<V> iterator() {
			return new CacheIterator<V>() {
				@Override
				protected V resolve(Entry<Object, CacheReference<V>> entry) {
					return entry.getValue().get();
				}
			};
		}

		@Override
		public Object[] toArray() {
			return toArray(O0);
		}

		@SuppressWarnings("unchecked")
		@Override
		public <T> T[] toArray(T[] a) {
			List<T> l = new ArrayList<T>();
			synchronized (cache) {
				Iterator<V> i = iterator();
				while (i.hasNext())
					l.add((T) i.next());
			}
			return l.toArray(a);
		}

		@Override
		public boolean remove(Object o) {
			if (o == null)
				return false;
			boolean changed = false;
			synchronized (cache) {
				Iterator<V> i = iterator();
				while (i.hasNext())
					if (o.equals(i.next())) {
						i.remove();
						changed = true;
					}
			}
			return changed;
		}

		@Override
		public boolean containsAll(Collection<?> c) {
			if (c == null || c.isEmpty())
				return true;
			Set<Object> s = new HashSet<>(c);
			synchronized (cache) {
				Iterator<V> i = iterator();
				while (i.hasNext())
					s.remove(i.next());
			}
			return s.isEmpty();
		}

		@Override
		public boolean removeAll(Collection<?> c) {
			if (c == null)
				return false;
			boolean changed = false;
			synchronized (cache) {
				Iterator<V> i = iterator();
				while (i.hasNext())
					if (c.contains(i.next())) {
						i.remove();
						changed = true;
					}
			}
			return changed;
		}

		@Override
		public boolean retainAll(Collection<?> c) {
			if (c == null)
				return false;
			boolean changed = false;
			synchronized (cache) {
				Iterator<V> i = iterator();
				while (i.hasNext())
					if (!c.contains(i.next())) {
						i.remove();
						changed = true;
					}
			}
			return changed;
		}
	}

	private void purge() {
		if (Duration.between(lastPurge, Instant.now()).compareTo(purgeInterval) < 0)
			return;

		synchronized (cache) {
			CacheReference<? extends V> ref;
			while ((ref = (CacheReference<? extends V>) refQ.poll()) != null)
				cache.remove(ref.key);

			Iterator<CacheReference<V>> cacheEntryIterator = cache.values().iterator();
			while (cacheEntryIterator.hasNext()) {
				ref = cacheEntryIterator.next();
				if (ref.isExpired())
					cacheEntryIterator.remove();
			}
		}

		lastPurge = Instant.now();
	}

	private final ReferenceQueue<V> refQ = new ReferenceQueue<>();
	private final Map<Object, CacheReference<V>> cache = new HashMap<>();
	private final Duration cacheTimeToLive;
	private final Duration purgeInterval;
	private CacheKeySet keySet;
	private CacheValues values;
	private CacheEntrySet entrySet;
	private Instant lastPurge = Instant.now();

	/**
	 * Constructor.
	 * 
	 * @param cacheTimeToLive maximum time to live for cache entries
	 * @param purgeInterval   minimum time between inline purge operations
	 */
	public CacheMap(Duration cacheTimeToLive, Duration purgeInterval) {
		this.cacheTimeToLive = cacheTimeToLive;
		this.purgeInterval = purgeInterval;
	}

	@Override
	public int size() {
		purge();
		return cache.size();
	}

	@Override
	public boolean isEmpty() {
		purge();
		return cache.isEmpty();
	}

	@Override
	public boolean containsKey(Object key) {
		purge();
		return cache.containsKey(key) && get(key) != null;
	}

	@Override
	public boolean containsValue(Object value) {
		if (value == null)
			return false;
		purge();
		synchronized (cache) {
			for (CacheReference<V> ref : cache.values())
				if (value.equals(ref))
					return true;
		}
		return false;
	}

	@Override
	public V get(Object key) {
		purge();
		CacheReference<V> ref = cache.get(key);
		if (ref == null)
			return null;

		if (ref.isExpired())
			synchronized (cache) {
				cache.remove(key);
				return null;
			}

		V rv = ref.get();
		if (rv == NULL)
			return null;
		else
			return rv;
	}

	@SuppressWarnings("unchecked")
	@Override
	public V put(K key, V value) {
		purge();
		if (value == null)
			value = (V) NULL;

		CacheReference<V> ref = new CacheReference<>(key, value, Instant.now().plus(cacheTimeToLive), refQ);
		synchronized (cache) {
			ref = cache.put(key, ref);
		}

		V rv = ref == null ? null : ref.get();
		if (rv == NULL)
			return null;
		else
			return rv;
	}

	@Override
	public V remove(Object key) {
		purge();

		CacheReference<V> ref;
		synchronized (cache) {
			ref = cache.remove(key);
		}

		V rv = ref.get();
		if (rv == NULL)
			return null;
		else
			return rv;
	}

	@Override
	public void putAll(Map<? extends K, ? extends V> m) {
		if (m == null)
			return;

		purge();
		synchronized (cache) {
			m.forEach((key, value) -> {
				@SuppressWarnings("unchecked")
				CacheReference<V> ref = new CacheReference<>(key, value == null ? (V) NULL : value,
						Instant.now().plus(cacheTimeToLive), refQ);
				ref = cache.put(key, ref);
			});
		}
	}

	@Override
	public void clear() {
		synchronized (cache) {
			cache.clear();
		}
	}

	@Override
	public Set<K> keySet() {
		if (keySet == null)
			keySet = new CacheKeySet();
		return keySet;
	}

	@Override
	public Collection<V> values() {
		if (values == null)
			values = new CacheValues();
		return values;
	}

	@Override
	public Set<Entry<K, V>> entrySet() {
		if (entrySet == null)
			entrySet = new CacheEntrySet();
		return entrySet;
	}

}
