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
7. Read the **welcome email** shown below the summary. If it is right, click **Send this email**. It goes from antweb@antweb.org; replies go to that address, which forwards to the AntWeb lead.

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

## What the uploader does next (the welcome email)

1. Opens the link and chooses a password (new accounts and inactive accounts), or logs in with their existing password. Someone who was logged in already logs out and in again to see the upload tools.
2. Opens Curator Tools (`/curate.do`).
3. Fills in the specimen template (`/web/homepage/AntWeb_specimen_data_requirements_2026v1.xlsx`). Specimen codes must be unique on AntWeb: their own prefix, not another collection's codes.
4. Saves it as a tab-delimited text file, zips it, and uploads it under **Specimen Data**. Each upload replaces all records of their upload group.
5. Checks the upload report (they also get an email when the upload is finished).

## Other pages

- **Upload groups** (`/viewGroups.do`): all upload groups; open one to change its names or admin curator. Its number cannot be changed. Delete is refused while it has logins or specimens.
- **Login Manager** (`/viewLogins.do`): every other setting of a login. Its **Upload group** list moves a login to another group.

## "Your session changed"

If AntWeb restarted while the form was open (with "Keep me logged in" you are logged in again at once, in a new session), or you logged in again in another tab, the form's security token no longer matches. Instead of the "Request refused (403)" page, Add an uploader shows the form again with what you typed and the note "Your session changed (AntWeb restarted or you logged in again)". Nothing was done: check the form and press Add uploader (or Send) again.
