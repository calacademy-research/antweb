# Adding a new uploader

For admins. Use this when someone writes "I want to upload specimens to AntWeb".

**Page:** Curator Tools, Manager Tools, **Add an uploader** (`https://www.antweb.org/addUploader.do`). Admins only.

## Before you start

- Their **email address**. If they already made an AntWeb account themselves, you can also enter its username.
- **Which upload group** their records belong to. An upload group is the institution, lab or personal collection that owns the specimen records. **An upload replaces all specimens of the uploader's upload group**, so:
  - a second person in a lab that already uploads joins the **existing** upload group only if they upload the lab's whole shared data set;
  - a new institution, lab or personal collection gets a **new** upload group.
- For a new upload group: its **full name** (shown on specimen pages, for example `Archbold Biological Station` or `Curator Kiko Gomez`) and a **short name**: the institution's code from [AntCat's list of institutions](https://antcat.org/institutions) when there is one, otherwise its own abbreviation or the person's surname.

## Steps

1. Open **Add an uploader**.
2. Enter the email address, and first and last name.
3. Choose **Existing upload group** from the list, or **New upload group** and fill in the full and short name.
4. Tick **Can also upload specimen images** if they will upload images too.
5. Click **Add uploader**.
6. Read the summary: what was created or changed.
7. Below the summary is a **Draft email** box: **To** (the person's address), **Subject**, and the text of the welcome email. AntWeb does not send it: you send it from your own email, so you can add what this person needs to know.
   - Click **Copy** and paste the text into a new email to the address shown (the main way). Or click **Open in my email program**: it starts a new email with To, Subject and the text filled in, but some email programs cut a long text short, so check it or use Copy.
   - Add the details for this person, replace **[Your name]** at the end with your name, and send it.
   - When the person had no password yet, the text holds their one-time link to choose one (it says the link is for them only). Send it only to them. The link is not kept anywhere else: once you leave the page for a new uploader, use "Save and make a set-password link" on the login to make a new one.

## What the page does

- Finds the login whose email address or username is what you entered (capitals do not matter). If there is none, it creates one: the username is the email address (or the part before the @ when the address is longer than 32 characters), with no password yet. The welcome email then holds a one-time link (14 days) for them to choose a password.
- Creates the new upload group if you asked for one (the next free number). It refuses a full name or short name that another upload group already has.
- Puts the login in the upload group and gives it the right to upload specimens (and images if ticked). Existing rights are never taken away.
- Stores the email address on an account that had none (accounts people make themselves have no email address on file, so "Forgot your password?" and the upload-complete emails could not reach them).
- Makes the person the upload group's admin curator if the group has none.
- Projects, countries, "Upload as" and administrator rights are not needed for uploading and are not touched.

Nothing is changed when something is wrong (all in one step). The page then says what to fix, for example:

- **already in another upload group**: their uploads would replace the new group's specimens from then on, no longer the old group's. If that is what you want, tick **Move them to this upload group** and click Add uploader again.
- **matches more than one login**: open them in the Login Manager and decide which one to use; then enter its username.
- **no login has the username**: enter the email address instead, to invite them.

## What the uploader does next (the welcome email text)

1. Opens the link and chooses a password (new accounts and inactive accounts), or logs in with their existing password. Someone who was logged in already logs out and in again to see the upload tools.
2. Opens Curator Tools (`/curate.do`).
3. Fills in the specimen template (`/web/homepage/AntWeb_specimen_data_requirements_2026v1.xlsx`). Specimen codes must be unique on AntWeb: their own prefix, not another collection's codes.
4. Saves it as a tab-delimited text file, zips it, and uploads it under **Specimen Data**. Each upload replaces all records of their upload group.
5. Checks the upload report (they also get an email when the upload is finished).

## Other pages

- **Upload groups** (`/viewGroups.do`): all upload groups; open one to change its names or admin curator. Its number cannot be changed. Delete is refused while it has logins or specimens.
- **Login Manager** (`/viewLogins.do`): every other setting of a login (see below). Its **Upload group** list moves a login to another group.

## Editing a login

Login Manager, click the login. Admins only (a curator who opens their own login sees only their name, email address and password, and what they may do as plain text). Change what you need and click **Save changes** at the bottom. Everything is saved in one step; if anything is wrong, the page says what and **nothing** is changed. The next page lists what changed, for example "Upload specimen data: added" or "Project Madagascar (madagascarants): removed", and the same line goes to admin.log. The person sees new rights the next time they log in.

The page has these sections:

- **Account**: username, email address (where password links and upload emails go), first and last name; and, to read only, the login number, when it was created (dates of 14 to 16 October 2026 are not real: they were overwritten then), the last login, whether the account is inactive, and whether it has a password yet. A username or email address another login already has is refused.
- **What this person can do**: **Upload specimen data** (for their upload group; each upload replaces all specimens of that upload group), **Upload specimen images**, and **Administrator** (full access to all tools, all logins and all data; only for AntWeb staff; a red warning shows while it is ticked). You cannot remove your own administrator rights (so you cannot lock yourself out); another administrator can.
- **Upload group**: one list, sorted by name, each shown as "Museum X (MX) — group 71", plus **No upload group**. A login with "Upload specimen data" but no upload group cannot upload; the summary says so.
- **Places and projects this curator can edit**: the projects and countries whose species lists (Species List Tool) and page text this login may change. A country includes its states and provinces. Each assigned one is a row with a **Remove** box. To add one, type part of its name in **Add a project** or **Add a country**, pick it from the list that appears, and click **Add** (repeat for more). Only live projects and valid countries are offered; an assignment to a project that is no longer live, or a country that is no longer valid, stays until you remove it. Administrators can edit every project and country, so they need none here.
- **Other settings**: **Upload as**, login numbers separated by commas. It matters only for an administrator: these logins are listed in the administrator's "Upload as" menu on Curator Tools (upload a file into that login's upload group). Administrators may upload for anyone, so the list only fills the menu. The page shows whose numbers they are.
- **Other actions** (each its own button, so they never save the fields above): **Set a new password** (they are logged out everywhere; usually better: ask them to use "Forgot your password?"), **Log in as** the login, and **Delete this login** (asks first; their uploads and specimens stay; not your own login). A login without a password also has **Save and make a set-password link**.

Login numbers are never reused (since 21 October 2026): a new login gets a number above every number any login ever had, so it never takes over a deleted login's curator folder (`web/curator/<number>/`, with the images that login put into page text) or its name as author of page text. The number shown on the new-login form may therefore change when you save.

Removed in October 2026 because they did nothing or were misleading: the greyed-out "Login ID" box, the "None" entry and the Ctrl-click note of the old project and country lists, the note about the "Changed Password" button, and the password boxes on other people's logins (they were never saved by Done). Before this change, saving another person's login with empty password boxes was refused with "Empty Password".

## "Your session changed"

If AntWeb restarted while the form was open (with "Keep me logged in" you are logged in again at once, in a new session), or you logged in again in another tab, the form's security token no longer matches. Instead of the "Request refused (403)" page, Add an uploader shows the form again with what you typed and the note "Your session changed (AntWeb restarted or you logged in again)". Nothing was done: check the form and press Add uploader again.

Until 21 October 2026 the page had a **Send this email** button that sent the welcome email from antweb@antweb.org. It was replaced by the draft, because each new uploader needs details that only the administrator knows.
