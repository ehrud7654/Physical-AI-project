UPDATE skill_versions
SET checkpoint_bucket = 'pjt2-shanks-ssafy-720223570179-ap-northeast-2-an'
WHERE checkpoint_bucket <> 'pjt2-shanks-ssafy-720223570179-ap-northeast-2-an';

UPDATE episode_upload_sessions
SET bucket_name = 'pjt2-shanks-ssafy-720223570179-ap-northeast-2-an'
WHERE bucket_name <> 'pjt2-shanks-ssafy-720223570179-ap-northeast-2-an';
