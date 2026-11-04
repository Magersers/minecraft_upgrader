"""Verify every declared production archive before publishing a release."""
import hashlib
import json
from pathlib import Path
import sys
import tomllib
import zipfile

root = Path(__file__).resolve().parents[1]
metadata = json.loads((root / 'docs/publishing/release-metadata.json').read_text(encoding='utf-8'))
resources = root / 'fabric/src/main/resources/assets/upgrade/lang'
release = Path(sys.argv[1])
checksums = []
for target in metadata['files']:
    jar = release / target['file']
    with zipfile.ZipFile(jar) as archive:
        names = set(archive.namelist())
        loader = target['loader']
        if loader == 'fabric':
            mod = json.loads(archive.read('fabric.mod.json'))
            assert mod['id'] == 'upgrade'
            assert mod['version'] == metadata['version'], (jar, mod['version'])
            assert mod['depends']['minecraft'] == target['minecraft'], jar
            assert not any(n in names for n in ('META-INF/mods.toml', 'META-INF/neoforge.mods.toml'))
            assert not any('Probe' in str(e) or 'Test' in str(e) for e in mod.get('entrypoints', {}).values())
        else:
            entry = 'META-INF/neoforge.mods.toml' if loader == 'neoforge' else 'META-INF/mods.toml'
            mod = tomllib.loads(archive.read(entry).decode())
            assert mod['mods'][0]['modId'] == 'upgrade'
            assert mod['mods'][0]['version'] == metadata['version'], jar
            deps = {d['modId']: d for d in mod['dependencies']['upgrade']}
            assert deps['minecraft']['versionRange'] == f'[{target["minecraft"]}]', jar
            assert loader in deps and 'fabric.mod.json' not in names, jar
        for locale in metadata['ui_locales']:
            name = locale + '.json'
            assert archive.read('assets/upgrade/lang/' + name) == (resources / name).read_bytes(), (jar, locale)
        assert 'LICENSE' in names, jar
        assert 'dev/upgrade/client/UpgradeScreen.class' in names, jar
        assert not any(('Test' in n or 'Probe' in n or 'Fixture' in n or 'testmod' in n) and n.endswith('.class') for n in names), f'Test code shipped: {jar}'
        for name in names:
            if name.startswith('dev/upgrade/') and name.endswith('.class'):
                assert int.from_bytes(archive.read(name)[6:8], 'big') == target['java_min'] + 44, (jar, name)
    checksums.append(f'{hashlib.sha256(jar.read_bytes()).hexdigest()}  {jar.name}')
    print(f'RELEASE PASS: {jar.name}, exact loader/version, Java {target["java_min"]}, {len(metadata["ui_locales"])} locales, no test code')
(release / 'SHA256SUMS.txt').write_text('\n'.join(checksums) + '\n', encoding='utf-8')
