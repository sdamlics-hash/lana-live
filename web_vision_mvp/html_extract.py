"""Offline, user-supplied webpage extraction. No fetching, scripts or login."""
from html.parser import HTMLParser
from source_policy import classify

class _TextParser(HTMLParser):
    def __init__(self):
        super().__init__(convert_charrefs=True)
        self.hidden = []
        self.parts = []
        self.title_parts = []
        self.in_title = False

    def handle_starttag(self, tag, attrs):
        if tag in ("script", "style", "noscript", "template", "svg"):
            self.hidden.append(tag)
        if tag == "title":
            self.in_title = True

    def handle_endtag(self, tag):
        if tag in ("script", "style", "noscript", "template", "svg") and tag in self.hidden:
            # Do not expose hidden content if malformed HTML closes tags out of order.
            self.hidden = self.hidden[:len(self.hidden) - 1 - self.hidden[::-1].index(tag)]
        if tag == "title":
            self.in_title = False

    def handle_data(self, data):
        if self.hidden:
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
