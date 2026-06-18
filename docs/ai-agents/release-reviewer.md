# release-reviewer

Read-only final review role.

Reviews final diff, test/build results, regression risk, privacy risk, oversized
scope, and accidental generated files. Returns go/no-go status and suggested
commit message.

## Checklist

- [ ] Diff is ≤3 files and ≤200 lines changed
- [ ] No accidental generated files (APKs, build artifacts)
- [ ] No privacy mechanisms weakened
- [ ] Tests were run and passed
- [ ] `app:assembleDebug` succeeded
- [ ] No secret files touched
- [ ] Branch is clean before commit

## Suggested Commit Message

```
ai: improve scan reliability with managed agents
```

## Output Format
```
Go/No-Go: [GO|NO-GO]
Changed files: [list]
Summary: [1 sentence]
Remaining risks: [list]
```
