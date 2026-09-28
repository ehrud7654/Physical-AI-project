"""Blender --background --factory-startup --disable-autoexec Ver1.blend
--python-exit-code 1 --python HW/export_ver1_stl.py

Exports beside this script; refuses overwrites. Never saves the source blend.
"""
import hashlib
import json
import math
from pathlib import Path
import struct

import bmesh
import bpy

ROOT = Path(__file__).resolve().parent
CUSTOM = {
    'GEO-finger-holder-U': '01_finger_holder_right',
    'GEO-finger-holder-U-opposite': '02_finger_holder_left',
    'soft_gripper_finger_60pct': '03_soft_finger_right',
    'soft_gripper_finger_60pct_opposite': '04_soft_finger_left',
    'GEO-center-pinion': '05_center_pinion',
    'GEO-guide-cover-top': '06_guide_cover_top',
    'GEO-guide-cover-bottom': '07_guide_cover_bottom',
    'GEO-wrist-I-adapter': '08_wrist_I_adapter',
}


def bounds(points):
    return [[min(p[i] for p in points) for i in range(3)],
            [max(p[i] for p in points) for i in range(3)]]


def main():
    source = Path(bpy.data.filepath)
    source_hash = hashlib.sha256(source.read_bytes()).hexdigest()
    assert source.name == 'Ver1.blend'
    assert abs(bpy.context.scene.unit_settings.scale_length - 0.001) < 1e-9
    objects = sorted((o for o in bpy.context.scene.objects if o.type == 'MESH'), key=lambda o: o.name)
    assert set(CUSTOM) <= {o.name for o in objects}
    jobs = []
    for obj in objects:
        if obj.name in CUSTOM:
            relative = Path('gripper/stl') / (CUSTOM[obj.name] + '.stl')
            category = 'custom_gripper'
        elif any(c.name == 'COL-SO101-BODY' for c in obj.users_collection):
            relative = Path('so-arm101/ver1-stl') / Path(obj['source_mesh']).name
            category = 'arm_frame_blender_export'
        else:
            relative = Path('assembly/reference-stl') / (obj.name + '.stl')
            category = 'purchased_hardware_reference_only'
        jobs.append((obj, relative, category))
    paths = [ROOT / relative for _, relative, _ in jobs]
    report = ROOT / 'assembly/ver1-stl-manifest.json'
    assert len(set(paths)) == len(paths)
    assert not any(p.exists() for p in paths + [report]), 'Output already exists'
    deps = bpy.context.evaluated_depsgraph_get()
    records = []
    for obj, relative, category in jobs:
        output = ROOT / relative
        output.parent.mkdir(parents=True, exist_ok=True)
        evaluated = obj.evaluated_get(deps)
        mesh = evaluated.to_mesh()
        mesh.calc_loop_triangles()
        vertices = [evaluated.matrix_world @ v.co for v in mesh.vertices]
        source_bounds = bounds([vertices[i] for t in mesh.loop_triangles for i in t.vertices])
        triangle_count = len(mesh.loop_triangles)
        evaluated.to_mesh_clear()
        bpy.ops.object.select_all(action='DESELECT')
        obj.hide_set(False)
        obj.select_set(True)
        bpy.context.view_layer.objects.active = obj
        bpy.ops.wm.stl_export(filepath=str(output), export_selected_objects=True,
                              ascii_format=False, global_scale=1.0, use_scene_unit=False,
                              forward_axis='Y', up_axis='Z', apply_modifiers=True)
        data = output.read_bytes()
        count = struct.unpack_from('<I', data, 80)[0]
        assert count == triangle_count > 0
        assert len(data) == 84 + 50 * count
        points = []
        collapsed_triangles = 0
        for triangle in struct.iter_unpack('<12fH', data[84:]):
            assert all(math.isfinite(v) for v in triangle[:12])
            corners = [triangle[3:6], triangle[6:9], triangle[9:12]]
            collapsed_triangles += len(set(corners)) < 3
            points.extend(corners)
        stl_bounds = bounds(points)
        error = max(abs(a-b) for p, q in zip(source_bounds, stl_bounds) for a, b in zip(p, q))
        assert error < 0.001, (obj.name, error)
        bpy.ops.object.select_all(action='DESELECT')
        bpy.ops.wm.stl_import(filepath=str(output), global_scale=1.0, use_scene_unit=False,
                              forward_axis='Y', up_axis='Z')
        imported = bpy.context.object
        imported.data.calc_loop_triangles()
        reimport_count = len(imported.data.loop_triangles)
        assert reimport_count == count - collapsed_triangles, (obj.name, count, reimport_count, collapsed_triangles)
        imported_bounds = bounds([imported.matrix_world @ v.co for v in imported.data.vertices])
        assert max(abs(a-b) for p,q in zip(stl_bounds, imported_bounds) for a,b in zip(p,q)) < 0.001
        bm = bmesh.new()
        bm.from_mesh(imported.data)
        topology = dict(boundary_edges=sum(e.is_boundary for e in bm.edges),
                        non_manifold_edges=sum(not e.is_manifold for e in bm.edges),
                        inconsistent_winding_edges=sum(e.is_manifold and not e.is_contiguous for e in bm.edges),
                        degenerate_faces=sum(f.calc_area() < 1e-10 for f in bm.faces))
        bm.free()
        imported_mesh = imported.data
        bpy.data.objects.remove(imported, do_unlink=True)
        bpy.data.meshes.remove(imported_mesh)
        records.append(dict(file=relative.as_posix(), object=obj.name, category=category,
                            triangles=count, reimport_triangles=reimport_count,
                            source_collapsed_triangles=collapsed_triangles,
                            bytes=len(data), sha256=hashlib.sha256(data).hexdigest(),
                            bounds_mm=stl_bounds, bounds_error_mm=error, topology=topology,
                            source_mesh=obj.get('source_mesh'), source_commit=obj.get('source_commit')))
    assert hashlib.sha256(source.read_bytes()).hexdigest() == source_hash
    report.write_text(json.dumps(dict(source=source.name, source_sha256=source_hash,
        blender=bpy.app.version_string, units='mm', coordinates='source world; Y forward, Z up',
        verification='binary structure, finite coordinates, triangle count, bounds and Blender reimport checked; topology findings retained without repair',
        parts=records), indent=2) + '\n', encoding='utf-8')
    print('VERIFIED', len(records), 'STL files')
    for r in records:
        print(r['file'], r['triangles'], r['topology'])


if __name__ == '__main__':
    main()
