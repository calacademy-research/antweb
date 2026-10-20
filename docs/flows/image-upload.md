# Image upload: the Image Uploader

Read from the code on master at commit 5b0c1d5 (2026-10-18). Paths are relative to the repo root; Java classes are under `src/org/calacademy/antweb/`. "Check" means the code suggests it but it should be confirmed on staging.

## In plain words

A curator opens the Image Uploader (`imageUploader.do`), picks a photographer ("artist") and selects image files named after the specimen, for example `CASENT0123456_H_1.TIF`. The page sends the files to AntWeb **one at a time**, so no single request comes near Cloudflare's limits (100 MB per request, an answer within 100 seconds).

For every file AntWeb checks the name, that the specimen is already in AntWeb, and, when an image with that name exists, that it belongs to the curator's group. It saves the original in `images/<code>/`, makes four JPEG sizes with ImageMagick (thumbnail, low, medium, high), and records the image. If the file replaces an image, the old original is first copied to `images/backup/` under a dated name.

Files that are not saved (bad name, unknown specimen, another group's image, too large, failed after retries) are listed with the reason under "Not saved" in the upload report. When the curator finishes, AntWeb marks the upload complete, refreshes the "recent images" list on the home page and emails the curator a link to the report.

---

## Who can upload at the same time (locks), in plain words

Written 2026-10-20 at Brian Fisher's request, so the reasons are not lost.

- **Several curators can upload images at the same time.** The old lock that let only one person upload images at a time was removed when the uploader was rebuilt (October 2026). Each file is its own request; `ImageUploadGate` counts how many are being processed.
- **Image uploads pause while a specimen upload or the nightly CAS (TaxonWorks) upload runs.** These share the site-wide "upload in process" flag (`UploadAction.setIsInUploadProcess`). The browser gets "busy, try again in 30 seconds" (503 with `retryAfter`) and retries by itself; the curator does nothing. Reason: two heavy jobs (image conversion with ImageMagick and a full specimen load) at once overload the server. A specimen upload started while images are being processed waits too (with the background job queue, PR #201, it waits in the queue instead of being refused).
- **A short database lock around saving each image's row** (`ImageUploadGate.DB_LOCK`, about a second per file). Reason: `ImageDb.insertImage` numbers a new image as the highest id + 1, so two images saved at the same instant could get the same number and one would be lost. This lock can go if the `image` table's id becomes an auto-increment column assigned by the database (on the fix list).
- **Each file is reserved while it is processed** (`ImageUploadGate.reserve(code/name)`). Reason: if a browser gives up on a slow file and sends it again, the second try waits (409 "busy") instead of backing up the new file over the backup of the original image.

All four are covered by the image upload tests (`tests/e2e/test_image_upload.py`).

## 1. Entry points

| URL | Code | What |
|---|---|---|
| `imageUploader.do` | `imageUploader/ImageUploaderAction` (`WEB-INF/struts-config.xml` line 1486) | Shows the page `web/imageUploader/imageUploader.jsp` (script `imageUploader.js`). Curators with "upload images" (`Login.isUploadImages()`) or admins. A multipart form post is refused with "The Image Uploader needs JavaScript" (`NEEDS_JAVASCRIPT`, line 77): the old one-request uploader was removed in 2026-10. `?action=regenerate` rebuilds the recent-images include. |
| `imageUploadBatch.do?step=start|add|finish` | `imageUploader/ImageUploadBatchAction` (line 1494) | The upload itself, JSON answers. |
| `imageUploadReport.do?id=N` | `imageUploader/ImageUploadReport` (line 1498) | The report page (`imageUploadReport.jsp`); curators. |
| `listImageUploads.do` | struts line 633 | List of image uploads. |

Linked from the Curate page ("Specimen Image Upload", `web/curate/curate-body.jsp` around line 400).

## 2. Naming rules

`ImageUploaded.checkName()` (line 160) and `init()` (line 179). In order:

1. Folder parts are removed (`baseName()`): browsers may send `C:\x\y.jpg`.
2. Not empty, at most 100 characters, only letters, digits, `_`, `.` and `-` (`NAME_CHARS`, line 145).
3. At least one `_` (else "_ and shot type required.").
4. Code = everything before the first `_`; if the shot part looks too long (more than 5 characters between that `_` and the first `.`), the next `_` is used instead, up to twice. This lets codes that contain `_` through.
5. Everything before the first `.` must be upper case ("Filename must be uppercase", line 213).
6. `CODE_SHOT.ext`: shot must be one of D, P, H, L, V, F (`SHOT_TYPES`, line 33; "Unsupported shot type", line 229); number = 1. `CODE_SHOT_NUMBER.ext`: number must be an integer; **the shot letter is not checked in this form** (see the test-gaps file).
7. Extension (lower-cased) `jpg`, `tif` or `png` (line 243). `.jpeg` and `.tiff` are refused.
8. The specimen code (lower-cased) must exist in `specimen` (any group): "Specimen <CODE> is not in AntWeb. Upload its data first." (`ImageUploadBatchAction.checkFile()` line 214).

Examples: `CASENT0123456_H_1.TIF` (code casent0123456, shot h, number 1), `CASENT0123456_P.JPG` (number 1). The original is stored under its uploaded name; the four sizes are `<code>_<shot>_<number>_<size>.jpg` in lower case.

## 3. Ownership rule

`ImageUploadBatchAction.mayReplace()` (line 233), the same rule as deleting images (`DeleteImagesAction`):

- admins: always;
- no image row yet for this code, shot and number (`ImageDb.getImageGroupIds()`, line 261, via `group_image`): allowed, whatever group owns the specimen;
- an image exists: allowed only if every group it belongs to is the curator's group; otherwise "This image already exists and belongs to another group. Ask an AntWeb administrator to replace it."

The new image is recorded under the uploader's group (`image_upload.group_id`, then `group_image`).

## 4. Step by step

All three steps are POST with the form token in the `X-CSRF-Token` header (checked by `SessionRequestFilter`), from a logged-in curator who may upload images (`execute()` lines 92 to 109). Before any step: `Check.init` (not while starting up) and `DBStatus.isServerBusy` (answer 503 "busy", the page waits and retries). Connection from `longConPool`.

### 4.1 Page (browser, `imageUploader.js`)

- Files selected twice, larger than 95 MB (`MAX_FILE_BYTES`, line 83) or empty are marked "not sent" before anything is sent.
- `step=start`, then one `step=add` per file (two at a time, `PARALLEL = 2`; retries on network errors, 5xx and 524: 4 tries, 5 s / 20 s / 60 s apart; "busy" answers wait `retryAfter`), then `step=finish`.
- Pause / Resume, "Retry failed", "Copy failed list" buttons; a warning when leaving the page during an upload.

### 4.2 `step=start` (`start()`, line 144)

1. `artist` must be a known artist id; `names` = one file name per line, at most 2000 (`MAX_NAMES`).
2. Each name is checked (section 2, section 3). Rejected names are returned to the page with their reason.
3. If at least one name is good: a row in `image_upload` (curator, group, artist, current copyright, license, count 0; `ImageUploadDb.saveImageUpload()` line 130), and the rejected names plus the page's own refusals are stored in `image_upload_refused` (source `server` or `browser`).
4. Answer `{"batchId":..,"accepted":..,"rejected":[..],"notSaved":..}`.

### 4.3 `step=add` (`add()`, line 245), one file

1. The `image_upload` row must exist, belong to this login and not be finished (404 / 403 / 409; the file is read and dropped first so the browser sees the answer).
2. Multipart parse to `/usr/local/antweb/temp/images/` (inside the container, not Spaces): one file, at most 95 MB.
3. The name is checked again; a refusal is stored in `image_upload_refused` and answered 422.
4. `ImageUploadGate.reserve(code/name)` (line 64): a retry of the same file while the first try is still running gets 409 "busy".
5. Already saved in this upload (`image_uploaded` row "done"): answered "alreadySaved", nothing done.
6. `ImageUploadGate.enter()` (line 39): sets the site-wide "upload in process" flag (shared with specimen uploads) while at least one image is being processed. If a specimen upload or the nightly TaxonWorks upload holds the flag: 503 "Another upload is running on the server", the page waits and retries.
7. First try only: `ImageUploaded.backupExisting()` (line 108): if `images/<code>/<name>` exists, copy it to `images/backup/<name without ext>.<yyyyMMdd-HHmmss>[-2..].<ext>` (`backupName()` line 133; every replacement keeps its own backup). Then an `image_uploaded` row in state "processing" and the upload's image count (under `ImageUploadGate.DB_LOCK`).
8. Write the file to `images/<code>/<name>` (`setFileItem(..., false)` line 72; `images/` is the Spaces bucket, served from static.antweb.org).
9. `genImages()` (line 259): four sizes, see section 5.
10. Under `DB_LOCK`: `ImageDb.putImage()` (line 314): if an image row exists for code, shot and number, delete it and its `group_image` rows; insert a new `image` row (id = max(id)+1, artist, copyright, license 1, `has_tiff`, `upload_date = now()`, `image_upload_id`) and a `group_image` row. Then `image_uploaded` marked "done" (and `reuploaded` if it replaced a file).
11. Answer `{"status":"saved"|"replaced"|"savedOnRetry",...,"ms":..}`. Temporary files are deleted.

### 4.4 `step=finish` (`finish()`, line 383)

1. Same ownership check.
2. Once only (an upload already complete changes nothing): store the files the page could not send (`refused`), recount, `ImageUploadDb.completeImageUpload()`, rebuild the home page's recent images (`ImageUploaderAction.writeRecentImages()` line 79, writes `web/genInc/recentImages_gen_inc.html`), email the uploader (section 7).
3. Answer `{"images":..,"notSaved":..,"reportUrl":"/imageUploadReport.do?id=N"}`.

`dryRun=1` on any step checks everything (and reads the file on `add`) but saves nothing; the end-to-end tests use it.

## 5. ImageMagick conversion and the policy

- `ImageUploaded.genImage()` (line 277), through im4java `ConvertCmd`: `convert images/<code>/<name> -flatten -resize <box> temp/images/<code>_<shot>_<n>_<size>.jpg`, then the temp file is moved into `images/<code>/` (replacing an older one).
- Sizes requested: thumbview 400x300, low 800x600, med 1200x900, high 2000x1600. The code passes them to `op.resize(height, width)` (line 287); im4java's `resize` takes width first, so the boxes are actually 300x400, 600x800, 900x1200 and 1600x2000 (width x height). Check which is intended.
- Errors from ImageMagick are only logged (`AntwebUtil.log`, line 307 onwards); the file is still answered "saved" and the image row is still written. See the test-gaps file.
- Policy (`docker/antweb/imagemagick-policy.xml`, installed into every `/etc/ImageMagick-*` folder by `docker/antweb/Dockerfile` lines 39 to 45 and checked by CI step "ImageMagick policy" with `tests/container/imagemagick_policy_check.sh`): memory 1 GiB, map 2 GiB, area 256 megapixels, disk 4 GiB, width and height 32K pixels, list length 64, time 300 s per conversion; no delegates (no Ghostscript, curl, etc.); coders and modules that read other files, fetch URLs, run scripts or render text or vector data (MVG, MSL, TEXT, LABEL, URL, HTTP, EPHEMERAL, PS, PDF, SVG and others) are off; no `@file` lists. TIFF, JPEG and PNG convert normally.
- Temporary space: `/usr/local/antweb/temp/images` is inside the container (the host disk), on purpose: Spaces is too slow for temp files.

Timing: a normal TIFF answers in seconds; Brian's staging test of 50 TIFFs (310 MB) went through in one upload (report 6653, 2026-10-17).

## 6. Not saved files (`image_upload_refused`)

- Table created on startup if missing (`ImageUploadRefusedDb.ensureSchema()` line 75; same DDL in `db/upgrade/8.111/2026-10-18-image-upload-refused.sql`): `image_upload_id`, `filename` (255), `reason` (300), `source` (`server` or `browser`), `created`; one row per file name per upload (unique key), at most 2000 per upload (`MAX_PER_UPLOAD`).
- Written at start (rejected names and the page's refusals), at add (a name refused by the server) and at finish (files the page gave up on).
- Names and reasons from the page are cleaned and cut (`parse()` line 110) and escaped on the report; they are never used as file names.
- The report and the email leave out any name that was saved in the same upload (for example on a later try).

## 7. Report and email

- Report: `imageUploadReport.do?id=N` shows the upload (curator, group, artist, date, complete), each saved image with a link to its specimen, and "Not saved" with reasons. Login required (curator). A replaced image is marked "Duplicate" with a link to `/images/backup/<file name>` (`imageUploadReport-body.jsp` line 177); since backups get a dated name (section 4.3 step 7) that link no longer finds the file.
- Email: `UploadNotice` with `images = true` (`ImageUploadBatchAction.sendNotice()` line 419): subject "AntWeb: your image upload is complete (N images)", counts of images saved and not saved, report link (needs login). Same switch and exclusions as specimen uploads ([specimen-upload.md](specimen-upload.md) section 6); CAS (group 1) gets no mail by default.

## 8. What can fail and how it shows

| What | How it shows |
|---|---|
| Not logged in (session expired) | 401; the page pauses and asks to log in again in another tab, then Resume |
| Form token refused (logged in again elsewhere) | 403 HTML; the page pauses with a message |
| Specimen upload or nightly TaxonWorks upload running | 503 busy; the page waits and retries |
| Server busy, starting up, no DB connection | 503 busy, retried |
| Network cut while sending, 524, 5xx | retried up to 4 times, then "failed" and listed as not saved at finish |
| File over 95 MB | refused by the page (never sent) or 413 |
| Bad name, unknown specimen, another group's image | refused with the reason; listed under "Not saved" |
| ImageMagick fails (corrupt TIFF, over a policy limit) | logged only; the file counts as saved; sizes missing or old (check) |
| Database insert of the image row fails | logged only (`ImageDb.insertImage()` line 352 catches the exception); the file counts as saved (check) |

## 9. Tables and files written

| Where | What |
|---|---|
| `image_upload` | one row per upload; count; complete flag |
| `image_uploaded` | one row per file (processing, done, reuploaded) |
| `image_upload_refused` | files not saved |
| `image`, `group_image` | the image row (deleted and re-inserted with a new id on replacement) |
| `images/<code>/` (Spaces) | original and four JPEG sizes |
| `images/backup/` (Spaces) | earlier original of a replaced image |
| `web/genInc/recentImages_gen_inc.html` | home page recent images |

Image uploads are not stopped by the nightly sets (they do not call `Check.compute`), only by an upload holding the flag.
