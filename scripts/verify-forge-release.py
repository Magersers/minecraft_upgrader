"""Check the distributable, not the development classpath."""
from pathlib import Path
import json, sys, zipfile
for mc in sys.argv[1:] or ['1.20.1','1.19.2']:
    jar=Path(f'forge/build/{mc}/libs/upgrade-forge-{mc}-0.7.0.jar')
    with zipfile.ZipFile(jar) as z:
        names=z.namelist()
        meta=z.read('META-INF/mods.toml').decode()
        assert f'versionRange="[{mc}]"' in meta and 'modId="forge"' in meta
        assert 'version="0.7.0"' in meta and '${' not in meta
        assert not any('Test' in n or 'Fixture' in n or 'testmod' in n for n in names), 'Test code shipped'
        assert 'fabric.mod.json' not in names and 'META-INF/neoforge.mods.toml' not in names
        langs=[n for n in names if n.startswith('assets/upgrade/lang/') and n.endswith('.json')]
        assert len(langs)==22
        for lang in langs: assert len(json.loads(z.read(lang)))>40
        assert b'forge:' in z.read('data/upgrade/upgrade_values/baseline.json')
        for n in names:
            if n.endswith('.class'): assert int.from_bytes(z.read(n)[6:8],'big')==61, n
    print(f'PASS {jar}: exact Forge metadata, Java 17, 22 languages, no test code')
