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
package edu.iu.type.testresources;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Type variable names that collide across scopes, e.g., a method's {@code T}
 * referred to through {@code List<E>}, {@code Collection<E>}, and
 * {@code Iterable<T>}.
 */
@SuppressWarnings({ "javadoc", "unused" })
public class TypeVariableScopeSupport {

	public static class ReturnsList {
		public static <T> List<T> f() {
			return null;
		}
	}

	public static class ReturnsCollection {
		public static <T> Collection<T> f() {
			return null;
		}
	}

	public static class ReturnsArrayList {
		public static <T> ArrayList<T> f() {
			return null;
		}
	}

	public static class AcceptsList {
		public static <T> void f(List<T> x) {
		}
	}

	public static class ListsIterable {
		public static <T> List<T> list(Iterable<? extends T> x) {
			return null;
		}
	}

	// same shape as edu.iu.dao.IuEntityDao<K, I, T>
	public interface EntityDao<K, I, T> {
		void selectAndPopulate(List<T> found);

		List<T> select(K key);
	}

	public static class Pair<A, B> {
	}

	public static class Swapper<A, B> {
		public Pair<B, A> swap() {
			return null;
		}
	}

	// method and constructor T shadow the class T, with different bounds
	public static class Shadow<T extends Number> {
		public <T extends CharSequence> Shadow(T t) {
		}

		public <T extends CharSequence> T shadow(T t) {
			return t;
		}
	}

	public static class IntegerShadow extends Shadow<Integer> {
		public IntegerShadow() {
			super("");
		}
	}

}
