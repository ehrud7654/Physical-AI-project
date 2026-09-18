UPDATE skill_versions
SET checkpoint_bytes = 71,
    checkpoint_sha256 = 'e9682288c1a5b34e31977d484cc433d8e03fc202fd84e0a19977cd5143289e0e'
WHERE id = '56000000-0000-4000-8000-000000000001'
  AND skill_id = '54000000-0000-4000-8000-000000000001'
  AND version = 1
  AND checkpoint_key = 'ckpt/50000000-0000-4000-8000-000000000001/grab_blue_flag/v1/grab_blue_flag_20260909T001000Z.ckpt';

UPDATE skill_versions
SET checkpoint_bytes = 70,
    checkpoint_sha256 = 'c3c7269a691a71abbb5d7012a85981a0e016f8ddf544ff91ff9e2b627a7ea5b1'
WHERE id = '56000000-0000-4000-8000-000000000002'
  AND skill_id = '54000000-0000-4000-8000-000000000002'
  AND version = 1
  AND checkpoint_key = 'ckpt/50000000-0000-4000-8000-000000000001/raise_arm/v1/raise_arm_20260909T002000Z.ckpt';
