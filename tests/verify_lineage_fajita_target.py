"""Re-read the supplied fajita ROM's components; ABI evidence is not a device test.

Extract system/framework, Telecom, SystemUI and TrebuchetQuickStep from the verified
payload's system image, then pass their common parent with --root. ROM/DEX files
stay outside Git. AAPT2 comes from --aapt2 or ANDROID_HOME/build-tools/36.1.0.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import zipfile
import verify_signal_target as dex
from verify_navbar_target import dex_views

SPEC = Path(__file__).resolve().parent / "target/lineage-fajita-20261110.json"


def instruction_hash(instructions):
    return hashlib.sha256(json.dumps(instructions, sort_keys=True, separators=(",", ":")).encode()).hexdigest()


def read_archives(spec, root):
    classes, streams = {}, {}
    for label, artifact in spec["artifacts"].items():
        path = root / artifact["path"]
        assert hashlib.sha256(path.read_bytes()).hexdigest() == artifact["sha256"], (label, "SHA256 mismatch")
        targets = set(spec["classes"].get(label, {}))
        if not targets:
            continue
        dex.TARGETS = targets
        required = {(c["owner"], c["name"], c["descriptor"]) for c in spec["code_evidence"] if c["artifact"] == label}
        with zipfile.ZipFile(path) as archive:
            for name in archive.namelist():
                if not re.fullmatch(r"classes\d*\.dex", name):
                    continue
                for data in dex_views(archive.read(name)):
                    for owner, cls in data.classes.items():
                        assert (label, owner) not in classes, (label, "duplicate definition", owner)
                        classes[label, owner] = cls
                    for key in required & data.code.keys():
                        streams[label, *key] = data.instructions(*key)
    return classes, streams


def verify(spec, root, aapt2):
    classes, streams = read_archives(spec, root)
    for label, owners in spec["classes"].items():
        for owner, parent in owners.items():
            assert classes[label, owner]["superclass"] == parent, (label, "native inheritance", owner)
    for check in spec["checks"]:
        key = "type" if check["kind"] == "fields" else "descriptor"
        rows = classes[check["artifact"], check["owner"]][check["kind"]]
        matches = [m for m in rows if m["name"] == check["name"] and m[key] == check["descriptor"]]
        assert len(matches) == 1 and matches[0]["access_flags"] == check["access_flags"], check
    for check in spec["absent"]:
        owner = check["owner"]
        while owner not in (None, "Ljava/lang/Object;"):
            cls = classes[check["artifact"], owner]
            assert not any(m["name"] == check["name"] for m in cls[check["kind"]]), check
            owner = cls["superclass"]
    for check in spec["code_evidence"]:
        instructions = streams[check["artifact"], check["owner"], check["name"], check["descriptor"]]
        assert instruction_hash(instructions) == check["instruction_sha256"], ("native instructions", check["owner"], check["name"])
        calls = [ins["method"] for ins in instructions if "method" in ins]
        accesses = [dict(opcode=ins["opcode"], **ins["field"]) for ins in instructions if "field" in ins]
        assert calls == check["calls"] and accesses == check["field_accesses"], check
    # Verify the actual package and process identities, not the APK filename.
    for label, attributes in spec["manifest_attributes"].items():
        output = subprocess.check_output([str(aapt2), "dump", "xmltree", str(root / spec["artifacts"][label]["path"]),
                                          "--file", "AndroidManifest.xml"], text=True, encoding="utf-8")
        for attribute in attributes:
            assert re.search(attribute, output), (label, "native manifest", attribute)
    print(f"PASS: {spec['target']}: {len(spec['artifacts'])} component hashes, {len(spec['checks'])} exact members, "
          f"{len(spec['code_evidence'])} native instruction/call/field streams and package/process manifests")
    print("PASS: native focus, finish layers, split/freeform bars, Binder outputs, visibility, launcher input/theme/icons/recents/taskbar and Telecom filtering evidence")
    print("UNVERIFIED: ART Hook execution, device settings, input/rendering, call delivery and disable/reboot restoration")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, required=True)
    parser.add_argument("--aapt2", type=Path)
    args = parser.parse_args()
    aapt2 = args.aapt2 or Path(os.environ.get("ANDROID_HOME", "")) / "build-tools/36.1.0" / ("aapt2.exe" if os.name == "nt" else "aapt2")
    assert aapt2.is_file(), "Set --aapt2 or ANDROID_HOME"
    verify(json.loads(SPEC.read_text(encoding="utf-8")), args.root, aapt2)


if __name__ == "__main__":
    main()
