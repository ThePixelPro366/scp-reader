-- SCP Reader friends backend schema (SQLite).
-- Identity is a device-generated token; the friend_code is the shareable public handle.

CREATE TABLE IF NOT EXISTS devices (
    token       TEXT PRIMARY KEY,          -- on-device generated secret (acts as the password)
    friend_code TEXT UNIQUE NOT NULL,      -- short shareable handle, e.g. "X7K2P9"
    name        TEXT NOT NULL DEFAULT '',  -- optional display name shown to friends
    created_at  INTEGER NOT NULL,
    last_seen   INTEGER NOT NULL
);

-- One row per direction, so a lookup for "my friends" is a single indexed scan.
CREATE TABLE IF NOT EXISTS friends (
    owner_token  TEXT NOT NULL,
    friend_token TEXT NOT NULL,
    created_at   INTEGER NOT NULL,
    PRIMARY KEY (owner_token, friend_token),
    FOREIGN KEY (owner_token)  REFERENCES devices(token) ON DELETE CASCADE,
    FOREIGN KEY (friend_token) REFERENCES devices(token) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS recommendations (
    id         INTEGER PRIMARY KEY AUTOINCREMENT,
    from_token TEXT NOT NULL,
    to_token   TEXT NOT NULL,
    scp_url    TEXT NOT NULL,   -- canonical wikidot url (stable id the reader opens)
    scp_number TEXT NOT NULL,   -- e.g. "SCP-173"
    scp_title  TEXT NOT NULL,
    note       TEXT NOT NULL DEFAULT '',
    created_at INTEGER NOT NULL,
    FOREIGN KEY (from_token) REFERENCES devices(token) ON DELETE CASCADE,
    FOREIGN KEY (to_token)   REFERENCES devices(token) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_recs_to ON recommendations (to_token, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_friends_owner ON friends (owner_token);

CREATE TABLE IF NOT EXISTS rate_limits (
    token  TEXT NOT NULL,
    bucket TEXT NOT NULL,
    ts     INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_rate ON rate_limits (token, bucket, ts);
