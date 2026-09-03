# Keycloak client registration

What an OAuth2 client looks like for a real consumer solution on the FUT platform. This covers the configuration a vendor needs to request. For the reference client's own setup, see [connect-to-test.md](connect-to-test.md).

## Realm

| App              | Realm      | Keycloak base URL                       | Issuer path             |
|------------------|------------|-----------------------------------------|-------------------------|
| Clinician-facing | `ehealth`  | `https://saml.<env>.ehealth.sundhed.dk` | `/auth/realms/ehealth`  |
| Citizen-facing   | `nemlogin` | `https://saml.<env>.ehealth.sundhed.dk` | `/auth/realms/nemlogin` |

Replace `<env>` with the target environment name (e.g. `devenvcgi`, `inttest`, `test001`).
Both realms are served from the same Keycloak host.

## Client type

**Clinician client: confidential.** Keycloak issues a client secret. The backend exchanges the authorization code for tokens server-side, so it can safely hold that secret. This uses Spring Security's `authorization_code` grant with a client secret.

**Citizen client: public.** No client secret is issued. The reference client is still a server-side app, so the code exchange happens server-side too. Spring Security uses PKCE automatically for public clients.

## Redirect URIs

Spring Security generates redirect URIs in the form:

```
<scheme>://<host>/login/oauth2/code/<registration-id>
```

For the reference apps the registration IDs are `clinician` and `citizen`:

```
http://localhost:8080/login/oauth2/code/clinician   (clinician, local dev)
http://localhost:8090/login/oauth2/code/citizen     (citizen, local dev)
```

Register your deployment URI alongside the local dev URI (or replace it when moving to production).

## Other settings

- Refresh tokens: enabled. The clinician app uses the refresh-token-with-context flow to renew the ehealth-context claims alongside the access token.
- Post-logout redirect URI: register `<scheme>://<host>` if your client initiates OIDC RP-initiated logout.

## Sending the registration request

Contact your FUT platform team representative with the realm, client name, client type,
requested scopes, and redirect URIs.

## FHIR Implementation Guide

The full FUT FHIR profile definitions, CapabilityStatements, and operation definitions are
published at [github.com/fut-infrastructure/implementation-guide](https://github.com/fut-infrastructure/implementation-guide).
This is the canonical reference for FHIR resource shapes, required extensions, and
operation parameters beyond what the reference clients demonstrate.
