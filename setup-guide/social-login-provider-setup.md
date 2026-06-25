# Social Login (Google & Apple) — Provider Setup

> Reusable, project-agnostic checklist. Do this **once per new project** to make the
> `/api/auth/google` and `/api/auth/apple` endpoints work. No backend code changes — you only
> create provider credentials and set two environment variables.

## What you're actually setting up (and why)

The backend does **not** use Firebase Auth (or any provider) as its login system. The flow is:

```
Mobile/web app  →  native Google/Apple sign-in  →  provider ID token
      →  POST /api/auth/google|apple (your backend)
      →  backend verifies the token against the provider's PUBLIC keys (JWKS)
      →  backend issues ITS OWN access + refresh JWTs (same as email login)
```

So all you need from the providers is an **identifier for your app**:

- **Google** → an OAuth **client ID** (the token's `aud` claim).
- **Apple** → your app's **bundle id** (the token's `aud` claim).

These go into `GOOGLE_CLIENT_IDS` / `APPLE_CLIENT_IDS`. The backend's allowlist check on `aud`
is the core security control — it ensures a token was minted for *your* app, not someone else's.
**Only ever put your own client IDs in these vars.**

> Enabling a provider in Firebase Auth (below) is just the *easiest way to create the Google
> client ID*. The backend verifies tokens directly against Google/Apple — it never calls Firebase
> Auth. (You can create the client ID manually in Google Cloud Console instead; same result.)

---

## Google

You need at minimum a **Web (server) client ID**. For real apps you'll also have an **Android**
and an **iOS** client ID — add all of them to the allowlist.

### Easiest: via Firebase
1. **Firebase Console → Authentication** → *Get started* (first time only).
2. **Sign-in method → Add provider → Google → Enable** → set a **support email** → **Save**.
3. Get the value: **Authentication → Sign-in method → Google → Web SDK configuration → Web client ID**
   (also appears in **Google Cloud Console → Google Auth Platform → Clients**).

### Manual alternative: Google Cloud Console
1. **Google Auth Platform → Get started** → fill **Branding** (app name + support email).
2. **Audience** → **External**, and add your Google account under **Test users**.
3. **Clients → Create client → Web application** → **Create** → copy the **Client ID**.

### Where the platform client IDs come from (for real apps)
- **Web/server client ID** — created above. On Android, `requestIdToken(webClientId)` mints tokens whose `aud` = this.
- **Android client ID** — created when you add your app's **SHA-1 fingerprint** to the Firebase Android app.
- **iOS client ID** — the `CLIENT_ID` value inside `GoogleService-Info.plist`.

### Testing note
While the OAuth app is in **Testing** status, only accounts listed under **Audience → Test users**
can sign in. Add the Google account you'll test with.

---

## Apple (native iOS)

Requires an **Apple Developer account** ($99/yr).

1. **developer.apple.com → Certificates, Identifiers & Profiles → Identifiers**.
2. Open your **App ID** (your bundle id, e.g. `com.yourcompany.app`).
3. Enable the **"Sign In with Apple"** capability → **Save**.
4. `APPLE_CLIENT_IDS` = that **bundle id**.

Notes:
- You do **not** need to configure Apple in Firebase for this backend (Firebase's Apple provider is
  for Firebase-managed sign-in, which this backend doesn't use).
- No Service ID / Team ID / private key is needed for *identity-token verification* — only the bundle id.
- There's no browser shortcut for an Apple test token; it must come from a real iOS sign-in.

---

## Wire it into the backend

Set environment variables (comma-separated; no spaces needed):

```bash
GOOGLE_CLIENT_IDS=<web-id>.apps.googleusercontent.com,<android-id>.apps.googleusercontent.com,<ios-id>.apps.googleusercontent.com
APPLE_CLIENT_IDS=com.yourcompany.app
```

- **Local dev:** add them to the IDE run configuration / shell before launching.
- **Production:** add them to the server's service environment (e.g. the systemd unit).
- They map to config keys `chirp.social.google.client-ids` / `chirp.social.apple.client-ids`
  in `application.yml`.
- The app boots fine **without** them; only the two social endpoints need them (verifiers are lazy).

---

## Verify you got it right

Once you have a real token, decode it at **jwt.io** and check the payload:

| Claim | Must be |
|-------|---------|
| `aud` | one of the values in your `*_CLIENT_IDS` |
| `iss` | `https://accounts.google.com` (Google) / `https://appleid.apple.com` (Apple) |
| `nonce` | equal to `SHA256_hex(rawNonce)` you sent |

**`401 INVALID_TOKEN` on a genuine token is almost always an `aud` mismatch** — compare the token's
`aud` to your env var.

---

## Per-project checklist

- [ ] Google: create the OAuth **Web client ID** (Firebase → enable Google, or Cloud Console).
- [ ] Google: collect the **Android** + **iOS** client IDs your apps will actually use.
- [ ] Google: add your test account under **Audience → Test users**.
- [ ] Apple: App ID with **Sign In with Apple** enabled; note the **bundle id**.
- [ ] Set `GOOGLE_CLIENT_IDS` and `APPLE_CLIENT_IDS` (dev run config + prod service env).
- [ ] Smoke test: empty body → `400`, garbage token → `401`, real token → `200`.
