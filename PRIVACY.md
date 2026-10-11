# Privacy Policy

i.c.u. is a private Discord moderation bot. It is not publicly listed and only runs in Discord servers operated by its developer.

## What data is stored

For members of servers the bot is in who have at least one role or a nickname:
- Server ID, user ID, role IDs and nickname. These are saved when the bot starts, when a member's roles or nickname change, and when they leave the server.

Members without any roles or nickname have nothing to restore, so nothing is stored for them. If a member loses all their roles and their nickname, their stored data is deleted.

For bans issued through the bot's global ban feature:
- The banned user's ID, the ban reason, and when the ban was created.

Per-server configuration set by server admins (role, channel and user IDs) is also stored.

The bot does not store message content, and does not track presence or activity.

## How the data is used

The data is used only for the bot's moderation features:
- restoring a member's roles and nickname when they rejoin a server
- keeping bans in sync between the developer's servers, and warning moderators about users who are banned elsewhere

The data is never shared, sold, or used for any other purpose.

## Storage and access

Data is kept in a private database that only the developer can access, secured using industry-standard measures.

## Retention and deletion

Data is kept until deletion is requested, so that roles can still be restored for members who rejoin after a long time. To have your data deleted, contact the moderators of the server, or open an issue at https://github.com/CapybaraLabs/icu/issues.

Deletion requests are handled by deleting all stored data for your user ID, and banning your user from all servers the bot is in.
