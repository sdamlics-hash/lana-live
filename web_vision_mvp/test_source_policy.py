import unittest
from source_policy import classify, may_claim_watched

class SourcePolicyTests(unittest.TestCase):
    def test_01_youtube(self):
        self.assertEqual(classify("https://www.youtube.com/watch?v=abc").kind, "youtube")
    def test_02_youtu_be(self):
        self.assertEqual(classify("https://youtu.be/abc").kind, "youtube")
    def test_03_facebook(self):
        self.assertEqual(classify("https://www.facebook.com/watch/").kind, "facebook")
    def test_04_fb_watch(self):
        self.assertEqual(classify("https://fb.watch/abc").kind, "facebook")
    def test_05_other_site(self):
        self.assertEqual(classify("https://example.org").kind, "website")
    def test_06_no_http(self):
        with self.assertRaises(ValueError): classify("http://example.org")
    def test_07_no_credentials(self):
        with self.assertRaises(ValueError): classify("https://user:pass@example.org")
    def test_08_no_localhost(self):
        with self.assertRaises(ValueError): classify("https://localhost")
    def test_09_no_private_ip(self):
        with self.assertRaises(ValueError): classify("https://192.168.1.1")
    def test_10_no_custom_port(self):
        with self.assertRaises(ValueError): classify("https://example.org:8080")
    def test_11_no_lookalike_youtube(self):
        self.assertEqual(classify("https://youtube.com.evil.org").kind, "website")
    def test_12_no_lookalike_facebook(self):
        self.assertEqual(classify("https://facebook.com.evil.org").kind, "website")
    def test_13_url_not_watched(self):
        self.assertFalse(may_claim_watched(classify("https://youtu.be/abc")))
    def test_14_transcript_is_evidence(self):
        self.assertTrue(may_claim_watched(classify("https://youtu.be/abc"), transcript_available=True))
    def test_15_frames_are_evidence(self):
        self.assertTrue(may_claim_watched(classify("https://fb.watch/abc"), frames_analyzed=True))
    def test_16_website_not_video(self):
        self.assertFalse(may_claim_watched(classify("https://example.org"), transcript_available=True))
    def test_17_no_javascript(self):
        with self.assertRaises(ValueError): classify("javascript:alert(1)")
    def test_18_no_private_domain(self):
        with self.assertRaises(ValueError): classify("https://service.internal")
    def test_19_no_control_characters(self):
        with self.assertRaises(ValueError): classify("https://example.org/\nhello")
    def test_20_no_invalid_port(self):
        with self.assertRaises(ValueError): classify("https://example.org:abc")
if __name__ == "__main__":
    unittest.main()
