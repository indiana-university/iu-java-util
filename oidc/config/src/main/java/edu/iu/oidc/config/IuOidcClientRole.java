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
package edu.iu.oidc.config;

import java.util.List;

/**
 * Maps a role a client endpoint may act in onto the identity roles that entitle
 * an end user to it.
 *
 * <p>
 * The two are not the same thing. {@link #getRole()} is the name the client's
 * application knows &mdash; what it checks a user's role by &mdash; while
 * {@link #getIdRoles()} names the roles the identity system actually grants.
 * Keeping the mapping here lets an application's role names stay stable while
 * the enterprise roles behind them are reorganized, and lets two clients use
 * the same name for different entitlements.
 * </p>
 *
 * <p>
 * Property names use lower case with underscores, so {@link #getIdRoles()}
 * reads {@code id_roles}.
 * </p>
 */
public interface IuOidcClientRole {

	/**
	 * Gets the role name the client's application uses.
	 *
	 * @return application role name
	 */
	String getRole();

	/**
	 * Gets the identity roles that entitle an end user to {@link #getRole()}.
	 *
	 * <p>
	 * Holding any one of them is enough; an end user holding none does not act in
	 * this role.
	 * </p>
	 *
	 * @return identity role names
	 */
	List<String> getIdRoles();

}
