# Contributing to NETHAL

NETHAL is a network hardware abstraction project. Contributions must be safe, documented and respectful of user authorization.

## Core rules

- Do not bypass authentication.
- Do not include exploits.
- Do not brute-force credentials.
- Do not store router passwords.
- Do not collect sensitive personal data.
- Do not send credentials to cloud services.
- Do not add write actions without a safety review.

## Driver contributions

NETHAL follows a layered HAL architecture, frozen in `docs/architecture/hal-layering-model.md`:

```text
Vendor → Platform → Protocol → Authentication Strategy → Driver Family → Profile → Capability
```

**Before writing any driver code, check whether an existing Driver Family already covers the
protocol/authentication mechanism your target device uses.** If it does, your contribution is a
new `Profile` in the compatibility catalog (data only, no Kotlin) — see
`docs/drivers/compatibility-catalog.md`. A new Driver Family (new Kotlin code under
`core/driver/family/<vendor>/<família>/`) is only justified when the protocol or authentication
mechanism is genuinely new — see the decision table in `hal-layering-model.md` §9. Do not organize
new drivers by vendor+model; that duplicates protocol/authentication logic per model instead of per
platform.

Every driver (or, for a new model reusing an existing Driver Family, every new Profile) must document:

- Vendor
- Platform / Driver Family it belongs to (existing, or new — with justification per §9)
- Tested models
- Tested firmware versions
- Protocol used
- Authentication method
- Supported capabilities
- Known limitations
- Safety risks

## Driver stages

```text
DRAFT
DISCOVERY_ONLY
READ_ONLY_ALPHA
READ_ONLY_BETA
WRITE_BETA
STABLE
DEPRECATED
BLOCKED
```
