---
name: next-pr
description: Review and advance the next easy-to-merge pull request in this repository, then continue to the next simplest PR. Use when the user wants Codex to list open PRs, pick the most trivial low-risk PR, establish or reuse a clean baseline on the default branch, collapse the rebased PR into a single commit on top of that branch, rerun tests and benchmarks when needed, summarize findings for approval, and only merge and push after explicit user approval.
---

# Next PR

Follow this workflow when the user wants to catch up on pull requests by taking the next easiest one first and then continuing through the queue from easiest to hardest.

## GitHub Access

Use the `gh` CLI for GitHub interactions in this workflow.

- Use `gh pr list` to enumerate open PRs.
- Use `gh pr view` to inspect candidate PRs and changed files.
- Use `gh pr checkout` or `gh api` for PR-specific fetch and checkout operations.

Only fall back to the web or raw API calls if `gh` is unavailable or not authenticated.

## Checkout Rules

Do not use `git worktree` in this workflow.

- Stay in the user's current checkout.
- Use local branches in the current repository when preparing the baseline branch and the PR review branch.
- If uncommitted local changes make that unsafe, stop and tell the user instead of creating a worktree.

## Workflow

1. Identify the parent repo and its default branch.
Use the actual remote default branch rather than assuming `main` or `master`.

2. List all open pull requests on the parent repo.
Prefer the parent repo over a fork unless the user says otherwise. Use `gh pr list` against the parent repo.

3. Pick the most trivial PR and explain why it is the easiest yes/no decision.
Prefer small, low-risk PRs such as:
- dependency bumps with narrow blast radius
- docs-only or config-only changes
- tiny test fixes
- one-commit PRs with limited file scope

Avoid PRs that:
- change behavior across many files
- mix refactors with fixes
- need product or architecture decisions
- are hard to evaluate because baseline validation is broken

4. Establish a clean baseline before touching the PR branch.
Update the local default branch from `origin` and validate that exact branch first, unless you are starting a new `next-pr` cycle immediately after finishing the previous one and the default branch tip is still the exact validated result of that previous cycle.

In that immediate follow-on case, reuse the test and benchmark run from the end of the previous cycle as the baseline for the next PR instead of rerunning the same baseline commands.

Run the repo's real validation commands, not substitutes. Include benchmarks when the repo has a benchmark workflow.

If baseline validation fails:
- do not treat PR validation as authoritative
- fix the baseline first if that matches the user's requested workflow
- otherwise stop and tell the user the PR cannot be judged against a green baseline yet

If any of these changed since the previous cycle, do not reuse the old baseline:
- the default branch tip
- the validation command set
- the local checkout or environment in a way that could affect results

5. Check out the PR only after the baseline is green.
Create a dedicated local review branch for the PR in the current checkout. Use `gh pr checkout` or an equivalent `gh`-driven fetch to materialize the PR locally. Rebase it on the latest default branch tip and collapse the PR changes into a single commit on top of that tip. Resolve conflicts carefully without discarding upstream or user work.

6. Validate the collapsed rebased PR branch.
Run the same tests and benchmarks used for the baseline so the comparison is meaningful. Call out:
- passes or failures
- benchmark regressions or improvements
- new warnings, flaky behavior, or environment issues

If the PR fixes a Java bug and does not include a regression test:
- add a regression test as part of the review branch before approval
- verify that the regression test fails against the baseline branch without the fix
- verify that the same regression test passes on the rebased PR branch with the fix

7. Summarize for approval before merging.
Report:
- PR number and title
- why it was chosen
- what changed at a high level
- rebase/collapse result
- baseline validation result
- PR validation result
- recommendation and any risks

Do not merge or push yet.

If the review shows the PR is cosmetic or otherwise a no-op, and it does not fix a demonstrated bug or change behavior:
- recommend closing it rather than merging it
- explain briefly why it is a no-op
- use `gh pr close` with a polite comment after the user approves closing it

8. Merge only after explicit user approval.
After approval, integrate the single collapsed PR commit into the default branch in the way the repo expects.

9. Push only after the merge is complete and the default branch is in the intended state.
Push the default branch to the appropriate remote and report what was pushed. If the pushed default branch tip matches the rebased PR branch you just validated, treat that validation run as the baseline for the next cycle.

10. Close the PR manually if GitHub does not auto-close it after the merge push.
When a PR was merged by rebasing or otherwise rewriting commits, verify whether it is still open. If it is, close it with `gh pr close` and leave a short comment explaining that the changes were merged onto the default branch via the collapsed rebased commit.

11. Immediately start the workflow again on the next simplest remaining PR unless the user explicitly asked to stop after one PR.
Go back to step 2, pick the next easiest yes/no decision, and keep working through the queue. When step 9 left you on the same validated default branch tip, use that just-finished validation run as the baseline for the new cycle instead of rerunning baseline tests and benchmarks.

## Validation Rules

- Use the same command set for baseline and PR validation so results are comparable.
- If the repo has both fast checks and full checks, run the full set unless the user explicitly narrows scope.
- If benchmarks are noisy, report broad direction and uncertainty instead of overclaiming precision.
- If a failure is clearly pre-existing, say that explicitly and separate it from PR-specific findings.
- For Java bugfix PRs, require a regression test. If one is missing, add it on the review branch and prove it fails before the fix and passes after the fix.
- If a PR is cosmetic or a no-op, do not merge it just because validation is green; require evidence of a real bugfix or behavior change.
- When chaining directly from one completed cycle to the next, you may reuse the just-validated default-branch state as the next baseline instead of rerunning baseline checks, but only if the branch tip and validation command set are unchanged and nothing else invalidated that result.

## Communication

- Tell the user which PR you picked and why it is the lowest-risk candidate.
- When baseline is broken, say that first.
- Before merge and push, stop at an approval gate and wait for the user.
- After approval, report exactly which branch was updated and pushed.
- If a rebased merge leaves the PR open, close it and say which comment or reason you used.
- If closing a cosmetic or no-op PR, use a short polite message that explains it did not produce a demonstrated bugfix or behavior change and can be reopened with a concrete repro or regression test.
- After completing one PR, say that you are immediately continuing to the next easiest remaining PR and whether you reused the previous cycle's validation as the new baseline.
