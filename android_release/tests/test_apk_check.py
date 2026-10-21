import importlib.util
from pathlib import Path
import unittest
ROOT=Path(__file__).resolve().parents[2]
spec=importlib.util.spec_from_file_location('checker',ROOT/'android_release/check_apk_pair.py')
m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m)
class SigningTests(unittest.TestCase):
    def item(self,signer='a',code=1,package=m.EXPECTED_PACKAGE):
        return {'package':package,'version_code':code,'signers_sha256':[signer*64]}
    def test_mismatch_is_blocked_and_never_claims_samsung_ready(self):
        report=m.compare(self.item(),self.item('b',2))
        self.assertFalse(report['release_precheck_passed'])
        self.assertIn('certificate_mismatch',report['block_reasons'])
        self.assertFalse(report['samsung_install_ready'])
    def test_same_key_is_insufficient_without_increased_release_version(self):
        self.assertIn('release_version_code_not_increased',m.compare(self.item(),self.item())['block_reasons'])
        self.assertTrue(m.compare(self.item(),self.item(code=2))['release_precheck_passed'])
        self.assertFalse(m.compare(self.item(),self.item(code=2))['samsung_install_ready'])
    def test_different_package_is_not_an_update(self):
        self.assertIn('package_mismatch',m.compare(self.item(),self.item(code=2,package='another.app'))['block_reasons'])
    def test_missing_verified_fingerprint_fails_closed(self):
        for text in ('','Signer #1 certificate SHA-1 digest: '+'a'*40,'Signer #1 certificate SHA-256 digest: truncated'):
            with self.assertRaises(ValueError):m.parse_verified_signers(text)
        self.assertEqual(m.parse_verified_signers('Signer #1 certificate SHA-256 digest: '+'A'*64),['a'*64])
    def test_metadata_requires_real_package_header(self):
        with self.assertRaises(ValueError):m.parse_package('not an APK manifest')
        self.assertEqual(m.parse_package("package: name='example.app' versionCode='2' versionName='v2' platformBuildVersionName='15'")['version_code'],2)
