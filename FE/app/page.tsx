'use client';

import { FormEvent, useEffect, useState } from 'react';
import type { BlockKind, Dataset, DeviceType, TrainingStatus, View, WorkflowBlock } from '@/types';
import { blockCatalog, motorRanges } from '@/mocks';
import { AppStoreProvider, useAppStore } from '@/hooks/useAppStore';

let nextBlockId = 20;

function makeBlock(kind: BlockKind): WorkflowBlock {
  const item = blockCatalog.find((block) => block.kind === kind)!;
  return {
    id: nextBlockId++, kind, title: item.title,
    ...(kind === 'motor' ? { motor: 'J1', angle: 45 } : {}),
    ...(kind === 'repeat' ? { repeat: 3, children: [] } : {}),
  };
}

export default function Home() {
  return (
    <AppStoreProvider>
      <HomeInner />
    </AppStoreProvider>
  );
}

function HomeInner() {
  const [entered, setEntered] = useState(false);
  const [loggedIn, setLoggedIn] = useState(false);
  const [view, setView] = useState<View>('dashboard');
  const [selectedRobotId, setSelectedRobotId] = useState('RB-07');
  const [toast, setToast] = useState('');

  /* Figma 캡처 쿼리를 최초 진입 화면 상태에 한 번 반영한다. */
  /* eslint-disable react-hooks/set-state-in-effect */
  useEffect(() => {
    const captureView = new URLSearchParams(window.location.search).get('figmaView') as View | null;
    if (captureView && ['dashboard', 'devices', 'robot-detail', 'training', 'learn', 'work', 'admin'].includes(captureView)) {
      setView(captureView);
      setEntered(true);
      setLoggedIn(true);
    }
  }, []);
  /* eslint-enable react-hooks/set-state-in-effect */

  useEffect(() => {
    if (!toast) return;
    const timer = window.setTimeout(() => setToast(''), 2200);
    return () => window.clearTimeout(timer);
  }, [toast]);

  const enterView = (target: View) => { setView(target); setEntered(true); setLoggedIn(true); };
  if (!entered) return <Landing onLogin={() => enterView('dashboard')} onExplore={() => enterView('dashboard')} onNavigate={enterView} />;
  if (!loggedIn) return <Login onLogin={() => setLoggedIn(true)} />;

  return (
    <main className="app-shell">
      <GlobalHeader authenticated activeView={view} onHome={() => { setEntered(false); setLoggedIn(false); }} onNavigate={setView} onAuth={() => { setLoggedIn(false); setEntered(false); }} onConsole={() => setView('dashboard')} />

      <section className="content">
        {view === 'dashboard' && <DashboardView onNavigate={setView} onSelectRobot={(id) => { setSelectedRobotId(id); setView('robot-detail'); }} />}
        {view === 'devices' && <DevicesView onSelectRobot={(id) => { setSelectedRobotId(id); setView('robot-detail'); }} onToast={setToast} />}
        {view === 'robot-detail' && <RobotDetailView robotId={selectedRobotId} onBack={() => setView('devices')} onNavigate={setView} />}
        {view === 'training' && <TrainingStatusView onNavigate={setView} />}
        {view === 'learn' && <LearnView onToast={setToast} onGoWork={() => setView('work')} />}
        {view === 'work' && <WorkView onToast={setToast} />}
        {view === 'admin' && <AdminView onToast={setToast} />}
      </section>
      {toast && <div className="toast" role="status"><span>✓</span>{toast}</div>}
    </main>
  );
}

function Landing({ onLogin, onExplore, onNavigate }: { onLogin: () => void; onExplore: () => void; onNavigate: (view: View) => void }) {
  const [loginOpen, setLoginOpen] = useState(() => typeof window !== 'undefined' && new URLSearchParams(window.location.search).get('figmaView') === 'login');
  const [id, setId] = useState('admin01');
  const [password, setPassword] = useState('12345678');
  return <main className="landing-page">
    <GlobalHeader authenticated={false} onHome={() => setLoginOpen(false)} onNavigate={onNavigate} onAuth={() => setLoginOpen(true)} onConsole={onExplore} />
    <section className="landing-hero">
      <div className="landing-copy">
        <p>SHOW · LEARN · OPERATE</p>
        <h1>당신이 보여주는 순간,<br />로봇의 일이 시작합니다.</h1>
        <h2>UMI 시연 데이터부터 학습, 배포, 다중 로봇 관제까지 하나의 플랫폼에서.</h2>
        <div className="landing-actions"><button onClick={onExplore}>플랫폼 둘러보기</button><button onClick={() => setLoginOpen(true)}>로그인하기 <span>→</span></button></div>
      </div>
      <div className="landing-proof"><span><strong>ONE</strong>통합 운영 환경</span><span><strong>LIVE</strong>실시간 학습 현황</span><span><strong>SAFE</strong>검증된 작업 실행</span></div>
    </section>
    {loginOpen && <div className="landing-login-backdrop" onMouseDown={() => setLoginOpen(false)}><form className="landing-login-card" onSubmit={(event) => { event.preventDefault(); if(id && password) onLogin(); }} onMouseDown={(event) => event.stopPropagation()}>
      <div className="landing-login-head"><img src="/ssafy-logo-alpha.png" alt="S.S.A.F.Y" /><button type="button" onClick={() => setLoginOpen(false)} aria-label="로그인 닫기">×</button></div>
      <p className="eyebrow">ROBOT LEARNING CONSOLE</p><h2>플랫폼에 로그인</h2><p>시연 데이터와 로봇 운영 현황을 확인하세요.</p>
      <label>아이디<input value={id} onChange={(event) => setId(event.target.value)} autoComplete="username" /></label><label>비밀번호<input type="password" value={password} onChange={(event) => setPassword(event.target.value)} autoComplete="current-password" /></label>
      <button className="primary-button" disabled={!id || !password}>로그인하고 시작하기</button><small>와이어프레임 확인용 예시 계정이 입력되어 있어요.</small>
    </form></div>}
  </main>;
}

function GlobalHeader({ authenticated, activeView, onHome, onNavigate, onAuth, onConsole }: { authenticated: boolean; activeView?: View; onHome: () => void; onNavigate: (view: View) => void; onAuth: () => void; onConsole: () => void }) {
  return <header className="global-header">
    <button className="global-brand" onClick={onHome} aria-label="S.S.A.F.Y 메인으로 이동"><img src="/ssafy-logo-alpha.png" alt="S.S.A.F.Y" /><span className="brand-fullname">Show Simply Arm Follows You</span></button>
    <nav className="global-nav" aria-label="플랫폼 메뉴">
      <button className={activeView === 'dashboard' ? 'active' : ''} onClick={() => onNavigate('dashboard')}>PLATFORM</button>
      <button className={activeView === 'training' || activeView === 'learn' ? 'active' : ''} onClick={() => onNavigate('training')}>LEARNING</button>
      <button className={activeView === 'devices' || activeView === 'robot-detail' ? 'active' : ''} onClick={() => onNavigate('devices')}>FLEET</button>
      <button className={activeView === 'work' ? 'active' : ''} onClick={() => onNavigate('work')}>WORKFLOW</button>
    </nav>
    <div className="global-actions"><button className="global-auth" onClick={onAuth}>{authenticated ? 'SIGN OUT' : 'SIGN IN'}</button><button className="global-console" onClick={onConsole}>OPEN CONSOLE</button></div>
  </header>;
}

function Login({ onLogin }: { onLogin: () => void }) {
  const [id, setId] = useState('admin01');
  const [password, setPassword] = useState('12345678');

  const submit = (event: FormEvent) => {
    event.preventDefault();
    if (id && password) onLogin();
  };

  return (
    <main className="login-page">
      <section className="login-intro">
        <div className="login-brand"><img className="login-logo" src="/ssafy-logo-alpha.png" alt="S.S.A.F.Y" /><span>Robot Learning Platform</span></div>
        <div className="intro-copy">
          <span className="intro-kicker">SHOW · LEARN · WORK</span>
          <h1>보여주기만 하면,<br />로봇이 배워요.</h1>
          <p>복잡한 코딩 없이 직접 동작을 보여주고<br />나만의 로봇 작업을 만들어 보세요.</p>
        </div>
        <div className="robot-visual" aria-hidden="true">
          <div className="robot-base" /><div className="robot-arm arm-one" /><div className="robot-joint joint-one" />
          <div className="robot-arm arm-two" /><div className="robot-joint joint-two" /><div className="robot-grip">⌁</div>
          <div className="visual-chip chip-one">동작 수집</div><div className="visual-chip chip-two">Skill 학습</div><div className="visual-chip chip-three">작업 실행</div>
        </div>
        <small className="copyright">© 2026 S.S.A.F.Y · Show Simply Arm Follows You</small>
      </section>
      <section className="login-panel">
        <form className="login-card" onSubmit={submit}>
          <img className="login-card-logo" src="/ssafy-logo-alpha.png" alt="S.S.A.F.Y" />
          <span className="welcome-icon">👋</span>
          <h2>다시 만나서 반가워요!</h2>
          <p>계정 정보를 입력하고 로봇 학습을 시작하세요.</p>
          <label>아이디<input value={id} onChange={(e) => setId(e.target.value)} placeholder="아이디를 입력하세요" autoComplete="username" /></label>
          <label>비밀번호<input value={password} onChange={(e) => setPassword(e.target.value)} type="password" placeholder="비밀번호를 입력하세요" autoComplete="current-password" /></label>
          <button className="primary-button login-button" disabled={!id || !password}>로그인</button>
          <small className="demo-note">와이어프레임 확인용 예시 계정이 입력되어 있어요.</small>
        </form>
      </section>
    </main>
  );
}

function DashboardView({ onNavigate, onSelectRobot }: { onNavigate: (view: View) => void; onSelectRobot: (id: string) => void }) {
  const { robots, umis, trainings } = useAppStore();
  const umiOnline = umis.filter((item) => item.status === '온라인').length;
  const robotAvailable = robots.filter((item) => item.status === '온라인').length;
  const runningTrainings = trainings.filter((item) => item.status === '진행 중');
  const averageProgress = runningTrainings.length ? Math.round(runningTrainings.reduce((sum, item) => sum + item.progress, 0) / runningTrainings.length) : 0;
  return <>
    <div className="page-heading"><div><p className="eyebrow">운영 요약</p><h1>안녕하세요, 김관리님</h1><p>연결된 장치와 진행 중인 학습 상태를 한눈에 확인하세요.</p></div><button className="primary-button" onClick={() => onNavigate('learn')}>＋ 새 학습 시작</button></div>
    <div className="overview-cards">
      <button onClick={() => onNavigate('devices')}><span className="overview-icon blue">U</span><div><small>등록된 UMI</small><strong>{umis.length}<em>대</em></strong><p><i className="online-dot" /> {umiOnline}대 연결됨</p></div></button>
      <button onClick={() => onNavigate('devices')}><span className="overview-icon violet">R</span><div><small>등록된 로봇</small><strong>{robots.length}<em>대</em></strong><p><i className="online-dot" /> {robotAvailable}대 사용 가능</p></div></button>
      <button onClick={() => onNavigate('training')}><span className="overview-icon orange">◴</span><div><small>진행 중인 학습</small><strong>{runningTrainings.length}<em>건</em></strong><p>평균 진행률 {averageProgress}%</p></div></button>
      <button onClick={() => onNavigate('work')}><span className="overview-icon green">✓</span><div><small>오늘 완료한 작업</small><strong>12<em>건</em></strong><p>성공률 96%</p></div></button>
    </div>
    <div className="dashboard-grid">
      <section className="card dashboard-section"><div className="dashboard-title"><div><h2>로봇 상태</h2><p>최근 연결 상태를 기준으로 표시해요.</p></div><button className="text-button" onClick={() => onNavigate('devices')}>전체 보기 →</button></div><div className="compact-robot-list">{robots.map(robot => <button key={robot.id} onClick={() => onSelectRobot(robot.id)}><span className="robot-tile">R</span><div><strong>{robot.name}</strong><small>{robot.id} · {robot.location}</small></div><span className={`status-pill status-${robot.status.replace(' ', '-')}`}><i />{robot.status}</span><em>›</em></button>)}</div></section>
      <section className="card dashboard-section"><div className="dashboard-title"><div><h2>최근 학습</h2><p>진행 상태와 문제가 발생한 항목이에요.</p></div><button className="text-button" onClick={() => onNavigate('training')}>전체 보기 →</button></div><div className="compact-training-list">{trainings.map(item => <button key={item.name} onClick={() => onNavigate('training')}><div><strong>{item.name}</strong><small>{item.robot} · {item.stage}</small></div><span className={`training-badge ${item.status === '실패' ? 'failed' : item.status === '완료' ? 'complete' : ''}`}>{item.status}</span><div className="tiny-progress"><i style={{width: `${item.progress}%`}} /></div><em>{item.progress}%</em></button>)}</div></section>
    </div>
  </>;
}

function DevicesView({ onSelectRobot, onToast }: { onSelectRobot: (id: string) => void; onToast: (message: string) => void }) {
  const [tab, setTab] = useState<'robot' | 'umi'>('robot');
  const [registerOpen, setRegisterOpen] = useState(false);
  const { robots, umis: umi } = useAppStore();
  return <><div className="page-heading"><div><p className="eyebrow">장치 관리</p><h1>UMI와 로봇</h1><p>데이터 수집 장치와 작업 로봇의 연결 상태를 확인하세요.</p></div><button className="primary-button" onClick={() => setRegisterOpen(true)}>＋ 장치 등록</button></div><div className="device-tabs"><button className={tab === 'robot' ? 'active' : ''} onClick={() => setTab('robot')}>로봇 <span>{robots.length}</span></button><button className={tab === 'umi' ? 'active' : ''} onClick={() => setTab('umi')}>UMI <span>{umi.length}</span></button></div>
    {tab === 'robot' ? <div className="device-grid">{robots.map(robot => <button className="card device-card" key={robot.id} onClick={() => onSelectRobot(robot.id)}><div className="device-card-head"><span className="device-visual">R</span><span className={`status-pill status-${robot.status.replace(' ', '-')}`}><i />{robot.status}</span></div><h2>{robot.name}</h2><p>{robot.id} · {robot.location}</p><dl><div><dt>현재 모델</dt><dd>{robot.status === '오프라인' ? '확인 불가' : 'pick-block-v3'}</dd></div><div><dt>최근 작업</dt><dd>{robot.status === '작업 중' ? '부품 이동 중' : '오늘 10:24'}</dd></div></dl><span className="detail-link">상세 정보 보기 →</span></button>)}</div> : <div className="device-grid">{umi.map(item => <article className="card device-card" key={item.id}><div className="device-card-head"><span className="device-visual umi">U</span><span className={`status-pill status-${item.status}`}><i />{item.status}</span></div><h2>{item.id}</h2><p>{item.place}</p><dl><div><dt>최근 수집</dt><dd>{item.collected}</dd></div><div><dt>보유 데이터</dt><dd>{item.data}개</dd></div></dl><button className="secondary-button">수집 데이터 보기</button></article>)}</div>}
    {registerOpen && <DeviceRegisterModal onClose={() => setRegisterOpen(false)} onRegistered={(type, label) => { setRegisterOpen(false); setTab(type); onToast(`‘${label}’ 장치를 등록했어요.`); }} />}
  </>;
}

function DeviceRegisterModal({ onClose, onRegistered }: { onClose: () => void; onRegistered: (type: DeviceType, label: string) => void }) {
  const { addDevice } = useAppStore();
  const [type, setType] = useState<DeviceType>('robot');
  const [name, setName] = useState('');
  const [deviceId, setDeviceId] = useState('');
  const [location, setLocation] = useState('');
  const [error, setError] = useState('');
  const [saving, setSaving] = useState(false);

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    if (!name.trim() || !deviceId.trim() || !location.trim()) {
      setError('이름, ID, 설치 위치를 모두 입력해 주세요.');
      return;
    }
    setError('');
    setSaving(true);
    try {
      await addDevice({ type, id: deviceId, name, location });
      onRegistered(type, type === 'robot' ? name.trim() : deviceId.trim());
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : '등록에 실패했어요. 잠시 후 다시 시도해 주세요.');
      setSaving(false);
    }
  };

  return <div className="modal-backdrop" onMouseDown={onClose}>
    <form className="register-modal" role="dialog" aria-modal="true" aria-label="장치 등록" onMouseDown={(event) => event.stopPropagation()} onSubmit={submit}>
      <div className="modal-heading"><div><h2>새 장치 등록</h2><p>작업 로봇 또는 UMI 핸들을 플랫폼에 연결하세요.</p></div><button type="button" className="close-button" onClick={onClose} aria-label="닫기">×</button></div>
      <div className="form-grid">
        <label>장치 종류<select value={type} disabled={saving} onChange={(event) => setType(event.target.value as DeviceType)}><option value="robot">로봇</option><option value="umi">UMI</option></select><small>등록 후 이 탭에서 바로 확인할 수 있어요.</small></label>
        <label>장치 이름<input value={name} disabled={saving} onChange={(event) => setName(event.target.value)} placeholder={type === 'robot' ? '예: 5번 피킹 로봇' : '예: 실습실 C 핸들'} /></label>
        <label>장치 ID<input value={deviceId} disabled={saving} onChange={(event) => setDeviceId(event.target.value)} placeholder={type === 'robot' ? '예: RB-30' : '예: UMI-030'} /><small>중복되지 않는 ID를 입력해 주세요.</small></label>
        <label>설치 위치<input value={location} disabled={saving} onChange={(event) => setLocation(event.target.value)} placeholder="예: 물류 창고 2층" /></label>
      </div>
      {error && <p className="form-error" role="alert">{error}</p>}
      <div className="card-actions"><button type="button" className="secondary-button" disabled={saving} onClick={onClose}>취소</button><button className="primary-button" disabled={saving}>{saving ? '등록 중...' : '장치 등록'}</button></div>
    </form>
  </div>;
}

function RobotDetailView({ robotId, onBack, onNavigate }: { robotId: string; onBack: () => void; onNavigate: (view: View) => void }) {
  const { robots } = useAppStore();
  const robot = robots.find(item => item.id === robotId) ?? robots[0];
  return <><button className="back-link" onClick={onBack}>← 로봇 목록</button><div className="robot-detail-hero card"><div className="device-visual large">R</div><div><p className="eyebrow">{robot.id}</p><h1>{robot.name}</h1><p>{robot.location} · 마지막 통신 방금 전</p></div><span className={`status-pill large status-${robot.status.replace(' ', '-')}`}><i />{robot.status}</span><div className="detail-actions"><button className="secondary-button">설정</button><button className="primary-button" disabled={robot.status !== '온라인'} onClick={() => onNavigate('work')}>작업 만들기</button></div></div><div className="detail-metrics"><article className="card"><small>현재 모델</small><strong>pick-block-v3</strong><p>2026. 08. 28 배포</p></article><article className="card"><small>오늘 작업</small><strong>8건</strong><p>성공 7 · 실패 1</p></article><article className="card"><small>가동 시간</small><strong>6시간 24분</strong><p>최근 24시간 기준</p></article><article className="card"><small>오류 알림</small><strong>{robot.status === '오류' ? '2건' : '0건'}</strong><p>{robot.status === '오류' ? '확인이 필요해요' : '정상 작동 중'}</p></article></div><div className="dashboard-grid"><section className="card dashboard-section"><div className="dashboard-title"><div><h2>최근 작업</h2><p>이 로봇에서 실행된 작업 기록입니다.</p></div></div><div className="history-list"><p><strong>빨간 블록 정리</strong><span className="training-badge complete">성공</span><small>오늘 10:24 · 1분 18초</small></p><p><strong>부품 상자 이동</strong><span className="training-badge complete">성공</span><small>오늘 09:47 · 2분 03초</small></p><p><strong>컵 분류하기</strong><span className="training-badge failed">실패</span><small>어제 17:31 · 연결 끊김</small></p></div></section><section className="card dashboard-section"><div className="dashboard-title"><div><h2>배포된 모델</h2><p>현재 로봇에서 사용할 수 있는 Skill입니다.</p></div></div><div className="model-list"><p><span>V3</span><strong>pick-block-v3<small>현재 사용 중</small></strong><em>활성</em></p><p><span>V2</span><strong>move-box-v2<small>2026. 08. 25</small></strong><button className="text-button">적용</button></p></div></section></div></>;
}

function TrainingStatusView({ onNavigate }: { onNavigate: (view: View) => void }) {
  const { trainings } = useAppStore();
  const [statusFilter, setStatusFilter] = useState<'all' | TrainingStatus>('all');
  const countOf = (status: TrainingStatus) => trainings.filter((item) => item.status === status).length;
  const filtered = statusFilter === 'all' ? trainings : trainings.filter((item) => item.status === statusFilter);
  return <><div className="page-heading"><div><p className="eyebrow">학습 모니터링</p><h1>학습 현황</h1><p>진행 중이거나 완료된 모델 학습을 확인하세요.</p></div><button className="primary-button" onClick={() => onNavigate('learn')}>＋ 새 학습 시작</button></div><div className="training-summary"><span><small>전체</small><strong>{trainings.length}</strong></span><span><small>진행 중</small><strong>{countOf('진행 중')}</strong></span><span><small>완료</small><strong>{countOf('완료')}</strong></span><span><small>확인 필요</small><strong>{countOf('실패')}</strong></span></div><section className="card training-table"><div className="training-table-head"><div><h2>학습 작업</h2><p>최근 시작된 순서로 표시됩니다.</p></div><select value={statusFilter} onChange={(e) => setStatusFilter(e.target.value as 'all' | TrainingStatus)} aria-label="학습 상태 필터"><option value="all">전체 상태</option><option value="진행 중">진행 중</option><option value="완료">완료</option><option value="실패">실패</option></select></div>{filtered.map(item => <article key={item.name}><div className="training-main"><span className="learning-symbol">✦</span><div><strong>{item.name}</strong><small>{item.robot} · {item.started}</small></div></div><div><small>현재 단계</small><strong>{item.stage}</strong></div><div className="training-progress"><div><i style={{width: `${item.progress}%`}} /></div><strong>{item.progress}%</strong></div><span className={`training-badge ${item.status === '실패' ? 'failed' : item.status === '완료' ? 'complete' : ''}`}>{item.status}</span><button className="more-button">•••</button></article>)}{!filtered.length && <p className="table-empty">‘{statusFilter}’ 상태의 학습이 없어요. 다른 상태를 선택해 보세요.</p>}</section></>;
}

function LearnView({ onToast, onGoWork }: { onToast: (message: string) => void; onGoWork: () => void }) {
  const { robots, datasets } = useAppStore();
  const [selected, setSelected] = useState<number[]>([1, 2]);
  const [step, setStep] = useState(1);
  const [skillName, setSkillName] = useState('빨간 블록 옮기기');
  const [robot, setRobot] = useState('RB-07');
  const [progress, setProgress] = useState(12);
  const [video, setVideo] = useState<Dataset | null>(null);

  useEffect(() => {
    if (step !== 3 || progress >= 100) return;
    const timer = window.setInterval(() => setProgress((value) => Math.min(100, value + 4)), 650);
    return () => window.clearInterval(timer);
  }, [step, progress]);

  useEffect(() => {
    if (step === 3 && progress === 100) {
      const timer = window.setTimeout(() => setStep(4), 500);
      return () => window.clearTimeout(timer);
    }
  }, [progress, step]);

  const toggle = (id: number) => setSelected((current) => current.includes(id) ? current.filter((item) => item !== id) : [...current, id]);
  const allSelected = selected.length === datasets.length;
  const stage = progress < 35 ? '데이터 전처리' : progress < 75 ? '동작 패턴 학습' : '모델 검증';

  return (
    <>
      <div className="page-heading">
        <div><p className="eyebrow">새로운 동작 학습</p><h1>로봇에게 새로운 동작을 가르쳐 볼까요?</h1><p>UMI로 수집한 데이터를 선택하면 나머지는 차근차근 안내해 드릴게요.</p></div>
        <span className="server-status"><i /> 서버 연결됨</span>
      </div>
      <ol className="steps" aria-label="학습 단계">
        {['데이터 선택', '학습 설정', '학습 진행'].map((label, index) => (
          <li key={label} className={step === index + 1 || (step === 4 && index === 2) ? 'current' : step > index + 1 ? 'done' : ''}>
            <span>{step > index + 1 ? '✓' : index + 1}</span><div><strong>{label}</strong><small>{['학습할 영상을 골라요', '이름과 로봇을 정해요', '진행 상황을 확인해요'][index]}</small></div>
          </li>
        ))}
      </ol>

      {step === 1 && (
        <div className="workspace-grid">
          <div className="main-column">
            <section className="card handle-card">
              <div className="section-title"><span className="number">1</span><div><h2>UMI 핸들을 선택해 주세요</h2><p>목록에서 고르거나 핸들 번호를 직접 입력할 수 있어요.</p></div></div>
              <div className="handle-controls">
                <label>등록된 핸들<select defaultValue="UMI-024"><option>UMI-024 · 실습실 A</option><option>UMI-018 · 실습실 B</option></select></label>
                <span className="divider">또는</span>
                <label>핸들 번호 직접 입력<input placeholder="예: UMI-024" /></label>
                <button className="primary-button" onClick={() => onToast('최신 데이터를 불러왔어요.')}>데이터 조회</button>
              </div>
            </section>
            <section className="card data-card">
              <div className="data-heading"><div className="section-title compact"><span className="number">2</span><div><h2>학습할 데이터를 선택해 주세요</h2><p>총 {datasets.length}개의 수집 데이터를 찾았어요.</p></div></div><span className="updated">마지막 조회 10:48</span></div>
              <div className="table-wrap">
                <table><thead><tr><th><input type="checkbox" checked={allSelected} onChange={() => setSelected(allSelected ? [] : datasets.map((item) => item.id))} aria-label="전체 선택" /></th><th>영상</th><th>수집 일시</th><th>촬영 시간</th><th>파일 크기</th><th /></tr></thead>
                  <tbody>{datasets.map((item) => (
                    <tr key={item.id} className={selected.includes(item.id) ? 'selected' : ''} onDoubleClick={() => setVideo(item)}>
                      <td><input type="checkbox" checked={selected.includes(item.id)} onChange={() => toggle(item.id)} aria-label={`${item.date} 데이터 선택`} /></td>
                      <td><button className={`thumbnail ${item.tone}`} onClick={() => setVideo(item)} aria-label="영상 보기"><span>▶</span></button></td>
                      <td><strong>{item.date}</strong><small>더블클릭하여 영상 보기</small></td><td>{item.duration}</td><td>{item.size}</td><td><button className="more-button" aria-label="데이터 메뉴">•••</button></td>
                    </tr>
                  ))}</tbody>
                </table>
              </div>
              <div className="selection-bar"><p><strong>{selected.length}개</strong>의 데이터를 선택했어요</p><button className="primary-button" disabled={!selected.length} onClick={() => setStep(2)}>학습 설정으로 <span>→</span></button></div>
            </section>
          </div>
          <aside className="guide-card"><span className="guide-icon">i</span><h3>좋은 학습 데이터를 고르는 팁</h3><ul><li>동작이 처음부터 끝까지 담긴 영상</li><li>로봇이 움직일 공간이 잘 보이는 영상</li><li>비슷한 동작을 여러 번 촬영한 데이터</li></ul><p>데이터가 많고 다양할수록 로봇이 동작을 더 잘 배울 수 있어요.</p></aside>
        </div>
      )}

      {step === 2 && (
        <div className="center-panel">
          <section className="card setup-card">
            <div className="section-title"><span className="number">2</span><div><h2>학습 정보를 확인해 주세요</h2><p>알아보기 쉬운 이름을 붙이고 학습에 사용할 로봇을 선택하세요.</p></div></div>
            <div className="selected-summary"><span className="summary-icon">▦</span><div><strong>선택한 데이터 {selected.length}개</strong><small>총 촬영 시간 4분 2초 · 약 878 MB</small></div><button className="text-button" onClick={() => setStep(1)}>다시 선택</button></div>
            <div className="form-grid"><label>Skill 이름<input value={skillName} onChange={(e) => setSkillName(e.target.value)} placeholder="예: 빨간 블록 옮기기" /><small>작업 탭의 블록 이름으로 사용돼요.</small></label><label>학습에 사용할 로봇<select value={robot} onChange={(e) => setRobot(e.target.value)}>{robots.filter((item) => item.status !== '오프라인').map((item) => <option key={item.id} value={item.id}>{item.name} · {item.status}</option>)}</select><small>현재 연결 가능한 로봇만 표시돼요.</small></label></div>
            <div className="robot-check"><span className="status-dot online" /><div><strong>{robots.find((item) => item.id === robot)?.name}</strong><small>온라인 · 학습 준비가 완료됐어요.</small></div><span className="ready-badge">사용 가능</span></div>
            <div className="card-actions"><button className="secondary-button" onClick={() => setStep(1)}>← 이전</button><button className="primary-button" disabled={!skillName} onClick={() => { setProgress(12); setStep(3); }}>학습 시작</button></div>
          </section>
        </div>
      )}

      {step === 3 && (
        <div className="progress-layout">
          <section className="card progress-card">
            <div className="learning-orbit"><span>✦</span></div><p className="eyebrow">학습 진행 중</p><h2>{skillName}</h2><p>창을 닫아도 학습은 계속 진행돼요.</p>
            <div className="progress-number">{progress}<small>%</small></div><div className="progress-track"><i style={{ width: `${progress}%` }} /></div>
            <div className="progress-meta"><div><small>현재 단계</small><strong>{stage}</strong></div><div><small>예상 남은 시간</small><strong>약 {Math.max(1, Math.ceil((100 - progress) / 12))}분</strong></div><div><small>대상 로봇</small><strong>{robot}</strong></div></div>
          </section>
          <section className="card log-card"><div className="log-heading"><div><h3>학습 로그</h3><p>서버에서 전달되는 진행 정보를 보여드려요.</p></div><span>실시간</span></div><div className="log-list"><p><time>10:52:08</time><span className="log-ok">완료</span>데이터 검증 완료 ({selected.length}개)</p><p><time>10:52:14</time><span className="log-ok">완료</span>영상 프레임 추출 완료</p><p><time>10:53:02</time><span className="log-run">진행</span>{stage} 진행 중...</p></div></section>
        </div>
      )}

      {step === 4 && (
        <div className="center-panel"><section className="card complete-card"><span className="complete-icon">✓</span><p className="eyebrow">학습 완료</p><h2>새로운 동작을 모두 배웠어요!</h2><p>이제 작업 탭에서 <strong>{skillName}</strong> 블록을 사용할 수 있어요.</p><div className="complete-summary"><div><small>Skill 이름</small><strong>{skillName}</strong></div><div><small>학습 데이터</small><strong>{selected.length}개</strong></div><div><small>대상 로봇</small><strong>{robot}</strong></div><div><small>완료 시각</small><strong>오늘 11:01</strong></div></div><div className="complete-actions"><button className="secondary-button" onClick={() => { setStep(1); setSelected([]); }}>새 학습 시작</button><button className="primary-button" onClick={onGoWork}>작업에서 사용하기 →</button></div></section></div>
      )}

      {video && <div className="modal-backdrop" onMouseDown={() => setVideo(null)}><section className="video-modal" role="dialog" aria-modal="true" aria-label="수집 영상 재생" onMouseDown={(e) => e.stopPropagation()}><div className="modal-heading"><div><h2>수집 영상 미리보기</h2><p>{video.date} · {video.duration} · {video.size}</p></div><button className="close-button" onClick={() => setVideo(null)} aria-label="닫기">×</button></div><div className={`video-stage ${video.tone}`}><span className="big-play">▶</span><div className="video-controls"><span>▶</span><i><b /></i><time>00:37 / {video.duration}</time></div></div><p className="modal-note">서버에서 영상을 모두 불러왔어요.</p></section></div>}
    </>
  );
}

function WorkView({ onToast }: { onToast: (message: string) => void }) {
  const { robots, savedWorks } = useAppStore();
  const [robotId, setRobotId] = useState('RB-07');
  const [blocks, setBlocks] = useState<WorkflowBlock[]>([
    { id: 1, kind: 'umi', title: '빨간 블록 집기' },
    { id: 2, kind: 'motor', title: '모터 움직이기', motor: 'J2', angle: 35 },
    { id: 3, kind: 'repeat', title: '반복하기', repeat: 2, children: [{ id: 4, kind: 'vlm', title: '물체를 상자로 이동' }] },
  ]);
  const [runningIndex, setRunningIndex] = useState(-1);
  const [workName, setWorkName] = useState('빨간 블록 정리 작업');

  const selectedRobot = robots.find((robot) => robot.id === robotId)!;
  const running = runningIndex >= 0;

  useEffect(() => {
    if (!running) return;
    const timer = window.setTimeout(() => {
      if (runningIndex >= blocks.length - 1) { setRunningIndex(-1); onToast('작업을 안전하게 완료했어요.'); }
      else setRunningIndex((index) => index + 1);
    }, 1200);
    return () => window.clearTimeout(timer);
  }, [running, runningIndex, blocks.length, onToast]);

  const addBlock = (kind: BlockKind, repeatId?: number) => {
    const block = makeBlock(kind);
    if (repeatId) {
      if (kind === 'repeat') return;
      setBlocks((items) => items.map((item) => item.id === repeatId ? { ...item, children: [...(item.children ?? []), block] } : item));
    } else setBlocks((items) => [...items, block]);
  };

  const updateBlock = (id: number, patch: Partial<WorkflowBlock>) => setBlocks((items) => items.map((item) => item.id === id ? { ...item, ...patch } : item));
  const removeBlock = (id: number) => setBlocks((items) => items.filter((item) => item.id !== id));
  const moveBlock = (index: number, direction: -1 | 1) => {
    const target = index + direction;
    if (target < 0 || target >= blocks.length) return;
    setBlocks((items) => { const next = [...items]; [next[index], next[target]] = [next[target], next[index]]; return next; });
  };
  const dropKind = (event: React.DragEvent, repeatId?: number) => {
    event.preventDefault(); event.stopPropagation();
    const kind = event.dataTransfer.getData('block-kind') as BlockKind;
    if (kind) addBlock(kind, repeatId);
  };

  return (
    <>
      <div className="page-heading work-heading"><div><p className="eyebrow">블록 작업 만들기</p><h1>로봇이 할 일을 순서대로 놓아주세요</h1><p>왼쪽 블록을 끌어다 놓거나 클릭하면 작업에 추가돼요.</p></div><div className="work-tools"><label>실행할 로봇<select value={robotId} disabled={running} onChange={(e) => setRobotId(e.target.value)}>{robots.slice(0, 3).map((robot) => <option key={robot.id} value={robot.id}>{robot.name} · {robot.status}</option>)}</select></label><span className={`robot-status status-${selectedRobot.status.replace(' ', '-')}`}><i />{selectedRobot.status}</span></div></div>
      <div className="work-namebar"><label>작업 이름<input value={workName} disabled={running} onChange={(e) => setWorkName(e.target.value)} /></label><label>저장된 작업<select disabled={running} defaultValue=""><option value="" disabled>작업 불러오기</option>{savedWorks.map((work) => <option key={work.name}>{work.name}</option>)}</select></label><button className="secondary-button" disabled={running} onClick={() => onToast(`‘${workName}’ 작업을 저장했어요.`)}>저장</button></div>
      <div className="block-workspace">
        <aside className="block-library"><div className="library-heading"><h2>작업 블록</h2><p>필요한 블록을 작업 영역에 추가하세요.</p></div><p className="block-category">학습된 동작</p>{blockCatalog.slice(0, 2).map((item) => <button key={item.kind} className={`library-block ${item.kind}`} draggable={!running} onDragStart={(e) => e.dataTransfer.setData('block-kind', item.kind)} onClick={() => !running && addBlock(item.kind)}><span>{item.mark}</span><div><strong>{item.title}</strong><small>{item.description}</small></div><em>＋</em></button>)}<p className="block-category">직접 제어</p>{blockCatalog.slice(2, 3).map((item) => <button key={item.kind} className={`library-block ${item.kind}`} draggable={!running} onDragStart={(e) => e.dataTransfer.setData('block-kind', item.kind)} onClick={() => !running && addBlock(item.kind)}><span>{item.mark}</span><div><strong>{item.title}</strong><small>{item.description}</small></div><em>＋</em></button>)}<p className="block-category">흐름 제어</p>{blockCatalog.slice(3).map((item) => <button key={item.kind} className={`library-block ${item.kind}`} draggable={!running} onDragStart={(e) => e.dataTransfer.setData('block-kind', item.kind)} onClick={() => !running && addBlock(item.kind)}><span>{item.mark}</span><div><strong>{item.title}</strong><small>{item.description}</small></div><em>＋</em></button>)}</aside>

        <section className="canvas" onDragOver={(e) => e.preventDefault()} onDrop={(e) => dropKind(e)}>
          <div className="canvas-head"><div><h2>작업 흐름</h2><p>위에서 아래 순서로 실행돼요.</p></div><span>{blocks.length}개 블록</span></div>
          <div className="flow"><div className="start-block"><span>▶</span><div><strong>작업 시작</strong><small>{selectedRobot.name}에서 실행</small></div></div><div className="connector" />
            {blocks.map((block, index) => <div className="flow-unit" key={block.id}><WorkflowBlockCard block={block} active={runningIndex === index} disabled={running} onUpdate={updateBlock} onRemove={removeBlock} onMove={(direction) => moveBlock(index, direction)} onDropChild={(event) => dropKind(event, block.id)} /><div className="connector" /></div>)}
            <div className="drop-zone"><span>＋</span>이곳에 작업 블록을 놓아주세요</div>
          </div>
        </section>

        <aside className="run-panel"><div><p className="eyebrow">실행 준비</p><h2>{workName}</h2></div><div className="run-robot"><span className="robot-illustration">R</span><div><strong>{selectedRobot.name}</strong><small>{selectedRobot.location}</small></div><span className={`status-dot ${selectedRobot.status === '온라인' ? 'online' : 'busy'}`} /></div><dl><div><dt>작업 블록</dt><dd>{blocks.length}개</dd></div><div><dt>예상 시간</dt><dd>약 1분 20초</dd></div></dl>{running ? <><div className="mini-progress"><i style={{ width: `${((runningIndex + 1) / Math.max(1, blocks.length)) * 100}%` }} /></div><p className="running-copy"><span /> {runningIndex + 1}번째 블록을 실행하고 있어요</p><button className="danger-button" onClick={() => setRunningIndex(-1)}>■ 작업 중지</button></> : <><div className="safety-note"><span>!</span><p><strong>실행 전 확인해 주세요</strong>로봇 주변에 사람이나 장애물이 없는지 확인하세요.</p></div><button className="primary-button run-button" disabled={!blocks.length || selectedRobot.status !== '온라인'} onClick={() => setRunningIndex(0)}>▶ 작업 시작</button>{selectedRobot.status !== '온라인' && <small className="disabled-reason">온라인 상태의 로봇에서만 실행할 수 있어요.</small>}</>}</aside>
      </div>
    </>
  );
}

function WorkflowBlockCard({ block, active, disabled, onUpdate, onRemove, onMove, onDropChild }: { block: WorkflowBlock; active: boolean; disabled: boolean; onUpdate: (id: number, patch: Partial<WorkflowBlock>) => void; onRemove: (id: number) => void; onMove: (direction: -1 | 1) => void; onDropChild: (event: React.DragEvent) => void }) {
  const catalog = blockCatalog.find((item) => item.kind === block.kind)!;
  const range = motorRanges[block.motor ?? 'J1'];
  return <div className={`workflow-block ${block.kind} ${active ? 'executing' : ''}`}><div className="block-top"><span className="block-mark">{catalog.mark}</span><div><strong>{block.title}</strong><small>{catalog.description}</small></div>{active && <em className="executing-badge">실행 중</em>}<div className="block-actions"><button disabled={disabled} onClick={() => onMove(-1)} aria-label="위로 이동">↑</button><button disabled={disabled} onClick={() => onMove(1)} aria-label="아래로 이동">↓</button><button disabled={disabled} onClick={() => onRemove(block.id)} aria-label="삭제">×</button></div></div>
    {block.kind === 'motor' && <div className="motor-config"><label>모터<select value={block.motor} disabled={disabled} onChange={(e) => onUpdate(block.id, { motor: e.target.value, angle: 0 })}>{Object.keys(motorRanges).map((motor) => <option key={motor}>{motor}</option>)}</select></label><label className="angle-field">각도 <span>{block.angle}°</span><input type="range" min={range[0]} max={range[1]} value={block.angle} disabled={disabled} onChange={(e) => onUpdate(block.id, { angle: Number(e.target.value) })} /><small>{range[0]}° <b>{range[1]}°</b></small></label></div>}
    {block.kind === 'repeat' && <div className="repeat-config"><label>반복 횟수<input type="number" min="1" max="20" value={block.repeat} disabled={disabled} onChange={(e) => onUpdate(block.id, { repeat: Math.max(1, Math.min(20, Number(e.target.value))) })} /></label><div className="repeat-drop" onDragOver={(e) => e.preventDefault()} onDrop={onDropChild}>{block.children?.length ? block.children.map((child) => <div className={`nested-block ${child.kind}`} key={child.id}><span>{blockCatalog.find((item) => item.kind === child.kind)?.mark}</span><strong>{child.title}</strong></div>) : <p>반복할 블록을 이 안에 놓아주세요</p>}</div></div>}
  </div>;
}

function AdminView({ onToast }: { onToast: (message: string) => void }) {
  const { robots, users, permissions: storedPermissions } = useAppStore();
  const [userId, setUserId] = useState('user01');
  const [permissions, setPermissions] = useState<Record<string, string[]>>(storedPermissions);
  const allowed = permissions[userId] ?? [];
  const user = users.find((item) => item.id === userId)!;
  const toggleRobot = (robotId: string) => setPermissions((current) => ({ ...current, [userId]: allowed.includes(robotId) ? allowed.filter((id) => id !== robotId) : [...allowed, robotId] }));

  return <><div className="page-heading"><div><p className="eyebrow">관리자 전용</p><h1>사용자별 로봇 접근 권한</h1><p>각 사용자가 작업 탭에서 선택할 수 있는 로봇을 관리하세요.</p></div><span className="admin-badge">관리자</span></div><div className="admin-layout"><aside className="card user-list"><div className="admin-section-heading"><h2>일반 사용자</h2><span>{users.length}명</span></div><input className="search-input" placeholder="이름 또는 아이디 검색" />{users.map((item) => <button key={item.id} className={userId === item.id ? 'selected' : ''} onClick={() => setUserId(item.id)}><span className="avatar">{item.name[0]}</span><div><strong>{item.name}</strong><small>{item.id} · {item.team}</small></div><em>›</em></button>)}</aside><section className="card permission-panel"><div className="permission-heading"><div><span className="avatar large">{user.name[0]}</span><div><p className="eyebrow">선택한 사용자</p><h2>{user.name} <small>{user.id}</small></h2><p>{user.team} · 현재 {allowed.length}대 접근 가능</p></div></div><button className="primary-button" onClick={() => onToast(`${user.name}님의 로봇 권한을 저장했어요.`)}>변경 사항 저장</button></div><div className="permission-note"><span>i</span>권한을 해제해도 서버에 등록된 로봇은 삭제되지 않아요.</div><div className="robot-list-heading"><div><h3>서버에 등록된 로봇</h3><p>사용자가 작업 탭에서 선택할 로봇을 체크하세요.</p></div><span>{allowed.length}/{robots.length}대 허용</span></div><div className="robot-permission-list">{robots.map((robot) => <label key={robot.id} className={allowed.includes(robot.id) ? 'allowed' : ''}><input type="checkbox" checked={allowed.includes(robot.id)} onChange={() => toggleRobot(robot.id)} /><span className="robot-tile">R</span><div><strong>{robot.name}<small>{robot.id}</small></strong><p>{robot.location}</p></div><span className={`status-pill status-${robot.status.replace(' ', '-')}`}><i />{robot.status}</span><em>{allowed.includes(robot.id) ? '접근 허용' : '접근 안 함'}</em></label>)}</div></section></div></>;
}
