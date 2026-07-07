# Security Policy

NETHAL interacts with network equipment. Security is not optional.

## Forbidden behavior

NETHAL must not:

- bypass authentication
- exploit vulnerabilities
- brute-force passwords
- use default credentials automatically
- store router passwords permanently
- transmit router credentials to servers
- enable remote access without consent
- perform destructive actions silently

## Sensitive data handling

Do not collect:

- router admin password
- Wi-Fi password
- full client MAC addresses
- full public IP address
- SSID without explicit consent
- personal device names unless anonymized

## Catalog integrity

`CompatibilityProfile.driverConfig` (see `docs/drivers/compatibility-catalog.md`) carries the
section/field bundles a Driver Family sends verbatim in authenticated requests to the device (e.g.
`TpLinkLegacyCgiDriverFamily`). Today the catalog is 100% embedded/local (`RemoteCatalogSource` is a
no-op, see `DriverRegistry.kt`), so there is no network surface for this to be tampered with.

`RemoteCatalogSource` sync against a real backend is a planned product feature (spec §8.5), not
hypothetical. Before it gets a real implementation, this must exist:

- the remote manifest must be signed/verified before being accepted as a source of `driverConfig`;
- no Driver Family may send a section/field pulled from `driverConfig` without checking it against
  an allowlist of sections/fields that Driver Family already knows about for its protocol — a
  malicious or corrupted manifest must never be able to inject an arbitrary section/field into an
  authenticated request against the user's own router.

This is not a bypass-auth or credential-exfiltration risk by itself, but it is a "blind command
sourced from untrusted remote data" surface that does not exist yet only because the catalog has no
remote source yet.
