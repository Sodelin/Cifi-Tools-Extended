# Rust math experiments

These authored numerical primitives are research code. They are not a CIFI battle simulator, are not connected to the public app’s site simulation, and do not establish accuracy against the game. `boss_math.rs` contains uncertainty and repeated-attempt calculations; `native_bridge.rs` is an experimental JNI interface.

The Extended unsigned build script packages the JNI math library for ARM64. The included Gradle configuration does not yet build that library.
