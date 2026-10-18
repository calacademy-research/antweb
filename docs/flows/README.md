# Data flows

Step-by-step descriptions of how data moves through AntWeb, with the class, method and file names and line numbers to start from. Each file opens with a plain-language summary, then the technical detail. Written from the code on master at 5b0c1d5 (2026-10-18); line numbers drift as the code changes, method names do not.

| Flow | What it covers |
|---|---|
| [specimen-upload.md](specimen-upload.md) | Curator specimen file upload (`upload.do?action=specimenUpload`), the manual TaxonWorks upload and the nightly TaxonWorks job: file formats, checks, name resolution to the current valid AntCat name ("Uploaded As"), what is deleted and inserted, what is computed afterwards, the upload report, the upload-complete email, the 95% guard, what can fail |
| [image-upload.md](image-upload.md) | The Image Uploader (`imageUploadBatch.do`, one file per request): naming rules, ownership, backups of replaced images, ImageMagick and its policy, files not saved, report and email |
| [antcat-to-antweb.md](antcat-to-antweb.md) | The AntCat export (antcat-export server, 03:00) and the AntCat (WorldAnts) import in nightly set 1: size guard, tables changed, species lists moved to current valid names, and what does not happen (existing specimens are not re-resolved) |
| [nightly.md](nightly.md) | The 05:00 Scheduler and sets 1 to 5 step by step, restart logic, staging differences, running a set by hand, logs |
| [test-gaps-uploads-nightly.md](test-gaps-uploads-nightly.md) | Existing tests per step, proposed staging-only tests ranked by value, bugs and dead code found, feature backlog candidates, open questions |

Still to come (documentation plan, later steps): the API, email and password reset, inactive accounts.
