# NIWE license tool

For NIWE only. Never ship this folder, the private key or the register to a customer. Needs Java 17 or later; nothing to build.

```bash
# once: make the key pair. Keep the folder OUTSIDE the repository and back it up in two places.
java LicenseTool.java keygen --dir ~/niwe-license-keys
# paste the printed line into backend/src/main/resources/license/public-key.b64 and rebuild the application

# for a customer: they send you the installation id shown on Settings > Modules
java LicenseTool.java issue --key ~/niwe-license-keys/niwe-license-private.key \
     --customer "Acme Ltd" --installation 1b9d6bcd-bbfd-4b2d-9b5d-ab8dfbbd4bed \
     --modules POS,HOTEL,PAYROLL [--expires 2027-12-31] [--register ~/niwe-license-keys/licenses.csv]

java LicenseTool.java verify --pub ~/niwe-license-keys/niwe-license-public.b64 acme-ltd-1.niwelic
java LicenseTool.java modules
```

- `issue` adds the modules each module needs (Hotel brings POS and Restaurant, and so on) and says so. Core modules are never listed.
- Each license gets a number (1, 2, 3, ...) per installation, taken from the register (`licenses.csv`, semicolon separated: license id, number, customer, installation id, issue date, expiry, modules). A new license must always have a higher number than the installed one, so **issue the next license for a customer from the same register**. Keep the register with the keys.
- Send the customer the `.niwelic` file; they upload it on Settings > Modules.
- Losing the private key means a new key pair, a new application build and new licenses for every customer.
- The module list in `LicenseTool.java` must match `ModuleKey.java` in the backend. When a module is added to the application, add it here too.
