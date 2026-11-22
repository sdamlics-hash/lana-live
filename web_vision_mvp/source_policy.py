"""Pure offline URL and video source admission rules; no network access."""
from dataclasses import dataclass
from ipaddress import ip_address
from urllib.parse import urlsplit
import re

@dataclass(frozen=True)
class Source:
    url: str
    kind: str
    access: str

def classify(url):
    """Validate a public web URL; does not fetch or claim access to content."""
    if not isinstance(url, str) or len(url) > 2048 or any(ord(c) < 32 for c in url):
        raise ValueError("Invalid URL")
    try:
        parts = urlsplit(url)
        hostname = (parts.hostname or "").lower().rstrip(".")
        port = parts.port
    except ValueError as exc:
        raise ValueError("Invalid URL") from exc
    if parts.scheme != "https" or not hostname or parts.username or parts.password:
        raise ValueError("Only credential-free HTTPS URLs are allowed")
    if port not in (None, 443):
        raise ValueError("Nonstandard ports are blocked")
    if hostname in ("localhost", "localhost.localdomain") or hostname.endswith(".localhost"):
        raise ValueError("Local addresses blocked")
    if hostname.endswith((".local", ".internal", ".test", ".invalid")):
        raise ValueError("Private hostname blocked")
    try:
        address = ip_address(hostname.strip("[]"))
    except ValueError:
        if not re.fullmatch(r"[a-z0-9-]+(?:\.[a-z0-9-]+)+", hostname):
            raise ValueError("Invalid hostname")
        if any(part.startswith("-") or part.endswith("-") for part in hostname.split(".")):
            raise ValueError("Invalid hostname")
    else:
        if not address.is_global:
            raise ValueError("Private IP blocked")
        # Block literal IPs entirely until DNS/rebinding protections exist.
        raise ValueError("IP literals are not supported")
    youtube = hostname in ("youtube.com", "www.youtube.com", "m.youtube.com", "youtu.be")
    facebook = hostname in ("facebook.com", "www.facebook.com", "m.facebook.com", "fb.watch")
    kind = "youtube" if youtube else "facebook" if facebook else "website"
    return Source(url, kind, "UNVERIFIED")

def may_claim_watched(source, transcript_available=False, frames_analyzed=False):
    """A URL alone never proves that LANA watched a video."""
    return bool(source.kind in ("youtube", "facebook") and
                (transcript_available or frames_analyzed))
