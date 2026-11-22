import unittest
from html_extract import extract_html

URL = "https://example.org/article"
class HtmlExtractionTests(unittest.TestCase):
    def test_title(self):
        self.assertEqual(extract_html(URL, "<title>Book news</title>")["title"], "Book news")
    def test_text(self):
        self.assertEqual(extract_html(URL, "<p>Hello <b>world</b></p>")["text"], "Hello world")
    def test_script_removed(self):
        self.assertEqual(extract_html(URL, "<script>SECRET</script><p>Visible</p>")["text"], "Visible")
    def test_style_removed(self):
        self.assertEqual(extract_html(URL, "<style>hidden</style><p>Shown</p>")["text"], "Shown")
    def test_noscript_removed(self):
        self.assertEqual(extract_html(URL, "<noscript>hidden</noscript><p>Shown</p>")["text"], "Shown")
    def test_template_removed(self):
        self.assertEqual(extract_html(URL, "<template>hidden</template><p>Shown</p>")["text"], "Shown")
    def test_entities(self):
        self.assertEqual(extract_html(URL, "<p>A &amp; B</p>")["text"], "A & B")
    def test_whitespace(self):
        self.assertEqual(extract_html(URL, "<p> a   b </p>")["text"], "a b")
    def test_provenance(self):
        self.assertFalse(extract_html(URL, "<p>Text</p>")["verified_origin"])
    def test_source_type(self):
        self.assertEqual(extract_html(URL, "<p>Text</p>")["source_type"], "user_supplied_html")
    def test_invalid_url(self):
        with self.assertRaises(ValueError): extract_html("http://localhost", "<p>Text</p>")
    def test_size_limit(self):
        with self.assertRaises(ValueError): extract_html(URL, "x"*100, max_chars=10)
    def test_invalid_html_type(self):
        with self.assertRaises(TypeError): extract_html(URL, b"bytes")
    def test_invalid_limit(self):
        with self.assertRaises(ValueError): extract_html(URL, "<p>Text</p>", max_chars=0)
    def test_empty(self):
        self.assertEqual(extract_html(URL, "")["text"], "")
    def test_youtube_is_not_watched(self):
        self.assertEqual(extract_html("https://youtu.be/abc", "<p>Video title</p>")["kind"], "youtube")
    def test_nested_hidden(self):
        self.assertEqual(extract_html(URL, "<script>hide<style>also hide</style>more hide</script><p>Visible</p>")["text"], "Visible")
    def test_svg_hidden(self):
        self.assertEqual(extract_html(URL, "<svg><text>icon labels</text></svg><p>Article</p>")["text"], "Article")
    def test_malformed_hidden_stays_hidden(self):
        self.assertEqual(extract_html(URL, "<script><style>hide</script>still hidden</style><p>Visible</p>")["text"], "")
    def test_title_not_body(self):
        page = extract_html(URL, "<title>Headline</title><p>Story</p>")
        self.assertEqual(page["text"], "Story")
    def test_html_comment_ignored(self):
        self.assertEqual(extract_html(URL, "<!-- ignore me --><p>Article</p>")["text"], "Article")
if __name__ == "__main__":
    unittest.main()
