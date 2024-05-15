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
package edu.iu;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import java.util.Spliterator;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Caching {@link Map} implementation backed by {@link IuCachedValue}.
 * 
 * @param <K> key type
 * @param <V> value type
 */
public class IuCacheMap<K, V> implements Map<K, V> {

	private static final Object[] O0 = new Object[0];

	private static class CacheIterator<K, V, T> implements Iterator<T> {
		private final Iterator<Entry<K, IuCachedValue<V>>> iterator;
		private final Function<Entry<K, IuCachedValue<V>>, T> transform;
		private Entry<K, IuCachedValue<V>> current;

		// ensures GC doesn't clear reference between hasNext() and next()
		@SuppressWarnings("unused")
		private V hardRef;

		private CacheIterator(Iterator<Entry<K, IuCachedValue<V>>> iterator,
				Function<Entry<K, IuCachedValue<V>>, T> transform) {
			this.transform = transform;
			this.iterator = iterator;
		}

		@Override
		public boolean hasNext() {
			if (current != null)
				return true;

			while (iterator.hasNext()) {
				final var current = iterator.next();
				final var ref = current.getValue();
				final var hardRef = ref.get();
				if (ref.isValid()) {
					this.current = current;
					this.hardRef = hardRef;
					return true;
				}
			}

			return false;
		}

		@Override
		public T next() {
			if (hasNext()) {
				T rv = transform.apply(current);
				hardRef = null;
				current = null;
				return rv;
			} else
				throw new NoSuchElementException();
		}

		@Override
		public void remove() {
			iterator.remove();
		}
	}

	private static class CacheSpliterator<K, V, T> implements Spliterator<T> {
		private final Spliterator<Entry<K, IuCachedValue<V>>> split;
		private final Function<Entry<K, IuCachedValue<V>>, T> transform;

		private CacheSpliterator(Spliterator<Entry<K, IuCachedValue<V>>> split,
				Function<Entry<K, IuCachedValue<V>>, T> transform) {
			this.split = split;
			this.transform = transform;
		}

		@Override
		public boolean tryAdvance(Consumer<? super T> action) {
			class Box {
				private boolean found;
			}
			final var box = new Box();
			while (!box.found)
				if (!split.tryAdvance(entry -> {
					final var ref = entry.getValue();
					@SuppressWarnings("unused")
					final var hardRef = ref.get();
					if (box.found = ref.isValid())
						action.accept(transform.apply(entry));
				}))
					return false;

			return true;
		}

		@Override
		public Spliterator<T> trySplit() {
			final var split = this.split.trySplit();
			if (split == null)
				return null;
			else
				return new CacheSpliterator<>(split, transform);
		}

		@Override
		public long estimateSize() {
			return split.estimateSize();
		}

		@Override
		public int characteristics() {
			return split.characteristics();
		}
	}

	private abstract class CacheCollection<T> implements Collection<T> {
		protected abstract T transform(Entry<K, IuCachedValue<V>> entry);

		protected abstract Stream<Entry<K, IuCachedValue<V>>> findEntries(Object value);

		@Override
		public int size() {
			return IuCacheMap.this.size();
		}

		@Override
		public boolean isEmpty() {
			return IuCacheMap.this.isEmpty();
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
		public Iterator<T> iterator() {
			return new CacheIterator<>(cache.entrySet().iterator(), this::transform);
		}

		@Override
		public Spliterator<T> spliterator() {
			return new CacheSpliterator<>(cache.entrySet().spliterator(), this::transform);
		}

		@Override
		public Object[] toArray() {
			return toArray(O0);
		}

		@Override
		public <U> U[] toArray(U[] a) {
			final var q = new ArrayDeque<>();
			final var i = iterator();
			while (i.hasNext())
				q.offer(i.next());
			return q.toArray(a);
		}

		@Override
		public boolean contains(Object o) {
			return findEntries(o).findAny().isPresent();
		}

		@Override
		public boolean remove(Object o) {
			final var entry = findEntries(o).findAny();
			if (entry.isEmpty())
				return false;

			final var removed = entry.get();
			cache.remove(entry.get().getKey());
			removed.getValue().clear();
			return true;
		}

		@Override
		public boolean containsAll(Collection<?> c) {
			return c.parallelStream().allMatch(this::contains);
		}

		@Override
		public boolean removeAll(Collection<?> c) {
			class Box {
				boolean removed;
			}
			final var box = new Box();
			c.parallelStream().forEach(a -> {
				if (remove(a))
					box.removed = true;
			});
			return box.removed;
		}

		@Override
		public boolean retainAll(Collection<?> c) {
			return cache.entrySet().retainAll(
					c.parallelStream().flatMap(this::findEntries).filter(Objects::nonNull).collect(Collectors.toSet()));
		}

		@Override
		public void clear() {
			IuCacheMap.this.clear();
		}
	}

	private class CacheEntry implements Entry<K, V> {
		private final Entry<K, IuCachedValue<V>> entry;
		private V value;

		private CacheEntry(Entry<K, IuCachedValue<V>> entry) {
			this.entry = entry;
			this.value = entry.getValue().get();
		}

		@Override
		public K getKey() {
			return entry.getKey();
		}

		@Override
		public V getValue() {
			return value;
		}

		@Override
		public V setValue(V value) {
			K key = getKey();
			V oldValue = this.value;
			entry.setValue(ref(key, value));
			this.value = value;
			return oldValue;
		}

		@Override
		public int hashCode() {
			// matches HashMap.Entry#hashCode
			return Objects.hashCode(getKey()) ^ Objects.hashCode(value);
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (!(obj instanceof Entry))
				return false;
			Entry<?, ?> other = (Entry<?, ?>) obj;
			return IuObject.equals(getKey(), other.getKey()) && entry.getValue().has(other.getValue());
		}
	}

	private class CacheEntrySet extends CacheCollection<Entry<K, V>> implements Set<Entry<K, V>> {
		@Override
		protected Entry<K, V> transform(Entry<K, IuCachedValue<V>> entry) {
			return new CacheEntry(entry);
		}

		@Override
		protected Stream<Entry<K, IuCachedValue<V>>> findEntries(Object value) {
			if (value instanceof Entry) {
				final var entry = (Entry<?, ?>) value;
				return cache.entrySet().parallelStream().filter(//
						a -> a.getKey().equals(entry.getKey()) //
								&& a.getValue().has(entry.getValue()));
			} else
				return Stream.empty();
		}
	}

	private class CacheKeySet extends CacheCollection<K> implements Set<K> {
		@Override
		protected K transform(Entry<K, IuCachedValue<V>> entry) {
			return entry.getKey();
		}

		@Override
		protected Stream<Entry<K, IuCachedValue<V>>> findEntries(Object value) {
			return cache.entrySet().parallelStream().filter(a -> a.getKey().equals(value));
		}
	}

	private class CacheValues extends CacheCollection<V> {
		@Override
		protected V transform(Entry<K, IuCachedValue<V>> entry) {
			return entry.getValue().get();
		}

		@Override
		protected Stream<Entry<K, IuCachedValue<V>>> findEntries(Object value) {
			return cache.entrySet().parallelStream().filter(a -> a.getValue().has(value));
		}

	}

	private final Map<K, IuCachedValue<V>> cache = new ConcurrentHashMap<>();
	private final Duration cacheTimeToLive;
	private CacheKeySet keySet;
	private CacheValues values;
	private CacheEntrySet entrySet;

	/**
	 * Constructor.
	 * 
	 * @param cacheTimeToLive maximum time to live for cache entries
	 */
	public IuCacheMap(Duration cacheTimeToLive) {
		this.cacheTimeToLive = cacheTimeToLive;
	}

	@Override
	public int size() {
		return cache.size();
	}

	@Override
	public boolean isEmpty() {
		return cache.isEmpty();
	}

	@Override
	public boolean containsKey(Object key) {
		return cache.containsKey(key);
	}

	@Override
	public boolean containsValue(Object value) {
		return cache.values().parallelStream().anyMatch(a -> a.has(value));
	}

	@Override
	public V get(Object key) {
		final var ref = cache.get(key);
		if (ref == null)
			return null;
		else
			return ref.get();
	}

	@Override
	public V put(K key, V value) {
		final var ref = cache.get(key);
		final V rv;
		if (ref == null)
			rv = null;
		else {
			rv = ref.get();
			ref.clear();
		}
		cache.put(key, ref(key, value));
		return rv;
	}

	@Override
	public V remove(Object key) {
		final var ref = cache.remove(key);
		if (ref == null)
			return null;
		else {
			final var rv = ref.get();
			ref.clear();
			return rv;
		}
	}

	@Override
	public void putAll(Map<? extends K, ? extends V> m) {
		m.forEach((k, v) -> cache.put(k, ref(k, v)));
	}

	@Override
	public void clear() {
		cache.values().forEach(IuCachedValue::clear);
		cache.clear();
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

	private IuCachedValue<V> ref(K key, V value) {
		return new IuCachedValue<>(value, cacheTimeToLive, () -> cache.remove(key));
	}

}
