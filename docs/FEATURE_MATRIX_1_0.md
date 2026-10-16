# LAIN_OS 1.0 Capability and Workflow Matrix (50 examples)

**Status:** product-design candidate catalog, not implemented feature inventory.
**Date:** 2026-10-16
**Canonical design:** [API-first three-phase architecture](superpowers/specs/2026-10-16-api-first-three-phase-design.md)
**Flagship:** [Screenshot → crop → group send](superpowers/specs/2026-10-16-screen-crop-group-send-golden-workflow.md)

## Product positioning and truth standard

The owner's requirement is an Android assistant that interprets **one natural-language goal**, decomposes it into many type-safe steps, dynamically sequences dependencies, transfers verified artifacts, requests legitimate approvals, survives interruption, replans safely and returns one true outcome. **No list of commands masquerading as an autonomous agent.**

This table reworks 50 example requests into **planning candidates**. They are **not currently all implemented**, **not all promised for 1.0**, and **not claims that Google Gemini categorically cannot perform them**. Comparative claims require dated, source-backed separate research. A product release advertises only physically verified supported workflows. The phrase "do anything" means an extensible registry able to discover new **authorized** capabilities, not unrestricted Android or remote account access.

**Meaning of readiness:** `P0 GATE` is the owner benchmark required for any complete 1.0 release claim. `P1 GATE` and `P2 GATE` are phased delivery proofs; `Backlog` is a candidate for scope review and future increments, not a shipped promise. `Role-gated` requires explicit supported Android role/user-granted integration, otherwise remains UNSUPPORTED. Every entry's current status is **DESIGN ONLY** until independent implementation/physical evidence is attached.

**Release invariants:** typed and voice entries share a single interface; every final message is interpreted by the configured hosted model API; all side effects go through permission/policy/action verification; one shared canonical conversation records exact steps, errors and assistant reply attempts; no implicit local GGUF inference.

## Fifty example workflows

### Image and screen pipelines

| # | Natural-language target | Illustrative sequence / required adapters | Scope |
|---:|---|---|---|
| 1 | Screenshot → crop → real group message | `screen.capture → image.crop → group.resolve → group.send → group.verify` | P0 GATE |
| 2 | Screenshot → redact → share | `screen.capture → image.redact → share.choose` | Backlog |
| 3 | Compare three apps | `app.navigate → capture.repeat → image.compose` | Backlog |
| 4 | Visual bug report | `screen.capture → image.annotate → github.issue.create` | Backlog |
| 5 | Receipt to ledger | `camera.capture → document.extract → ledger.update → archive` | Backlog |
| 6 | Scroll-and-stitch capture | `screen.capture.sequence → image.stitch` | Backlog |
| 7 | Decode QR from screenshot | `screen.capture → image.qr.decode → uri.validate` | Backlog |
| 8 | Chart to CSV | `screen.capture → chart.extract → file.csv.write` | Backlog |
| 9 | Redacted document to PDF | `camera.capture → document.redact → pdf.create → share` | Backlog |
| 10 | Whiteboard to Markdown | `camera.capture → image.correct → ocr → note.write` | Backlog |

### Files and documents

| # | Natural-language target | Illustrative sequence / required adapters | Scope |
|---:|---|---|---|
| 11 | Organize Downloads | `storage.enumerate → file.classify → file.move` | P1 GATE |
| 12 | Encrypted file backup | `file.read.granted → archive.create → crypto.encrypt → export` | Backlog |
| 13 | Duplicate file cleanup | `file.hash → file.compare → quarantine.move` | Backlog |
| 14 | Batch HEIC conversion | `image.decode → image.convert → archive.write` | Backlog |
| 15 | Extract and merge PDF pages | `pdf.inspect → pdf.extract → pdf.merge → export` | Backlog |
| 16 | Verify and open APK installer | `github.release.fetch → hash.verify → installer.request` | Backlog |
| 17 | File-triggered invoice indexing | `storage.watch → ocr → structured.ledger.write` | Backlog |
| 18 | Package project evidence | `repository.collect → logs.redact → archive.write` | Backlog |
| 19 | Private local knowledge index | `file.read.granted → index.build → retrieval.query` | Backlog |
| 20 | Restore approved project archive | `archive.inspect → file.restore → evidence.report` | Backlog |

### Communication

| # | Natural-language target | Illustrative sequence / required adapters | Scope |
|---:|---|---|---|
| 21 | Create one group conversation | `contacts.resolve → group.create/vetted.provider` | Role-gated |
| 22 | Summarize authorized group messages | `message.read.authorized → summarize` | Role-gated |
| 23 | Compose replies for multiple recipients | `contacts.resolve → drafts → exact approvals → sends` | Backlog |
| 24 | Crop and forward incoming MMS | `message.read.authorized → image.crop → group.send` | Role-gated |
| 25 | Distribute one attachment across channels | `attachment.prepare → SMS/email/Matrix adapters` | Backlog |
| 26 | Extract tasks from accessible messages | `message.read.authorized → intent.extract → task.create` | Backlog |
| 27 | Group photo report | `camera.capture → image.resize → group.send` | Role-gated |
| 28 | Scheduled group report | `report.build → task.schedule → group.send` | Role-gated |
| 29 | Voice memo to messages | `mic.record → STT → draft → approved send` | Backlog |
| 30 | Message submission and delivery reconciliation | `provider.receipt → status.verify → report` | P0 GATE |

### Device actions and context

| # | Natural-language target | Illustrative sequence / required adapters | Scope |
|---:|---|---|---|
| 31 | Context-aware device profile | `volume.set → brightness.request → rotate.request` | Backlog |
| 32 | Battery-saving sequence | `battery.read → authorized.settings → verify` | Backlog |
| 33 | Bluetooth preparation | `bluetooth.discover → connect → app.launch` | Backlog |
| 34 | Location waypoint and route share | `location.consent → map.link → share` | Backlog |
| 35 | Notification triage | `notification.listener.authorized → summarize → reply.intent` | Role-gated |
| 36 | Focus workflow | `DND.role.check → policy.request → reminder` | Backlog |
| 37 | Wi-Fi diagnostics and settings handoff | `network.inspect → diagnose → settings.open` | Backlog |
| 38 | Camera image archive | `camera.capture → classify → scoped.file.write` | Backlog |
| 39 | Device health report | `battery/memory/storage/network.read → report` | P1 GATE |
| 40 | Event-triggered authorized automation | `trigger.subscribe → plan.execute → audit` | Backlog |

### Developer orchestration and recovery

| # | Natural-language target | Illustrative sequence / required adapters | Scope |
|---:|---|---|---|
| 41 | Reproduce GitHub issue on Android | `github.issue.read → apk.test → evidence.collect → comment` | Backlog |
| 42 | Repository maintenance PR | `repo.checkout → tests → patch → github.pr.create` | Backlog |
| 43 | Android app diagnostics | `app.log.authorized → trace.analyze → report` | Backlog |
| 44 | Build, verify and hand off APK | `github.workflow.dispatch → await → checksum.verify → handoff` | P2 GATE |
| 45 | Multi-device authorized task | `peer.identity.verify → capability.invoke → receipt` | Backlog |
| 46 | Bounded application UI tests | `test.launch → approved.accessibility/testhook → asserts` | Role-gated |
| 47 | Record reusable workflow recipe | `plan.record → typed.recipe → revalidate` | Backlog |
| 48 | Failure-aware multi-step replanning | `plan.checkpoint → verify.error → safe.replan` | P2 GATE |
| 49 | Crash-safe durable task resume | `checkpoint.load → effect.reconcile → resume` | P2 GATE |
| 50 | Provenance: what exactly did LAIN do? | `audit.read → action.receipts → explain` | P2 GATE |

## Mandatory 1.0 benchmark suite and evidence gates

1. **Owner flagship (#1):** one screenshot-capture consent → validated crop → exact-recipient group identity/approval → **real group image send** via an authorized transport → verified truthful send status. `message.group.compose` alone is NOT a pass; physically sending multiple individual SMS is NOT a single group message. If direct group send cannot be lawfully implemented on the owner's phone, mark **BLOCKED** rather than declaring the release finished.
2. **Foundation (#11 and #39):** available scoped files are classified and reorganized without traversal or destructive overwrite; a device-health report returns valid structured readings with explicit unsupported fields. Denials affect only relevant capabilities.
3. **Planner (#44, #48, #49):** initiate one bounded external build/test workflow, verify artifact provenance; safe dependency-aware replan of a failed step; no duplicate effects after crash/resume.
4. **Truth (#30 and #50):** independently query operation submission/delivery outcomes, read action IDs, approval scope and audit records; model narration never upgrades LIMITED or UNKNOWN into SENT/DELIVERED.
5. **Chat/voice:** same spoken/typed instruction enters one API intent pipeline and one persistent timeline, with a real model reply on each available turn; network failures remain explicit and retry only safe non-effectful portions.

## Capability-grant feasibility classes

- **NATIVE**: explicit Android SDK APIs, e.g. vibration, battery read, image cropping. Still require device feature/runtime check.
- **OWNER_CONSENT**: user-approved screen capture, scoped files, camera, media pickers, precise location, sensitive share/send.
- **ROLE/PRIVILEGED**: direct MMS, contact/message read, notification listener, Accessibility, and other restricted integrations. Install/configure with Android-owned UX; never imply broad permissions are granted merely because an adapter exists.
- **EXTERNAL_INTEGRATION**: GitHub, email, Matrix, remote peer, authorized HTTP API. Require connector/secret, scopes and data disclosure.
- **UNSUPPORTED**: Android-inaccessible or role-denied operations. The planner must stop or offer an honestly labeled assisted route, not bypass the OS.

For group multimedia transport, default SMS role and carrier/provider behavior must be tested; documented SMS/MMS composition alone cannot prove direct group send. For screenshot capture on modern Android targets, OS consent applies per MediaProjection session. Reference: https://developer.android.com/about/versions/14/behavior-changes-14 and https://developer.android.com/guide/components/intents-common.

## Acceptance metadata required per entry

```yaml
workflow_id: stable-id
owner_goal: natural-language instruction
phase: 1 | 2 | 3
status: DESIGN | IMPLEMENTED_UNVERIFIED | VERIFIED | BLOCKED | UNSUPPORTED
input_modes: [typed, final_stt]
device_api_and_role: explicit
required_capabilities: [validated-capability-ids]
approval_and_privacy: exact owner grants plus cloud disclosure
graph: nodes + typed dependencies + artifact handles
verification: observable trusted receipt schema
negative_tests: denial, missing role, wrong target, retry, process death
physical_evidence: apk_sha + git_commit + handset + real outcomes
```

The build must reject any claim of `VERIFIED` without a human-inspectable proof record tied to the exact implementation. Use separate `COMPOSED`, `SUBMITTED`, `SENT`, `DELIVERED` and `UNKNOWN` statuses for communications.

## Backlog triage

Keep the user's three-phase plan intact: Phase 1 creates an **extensible typed foundation** and safe adapters, Phase 2 composes and runs them as **durable execution graphs**, Phase 3 presents **one integrated conversation**. Do not implement 50 independent workflows before validating the single flagship end-to-end graph. Build shared primitives once; add catalog examples later by composing existing verified capabilities and explicit new adapters.

**This document is a design catalog only. It is not a Gemini feature audit or a claim that these 50 functions work today.**
