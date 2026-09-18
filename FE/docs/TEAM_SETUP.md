# S.S.A.F.Y 프론트엔드 팀 개발 안내

## 준비 환경

- Node.js 22.13 이상
- pnpm 11
- Chrome 또는 Edge 최신 버전
- 백엔드: 로컬 `http://localhost:8080` 또는 배포 서버(아래 프록시 설정)

## 처음 실행하기

```bash
git switch fe
git pull origin fe
cd FE
pnpm install
pnpm dev
```

브라우저에서 [http://localhost:3000](http://localhost:3000)을 엽니다. 로그인 폼에는 데모 계정 `admin01@ssafy.local` / `12345678`이 기본 입력되어 있으며, 실제 `POST /auth/login`으로 인증합니다.

### 배포 서버 API를 로컬에서 쓰기

백엔드 CORS 허용목록에 로컬 origin이 없어 브라우저가 직접 호출하면 403이 납니다. `FE/.env.local`(git 제외)을 만들면 dev 서버가 `/api` 요청을 프록시합니다.

```env
NEXT_PUBLIC_API_BASE_URL=/api/v1
DEV_API_PROXY=http://<배포 서버 호스트>
```

## 자주 쓰는 명령

```bash
pnpm dev      # 개발 서버
pnpm lint     # ESLint 검사 (오류 0 유지)
pnpm build    # 프로덕션 빌드 → dist/
pnpm start    # dist/ 실행 (PORT 기본 3000)
npx tsc --noEmit   # 타입 검사 (빌드는 타입 검사를 하지 않음)
```

의존성은 `pnpm-lock.yaml` 기준입니다. npm/yarn으로 설치하지 마세요.

## 코드 배치 규칙

```text
app/(console)/**/page.tsx   라우트 → 뷰 연결과 URL 파라미터 처리만
components/views/*View.tsx  화면 로직·마크업
services/*.ts               REST 호출과 API ↔ 화면 모델 변환
hooks/useAppStore.tsx       여러 화면이 공유하는 도메인 상태
lib/*.ts                    순수 함수(테스트 가능, React 의존 없음)
types/index.ts              공통 타입
```

- 새 API는 `services`에 래퍼를 두고 `types`를 명세와 맞춥니다. 뷰에서 `fetch`를 직접 쓰지 않습니다.
- 인증 헤더·401 처리·base URL은 `services/client.ts`가 담당하므로 각 서비스는 경로만 씁니다.
- 화면 사이 이동은 `lib/routes.ts`의 `viewToPath`를 거칩니다.
- `react-hooks/set-state-in-effect` 규칙을 어길 수밖에 없는 마운트 시 조회는 기존 파일처럼 `/* eslint-disable … -- 이유 */` 구간으로 감쌉니다.
- CSS는 `app/globals.css` 한 파일입니다. 새 규칙은 파일 끝에 목적을 적은 주석 블록으로 추가하고, 미사용 클래스는 남기지 않습니다.

## 브라우저 저장 데이터

| 키 | 내용 |
|---|---|
| `localStorage['ssafy:auth-session']` | 액세스 토큰·만료 시각·사용자 정보 (로그아웃·만료 시 삭제) |
| `localStorage['ssafy-learning-draft-v1']` | 진행 중인 학습 생성 초안 |

## 브랜치와 검증

최신 기준 브랜치에서 Jira 키가 포함된 브랜치를 만듭니다.

```bash
git switch fe
git pull origin fe
git switch -c feat/fe/S15P21A103-000-short-description
```

작업 전후로 확인합니다.

```bash
pnpm lint
pnpm build
git status --short
```

- 커밋 메시지는 한 줄 `type: 설명 (S15P21A103-NNN)`
- MR 대상은 `fe`, 최소 1명 리뷰 후 병합
- 다른 팀원의 변경과 무관한 파일은 함께 커밋하지 않음
- `dist/`, `.vite/`, `*.tsbuildinfo`, `.env*`, 패키지 캐시 등 생성물은 커밋하지 않음

## 배포 설정

`FE/Dockerfile`이 `pnpm build` 후 `pnpm start`(vinext Node 프로덕션 서버)를 실행합니다. 빌드 인자 `NEXT_PUBLIC_API_BASE_URL`은 `BE/EC2_/compose.yaml`에서 `/api/v1`로 넘기며, nginx가 `/api/`를 백엔드로 전달합니다. 초기 스캐폴드에 있던 Cloudflare Workers·`.openai/hosting.json` 설정은 사용하지 않아 제거했습니다. 배포 설정 변경은 별도 Jira 태스크와 브랜치에서 진행합니다.
