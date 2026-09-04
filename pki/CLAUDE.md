# CLAUDE.md — pki

`iu-java-pki-api`, `iu-java-pki-impl` / modules `iu.util.pki`, `iu.util.pki.impl`

Read the repository root `CLAUDE.md` first for build commands and shared conventions.

## Role

X.509 trust verification expressed in terms of `crypt`'s `WebKey`. Turns a key with a certificate chain into a verified `java.security.Principal`.

Compiled with `--release 11`. `api` re-exports `iu.util.crypt` transitively.

## Layout

Unlike its siblings, this pair does **not** use a `ServiceLoader` SPI. `iu.util.pki.impl` simply `exports iu.pki` and consumers instantiate the verifiers directly.

| Module | Contents |
|---|---|
| `api` | `IuPkiVerifier`, `IuPkiPrincipal`, `IuCertificateAuthority`, `KeyUsage` |
| `impl` | `CaVerifier`, `SelfSignedVerifier`, `PkiPrincipal` |

## API surface

- `IuPkiVerifier` — verifies a `WebKey` contains a trusted certificate chain. Two implementations: `CaVerifier` (chain to a configured `IuCertificateAuthority`) and `SelfSignedVerifier`.
- `IuPkiPrincipal extends Principal` — a principal backed by a verified `WebKey` and chain.
- `IuCertificateAuthority` — supplies CA public key material for chain validation.
- `KeyUsage` — decodes the X.509 Key Usage extension bit string into a usable form.

## Editing notes

A verifier decides trust; treat any change to `CaVerifier` or `SelfSignedVerifier` as security-relevant. New rejection paths need explicit negative tests, both because the coverage gate requires the branch and because a verifier that silently accepts is the failure mode that matters here.
