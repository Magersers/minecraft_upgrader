"""Download pinned public test mods, verify content, and expose their nested compile dependencies."""
import hashlib
import json
from pathlib import Path
import sys
import time
import urllib.request
import zipfile

root = Path(__file__).resolve().parent.parent
out = Path(sys.argv[1])
out.mkdir(parents=True, exist_ok=True)
for entry in json.loads((root / 'fabric/test-mods/betterx-1.20.1.json').read_text()):
    target = out / entry['file']
    if not target.exists() or hashlib.sha512(target.read_bytes()).hexdigest() != entry['sha512']:
        for attempt in range(5):
            try:
                with urllib.request.urlopen(entry['url'], timeout=90) as response:
                    data = response.read()
                if hashlib.sha512(data).hexdigest() != entry['sha512']:
                    raise ValueError('Checksum mismatch: ' + entry['file'])
                target.write_bytes(data)
                break
            except Exception:
                if attempt == 4:
                    raise
                time.sleep(2)
    with zipfile.ZipFile(target) as archive:
        metadata = json.loads(archive.read('fabric.mod.json'), strict=False)
        for nested in metadata.get('jars', []):
            name = nested['file']
            (out / Path(name).name).write_bytes(archive.read(name))
    print('Verified', target.name)
