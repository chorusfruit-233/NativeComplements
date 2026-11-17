"""Verify final APK keeps host-loader reflection names (run after R8 packaging)."""
import sys
import zipfile
from pathlib import Path
import verify_signal_target as dex

PREFIX = "Ldev/local/sunwindowdemo/"
REQUIRED = {
    (PREFIX + "FrameworkAccess;", "<init>"): {
        "getTopDisplayFocusedRootTask", "getTopMostTask", "mInputMethodWindow", "assignLayers",
        "mBuildingFinishLayers", "getAppCompatSizeCompatModePolicy", "android.window.TransitionRequestInfo$DisplayChange"},
    (PREFIX + "LauncherGuard;", "onPackageLoaded"): {
        "$r8$lambda$JDK2db7a_ZtzI6D1EJ5hYukunXo", "com.android.quickstep.TouchInteractionService$$ExternalSyntheticLambda2"},
    (PREFIX + "features/iconlayers/LauncherThemeReporter;", "install"): {
        "com.android.launcher3.util.Themes", "com.android.launcher3.LauncherPrefs", "isThemedIconEnabled", "getPrefs"},
    (PREFIX + "features/callblocking/CallFilteringCopy;", "response"): {
        "android.telecom.ParcelableCallResponse", "android.telecom.CallScreeningService$ParcelableCallResponse", "setCallScreeningResponse"},
    (PREFIX + "features/iconlayers/IconLayerModule;", "installAnimationProbe"): {
        "com.android.launcher3.views.FloatingIconView$$ExternalSyntheticLambda5", "getFloatingIconView",
        "checkIconResult", "lambda$checkIconResult$1", "$r8$lambda$VSxnDoJuuRfQFY4HAM9sw1ccIlg"},
    (PREFIX + "TaskCover;", "refreshScreenCorners"): {"com.android.internal.policy.ScreenDecorationsUtils"},
    (PREFIX + "WindowControls;", "buildControlsRoot"): {
        "android.view.ViewTreeObserver$OnComputeInternalInsetsListener", "android.view.ViewTreeObserver$InternalInsetsInfo"},
    (PREFIX + "features/navbar/NavbarHost;", "<init>"): {
        "mContext", "mNavBarMode", "mNavigationBarTransitions", "mBarBackground", "repositionNavigationBar",
        "providedInsets", "getType", "setInsetsSize"},
    (PREFIX + "features/navbar/NavbarHost;", "install"): {
        "com.android.systemui.navigationbar.views.NavigationBar", "android.view.InsetsFrameProvider",
        "getBarLayoutParamsForRotation", "onInit", "draw", "callApplicationOnCreate", "IMMERSIVE_PREFLIGHT_READY"},
    (PREFIX + "features/navbar/NavbarTaskbarHost;", "<init>"): {
        "context", "mIsDestroyed", "mDragLayer", "mWindowManager", "mWindowLayoutParams",
        "mBackgroundRenderer", "mControllers", "navbarButtonsViewController", "mTaskbarTransitions",
        "mBarBackground", "isGestureNav", "onTaskbarOrBubblebarWindowHeightOrInsetsChanged", "getType", "setInsetsSize"},
    (PREFIX + "features/navbar/NavbarTaskbarHost;", "install"): {
        "com.android.launcher3.taskbar.TaskbarInsetsController", "android.view.InsetsFrameProvider",
        "setProviderInsets", "draw", "dispatchDraw", "callApplicationOnCreate"},
    (PREFIX + "NativeBackInput;", "resolve"): {
        "kotlin.Unit", "com.android.systemui.navigationbar.gestural.EdgeBackGestureHandler$$ExternalSyntheticLambda11",
        "com.android.systemui.shared.system.InputChannelCompat$InputEventReceiver$1"},
    (PREFIX + "NativeBackInput;", "<init>"): {
        "mDisplayBackGestureHandlers", "inputMonitorCompat", "mDisplayId", "mInputMonitor",
        "onInputEvent", "invoke", "val$listener", "INSTANCE"},
    (PREFIX + "NotificationClickTargets;", "<init>"): {
        "run", "startPendingIntent", "f$0", "f$2", "f$3", "f$4", "f$5", "mDisplayId"},
    (PREFIX + "features/shade/ComposeShadeLayout;", "install"): {"kotlin.coroutines.Continuation"},
    (PREFIX + "features/shade/SignalDisplayTargets;", "resolve"): {
        "kotlin.Pair", "kotlin.coroutines.Continuation",
        "com.android.systemui.statusbar.pipeline.mobile.domain.model.SignalIconModel$Cellular",
        "com.android.systemui.statusbar.pipeline.mobile.domain.model.SignalIconModel$CellularTypeIconModel$Cellular"},
    (PREFIX + "features/shade/SignalDisplay;", "installStacked"): {
        "kotlin.Pair", "androidx.compose.runtime.ParcelableSnapshotMutableIntState",
        "androidx.compose.runtime.Composer", "androidx.compose.ui.Modifier"},
    (PREFIX + "features/shade/StackedSignalDisplay;", "<init>"): {"sun.misc.Unsafe"},
    (PREFIX + "TaskCleanupGuard;", "install"): {"com.android.server.am.ActivityManagerService", "forceStopPackage"},
}

# Verify the delivered APK includes the restored runtime and the integrated navigation host.
RUNTIME_METHODS = {
    "FrameworkVariants": {"nativeOverrideValidation", "focusedTask", "withoutFreeform"},
    "FrameworkAccess": {"focusedTask", "imeWindow", "sizeCompatPolicy", "buildingTransitionLayers", "calculateInsets"},
    "ViewportTargets": {"describe"},
    "core/LauncherHosts": {"isLauncher", "packageForProcess", "ownsUid", "broadcast"},
    "features/callblocking/CallFilteringCopy": {"copy", "response"},
    "WindowEngine": {"blockBackgroundTouch", "trackTouchGuard", "clientOwner", "ownerInsets", "idle", "mayAllowResize", "visibleFullscreen", "endClosedApplications"},
    "WindowPolicy": {"backgroundTouchGuard", "startsTouchGuard", "coveredFullscreen"},
    "BackBridge": {"connect", "cancelNativeUp", "abort"},
    "NativeBackInput": {"resolve", "display", "pilfer"},
    "NotificationClickTargets": {"resolve", "display"},
    "features/shade/SignalDisplayTargets": {"resolve", "model"},
    "Config": {"longBackWindow"},
    "TaskCover": {"beforeReveal", "canRetarget", "reusesPresentation", "handoff"},
    "TaskCleanupGuard": {"install", "delay", "acquired", "check"},
    "LaunchCoverFlow": {"ready", "changed", "visibility"},
    "WindowControls": {"buildControlsRoot", "fillTouchRegion"},
    "features/edgetouch/EdgeInputFilter": {"install", "sync"},
    "features/edgetouch/EdgeInputPolicy": {"accepted", "regions"},
    "features/iconlayers/IconOverrides": {"prune"},
    "features/iconlayers/IconPolicy": {"batch"},
    "features/navbar/NavbarModule": {"onModuleLoaded", "onPackageLoaded"},
    "features/navbar/NavbarHost": {"install", "eligible", "onLayout", "removeNavigationInset", "hideBackground", "applyEnabled", "refreshBars", "log"},
    "features/navbar/NavbarTaskbarHost": {"install", "eligible", "onProvider", "hideBackground", "applyEnabled", "refreshControllers"},
    "core/HostSnapshot": {"read", "cached", "store"},
    "features/iconlayers/IconAssets": {"imageBytes"},
    "features/iconlayers/IconImages": {"notificationSize"},
}

REMOVED_NAVBAR_CLASSES = {
    "NavbarApp", "NavbarSystem", "NavbarContainer", "NavbarRules", "NavbarStructure", "NavbarSignature",
    "NavbarTrace", "NavbarLabelHost", "NavbarViewTree", "NavbarInsets", "NavbarReflect",
    "NavbarColorCache", "NavbarColors", "NavbarWindowLayer", "NavbarLayout", "NavbarScreenKt",
}
REMOVED_NAVBAR_OWNERS = {PREFIX + "features/navbar/" + name + ";" for name in REMOVED_NAVBAR_CLASSES}


def check(apk, window=False):
    excluded = {"features/navbar/", "features/edgetouch/", "features/shade/", "features/callblocking/", "features/iconlayers/IconLayerModule"}
    shared = lambda owner: not any(owner.startswith(prefix) for prefix in excluded) and owner not in {"features/iconlayers/IconOverrides", "features/iconlayers/IconPolicy", "features/iconlayers/IconImages"}
    required = {key: value for key, value in REQUIRED.items() if not window or shared(key[0][len(PREFIX):])}
    runtime = {PREFIX + owner + ";": methods for owner, methods in RUNTIME_METHODS.items() if not window or shared(owner)}
    dex.TARGETS = {owner for owner, _ in required} | set(runtime)
    found = set()
    runtime_found = set()
    with zipfile.ZipFile(apk) as archive:
        for name in archive.namelist():
            if not name.endswith(".dex"):
                continue
            data = dex.Dex(archive.read(name))
            # Inspect definitions, not type references: include nested remnants without parsing every class body.
            owners = {data.types[data.u32(data.u32(100) + 32 * i)] for i in range(data.u32(96))}
            obsolete = {owner for owner in owners if owner.split("$", 1)[0].rstrip(";") + ";" in REMOVED_NAVBAR_OWNERS}
            if obsolete:
                raise RuntimeError(f"Removed navigation runtime still present in final DEX: {sorted(obsolete)}")
            for owner, cls in data.classes.items():
                if owner in runtime:
                    missing = runtime[owner] - {method["name"] for method in cls["methods"]}
                    if missing:
                        raise RuntimeError(f"Missing integrated runtime methods in {owner}: {sorted(missing)}")
                    runtime_found.add(owner)
                for method in cls["methods"]:
                    key = owner, method["name"]
                    if key not in required:
                        continue
                    strings = set()
                    for ins in data.instructions(owner, method["name"], method["descriptor"]):
                        if ins["opcode"] == "1a":
                            strings.add(data.strings[ins["units"][1]])
                        elif ins["opcode"] == "1b":
                            strings.add(data.strings[ins["units"][1] | ins["units"][2] << 16])
                    missing = required[key] - strings
                    if missing:
                        raise RuntimeError(f"Host ABI name rewritten/removed in {key}: {sorted(missing)}")
                    found.add(key)
    if found != set(required):
        raise RuntimeError(f"Missing hook methods: {set(required) - found}")
    if runtime_found != set(runtime):
        raise RuntimeError(f"Missing integrated runtime classes: {set(runtime) - runtime_found}")
    print("PASS: final DEX retains Kotlin/Compose host-loader reflection names at all checked hooks")
    print("PASS: final DEX includes expected " + ("standalone window" if window else "full window, icon and immersive") + " runtime; removed navigation runtime absent")

if __name__ == "__main__":
    check(Path(sys.argv[1]))
