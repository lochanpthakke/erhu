import unittest
from verify_release import validate_badging, PACKAGE
from signing import escaped

class ReleaseValidationTest(unittest.TestCase):
    def setUp(self):
        self.good = f"package: name='{PACKAGE}' versionCode='2' versionName='1.0.1'\napplication-label:'erhu'\nnative-code: 'arm64-v8a'"
    def test_release_metadata(self):
        validate_badging(self.good, '1.0.1', 2)
    def test_debug_wrong_identity_or_version_rejected(self):
        for value in [self.good + '\napplication-debuggable', self.good.replace(PACKAGE, 'other.app'),
                      self.good.replace("versionCode='2'", "versionCode='1'"), self.good.replace('erhu', 'other')]:
            with self.assertRaises(ValueError): validate_badging(value, '1.0.1', 2)
    def test_password_property_escaping(self):
        self.assertEqual('a\\ b\\=c\\n', escaped('a b=c\n'))

if __name__ == '__main__': unittest.main()
