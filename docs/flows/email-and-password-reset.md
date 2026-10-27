# Email and password reset

Read from the code on master at commit e81a8945 (2026-10-22). Paths are relative to the repo root; Java classes are under `src/org/calacademy/antweb/`. Traffic: production Tomcat log, 2026-10-11 to 2026-10-18 (8 days): `resetPassword.do` 86, `forgotPassword.do` 18.

## In plain words

AntWeb sends four kinds of email, all from antweb@antweb.org through Amazon SES (us-west-2):

1. **Password links** (transactional, to one person): "Forgot your password?" sends a link to set a new password; an administrator's password-reset campaign and the welcome of a new uploader (Add an uploader, invitations) send the same kind of link; an inactive account that tries to log in, or asks for a reset, gets a reactivation link. The link (`resetPassword.do?token=...`) works once and expires after 24 hours (14 days for campaign and invitation links).
2. **Upload complete** (notice, to the curator who uploaded): the specimen upload's summary with a link to its report; not for CAS (group 1) nor for the nightly TaxonWorks upload; switched off by `etc/upload-email.properties` `enabled=off`. Described in [specimen-upload.md](specimen-upload.md).
3. **Alerts to the administrators** (to `AntwebUtil.getAdminEmail()` / the developer address): server busy, database status, log sizes, admin jobs.
4. Nothing else: there is no newsletter or mail to all users (announcements are in GitHub Discussions).

**Forgot password, step by step (what a visitor sees):**

1. The log-in page links "Forgot your password?" -> `forgotPassword.do`: "Forgot Your Password?", one box: username or email address.
2. Submit. Whatever was typed, the page says "If an account exists for what you entered, we've sent a link to its email address." (it does not tell whether an account exists). An empty box: "Please enter your username or email address." More than 10 requests in an hour from one network: "Too many requests from your network. Please try again in an hour."
3. In the background: the login is looked up by username or email; no login, or a login without an email address: nothing is sent (logged). An inactive account gets a reactivation link instead. At most 3 links per login per hour.
4. The email: subject "AntWeb password reset", the link, the username, valid 24 hours.
5. The link opens `resetPassword.do?token=...`: "Set a New Password" with the rules (at least `PasswordPolicy.MIN_LENGTH` characters, not the username or email, not a common password). Opening the link changes nothing (email scanners open links first). A used, expired or unknown link: "This link is not valid." with "Ask for a new link".
6. Submit the new password twice: it is checked, stored (hashed), the link is used up, the login's other links are cancelled, "must reset" is cleared, every session of the login is ended, an inactive account is active again. "Password set. Please log in with your new password."

Mail settings: `etc/mail.properties` (SES SMTP host, user, password). Without the file nothing is sent and the attempt is written to `emails.log` (content withheld for password links). Limits in `util/Emailer`: alerts 100 a day and one per subject per hour; transactional 2,000 a day; notices 500 a day and 20 per address per day. Every send or refusal is logged in `emails.log` (read through `logMgr.do`).

## URLs

| URL | What it does |
|---|---|
| `forgotPassword.do` (GET) | The form; `Cache-Control: no-store` |
| `forgotPassword.do` (POST `identifier`) | Ask for a link (no form token needed: `Csrf.EXEMPT_PATHS`) |
| `resetPassword.do?token=<43 characters>` (GET) | The new-password form, or "This link is not valid."; `Cache-Control: no-store`, `Referrer-Policy: no-referrer` |
| `resetPassword.do` (POST token, password, retypePassword) | Set the password |
| `passwordResetCampaign.do?action=preview|mark|send` | Admin: the campaign (see the Manager Tools guide) |

## Code path

- `ForgotPasswordAction.execute()`: POST only acts; `HttpUtil.getClientIpAddress()`; `PasswordResetMgr.allowIp()` (10 an hour per address, in memory); `PasswordResetMgr.requestResetAsync(identifier, ip)` (a background thread: `PasswordResetDb.findAccount()`, `InactiveAccountDb.isInactive()` -> `sendReactivationLink()` or `sendResetLink()`). JSP `forgot_password.jsp` + `forgot_password-body.jsp` (title "AntWeb - Password Retrieval"; request attributes `resetState` form|sent, `resetError`).
- `PasswordResetMgr.sendLink()`: `newToken()` (32 random bytes, URL-safe base64, 43 characters), stores only `hashToken()` with the expiry, `Emailer.sendTransactional()`, `logEvent()`.
- `ResetPasswordAction.execute()`: `PasswordResetMgr.isWellFormed(token)`, `handleResetPage(connection, token, isPost, password, retypePassword)` -> state invalid|form|done, `PasswordPolicy`, `LoginDb` (password), session version (ends other sessions), `InactiveAccounts`. JSP `resetPassword.jsp` + `resetPassword-body.jsp` (title "AntWeb - Set a New Password").
- `util/Emailer`: `sendMail()` (alerts), `sendTransactional()` (links), `sendNotice()` (upload complete, Reply-To), all through `deliver()` (JavaMail, SMTP to SES).

## Tables read and written

[`login`](../data-model.md#login) (find the account, the new password, must_reset, the `inactive` column of `InactiveAccounts`, the session version that ends other sessions), [`password_reset`](../data-model.md#password_reset) (token hashes, expiry, used).

## Numbers shown

None.

## Known issues

None found in this pass beyond what is listed in [public-pages-findings.md](public-pages-findings.md) for all public pages.

## Tests

| Test | What it pins |
|---|---|
| `tests/e2e/test_password_reset_pages.py::test_forgot_password_form` | the form, no-store |
| `test_password_reset_pages.py::test_forgot_password_empty_post_is_refused` | an empty request is refused; nothing sent |
| `test_password_reset_pages.py::test_reset_page_with_an_unusable_token` | no token, a malformed and an unknown token: "This link is not valid.", no form, no-store, no Referer |
| `test/org/calacademy/antweb/security/PasswordResetTest.java` (CI) | tokens, hashing, expiry, one use, limits, sessions ended, policy |
| `test/org/calacademy/antweb/security/EmailLinkTest.java`, `InactiveAccountsTest.java` (CI) | the email texts and links, reactivation |
| `tests/unit/test_email_texts.py` | no email tells the reader to ignore it |
| `test_add_uploader.py`, `test_removals_2026_10_21.py` | the uploader welcome (draft email box, no Send button) |
| `test_password_reset_pages.py::test_login_page_links_forgot_password` | the log-in page links forgotPassword.do |

Not tested end to end: a real reset (it would send an email); it can be tried by hand on staging with a test login.
