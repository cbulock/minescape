# Automatic Google Play internal releases

The user selected automatic uploads after successful changes on `main`. Keep production promotion in Play Console. Retain the private `cbulock/minescape-builds` repository for AABs and credentials.

## Approaches

- Manual release button: explicit timing, but repeats a step for every update.
- Automatic internal releases (selected): the existing private workflow polls `main` every 30 minutes, waits for its public APK checks to succeed, then builds, signs, and publishes that source commit to internal testing. Closely spaced commits can be combined into the latest successful main build.
- Automatic production: unnecessary for the requested internal testing setup.

## Design and constraints

Use the existing Git-derived version and Android bundle build. Run unit tests and release lint before signing. Sign with the registered upload keystore only in the private repository. Archive AAB, checksum, and R8 mapping there. Use Google's Python authentication library and Developer API to publish only `com.cbulock.minescape` / `internal`.

Keep unsigned private builds working while setup is incomplete. Enable uploads using `PLAY_UPLOAD_ENABLED=true` only after configuring the keystore, passwords, service account, and expected upload certificate fingerprint. A missing credential after enabling is an error. Do not expose credentials in logs or commit them.

Retries inspect Play before changing anything: reuse an identical existing bundle, recognize an already completed internal release, reject version rollback or a different bundle with the same version. Mark the private release only after Play commits successfully. Production and store listing changes remain outside automation.

## Verification and setup boundary

Exercise API behavior with controlled responses, lint the workflows, run the publisher tests in public CI, and run Android unit tests/release lint/build locally. A real Play upload needs the user's existing keystore and a service account granted testing permissions in Play Console. Document and report that boundary explicitly.
