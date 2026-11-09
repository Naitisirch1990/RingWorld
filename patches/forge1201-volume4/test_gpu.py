#!/usr/bin/env python3
"""Draw a saved-region mesh fixture with the actual packaged GLSL in EGL."""
import json
from pathlib import Path
import struct
import sys
import zipfile
import zlib
import moderngl
import numpy as np

root=Path(__file__).resolve().parent
raw=zlib.decompress((root/'build/fixture.volume').read_bytes())
magic,cx,cz,count=struct.unpack_from('>IiiH',raw)
assert magic==0x52445634
vertices=[]
for i in range(count):
    x,y,z,du,dv,direction=struct.unpack_from('>BhBHHB',raw,14+i*12)
    rgb=int.from_bytes(raw[14+i*12+9:14+i*12+12],'big')
    d=direction//2;u=(d+1)%3;v=(d+2)%3
    p=np.array([cx*16+x,y,cz*16+z],dtype=float)
    points=[p.copy()];p[u]+=du;points.append(p.copy());p[v]+=dv;points.append(p.copy());p[u]-=du;points.append(p.copy())
    color=[((rgb>>shift)&255)/255 for shift in (16,8,0)]+[1]
    for j in (0,1,2,0,2,3):vertices.append(list(points[j])+color)

ctx=moderngl.create_standalone_context(backend='egl',require=320)
with zipfile.ZipFile(sys.argv[1]) as jar:
    prefix='assets/ringworld/shaders/core/ring_surface_1201.'
    program=ctx.program(vertex_shader=jar.read(prefix+'vsh').decode(),fragment_shader=jar.read(prefix+'fsh').decode())
    descriptor=json.loads(jar.read(prefix+'json'))
    declared={u['name'] for u in descriptor['uniforms']}|{s['name'] for s in descriptor['samplers']}|{'Position','Color'}
    assert set(program)<=declared
buffer=ctx.buffer(np.array(vertices,dtype='f4').tobytes())
vao=ctx.vertex_array(program,[(buffer,'3f 4f','Position','Color')])
color=ctx.texture((256,256),4,dtype='f4');depth=ctx.depth_texture((256,256))
fbo=ctx.framebuffer([color],depth);fbo.use();fbo.clear(0,0,0,0,depth=1)
ctx.enable(moderngl.DEPTH_TEST);ctx.disable(moderngl.CULL_FACE)
view=np.diag([1,1,-1,1]).astype('f4')
left,right,bottom,top,near,far=-2,18,-45,45,1,500
projection=np.array([[2/(right-left),0,0,-(right+left)/(right-left)],
 [0,2/(top-bottom),0,-(top+bottom)/(top-bottom)],
 [0,0,-2/(far-near),-(far+near)/(far-near)],[0,0,0,1]],dtype='f4')
program['ModelViewMat'].write(view.T.tobytes());program['ProjMat'].write(projection.T.tobytes())
program['RingCamera'].value=(0,120,-200,144) # nine-chunk real view
program['RingLayout'].value=(61440,64,0,0)
program['ColorModulator'].value=(1,1,1,1)
program['DetailInfo'].value=(8,8,0,1)
texture=ctx.texture((8,8),1,data=b'\xff'*64);texture.use(0);program['DetailCoverage'].value=0
vao.render(moderngl.TRIANGLES)
image=np.frombuffer(color.read(),dtype='f4').reshape(256,256,4)
assert image[:100,:,3].max()>.9,'lower structure is visible'
assert image[170:,:,3].max()>.9,'floating hull is visible'
assert not image[110:145,:,3].any(),'air gap must remain empty, without curtains'
assert np.allclose(np.frombuffer(depth.read(),dtype='f4').reshape(256,256)[110:145],1),'gap must not occlude'
print(json.dumps({'result':'PASS','renderer':ctx.info['GL_RENDERER'],
 'checks':['packaged GLSL link and descriptor ABI','nine-chunk camera','lower and upper surfaces render',
 'volumetric gap is visually empty','gap depth stays clear'], 'minecraft_runtime_tested':False},indent=2))
