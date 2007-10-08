DROP TABLE IF EXISTS account;
DROP TABLE IF EXISTS user;
DROP TABLE IF EXISTS rank;

CREATE TABLE account (
	id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL ON CONFLICT ROLLBACK,
	access INTEGER NOT NULL,
	name TEXT UNIQUE NOT NULL,
	created INTEGER NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE user (
	id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL ON CONFLICT ROLLBACK,
	login TEXT UNIQUE NOT NULL,
	account TEXT DEFAULT NULL,
	created INTEGER NOT NULL DEFAULT CURRENT_TIMESTAMP,
	lastSeen INTEGER NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE rank (
	id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL ON CONFLICT ROLLBACK,
	name TEXT UNIQUE NOT NULL
);