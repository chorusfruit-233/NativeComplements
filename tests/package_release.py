"""Build and verify same-source full/window APKs; never install, reboot or make backups."""
from pathlib import Path
import argparse
import hashlib
import os
import re
import shutil
import subprocess
import zipfile
from verify_r8_host_names import check as check_r8
import verify_signal_target as dex

ROOT = Path(__file__).resolve().parents[1]
CERTIFICATE = "28bbc4a8959e73a9282f94d33b6d51c6f730d4973c4a37c290d70f8274583104"
SCOPE = {product: ROOT / f"app/src/{product}/resources/META-INF/xposed/scope.list" for product in ("full", "window")}
SYSTEM_SCOPES = {"system", "com.google.android.apps.nexuslauncher", "com.android.systemui", "com.android.launcher3"}
PRODUCT_SCOPES = {"full": SYSTEM_SCOPES | {"com.android.server.telecom"}, "window": SYSTEM_SCOPES}
BASE_ENTRIES = {"dev.local.sunwindowdemo." + name for name in ("SunWindowModule", "LauncherGuard", "BackBridge")}
ENTRIES = {
    "full": BASE_ENTRIES | {"dev.local.sunwindowdemo.features." + name for name in (
        "callblocking.CallBlockingModule", "iconlayers.IconLayerModule", "iconlayers.SystemIconModule", "navbar.NavbarModule")},
    "window": BASE_ENTRIES | {"dev.local.sunwindowdemo.features.iconlayers.LauncherThemeModule"},
}
WINDOW_FORBIDDEN = {"Ldev/local/sunwindowdemo/" + name + ";" for name in (
    "SuggestionHost", "SuggestionInput", "BarrageHost", "RotationSuggestion", "LauncherRotationSuggestion",
    "features/callblocking/CallBlockingModule", "features/callblocking/CallFilteringCopy", "features/navbar/NavbarModule", "features/iconlayers/IconLayerModule",
    "features/iconlayers/SystemIconModule", "features/iconlayers/DrawerMonet", "features/motion/MotionAccess",
    "features/edgetouch/EdgeInputFilter", "features/shade/LandscapeShade", "features/shade/StatusBarOffset",
    "features/shade/SignalDisplay", "features/shade/SignalDisplayTargets")}


def check_scopes(text, product="full"):
    scopes = text.splitlines()
    expected = PRODUCT_SCOPES[product]
    assert len(scopes) == len(expected) and set(scopes) == expected, (product, "Unexpected system host scope", scopes)


def run(*args):
    result = subprocess.run(list(map(str, args)), cwd=ROOT, capture_output=True,
                            text=True, encoding="utf-8", errors="replace", check=True)
    return result.stdout + result.stderr


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--debug-signing", action="store_true",
                        help="Use a separate local Android Debug certificate; never replace the original upgrade key")
    args = parser.parse_args()
    for product, path in SCOPE.items(): check_scopes(path.read_text(encoding="utf-8"), product)
    gradle = (ROOT / "app/build.gradle.kts").read_text(encoding="utf-8")
    assert re.search(r"isMinifyEnabled\s*=\s*true", gradle), "Release must enable R8"
    version = re.search(r'versionName = "([^"]+)"', gradle)[1]
    code = int(re.search(r'versionCode = (\d+)', gradle)[1])
    sdk = Path(os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT")
               or str(Path(os.environ.get("LOCALAPPDATA", str(Path.home()))) / "Android/Sdk"))
    tools = sdk / "build-tools/36.1.0"
    jdk = Path(os.environ.get("JAVA_HOME") or
               (r"C:\Program Files\Android\Android Studio\jbr" if os.name == "nt" else str(Path(shutil.which("java")).resolve().parents[1])))
    suffix = ".exe" if os.name == "nt" else ""
    java = jdk / ("bin/java" + suffix)
    aapt = tools / ("aapt2" + suffix)
    zipalign = tools / ("zipalign" + suffix)
    env = os.environ.copy(); env["JAVA_HOME"] = str(jdk)
    command = [str(ROOT / "gradlew.bat")] if os.name == "nt" else ["bash", str(ROOT / "gradlew")]
    command += ["--no-daemon", "--console=plain"]
    expected_certificate = CERTIFICATE
    staging = ROOT / ".local/package-release"
    if args.debug_signing:
        staging.mkdir(parents=True, exist_ok=True)
        keystore = ROOT / ".local/debug.keystore"
        keytool = jdk / ("bin/keytool" + suffix)
        if not keystore.exists():
            run(keytool, "-genkeypair", "-keystore", keystore, "-storetype", "JKS", "-storepass", "android",
                "-keypass", "android", "-alias", "androiddebugkey", "-keyalg", "RSA", "-keysize", "2048",
                "-validity", "10000", "-dname", "CN=Android Debug,O=Android,C=US", "-noprompt")
        if os.name != "nt": keystore.chmod(0o600)
        certificate = subprocess.check_output([str(keytool), "-exportcert", "-keystore", str(keystore),
                                               "-storepass", "android", "-alias", "androiddebugkey"])
        expected_certificate = hashlib.sha256(certificate).hexdigest()
        assert expected_certificate != CERTIFICATE, "Debug signing must stay separate from the original certificate"
        init = staging / "unsigned.gradle"
        init.write_text("allprojects { pluginManager.withPlugin('com.android.application') { "
                        "androidComponents.finalizeDsl { extension -> "
                        "extension.buildTypes.getByName('release').signingConfig = null } } }\n", encoding="utf-8")
        command += ["--init-script", str(init)]
    subprocess.run(command + [":app:assembleFullRelease", ":app:assembleWindowRelease"], cwd=ROOT, env=env, check=True)
    verified = []
    for product in ("full", "window"):
        standalone = product == "window"
        package = "dev.nativecomplements.window" if standalone else "dev.nativecomplements.module"
        apk = ROOT / f"app/build/outputs/apk/{product}/release/app-{product}-release{'-unsigned' if args.debug_signing else ''}.apk"
        if args.debug_signing:
            unsigned = apk
            run(zipalign, "-c", "-P", "16", "4", unsigned)
            apk = staging / f"{product}-debug-signed.apk"
            run(java, "-jar", tools / "lib/apksigner.jar", "sign", "--ks", keystore,
                "--ks-key-alias", "androiddebugkey", "--ks-pass", "pass:android", "--key-pass", "pass:android",
                "--v1-signing-enabled", "false", "--v2-signing-enabled", "true", "--v3-signing-enabled", "true",
                "--v4-signing-enabled", "false", "--out", apk, unsigned)
            with zipfile.ZipFile(unsigned) as before, zipfile.ZipFile(apk) as after:
                assert before.namelist() == after.namelist(), "Signing changed ZIP entries"
                assert all(before.read(name) == after.read(name) for name in before.namelist()), "Signing changed package payload"
        run(zipalign, "-c", "-P", "16", "4", apk)
        mapping = ROOT / f"app/build/outputs/mapping/{product}Release/mapping.txt"
        assert mapping.exists() and "# compiler: R8" in mapping.read_text(encoding="utf-8"), "R8 mapping missing"
        check_r8(apk, window=standalone)
        with zipfile.ZipFile(apk) as archive:
            assert archive.testzip() is None, "APK CRC failure"
            assert "assets/navbar/templates.json" not in archive.namelist(), "Removed navigation templates asset in APK"
            check_scopes(archive.read("META-INF/xposed/scope.list").decode("utf-8"), product)
            assert "staticScope=true" in archive.read("META-INF/xposed/module.prop").decode().splitlines()
            entries = archive.read("META-INF/xposed/java_init.list").decode().splitlines()
            assert len(entries) == len(ENTRIES[product]) and set(entries) == ENTRIES[product], (product, entries)
            assert not any(name.endswith((".keystore", ".jks", ".p12", ".pfx")) for name in archive.namelist())
            if standalone:
                for name in archive.namelist():
                    if not name.endswith(".dex"): continue
                    data = dex.Dex(archive.read(name))
                    owners = {data.types[data.u32(data.u32(100) + 32 * i)] for i in range(data.u32(96))}
                    forbidden = {owner for owner in owners if owner.split("$", 1)[0].rstrip(";") + ";" in WINDOW_FORBIDDEN}
                    assert not forbidden, f"Non-window hooks in standalone DEX: {sorted(forbidden)}"
        signature = run(java, "-jar", tools / "lib/apksigner.jar", "verify", "--verbose", "--print-certs", apk)
        assert re.search(r"certificate SHA-256 digest: (\w+)", signature)[1] == expected_certificate
        if args.debug_signing:
            assert "CN=Android Debug, O=Android, C=US" in signature and "Number of signers: 1" in signature
            # With manifest minSdk34 the verifier selects v3 only. Also inspect
            # the v2 block using its verification range; APK minSdk stays unchanged.
            schemes = run(java, "-jar", tools / "lib/apksigner.jar", "verify", "--min-sdk-version", "24", "--verbose", apk)
            assert "Verified using v2 scheme (APK Signature Scheme v2): true" in schemes
            assert "Verified using v3 scheme (APK Signature Scheme v3): true" in schemes
        badging = run(aapt, "dump", "badging", apk).splitlines()[0]
        for expected in (f"name='{package}'", f"versionCode='{code}'", f"versionName='{version}'"):
            assert expected in badging, badging
        manifest = run(aapt, "dump", "xmltree", apk, "--file", "AndroidManifest.xml")
        assert "BIND_ACCESSIBILITY_SERVICE" not in manifest
        assert package + ".CONTROL" in manifest and package + ".settings" in manifest
        assert package + ".iconlayers" in manifest and package + ".diagnostics" in manifest
        if standalone:
            assert "dev.nativecomplements.module." not in manifest, "Standalone manifest aliases full-product identity"
            for feature in ("CallBlockingProvider", "MotionProvider", "EdgeTouchProvider", "ShadeProvider", "BarrageProvider", "SuggestionProvider", "NavbarProvider", "SuggestionSmsReceiver", "IconLayerProvider"):
                assert feature not in manifest, (product, feature)
            assert "WindowIconProvider" in manifest
        resources = run(aapt, "dump", "resources", apk)
        shortcut_path = re.search(r"resource [^\n]+ xml/shortcuts\s+\(\) \(file\) (\S+)", resources)
        assert shortcut_path, "Missing compiled static shortcut resource"
        shortcut = run(aapt, "dump", "xmltree", apk, "--file", shortcut_path[1])
        assert package + ".FLOAT_CURRENT" in shortcut and package in shortcut
        target = ROOT / f"deliverables/NativeComplements{'-Window' if standalone else ''}-{version}{f'-{code}-debug-signed' if args.debug_signing else ''}.apk"
        verified.append((apk, target, badging))
    # Publish neither deliverable until both products pass every check.
    for apk, target, badging in verified:
        target.parent.mkdir(exist_ok=True); shutil.copyfile(apk, target)
        digest = hashlib.sha256(apk.read_bytes()).hexdigest()
        assert hashlib.sha256(target.read_bytes()).hexdigest() == digest
        print(badging); print(f"APK={target}"); print(f"SHA256={digest}")
    print(("Android Debug certificate" if args.debug_signing else "Original certificates")
          + ", isolated identities, static scopes, entries and window-only Hook boundary verified; device effects unverified")


if __name__ == "__main__":
    main()
