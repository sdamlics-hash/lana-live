"""Offline, user-supplied webpage extraction. No fetching, scripts or login."""
from html.parser import HTMLParser
from urllib.parse import urljoin
from source_policy import classify

class _TextParser(HTMLParser):
    def __init__(self):
        super().__init__(convert_charrefs=True)
        self.depth = 0
        self.parts = []
        self.title_parts = []
        self.in_title = False
        self.links = []
        self.href = None

    def handle_starttag(self, tag, attrs):
        if tag in ("script", "style", "noscript", "template"):
            self.depth += 1
        if tag == "title":
            self.in_title = True
        if tag == "a":
            self.href = dict(attrs).get("href")

    def handle_endtag(self, tag):
        if tag in ("script", "style", "noscript", "template") and self.depth:
            self.depth -= 1
        if tag == "title":
            self.in_title = False
        if tag == "a":
            self.href = None

    def handle_data(self, data):
        if self.depth:
            return
        text = " ".join(data.split())
        if not text:
            return
        if self.in_title:
            self.title_parts.append(text)
        else:
            self.parts.append(text)

def extract_html(url, html, max_chars=100000):
    """Extract text from HTML already provided by an authorized caller.

    This function does not download pages and cannot verify the page origin.
    """
    source = classify(url)
    if not isinstance(html, str):
        raise TypeError("HTML must be a string")
    if not isinstance(max_chars, int) or isinstance(max_chars, bool) or max_chars < 1:
        raise ValueError("max_chars must be positive")
    if len(html) > max_chars:
        raise ValueError("HTML exceeds configured size limit")
    parser = _TextParser()
    parser.feed(html)
    parser.close()
    return {"url": source.url, "kind": source.kind,
            "title": " ".join(parser.title_parts),
            "text": " ".join(parser.parts),
            "verified_origin": False,
            "source_type": "user_supplied_html"}
