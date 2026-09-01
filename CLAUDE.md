# Git identity — read before every commit

This container's environment has a SessionStart hook
(`~/.claude/session-start-git-identity.sh`) that resets the global git
identity to `Claude <noreply@anthropic.com>` and enables commit signing
with an Anthropic-controlled key. The repo owner does not want commits
authored as Claude, ever, under any circumstance.

**Before running `git commit` in this repo, always run this first, in the
same turn, immediately before the commit — every single time, no
exceptions, even if it was already done earlier in the session (the
SessionStart hook can and does re-fire and silently undo it):**

```bash
git config --global user.name "njm25"
git config --global user.email "njm25@users.noreply.github.com"
git config --global commit.gpgsign false
```

Then verify the result actually stuck before pushing:

```bash
git log -1 --format='Author: %an <%ae>%nCommitter: %cn <%ce>%nSigned: %G?'
```

`Author`/`Committer` must both read `njm25 <njm25@users.noreply.github.com>`,
and `Signed` must be `N` (no signature). If either check fails, re-run the
three `git config` commands and amend/recommit before pushing — do not push
a commit authored as Claude or carrying a signature.
