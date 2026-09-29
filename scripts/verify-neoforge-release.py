"""Check the actual archives; no test mod, Fabric bootstrap, or missing translations."""
from pathlib import Path
import json
import tomllib
import zipfile

ROOT = Path(__file__).resolve().parents[1]
LOCALES = set('ar_sa de_de en_us es_es es_mx fr_fr hi_in id_id it_it ja_jp ko_kr nl_nl pl_pl pt_br pt_pt ru_ru th_th tr_tr uk_ua vi_vn zh_cn zh_tw'.split())
for mc in ('1.21.1', '1.21'):
    jar = ROOT / f'neoforge/build/{mc}/libs/upgrade-neoforge-{mc}-0.7.0.jar'
    with zipfile.ZipFile(jar) as z:
        names = set(z.namelist())
        metadata = tomllib.loads(z.read('META-INF/neoforge.mods.toml').decode())
        assert metadata['mods'][0]['modId'] == 'upgrade'
        assert metadata['mods'][0]['version'] == '0.7.0'
        deps = {d['modId']: d for d in metadata['dependencies']['upgrade']}
        assert deps['minecraft']['versionRange'] == f'[{mc}]'
        assert deps['neoforge']['type'] == 'required'
        assert 'fabric.mod.json' not in names
        assert not any('Test' in n or 'testmod' in n or n.startswith('net/fabricmc/') for n in names)
        assert 'LICENSE' in names
        assert 'dev/upgrade/Upgrade.class' in names
        assert 'dev/upgrade/client/ClientEvents.class' in names
        locales = {Path(n).stem for n in names if n.startswith('assets/upgrade/lang/') and n.endswith('.json')}
        assert locales == LOCALES, locales
        baseline = json.loads(z.read('data/upgrade/upgrade_values/baseline.json'))
        assert not any(s.get('tag', '').startswith('forge:') for s in baseline['sources'])
        assert any(s.get('tag') == 'c:ingots/iron' for s in baseline['sources'])
    print(f'PASS: {jar.name} (NeoForge metadata, gameplay, languages, data, no test classes)')
