# Fork Notice

This repository is a local adaptation fork of OpenShamrock for QQ `9.2.95`
(versionCode `14200`).

## Source

- Adaptation base: `Mythologyli/OpenShamrock-fix`, commit `238b954`
- Upstream project: `whitechi73/OpenShamrock`
- Original project lineage: Shamrock / OpenShamrock by the Shamrock Team and
  related contributors, including `fuqiuluo` and `whitechi73`

The original README, documentation links, contributor references, and license
notice are kept to preserve upstream attribution.

## License

This fork keeps the upstream GPL license. See [LICENSE](LICENSE).

Unless a file states otherwise, modifications in this fork are distributed under
the same GPL-3.0-or-later terms as the upstream project.

## Local Changes

- Adapted QQNT listener interfaces for QQ `9.2.95`.
- Added minimal runtime type stubs required by QQ `9.2.95` listener signatures.
- Updated Shamrock listener overrides to match the newer QQ runtime interfaces.
- Added an Xposed entry log marker for the QQ `9.2.95` adaptation build.

This fork is provided for learning, research, and self-owned device testing only.
It is not an official OpenShamrock release.
