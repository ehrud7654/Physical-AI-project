-- Replace provisional seed metadata with the exact S3 object size and SHA-256.
UPDATE episode_archives
SET size_bytes = 25688942,
    sha256 = '6dca74ab39952848a1ab89f356a9fae42635f7154f4ce7ef118cfc7c276f3839',
    updated_at = CURRENT_TIMESTAMP
WHERE episode_id = '61000000-0000-4000-8000-000000000001'
  AND storage_key = 'episodes/50000000-0000-4000-8000-000000000001/20260915T031755Z/b4a1d35f-9b90-4c49-8f49-bd4ca35059b1.zip';

UPDATE episode_archives
SET size_bytes = 24923663,
    sha256 = 'a7cecbac803ed8e00a1904adbeb5e53c7dd70b0a4902b1a6119353df494a29b7',
    updated_at = CURRENT_TIMESTAMP
WHERE episode_id = '61000000-0000-4000-8000-000000000002'
  AND storage_key = 'episodes/50000000-0000-4000-8000-000000000001/20260915T031800Z/dff0f188-a016-46e3-a39b-8050711ce130.zip';

UPDATE episode_archives
SET size_bytes = 21030598,
    sha256 = 'ccf8a676edc1f2ae3a2a9899a84c5a055bd4114cf5c9b2012cbaf34318446812',
    updated_at = CURRENT_TIMESTAMP
WHERE episode_id = '61000000-0000-4000-8000-000000000003'
  AND storage_key = 'episodes/50000000-0000-4000-8000-000000000001/20260915T031805Z/13845047-dc3f-4ce5-985f-e9d9baece5bb.zip';

UPDATE episode_archives
SET size_bytes = 28009097,
    sha256 = 'fbce64fe99c1140078bf8a231c77ef0646ae81e16d4445fce3a8a04f6cf6af72',
    updated_at = CURRENT_TIMESTAMP
WHERE episode_id = '61000000-0000-4000-8000-000000000004'
  AND storage_key = 'episodes/50000000-0000-4000-8000-000000000001/20260915T031809Z/0b4a7981-552e-40e5-8db3-fdcbdd0b5ca3.zip';
