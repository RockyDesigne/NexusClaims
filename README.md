# NexusClaims

NexusClaims is being built into an insurance file management system for claim intake and claim file handling. The current milestone focuses on the insurance director creating an insurance file and adding it to the claims stack.

Project instructions live in `AGENTS.md` and `CLAUDE.md`. Product acceptance criteria live in `USER_STORIES.md`. The reference screenshots are in `screenshots/Program.pdf`.

## Repository Layout

- `src/` - Spring Boot backend.
- `frontend/my-files-ui/` - React, TypeScript, Vite frontend.
- `file-share.db` - local SQLite database used by the backend.
- `screenshots/Program.pdf` - source UI reference.

## Required Tooling

Backend:

- JDK 21. The Gradle build declares `java.toolchain.languageVersion = 21`.
- Gradle 8.11.1. The wrapper configuration is present in `gradle/wrapper/gradle-wrapper.properties`, but the root `gradlew` scripts are currently missing. Until they are restored, use a locally installed Gradle compatible with Java 21.

Frontend:

- Node.js `20.19.0` or newer in the Node 20 line, or Node.js `22.12.0` or newer.
- npm 10.x or newer. The current local lockfile was generated with npm lockfile version 2 and the installed npm on this machine is 10.9.x.

Recommended local versions:

```sh
java -version
gradle -v
node -v
npm -v
```

## Backend Setup

From the repository root:

```sh
gradle clean build
gradle bootRun
```

The backend defaults to:

- Address: `127.0.0.1`
- Port: `8081`
- Database: `jdbc:sqlite:file-share.db`

The legacy token service reads its signing secret from `JWT_HMAC_SECRET`. When unset, it generates a random signing key for the current process; legacy tokens then expire on restart. No signing secrets, local databases, or uploaded documents are committed.

Override address or port when needed:

```sh
ADDRESS=127.0.0.1 PORT=8081 gradle bootRun
```

Run backend tests:

```sh
gradle test
```

When the Gradle wrapper scripts are restored, prefer:

```sh
./gradlew clean build
./gradlew bootRun
./gradlew test
```

## Frontend Setup

From the frontend directory:

```sh
cd frontend/my-files-ui
npm ci
npm run dev
```

Common commands:

```sh
npm run lint
npm run build
npm run preview
```

Vite will print the local frontend URL when `npm run dev` starts.

## npm Supply Chain Safety

Use the lockfile-first workflow for normal development:

```sh
cd frontend/my-files-ui
npm ci --ignore-scripts
npm audit signatures
npm audit
npm run lint
npm run build
```

Guidelines:

- Prefer `npm ci` for repeatable installs. Avoid using `npm install` just to refresh dependencies.
- Keep `package-lock.json` committed and review dependency diffs carefully.
- Use `--ignore-scripts` for first-pass installs to prevent dependency lifecycle scripts from running automatically.
- Do not run `npm audit fix --force` without reviewing the major-version changes it will make.
- Before adding a new package, check its owner, maintainers, publish history, weekly downloads, license, repository, and recent version changes.
- Prefer small, established packages. Avoid packages with unexpected install scripts, obfuscated code, typo-squatted names, or sudden ownership changes.
- Verify the registry before installing:

```sh
npm config get registry
```

For public packages this should normally be:

```text
https://registry.npmjs.org/
```

When evaluating a new dependency:

```sh
npm view <package-name> name version license repository maintainers time dist.integrity
npm diff <package-name>@<old-version> <package-name>@<new-version>
npm install <package-name> --package-lock-only --ignore-scripts
npm audit
git diff -- package.json package-lock.json
```

After review, reinstall from the lockfile:

```sh
npm ci --ignore-scripts
```

If a trusted package genuinely requires an install script, inspect the package first and run the smallest necessary rebuild command instead of enabling scripts globally:

```sh
npm rebuild <package-name>
```

Do not commit `node_modules/`.

## Useful References

- Backend app entrypoint: `src/main/java/com/titu/file_share/FileShareApplication.java`
- Backend config: `src/main/resources/application.yml`
- Frontend package metadata: `frontend/my-files-ui/package.json`
- Frontend lockfile: `frontend/my-files-ui/package-lock.json`

## Opening and editing claim files

Click a claim number in the stack to open its saved details. URLs use `#/claims/{id}` and survive refresh. The Insurance Director can select **Edit claim**, change intake fields, and **Save changes**. Claim identity and workflow status remain fixed. Seeded summary-only files need the missing required intake fields completed before their first update.

The prototype role is owned by the backend and defaults to a fictional Insurance Director. Set `CLAIMS_ACTOR_ROLE=VIEWER` when starting the backend to verify read-only claim-detail behavior; update requests then return HTTP 403. This role switch is a development stub, not authenticated access control. Replace `ClaimAccessPolicy`'s actor source when authentication is introduced.

## Complete demo claims

An empty database is seeded with exactly three fully populated fictional examples: Vehicle, Injury, and Other property. Their snapshots live in `src/main/resources/demo/claims.json`; incident and policy dates are resolved relative to the current Bucharest business date. Each example contains only its active damaged-object branch and valid amount/currency pairs. Vehicle-specific valuations are `0.00 RON` for the non-vehicle examples. Policy and AIDA information is explicitly mocked, with no external verification implied.

Seeding runs only when the claims table is empty. A restart never replaces existing claims or edits. The requested local-data reset replaced the previous four records once; no automatic deletion or reset endpoint was added.

## Claim reserves

Click a claim number in the stack to open its own page and reveal its tabs, including **Reserves**. The selected claim remains visible at the top left within the claim view/editor and **Reserves**. Returning to the stack hides the selected-claim header and all claim-specific tabs. Open `#/claims/{id}/reserves` to see reserve and recovery-reserve history, current balances by type/currency, and total history. The Insurance Director can enter a signed adjustment and its reason. Each change is appended; previous entries remain intact. Different currencies have separate totals.

Existing claims receive their original intake reserve as a history entry once, and new claims initialize it during creation. The initial reserve becomes read-only after creation; later changes belong in Reserves. Updates use decimal arithmetic and TEXT-backed decimal storage, request keys for retries, and ledger revisions to reject stale writes. Current prototype limits and open business rules are recorded in `USER_STORIES.md`. No existing claim or prior history is deleted by initialization.

## Claim documents

Open a claim, then select **Documents**. Choose an optional tag, select or drag files into the queue, and select **Upload files**. The Director can attach files; Viewer can browse and download. Cards include first-page previews, names, sizes, tags, timestamps, and uploader. Filter by All/Images/Other and tag. Downloads preserve the original bytes.

Attachments are saved with claim-scoped IDs in the `claim_documents` SQLite table, including file bytes as BLOBs, so database backups include the attachments. Metadata listing excludes bytes. Same-name files are kept separately; upload retries reuse a key to avoid duplicate records. Default limits are 10 MB per file and 20 queued files; file formats and tags are listed in `USER_STORIES.md`. The old user-based file-sharing code is not used for claim documents. No external document-sending integration is implied.

For a separate local API, start Vite with `VITE_API_BASE_URL=http://127.0.0.1:18081 npm run dev`. The default remains `http://localhost:8081`.

### First-page document previews

Existing and new attachments get previews without re-uploading. Images are displayed directly. PDF pages are rendered locally with Apache PDFBox 3.0.8; DOC/DOCX, XLS/XLSX, TXT and CSV are converted locally with headless LibreOffice, exporting only page one. Select a thumbnail to open the preview at readable size. Original downloads always retain their original bytes and filenames.

Install LibreOffice on the backend host for Office/text previews. On macOS: `brew install --cask --appdir="$HOME/Applications" libreoffice`. Standard `/Applications` and `~/Applications` installations are detected automatically; on Linux, install LibreOffice Writer and Calc with `soffice` on PATH. Override the executable path with `CLAIMS_PREVIEW_OFFICE_COMMAND` if needed. PDF and image previews do not require LibreOffice. The native Office integration test runs when LibreOffice is installed and otherwise reports a skip.

Conversion uses temporary, isolated profiles with macros disabled, two rendering slots, and a 40-second process timeout. macOS child processes get a bounded file-descriptor limit because LibreOffice scans the inherited limit at startup. Successful derived PNGs are cached in `claim_document_previews`; source SHA-256 prevents stale previews. Cache misses never modify the originals. Unreadable or password-protected documents show an explicit retryable preview fallback and remain downloadable.

Rendering references: [PDFBox](https://pdfbox.apache.org/download.html), [LibreOffice PDF export parameters](https://help.libreoffice.org/latest/en-US/text/shared/guide/pdf_params.html).

### Claim tasks and demo loss adjusters

Open a claim and select **Tasks**. Existing Open claims receive an initial **Damage Assessment** task once; new claims receive theirs during creation. Assignments alternate between **Alex Marin (demo)** (`demo-adjuster-alex`) and **Dana Pavel (demo)** (`demo-adjuster-dana`). Their fictional assignment accounts are stored in `claim_staff_accounts`, independently of the legacy file-sharing users and their expiration job. These accounts do not introduce login credentials.

The predefined task names are **Damage Assessment**, **Claim Settlement**, **Damage Reassessment**, **Payment**, **Recovery**, and **Anti-fraud Investigation**. The first task is always Damage Assessment. Open a task from the list to see its ticket details: task name, free-text description, assignee, assigned by, assigned at, closed at, and state. Managers can edit active-task descriptions and assign tasks; assignees can start their own tasks, hand them off, and confirm final settlement. The page includes name/assignee/state/search filters, state counts, and activity history. Automatic assignment uses a durable database cursor over active loss adjusters in username order; manual assignment leaves that cursor unchanged. Failed transactions and retries do not consume a turn. Each Open claim has exactly one active task (Open or In progress). **Create task** replaces the current task and closes it in the same save. The Insurance Director can also open the current task and select **Take over and create next task** to take it from its existing owner and assign a replacement. The previous assignee and the manager takeover remain in history. Assignees use **Create handoff and close task** to assign their successor to someone else. The successor and source closure save atomically. A database constraint prevents parallel active tasks. Stale replacement requests and duplicate submissions cannot close a newer task or create an extra one. Assignee self-handoffs and direct completion remain prohibited. **Claim Settlement (lichidare)** is terminal: its assignee confirms that final payment is complete, completing settlement and closing the claim. Any remaining active tasks are Cancelled with a settlement audit entry. This records payment completion; it does not send money. Closed claims reject further task changes. Assignment and completion dates come from the server; each reassignment records its actor and time separately from the original creation metadata. No SLA duration is assumed. Existing tasks are upgraded to the catalog in place; original names remain in audit history and descriptions are preserved.

The default fictional Insurance Director can manage tasks. Start the backend with `CLAIMS_ACTOR_ROLE=CLAIMS_INSPECTOR` to test the Claims Inspector, who can create and assign tasks without gaining intake/reserve/document editing permissions. `VIEWER` is read-only. A `LOSS_ADJUSTER` can work only on tasks assigned to their server-owned username. Managers can create, assign, and replace another person's task, with the takeover recorded. Final-payment confirmation still belongs to the final task's assignee. These remain server configuration stubs, not authenticated identities. Request bodies cannot select an actor or role. Existing data is preserved; initialization only adds missing demo accounts and missing tasks. See `USER_STORIES.md` for provisional task lifecycle and eligibility rules.


### Testing assignee handoffs

Keep the default Director actor for claim intake and manual task management. To run the backend as Alex, set `CLAIMS_ACTOR_ROLE=LOSS_ADJUSTER` and `CLAIMS_ACTOR_USERNAME=demo-adjuster-alex`; use `demo-adjuster-dana` for Dana. These configure the fictional backend actor; no role or username in a request body grants ownership. Authentication remains deferred.

Open an assigned task, choose **Create handoff and close task**, select the next task name, description, and a different assignee, then save. The old task closes and the new one opens with its predecessor link and the owner's identity recorded. The next assignee can continue it; a task manager can take over and create another replacement. A final **Claim Settlement** exposes **Final payment has been completed** and **Confirm final payment and close claim**. Closing updates claim details and the stack automatically. Task/claim closure dates, actors, and reasons are persisted; retries are idempotent and partial failures roll back the entire workflow.

When upgrading earlier demo data, the newest active task is retained and older parallel tasks are Cancelled with an audited superseding-task reference. No tasks are deleted. A partial unique index enforces one Open/In progress task per claim after this reconciliation. The current active task is highlighted above the history list.
