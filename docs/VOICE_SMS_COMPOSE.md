## Voice-to-SMS composition (TASK-074 slice)

On a clean install with a working Android SMS app, accepted **final** typed
input or speech can use the deliberately bounded, offline deterministic form:

```text
Text +15551234567 saying I'm running late
Send a text to 5551234567 saying hello
Text 5551234567: hello
```

The final utterance becomes an ordinary trusted task, **not** a model-approved
execution request. `android.sms.compose` accepts only a numeric phone number
(3–15 digits with optional leading `+` and safe visual separators) and a
nonempty message (maximum 4096 UTF-8 bytes). Contact names such as `Alex`
require clarification; LAIN does not read contacts or guess their numbers.

The existing TASK-073 voice acceptance, AgentController revision/Stop,
policy, and audit boundaries apply. The action is risk 2 and **always
requires exact-action LAIN approval**, even in Demo mode. Unapproved or
cancelled requests never launch a composer. After approval, Android opens the
installed SMS app with recipient and message populated using
`ACTION_SENDTO`/`smsto:`; the owner reviews the draft and taps **Send** there.
LAIN never sends an SMS itself and **cannot attest to message delivery**.
It does not request `SEND_SMS` or `READ_CONTACTS`, does not bypass the
default SMS app, and marks execution verification **LIMITED**.

### Physical device check (still UNVERIFIED)

1. Install an exact debug APK with these changes. Record APK SHA-256, device,
   OS version, installed SMS app, and microphone provider.
2. In Demo mode, type `Text +15551234567 saying hello` with a test-safe number.
   Verify **PAUSED CONFIRMATION** before any external SMS UI appears. Cancel
   first and confirm nothing opens.
3. Repeat, approve exact-action. Confirm the correct system SMS composer opens
   with one recipient and one body. Dismiss **without tapping Send**.
   Verify LAIN reports command acceptance/LIMITED, never delivered.
4. Repeat from an accepted final speech transcript. Partial or low-confidence
   recognition must execute nothing; replay/rotation must not duplicate the UI.
5. Try a named contact, malformed number, oversized message, and missing SMS app.
   Expect clarification/invalid/unsupported, never a send. Revoke microphone
   and verify typed composition is still usable.

CI Android instrumentation uses an intercepted `startActivity` and therefore
proves only intent handoff and validation, **not** modem/SMS delivery.
