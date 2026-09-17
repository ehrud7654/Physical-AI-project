# S.S.A.F.Y 프론트엔드 팀 개발 안내

## 준비 환경

- Node.js 22.13 이상
- pnpm 11 권장
- Chrome 또는 Edge 최신 버전

현재 Mock 실행에는 데이터베이스, 서버 계정, 별도 환경 변수가 필요하지 않습니다.

## 처음 실행하기

```bash
git switch fe
git pull origin fe
cd FE
pnpm install
pnpm dev
```

브라우저에서 [http://localhost:3000](http://localhost:3000)을 엽니다. 로그인 모달에는 `admin01`과 `12345678`이 기본 입력되어 있으며, 현재는 빈 값만 아니면 Mock 로그인됩니다.

## 자주 쓰는 명령

```bash
pnpm dev      # 개발 서버
pnpm lint     # ESLint 검사
pnpm build    # 배포용 빌드
pnpm start    # 빌드 결과 실행
```

팀에서는 `pnpm-lock.yaml`을 기준으로 의존성 버전을 맞춥니다. 다른 패키지 관리자로 설치해 lock 파일을 불필요하게 변경하지 마세요.

## 확인 가능한 기능

- 랜딩, 로그인·회원가입과 반응형 내비게이션
- 대시보드에서 장치·학습·작업 상세 이동
- 데이터 상태 필터·정렬·상세·미리보기
- 학습 상태 요약, 새로고침, 상세, 취소·재시도
- 단계형 학습 생성과 학습 결과의 작업 연결
- 작업 검색·상세·편집과 블록 조립
- 모터 범위·반복 블록 검증, 작업 저장·배포·실행
- UMI·로봇 등록·정렬과 로봇 상세·설정
- 사용자별 로봇 접근 권한 관리
- 로딩·오류·빈 상태·토스트

URL에 `?mockError`를 추가하면 초기 데이터 통신 오류 화면을 확인할 수 있습니다.

```text
http://localhost:3000/?mockError
```

## 현재 데이터 보존 범위

- 로그인 상태: `sessionStorage`
- 학습 초안과 새로 완료한 학습 동작: `localStorage`
- 나머지 장치·학습·작업 변경: 현재 탭의 메모리

실제 서버에는 저장되지 않으며 새 환경에서 실행하면 초기 Mock 데이터로 시작합니다.

## 폴더 안내

```text
FE/
├─ app/
│  ├─ page.tsx          # 화면과 상호작용
│  ├─ globals.css       # 디자인 토큰과 반응형 스타일
│  ├─ layout.tsx        # 메타데이터와 글꼴
│  └─ Tutorial.tsx      # 랜딩 튜토리얼
├─ hooks/               # 공통 앱 상태와 배포 흐름
├─ mocks/               # 초기 Mock 데이터
├─ services/            # 비동기 Mock 서비스
├─ types/               # 도메인 타입
├─ public/              # 로고와 이미지
├─ docs/
│  ├─ README.md         # 문서 목차
│  ├─ DESIGN_SPEC.md    # UI·UX 명세
│  └─ TEAM_SETUP.md     # 이 문서
└─ README.md            # 프로젝트 개요와 연동 원칙
```

새 API를 연결할 때는 UI에서 데이터를 직접 처리하지 말고 `services` 구현을 교체하고 `types`를 명세와 맞춥니다. 공통 상태 변경은 `hooks/useAppStore.tsx`를 통해 화면에 전달합니다.

## 브랜치와 검증

최신 `fe`를 기준으로 Jira 키가 포함된 브랜치를 만듭니다.

```bash
git switch fe
git pull origin fe
git switch -c feature/fe/S15P21A103-000-short-description
```

작업 전후로 다음을 확인합니다.

```bash
pnpm lint
pnpm build
git status --short
```

- MR 대상은 `fe`
- 커밋과 MR 제목에 Jira 키 포함
- 기능 단위로 커밋하고 최소 1명 리뷰 후 병합
- 다른 팀원의 변경과 무관한 파일은 함께 커밋하지 않음
- `.vite/`, `dist/`, `*.tsbuildinfo`, 패키지 캐시 등 생성물은 커밋하지 않음

## 배포 설정

`.openai/hosting.json`은 기존 미리보기 프로젝트 설정입니다. 로컬 실행에는 영향을 주지 않습니다. 팀 배포 주소·환경 변수·API 주소가 확정되기 전에는 임의로 변경하지 말고, 배포 작업은 별도 Jira 태스크와 브랜치에서 진행합니다.
