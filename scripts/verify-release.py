"""Fail publication if a runtime JAR has missing translations or the wrong version."""
import json
from pathlib import Path
import sys
import zipfile

root = Path(__file__).resolve().parents[1]
metadata = json.loads((root / 'docs/publishing/release-metadata.json').read_text(encoding='utf-8'))
resources = root / 'fabric/src/main/resources/assets/upgrade/lang'
for target in metadata['files']:
    jar = Path(sys.argv[1]) / target['file']
    with zipfile.ZipFile(jar) as archive:
        mod = json.loads(archive.read('fabric.mod.json'))
        assert mod['version'] == metadata['version'], (jar, mod['version'])
        assert mod['depends']['minecraft'] == target['minecraft'], jar
        for locale in metadata['ui_locales']:
            name = locale + '.json'
            assert archive.read('assets/upgrade/lang/' + name) == (resources / name).read_bytes(), (jar, locale)
        assert not any('ClientSmokeTest' in name or 'LocalizationTest' in name for name in archive.namelist()), jar
    print(f'RELEASE PASS: {jar.name}, version {mod["version"]}, {len(metadata["ui_locales"])} locales')
