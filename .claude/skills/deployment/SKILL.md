---
name: deployment
description: Use when working on build, packaging, or deployment of this backend — the GitHub Actions deploy workflow, building the bootJar, injecting Firebase credentials in CI, the rsync-to-server + systemd restart, or how runtime secrets reach the server.
---

# Deployment

## Overview

Deployment is a single **GitHub Actions** workflow that fires on push to `main`: it builds the
fat jar, injects the Firebase credentials from a secret, copies the jar to a Linux server over
SSH, and restarts a **systemd** service. There are no containers — a plain `java -jar` under
systemd.

## The pipeline (`.github/workflows/deploy.yml`)

Trigger: `push` to `main`. Steps:

1. **Checkout**.
2. **Setup SSH** — loads the deploy key from `secrets.DEPLOY_KEY` (webfactory/ssh-agent).
3. **Known hosts** — `ssh-keyscan` the server into `~/.ssh/known_hosts`.
4. **Firebase credentials** — decode `secrets.FIREBASE_CREDENTIALS_BASE64` into
   `app/src/main/resources/firebase-credentials/chirp-firebase-adminsdk.json` **before** the
   build, so `bootJar` bundles it (the file is not in git). See [[firebase-push]].
   ```bash
   mkdir -p app/src/main/resources/firebase-credentials
   echo "${{ secrets.FIREBASE_CREDENTIALS_BASE64 }}" | base64 -d > app/src/main/resources/firebase-credentials/chirp-firebase-adminsdk.json
   ```
5. **Build** — `./gradlew :app:bootJar` (only `app` builds a jar — see [[gradle-build-system]]).
6. **Deploy** — `rsync` the jar to the server, then over SSH rename it to `chirp.jar` and
   `sudo systemctl restart chirp.service`:
   ```bash
   rsync -avz -e "ssh" app/build/libs/app-0.0.1-SNAPSHOT.jar admin@<server>:/opt/chirp/app-0.0.1-SNAPSHOT.jar
   ssh admin@<server> << 'EOF'
     mv /opt/chirp/app-0.0.1-SNAPSHOT.jar /opt/chirp/chirp.jar
     sudo systemctl restart chirp.service
   EOF
   ```

The jar version string (`app-0.0.1-SNAPSHOT.jar`) comes from the root `version` (see
[[gradle-build-system]]); if you bump the version, update the workflow's `JAR_NAME`.

## Server runtime

- The app runs from `/opt/chirp/chirp.jar` as a **systemd unit** `chirp.service` (so it
  auto-restarts and starts on boot). The unit (on the server, not in the repo) runs the jar with
  `--spring.profiles.active=prod` and provides the runtime **environment variables**
  (`POSTGRES_PASSWORD`, `REDIS_PASSWORD`, `RABBITMQ_PASSWORD`, `MAILGUN_PASSWORD`,
  `JWT_SECRET_BASE64`, `SUPABASE_SERVICE_KEY`) — see [[configuration]].
- An **nginx** reverse proxy fronts the app; prod config trusts it via `nginx.trusted-ips` and
  `require-proxy: true` so client IPs are resolved from `X-Real-IP` — see [[rate-limiting]].

## GitHub secrets used

| Secret                      | Purpose                                            |
|-----------------------------|----------------------------------------------------|
| `DEPLOY_KEY`                | SSH private key for the deploy user.               |
| `FIREBASE_CREDENTIALS_BASE64` | base64 of the Firebase Admin SDK JSON.           |

Runtime app secrets (DB, Redis, etc.) are **not** in GitHub — they live in the systemd unit's
environment on the server.

## Building/running locally

```bash
./gradlew :app:bootJar
java -jar app/build/libs/app-0.0.1-SNAPSHOT.jar --spring.profiles.active=dev
```

Server starts on `:8080`. `.sdkmanrc` selects Java 21 on `cd` if you use SDKMAN.

## Adapting for a new project

- Point the workflow at your server/host and update the `secrets.*` names.
- Keep the **build-before-credentials ordering**: any classpath secret (Firebase, etc.) must be
  materialized before `bootJar`, and the feature module's resources must be wired into the jar —
  see [[gradle-build-system]].
- Recreate the systemd unit with the runtime env vars and `--spring.profiles.active=prod`.
- Provision the backing services (Postgres/Supabase, Redis, RabbitMQ, Firebase, Mailgun) and set
  the matching env vars — see [[configuration]]. `STRUCTURE.md` §16 has the provisioning checklist.

## Common mistakes

- Decoding the Firebase JSON **after** the build (jar ships without creds → app throws on init).
- Bumping the project version but not the workflow's `JAR_NAME`.
- Putting runtime DB/Redis secrets in GitHub instead of the server's systemd environment.
- Expecting a feature module's resources in the jar without the `bootJar` `from(...)` copy.
- Deploying with `ddl-auto: validate` (prod) after changing entities without applying the schema
  change on the DB first — boot will fail validation. See [[jpa-persistence]].
