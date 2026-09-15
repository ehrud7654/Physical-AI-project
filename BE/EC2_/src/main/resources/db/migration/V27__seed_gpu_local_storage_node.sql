INSERT INTO storage_nodes (
    id,
    worker_id,
    node_key,
    name,
    storage_type,
    status,
    capacity_bytes,
    available_bytes,
    last_seen_at
) VALUES (
    '18000000-0000-4000-8000-000000000002'::uuid,
    'ssafy_gpu_01',
    'gpu-01-local-cache',
    'SSAFY GPU Worker',
    'LOCAL',
    'ONLINE',
    1099511627776,
    1099511627776,
    CURRENT_TIMESTAMP
);
