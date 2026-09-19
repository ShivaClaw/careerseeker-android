# R8 rules for release builds.
#
# Empty by design right now: nothing in the app yet uses reflection, serialization, or
# JNI. Rules get added alongside the code that needs them (kotlinx.serialization in P1,
# Room in P2), never speculatively -- a keep rule added "just in case" silently disables
# shrinking for whatever it names.
