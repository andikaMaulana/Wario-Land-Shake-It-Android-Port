// custom_driver.cpp - Loads the Vulkan driver the player picked in the Android launcher.
//
// Dawn opens libvulkan.so with a plain dlopen. The product links with --wrap=dlopen so that call lands
// here: with a driver chosen, libadrenotools opens a private copy of the system loader that uses it.
// The system loader has to stay in the path because it implements the Android swapchain.
#if defined(__ANDROID__)

#include <adrenotools/driver.h>
#include <android/log.h>
#include <dlfcn.h>

#include <cstdlib>
#include <cstring>

extern "C" void* __real_dlopen(const char* file, int flags);

namespace {

constexpr const char* kTag = "WarioRecomp";

void* OpenCustomDriver(int flags) {
    const char* hookDir = std::getenv("WIICOMPILED_HOOK_DIR");
    const char* driverDir = std::getenv("WIICOMPILED_GPU_DRIVER_DIR");
    const char* driverName = std::getenv("WIICOMPILED_GPU_DRIVER_NAME");
    if (hookDir == nullptr || driverDir == nullptr || driverName == nullptr) {
        return nullptr;
    }
    void* handle = adrenotools_open_libvulkan(flags, ADRENOTOOLS_DRIVER_CUSTOM, nullptr, hookDir, driverDir,
                                              driverName, nullptr, nullptr);
    if (handle != nullptr) {
        __android_log_print(ANDROID_LOG_INFO, kTag, "Custom GPU driver: %s%s", driverDir, driverName);
    } else {
        __android_log_print(ANDROID_LOG_ERROR, kTag, "Could not load the custom GPU driver %s%s: %s",
                            driverDir, driverName, dlerror());
    }
    return handle;
}

} // namespace

extern "C" void* __wrap_dlopen(const char* file, int flags) {
    // adrenotools opens the real loader itself, so a nested call goes straight through.
    static thread_local bool opening = false;
    if (file != nullptr && std::strcmp(file, "libvulkan.so") == 0 && !opening) {
        opening = true;
        void* handle = OpenCustomDriver(flags);
        opening = false;
        if (handle != nullptr) {
            return handle;
        }
    }
    return __real_dlopen(file, flags);
}

#endif // __ANDROID__
