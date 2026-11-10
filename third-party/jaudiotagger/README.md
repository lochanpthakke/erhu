# jaudiotagger distribution source

LGPL-2.1-or-later; original file notices remain controlling. Based on Maven
`net.jthink:jaudiotagger:3.0.1`. See MODIFICATIONS.json for exact upstream hash,
retained file hashes and removals. Erhu claims no ownership of upstream code.

This fork excludes the nine Oracle-noticed tree source files, their nested
classes, the tree package description and five dependent MP4 I/O classes.
AudioFileIO no longer registers MP4/M4A/M4P/M4B readers/writers.
Other format implementations and upstream headers are preserved.

The default 1.0.18 candidate uses this source module, not the Maven binary.
Edit its sources, then rebuild the application using the recipient instructions
in APK_RELEASE_READINESS.md. No production signing credentials are required
for a separately signed installation. Android will not update an existing
installation signed by a different key. Do not uninstall a production app to
try a modified build without first preserving user data.

Library substitution for THIS fork has not yet been compiled or run. Earlier
Stage 3/4 results concern the original library and are not proof for this fork.
