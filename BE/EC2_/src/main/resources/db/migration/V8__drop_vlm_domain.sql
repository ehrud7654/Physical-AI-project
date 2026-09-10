-- VLM 도메인 제외 (DB 스키마 1.2.0 §17). V1~V7은 수정하지 않는다.
-- 대상: skill_destinations, vlm_model_versions, vlm_policies, vlm_inference_results,
--       skills.needs_vlm, deployment_items.vlm_model_version_id, robot_skill_deployments.vlm_model_version_id

-- 0. 운영 데이터 보호: VLM 추론 결과가 남아 있으면 중단한다 (seed 행만 있는 vlm_policies/skill_destinations는 삭제 대상).
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM vlm_inference_results) THEN
        RAISE EXCEPTION 'vlm_inference_results has rows; archive them before dropping the VLM domain';
    END IF;
END $$;

-- 1. 배포 자격 검증 트리거를 VLM 조건 없는 버전으로 교체 (컬럼 DROP 전에 UPDATE OF 목록에서 제거해야 한다)
CREATE OR REPLACE FUNCTION enforce_deployable_skill_version()
RETURNS trigger AS $$
BEGIN
  IF NOT EXISTS (
    SELECT 1
    FROM skill_versions sv
    WHERE sv.id = NEW.skill_version_id
      AND sv.status = 'READY'
      AND sv.gate_passed = true
  ) THEN
    RAISE EXCEPTION 'SkillVersion is not deployable: %', NEW.skill_version_id;
  END IF;
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER trg_deployment_item_deployable ON deployment_items;
CREATE TRIGGER trg_deployment_item_deployable
BEFORE INSERT OR UPDATE OF skill_version_id ON deployment_items
FOR EACH ROW EXECUTE FUNCTION enforce_deployable_skill_version();

-- 2. FK 종속 순서대로 테이블 제거
DROP TABLE vlm_inference_results;
DROP TABLE skill_destinations;

-- 3. 참조 컬럼 제거 (FK·인덱스 ix_deployment_items_2 는 컬럼과 함께 삭제된다)
ALTER TABLE deployment_items DROP COLUMN vlm_model_version_id;
ALTER TABLE robot_skill_deployments DROP COLUMN vlm_model_version_id;

-- 4. 참조가 없어진 VLM 테이블 제거 (V1 seed default-motion-gate 포함)
DROP TABLE vlm_model_versions;
DROP TABLE vlm_policies;

-- 5. skills.needs_vlm 제거
ALTER TABLE skills DROP COLUMN needs_vlm;
COMMENT ON COLUMN skills.skill_key IS 'FE·BE·AI 공통의 전역 불변 key';
