# Data flows

Step-by-step descriptions of how data moves through AntWeb, with the class, method and file names and line numbers to start from. Each file opens with a plain-language summary, then the technical detail. The upload and nightly flows were written from the code on master at 5b0c1d5 (2026-10-18), the public page flows at e81a8945 (2026-10-22); line numbers drift as the code changes, method names do not.

| Flow | What it covers |
|---|---|
| [specimen-upload.md](specimen-upload.md) | Curator specimen file upload (`upload.do?action=specimenUpload`), the manual TaxonWorks upload and the nightly TaxonWorks job: file formats, checks, name resolution to the current valid AntCat name ("Uploaded As"), what is deleted and inserted, what is computed afterwards, the upload report, the upload-complete email, the 95% guard, what can fail |
| [image-upload.md](image-upload.md) | The Image Uploader (`imageUploadBatch.do`, one file per request): naming rules, ownership, backups of replaced images, ImageMagick and its policy, files not saved, report and email |
| [antcat-to-antweb.md](antcat-to-antweb.md) | The AntCat export (antcat-export server, 03:00) and the AntCat (WorldAnts) import in nightly set 1: size guard, tables changed, species lists moved to current valid names, and what does not happen (existing specimens are not re-resolved) |
| [nightly.md](nightly.md) | The 05:00 Scheduler and sets 1 to 5 step by step, restart logic, staging differences, running a set by hand, logs |
| [test-gaps-uploads-nightly.md](test-gaps-uploads-nightly.md) | Existing tests per step, proposed staging-only tests ranked by value, bugs and dead code found, feature backlog candidates, open questions |

### Public pages and the API (road map step 2, 2026-10-22, from master at e81a8945)

In order of traffic (production Tomcat log, 8 days). Each file: who uses it, the URLs, the code path (Action, Db classes, JSP), the tables read ([data-model.md](../data-model.md)), the numbers shown ([numbers.md](../numbers.md)), caching, the answers to unknown names, known issues and the tests that cover it.

| Flow | What it covers |
|---|---|
| [taxon-pages.md](taxon-pages.md) | `description.do`, `browse.do`, `images.do` at subfamily, genus, species and subspecies rank: sections, default images, maps, the place filter (project, country, bioregion ...; kept in the session), the header Taxa box, taxonName and antcatId links, no page cache |
| [specimen-and-image-pages.md](specimen-and-image-pages.md) | `specimen.do` (with Name History), `specimenImages.do`, `bigPicture.do`, `locality.do`, `collection.do` |
| [places.md](places.md) | Maps (`bigMap.do`), country, adm1, region, subregion, island, bioregion, museum and project pages and their lists, species lists (`taxonomicPage.do`), field guides, the image and map comparisons, the site-wide simultaneous limits |
| [search.md](search.md) | The header Taxa and Places boxes, advanced search (specimen list, by taxon, map), paging and download guards, recent images |
| [api.md](api.md) | API v3.1: every route, its parameters, what it returns and from which tables, the 500 cap, offset paging, `up=1`, unknown parameters |
| [email-and-password-reset.md](email-and-password-reset.md) | Every email AntWeb sends, Forgot password and the reset link page step by step, limits and logs |
| [home-and-statistics.md](home-and-statistics.md) | The home page and its generated parts, `statsPage.do`, `taxaList.do` |
| [public-pages-findings.md](public-pages-findings.md) | Bugs found in this pass, with severity and the xfail test that documents each |

Still to come (documentation plan, later steps): inactive accounts, curator and admin tools beyond the guides.
