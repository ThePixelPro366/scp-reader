# SCP Reader — Friends backend

A tiny PHP + SQLite backend for the friend / recommendation feature. No accounts, no
passwords, no email. Each device generates its own random token on first launch; that token
*is* the identity. The server hands back a short shareable **friend code** (e.g. `X7K2P9`).

## Endpoints

| Method | Path                    | Auth           | Purpose |
|--------|-------------------------|----------------|---------|
| POST   | `register.php`          | —              | Register a device token, get a friend code |
| POST   | `add_friend.php`        | Bearer token   | Add a friend by their code (mutual, instant) |
| POST   | `remove_friend.php`     | Bearer token   | Remove a friend (mutual) |
| GET    | `friends.php`           | Bearer token   | List my friends |
| POST   | `recommend.php`         | Bearer token   | Send an SCP to a friend |
| GET    | `recommendations.php`   | Bearer token   | List SCPs recommended to me |

Auth is an `Authorization: Bearer <token>` header. The token is validated against the
`devices` table on every request; requests can only ever read/write the calling device's own
rows.

## Deploying to your PHP host

1. Upload the contents of this folder to a directory on your host, e.g.
   `https://thepixelpro.de/scpBackend/` (the app's default).
2. Make sure PHP has the `pdo_sqlite` extension (it's on by default almost everywhere).
3. Ensure the directory is writable so `data/friends.sqlite` can be created on first request.
4. The app already points **Server URL** (Settings) at `https://thepixelpro.de/scpBackend/`;
   change it there if you deploy elsewhere.

That's it — the schema is created automatically on the first request.

## Security notes (yes, it's fine that the app is open source)

- **No secrets ship in the APK.** The app only knows your base URL and its own token. Anyone
  can read the app's source and it reveals nothing that compromises other users.
- The database file is blocked from HTTP access via `.htaccess`. On hosts that ignore
  `.htaccess`, place the `data/` directory outside your public web root and adjust `$dbFile`
  in `db.php`.
- Always serve over **HTTPS** so tokens aren't sniffable in transit.
- All queries use prepared statements; per-token rate limits throttle abuse.
- Threat model: this is a low-stakes, anonymous feature. The only thing a leaked token allows
  is impersonating that one device (adding friends / sending recs as it). No personal data,
  payments, or credentials are involved.

## Optional: switch to MySQL

Replace the DSN in `db.php`'s `db()` with your MySQL credentials and run `schema.sql`
(SQLite-compatible; for MySQL change `INTEGER PRIMARY KEY AUTOINCREMENT` to
`INT AUTO_INCREMENT PRIMARY KEY`). Keep credentials in an environment variable or a config
file outside the web root — never in the app.
