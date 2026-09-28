"""Run with Python 3: python HW/urdf/check_urdf.py (stdlib only)."""
from pathlib import Path
import math
import struct
import xml.etree.ElementTree as ET

folder = Path(__file__).resolve().parent
root = ET.parse(folder / 'so101_ver1.urdf').getroot()
links = [e.get('name') for e in root.findall('link')]
joints = {e.get('name'): e for e in root.findall('joint')}
assert len(links) == len(set(links)) == 11
assert len(joints) == len(root.findall('joint')) == 10
parents = {}
for joint in joints.values():
    parent, child = joint.find('parent').get('link'), joint.find('child').get('link')
    assert parent in links and child in links and child not in parents
    parents[child] = parent
    if joint.get('type') != 'fixed':
        limit = joint.find('limit')
        assert float(limit.get('lower')) < float(limit.get('upper'))
    mimic = joint.find('mimic')
    if mimic is not None:
        assert mimic.get('joint') in joints
assert set(links) - parents.keys() == {'base_link'}
for link in links:
    seen = set()
    while link in parents:
        assert link not in seen, 'Kinematic cycle'
        seen.add(link)
        link = parents[link]
    assert link == 'base_link'
meshes = root.findall('.//mesh')
assert len(meshes) == 46
paths = {(folder / m.get('filename')).resolve() for m in meshes}
assert len(paths) == 20
for path in paths:
    assert path.is_relative_to(folder) and path.is_file(), path
    if path.suffix == '.stl':
        data = path.read_bytes()
        count = struct.unpack_from('<I', data, 80)[0]
        assert count > 0 and len(data) == 84 + count * 50
        vertices = [t[3:12] for t in struct.iter_unpack('<12fH', data[84:])]
    else:
        lines = path.read_text().splitlines()
        vertices = [[float(x) for x in line.split()[1:]] for line in lines if line.startswith('v ')]
        faces = [[int(x.split('/')[0]) for x in line.split()[1:]] for line in lines if line.startswith('f ')]
        assert vertices and faces and all(1 <= i <= len(vertices) for face in faces for i in face)
    assert all(math.isfinite(x) and abs(x) < 1 for v in vertices for x in v), path
for gap in (0, .045, .09):
    slide = gap / 2
    assert 0 <= slide <= float(joints['gripper'].find('limit').get('upper'))
    mirror = joints['gripper_mirror'].find('mimic')
    assert math.isclose(slide * float(mirror.get('multiplier')) + float(mirror.get('offset')), slide)
    pinion = joints['pinion_passive'].find('mimic')
    angle = slide * float(pinion.get('multiplier')) + float(pinion.get('offset'))
    assert -2.5 - 1e-10 <= angle <= 0
assert 'gripper_tcp' in links
print('URDF_OK: 11 links, 10 joints, 46 mesh references, 20 mesh files; tree, geometry and gap mapping checked')
