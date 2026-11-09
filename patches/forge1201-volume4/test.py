#!/usr/bin/env python3
"""Verify the exact packaged candidate with no live Minecraft state."""
from pathlib import Path
import sys
import zipfile
import build

root=Path(__file__).resolve().parent
candidate=Path(sys.argv[1]).resolve()
classes=root/'build/candidate'
if classes.exists():build.shutil.rmtree(classes)
with zipfile.ZipFile(candidate) as jar:
    for name in jar.namelist():
        if name.startswith('dev/ringworld/') and name.endswith('.class'):
            target=classes/name;target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(jar.read(name))
# The tests use the packaged candidate classes first, never an earlier compile.
build.javac(root/'build/tests',[root/'VolumeTest.java'],str(classes))
build.run('java','-Xverify:all','-cp',f'{root}/build/tests:{classes}',
          'dev.ringworld.detail.VolumeTest',root/'build/fixture.volume')
build.run(sys.executable,root/'test_integration.py',candidate)
build.run(sys.executable,root/'test_gpu.py',candidate)
