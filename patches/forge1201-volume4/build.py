#!/usr/bin/env python3
"""Build the exact-input Forge 1.20.1 Overworld-only VOLUME-4 rendering trial."""
import hashlib
from pathlib import Path
import shutil
import subprocess
import sys
import zipfile

HERE = Path(__file__).resolve().parent
INPUT_SHA = '340e43efd072d50fd3ed0a4afbf1d28a512a8a7fc8a847d238fd9a1b396db3fc'
JAVA = 'java'
EXPORT = '--add-exports=java.base/jdk.internal.org.objectweb.asm=ALL-UNNAMED'
STUBS = {
'com/mojang/blaze3d/vertex/VertexFormat.java': '''package com.mojang.blaze3d.vertex;
public class VertexFormat { public enum Mode { QUADS } }''',
'com/mojang/blaze3d/vertex/DefaultVertexFormat.java': '''package com.mojang.blaze3d.vertex;
public class DefaultVertexFormat { public static VertexFormat f_85815_; }''',
'com/mojang/blaze3d/vertex/BufferBuilder.java': '''package com.mojang.blaze3d.vertex;
public class BufferBuilder {
 public void m_166779_(VertexFormat.Mode m,VertexFormat f){}
 public RenderedBuffer m_231175_(){return new RenderedBuffer();}
 public static class RenderedBuffer { public void m_231200_(){} }
}''',
'com/mojang/blaze3d/vertex/Tesselator.java': '''package com.mojang.blaze3d.vertex;
public class Tesselator { public static Tesselator m_85913_(){return null;}
 public BufferBuilder m_85915_(){return null;} }''',
'com/mojang/blaze3d/vertex/VertexBuffer.java': '''package com.mojang.blaze3d.vertex;
public class VertexBuffer { public enum Usage { STATIC }
 public VertexBuffer(Usage u){} public void close(){} public void m_85921_(){}
 public void m_231221_(BufferBuilder.RenderedBuffer b){} public static void m_85931_(){} }''',
}

def run(*args):
    subprocess.run([str(a) for a in args], check=True)

def javac(out, sources, classpath=None, exports=False):
    args = [JAVA]
    if exports: args += [EXPORT]
    args += ['-m','jdk.compiler/com.sun.tools.javac.Main','-encoding','UTF-8','-d',out]
    if exports: args += [EXPORT]
    else: args += ['--release','17']
    if classpath: args += ['-cp',classpath]
    run(*args,*sources)

def compile_patch(original, build):
    stubs = build/'stubs-src'; stub_classes = build/'stubs'; classes = build/'classes'
    for directory in (stubs,stub_classes,classes,build/'adapter'):
        if directory.exists():shutil.rmtree(directory)
    for name, content in STUBS.items():
        file=stubs/name;file.parent.mkdir(parents=True,exist_ok=True);file.write_text(content)
    javac(stub_classes,list(stubs.rglob('*.java')))
    javac(classes,list((HERE/'src').rglob('*.java')),f'{stub_classes}:{original}')
    adapter=build/'adapter'
    javac(adapter,[HERE/'PatchClasses.java'],exports=True)
    run(JAVA,EXPORT,'-cp',adapter,'PatchClasses',original,classes)
    return classes

def main():
    if len(sys.argv)!=3: raise SystemExit('Usage: build.py ORIGINAL.jar OUTPUT.jar')
    original=Path(sys.argv[1]).resolve();output=Path(sys.argv[2]).resolve()
    if original==output or output.exists():raise SystemExit('Keep the original; output must be a new path')
    if hashlib.sha256(original.read_bytes()).hexdigest()!=INPUT_SHA:raise SystemExit('Unexpected baseline SHA-256')
    build=HERE/'build';build.mkdir(exist_ok=True)
    classes=compile_patch(original,build)
    generated={p.relative_to(classes).as_posix():p.read_bytes() for p in classes.rglob('*.class')}
    # Source stubs are documented build-time placeholders and NEVER runtime classes.
    if any(not n.startswith('dev/ringworld/') for n in generated):raise SystemExit('Unexpected runtime class')
    with zipfile.ZipFile(original) as src:
        names=src.namelist()
        if len(names)!=len(set(names)):raise SystemExit('Duplicate baseline entries')
        if any(n.upper().endswith(('.SF','.RSA','.DSA','.EC')) for n in names if n.startswith('META-INF/')):raise SystemExit('Signed baseline')
        replacements=dict(generated)
        shader='assets/ringworld/shaders/core/ring_surface_1201.fsh'
        replacements[shader]=(HERE/'ring_surface_1201.fsh').read_bytes()
        mods=src.read('META-INF/mods.toml').decode().replace('0.1.0-alpha.6+mc1.20.1.owonly.test1','0.1.0-alpha.6+mc1.20.1.owonly.volume4')
        mods=mods.replace('RingWorld Atlas - Overworld Only test1','RingWorld Atlas - Overworld Only VOLUME-4 trial')
        replacements['META-INF/mods.toml']=mods.encode()
        replacements['ringworld-build.properties']=b'artifactVersion=0.1.0-alpha.6+mc1.20.1.owonly.volume4\nreleaseLabel=Atlas Overworld Only VOLUME-4 trial\n'
        with zipfile.ZipFile(output,'x',compression=zipfile.ZIP_DEFLATED) as dst:
            for item in src.infolist():dst.writestr(item,replacements.pop(item.filename,src.read(item.filename)))
            for name,content in sorted(replacements.items()):dst.writestr(name,content)
            # Complete modified source, adapters, tests and reproducible build instructions.
            for p in sorted(HERE.rglob('*')):
                relative=p.relative_to(HERE)
                if p.is_file() and not {'build','__pycache__'} & set(relative.parts):
                    dst.writestr('META-INF/volume4/'+relative.as_posix(),p.read_bytes())
    with zipfile.ZipFile(original) as src,zipfile.ZipFile(output) as dst:
        assert dst.testzip() is None
        changed={n for n in src.namelist() if src.read(n)!=dst.read(n)}
        expected=set(generated)&set(src.namelist())|{'META-INF/mods.toml','ringworld-build.properties',shader}
        assert changed==expected,(changed,expected)
        assert all(src.read(n)==dst.read(n) for n in src.namelist()
                   if '/overworld-only/' in n or n.endswith(('.vsh','.json')) or n.endswith('.fsh') and n!=shader)
        print('Archive verification PASS; changed entries:',*sorted(changed),sep='\n')
    print(hashlib.sha256(output.read_bytes()).hexdigest(),output.name)

if __name__=='__main__':main()
