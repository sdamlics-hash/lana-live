#!/usr/bin/env python3
"""LANA one-artifact source snapshot export for owner handoff, not deployment.

All remote branch-tip tracked files are represented by deduplicated SHA256 blobs.
Current priority and main source trees are also directly readable. Excludes
signing keys, env secrets, known credential files, and token-looking payloads.
Does not change repo branches, execute application code, or use trading APIs.
"""
from __future__ import annotations
import hashlib
import io
import json
import os
from pathlib import PurePosixPath
import re
import subprocess
import tarfile
from datetime import datetime, timezone
from zipfile import ZipFile, ZIP_DEFLATED

REPO = os.environ["GITHUB_REPOSITORY"]
PRIORITY = os.environ.get("LANA_PRIORITY_BRANCH", "main")
OUT = os.environ.get("LANA_EXPORT_OUTPUT", "/tmp/lana-source-export.zip")
MAX_SIZE = 16 * 1024 * 1024
DENY_SUFFIX = (".pem", ".key", ".p12", ".pfx", ".jks", ".keystore", ".asc", ".kdbx")
DENY_NAMES = {
    ".env", "credentials.json", "service-account.json",
    "google-services.json", "id_rsa", "id_ed25519", "secret.txt",
    "signing.properties", "key.properties", "local.properties",
}
DENY_PARTS = {".git", "node_modules", "__pycache__", ".gradle", "build", ".idea"}
SECRET_PATTERNS = [
    re.compile(rb"-----BEGIN (?:RSA |EC |OPENSSH |DSA )?PRIVATE KEY-----"),
    re.compile(rb"sk-(?:proj-)?[A-Za-z0-9_-]{30,}"),
    re.compile(rb"gh[pousr]_[A-Za-z0-9]{30,}"),
    re.compile(rb"xox[baprs]-[A-Za-z0-9-]{25,}"),
]


def proc(*argv: str, check: bool = True) -> str:
    p = subprocess.run(argv, stdout=subprocess.PIPE, stderr=subprocess.PIPE, check=False)
    if check and p.returncode:
        raise RuntimeError("COMMAND_FAILED: " + argv[0] + " " + argv[1])
    return p.stdout.decode("utf-8", errors="replace")


def permitted(path: str, data: bytes) -> tuple[bool, str]:
    parts = PurePosixPath(path).parts
    if not parts or path.startswith("/") or ".." in parts:
        return False, "UNSAFE_PATH"
    name = parts[-1].lower()
    if any(p.lower() in DENY_PARTS for p in parts):
        return False, "BUILD_OR_CACHE"
    if name in DENY_NAMES or (name.startswith(".env.") and name != ".env.example"):
        return False, "CREDENTIAL_FILENAME"
    if name.endswith(DENY_SUFFIX) or name.endswith(".apk"):
        return False, "SIGNING_OR_BINARY_INSTALL"
    if len(data) > MAX_SIZE:
        return False, "FILE_TOO_LARGE"
    if any(pattern.search(data) for pattern in SECRET_PATTERNS):
        return False, "SECRET_PATTERN"
    return True, ""


refs = {}
for line in proc("git", "for-each-ref", "--format=%(refname) %(objectname)", "refs/remotes/origin").splitlines():
    try:
        ref, sha = line.split(" ", 1)
    except ValueError:
        continue
    if not ref.startswith("refs/remotes/origin/"):
        continue
    branch = ref[len("refs/remotes/origin/"):]
    if branch == "HEAD":
        continue
    refs[branch] = sha

# Explicitly include base branch; never silently call an incomplete export "all".
if "main" not in refs:
    raise RuntimeError("MISSING_MAIN_REMOTE_REF")
if PRIORITY not in refs:
    raise RuntimeError("MISSING_PRIORITY_REMOTE_REF: " + PRIORITY)

blobs = {}
manifest = {}
excluded = []
for branch, sha in sorted(refs.items()):
    branch_manifest = {}
    cmd = subprocess.Popen(["git", "archive", "--format=tar", sha], stdout=subprocess.PIPE,
                           stderr=subprocess.PIPE)
    assert cmd.stdout is not None
    with tarfile.open(fileobj=cmd.stdout, mode="r|") as tar:
        for member in tar:
            if not member.isfile():
                continue
            content = tar.extractfile(member)
            if content is None:
                continue
            if member.size > MAX_SIZE:
                excluded.append({"branch": branch, "path": member.name, "reason": "FILE_TOO_LARGE"})
                continue
            data = content.read(MAX_SIZE + 1)
            ok, reason = permitted(member.name, data)
            if not ok:
                excluded.append({"branch": branch, "path": member.name, "reason": reason})
                continue
            digest = hashlib.sha256(data).hexdigest()
            blobs.setdefault(digest, data)
            branch_manifest[member.name] = digest
    code = cmd.wait()
    if code:
        raise RuntimeError("GIT_ARCHIVE_FAILED: " + branch)
    manifest[branch] = {"commit_sha": sha, "files": branch_manifest}

pr_status = "NOT_FETCHED"
prs = []
try:
    raw = proc("gh", "api", "repos/" + REPO + "/pulls?state=all&per_page=100")
    arr = json.loads(raw)
    prs = [{"number": item["number"], "title": item["title"], "state": item["state"],
            "created_at": item["created_at"], "merged_at": item.get("merged_at"),
            "head_branch": item["head"]["ref"], "head_sha": item["head"]["sha"],
            "url": item["html_url"]}
           for item in arr]
    pr_status = "FETCHED"
except Exception:
    pr_status = "UNAVAILABLE"

metadata = {
    "schema": "LANA_GITHUB_HANDOFF_BRANCH_TIPS_V1",
    "repository": REPO,
    "created_utc": datetime.now(timezone.utc).isoformat(),
    "priority_branch": PRIORITY,
    "priority_sha": refs[PRIORITY],
    "branch_count": len(refs),
    "unique_file_blobs": len(blobs),
    "excluded_entries": len(excluded),
    "pr_index_status": pr_status,
    "pr_count": len(prs),
    "scope": "ALL_GITHUB_REMOTE_BRANCH_TIPS_NOT_FULL_GIT_HISTORY",
    "historical_closed_pr_branches_missing_from_remotes": "NOT_INCLUDED_UNLESS_BRANCH_PRESENT",
    "debian_uncommitted_files_or_secret_keys": "NOT_INCLUDED",
    "execution_verified_on_samsung": False,
}
readme = """LANA - GITHUB SOURCE HANDOFF

This is a SOURCE and PROJECT HISTORY bundle, NOT a working installed APK.
Priority branch is in LATEST_SOURCE/; main is in MAIN_SOURCE/.
BRANCH_MANIFEST.json maps ALL currently existing remote branch tip files to
content-addressed SHA256 entries in BLOBS/. Use RESTORE_BRANCH.py to restore
any branch tip without GitHub access:
  python RESTORE_BRANCH.py "branch-name" "/some/empty/output/folder"
PR_INDEX.json lists available issue/PR metadata (if API accessible).

Secrets, signing keys, APK files, build outputs, and suspicious tokens were
excluded; list of skipped entries is in EXCLUSIONS.json. User's live Debian
files and real device state were NOT gathered. DO NOT overwrite, uninstall,
re-sign, or create a second Android LANA app. Ensure install signature parity.
Read handoff CHECKPOINT, tests and Android update docs before changing code.
"""
restore = r'''#!/usr/bin/env python3
import hashlib, json, pathlib, sys, zipfile
if len(sys.argv) != 3:
    sys.exit('Usage: python RESTORE_BRANCH.py <branch-name> <empty-output-folder>')
src=pathlib.Path(__file__).resolve().parent
branch,out=sys.argv[1],pathlib.Path(sys.argv[2]).resolve()
manifest=json.loads((src/'BRANCH_MANIFEST.json').read_text())
if branch not in manifest:sys.exit('Unknown branch')
if out.exists() and any(out.iterdir()):sys.exit('Destination must be empty')
out.mkdir(parents=True,exist_ok=True)
for rel,digest in manifest[branch]['files'].items():
    path=(out/rel).resolve()
    if out not in path.parents:sys.exit('Unsafe path')
    data=(src/'BLOBS'/digest).read_bytes()
    if hashlib.sha256(data).hexdigest()!=digest:sys.exit('Hash mismatch')
    path.parent.mkdir(parents=True,exist_ok=True)
    path.write_bytes(data)
print('Restored:',branch,len(manifest[branch]['files']),'files')
'''
os.makedirs(os.path.dirname(OUT), exist_ok=True)
with ZipFile(OUT, "w", compression=ZIP_DEFLATED, compresslevel=7, allowZip64=True) as z:
    z.writestr("README_GITHUB_SOURCE.txt", readme)
    z.writestr("METADATA.json", json.dumps(metadata, ensure_ascii=False, indent=2))
    z.writestr("BRANCH_MANIFEST.json", json.dumps(manifest, ensure_ascii=False, indent=2))
    z.writestr("PR_INDEX.json", json.dumps(prs, ensure_ascii=False, indent=2))
    z.writestr("EXCLUSIONS.json", json.dumps(excluded, ensure_ascii=False, indent=2))
    z.writestr("RESTORE_BRANCH.py", restore)
    for digest, data in sorted(blobs.items()):
        z.writestr("BLOBS/" + digest, data)
    for dest, branch in (("LATEST_SOURCE", PRIORITY), ("MAIN_SOURCE", "main")):
        for rel, digest in manifest[branch]["files"].items():
            z.writestr(dest + "/" + rel, blobs[digest])
print(json.dumps(metadata, ensure_ascii=False))
