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
package iu.client;

/**
 * Tracks the item a collection, array, or map conversion is working on, so a
 * failure can name it.
 *
 * <p>
 * Each item is entered before it converts and exited after, whether or not it
 * failed; a failure passes through {@link #fail(boolean, RuntimeException)}
 * while the item is still entered.
 * </p>
 */
public interface ItemScope {

	/**
	 * Tracks nothing.
	 */
	ItemScope NONE = new ItemScope() {
		@Override
		public void enterIndex(boolean writing, int index) {
		}

		@Override
		public void enterKey(boolean writing, String key) {
		}

		@Override
		public RuntimeException fail(boolean writing, RuntimeException failure) {
			return failure;
		}

		@Override
		public void exit(boolean writing) {
		}
	};

	/**
	 * Enters an array or collection item.
	 *
	 * @param writing true when writing JSON; false when reading it
	 * @param index   item's position, from 0
	 */
	void enterIndex(boolean writing, int index);

	/**
	 * Enters a map entry.
	 *
	 * @param writing true when writing JSON; false when reading it
	 * @param key     entry's JSON name
	 */
	void enterKey(boolean writing, String key);

	/**
	 * Records a failure converting the item entered.
	 *
	 * @param writing true when writing JSON; false when reading it
	 * @param failure failure
	 * @return {@code failure}, to rethrow
	 */
	RuntimeException fail(boolean writing, RuntimeException failure);

	/**
	 * Exits the item most recently entered.
	 *
	 * @param writing true when writing JSON; false when reading it
	 */
	void exit(boolean writing);

}
