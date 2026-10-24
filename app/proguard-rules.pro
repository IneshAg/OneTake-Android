# OneTake release (R8) rules. The libraries used (Compose, CameraX, ML Kit, AndroidX lifecycle)
# ship their own consumer rules, and the app itself uses no reflection: AppStore is built by the
# AndroidViewModel factory, whose constructor lifecycle's rules keep, and state is (de)serialised by
# hand with org.json, so nothing needs keeping by name. Add rules here only for a real crash.
