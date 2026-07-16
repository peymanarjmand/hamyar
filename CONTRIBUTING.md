# Contributing to HesabYar Garage

Thank you for considering a contribution. Changes to this project should preserve its offline-first behavior, Persian RTL experience, and accounting invariants.

## Development setup

1. Install JDK 17 and Android SDK 36.
2. Fork and clone the repository.
3. Create a focused branch from `main`.
4. Run the verification suite before opening a pull request:

```bash
./gradlew testDebugUnitTest lintDebug assembleDebugAndroidTest
```

## Branch and commit conventions

Use short branch names such as `feat/settlement-export` or `fix/absence-boundary`.

Commit messages follow Conventional Commits:

- `feat:` user-visible behavior
- `fix:` bug correction
- `test:` test-only changes
- `docs:` documentation
- `refactor:` behavior-preserving code changes
- `chore:` build or repository maintenance

Keep each commit scoped to one coherent change. Do not add generated build output, signing material, credentials, personal data, or real card numbers.

## Pull request requirements

- Explain the user problem and the chosen solution.
- Add or update tests for financial rules and date boundaries.
- Preserve integer Toman arithmetic; never introduce floating-point money.
- Preserve append-only financial history and immutable eligibility snapshots.
- Include screenshots for visible UI changes, especially RTL layouts.
- Update Room schemas and migration tests when the database schema changes.
- Update documentation when behavior or release steps change.

## Financial-domain review checklist

Any ledger mutation should demonstrate that:

1. Claim totals remain explainable by source income.
2. Custody totals remain explainable by source income and transfers.
3. Transfers do not create or destroy money.
4. Settlements reduce the intended claim and custody positions.
5. Closed financial years remain immutable.

## Reporting security issues

Do not open a public issue for a suspected vulnerability or privacy leak. Follow [SECURITY.md](SECURITY.md).
