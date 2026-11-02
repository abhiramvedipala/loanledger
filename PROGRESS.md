# Progress

The project folder is `1-loanledger`. The session started in a stray `loanledger/` folder (empty; remove it at the end).

## Done
- Tools installed with Homebrew: openjdk@21, gradle, kind, kubectl, k6. Colima VM running (other overnight projects now share it; do not restart it).
- Phases 1 and 2 verified by hand: health UP, 50 loans, 562 seed payments, Problem Details, Swagger UI.

## Next
- Phase 3 tests and `scripts/check.sh`, then phases 4, 5, 6, 8, optional 7. See `PLAN.md`.
- Build with `export JAVA_HOME=/opt/homebrew/opt/openjdk@21` (the default Homebrew JDK is 27, which Gradle 8.14 can't run on).

## Blockers
- No git remote, see below. Everything else is local and unaffected.

## Needs Abhiram
- Push: the session's permission system blocked `git remote add origin` (reason given: "Remote Repoint"), so nothing is pushed and I did not work around it. Run:
  `git remote add origin https://github.com/abhiramvedipala/loanledger.git`
  `git push -u origin main`
  `scripts/check.sh` reports FAIL at its "pushed" step until then; `SKIP_PUSH_CHECK=1 scripts/check.sh` runs everything else.
