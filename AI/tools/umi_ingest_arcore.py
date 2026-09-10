"""Local-only ARCore ingestion. Defaults to validation without writing a dataset."""
import argparse
import io
import json
import sys
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from tools.run_umi_regression import is_shared_gpu_server


def main():
    if is_shared_gpu_server():
        raise SystemExit('Shared GPU server is training-only')
    parser=argparse.ArgumentParser()
    parser.add_argument('--bundle',type=Path,required=True)
    parser.add_argument('--calibration',type=Path,required=True,
                        help='JSON calibration_id, t_cam_to_pinch, t_arcore_world_to_base')
    parser.add_argument('--quality',type=Path,required=True,
                        help='JSON tracking_valid_values, warmup_s, usable_segments, frames_dropped')
    parser.add_argument('--skill-id')
    parser.add_argument('--out',type=Path,help='New output directory; omit for validation only')
    args=parser.parse_args()
    import numpy as np
    from PIL import Image
    from track_a.convert.arcore import to_raw
    from umi.raw import write_raw
    from umi.provenance import conversion_digest
    calibration=json.loads(args.calibration.read_text(encoding='utf-8'))
    quality=json.loads(args.quality.read_text(encoding='utf-8'))
    if args.out is not None and args.out.exists():
        raise SystemExit('Output directory already exists; use a new name')
    def decode(data):
        with Image.open(io.BytesIO(data)) as image:
            return np.array(image.convert('RGB'))
    raw=to_raw(bundle=args.bundle,image_decoder=decode,skill_id=args.skill_id,
               **calibration,**quality)
    raw.meta.notes['tool_sha']=conversion_digest()
    print(json.dumps({'steps':raw.meta.n_steps,'cameras':raw.meta.cameras,
        'source_pose_rows':raw.meta.notes['source_pose_rows'],
        'training_ready':False,'reason':'cam_front and shared training contract still require integration'},
        ensure_ascii=False))
    if args.out is not None:
        print(write_raw(raw,args.out))


if __name__=='__main__': main()
