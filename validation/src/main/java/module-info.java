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
/**
 * Deep bean validation over the
 * <a href="https://jakarta.ee/specifications/bean-validation/3.1/">Jakarta
 * Validation</a> constraint vocabulary.
 * 
 * <p>
 * Walks a business object graph by bean property, evaluating every declared
 * constraint and collecting <em>all</em> failures into a single
 * {@link edu.iu.validation.IuValidationResult}, rather than throwing at the
 * first invalid value. The constraint annotations are consumed as a
 * vocabulary only: no {@link jakarta.validation.Validator},
 * {@link jakarta.validation.ValidatorFactory}, or XML configuration is
 * involved.
 * </p>
 * 
 * <p>
 * {@code iu.util.type} is an optional dependency. When it is resolved
 * <em>and</em> an implementation service provider initializes, generic type
 * arguments are resolved through the full type hierarchy; otherwise an
 * equivalent reflective introspector is used. Neither the API nor the results
 * differ between the two.
 * </p>
 */
module iu.util.validation {
	exports edu.iu.validation;

	requires iu.util;
	requires java.desktop;
	requires java.logging;
	requires transitive jakarta.validation;

	requires static iu.util.type;
}
