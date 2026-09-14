ALTER TABLE idempotency_keys
    DROP CONSTRAINT idempotency_keys_actor_type_check;

ALTER TABLE idempotency_keys
    ADD CONSTRAINT idempotency_keys_actor_type_check
    CHECK (actor_type IN ('USER', 'DEVICE', 'UMI', 'GPU', 'JETSON', 'INTERNAL'));
