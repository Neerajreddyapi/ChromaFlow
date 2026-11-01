# Security Policy

## Supported versions

Only the latest version of Chroma Flow on Google Play (and the latest commit on `master`) receives fixes.

## Reporting a vulnerability

Please **don't** report security problems in public issues.

Instead, use one of these:

- GitHub's private reporting: the **Security** tab → **Report a vulnerability**, or
- email **pingdrop.support@gmail.com** with "Chroma Flow security" in the subject.

Please include what you found, the steps to reproduce it, and which version and phone you used. You'll get a reply within a few days. Once the problem is fixed, you'll be credited in the changelog if you'd like.

## Scope

Chroma Flow has no servers, no accounts, no internet permission and no Android permissions, so the attack surface is small. Things worth reporting include:

- a way for content inside the app to load remote code or leave the bundled page
- anything that makes the app collect or send data
- a crash or hang that a crafted input can trigger
