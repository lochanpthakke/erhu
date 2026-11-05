import hashlib
import json
from pathlib import Path
import tempfile
import unittest
from publish_release import release_plan, validate_dist, require_increasing_code


class PublicationTest(unittest.TestCase):
    def test_beta_and_stable_are_explicit(self):
        self.assertEqual(("erhu-v1.0.3-beta.42", True), release_plan("1.0.3", "refs/heads/kshitij", "beta", 42))
        self.assertEqual(("erhu-v1.0.3", False), release_plan("1.0.3", "refs/heads/kshitij", "stable", 42))
        self.assertEqual(("erhu-v1.0.3-rc.1", True), release_plan("1.0.3", "refs/tags/erhu-v1.0.3-rc.1", "stable", 42))
        with self.assertRaises(ValueError): release_plan("1.0.3", "refs/tags/erhu-v1.0.2", "beta", 42)
        with self.assertRaises(ValueError): release_plan("1.0.3", "refs/heads/kshitij", "unknown", 42)

    def test_wrong_commit_and_damaged_artifacts_cannot_publish(self):
        with tempfile.TemporaryDirectory() as temp:
            directory = Path(temp)
            metadata = dict(applicationId="com.lochanpthakke.empee3", debuggable=False, commit="source", versionCode=4, versionName="1.0.3")
            (directory / "verification.json").write_text(json.dumps(metadata))
            apk = directory / "erhu.apk"
            apk.write_bytes(b"fixture")
            (directory / "erhu.apk.sha256").write_text(hashlib.sha256(b"fixture").hexdigest())
            self.assertEqual(3, len(validate_dist(directory, "source")[1]))
            with self.assertRaises(ValueError): validate_dist(directory, "other")
            apk.write_bytes(b"damaged")
            with self.assertRaises(ValueError): validate_dist(directory, "source")

    def test_published_code_must_increase(self):
        current = dict(applicationId="com.lochanpthakke.empee3", versionCode=5)
        require_increasing_code(current, dict(applicationId=current["applicationId"], versionCode=4))
        for previous in [dict(applicationId=current["applicationId"],versionCode=5),dict(applicationId="other",versionCode=4),{}]:
            with self.assertRaises(ValueError): require_increasing_code(current, previous)
