import json
import pathlib
import subprocess
import sys
import tempfile
import unittest

SCRIPT = pathlib.Path(__file__).with_name('prepare-app-links.py')

class AppLinksPreparationTests(unittest.TestCase):
    def test_prepares_real_format_preserves_other_apps_and_rejects_bad_fingerprints(self):
        with tempfile.TemporaryDirectory() as temp:
            output = pathlib.Path(temp) / '.well-known' / 'assetlinks.json'
            output.parent.mkdir()
            other = {'relation': ['delegate_permission/common.handle_all_urls'], 'target': {
                'namespace': 'android_app', 'package_name': 'example.other', 'sha256_cert_fingerprints': ['BB:' * 31 + 'BB']}}
            output.write_text(json.dumps([other]), encoding='utf-8')
            result = subprocess.run([sys.executable, str(SCRIPT), '--fingerprint', 'AA' * 32, '--output', str(output)], capture_output=True, text=True)
            self.assertEqual(0, result.returncode, result.stderr)
            statements = json.loads(output.read_text(encoding='utf-8'))
            self.assertEqual(other, statements[0])
            self.assertEqual('com.cbulock.minescape', statements[1]['target']['package_name'])
            self.assertEqual(['AA:' * 31 + 'AA'], statements[1]['target']['sha256_cert_fingerprints'])
            before = output.read_bytes()
            result = subprocess.run([sys.executable, str(SCRIPT), '--fingerprint', 'placeholder', '--output', str(output)], capture_output=True, text=True)
            self.assertNotEqual(0, result.returncode)
            self.assertEqual(before, output.read_bytes())

if __name__ == '__main__':
    unittest.main()
