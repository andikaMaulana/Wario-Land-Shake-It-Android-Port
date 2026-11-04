#!/usr/bin/env bash
# WarioRecomp: builds the Android APK (experimental) from a finished translation (generated/, see
# tools/build-wario.sh). The APK holds code and data from your game: keep it for your own devices.
#
# Usage: tools/build-android.sh [--jobs N]
# Needs: the Android SDK with NDK 28.2 (ANDROID_HOME), cmake, ninja, JDK 17 and Gradle 8.9+ (a Gradle
# that Android Studio already downloaded is enough).
# Output: android/app/build/outputs/apk/release/app-release.apk
set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
build="$root/build/android-arm64"
jobs="$(nproc)"
nod_version="v2.0.0-alpha.10"
nod_sha256="b0d94617ed2393334845669cc1f74f1e4fce0d6acb24e8be28c479689a1b907b"
[[ "${1:-}" == "--jobs" ]] && jobs="$2"

sdk="${ANDROID_HOME:-$HOME/Android/Sdk}"
ndk="$sdk/ndk/28.2.13676358"
[[ -d "$ndk" ]] || { echo "NDK not found: $ndk" >&2; exit 1; }
[[ -f "$root/generated/build_shards/shards.cmake" ]] || { echo "No translation: run tools/build-wario.sh first" >&2; exit 1; }

ram_gb=$(( $(awk '/MemTotal/ {print $2}' /proc/meminfo) / 1024 / 1024 ))
shard_jobs=$(( (ram_gb - 4) / 2 )); (( shard_jobs < 1 )) && shard_jobs=1; (( shard_jobs > jobs )) && shard_jobs=$jobs

cmake -S "$root/runtime" -B "$build" -G Ninja \
    -DCMAKE_TOOLCHAIN_FILE="$ndk/build/cmake/android.toolchain.cmake" \
    -DANDROID_ABI=arm64-v8a -DANDROID_PLATFORM=android-30 -DANDROID_STL=c++_static \
    -DCMAKE_BUILD_TYPE=Release -DAURORA_DAWN_PROVIDER=package -DAURORA_SDL3_PROVIDER=vendor \
    -DCMAKE_POLICY_DEFAULT_CMP0168=NEW -DMKW_TRANSLATED_COMPILE_JOBS="$shard_jobs"
cmake --build "$build" --target WiiCompiled --parallel "$jobs"

# Stage what the Gradle project packages (android/app/build.gradle).
apk="$build/apk"
rm -rf "$apk"
mkdir -p "$apk/jniLibs/arm64-v8a" "$apk/assets/runtime"
strip="$ndk/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-strip"
"$strip" --strip-unneeded -o "$apk/jniLibs/arm64-v8a/libmain.so" "$build/libmain.so"
"$strip" --strip-unneeded -o "$apk/jniLibs/arm64-v8a/libpng16.so" "$(readlink -f "$build/_deps/png-build/libpng16.so")"
# The hooks libadrenotools loads to swap in a custom GPU driver.
for hook in main_hook hook_impl; do
    "$strip" --strip-unneeded -o "$apk/jniLibs/arm64-v8a/lib$hook.so" "$(find "$build/_deps" -name "lib$hook.so" | head -n 1)"
done
cp -r "$build/dsp_coef.bin" "$build/cacert.pem" "$build/initial_pipeline_cache.db" "$build/wii_bootstrap" "$apk/assets/runtime/"

# nodtool unpacks disc images in the launcher. It is a static binary, and Android only lets an app run
# files from its library folder, hence the name.
nod="$build/nodtool-linux-aarch64-$nod_version"
if [[ ! -f "$nod" ]]; then
    curl -fL --retry 3 -o "$nod" "https://github.com/encounter/nod/releases/download/$nod_version/nodtool-linux-aarch64"
fi
[[ "$(sha256sum "$nod" | cut -d' ' -f1)" == "$nod_sha256" ]] || { echo "nodtool: SHA-256 mismatch" >&2; rm -f "$nod"; exit 1; }
cp "$nod" "$apk/jniLibs/arm64-v8a/libnodtool.so"

# SDL's Java, with what the native side relies on. Aurora waits to be told when the surface exists
# (aurora/lib/window.cpp), so without these calls the game never draws.
cp -r "$build/_deps/sdl-src/android-project/app/src/main/java" "$apk/sdl-java"
surface="$apk/sdl-java/org/libsdl/app/SDLSurface.java"
sed -i \
    -e 's/^ *SensorEventListener, ScaleGestureDetector.OnScaleGestureListener {$/&\n    private static native void auroraNativeSetSurfaceReady(boolean ready);/' \
    -e 's/^\( *\)mIsSurfaceReady = true;/&\n\1auroraNativeSetSurfaceReady(true);/' \
    -e 's/^\( *\)Log.v("SDL", "surfaceDestroyed()");/&\n\1auroraNativeSetSurfaceReady(false);/' \
    "$surface"
[[ "$(grep -c 'auroraNativeSetSurfaceReady' "$surface")" == 3 ]] || { echo "Could not patch $surface" >&2; exit 1; }
# Guest fiber stacks are carved from this thread's stack (runtime/src/host_context.cpp).
activity="$apk/sdl-java/org/libsdl/app/SDLActivity.java"
sed -i 's/new Thread(new SDLMain(), "SDLThread")/new Thread(null, new SDLMain(), "SDLThread", 64L * 1024 * 1024)/' "$activity"
grep -q '64L \* 1024 \* 1024' "$activity" || { echo "Could not patch $activity" >&2; exit 1; }

gradle="$(command -v gradle || ls -d "$HOME"/.gradle/wrapper/dists/gradle-8.1[1-9]*-bin/*/gradle-*/bin/gradle 2> /dev/null | tail -n 1 || true)"
[[ -n "$gradle" ]] || { echo "Gradle 8.9+ not found" >&2; exit 1; }
for jdk in "${JAVA_HOME:-}" /usr/lib/jvm/java-17-openjdk-amd64; do
    [[ -x "$jdk/bin/javac" ]] && { export JAVA_HOME="$jdk"; break; }
done
export ANDROID_HOME="$sdk"
"$gradle" -p "$root/android" --no-daemon assembleRelease
echo "APK: $root/android/app/build/outputs/apk/release/app-release.apk"
