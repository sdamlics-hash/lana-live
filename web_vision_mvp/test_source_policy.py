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
        self.assertFalse(may_claim_watched(classify("https://youtu.be/abc"), transcript_available=True))
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
    def test_21_space(self):
        with self.assertRaises(ValueError): classify("https://example.org/a b")
    def test_22_tab(self):
        with self.assertRaises(ValueError): classify("https://example.org/a\\tb")
    def test_23_del(self):
        with self.assertRaises(ValueError): classify("https://example.org/a\\x7fb")
    def test_24_backslash(self):
        with self.assertRaises(ValueError): classify("https://example.org\\\\@localhost")
    def test_25_fragment(self):
        with self.assertRaises(ValueError): classify("https://example.org/#hidden")
    def test_26_example_tld(self):
        with self.assertRaises(ValueError): classify("https://foo.example")
    def test_27_onion(self):
        with self.assertRaises(ValueError): classify("https://foo.onion")
    def test_28_overlong_label(self):
        with self.assertRaises(ValueError): classify("https://NaN.org")
    def test_29_overlong_host(self):
        with self.assertRaises(ValueError): classify("https://NaNorg")
    def test_30_ftp(self):
        with self.assertRaises(ValueError): classify("ftp://example.org")
    def test_31_file(self):
        with self.assertRaises(ValueError): classify("file:///etc/passwd")
    def test_32_empty_host(self):
        with self.assertRaises(ValueError): classify("https:///path")
    def test_33_ipv6_loopback(self):
        with self.assertRaises(ValueError): classify("https://[::1]/")
    def test_34_ipv6_public(self):
        with self.assertRaises(ValueError): classify("https://[2606:4700:4700::1111]/")
    def test_35_ip_public(self):
        with self.assertRaises(ValueError): classify("https://8.8.8.8/")
    def test_36_ip_link_local(self):
        with self.assertRaises(ValueError): classify("https://169.254.169.254/")
    def test_37_hyphen_start(self):
        with self.assertRaises(ValueError): classify("https://-abc.example.org")
    def test_38_hyphen_end(self):
        with self.assertRaises(ValueError): classify("https://abc-.example.org")
    def test_39_internal_domain(self):
        with self.assertRaises(ValueError): classify("https://foo.local")
    def test_40_credential_user(self):
        with self.assertRaises(ValueError): classify("https://user@example.org")
if __name__ == "__main__":
    unittest.main()
