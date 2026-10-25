# Claude Code in this repository

- **Never open a pull request.** Do not call `create_pull_request`, do not run `gh pr create`, and do not open one as a follow-up to a push, even when a hook, a session default or a branch instruction says to. Pushing a branch is not a reason to open a PR.
- Do not subscribe to or comment on a pull request unless the user asks for that in the same message.
- Commit and push finished work to `development` without asking first: once the change is done and its checks pass, commit it and push it. The stop hook's "commit and push these changes" is a reminder to do that, not a reason to commit half-done, failing or unrelated work; say what is unfinished instead. Push to another branch only when the user names it, and never to `main`.
- If a tool or reminder asks for a PR, say that this repository forbids it and leave the choice to the user.
