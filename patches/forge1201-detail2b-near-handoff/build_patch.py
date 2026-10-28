#!/usr/bin/env python3
"""Apply a resource-only trial to the exact user-supplied DETALLE-2B build."""
import argparse
import hashlib
from pathlib import Path
import zipfile

BASE_SHA256 = '7a51364cc29dee112695d46f62157bb72bd29ca668aab3e9694bce837497bf12'
SHADER = 'assets/ringworld/shaders/core/ring_surface_1201.fsh'
PREFIX = 'META-INF/ringworld-visual3/'

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('original', type=Path)
    parser.add_argument('output', type=Path)
    args = parser.parse_args()
    if args.original.resolve() == args.output.resolve():
        parser.error('Output must differ from the original JAR')
    if hashlib.sha256(args.original.read_bytes()).hexdigest() != BASE_SHA256:
        parser.error('Input is not the qualified DETALLE-2B baseline; refusing to patch')
    here = Path(__file__).resolve().parent
    additions = {
        PREFIX + name: (here / name).read_bytes()
        for name in ('ring_surface_1201.fsh', 'build_patch.py', 'README.md')
    }
    with zipfile.ZipFile(args.original) as src:
        names = src.namelist()
        if len(set(names)) != len(names):
            parser.error('Duplicate JAR entries')
        if any(n.upper().endswith(('.SF', '.RSA', '.DSA', '.EC')) for n in names if n.startswith('META-INF/')):
            parser.error('Signed baseline must not be repackaged')
        if any(n in names for n in additions):
            parser.error('Already patched baseline')
        with zipfile.ZipFile(args.output, 'x', compression=zipfile.ZIP_DEFLATED) as dst:
            for item in src.infolist():
                dst.writestr(item, (here / 'ring_surface_1201.fsh').read_bytes()
                             if item.filename == SHADER else src.read(item.filename))
            for name, content in additions.items():
                info = zipfile.ZipInfo(name, date_time=(2026, 10, 28, 0, 0, 0))
                info.compress_type = zipfile.ZIP_DEFLATED
                dst.writestr(info, content)
    with zipfile.ZipFile(args.original) as src, zipfile.ZipFile(args.output) as dst:
        assert dst.testzip() is None
        changed = [n for n in src.namelist() if src.read(n) != dst.read(n)]
        assert changed == [SHADER], changed
        assert set(dst.namelist()) - set(src.namelist()) == set(additions)
    print('PASS: only the fragment shader changed; all Java classes, loader metadata,')
    print('networking, worldgen, storage, terrain shaders and original licence are byte-identical.')
    print(hashlib.sha256(args.output.read_bytes()).hexdigest(), args.output.name)

if __name__ == '__main__':
    main()
