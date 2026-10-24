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
 * {@link edu.iu.validation.IuValidator} walks a business object graph by bean
 * property, evaluates every constraint annotation it finds, and collects
 * <em>all</em> failures into one {@link edu.iu.validation.IuValidationResult}.
 * Nothing short-circuits: a single pass reports every property that failed and
 * which rule each value failed on.
 * </p>
 * 
 * <pre>
 * final var result = IuValidator.validate(EnrlHeader.class, header);
 * if (!result.isValid())
 * 	LOG.warning(result.report());
 * </pre>
 * 
 * <p>
 * The constraint annotations are read as a vocabulary only. This is not a
 * {@link jakarta.validation.Validator} implementation: there is no
 * {@link jakarta.validation.ValidatorFactory}, no {@code validation.xml}, and
 * no provider discovery.
 * </p>
 * 
 * <h2>What is validated</h2>
 * 
 * <p>
 * Bean properties are discovered exactly as
 * {@code edu.iu.client.IuJsonAdapter} discovers them for JSON binding &mdash;
 * the full interface and superclass hierarchy, nearest declaration winning on
 * name collision &mdash; so the set of validated properties always matches the
 * set of serialized properties. Constraints are honored on the read method, the
 * write method, and the backing field.
 * </p>
 * 
 * <p>
 * {@link jakarta.validation.Valid} cascades into a property value. Cascading
 * descends {@link java.lang.Iterable}, arrays, {@link java.util.Map} keys and
 * values, and {@link java.util.Optional}. Object graph cycles terminate on
 * instance identity rather than overflowing the stack.
 * </p>
 * 
 * <h2>Standalone annotation targets</h2>
 *
 * <p>
 * {@link edu.iu.validation.IuValidator#validate(java.lang.reflect.AnnotatedElement, Object)}
 * validates a value the caller already holds against the constraints on the
 * target that declares it &mdash; a field, the return value of a getter, a
 * method argument &mdash; with no bean to walk from. Only that target's own
 * annotations apply; nothing is unioned from the enclosing type.
 * </p>
 *
 * <pre>
 * IuValidator.require(parameter, "message", argument);
 * </pre>
 *
 * <p>
 * A {@link java.lang.Class} target is validated as a bean instead, so the
 * generic entry point is a superset of the type-based one rather than a
 * parallel path. Container element constraints and
 * {@link jakarta.validation.Valid} cascade behave exactly as they do during a
 * bean walk.
 * </p>
 *
 * <h2>Groups</h2>
 * 
 * <p>
 * Only the default group is evaluated. A constraint that declares no
 * {@code groups()} belongs to {@link jakarta.validation.groups.Default} and is
 * evaluated; a constraint that names an explicit group is not applicable to the
 * default group and is skipped. {@code payload()} is exposed through
 * {@link edu.iu.validation.IuConstraintViolation#attributes()} and otherwise
 * ignored.
 * </p>
 */
package edu.iu.validation;
