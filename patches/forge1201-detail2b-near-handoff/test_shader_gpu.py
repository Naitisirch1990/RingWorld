#!/usr/bin/env python3
"""Exercise the actual GLSL on an isolated EGL context, without Minecraft."""
import json
from pathlib import Path
import sys
import zipfile
import moderngl
import numpy as np

HERE = Path(__file__).resolve().parent
BASE = Path(sys.argv[1])
ctx = moderngl.create_standalone_context(backend='egl', require=320)
fragment = (HERE / 'ring_surface_1201.fsh').read_text()
with zipfile.ZipFile(BASE) as jar:
    vertex = jar.read('assets/ringworld/shaders/core/ring_surface_1201.vsh').decode()
    original_fragment = jar.read('assets/ringworld/shaders/core/ring_surface_1201.fsh').decode()
    descriptor = json.loads(jar.read('assets/ringworld/shaders/core/ring_surface_1201.json'))
    # Check the real packaged vertex/fragment ABI, not just a test substitute.
    linked = ctx.program(vertex_shader=vertex, fragment_shader=fragment)
    declared = {u['name'] for u in descriptor['uniforms']} | {s['name'] for s in descriptor['samplers']}
    assert {n for n in linked if not n.startswith('gl_')} - {'Position', 'Color'} <= declared

VERTEX = '''#version 150
in vec2 position;
uniform vec3 samplePos;
uniform float sampleSpan;
out vec3 atlasPosition;
out vec4 vertexColor;
out float handoff;
out float flatDistance;
void main() {
    gl_Position = vec4(position, -.4, 1.0);
    atlasPosition = samplePos + vec3((position.x*.5+.5)*sampleSpan, 0.0, 0.0);
    vertexColor = vec4(1.0, 0.0, 0.0, 1.0);
    // Original shader's vertex handoff for a six-chunk radius.
    flatDistance = length(atlasPosition.xz);
    handoff = smoothstep(96.0*.55, 96.0*.85, flatDistance);
}'''
program = ctx.program(vertex_shader=VERTEX, fragment_shader=fragment)
original = ctx.program(vertex_shader=VERTEX, fragment_shader=original_fragment)
vertices = ctx.buffer(np.array([-1, -1, 3, -1, -1, 3], dtype='f4').tobytes())
vao = ctx.simple_vertex_array(program, vertices, 'position')
old_vao = ctx.simple_vertex_array(original, vertices, 'position')
color = ctx.texture((32, 32), 4, dtype='f4')
depth = ctx.depth_texture((32, 32))
fbo = ctx.framebuffer([color], depth)
tex = ctx.texture((8, 8), 1, data=b'\0' * 64)
tex.filter = (moderngl.NEAREST, moderngl.NEAREST)
tex.use(0)
for p in (program, original):
    p['DetailCoverage'].value = 0
    p['ColorModulator'].value = (1, 1, 1, 1)
    if 'RingLayout' in p:
        p['RingLayout'].value = (2048, 64, 0, 0)

def render(pos, radius=96, detail=False, covered=False, camera=(0, 0, 0), span=0, p=program, v=vao):
    fbo.use()
    ctx.viewport = (0, 0, 32, 32)
    fbo.clear(0, 0, 0, 0, depth=1)
    ctx.enable(moderngl.DEPTH_TEST)
    ctx.disable(moderngl.BLEND)
    ctx.depth_func = '<'
    tex.write(bytes([255 if covered else 0]) * 64)
    p['samplePos'].value = pos
    p['sampleSpan'].value = span
    p['DetailInfo'].value = (8, 8, 0, int(detail))
    if 'RingCamera' in p:
        p['RingCamera'].value = (*camera, radius)
    v.render(moderngl.TRIANGLES)
    return np.frombuffer(color.read(), dtype='f4').reshape(32, 32, 4).copy()

checks = []
def check(label, condition):
    assert condition, label
    checks.append(label)

# Protect short/medium terrain in BOTH surface passes at several view distances.
for chunks in (2, 6, 8, 10, 16, 32):
    radius = chunks * 16
    for detail in (False, True):
        pixels = render((radius-16, 0, 0), radius, detail)
        check(f'near discard {chunks} chunks detail={detail}', not pixels.any())

# Demonstrate the original regression with actual fragments and depth writes.
pixels = render((64, 0, 0), p=original, v=old_vao)
check('original displays proxy at 64 blocks', pixels[:, :, 3].max() > 0.1)
pixels = render((64, 0, 0))
check('trial hides proxy at 64 blocks', not pixels.any())
check('discarded fragments keep clear depth', np.allclose(np.frombuffer(depth.read(), dtype='f4'), 1))

for distance, expected in ((80, 0), (88, .5), (96, 1), (200, 1)):
    pixels = render((distance, 0, 0))
    check(f'fade alpha at {distance}', np.allclose(pixels[:, :, 3], expected, atol=1e-5))
    expected_depth = 1 if expected == 0 else 1 - .7*expected
    check(f'fade depth at {distance}', np.allclose(np.frombuffer(depth.read(), dtype='f4'), expected_depth, atol=2e-6))

# Later authoritative terrain must pass a low-opacity proxy's depth test.
live = ctx.program(vertex_shader='''#version 150
in vec2 position;
void main(){gl_Position=vec4(position, .2, 1.0);}''', fragment_shader='''#version 150
out vec4 fragColor;
void main(){fragColor=vec4(0.0,1.0,0.0,1.0);}''')
live_vao = ctx.simple_vertex_array(live, vertices, 'position')
for distance, green in ((88, True), (96, False)):
    render((distance, 0, 0))
    live_vao.render(moderngl.TRIANGLES)
    pixels = np.frombuffer(color.read(), dtype='f4').reshape(32, 32, 4)
    check(f'real terrain depth priority at {distance}', np.allclose(pixels[:, :, 1], int(green)))

# A large primitive crossing the near boundary must mask EACH fragment.
pixels = render((0, 0, 0), span=160)
xs = (np.arange(32)+.5)/32*160
check('large-quad near exclusion', not pixels[:, xs <= 80].any())
check('large-quad far coverage', np.allclose(pixels[:, xs >= 96, 3], 1))

# The seam uses the shortest periodic delta in either direction.
pixels = render((2040, 0, 0), camera=(8, 0, 0))
check('seam near exclusion', not pixels.any())
pixels = render((80, 0, 0), camera=(2040, 0, 0))
check('seam fade matches ordinary 88-block fade', np.allclose(pixels[:, :, 3], .5))
pixels = render((0, 0, 0), camera=(0, 200, 0))
check('high-camera ground remains covered', np.allclose(pixels[:, :, 3], 1))

# Existing coarse/detail dither must stay complementary and preserve fallbacks.
for d in (200, 256, 288, 319, 320, 350):
    coarse = render((d, 0, 0), covered=True)[:, :, 3]
    detailed = render((d, 0, 0), detail=True)[:, :, 3]
    check(f'coarse/detail complement at {d}', np.allclose(coarse+detailed, 1))
    fallback = render((d, 0, 0), covered=False)[:, :, 3]
    check(f'missing detail fallback at {d}', np.allclose(fallback, 1))

print(json.dumps({'result': 'PASS', 'checks': len(checks), 'renderer': ctx.info['GL_RENDERER'],
                  'checks_passed': checks, 'minecraft_runtime_tested': False}, indent=2))
