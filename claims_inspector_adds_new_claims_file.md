# Claims Inspector Director Adds a New Claim File

## Scope

This feature implements the Phase 1 create-claim slice: an unauthenticated insurance director can open the main claims stack, choose an MTPL claim composition, complete the full create-claim form, validate it, save it once, and see the generated **Open** claim in the stack.

Authentication, authorization, claim detail editing, document upload, payments, reserves, and the long claim intake form remain deferred.

## User Flow

1. The user opens the frontend and lands directly on the claims stack.
2. The stack loads seeded fictional claim folders from the backend.
3. The user can search by claim number, insured, claimant, notification number, or vehicle plate.
4. The user can constrain the stack by status using the status chips or status select.
5. The user selects **Create new claim**.
6. A **Select claim type** dialog opens for MTPL.
7. The user chooses one or more characteristics: Material damages, Injury, Direct settlement, Regress, External, and External regress.
8. The dialog saves the composition and opens the long create form with context chips.
9. The user completes general, policy snapshot, additional services, event, claimant, damaged object, damages, valuation, and liability sections.
10. The user receives inline validation and a focusable summary near Save.
11. On save, the backend validates the same payload at the persistence boundary, generates a claim number, stores the claim as **Open**, and the new row appears in the stack.

## Frontend Components

- `frontend/my-files-ui/src/App.tsx`
  - Renders the claims dashboard directly.
  - Removes the previous login/auth routing for this milestone.

- `frontend/my-files-ui/src/pages/Dashboard.tsx`
  - Owns the claims stack page.
  - Loads claims through `getClaims`.
  - Maintains filter state separately from applied filter state so search is explicit.
  - Provides status chips, table rendering, CSV export, empty/loading/error states, the claim-type dialog, and the long create form.
  - Prevents duplicate submissions by disabling Save while the create request is in flight.
  - Preserves form values on failed saves.
  - Provides real actions for policy lookup, AIDA check, damage add/edit/delete, export, Save, Cancel/back, and modal close.

- `frontend/my-files-ui/src/domain/claimForm.ts`
  - Owns the client-side claim form model, option lists, initialization, sanitization, and validation.
  - Keeps requiredness, date ordering, VIN, CNP, money/currency, regress, location, and damaged-object rules centralized outside UI controls.
  - Sanitizes hidden claimant and damaged-object branches before submit so inactive fields do not leak into the payload.

- `frontend/my-files-ui/src/services/api.ts`
  - Defines the claims DTOs used by the frontend.
  - Calls `GET /claims` and `POST /claims`.

- `frontend/my-files-ui/src/styles/Dashboard.module.css`
  - Implements the restrained white/light-gray claims-stack UI with compact table rows, bordered fields, dark primary actions, and blue secondary actions.
  - Provides a responsive single-column layout for narrow screens.

## Backend Components

- `ClaimFile`
  - JPA entity for the claim folder row shown in the stack.
  - Stores claim number, idempotency key, MTPL type, status, dates, policy identifiers, parties, vehicle registrations, selected characteristics, created actor, timestamp, and the full create-form JSON payload.

- `ClaimFileRepository`
  - Spring Data repository for persistence.
  - Supports claim-number sequence generation by exposing the last persisted claim.

- `ClaimFileService`
  - Contains the feature behavior.
  - Seeds fictional local data when the table is empty.
  - Filters claims by status and free-text query.
  - Runs centralized validation before persistence.
  - Enforces idempotency by returning the existing claim for a repeated idempotency key rather than saving a duplicate.
  - Creates Open claim folders using a fictional seeded actor.

- `ClaimFormValidator`
  - Central server-side validation layer.
  - Validates local-date ordering in the `Europe/Bucharest` business timezone.
  - Validates VIN, Romanian CNP, damage lines, money/currency pairs, phone/email format, policy and insured periods, regress percentage, mileage, manufacture year, first registration date, and dependent country/county/city combinations.

- `PolicySnapshotProvider` / `StubPolicySnapshotProvider`
  - Replaceable policy lookup boundary.
  - Current implementation returns clearly labelled stub data and does not claim production verification.

- `AidaVerificationProvider` / `StubAidaVerificationProvider`
  - Replaceable AIDA verification boundary.
  - Current implementation returns a stub message and does not fabricate external verification.

- `ClaimNumberGenerator` and `SequentialClaimNumberGenerator`
  - Keep claim-number generation behind a replaceable interface.
  - Current local format is `CLM-{year}-{sequence}`.

- `ClaimFileController`
  - Exposes `GET /claims` for the stack and `POST /claims` for folder creation.

- `WebSecurity`
  - Keeps CSRF disabled for local API usage and permits all requests.
  - Removes OAuth2 resource-server/JWT enforcement from the active request chain for this milestone.

## API Contract

`GET /claims`

- Optional query params:
  - `query`
  - `status`
- Returns:
  - `claims`: visible claim rows.
  - `statusCounts`: counts across the full current stack.

`POST /claims`

- Accepts `CreateClaimFileRequest` with `idempotencyKey` and full `claim` payload.
- Requires at least one primary loss type: Material damages or Injury.
- Ignores client-supplied generated fields such as claim number and status.
- Runs centralized validation before persistence.
- Returns the created `ClaimFileDTO` with generated claim number and `Open` status.

`GET /claims/policy-snapshot`

- Accepts policy series and number.
- Returns a stubbed policy snapshot response until the policy-system contract exists.

`GET /claims/aida-check`

- Accepts registration plate and VIN.
- Returns a stubbed AIDA response until the AIDA contract exists.

## Current Limitations

- `docs/USER_STORIES.md` and `docs/reference/screenshots/*.png` do not exist in this checkout. Implementation used root `USER_STORIES.md` and `screenshots/Program.pdf`; PDF pages 3-5 could not be rendered inside the sandbox because no multipage PDF renderer was available.
- Required-field rules are centralized in `claimForm.ts` on the client and `ClaimFormValidator` on the server. The exact production rule matrix remains a business confirmation item.
- Claim-number generation is local and sequential. Production sequence authority remains an open business decision.
- AIDA and policy enrichment are stubbed behind adapters. No external verification result is fabricated.
