Notes for the next release, collected as changes go live. At release time this text moves into release-notes/X.Y.Z.md.

### API changes
- `/distinctTaxa?taxarank=species` now lists each species once by its full name, "Genus species" (for example "Pheidole megacephala"). Before, it gave only the second part of the name ("megacephala"), so names shared by several genera were mixed together.
- `country=` now matches the whole country name, in upper or lower case, on every API route that has it (`/specimens`, `/distinctTaxa`, `/measurements`, `/geolocaleTaxa`). Before, `/specimens`, `/distinctTaxa` and `/measurements` matched any country containing the text, so `country=Niger` also returned Nigeria. If you relied on partial names, use the full country name.
