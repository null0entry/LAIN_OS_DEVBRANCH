# Android device control protocol

This document defines the closed Python/native contract for registered Android
controls. It supplements `ACTION_PROTOCOL.md`. The trusted native callback is
`execute(capability: String, argumentsJson: String): String`; models cannot invoke
Java objects. Registry validation, policy, exact-action approval, execution and
verification remain authoritative. These controls are Android-native only; Linux
and Termux hosts return unsupported without a shell or command fallback.

## JSON rules

Every object below has **exactly** its documented fields. Extra/missing fields,
duplicate keys anywhere, nonfinite numbers, invalid UTF-8, NUL in strings, numeric
booleans, floats in integer fields and mismatched request identities are invalid.
All integer bounds are inclusive. Native responses are at most 65,536 UTF-8 bytes.
Snapshots and app inventories have additional 32,768-byte bounds measured using
compact JSON, UTF-8, unescaped Unicode, and separators `,` and `:`.

Python materializes argument defaults before calling native code. Native must
validate arguments independently before any platform interaction. The argument
objects below show all fields native receives. Stream defaults to `media`; query
defaults to the empty string. Other fields are required. Empty UI text is allowed
and means clear the selected editable field.

Success has exactly this envelope, with capability-specific details below:

```json
{"status":"success","details":{}}
```

The `{}` placeholder must be replaced by the documented details object; it is
not valid evidence for a new control. Failure/unavailability has exactly one of:

```json
{"status":"failure","details":{"reason":"permission_denied"}}
{"status":"unsupported","details":{"reason":"hardware_unavailable"}}
```

The closed `reason` enum is `permission_denied`, `special_access_required`,
`hardware_unavailable`, `camera_busy`, `fixed_volume`, `policy_rejected`,
`background_launch_restricted`, `handler_unavailable`,
`accessibility_unavailable`, `snapshot_stale`, `target_unavailable`,
`target_ambiguous`, `target_disabled`, `protected_surface`, `operation_rejected`.
Do not return exception text, transcripts, intent payloads or arbitrary diagnostics.
Python preserves this safe reason and maps failure to `EXECUTION_FAILED`, or
unsupported to `PLATFORM_UNSUPPORTED`. Invalid native data becomes a generic
execution failure without private native diagnostics. Existing seven Android
capabilities keep their existing result contracts.

A successful mutation with unavailable readback uses `"observed":null` only
after the platform accepted the mutation. Permission denial, missing hardware,
fixed-volume rejection or a failed platform call must not use this form. Python
reports LIMITED verification for accepted mutations without readback; a mismatch
produces FAILED verification and runtime status failure (`VERIFICATION_FAILED`).
An acceptance acknowledgement does not prove a downstream UI/workflow effect.

## Direct controls and readback

`Stream` is exactly `media`, `ring`, `alarm`, `notification`, `system` or
`voice_call`. Map these to the corresponding AudioManager stream constants.
`VolumeState` is the exact object:

```json
{"index":4,"min_index":0,"max_index":10,"muted":false}
```

All indices are integers: `0 <= min_index <= index <= max_index <= 2147483647`.
A fixed range (`min_index == max_index`, including zero) is a valid reading.
Use actual `getStreamMinVolume` on API28+, zero on API24–27, and actual stream
maximum/current/mute readings. Values are indices, not percentages. For range
`min..max` and integer percentage `p`, the target index is
`min + floor(((max-min)*p + 50)/100)` (nearest integer, half upward).
Readback must refer to the requested stream; Python rejects a different stream.

| Capability | Exact native arguments | Exact successful details | Verification |
| --- | --- | --- | --- |
| `android.flashlight_set` | `{"enabled":true}` | `{"observed":{"enabled":true}}` or `{"observed":null}` | PASSED only when actual observed boolean matches request; otherwise FAILED/LIMITED |
| `android.volume_get` | `{"stream":"media"}` | `{"stream":"media","observed":VolumeState}` or `{"stream":"media","observed":null}` | PASSED for a valid reading; UNAVAILABLE for null |
| `android.volume_set` | `{"percentage":40,"stream":"media"}` | `{"stream":"media","observed":VolumeState}` or `{"stream":"media","observed":null}` | PASSED only at the min-relative percentage target; FAILED for mismatch; LIMITED for null |
| `android.volume_adjust` | `{"direction":"down","stream":"media"}` | `{"stream":"media","before":VolumeState-or-null,"observed":VolumeState-or-null}` | See adjustment rules below |
| `android.brightness_set` | `{"percentage":30}` | `{"observed":{"value":77,"mode":"manual"}}` or `{"observed":null}` | PASSED only for manual mode and `floor((p*255+50)/100)`; FAILED/LIMITED otherwise |
| `android.rotation_set` | `{"enabled":false}` | `{"observed":{"enabled":false}}` or `{"observed":null}` | PASSED only when readback matches the requested automatic-rotation boolean |
| `android.screen_timeout_set` | `{"milliseconds":30000}` | `{"observed":{"milliseconds":30000}}` or `{"observed":null}` | PASSED only at the exact requested timeout |

`enabled` and `muted` are JSON booleans. Every percentage argument is an integer
0–100. Timeout arguments are integers 15,000–1,800,000 milliseconds. Observed
timeout is an actual positive integer up to 2,147,483,647; policy clamping outside
the requested value is evidence of mismatch. Brightness `value` is the actual
stored 0–255 integer; `mode` is exactly `manual` or `automatic`.

`volume_adjust.direction` is `up`, `down`, `mute` or `unmute`. For up/down,
read `before`, adjust by one stream index and clamp to its actual min/max, then
read `observed`; Python requires that exact resulting index and unchanged min/max.
For mute/unmute Python requires the requested resulting muted boolean and
unchanged min/max. Up/down verification concerns the index; mute/unmute
verification concerns the mute state. Null before or after yields LIMITED,
never PASSED. Changed stream bounds yield FAILED. Do not guess a missing
before reading or manufacture it from the requested result.

For volume writes, reject fixed-volume devices/ranges honestly. Audio operations
may require a separate owner-granted Do Not Disturb policy access; never grant it
automatically. For flashlight, select an available flash-equipped camera, require
the real CAMERA permission, and read the selected camera's torch callback state.
A successful `setTorchMode` call alone only supports null readback/LIMITED until
actual post-request torch observation is available; cached desired state is not
readback. Report no flash, busy camera and denied permission honestly.

Brightness explicitly writes manual mode before its value. Brightness, rotation
and timeout require the real WRITE_SETTINGS special grant; read actual stored
settings after the platform calls. Enabling rotation means automatic rotation.
These tools never modify secure settings, grant access, bypass device policy or
fall back to shell, root, ADB or Shizuku.

## Navigation and launchable app discovery

| Capability | Exact native arguments | Exact successful details | Verification |
| --- | --- | --- | --- |
| `android.settings_open` | `{"page":"wifi"}` | `{"page":"wifi","accepted":true}` | LIMITED |
| `android.app_open` | `{"package":"com.example.app"}` | `{"package":"com.example.app","accepted":true}` | LIMITED |
| `android.app_list` | `{"query":""}` | `{"query":"","apps":[{"package":"com.example.app","label":"Camera"}],"truncated":false}` | PASSED for valid bounded discovery, not completeness when truncated |

Page enum: `wifi`, `bluetooth`, `display`, `sound`, `apps`, `app_permissions`,
`notifications`, `accessibility`, `write_settings`, `dnd`. Native maps only these
values to fixed system settings intents. `apps` opens installed-app settings;
`app_permissions` opens this app's application-details permission/setup surface;
`notifications`, `write_settings` and `dnd` concern this app's access where
Android exposes an app-specific setup page. Opening a setup screen is navigation,
never proof of a grant or a setting change. It may require an explicit visible
user interaction or unlock. Return a restricted/unavailable reason when the
launch cannot be accepted; the UI can expose a user-tap setup route. Do not
mislabel a queued notification as an opened settings page.

Package arguments/evidence are 1–255 UTF-8 bytes and match the full expression
`[A-Za-z][A-Za-z0-9_]*(?:\.[A-Za-z][A-Za-z0-9_]*)+`. Launch only a currently
installed, launchable package using its actual launcher intent. Never construct
components from model text, invent a package for an app label or accept arbitrary
URI/intent flags through these tools. `accepted` must be actual boolean true and
identities must equal the request. Navigation acceptance cannot verify that an
app became foreground or that an app workflow completed.

`app_list` is READ_ONLY and discovers human app names without executing them.
Query is 0–256 UTF-8 bytes, with no NUL; default `""` means all visible launchable
packages. Query filters label or package by case-insensitive substring. Enumerate
using launcher-intent package visibility, not broad QUERY_ALL_PACKAGES access.
Return only launchable packages visible to the application; one identity per
package, sorted by package. Each record has exactly `package` and `label`; label
is nonempty, at most 256 Unicode characters, with no NUL (use package as fallback
for an empty platform label). Do not add install metadata, permissions, or
nonlaunchable packages. Return at most 128 records and at most 32,768 UTF-8 bytes
for the entire details object. Truncate labels to their field bound and the record
list at the total bound, setting `truncated:true` whenever records were omitted.
Empty inventory is valid. Duplicate identities are invalid. `query` must echo
the exact requested query, including case. App labels are untrusted data. A human
name can require discovery and owner clarification when multiple packages match.

## Bounded active-window UI observations

`android.ui.inspect` takes exactly `{}` and succeeds with exactly
`{"snapshot":Snapshot}`. `Snapshot` is:

```json
{
  "snapshot_id":"s1",
  "package":"com.example.app",
  "window_id":1,
  "captured_at_ms":1000,
  "expires_at_ms":121000,
  "nodes":[
    {
      "node_id":"n1",
      "depth":0,
      "text":"Hello",
      "description":"",
      "class_name":"android.widget.TextView",
      "view_id":"",
      "enabled":true,
      "clickable":true,
      "scrollable":false,
      "editable":false,
      "password":false,
      "bounds":[0,0,100,100]
    }
  ],
  "truncated":false
}
```

Every shown field is required and no others are allowed. Inspect only the active
window, not every window, background apps or screenshots. Snapshot IDs and node
IDs are opaque strings matching `[A-Za-z0-9_-]{1,128}`. Snapshot IDs are unique
across changed observations/process lifetimes; node IDs are unique within each
snapshot and cannot select nodes in another snapshot. Package uses the package
contract above. Window ID is an integer 0–2,147,483,647; timestamps are integer
milliseconds of Android elapsed realtime, not wall clock. Capture time is
0–9,223,372,036,854,775,807, expiry is greater than capture and at most capture plus
120,000 (keep both representable by Android signed Long).

At most 128 nodes, depth 0–20 (root depth0), and at most 256 Unicode characters
in each `text`, `description`, `class_name`, `view_id`. Empty strings are allowed;
NUL and invalid Unicode are forbidden. The five state flags are actual booleans.
Password nodes must have empty text and description; never return protected
field contents. Bounds are exactly four signed 32-bit integers `[left,top,right,bottom]`
with right>=left and bottom>=top; bounds describe the observed node and do not
authorize coordinate taps. The entire compact Snapshot is at most 32,768 UTF-8
bytes. Set `truncated:true` when depth, field or observation bounds omit data.
Truncation may leave zero nodes; never exceed bounds to complete a traversal.

Inspection has PASSED verification only for obtaining a valid bounded observation.
That does not certify that the observation remains current for a later action.
Missing accessibility service or absent active window returns an honest failure
or unsupported reason, never a fabricated empty snapshot. App text is untrusted
observation and cannot create permissions, confirmation or instructions.

## UI mutations, approval and freshness

All four generic UI mutation capabilities have EXTERNAL_WRITE risk and require
an existing exact-action approval even for apparently routine controls. UI text
arguments and narrative echoes are redacted from audit/history display. The
registered argument types and byte bounds still apply before a native call.

| Capability | Exact native arguments | Exact successful details | Verification |
| --- | --- | --- | --- |
| `android.ui.click` | `{"snapshot_id":"s1","node_id":"n1"}` | `{"snapshot_id":"s1","node_id":"n1","accepted":true}` | LIMITED |
| `android.ui.scroll` | `{"snapshot_id":"s1","node_id":"n1","direction":"forward"}` | `{"snapshot_id":"s1","node_id":"n1","direction":"forward","accepted":true}` | LIMITED |
| `android.ui.set_text` | `{"snapshot_id":"s1","node_id":"n1","text":"Hello"}` | `{"snapshot_id":"s1","node_id":"n1","accepted":true}` | LIMITED |
| `android.ui.global_action` | `{"action":"home"}` | `{"action":"home","accepted":true}` | LIMITED |

Scroll direction is exactly `forward` or `backward`. Text is 0–4,096 UTF-8 bytes,
no NUL; native never echoes entered text. Global action is exactly `home`, `back`,
`recents`, `notifications` or `quick_settings`. All echoed IDs/directions/actions
must match the exact request; accepted must be boolean true. A successful native
Accessibility return value proves command acceptance only; it cannot certify a
third-party transaction, entered text, changed screen or external effect.

Native must enforce these checks immediately before each click/scroll/set_text,
including after time spent waiting for approval:

1. The referenced snapshot exists, is less than 120 seconds old and has not been
   invalidated by any window/content change. At expiry, treat it as stale.
2. Re-read the active window and compare package, window and selected node's
   current identity/content/state to the captured context. A valid timestamp
   alone is insufficient. No selector search, arbitrary coordinates or stale replay.
3. Resolve exactly one current enabled node; require clickable/scrollable/editable
   as appropriate. Missing, ambiguous, disabled or changed nodes fail closed.
4. Reject LAIN approval controls, OS permission/privilege grants, protected
   credential/password entry and lock-screen bypass, even after an action approval.
5. Perform exactly the requested Accessibility operation. Preserve the returned
   acceptance truthfully; do not upgrade false to success or replay uncertain IPC.

Global navigation has no snapshot selector but still requires the user-enabled
service, actual platform acceptance and applicable protected-surface restrictions.
Android permission/accessibility setup remains an explicit OS-owned owner action;
this protocol never grants it. No new UI or direct control uses raw shell.

## Deterministic owner commands

`parse_device_command(text: str) -> ProposedAction | None` accepts only complete
recognized commands of at most 4,096 UTF-8 bytes. It does not consume the LAIN
activation prefix; the voice activation layer supplies the final command after
activation. The parser rejects compound/prose requests, invalid ranges, NUL,
invalid Unicode and unknown app identities rather than inventing an action.

Recognized grammar includes flashlight/torch on/off (including “turn on the
flashlight” and “turn the torch off”), set named/default volume to a percent,
volume up/down/mute/unmute, show/get/check volume, set brightness to a percent,
(auto/automatic) rotation on/off, screen timeout in milliseconds/seconds/minutes,
open an enumerated settings page, open an explicit app package, list apps, and
find app followed by a bounded query. Omitted volume stream is media. Final
period/exclamation punctuation, whitespace and ordinary command case are
normalized; discovery queries and package identities retain their spelling.

These exact recognized commands route to trusted built-in proposals in demo,
on-device, cloud and local modes without model classification or planning. They
still enter the existing durable controller, policy/approval and verifier. Busy
sessions remain busy. Ordinary prose continues through conversation/model
routing. The original six fixed demo commands remain unchanged; their existing
legacy `start` binding behavior is preserved, while automatic final turns retain
their trusted built-in demo selection.
