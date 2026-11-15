"""Check the draft _15.apk's actual DEX ABI and native callers, without a device.

Usage: python tests/verify_systemui15_target.py --apk /path/to/_15.apk
Only this SystemUI artifact is verified; framework/services/Launcher are separate.
"""
import argparse
import hashlib
from pathlib import Path
import re
import zipfile
import verify_signal_target as dex
from verify_navbar_target import dex_views

ROOT = Path(__file__).resolve().parents[1]
SHA256 = "9b922ad353ddab0c2a286804e4960f7d17fc9d82c8767434553645fdb9619f0c"
EDGE = "Lcom/android/systemui/navigationbar/gestural/"
HANDLER = EDGE + "EdgeBackGestureHandler;"
INPUT = EDGE + "EdgeBackGestureHandler$$ExternalSyntheticLambda11;"
RECEIVER = "Lcom/android/systemui/shared/system/InputChannelCompat$InputEventReceiver$1;"
COMPAT = "Lcom/android/systemui/shared/system/InputMonitorCompat;"
CLICK = "Lcom/android/systemui/statusbar/phone/StatusBarNotificationActivityStarter"
ENTRY = "Lcom/android/systemui/statusbar/notification/collection/NotificationEntry;"
RUNNER, SENDER = CLICK + "$$ExternalSyntheticLambda4;", CLICK + "$$ExternalSyntheticLambda9;"
MOBILE = "Lcom/android/systemui/statusbar/pipeline/mobile/"
CELL = MOBILE + "domain/model/SignalIconModel$Cellular;"
BINDER = MOBILE + "ui/binder/MobileIconBinder$bind$1$2$1$2$1;"
VM = MOBILE + "ui/viewmodel/LocationBasedMobileViewModel;"
REPO = MOBILE + "data/repository/prod/MobileConnectionRepositoryImpl;"
RADIO = MOBILE + "data/repository/prod/MobileConnectionRepositoryImpl$callbackEvents$1$1$callback$1;"
SPLIT = "Lcom/android/systemui/statusbar/policy/SplitShadeStateControllerImpl;"
PANEL = "Lcom/android/systemui/shade/NotificationPanelViewController;"
QS = "Lcom/android/systemui/shade/QuickSettingsControllerImpl;"
STARTABLE = "Lcom/android/systemui/shade/domain/startable/ShadeStartable;"
HYDRATE = "Lcom/android/systemui/shade/domain/startable/ShadeStartable$hydrateShadeLayoutWidth$1$2;"
CONFIG = "Lcom/android/systemui/common/ui/data/repository/ConfigurationRepositoryImpl$onConfigurationChange$1$callback$1;"
ICON = "Lcom/android/systemui/statusbar/StatusBarIconView;"
ROTATE = "Lcom/android/systemui/shared/rotation/RotationButtonController;"
NAV = "Lcom/android/systemui/navigationbar/views/NavigationBar;"
BAR = "Lcom/android/systemui/shared/statusbar/phone/BarTransitions"
SPLASH = "Lcom/android/wm/shell/startingsurface/SplashscreenContentDrawer"
EMIT = "(Ljava/lang/Object;Lkotlin/coroutines/Continuation;)Ljava/lang/Object;"
EVENT = "(Landroid/view/InputEvent;)V"
MOTION = "(Landroid/view/MotionEvent;)V"
CONFIG_EVENT = "(Landroid/content/res/Configuration;)V"

METHODS = {
    HANDLER: [("cancelGesture", MOTION), ("dispatchToBackAnimation", MOTION)],
    INPUT: [("onInputEvent", EVENT)], RECEIVER: [("onInputEvent", EVENT)],
    EDGE + "BackPanelController;": [("onMotionEvent", MOTION), ("onViewAttached", "()V"), ("onDestroy", "()V")],
    RUNNER: [("run", "()V")], SENDER: [("startPendingIntent", "(Landroid/view/RemoteAnimationAdapter;)I")],
    BINDER: [("emit", EMIT)], VM: [("getSubscriptionId", "()I")],
    RADIO: [("onSignalStrengthsChanged", "(Landroid/telephony/SignalStrength;)V"),
            ("onServiceStateChanged", "(Landroid/telephony/ServiceState;)V"),
            ("onCarrierRoamingNtnModeChanged", "(Z)V")],
    SPLIT: [("shouldUseSplitNotificationShade", "(Landroid/content/res/Resources;)Z")],
    PANEL: [("updateResources", "()V")], HYDRATE: [("emit", EMIT)], CONFIG: [("onConfigChanged", CONFIG_EVENT)],
    ICON: [("getIcon", "(Lcom/android/internal/statusbar/StatusBarIcon;)Landroid/graphics/drawable/Drawable;"),
           ("updateDrawable", "(Z)Z"), ("onDraw", "(Landroid/graphics/Canvas;)V")],
    ROTATE: [("setRotateSuggestionButtonState", "(ZZ)V")],
    NAV: [("getBarLayoutParamsForRotation", "(I)Landroid/view/WindowManager$LayoutParams;"),
          ("repositionNavigationBar", "(I)V"), ("onInit", "()V")],
    BAR + "$BarBackgroundDrawable;": [("draw", "(Landroid/graphics/Canvas;)V")],
    SPLASH + "$SplashViewBuilder;": [("build", "()Landroid/window/SplashScreenView;")],
    "Lcom/android/systemui/qs/tileimpl/QSFactoryImpl;": [("createTile", "(Ljava/lang/String;)Lcom/android/systemui/plugins/qs/QSTile;")],
    "Lcom/android/systemui/qs/tileimpl/QSTileImpl;": [("click", "(Lcom/android/systemui/animation/Expandable;)V"), ("setTileSpec", "(Ljava/lang/String;)V")],
    "Lcom/android/systemui/statusbar/phone/PhoneStatusBarView;": [("updateStatusBarHeight", "()V")],
}
FIELDS = {
    HANDLER: [("mDisplayId", "I"), ("mInputMonitor", COMPAT), *[(n, "Z") for n in ("mAllowGesture", "mLogGesture", "mInRejectedExclusion")]],
    INPUT: [("f$0", HANDLER)], RECEIVER: [("val$listener", "Lcom/android/systemui/shared/system/InputChannelCompat$InputEventListener;")],
    COMPAT: [("mInputMonitor", "Landroid/view/InputMonitor;")],
    RUNNER: [("f$1", ENTRY), ("f$3", "Landroid/app/PendingIntent;"), ("f$5", "Z")],
    SENDER: [("f$0", CLICK + ";"), ("f$2", "Landroid/app/PendingIntent;"), ("f$3", "Landroid/content/Intent;"), ("f$4", ENTRY)],
    CLICK + ";": [("mDisplayId", "I")], ENTRY: [("mSbn", "Landroid/service/notification/StatusBarNotification;")],
    CELL: [("carrierNetworkChange", "Z"), ("level", "I"), ("numberOfLevels", "I"), ("showExclamationMark", "Z")],
    BINDER: [("$r8$classId", "I"), ("$mobileDrawable", "Ljava/lang/Object;"), ("$viewModel", VM)],
    RADIO: [("$this_run", REPO)], REPO: [("subId", "I")],
    PANEL: [("mQsController", QS), ("mResources", "Landroid/content/res/Resources;"), ("mSplitShadeStateController", SPLIT)],
    QS: [("mResources", "Landroid/content/res/Resources;"), ("mSplitShadeStateController", SPLIT), ("mSplitShadeEnabled", "Z")],
    STARTABLE: [("splitShadeStateController", SPLIT)], HYDRATE: [("this$0", STARTABLE)],
    ICON: [("mIconScale", "F"), ("mSlot", "Ljava/lang/String;"), ("mOriginalStatusBarIconSize", "I"),
           ("mNotification", "Landroid/service/notification/StatusBarNotification;"), ("mShowsConversation", "Z")],
    ROTATE: [("mMainThreadHandler", "Landroid/os/Handler;"), ("mLastRotationSuggestion", "I"),
             ("mPendingRotationSuggestion", "Z"), ("mRotationButton", "Lcom/android/systemui/shared/rotation/FloatingRotationButton;"),
             ("mCancelPendingRotationProposal", ROTATE[:-1] + "$$ExternalSyntheticLambda0;")],
    "Lcom/android/systemui/shared/rotation/FloatingRotationButton;": [("mKeyButtonView", "Lcom/android/systemui/shared/rotation/FloatingRotationButtonView;")],
    NAV: [("mContext", "Landroid/content/Context;"), ("mNavBarMode", "I"),
          ("mNavigationBarTransitions", "Lcom/android/systemui/navigationbar/views/NavigationBarTransitions;")],
    BAR + ";": [("mBarBackground", BAR + "$BarBackgroundDrawable;")],
    SPLASH + "$SplashViewBuilder;": [("mActivityInfo", "Landroid/content/pm/ActivityInfo;"), ("mSuggestType", "I"), ("this$0", SPLASH + ";")],
    SPLASH + ";": [("mTmpAttrs", SPLASH + "$SplashScreenWindowAttrs;")],
    SPLASH + "$SplashScreenWindowAttrs;": [("mSplashScreenIcon", "Landroid/graphics/drawable/Drawable;"), ("mIconBgColor", "I")],
}


def verify(apk):
    assert hashlib.sha256(apk.read_bytes()).hexdigest() == SHA256, "draft APK SHA256 mismatch"
    source = (ROOT / "app/src/main/java/dev/local/sunwindowdemo/features/shade/LandscapeShade.java").read_text()
    api35 = source.split("String[][] API35_CALLERS = {", 1)[1].split("};", 1)[0]
    required_methods = {owner: list(rows) for owner, rows in METHODS.items()}
    for owner, name in re.findall(r'\{"([^"]+)", "([^"]+)"\}', api35):
        owner = "Lcom/android/systemui/" + owner.replace(".", "/") + ";"
        required_methods.setdefault(owner, []).append((name, CONFIG_EVENT if name in ("onConfigChanged", "onConfigurationChange") else EMIT if name == "emit" else "()V"))
    dex.TARGETS = set(required_methods) | set(FIELDS) | {"Lcom/android/systemui/navigationbar/views/NavigationBarTransitions;"}
    classes, definitions = {}, {}
    with zipfile.ZipFile(apk) as archive:
        for name in archive.namelist():
            if not re.fullmatch(r"classes\d*\.dex", name):
                continue
            for data in dex_views(archive.read(name)):
                for owner, cls in data.classes.items():
                    assert owner not in classes, ("duplicate class", owner)
                    classes[owner], definitions[owner] = cls, data
    count = 0
    for kind, required, signature in [("methods", required_methods, "descriptor"), ("fields", FIELDS, "type")]:
        for owner, members in required.items():
            assert owner in classes, ("missing class", owner)
            for name, descriptor in members:
                found = [m for m in classes[owner][kind] if m["name"] == name and m[signature] == descriptor]
                assert len(found) == 1 and (name == "<init>" or not found[0]["access_flags"] & 8), (owner, name, descriptor)
                count += 1

    def instructions(owner, name, descriptor):
        return definitions[owner].instructions(owner, name, descriptor)

    def calls(owner, name, descriptor):
        return [i["method"] for i in instructions(owner, name, descriptor) if "method" in i]

    def call(rows, owner, name):
        return any(m["owner"] == owner and m["name"] == name for m in rows)

    native_input = calls(INPUT, "onInputEvent", EVENT)
    assert call(native_input, HANDLER, "dispatchToBackAnimation")
    assert any(m["name"] == "onMotionEvent" for m in native_input)
    receiver = calls(RECEIVER, "onInputEvent", EVENT)
    listener = next(i for i, m in enumerate(receiver) if m["name"] == "onInputEvent")
    finish = next(i for i, m in enumerate(receiver) if m["name"] == "finishInputEvent")
    assert listener < finish, "listener must return to native event completion"
    run = instructions(RUNNER, "run", "()V")
    assert any(i.get("type") == SENDER for i in run), "notification body runnable must create the selected sender"
    assert any(i.get("field", {}).get("name") == "f$5" for i in run)
    sender = instructions(SENDER, "startPendingIntent", "(Landroid/view/RemoteAnimationAdapter;)I")
    assert any(i.get("field", {}).get("owner") == CLICK + ";" and i["field"]["name"] == "mDisplayId" for i in sender)
    assert call(calls(SENDER, "startPendingIntent", "(Landroid/view/RemoteAnimationAdapter;)I"), "Landroid/app/PendingIntent;", "sendAndReturnResult")
    mobile = instructions(BINDER, "emit", EMIT)
    assert any(i.get("type") == CELL for i in mobile)
    assert {"level", "numberOfLevels", "carrierNetworkChange", "showExclamationMark"} <= {
        i["field"]["name"] for i in mobile if i.get("field", {}).get("owner") == CELL}
    assert any(m["name"] == "setLevel" and m["descriptor"] == "(I)Z" for m in calls(BINDER, "emit", EMIT))
    panel = calls(PANEL, "updateResources", "()V")
    assert sum(m["owner"] == SPLIT and m["name"] == "shouldUseSplitNotificationShade" for m in panel) == 2
    assert any(m["name"] == "setInSplitShade" for m in panel)
    assert not any(m["name"] == "updateResources" for m in classes[QS]["methods"])
    assert call(calls(HYDRATE, "emit", EMIT), SPLIT, "shouldUseSplitNotificationShade")
    assert not any(f["name"] == "mBundleEntry" for f in classes[ICON]["fields"])
    assert not any(f["name"] == "mUiHandler" for f in classes[ROTATE]["fields"])
    assert classes["Lcom/android/systemui/navigationbar/views/NavigationBarTransitions;"]["superclass"] == BAR + ";"
    for name in ("onInit", "repositionNavigationBar"):
        assert call(calls(NAV, name, "()V" if name == "onInit" else "(I)V"), NAV, "getBarLayoutParamsForRotation")
    print(f"PASS: draft Android15 SystemUI SHA256, {count} exact members and native input/notification/signal/shade/navigation callers")
    print("SystemUI ABI evidence only; device effects and framework/services/Launcher compatibility remain unverified")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--apk", type=Path, default=ROOT / "verification/systemui15/SystemUI.apk")
    verify(parser.parse_args().apk)
