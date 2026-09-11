'use client';

import { FormEvent, useCallback, useEffect, useRef, useState } from 'react';
import type { AuthenticatedUser, BlockKind, DashboardData, DashboardRobotStatus, Dataset, DeviceType, EpisodePreviewSession, LearnedSkill, RobotDetail, RobotExecution, RobotStatus, SavedWork, Training, TrainingRequirements, TrainingSkill, TrainingStatus, View, WorkflowBlock } from '@/types';
import { blockCatalog, motorRanges } from '@/mocks';
import { useTaskDeployment } from '@/hooks/useTaskDeployment';
import { AppStoreProvider, useAppStore } from '@/hooks/useAppStore';
import { createEpisodePreviewSession, createTrainingJob, fetchDashboard, fetchRobotDetail, fetchRobotExecutions, fetchTrainingDetail, fetchTrainingRequirements, fetchTrainingSkills, login as requestLogin, logout as clearAuthentication } from '@/services';
import Tutorial from './Tutorial';

let nextBlockId = 20;

function makeBlock(kind: BlockKind): WorkflowBlock {
  const item = blockCatalog.find((block) => block.kind === kind)!;
  return {
    id: nextBlockId++, kind, title: item.title,
    ...(kind === 'motor' ? { motor: 'J1', angle: 45 } : {}),
    ...(kind === 'repeat' ? { repeat: 3, children: [] } : {}),
  };
}

function makeSkillBlock(skill: LearnedSkill): WorkflowBlock {
  return { id: nextBlockId++, kind: skill.kind, title: skill.name, skillId: skill.id };
}

const isSkillBlock = (block: WorkflowBlock) => block.kind === 'umi' || block.kind === 'vlm';

function flattenWorkflowBlocks(blocks: WorkflowBlock[]): WorkflowBlock[] {
  return blocks.flatMap((block) => [block, ...flattenWorkflowBlocks(block.children ?? [])]);
}

function estimateWorkflowSeconds(blocks: WorkflowBlock[]): number {
  return blocks.reduce((total, block) => {
    if (block.kind === 'repeat') {
      return total + estimateWorkflowSeconds(block.children ?? []) * (block.repeat ?? 1);
    }
    if (block.kind === 'umi') return total + 25;
    if (block.kind === 'vlm') return total + 35;
    return total + 12;
  }, 0);
}

function formatWorkDuration(seconds: number): string {
  const minutes = Math.floor(seconds / 60);
  const rest = seconds % 60;
  if (!minutes) return `약 ${rest}초`;
  return rest ? `약 ${minutes}분 ${rest}초` : `약 ${minutes}분`;
}

function formatUpdatedAt(value: string): string {
  return new Intl.DateTimeFormat('ko-KR', { year: 'numeric', month: '2-digit', day: '2-digit' }).format(new Date(value));
}

function getWorkflowIssues(work: SavedWork): string[] {
  const issues: string[] = [];
  const blocks = flattenWorkflowBlocks(work.blocks);
  if (!work.name.trim()) issues.push('작업명이 비어 있어요.');
  if (!work.blocks.length) issues.push('실행할 블록이 없어요.');
  blocks.forEach((block) => {
    if (isSkillBlock(block) && !block.skillId) issues.push(`${block.title}에 학습 버전 정보가 없어요.`);
    if (block.kind === 'motor') {
      const range = motorRanges[block.motor ?? ''];
      if (!range || block.angle === undefined || block.angle < range[0] || block.angle > range[1]) issues.push(`${block.title} 설정을 확인해 주세요.`);
    }
    if (block.kind === 'repeat' && ((block.repeat ?? 0) < 1 || (block.repeat ?? 0) > 100 || !block.children?.length)) issues.push('반복 블록에는 실행할 동작과 1~100의 반복 횟수가 필요해요.');
  });
  return issues;
}

function workflowContentEquals(left: WorkflowBlock[], right: WorkflowBlock[]): boolean {
  if (left.length !== right.length) return false;
  return left.every((block, index) => {
    const other = right[index];
    return block.kind === other.kind
      && block.title === other.title
      && block.skillId === other.skillId
      && block.motor === other.motor
      && block.angle === other.angle
      && block.repeat === other.repeat
      && workflowContentEquals(block.children ?? [], other.children ?? []);
  });
}

export default function Home() {
  return (
    <AppStoreProvider>
      <HomeInner />
    </AppStoreProvider>
  );
}

function HomeInner() {
  const { loading, loadError, refresh, learningDraft, resetLearningDraft } = useAppStore();
  const [entered, setEntered] = useState(false);
  const [authenticated, setAuthenticated] = useState(false);
  const [currentUserId, setCurrentUserId] = useState('admin01');
  const [currentUser, setCurrentUser] = useState<AuthenticatedUser | null>(null);
  const [view, setView] = useState<View>('dashboard');
  const [deviceTab, setDeviceTab] = useState<DeviceType>('umi');
  const [selectedRobotId, setSelectedRobotId] = useState('RB-07');
  const [editingWorkId, setEditingWorkId] = useState<string | null>(null);
  const [workEditorRevision, setWorkEditorRevision] = useState(0);
  const [suggestedSkillName, setSuggestedSkillName] = useState<string | null>(null);
  useEffect(() => { window.scrollTo({ top: 0, behavior: 'instant' }); }, [view, entered]);
  const [toast, setToast] = useState('');
  const [toastTone, setToastTone] = useState<'success' | 'error'>('success');

  const showToast = useCallback((message: string, tone: 'success' | 'error' = 'success') => {
    setToast(message);
    setToastTone(tone);
  }, []);

  /* eslint-disable react-hooks/set-state-in-effect */
  useEffect(() => {
    if (loadError) showToast('데이터를 불러오지 못했어요.', 'error');
  }, [loadError, showToast]);
  /* eslint-enable react-hooks/set-state-in-effect */

  /* Figma 캡처 쿼리를 최초 진입 화면 상태에 한 번 반영한다. */
  /* eslint-disable react-hooks/set-state-in-effect */
  useEffect(() => {
    const captureView = new URLSearchParams(window.location.search).get('figmaView') as View | null;
    if (captureView && ['dashboard', 'data', 'library', 'devices', 'robot-detail', 'training', 'learn', 'work', 'admin'].includes(captureView)) {
      setView(captureView);
      setAuthenticated(true);
      setEntered(true);
    }
  }, []);
  /* eslint-enable react-hooks/set-state-in-effect */

  useEffect(() => {
    if (!toast) return;
    const timer = window.setTimeout(() => setToast(''), 2200);
    return () => window.clearTimeout(timer);
  }, [toast]);

  const enterView = (target: View) => { setView(target); setEntered(true); };
  const navigateTo = (target: View) => {
    if (view === 'learn' && target !== 'learn' && learningDraft.step < 5) {
      resetLearningDraft();
    }
    if (target === 'work') {
      setEditingWorkId(null);
      setWorkEditorRevision((revision) => revision + 1);
    }
    setView(target);
  };
  const editWork = (workId: string) => {
    setEditingWorkId(workId || null);
    setWorkEditorRevision((revision) => revision + 1);
    setView('work');
  };
  const login = (target: View, user?: AuthenticatedUser) => {
    if (user) {
      setCurrentUser(user);
      setCurrentUserId(user.name);
    } else if (!currentUser) return;
    setAuthenticated(true);
    enterView(target);
    void refresh();
  };
  const logout = () => {
    if (view === 'learn' && learningDraft.step < 5) resetLearningDraft();
    clearAuthentication();
    setCurrentUser(null);
    setAuthenticated(false);
    setEntered(false);
  };
  if (!entered) return <Landing authenticated={authenticated} onLogin={login} onLogout={logout} />;

  return (
    <main className="app-shell">
      <GlobalHeader variant="sidebar" authenticated={authenticated} userId={currentUserId} activeView={view} onHome={() => { if (view === 'learn' && learningDraft.step < 5) resetLearningDraft(); setEntered(false); }} onNavigate={navigateTo} onAuth={logout} />

      <section className="content">
        {loading && <div className="center-panel"><section className="card state-card"><span className="spinner" aria-hidden="true" /><h2>데이터를 불러오는 중이에요</h2><p>잠시만 기다려 주세요.</p></section></div>}
        {!loading && loadError && <div className="center-panel"><section className="card state-card"><span className="state-icon">!</span><h2>데이터를 불러오지 못했어요</h2><p>네트워크 상태를 확인한 뒤 다시 시도해 주세요.</p><button className="primary-button" onClick={() => void refresh()}>다시 시도</button></section></div>}
        {!loading && !loadError && <>
          {view === 'dashboard' && <DashboardView onNavigate={navigateTo} onOpenDevices={(tab) => { setDeviceTab(tab); setView('devices'); }} onSelectRobot={(id) => { setSelectedRobotId(id); setDeviceTab('robot'); setView('robot-detail'); }} />}
          {view === 'data' && <DataView />}
          {view === 'library' && <WorkflowLibraryView onEdit={editWork} onToast={showToast} />}
          {view === 'devices' && <DevicesView initialTab={deviceTab} onSelectRobot={(id) => { setSelectedRobotId(id); setDeviceTab('robot'); setView('robot-detail'); }} onToast={showToast} />}
          {view === 'robot-detail' && <RobotDetailView robotId={selectedRobotId} onBack={() => setView('devices')} onNavigate={navigateTo} onToast={showToast} />}
          {view === 'training' && <TrainingStatusView onUseResult={(name) => { setSuggestedSkillName(name); navigateTo('work'); }} onNavigate={navigateTo} onToast={showToast} />}
          {view === 'learn' && <LearnView onNavigate={navigateTo} onToast={showToast} />}
          {view === 'work' && <WorkView key={`${editingWorkId ?? 'new'}-${workEditorRevision}`} suggestedSkillName={suggestedSkillName} editingWorkId={editingWorkId} onNavigate={navigateTo} onToast={showToast} />}
          {view === 'admin' && <AdminView onToast={showToast} />}
        </>}
      </section>
      {toast && <div className={`toast${toastTone === 'error' ? ' error' : ''}`} role="status"><span>{toastTone === 'error' ? '!' : '✓'}</span>{toast}</div>}
    </main>
  );
}

function Landing({ authenticated, onLogin, onLogout }: { authenticated: boolean; onLogin: (target: View, user?: AuthenticatedUser) => void; onLogout: () => void }) {
  const [loginOpen, setLoginOpen] = useState(() => typeof window !== 'undefined' && new URLSearchParams(window.location.search).get('figmaView') === 'login');
  const [signupOpen, setSignupOpen] = useState(false);
  const [tutorialOpen, setTutorialOpen] = useState(false);
  const [pendingView, setPendingView] = useState<View>('dashboard');
  const [id, setId] = useState('admin01@ssafy.local');
  const [password, setPassword] = useState('12345678');
  const [loginPending, setLoginPending] = useState(false);
  const [showPassword, setShowPassword] = useState(false);
  const [loginError, setLoginError] = useState('');
  const [signupName, setSignupName] = useState('');
  const [signupId, setSignupId] = useState('');
  const [signupPassword, setSignupPassword] = useState('');
  const [signupPasswordConfirm, setSignupPasswordConfirm] = useState('');
  const [signupError, setSignupError] = useState('');
  const [signupComplete, setSignupComplete] = useState(false);
  const loginForm = useRef<HTMLFormElement>(null);
  const signupForm = useRef<HTMLFormElement>(null);
  useEffect(() => {
    if (!loginOpen && !signupOpen) return;
    const activeForm = signupOpen ? signupForm.current : loginForm.current;
    const previous = document.activeElement as HTMLElement | null;
    const overflow = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    activeForm?.querySelector('input')?.focus();
    const handle = (event: KeyboardEvent) => {
      if (event.key === 'Escape') { setLoginOpen(false); setSignupOpen(false); }
      if (event.key !== 'Tab') return;
      const fields = activeForm?.querySelectorAll<HTMLElement>('button:not(:disabled), input');
      if (!fields?.length) return;
      if (event.shiftKey && document.activeElement === fields[0]) { event.preventDefault(); fields[fields.length-1].focus(); }
      if (!event.shiftKey && document.activeElement === fields[fields.length-1]) { event.preventDefault(); fields[0].focus(); }
    };
    document.addEventListener('keydown', handle);
    return () => { document.removeEventListener('keydown', handle); document.body.style.overflow = overflow; previous?.focus(); };
  }, [loginOpen, signupOpen]);
  const submitLogin = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (!id.trim() || !password) {
      setLoginError('이메일과 비밀번호를 입력해 주세요.');
      return;
    }
    setLoginPending(true);
    setLoginError('');
    try {
      const result = await requestLogin(id.trim(), password);
      setLoginOpen(false);
      onLogin(pendingView, result.user);
    } catch (error) {
      setLoginError(error instanceof Error ? error.message : '로그인하지 못했어요.');
    } finally {
      setLoginPending(false);
    }
  };
  return <main className="landing-page">
    {tutorialOpen && <Tutorial onClose={() => setTutorialOpen(false)} />}
    <header className="landing-simple-header">
      <button className="landing-simple-logo" type="button" onClick={() => setLoginOpen(false)} aria-label="S.S.A.F.Y 홈"><img src="/ssafy-logo-alpha.png" alt="S.S.A.F.Y" /></button>
      <div className="landing-auth-actions">{authenticated ? <><button type="button" onClick={() => onLogin('dashboard')}>대시 보드</button><button className="filled" type="button" onClick={onLogout}>로그아웃</button></> : <><button type="button" onClick={() => { setLoginOpen(false); setSignupOpen(true); setSignupError(''); }}>회원가입</button><button className="filled" type="button" onClick={() => { setPendingView('dashboard'); setSignupOpen(false); setLoginOpen(true); }}>로그인</button></>}</div>
    </header>
    <section className="landing-hero">
      <div className="landing-copy">
        <p>ROBOT LEARNING PLATFORM</p>
        <h1>Smart Solutions<br />All For You</h1>
        <h2>로봇 학습부터 실행까지, S.S.A.F.Y 와 함께</h2>
        <div className="landing-actions"><button type="button" onClick={() => setTutorialOpen(true)}>튜토리얼</button></div>
      </div>
    </section>
    {loginOpen && <div className="landing-login-backdrop" onMouseDown={() => setLoginOpen(false)}><form ref={loginForm} role="dialog" aria-modal="true" aria-label="로그인" className="landing-login-card" onSubmit={submitLogin} onMouseDown={(event) => event.stopPropagation()}>
      <div className="landing-login-head"><img src="/ssafy-logo-alpha.png" alt="S.S.A.F.Y" /><button type="button" onClick={() => setLoginOpen(false)} aria-label="로그인 닫기">×</button></div>
      {signupComplete && <p className="signup-success" role="status">회원가입이 완료됐어요. 입력한 아이디로 로그인해 주세요.</p>}
      <label>이메일<input type="email" value={id} onChange={(event) => { setId(event.target.value); setLoginError(''); setSignupComplete(false); }} autoComplete="username" /></label><label htmlFor="login-password">비밀번호</label><div className="password-field"><input id="login-password" type={showPassword ? 'text' : 'password'} value={password} onChange={(event) => { setPassword(event.target.value); setLoginError(''); }} autoComplete="current-password" /><button type="button" aria-label={showPassword ? '비밀번호 숨기기' : '비밀번호 보기'} aria-pressed={showPassword} onClick={() => setShowPassword(!showPassword)}><svg viewBox="0 0 24 24" aria-hidden="true"><path d="M3 12s3.8-5 9-5 9 5 9 5-3.8 5-9 5-9-5-9-5z" /><circle cx="12" cy="12" r="2.5" />{showPassword && <path d="M4 4l16 16" />}</svg></button></div>{loginError && <p className="field-error" role="alert">{loginError}</p>}
      <button className="primary-button" disabled={loginPending}>{loginPending ? '로그인 중...' : '로그인'}</button>
    </form></div>}
    {signupOpen && <div className="landing-login-backdrop" onMouseDown={() => setSignupOpen(false)}><form ref={signupForm} role="dialog" aria-modal="true" aria-label="회원가입" className="landing-login-card signup-card" onSubmit={(event) => { event.preventDefault(); if (!signupName.trim() || !signupId.trim() || !signupPassword || !signupPasswordConfirm) { setSignupError('모든 항목을 입력해 주세요.'); return; } if (signupPassword.length < 8) { setSignupError('비밀번호는 8자 이상 입력해 주세요.'); return; } if (signupPassword !== signupPasswordConfirm) { setSignupError('비밀번호가 일치하지 않아요.'); return; } setId(signupId.trim()); setPassword(signupPassword); setSignupComplete(true); setSignupOpen(false); setPendingView('dashboard'); setLoginOpen(true); }} onMouseDown={(event) => event.stopPropagation()}>
      <div className="landing-login-head"><img src="/ssafy-logo-alpha.png" alt="S.S.A.F.Y" /><button type="button" onClick={() => setSignupOpen(false)} aria-label="회원가입 닫기">×</button></div>
      <label>이름<input value={signupName} onChange={(event) => { setSignupName(event.target.value); setSignupError(''); }} autoComplete="name" placeholder="이름을 입력하세요" /></label>
      <label>아이디<input value={signupId} onChange={(event) => { setSignupId(event.target.value); setSignupError(''); }} autoComplete="username" placeholder="사용할 아이디를 입력하세요" /></label>
      <label>비밀번호<input type="password" value={signupPassword} onChange={(event) => { setSignupPassword(event.target.value); setSignupError(''); }} autoComplete="new-password" placeholder="8자 이상 입력하세요" /></label>
      <label>비밀번호 확인<input type="password" value={signupPasswordConfirm} onChange={(event) => { setSignupPasswordConfirm(event.target.value); setSignupError(''); }} autoComplete="new-password" placeholder="비밀번호를 다시 입력하세요" /></label>
      {signupError && <p className="field-error" role="alert">{signupError}</p>}
      <button className="primary-button">가입하기</button>
      <button className="signup-login-link" type="button" onClick={() => { setSignupOpen(false); setLoginOpen(true); }}>이미 계정이 있어요 · 로그인</button>
    </form></div>}
  </main>;
}

function NavIcon({ name }: { name: 'dashboard' | 'data' | 'learning' | 'work' | 'devices' }) {
  const paths = {
    dashboard: <><rect x="3" y="3" width="7" height="7" rx="2" /><rect x="14" y="3" width="7" height="7" rx="2" /><rect x="3" y="14" width="7" height="7" rx="2" /><rect x="14" y="14" width="7" height="7" rx="2" /></>,
    data: <><ellipse cx="12" cy="5" rx="8" ry="3" /><path d="M4 5v7c0 1.7 3.6 3 8 3s8-1.3 8-3V5" /><path d="M4 12v7c0 1.7 3.6 3 8 3s8-1.3 8-3v-7" /></>,
    learning: <><path d="M3 5.5 12 2l9 3.5-9 3.5-9-3.5Z" /><path d="M6 8v6.5c0 1.7 2.7 3.5 6 3.5s6-1.8 6-3.5V8" /><path d="M21 6v7" /></>,
    work: <><rect x="5" y="4" width="14" height="17" rx="2" /><path d="M9 4V2h6v2M8.5 10h7M8.5 15h5" /></>,
    devices: <><rect x="3" y="4" width="18" height="13" rx="2" /><path d="M8 21h8M12 17v4" /><circle cx="12" cy="10.5" r="2.5" /></>,
  };
  return <span className="global-nav-icon" aria-hidden="true"><svg viewBox="0 0 24 24">{paths[name]}</svg></span>;
}

function GlobalHeader({ authenticated, activeView, onHome, onNavigate, onAuth, variant = 'header', userId = 'admin01' }: { authenticated: boolean; activeView?: View; onHome: () => void; onNavigate: (view: View) => void; onAuth: () => void; variant?: 'header' | 'sidebar'; userId?: string }) {
  const [menuOpen, setMenuOpen] = useState(false);
  const [expandedSections, setExpandedSections] = useState(() => ({
    learning: activeView === 'training' || activeView === 'learn',
    work: activeView === 'library' || activeView === 'work',
  }));
  const menuRef = useRef<HTMLElement>(null);
  useEffect(() => {
    if (menuOpen) menuRef.current?.scrollTo({ top: 0, left: 0 });
  }, [menuOpen]);
  /* eslint-disable react-hooks/set-state-in-effect -- keep the current route's sidebar group visible */
  useEffect(() => {
    if (activeView === 'training' || activeView === 'learn') setExpandedSections((current) => ({ ...current, learning: true }));
    else if (activeView === 'library' || activeView === 'work') setExpandedSections((current) => ({ ...current, work: true }));
  }, [activeView]);
  /* eslint-enable react-hooks/set-state-in-effect */
  const goTo = (view: View) => {
    setMenuOpen(false);
    onNavigate(view);
  };
  const handleSection = (section: 'learning' | 'work', fallback: View) => {
    if (variant === 'sidebar') {
      if (typeof window !== 'undefined' && window.matchMedia('(max-width: 900px)').matches) {
        setExpandedSections((current) => ({ ...current, [section]: !current[section] }));
      }
      return;
    }
    goTo(fallback);
  };
  return <header className={`global-header${variant === 'sidebar' ? ' app-sidebar' : ''}${menuOpen ? ' menu-open' : ''}`}>
    <button className="global-brand" onClick={onHome} aria-label="S.S.A.F.Y 메인으로 이동"><img src="/ssafy-logo-alpha.png" alt="S.S.A.F.Y" />{variant === 'header' && <span className="brand-fullname">Show Simply Arm Follows You</span>}</button>
    <nav ref={menuRef} className={`global-nav${menuOpen ? ' open' : ''}`} aria-label="플랫폼 메뉴">
      <div className="global-nav-item"><button className={activeView === 'dashboard' ? 'active' : ''} onClick={() => goTo('dashboard')}><NavIcon name="dashboard" />대시 보드</button></div>
      <div className="global-nav-item"><button className={activeView === 'data' ? 'active' : ''} onClick={() => goTo('data')}><NavIcon name="data" />데이터</button></div>
      <div className={`global-nav-item has-submenu${expandedSections.learning ? ' expanded' : ''}`}><button className={activeView === 'training' || activeView === 'learn' ? 'active' : ''} aria-expanded={variant === 'sidebar' ? expandedSections.learning : undefined} onClick={() => handleSection('learning', 'training')}><NavIcon name="learning" />학습 <span className="nav-chevron" aria-hidden="true" /></button><div className="global-submenu"><button onClick={() => goTo('training')}>학습 목록</button><button onClick={() => goTo('learn')}>학습 생성</button></div></div>
      <div className={`global-nav-item has-submenu${expandedSections.work ? ' expanded' : ''}`}><button className={activeView === 'library' || activeView === 'work' ? 'active' : ''} aria-expanded={variant === 'sidebar' ? expandedSections.work : undefined} onClick={() => handleSection('work', 'library')}><NavIcon name="work" />작업 <span className="nav-chevron" aria-hidden="true" /></button><div className="global-submenu"><button onClick={() => goTo('library')}>작업 목록</button><button onClick={() => goTo('work')}>작업 생성</button></div></div>
      <div className="global-nav-item"><button className={activeView === 'devices' || activeView === 'robot-detail' ? 'active' : ''} onClick={() => goTo('devices')}><NavIcon name="devices" />장치 관리</button></div>
      <button className="mobile-auth" onClick={() => { setMenuOpen(false); onAuth(); }}>{authenticated ? '로그아웃' : '로그인'}</button>
    </nav>
    <div className="global-actions">{variant === 'sidebar' && <span className="sidebar-greeting">안녕하세요, <strong>{userId}</strong>님</span>}<button className="global-auth" onClick={onAuth}>{authenticated ? 'SIGN OUT' : 'SIGN IN'}</button><button className={`global-menu-toggle${menuOpen ? ' open' : ''}`} type="button" aria-label={menuOpen ? '메뉴 닫기' : '메뉴 열기'} aria-expanded={menuOpen} onClick={() => setMenuOpen((open) => !open)}><i /><i /><i /></button></div>
    {variant === 'sidebar' && menuOpen && <button className="sidebar-backdrop" type="button" aria-label="메뉴 닫기" onClick={() => setMenuOpen(false)} />}
  </header>;
}

function DashboardView({ onNavigate, onOpenDevices, onSelectRobot }: { onNavigate: (view: View) => void; onOpenDevices: (tab: DeviceType) => void; onSelectRobot: (id: string) => void }) {
  const [dashboard, setDashboard] = useState<DashboardData | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(false);
  const loadDashboard = useCallback(async () => {
    setLoading(true);
    setError(false);
    try { setDashboard(await fetchDashboard()); }
    catch { setError(true); }
    finally { setLoading(false); }
  }, []);
  /* eslint-disable react-hooks/set-state-in-effect -- 화면 진입 시 서버 대시보드를 조회한다. */
  useEffect(() => { void loadDashboard(); }, [loadDashboard]);
  /* eslint-enable react-hooks/set-state-in-effect */

  if (loading) return <div className="center-panel"><section className="card state-card"><span className="spinner" aria-hidden="true" /><h2>대시보드를 불러오는 중이에요</h2></section></div>;
  if (error || !dashboard) return <div className="center-panel"><section className="card state-card"><span className="state-icon">!</span><h2>대시보드를 불러오지 못했어요</h2><button className="primary-button" onClick={() => void loadDashboard()}>다시 시도</button></section></div>;

  const { summary, robots, recentTrainings } = dashboard;
  return <>
    <div className="page-heading title-divider"><div><h1>대시 보드</h1></div></div>
    <div className="overview-cards">
      <button onClick={() => onOpenDevices('umi')}><div><small>등록된 UMI</small><strong>{summary.umi.totalCount}<em>대</em></strong><p><i className="online-dot" /> {summary.umi.onlineCount}대 연결됨</p></div></button>
      <button onClick={() => onOpenDevices('robot')}><div><small>등록된 로봇</small><strong>{summary.robots.totalCount}<em>대</em></strong><p><i className="online-dot" /> {summary.robots.availableCount}대 사용 가능</p></div></button>
      <button onClick={() => onNavigate('training')}><div><small>진행 중인 학습</small><strong>{summary.trainings.runningCount}<em>건</em></strong><p>평균 진행률 {Math.round(summary.trainings.averageProgressPercent)}%</p></div></button>
      <button onClick={() => onNavigate('work')}><div><small>오늘 완료한 작업</small><strong>{summary.executions.completedTodayCount}<em>건</em></strong><p>성공률 {Math.round(summary.executions.successRatePercent)}%</p></div></button>
    </div>
    <div className="dashboard-grid">
      <section className="card dashboard-section"><div className="dashboard-title"><div><h2>로봇 상태</h2></div><button className="text-button" onClick={() => onOpenDevices('robot')}>전체 보기 →</button></div><div className="compact-robot-list">{robots.map(robot => { const label = dashboardRobotStatusLabel[robot.status]; return <button key={robot.id} onClick={() => onSelectRobot(robot.id)}><span className="robot-tile">R</span><div><strong>{robot.name}</strong><small>{formatDashboardTime(robot.lastSeenAt)}</small></div><span className={`status-pill status-${label.replace(' ', '-')}`}><i />{label}</span><em>›</em></button>; })}{!robots.length && <p className="table-empty">등록된 로봇이 없어요.</p>}</div></section>
      <section className="card dashboard-section"><div className="dashboard-title"><div><h2>최근 학습</h2></div><button className="text-button" onClick={() => onNavigate('training')}>전체 보기 →</button></div><div className="compact-training-list">{recentTrainings.map(item => { const status = dashboardTrainingStatus(item.status); return <button key={item.id} onClick={() => onNavigate('training')}><div><strong>{item.skillName}</strong><small>{item.umiDeviceName} · {item.stage ?? '단계 확인 중'}</small></div><span className={`training-badge ${status === '실패' ? 'failed' : status === '완료' ? 'complete' : ''}`}>{status}</span><div className="compact-training-progress"><div className="tiny-progress"><i style={{width: `${item.progressPercent}%`}} /></div><em>{Math.round(item.progressPercent)}%</em></div></button>; })}{!recentTrainings.length && <p className="table-empty">최근 학습이 없어요.</p>}</div></section>
    </div>
  </>;
}

const dashboardRobotStatusLabel: Record<DashboardRobotStatus, RobotStatus> = {
  AVAILABLE: '온라인', BUSY: '작업 중', OFFLINE: '오프라인',
};

function dashboardTrainingStatus(status: string): TrainingStatus {
  if (status === 'SUCCEEDED') return '완료';
  if (status === 'FAILED') return '실패';
  if (status === 'CANCELED') return '취소됨';
  if (status === 'CANCEL_REQUESTED') return '취소 요청';
  return '진행 중';
}

function formatDashboardTime(value: string | null): string {
  if (!value) return '통신 기록 없음';
  return new Intl.DateTimeFormat('ko-KR', { month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' }).format(new Date(value));
}

function DevicesView({ initialTab, onSelectRobot, onToast }: { initialTab: DeviceType; onSelectRobot: (id: string) => void; onToast: (message: string) => void }) {
  const [tab, setTab] = useState<DeviceType>(initialTab);
  const [sort, setSort] = useState<'recent' | 'oldest'>('recent');
  const [registerOpen, setRegisterOpen] = useState(false);
  const { robots, umis: umi } = useAppStore();
  const recentOrder = (value: string) => value === '방금 전' ? -1 : value.startsWith('오늘') ? 0 : value.startsWith('어제') ? 1 : Number.parseInt(value, 10) || 99;
  const sortedRobots = sort === 'recent' ? [...robots] : [...robots].reverse();
  const sortedUmi = [...umi].sort((a, b) => sort === 'recent' ? recentOrder(a.collected) - recentOrder(b.collected) : recentOrder(b.collected) - recentOrder(a.collected));
  const changeTab = (nextTab: DeviceType) => { setTab(nextTab); setSort('recent'); };
  return <><div className="page-heading title-divider"><div><h1>장치 관리</h1></div></div><div className="device-toolbar"><div className="device-tabs"><button className={tab === 'umi' ? 'active' : ''} onClick={() => changeTab('umi')}>UMI <span>{umi.length}</span></button><button className={tab === 'robot' ? 'active' : ''} onClick={() => changeTab('robot')}>로봇 <span>{robots.length}</span></button></div><label className="device-sort"><select aria-label="장치 정렬" value={sort} onChange={(event) => setSort(event.target.value as typeof sort)}><option value="recent">최근 사용순</option><option value="oldest">오래된 순</option></select></label><button className="device-register-action" onClick={() => setRegisterOpen(true)}>새 장치 등록 <span aria-hidden="true">›</span></button></div>
    {tab === 'robot' && !robots.length && <p className="table-empty">등록된 로봇이 없어요. ‘장치 등록’ 버튼으로 추가해 보세요.</p>}
    {tab === 'umi' && !umi.length && <p className="table-empty">등록된 UMI가 없어요. ‘장치 등록’ 버튼으로 추가해 보세요.</p>}
    {tab === 'robot' ? <div className="device-grid">{sortedRobots.map(robot => <button className="card device-card" key={robot.id} onClick={() => onSelectRobot(robot.id)}><div className="device-card-head"><span className="device-visual">R</span><span className={`status-pill status-${robot.status.replace(' ', '-')}`}><i />{robot.status}</span></div><h2>{robot.name}</h2><p>{robot.location}</p><dl><div><dt>현재 모델</dt><dd>{robot.model}</dd></div><div><dt>연결 상태</dt><dd>{robot.status}</dd></div></dl><span className="detail-link">상세 정보 보기 →</span></button>)}</div> : <div className="device-grid">{sortedUmi.map(item => <article className="card device-card" key={item.id}><div className="device-card-head"><span className="device-visual umi">U</span><span className={`status-pill status-${item.status}`}><i />{item.status}</span></div><h2>{item.id}</h2><p>{item.place}</p><dl><div><dt>최근 사용</dt><dd>{item.collected}</dd></div><div><dt>보유 데이터</dt><dd>{item.data}개</dd></div></dl><button className="secondary-button">데이터 보기</button></article>)}</div>}
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

function RobotDetailView({ robotId, onBack, onNavigate, onToast }: { robotId: string; onBack: () => void; onNavigate: (view: View) => void; onToast: (message: string) => void }) {
  const { robots, savedWorks, updateRobotInfo } = useAppStore();
  const listedRobot = robots.find(item => item.id === robotId) ?? robots[0];
  const detailRobotId = listedRobot?.robotId;
  const [detail, setDetail] = useState<RobotDetail | null>(null);
  const [executionHistory, setExecutionHistory] = useState<RobotExecution[] | null>(null);
  const [detailError, setDetailError] = useState(false);
  const [settingsOpen, setSettingsOpen] = useState(false);
  useEffect(() => {
    let active = true;
    setDetail(null);
    setExecutionHistory(null);
    setDetailError(false);
    if (!detailRobotId) return () => { active = false; };
    Promise.allSettled([fetchRobotDetail(detailRobotId), fetchRobotExecutions(detailRobotId)]).then(([robotResult, executionResult]) => {
      if (!active) return;
      if (robotResult.status === 'fulfilled') setDetail(robotResult.value);
      else setDetailError(true);
      if (executionResult.status === 'fulfilled') setExecutionHistory(executionResult.value.data);
    });
    return () => { active = false; };
  }, [detailRobotId]);
  const robot = listedRobot && detail ? { ...listedRobot, name: detail.name, status: detail.status } : listedRobot;
  if (!robot) return <div className="center-panel"><section className="card state-card"><span className="state-icon">R</span><h2>등록된 로봇이 없어요</h2><p>장치 관리에서 로봇을 먼저 등록해 주세요.</p><button className="primary-button" onClick={onBack}>장치 관리로 이동</button></section></div>;

  const today = new Date().toDateString();
  const todayExecutions = executionHistory?.filter(item => new Date(item.requestedAt).toDateString() === today);
  const succeeded = todayExecutions?.filter(item => item.status === 'SUCCEEDED').length;
  const failed = todayExecutions?.filter(item => item.status === 'FAILED').length;
  const runtimeSeconds = todayExecutions?.reduce((sum, item) => item.startedAt && item.finishedAt
    ? sum + Math.max(0, (new Date(item.finishedAt).getTime() - new Date(item.startedAt).getTime()) / 1000)
    : sum, 0);
  const runtimeLabel = runtimeSeconds === undefined ? 'API로 호출할 수 없습니다' : `${Math.floor(runtimeSeconds / 3600)}시간 ${Math.floor(runtimeSeconds % 3600 / 60)}분`;
  const executionLabel = (status: RobotExecution['status']) => status === 'SUCCEEDED' ? '성공' : status === 'FAILED' ? '실패' : status === 'CANCELED' ? '취소' : '진행 중';

  return <><button className="back-link" onClick={onBack}>← 로봇 목록</button><div className="robot-detail-hero card"><div className="device-visual large">R</div><div><h1>{robot.name}</h1><p>{robot.location} · 마지막 통신 {detail?.lastSeenAt ? new Date(detail.lastSeenAt).toLocaleString('ko-KR') : 'API로 호출할 수 없습니다'}</p></div><span className={`status-pill large status-${robot.status.replace(' ', '-')}`}><i />{detailError ? 'API로 호출할 수 없습니다' : robot.status}</span><div className="detail-actions"><button className="secondary-button" onClick={() => setSettingsOpen(true)}>설정</button><button className="primary-button" disabled={robot.status !== '온라인'} onClick={() => onNavigate('work')}>작업 생성</button></div></div><div className="detail-metrics robot-detail-metrics"><article className="card"><small>오늘 작업</small><strong>{todayExecutions ? `${todayExecutions.length}건` : 'API로 호출할 수 없습니다'}</strong><p>{todayExecutions ? `성공 ${succeeded} · 실패 ${failed}` : 'API로 호출할 수 없습니다'}</p></article><article className="card"><small>가동 시간</small><strong>{runtimeLabel}</strong><p>오늘 완료된 작업 기준</p></article><article className="card"><small>현재 모델</small><strong>API로 호출할 수 없습니다</strong><p>{detail?.currentDeploymentId ? '배포 정보는 있지만 모델명은 제공되지 않음' : '현재 배포 정보 없음'}</p></article></div><div className="robot-history-grid"><section className="card dashboard-section"><div className="dashboard-title"><div><h2>최근 작업 기록</h2></div></div><div className="history-list">{executionHistory === null ? <p><strong>API로 호출할 수 없습니다</strong></p> : executionHistory.length ? executionHistory.slice(0, 5).map(item => { const work = savedWorks.find(saved => saved.taskVersionId === item.taskVersionId); return <p key={item.id}><strong>{work?.name ?? 'API로 호출할 수 없습니다'}</strong><span className={`training-badge ${item.status === 'SUCCEEDED' ? 'complete' : item.status === 'FAILED' ? 'failed' : ''}`}>{executionLabel(item.status)}</span><small>{new Date(item.requestedAt).toLocaleString('ko-KR')}</small></p>; }) : <p><strong>최근 작업 기록이 없습니다.</strong></p>}</div></section></div>
    {settingsOpen && <RobotSettingsModal robot={robot} onClose={() => setSettingsOpen(false)} onSave={async (patch) => { await updateRobotInfo(robot.jetsonDeviceId ?? robot.id, patch); setSettingsOpen(false); onToast('로봇 설정을 저장했어요.'); }} />}
  </>;
}

function RobotSettingsModal({ robot, onClose, onSave }: { robot: { id: string; name: string; location: string }; onClose: () => void; onSave: (patch: { name: string; location: string }) => Promise<void> }) {
  const [name, setName] = useState(robot.name);
  const [location, setLocation] = useState(robot.location);
  const [error, setError] = useState('');
  const [saving, setSaving] = useState(false);

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    if (!name.trim() || !location.trim()) {
      setError('이름과 설치 위치를 모두 입력해 주세요.');
      return;
    }
    setError('');
    setSaving(true);
    await onSave({ name, location });
  };

  return <div className="modal-backdrop" onMouseDown={onClose}>
    <form className="register-modal" role="dialog" aria-modal="true" aria-label="로봇 설정" onMouseDown={(event) => event.stopPropagation()} onSubmit={submit}>
      <div className="modal-heading"><div><h2>로봇 설정</h2><p>로봇의 기본 정보를 변경하세요.</p></div><button type="button" className="close-button" onClick={onClose} aria-label="닫기">×</button></div>
      <div className="form-grid">
        <label>로봇 이름<input value={name} disabled={saving} onChange={(event) => setName(event.target.value)} placeholder="예: 5번 피킹 로봇" /></label>
        <label>설치 위치<input value={location} disabled={saving} onChange={(event) => setLocation(event.target.value)} placeholder="예: 물류 창고 2층" /></label>
      </div>
      {error && <p className="form-error" role="alert">{error}</p>}
      <div className="card-actions"><button type="button" className="secondary-button" disabled={saving} onClick={onClose}>취소</button><button className="primary-button" disabled={saving}>{saving ? '저장 중...' : '설정 저장'}</button></div>
    </form>
  </div>;
}

function TrainingStatusView({ onNavigate, onToast, onUseResult }: { onNavigate: (view: View) => void; onToast: (message: string) => void; onUseResult: (name: string) => void }) {
  const { skills, trainings, trainingRefreshing, trainingRefreshError, trainingUpdatedAt, refreshTrainings, cancelTrainingById, deleteTrainingsById, retryTraining } = useAppStore();
  const [statusFilter, setStatusFilter] = useState<'all' | TrainingStatus>('all');
  const [detail, setDetail] = useState<Training | null>(null);
  const [confirmFor, setConfirmFor] = useState<Training | null>(null);
  const [busy, setBusy] = useState(false);
  const [selecting, setSelecting] = useState(false);
  const [selectedIds, setSelectedIds] = useState<string[]>([]);
  const [deleteOpen, setDeleteOpen] = useState(false);
  const countOf = (status: TrainingStatus) => trainings.filter((item) => item.status === status).length;
  const filtered = statusFilter === 'all' ? trainings : trainings.filter((item) => item.status === statusFilter);
  const openDetail = async (item: Training) => {
    if (item.id.startsWith('draft-')) { setDetail(item); return; }
    try { setDetail(await fetchTrainingDetail(item.id)); }
    catch { onToast('학습 상세 정보를 불러오지 못했어요.'); }
  };

  const retry = async (item: Training) => {
    setDetail(null);
    await retryTraining(item);
    onToast(`‘${item.name}’ 학습을 다시 시작했어요.`);
  };
  const cancelConfirmed = async () => {
    if (!confirmFor) return;
    setBusy(true);
    await cancelTrainingById(confirmFor.id);
    onToast(`‘${confirmFor.name}’ 학습을 취소했어요.`);
    setBusy(false);
    setConfirmFor(null);
  };
  const refreshStatus = async () => {
    const refreshed = await refreshTrainings();
    onToast(refreshed ? '학습 상태를 최신 정보로 갱신했어요.' : '학습 상태를 불러오지 못했어요. 다시 시도해 주세요.');
  };
  const deletable = filtered.filter((item) => item.status !== '진행 중' && item.status !== '취소 요청');
  const deleteSelected = async () => {
    setBusy(true);
    try {
      await deleteTrainingsById(selectedIds);
      onToast(`${selectedIds.length}개 학습을 삭제했어요.`);
      setSelectedIds([]);
      setSelecting(false);
      setDeleteOpen(false);
    } catch { onToast('삭제 API가 아직 연결되지 않았거나 삭제에 실패했어요.'); }
    finally { setBusy(false); }
  };
  const updatedLabel = trainingUpdatedAt
    ? new Intl.DateTimeFormat('ko-KR', { hour: '2-digit', minute: '2-digit', second: '2-digit' }).format(trainingUpdatedAt)
    : '확인 중';

  return <>
    <div className="page-heading list-page-heading title-divider"><div><h1>학습 목록</h1></div><button className="primary-button page-create-button" onClick={() => onNavigate('learn')}>새 학습 생성 <span aria-hidden="true">›</span></button></div>
    <div className="training-summary" aria-label="학습 상태별 보기"><button className={statusFilter === 'all' ? 'active' : ''} onClick={() => setStatusFilter('all')}><small>전체</small><strong>{trainings.length}</strong></button><button className={statusFilter === '진행 중' ? 'active' : ''} onClick={() => setStatusFilter('진행 중')}><small>진행 중</small><strong>{countOf('진행 중')}</strong></button><button className={statusFilter === '완료' ? 'active' : ''} onClick={() => setStatusFilter('완료')}><small>완료</small><strong>{countOf('완료')}</strong></button><button className={statusFilter === '실패' ? 'active' : ''} onClick={() => setStatusFilter('실패')}><small>확인 필요</small><strong>{countOf('실패')}</strong></button></div>
    <section className="card training-table">
      <div className="training-table-head"><div className="selection-summary">{selecting && <input className="list-select-checkbox" type="checkbox" aria-label="삭제 가능한 학습 전체 선택" disabled={!deletable.length} checked={deletable.length > 0 && selectedIds.length === deletable.length} onChange={() => setSelectedIds(selectedIds.length === deletable.length ? [] : deletable.map(item => item.id))} />}<p className={`refresh-status ${trainingRefreshError ? 'failed' : ''}`} role="status">{selecting ? `${selectedIds.length}개 선택됨` : trainingRefreshError ? '마지막 갱신에 실패했어요.' : `마지막 갱신 ${updatedLabel}`}</p></div><div className="training-controls"><button className={selecting ? 'danger-button list-delete-button' : 'secondary-button'} onClick={() => { if (!selecting) setSelecting(true); else if (selectedIds.length) setDeleteOpen(true); else setSelecting(false); }}>삭제</button><button className="secondary-button refresh-button" disabled={trainingRefreshing} onClick={() => void refreshStatus()}>{trainingRefreshing ? '갱신 중...' : '↻ 새로고침'}</button><select value={statusFilter} onChange={(e) => setStatusFilter(e.target.value as 'all' | TrainingStatus)} aria-label="학습 상태 필터"><option value="all">전체 상태</option><option value="진행 중">진행 중</option><option value="취소 요청">취소 요청</option><option value="취소됨">취소됨</option><option value="완료">완료</option><option value="실패">실패</option></select></div></div>
      {filtered.map(item => <article key={item.id}>
        {selecting && <input className="list-select-checkbox" type="checkbox" aria-label={`${item.name} 선택`} disabled={item.status === '진행 중' || item.status === '취소 요청'} checked={selectedIds.includes(item.id)} onChange={() => setSelectedIds(current => current.includes(item.id) ? current.filter(id => id !== item.id) : [...current, item.id])} />}
        <div className="training-main"><span className="learning-symbol">✦</span><div><button className="training-title-button" onClick={() => void openDetail(item)}>{item.name}</button><small>{item.robot} · {item.started}</small></div></div>
        <div><small>현재 단계</small><strong>{item.stage}</strong></div>
        <div className="training-progress"><div><i style={{width: `${item.progress}%`}} /></div><strong>{item.progress}%</strong></div>
        <span className={`training-badge ${item.status === '실패' ? 'failed' : item.status === '완료' ? 'complete' : ''}`}>{item.status}</span>
      </article>)}
      {!filtered.length && <div className="table-empty empty-with-action"><p>{trainings.length ? '선택한 상태의 학습이 없어요.' : '아직 생성한 학습이 없어요.'}</p><button className="secondary-button" onClick={() => trainings.length ? setStatusFilter('all') : onNavigate('learn')}>{trainings.length ? '전체 상태 보기' : '학습 생성하기'}</button></div>}
    </section>
    {detail && <div className="modal-backdrop" onMouseDown={() => setDetail(null)}><section className="register-modal data-detail-modal training-detail-modal" role="dialog" aria-modal="true" aria-label="학습 상세 정보" onMouseDown={(e) => e.stopPropagation()}><div className="modal-heading data-modal-heading"><div><p>학습 상세</p><div className="data-modal-title"><h2>{detail.name}</h2><span className={`training-badge ${detail.status === '실패' ? 'failed' : detail.status === '완료' ? 'complete' : ''}`}>{detail.status}</span></div></div><button className="close-button" onClick={() => setDetail(null)} aria-label="닫기">×</button></div><dl className="data-detail-grid"><div><dt>현재 단계</dt><dd>{detail.stage}</dd></div><div><dt>대상 로봇</dt><dd>{detail.robot}</dd></div><div><dt>시작 시각</dt><dd>{detail.started}</dd></div><div><dt>진행률</dt><dd>{detail.progress}%</dd></div></dl><div className="training-modal-progress"><div className="training-progress"><div><i style={{width: `${detail.progress}%`}} /></div><strong>{detail.progress}%</strong></div></div>{(['완료', '진행 중', '실패'] as TrainingStatus[]).includes(detail.status) && <div className="data-modal-footer training-modal-footer"><div>{detail.status === '완료' && !skills.some(skill => skill.name === detail.name) && <p className="modal-note">이 학습 결과의 동작 연결 정보가 아직 없어요.</p>}</div>{detail.status === '완료' && <button className="primary-button" disabled={!skills.some(skill => skill.name === detail.name)} onClick={() => onUseResult(detail.name)}>이 동작으로 작업 생성</button>}{detail.status === '진행 중' && <button className="danger-button" onClick={() => { setDetail(null); setConfirmFor(detail); }}>학습 취소</button>}{detail.status === '실패' && <button className="primary-button" onClick={() => void retry(detail)}>다시 시도</button>}</div>}</section></div>}
    {confirmFor && <div className="modal-backdrop" onMouseDown={() => !busy && setConfirmFor(null)}><section className="confirm-modal" role="dialog" aria-modal="true" aria-label="학습 취소 확인" onMouseDown={(e) => e.stopPropagation()}><div className="modal-heading"><div><h2>학습을 취소할까요?</h2><p>취소 요청 후 서버가 중단을 확인하면 취소 완료로 표시돼요.</p></div><button className="close-button" onClick={() => setConfirmFor(null)} aria-label="닫기">×</button></div><div className="card-actions"><button className="secondary-button" disabled={busy} onClick={() => setConfirmFor(null)}>계속 진행</button><button className="danger-button" disabled={busy} onClick={cancelConfirmed}>{busy ? '요청 중...' : '취소 요청'}</button></div></section></div>}
    {deleteOpen && <div className="modal-backdrop" onMouseDown={() => !busy && setDeleteOpen(false)}><section className="confirm-modal" role="dialog" aria-modal="true" aria-label="학습 선택 삭제 확인" onMouseDown={(e) => e.stopPropagation()}><div className="modal-heading"><div><h2>선택한 학습을 삭제할까요?</h2><p>{selectedIds.length}개 학습이 삭제됩니다.</p></div><button className="close-button" onClick={() => setDeleteOpen(false)} aria-label="닫기">×</button></div><div className="card-actions"><button className="secondary-button" disabled={busy} onClick={() => setDeleteOpen(false)}>돌아가기</button><button className="danger-button" disabled={busy} onClick={() => void deleteSelected()}>{busy ? '삭제 중...' : '삭제'}</button></div></section></div>}
  </>;
}

const datasetStatusMeta: Record<Dataset['status'], { label: string; description: string; className: string }> = {
  REGISTERED: { label: '등록 완료', description: '파일 업로드를 기다리고 있어요.', className: 'registered' },
  UPLOADING: { label: '업로드 중', description: '영상과 센서 데이터를 전송하고 있어요.', className: 'uploading' },
  VALIDATING: { label: '확인 중', description: '파일 형식과 시간 정보를 확인하고 있어요.', className: 'validating' },
  READY: { label: '학습 가능', description: '', className: 'ready' },
  INVALID: { label: '확인 실패', description: '문제를 확인하고 데이터를 다시 수집해 주세요.', className: 'invalid' },
  DELETING: { label: '삭제 중', description: '보관 기한이 지나 파일을 삭제하고 있어요.', className: 'validating' },
  DELETED: { label: '삭제됨', description: '보관 기한이 지나 파일이 삭제됐어요.', className: 'invalid' },
  DELETE_FAILED: { label: '삭제 실패', description: '파일 삭제에 실패했어요. 관리자에게 문의해 주세요.', className: 'invalid' },
};

const recoveryActionLabel: Record<Dataset['recoveryAction'], string> = {
  WAIT: '처리가 끝날 때까지 기다려 주세요.', RETRY_UPLOAD: '데이터를 다시 전송해 주세요.', RECORD_AGAIN: '동작을 다시 보여주세요.',
  SET_LABEL: '데이터의 동작 정보를 확인해 주세요.', EXTEND_RETENTION: '보관 기간을 연장해 주세요.', CREATE_DEPLOYMENT: '로봇에 작업을 먼저 배포해 주세요.',
  RETRY: '잠시 후 다시 시도해 주세요.', CONTACT_SUPPORT: '관리자에게 문의해 주세요.', NONE: '',
};

function DataView() {
  const { datasets } = useAppStore();
  const [sortMode, setSortMode] = useState<'latest' | 'oldest' | 'retention'>('latest');
  const [statusFilter, setStatusFilter] = useState('');
  const [detail, setDetail] = useState<Dataset | null>(null);
  const [preview, setPreview] = useState<EpisodePreviewSession | null>(null);
  const [previewLoading, setPreviewLoading] = useState(false);
  const [previewError, setPreviewError] = useState('');
  const processingStatuses: Dataset['status'][] = ['REGISTERED', 'UPLOADING', 'VALIDATING', 'DELETING'];
  const matchesStatus = (item: Dataset) => !statusFilter || (statusFilter === 'PROCESSING' ? processingStatuses.includes(item.status) : item.status === statusFilter);
  const filtered = datasets.filter(matchesStatus).sort((a, b) => sortMode === 'retention' ? a.retentionDays - b.retentionDays : sortMode === 'oldest' ? a.date.localeCompare(b.date) : b.date.localeCompare(a.date));
  const statusCount = (status: string) => datasets.filter(item => item.status === status).length;
  const processingCount = datasets.filter(item => processingStatuses.includes(item.status)).length;
  const openPreview = async () => {
    if (!detail) return;
    setPreviewLoading(true); setPreviewError('');
    try { setPreview(await createEpisodePreviewSession(detail.id)); }
    catch (error) { setPreviewError(error instanceof Error ? error.message : '미리보기를 불러오지 못했어요.'); }
    finally { setPreviewLoading(false); }
  };
  return <>
    <div className="page-heading data-page-heading"><div><h1>데이터</h1></div><div className="data-list-controls"><select aria-label="데이터 상태" value={statusFilter} onChange={e => setStatusFilter(e.target.value)}><option value="">전체 ({datasets.length})</option><option value="READY">학습 가능 ({statusCount('READY')})</option><option value="PROCESSING">처리 중 ({processingCount})</option><option value="INVALID">확인 실패 ({statusCount('INVALID')})</option></select><select aria-label="데이터 정렬" value={sortMode} onChange={e => setSortMode(e.target.value as typeof sortMode)}><option value="latest">최신 등록순</option><option value="oldest">오래된 등록순</option><option value="retention">보관 만료 임박순</option></select></div></div>
    <section className="card episode-panel data-table-panel">
      <div className="episode-table" role="table">
        <div className="episode-row header" role="row"><span>데이터 번호</span><span>학습 동작</span><span>사용 장치</span><span>확인 결과</span><span>보관 기간</span><span>학습 가능 여부</span></div>
        {filtered.map(item => { const status = datasetStatusMeta[item.status]; return <div className="episode-row" role="row" key={item.id}><span data-label="데이터 번호"><button className="text-button" onClick={() => setDetail(item)}>데이터 {String(item.seq).padStart(3, '0')} →</button><small>{item.date}</small></span><span data-label="학습 동작">{item.skillName}</span><span data-label="사용 장치">{item.umiId}</span><span data-label="확인 결과"><span className={`dataset-status ${status.className}`}><i />{status.label}</span></span><span data-label="보관 기간">{item.retentionDays}일 남음</span><span data-label="학습 가능 여부">{item.status === 'READY' ? '선택 가능' : item.status === 'INVALID' ? '재수집 필요' : '처리 완료 후 가능'}</span></div>; })}
        {!filtered.length && <div className="table-empty empty-with-action"><p>{datasets.length ? '조건에 맞는 데이터가 없어요.' : '아직 등록된 데이터가 없어요.'}</p>{datasets.length > 0 && <button className="secondary-button" onClick={() => setStatusFilter('')}>전체 상태 보기</button>}</div>}
      </div>
    </section>
    {detail && <div className="modal-backdrop" onMouseDown={() => { setDetail(null); setPreview(null); }}><section className="register-modal data-detail-modal" role="dialog" aria-modal="true" aria-label="데이터 상세" onMouseDown={e => e.stopPropagation()}><div className="modal-heading data-modal-heading"><div><p>데이터 상세</p><div className="data-modal-title"><h2>데이터 {String(detail.seq).padStart(3, '0')}</h2><span className={`dataset-status ${datasetStatusMeta[detail.status].className}`}><i />{datasetStatusMeta[detail.status].label}</span></div></div><button className="close-button" aria-label="닫기" onClick={() => { setDetail(null); setPreview(null); }}>×</button></div><dl className="data-detail-grid"><div><dt>학습 동작</dt><dd>{detail.skillName}</dd></div><div><dt>사용 장치</dt><dd>{detail.umiId}</dd></div><div><dt>촬영 정보</dt><dd>{detail.duration}<small>{detail.size}</small></dd></div><div><dt>학습 사용</dt><dd>{detail.trainable ? '사용 가능' : '사용할 수 없음'}</dd></div></dl>{detail.blockingReasons.length > 0 && <div className="validation-error" role="alert"><strong>확인이 필요한 이유</strong><ul>{detail.blockingReasons.map(reason => <li key={reason.code}>{reason.message}</li>)}</ul><p>{recoveryActionLabel[detail.recoveryAction]}</p></div>}{detail.status !== 'READY' && !detail.blockingReasons.length && <p className="validation-progress" role="status">상태가 바뀌면 이 화면에 자동으로 반영됩니다.</p>}{preview && <div className={`video-stage ${detail.tone}`} role="img" aria-label="데이터 프레임 미리보기"><span className="big-play">▦</span><p>프레임 미리보기 준비 완료</p></div>}{previewError && <p className="form-error" role="alert">{previewError}</p>}{(preview || (detail.status === 'READY' && !preview)) && <div className="data-modal-footer"><p className="modal-note">{preview ? `미리보기는 ${new Intl.DateTimeFormat('ko-KR', { hour: '2-digit', minute: '2-digit' }).format(new Date(preview.expiresAt))}까지 열 수 있어요.` : ''}</p>{detail.status === 'READY' && !preview && <button className="secondary-button" disabled={previewLoading} onClick={() => void openPreview()}>{previewLoading ? '준비 중...' : '데이터 미리보기'}</button>}</div>}</section></div>}
  </>;
}

function LearnView({ onNavigate, onToast }: { onNavigate: (view: View) => void; onToast: (message: string) => void }) {
  const { umis, datasets, learningDraft, updateLearningDraft, resetLearningDraft, cancelTrainingById, completeLearning, draftReady, draftStored } = useAppStore();
  const { step, skillKey, method, umiId, expectedEpisodes, selected, progress, name } = learningDraft;
  const setStep = (step: number) => updateLearningDraft({ step });
  const [newSkillActive, setNewSkillActive] = useState(Boolean(name.trim()));
  const setSkillKey = (skillKey: string) => {
    setNewSkillActive(false);
    updateLearningDraft({ skillKey, selected: [], name: '' });
  };
  const setMethod = (method: 'new' | 'existing' | null) => updateLearningDraft({ method });
  const setUmiId = (umiId: string) => updateLearningDraft({ umiId });
  const setExpectedEpisodes = (expectedEpisodes: number) => updateLearningDraft({ expectedEpisodes });
  const setSelected = (fn: (current: string[]) => string[]) => updateLearningDraft({ selected: fn(selected) });
  const [catalog, setCatalog] = useState<TrainingSkill[]>([]);
  const [catalogLoading, setCatalogLoading] = useState(true);
  const [catalogError, setCatalogError] = useState(false);
  const [requirements, setRequirements] = useState<TrainingRequirements | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [cancelOpen, setCancelOpen] = useState(false);
  const [canceling, setCanceling] = useState(false);
  const [savingResult, setSavingResult] = useState(false);
  const serverSkill = newSkillActive ? undefined : catalog.find(item => item.skillKey === skillKey);
  const displayedSkills = catalog.map((item, index) => ({
    key: item.skillKey,
    name: item.displayName,
    description: item.description ?? '등록된 설명이 없습니다.',
    mark: item.displayName.trim().charAt(0) || String(index + 1),
  }));
  const catalogSkill = displayedSkills.find(item => item.key === skillKey) ?? displayedSkills[0];
  const customName = name.trim();
  const skill = newSkillActive ? (customName ? { ...catalogSkill, name: customName } : undefined) : catalogSkill;
  const selectedUmi = umis.find(item => item.id === umiId);
  const availableData = newSkillActive ? [] : datasets.filter(item => item.skillKey === skillKey);
  const readyData = availableData.filter(item => item.ready);
  const selectedData = availableData.filter(item => item.ready && selected.includes(item.id));
  const allReadySelected = readyData.length > 0 && readyData.every(item => selected.includes(item.id));
  useEffect(() => {
    setCatalogLoading(true);
    setCatalogError(false);
    void fetchTrainingSkills()
      .then(setCatalog)
      .catch(() => {
        setCatalogError(true);
        onToast('학습 동작 목록을 불러오지 못했어요.');
      })
      .finally(() => setCatalogLoading(false));
  }, [onToast]);
  useEffect(() => {
    if (catalog.length && !catalog.some(item => item.skillKey === skillKey)) {
      updateLearningDraft({
        step: 1,
        skillKey: catalog[0].skillKey,
        method: null,
        selected: [],
        trainingJobId: undefined,
        name: '',
        error: undefined,
      });
    } else if (!catalogLoading && !catalog.length && step !== 1) {
      updateLearningDraft({
        step: 1,
        method: null,
        selected: [],
        trainingJobId: undefined,
        name: '',
        error: undefined,
      });
    }
  }, [catalog, catalogLoading, skillKey, step, updateLearningDraft]);
  useEffect(() => {
    if (!serverSkill) return;
    void fetchTrainingRequirements(serverSkill.skillKey).then((next) => {
      setRequirements(next);
      if (expectedEpisodes < next.minimumEpisodeCount) setExpectedEpisodes(next.recommendedEpisodeCount);
    }).catch(() => onToast('학습 입력 조건을 불러오지 못했어요.'));
  }, [serverSkill?.id]);
  useEffect(() => {
    if (step !== 5 || !learningDraft.trainingJobId) return;
    let active = true;
    const refreshJob = async () => {
      try {
        const job = await fetchTrainingDetail(learningDraft.trainingJobId!);
        if (!active) return;
        updateLearningDraft({
          progress: job.progress,
          method: job.inputMode === 'COLLECT_NEW' ? 'new' : 'existing',
          error: job.status === '실패' ? job.stage : undefined,
        });
        if (job.status === '완료') updateLearningDraft({ step: 6, progress: 100, error: undefined });
      } catch {
        if (active) updateLearningDraft({ error: '학습 상태를 불러오지 못했어요.' });
      }
    };
    void refreshJob();
    const timer = window.setInterval(() => void refreshJob(), 3000);
    return () => { active = false; window.clearInterval(timer); };
  }, [step, learningDraft.trainingJobId, updateLearningDraft]);
  useEffect(() => { window.scrollTo({ top: 0, behavior: 'instant' }); }, [step]);
  const toggleEpisode = (id: string) => setSelected((current) => current.includes(id) ? current.filter((item) => item !== id) : [...current, id]);
  const toggleAllReady = () => setSelected(() => allReadySelected ? [] : readyData.map(item => item.id));
  const stage = progress < 35 ? '데이터 전처리' : progress < 75 ? '동작 패턴 학습' : '결과 확인';
  const canContinueData = method === 'new'
    ? Boolean(selectedUmi?.status === '온라인' && Number.isInteger(expectedEpisodes)
        && expectedEpisodes >= (newSkillActive ? 1 : (requirements?.minimumEpisodeCount ?? 1))
        && expectedEpisodes <= (newSkillActive ? 100 : (requirements?.maximumEpisodeCount ?? 100)))
    : selectedData.length > 0;
  const submitTraining = async () => {
    const targetUmi = method === 'new' ? selectedUmi : umis.find(item => item.id === selectedData[0]?.umiId);
    if (!method || !targetUmi) { onToast('학습에 사용할 UMI를 확인할 수 없어요.'); return; }
    setSubmitting(true);
    try {
      const job = await createTrainingJob({
        ...(newSkillActive
          ? { newSkill: { displayName: customName } }
          : { skillKey }),
        umiDeviceId: targetUmi.deviceId,
        inputMode: method === 'new' ? 'COLLECT_NEW' : 'EXISTING_EPISODES',
        expectedEpisodeCount: method === 'new' ? expectedEpisodes : undefined,
        episodeIds: method === 'existing' ? selectedData.map(item => item.id) : undefined,
      });
      updateLearningDraft({
        skillKey: job.skillKey,
        progress: job.progress,
        step: 5,
        method: job.inputMode === 'COLLECT_NEW' ? 'new' : 'existing',
        startedAt: null,
        name: customName || job.name,
        trainingJobId: job.id,
        error: undefined,
      });
      onToast('학습 작업을 생성했어요.');
    } catch {
      onToast('학습 작업을 생성하지 못했어요.');
    } finally { setSubmitting(false); }
  };
  const cancelCurrentTraining = async () => {
    if (!learningDraft.trainingJobId) return;
    setCanceling(true);
    try {
      await cancelTrainingById(learningDraft.trainingJobId);
      resetLearningDraft();
      setCancelOpen(false);
      onToast('학습 취소를 요청했어요.');
      onNavigate('training');
    } catch {
      onToast('학습을 취소하지 못했어요. 다시 시도해 주세요.');
    } finally {
      setCanceling(false);
    }
  };
  const saveCompletedTraining = async () => {
    if (!learningDraft.trainingJobId || step !== 6 || !skill) return;
    setSavingResult(true);
    try {
      await completeLearning({ name: skill.name, robot: umiId });
      resetLearningDraft();
      onToast('완료된 학습을 저장했어요.');
      onNavigate('training');
    } catch {
      onToast('학습 결과를 저장하지 못했어요. 다시 시도해 주세요.');
    } finally {
      setSavingResult(false);
    }
  };
  const learningStepSummaries = [
    skill?.name ?? '',
    method === 'new' ? '새로 동작 보여주기' : method === 'existing' ? '기존 데이터 사용' : '',
    step > 3 ? '준비 완료' : '',
    step > 4 ? '확인 완료' : '',
    '',
  ];

  if (catalogLoading) {
    return <div className="center-panel"><section className="card state-card"><span className="spinner" aria-hidden="true" /><h2>학습 동작을 불러오는 중이에요</h2><p>잠시만 기다려 주세요.</p></section></div>;
  }

  if (catalogError) {
    return <div className="center-panel"><section className="card state-card"><span className="state-icon">!</span><h2>학습 동작을 불러오지 못했어요</h2><p>페이지를 새로고침해 다시 시도해 주세요.</p></section></div>;
  }

  return <>
    <div className="page-heading learning-heading title-divider"><div><h1>학습 생성</h1></div><span className="draft-label">{draftReady && draftStored ? '이 기기에 임시 저장됨' : '현재 화면에 보관 중'}</span></div>
    <p className="mobile-step-status" role="status">{Math.min(step, 5)} / 5 · {['동작 선택', '데이터 방식', '데이터 준비', '최종 확인', '학습 진행'][Math.min(step,5)-1]}</p><ol className="steps redesigned-steps" aria-label="학습 생성 단계">
      {['동작 선택', '데이터 방식', '데이터 준비', '최종 확인', '학습 진행'].map((label, index) => <li key={label} className={step === index + 1 || (step === 6 && index === 4) ? 'current' : step > index + 1 ? 'done' : ''}><span>{step > index + 1 ? '✓' : index + 1}</span><div><strong>{label}</strong>{learningStepSummaries[index] && <small>{learningStepSummaries[index]}</small>}</div></li>)}
    </ol>

    {step === 1 && <section className="card learning-wizard-card"><div className="wizard-title"><span>1</span><div><h2>학습할 동작을 선택하거나 새 이름을 입력해 주세요</h2></div></div><div className="skill-choice-grid">{displayedSkills.map((item) => <button key={item.key} className={!newSkillActive && skillKey === item.key ? 'selected' : ''} onClick={() => setSkillKey(item.key)}><span>{item.mark}</span><div><strong>{item.name}</strong><p>{item.description}</p></div><i>{!newSkillActive && skillKey === item.key ? '✓' : ''}</i></button>)}<div className={`new-skill-entry${newSkillActive ? ' selected' : ''}`}><span aria-hidden="true">＋</span><div><label htmlFor="new-skill-name">새로운 동작 학습하기</label><div className="new-skill-input-row"><input id="new-skill-name" maxLength={40} value={name} onFocus={() => { setNewSkillActive(true); setMethod('new'); }} onChange={(event) => { setNewSkillActive(true); updateLearningDraft({ name: event.target.value, method: 'new', selected: [] }); }} placeholder="학습 이름을 입력해 주세요" autoComplete="off" /></div></div></div></div><div className="wizard-actions"><span /><button className="primary-button" disabled={!skill} onClick={() => setStep(2)}>다음: 데이터 방식 →</button></div></section>}

    {step === 2 && <section className="card learning-wizard-card"><div className="wizard-title"><span>2</span><div><h2>학습 데이터를 어떻게 준비할까요?</h2></div></div><div className="method-choice-grid"><button className={method === 'new' ? 'selected' : ''} onClick={() => setMethod('new')}><span className="method-icon">＋</span><strong>새로 동작 보여주기</strong><p>온라인 UMI를 선택하고 로봇에게 새로운 동작을 보여줍니다.</p><ul><li>새 환경이나 동작을 학습할 때</li><li>UMI로 동작을 직접 보여줄 수 있을 때</li></ul></button><button disabled={newSkillActive} className={method === 'existing' ? 'selected' : ''} onClick={() => setMethod('existing')}><span className="method-icon">↻</span><strong>기존 데이터 사용</strong><p>{newSkillActive ? '새로운 동작은 먼저 데이터를 수집해야 합니다.' : '준비된 기존 데이터를 선택해 바로 학습합니다.'}</p><ul><li>같은 동작을 다시 학습할 때</li><li>기존 데이터를 조합하고 싶을 때</li></ul></button></div><div className="wizard-actions"><button className="secondary-button" onClick={() => setStep(1)}>← 이전</button><button className="primary-button" disabled={!method} onClick={() => setStep(3)}>다음: 데이터 준비 →</button></div></section>}

    {step === 3 && <section className="card learning-wizard-card"><div className="wizard-title"><span>3</span><div><h2>{method === 'new' ? '동작을 보여줄 UMI를 선택해 주세요' : '학습에 사용할 데이터를 선택해 주세요'}</h2></div></div>{method === 'new' ? <><div className="umi-choice-list">{umis.map((item) => <button key={item.id} disabled={item.status !== '온라인'} className={umiId === item.id ? 'selected' : ''} onClick={() => setUmiId(item.id)}><span className="device-visual umi">U</span><div><strong>{item.id}</strong><p>{item.place} · 최근 사용 {item.collected}</p></div><span className={`status-pill status-${item.status}`}><i />{item.status}</span></button>)}</div><label className="episode-count">예상 동작 횟수<input type="number" min={1} value={expectedEpisodes} onChange={(event) => setExpectedEpisodes(Number(event.target.value))} /></label></> : <div className="episode-select-list"><div className="episode-selection-toolbar"><span>학습 가능 {readyData.length}개</span><button className="text-button" type="button" disabled={!readyData.length} onClick={toggleAllReady}>{allReadySelected ? '전체 선택 해제' : '학습 가능한 데이터 전체 선택'}</button></div>{availableData.map((item) => { const available = item.trainable; return <label key={item.id} className={`${selected.includes(item.id) ? 'selected' : ''}${!available ? ' disabled' : ''}`}><input type="checkbox" disabled={!available} checked={selected.includes(item.id)} onChange={() => toggleEpisode(item.id)} /><span><strong>데이터 {String(item.seq).padStart(3, '0')}</strong><small>{item.date} · {item.duration} · {item.umiId}</small></span><em>{available ? '확인 완료 · 학습 가능' : recoveryActionLabel[item.recoveryAction]}</em></label>; })}{!availableData.length && <p className="table-empty">이 동작의 데이터가 없어요. 이전 단계에서 새로 동작 보여주기를 선택해 주세요.</p>}<div className="episode-selection-summary" role="status"><strong>{selectedData.length}개 선택됨</strong><span>{selectedData.length > 0 ? '선택한 데이터로 진행할 수 있어요.' : '데이터를 선택해 주세요.'}</span></div></div>}<div className="wizard-actions"><button className="secondary-button" onClick={() => setStep(2)}>← 이전</button><button className="primary-button" disabled={!canContinueData} onClick={() => setStep(4)}>다음: 최종 확인 →</button></div></section>}

    {step === 4 && <section className="card learning-wizard-card review-card"><div className="wizard-title"><span>4</span><div><h2>학습 내용을 확인해 주세요</h2></div></div><dl><div><dt>학습 동작</dt><dd>{skill.name}</dd></div><div><dt>데이터 방식</dt><dd>{method === 'new' ? '새 데이터 만들기' : '기존 데이터 사용'}</dd></div><div><dt>{method === 'new' ? '사용할 UMI' : '선택 데이터'}</dt><dd>{method === 'new' ? `${umiId} · ${expectedEpisodes}회 예정` : `${selected.length}개 데이터`}</dd></div></dl><div className="review-notice"><strong>생성하면 바로 데이터 준비를 시작합니다.</strong><p>{method === 'new' ? 'UMI로 동작을 보여주면 데이터 확인 후 학습이 시작돼요.' : '선택한 데이터 확인 후 학습이 시작돼요.'}</p></div><div className="wizard-actions"><button className="secondary-button" onClick={() => setStep(3)}>← 이전</button><button className="primary-button" disabled={submitting} onClick={() => void submitTraining()}>{submitting ? '생성 중...' : '학습 작업 생성'}</button></div></section>}

    {(step === 5 || step === 6) && <div className="progress-layout">
      <section className="card progress-card">
        <div className="progress-card-actions">{step === 5 && <button className="danger-button" disabled={canceling} onClick={() => setCancelOpen(true)}>학습 취소</button>}</div>
        <div className="learning-orbit"><span>{step === 6 ? '✓' : '✦'}</span></div>
        <p className="eyebrow">{step === 6 ? '학습 완료' : method === 'new' && progress < 25 ? '데이터 준비 중' : '학습 진행 중'}</p>
        <h2>{skill.name}</h2>
        {step === 6 && <p>학습 결과가 준비됐어요. 오른쪽의 학습 저장 버튼으로 결과를 등록해 주세요.</p>}
        <div className="progress-number">{step === 6 ? 100 : progress}<small>%</small></div>
        <div className="progress-track"><i style={{ width: `${step === 6 ? 100 : progress}%` }} /></div>
        <div className="progress-meta"><div><small>현재 단계</small><strong>{step === 6 ? '학습 완료' : method === 'new' && progress < 25 ? '데이터 확인' : stage}</strong></div><div><small>예상 남은 시간</small><strong>{step === 6 ? '완료' : `약 ${Math.max(1, Math.ceil((100 - progress) / 12))}분`}</strong></div><div><small>입력 방식</small><strong>{method === 'new' ? '새 데이터' : '기존 데이터'}</strong></div></div>
      </section>
      <section className="card log-card">
        <div className="log-heading"><div><h3>진행 기록</h3></div><span>{step === 6 ? '완료' : '실시간'}</span></div>
        {learningDraft.error && <div className="log-error" role="alert"><p>{learningDraft.error}</p><button className="secondary-button" onClick={() => onNavigate('training')}>학습 목록에서 다시 시도</button></div>}
        <div className="log-list"><p><time>10:52:08</time><span className="log-ok">완료</span>학습 작업 생성</p><p><time>10:52:14</time><span className="log-ok">완료</span>입력 데이터 확인</p>{step === 6 ? <p><time>완료</time><span className="log-ok">완료</span>학습 및 결과 확인 완료</p> : <p><time>10:53:02</time><span className="log-run">진행</span>{stage} 진행 중...</p>}</div>
        <div className="training-save-actions"><button className="primary-button" disabled={step !== 6 || savingResult} onClick={() => void saveCompletedTraining()}>{savingResult ? '저장 중...' : '학습 저장'}</button>{step !== 6 && <small>학습이 완료되면 저장할 수 있어요.</small>}</div>
      </section>
    </div>}
    {cancelOpen && <div className="modal-backdrop" onMouseDown={() => !canceling && setCancelOpen(false)}><section className="confirm-modal" role="dialog" aria-modal="true" aria-label="학습 취소 확인" onMouseDown={(event) => event.stopPropagation()}><div className="modal-heading"><div><h2>학습을 취소할까요?</h2></div><button className="close-button" disabled={canceling} onClick={() => setCancelOpen(false)} aria-label="닫기">×</button></div><div className="card-actions"><button className="secondary-button" disabled={canceling} onClick={() => setCancelOpen(false)}>계속 진행</button><button className="danger-button" disabled={canceling} onClick={() => void cancelCurrentTraining()}>{canceling ? '취소 중...' : '취소'}</button></div></section></div>}
  </>;
}

export function LegacyLearnView({ onToast, onGoWork }: { onToast: (message: string) => void; onGoWork: () => void }) {
  const { robots, datasets, completeLearning } = useAppStore();
  const [selected, setSelected] = useState<string[]>([]);
  const [step, setStep] = useState(1);
  const [skillName, setSkillName] = useState('빨간 블록 옮기기');
  const [robot, setRobot] = useState('RB-07');
  const [progress, setProgress] = useState(12);
  const [video, setVideo] = useState<Dataset | null>(null);
  const completionSaved = useRef(false);

  useEffect(() => {
    if (step !== 3 || progress >= 100) return;
    const timer = window.setInterval(() => setProgress((value) => Math.min(100, value + 4)), 650);
    return () => window.clearInterval(timer);
  }, [step, progress]);

  useEffect(() => {
    if (step === 3 && progress === 100) {
      if (!completionSaved.current) {
        completionSaved.current = true;
        void completeLearning({ name: skillName.trim(), robot });
      }
      const timer = window.setTimeout(() => setStep(4), 500);
      return () => window.clearTimeout(timer);
    }
  }, [completeLearning, progress, robot, skillName, step]);

  const toggle = (id: string) => setSelected((current) => current.includes(id) ? current.filter((item) => item !== id) : [...current, id]);
  const allSelected = selected.length === datasets.length;
  const stage = progress < 35 ? '데이터 전처리' : progress < 75 ? '동작 패턴 학습' : '결과 확인';

  return (
    <>
      <div className="page-heading">
        <div><p className="eyebrow">새로운 동작 학습</p><h1>로봇에게 새로운 동작을 가르쳐 볼까요?</h1><p>UMI로 추가한 데이터를 선택하면 나머지는 차근차근 안내해 드릴게요.</p></div>
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
              <div className="data-heading"><div className="section-title compact"><span className="number">2</span><div><h2>학습할 데이터를 선택해 주세요</h2><p>총 {datasets.length}개의 데이터를 찾았어요.</p></div></div><span className="updated">마지막 조회 10:48</span></div>
              <div className="table-wrap">
                <table><thead><tr><th><input type="checkbox" checked={allSelected} onChange={() => setSelected(allSelected ? [] : datasets.map((item) => item.id))} aria-label="전체 선택" /></th><th>영상</th><th>추가 일시</th><th>촬영 시간</th><th>파일 크기</th><th /></tr></thead>
                  <tbody>{datasets.map((item) => (
                    <tr key={item.id} className={selected.includes(item.id) ? 'selected' : ''} onDoubleClick={() => setVideo(item)}>
                      <td><input type="checkbox" checked={selected.includes(item.id)} onChange={() => toggle(item.id)} aria-label={`${item.date} 데이터 선택`} /></td>
                      <td><button className={`thumbnail ${item.tone}`} onClick={() => setVideo(item)} aria-label="영상 보기"><span>▶</span></button></td>
                      <td><strong>{item.date}</strong><small>영상 미리보기</small></td><td>{item.duration}</td><td>{item.size}</td><td><button className="more-button" aria-label="데이터 메뉴">•••</button></td>
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
            <div className="form-grid"><label>동작 이름<input value={skillName} onChange={(e) => setSkillName(e.target.value)} placeholder="예: 빨간 블록 옮기기" /><small>작업 탭의 블록 이름으로 사용돼요.</small></label><label>학습에 사용할 로봇<select value={robot} onChange={(e) => setRobot(e.target.value)}>{robots.filter((item) => item.status !== '오프라인').map((item) => <option key={item.id} value={item.id}>{item.name} · {item.status}</option>)}</select><small>현재 연결 가능한 로봇만 표시돼요.</small></label></div>
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
          <section className="card log-card"><div className="log-heading"><div><h3>학습 로그</h3><p>서버에서 전달되는 진행 정보를 보여드려요.</p></div><span>실시간</span></div><div className="log-list"><p><time>10:52:08</time><span className="log-ok">완료</span>데이터 확인 완료 ({selected.length}개)</p><p><time>10:52:14</time><span className="log-ok">완료</span>영상 프레임 추출 완료</p><p><time>10:53:02</time><span className="log-run">진행</span>{stage} 진행 중...</p></div></section>
        </div>
      )}

      {step === 4 && (
        <div className="center-panel"><section className="card complete-card"><span className="complete-icon">✓</span><p className="eyebrow">학습 완료</p><h2>새로운 동작을 모두 배웠어요</h2><p>이제 작업 탭에서 <strong>{skillName}</strong> 블록을 사용할 수 있어요.</p><div className="complete-summary"><div><small>동작 이름</small><strong>{skillName}</strong></div><div><small>학습 데이터</small><strong>{selected.length}개</strong></div><div><small>대상 로봇</small><strong>{robot}</strong></div><div><small>완료 시각</small><strong>방금 전</strong></div></div><div className="complete-actions"><button className="secondary-button" onClick={() => { completionSaved.current = false; setProgress(12); setStep(1); setSelected([]); }}>새 학습 시작</button><button className="primary-button" onClick={onGoWork}>작업에서 사용하기 →</button></div></section></div>
      )}

      {video && <div className="modal-backdrop" onMouseDown={() => setVideo(null)}><section className="video-modal" role="dialog" aria-modal="true" aria-label="데이터 영상 재생" onMouseDown={(e) => e.stopPropagation()}><div className="modal-heading"><div><h2>데이터 영상 미리보기</h2><p>{video.date} · {video.duration} · {video.size}</p></div><button className="close-button" onClick={() => setVideo(null)} aria-label="닫기">×</button></div><div className={`video-stage ${video.tone}`}><span className="big-play">▶</span><div className="video-controls"><span>▶</span><i><b /></i><time>00:37 / {video.duration}</time></div></div><p className="modal-note">서버에서 영상을 모두 불러왔어요.</p></section></div>}
    </>
  );
}

function WorkflowLibraryView({ onEdit, onToast }: { onEdit: (workId: string) => void; onToast: (message: string) => void }) {
  const { robots, savedWorks, deleteWorksById } = useAppStore();
  const [mobileDetail, setMobileDetail] = useState(false);
  const [query, setQuery] = useState('');
  const [selectedWorkId, setSelectedWorkId] = useState(savedWorks[0]?.id ?? '');
  const initialWork = savedWorks.find((work) => work.id === selectedWorkId) ?? savedWorks[0];
  const [robotId, setRobotId] = useState(initialWork?.robotId || robots[0]?.id || '');
  const [executing, setExecuting] = useState(false);
  const [selectingWorks, setSelectingWorks] = useState(false);
  const [selectedWorkIds, setSelectedWorkIds] = useState<string[]>([]);
  const [deleteWorksOpen, setDeleteWorksOpen] = useState(false);
  const [deletingWorks, setDeletingWorks] = useState(false);

  const filteredWorks = savedWorks.filter((work) => {
    const blocks = flattenWorkflowBlocks(work.blocks);
    const normalizedQuery = query.trim().toLocaleLowerCase('ko-KR');
    return !normalizedQuery || [work.name, ...blocks.map((block) => block.title)].some((value) => value.toLocaleLowerCase('ko-KR').includes(normalizedQuery));
  });

  const selectedWork = savedWorks.find((work) => work.id === selectedWorkId) ?? savedWorks[0];
  const selectedRobot = robots.find((robot) => robot.id === robotId);
  const blocks = selectedWork ? flattenWorkflowBlocks(selectedWork.blocks) : [];
  const skillCount = blocks.filter(isSkillBlock).length;
  const controlCount = blocks.length - skillCount;
  const workflowIssues = selectedWork ? getWorkflowIssues(selectedWork) : ['작업을 선택해 주세요.'];
  const robotReady = selectedRobot?.status === '온라인';
  const deployment = useTaskDeployment(selectedWork, robotId);
  const canExecute = deployment.ready && !!selectedWork && !!selectedRobot && robotReady && !workflowIssues.length && !executing;

  const selectWork = (work: SavedWork) => {
    setMobileDetail(true);
    window.scrollTo({ top: 0, behavior: 'instant' });
    setSelectedWorkId(work.id);
    setRobotId(robots.some((robot) => robot.id === work.robotId) ? work.robotId : (robots.find((robot) => robot.status === '온라인')?.id ?? robots[0]?.id ?? ''));
    setExecuting(false);
  };

  const executeSelectedWork = async () => {
    if (!selectedWork || !canExecute) return;
    try {
      await deployment.execute();
      setExecuting(true);
      onToast(`‘${selectedWork.name}’ 작업 실행을 요청했어요.`);
    } catch (cause) {
      onToast(cause instanceof Error ? cause.message : '작업을 시작하지 못했어요.');
    }
  };
  const deleteSelectedWorks = async () => {
    setDeletingWorks(true);
    try {
      await deleteWorksById(selectedWorkIds);
      onToast(`${selectedWorkIds.length}개 작업을 삭제했어요.`);
      setSelectedWorkIds([]); setSelectingWorks(false); setDeleteWorksOpen(false);
    } catch { onToast('삭제 API가 아직 연결되지 않았거나 삭제에 실패했어요.'); }
    finally { setDeletingWorks(false); }
  };

  if (!selectedWork) {
    return <div className="center-panel"><section className="card state-card"><span className="state-icon">i</span><h2>저장된 작업이 없어요</h2><p>첫 작업을 만들어 로봇이 할 일을 구성해 보세요.</p><button className="primary-button" onClick={() => onEdit('')}>작업 생성하기</button></section></div>;
  }

  return <>
    <div className="page-heading library-page-heading title-divider"><div><h1>작업 목록</h1></div></div>

    <section className="workflow-library-toolbar" aria-label="작업 검색">
      {selectingWorks && <div className="selection-summary"><input className="list-select-checkbox" type="checkbox" aria-label="작업 전체 선택" disabled={!filteredWorks.length} checked={filteredWorks.length > 0 && selectedWorkIds.length === filteredWorks.length} onChange={() => setSelectedWorkIds(selectedWorkIds.length === filteredWorks.length ? [] : filteredWorks.map(work => work.id))} /><span>{selectedWorkIds.length}개 선택됨</span></div>}
      <label className="workflow-search"><span aria-hidden="true">⌕</span><input value={query} onChange={(event) => setQuery(event.target.value)} placeholder="작업명 또는 포함된 블록 검색" aria-label="저장된 작업 검색" /></label>
      <button className={`${selectingWorks ? 'danger-button' : 'secondary-button'} list-delete-button`} onClick={() => { if (!selectingWorks) setSelectingWorks(true); else if (selectedWorkIds.length) setDeleteWorksOpen(true); else setSelectingWorks(false); }}>삭제</button>
      <button className="primary-button page-create-button" onClick={() => onEdit('')}>새 작업 생성 <span aria-hidden="true">›</span></button>
    </section>

    <div className={`workflow-library-layout${mobileDetail ? ' mobile-detail-open' : ''}`}>
      <section className="card saved-work-list" aria-label="저장된 작업 목록">
        <div className="saved-work-columns" aria-hidden="true"><span>작업</span><span>예상 시간</span><span>최근 수정일</span><span className="saved-work-total">전체 {savedWorks.length}개</span></div>
        {filteredWorks.length ? filteredWorks.map((work) => {
          const workBlocks = flattenWorkflowBlocks(work.blocks);
          const workSkills = workBlocks.filter(isSkillBlock).length;
          const workControls = workBlocks.length - workSkills;
          return <div key={work.id} className="saved-work-select-row">{selectingWorks && <input className="list-select-checkbox" type="checkbox" aria-label={`${work.name} 선택`} checked={selectedWorkIds.includes(work.id)} onChange={() => setSelectedWorkIds(current => current.includes(work.id) ? current.filter(id => id !== work.id) : [...current, work.id])} />}<button className="saved-work-row" onClick={() => selectingWorks ? setSelectedWorkIds(current => current.includes(work.id) ? current.filter(id => id !== work.id) : [...current, work.id]) : selectWork(work)}>
            <span className="saved-work-main"><i>W</i><span><strong>{work.name}</strong><small>{workSkills ? `학습 동작 ${workSkills}개` : '학습 동작 없음'} · 제어 블록 {workControls}개</small><em>{workBlocks.slice(0, 3).map((block) => block.title).join(' · ')}{workBlocks.length > 3 ? ` 외 ${workBlocks.length - 3}개` : ''}</em></span></span>
            <span className="saved-work-duration">{formatWorkDuration(work.estimatedSeconds)}</span>
            <span className="saved-work-updated">{formatUpdatedAt(work.updatedAt)}</span>
            <span className="saved-work-chevron">›</span>
          </button></div>;
        }) : <div className="library-empty"><strong>검색 결과가 없어요</strong><p>다른 작업명이나 동작을 검색해 보세요.</p></div>}
      </section>

      <aside className="card saved-work-detail"><button className="text-button mobile-detail-back" onClick={() => setMobileDetail(false)}>← 작업 목록</button>
        <div className="saved-work-detail-head"><div><p className="eyebrow">작업 상세</p><h2>{selectedWork.name}</h2><span>{selectedWork.id} · {formatUpdatedAt(selectedWork.updatedAt)} 수정</span></div><button className="secondary-button" onClick={() => onEdit(selectedWork.id)}>수정하기</button></div>
        <div className="work-summary-metrics"><div><small>학습된 동작</small><strong>{skillCount}<em>개</em></strong></div><div><small>제어 블록</small><strong>{controlCount}<em>개</em></strong></div><div><small>예상 실행 시간</small><strong>{formatWorkDuration(selectedWork.estimatedSeconds).replace('약 ', '')}</strong></div></div>

        <section className="detail-section"><div className="detail-section-title"><div><h3>작업 흐름</h3></div><span>{blocks.length}개 블록</span></div><div className="library-workflow-flow">
          {selectedWork.blocks.map((block, index) => <article key={block.id} className="library-flow-block"><span className="flow-order">{index + 1}</span><span className={`block-mark ${block.kind}`}>{blockCatalog.find((item) => item.kind === block.kind)?.mark}</span><div><strong>{block.title}</strong><small>{isSkillBlock(block) ? '학습된 단일 동작' : block.kind === 'repeat' ? `${block.repeat ?? 1}회 반복 · 제어 블록` : `${block.motor} ${block.angle}° · 제어 블록`}</small>{block.children?.length ? <p>{block.children.map((child) => child.title).join(' → ')}</p> : null}</div><em className={isSkillBlock(block) ? 'skill-label' : 'control-label'}>{isSkillBlock(block) ? '학습 동작' : '제어'}</em></article>)}
        </div></section>

        <section className="detail-section execution-setup"><div className="detail-section-title"><div><h3>실행 설정</h3><p>실행할 로봇을 선택하고 준비 상태를 확인하세요.</p></div></div><label>실행할 로봇<select value={robotId} disabled={executing} onChange={(event) => { setRobotId(event.target.value); setExecuting(false); }}>{robots.map((robot) => <option key={robot.id} value={robot.id}>{robot.name} · {robot.status}</option>)}</select></label>
          <div className="preflight-grid"><div className={robotReady ? 'check-pass' : 'check-fail'}><span>{robotReady ? '✓' : '!'}</span><div><strong>로봇 상태</strong><small>{selectedRobot ? `${selectedRobot.name} · ${selectedRobot.status}` : '로봇을 선택해 주세요.'}</small></div></div><div className={!workflowIssues.length ? 'check-pass' : 'check-fail'}><span>{!workflowIssues.length ? '✓' : '!'}</span><div><strong>작업 유효성</strong><small>{workflowIssues[0] ?? `학습 동작 ${skillCount}개와 제어 블록 ${controlCount}개를 실행할 수 있어요.`}</small></div></div></div>
          <div className="deployment-status" role="status"><strong>{deployment.pending ? '배포 중' : deployment.ready ? `작업 v${deployment.version} · 실행 가능` : '배포 필요'}</strong><p>{deployment.ready ? `${selectedRobot?.name}에 현재 작업 버전이 준비됐어요.` : '현재 작업 구성을 확인하고 선택한 로봇에 배포하세요.'}</p>{deployment.error && <p role="alert">{deployment.error}</p>}{!deployment.ready && <button className="secondary-button" disabled={!robotReady || !!workflowIssues.length || deployment.pending} onClick={() => void deployment.deploy()}>{deployment.pending ? '배포 중...' : deployment.error ? '배포 다시 시도' : '작업 버전 배포'}</button>}</div>
          <div className="execution-actions"><button className="secondary-button" onClick={() => onEdit(selectedWork.id)}>수정하기</button><button className="primary-button" disabled={!canExecute} onClick={() => void executeSelectedWork()}>{executing ? '작업 실행 요청됨' : '▶ 작업 실행'}</button></div>
          {!robotReady && <p className="execution-disabled-reason">온라인 상태의 로봇에서만 작업을 실행할 수 있어요.</p>}
          {executing && <div className="library-running"><i /><span>선택한 로봇에서 작업을 실행하고 있어요.</span><button className="danger-button" onClick={() => void deployment.cancel().then(() => { setExecuting(false); onToast('작업 중지를 요청했어요.'); })}>작업 중지</button></div>}
        </section>
      </aside>
    </div>
    {deleteWorksOpen && <div className="modal-backdrop" onMouseDown={() => !deletingWorks && setDeleteWorksOpen(false)}><section className="confirm-modal" role="dialog" aria-modal="true" aria-label="작업 선택 삭제 확인" onMouseDown={(e) => e.stopPropagation()}><div className="modal-heading"><div><h2>선택한 작업을 삭제할까요?</h2><p>{selectedWorkIds.length}개 작업이 삭제됩니다.</p></div><button className="close-button" onClick={() => setDeleteWorksOpen(false)} aria-label="닫기">×</button></div><div className="card-actions"><button className="secondary-button" disabled={deletingWorks} onClick={() => setDeleteWorksOpen(false)}>돌아가기</button><button className="danger-button" disabled={deletingWorks} onClick={() => void deleteSelectedWorks()}>{deletingWorks ? '삭제 중...' : '삭제'}</button></div></section></div>}
  </>;
}

function WorkView({ editingWorkId, suggestedSkillName, onNavigate, onToast }: { editingWorkId: string | null; suggestedSkillName: string | null; onNavigate: (view: View) => void; onToast: (message: string) => void }) {
  const { robots, skills, savedWorks, saveWorkflow } = useAppStore();
  const editingWork = savedWorks.find((work) => work.id === editingWorkId);
  const [robotId, setRobotId] = useState(editingWork?.robotId || robots[0]?.id || '');
  const [blocks, setBlocks] = useState<WorkflowBlock[]>(() => structuredClone(
    editingWork?.blocks ?? (suggestedSkillName && skills.find(skill => skill.name === suggestedSkillName)
      ? [makeSkillBlock(skills.find(skill => skill.name === suggestedSkillName)!)]
      : skills.slice(0, 1).map(makeSkillBlock)),
  ));
  const [runningIndex, setRunningIndex] = useState(-1);
  const [workName, setWorkName] = useState(editingWork?.name ?? '');
  const [selectedWorkId, setSelectedWorkId] = useState(editingWork?.id ?? '');
  const [nameError, setNameError] = useState('');
  const [saving, setSaving] = useState(false);
  const [workStep, setWorkStep] = useState<1 | 2 | 3>(1);

  const selectedRobot = robots.find((robot) => robot.id === robotId);
  const running = runningIndex >= 0;
  const workForValidation: SavedWork = { id: selectedWorkId || 'draft', name: workName, robotId, blocks, estimatedSeconds: estimateWorkflowSeconds(blocks), updatedAt: '' };
  const unavailableSkills = flattenWorkflowBlocks(blocks).filter((block) => block.skillId && !skills.some((skill) => skill.id === block.skillId));
  const workflowIssues = [...getWorkflowIssues(workForValidation), ...unavailableSkills.map((block) => `‘${block.title}’ 학습 동작을 사용할 수 없어요.`)];
  const robotReady = selectedRobot?.status === '온라인';
  const savedSnapshot = savedWorks.find(work => work.id === selectedWorkId);
  const isSaved = !!savedSnapshot
    && savedSnapshot.name === workName.trim()
    && workflowContentEquals(savedSnapshot.blocks, blocks);
  const deployment = useTaskDeployment(workForValidation, robotId);
  const canStartRun = isSaved && robotReady && !workflowIssues.length && deployment.ready;

  const save = async () => {
    if (!workName.trim()) { setNameError('작업 이름을 입력해 주세요.'); return false; }
    const normalizedName = workName.trim().toLocaleLowerCase('ko-KR');
    if (savedWorks.some((work) => work.name.trim().toLocaleLowerCase('ko-KR') === normalizedName)) {
      setNameError('이미 저장된 작업 이름입니다. 다른 이름을 입력해 주세요.');
      return false;
    }
    if (workflowIssues.length) { setNameError(workflowIssues[0]); return false; }
    setNameError('');
    setSaving(true);
    try {
      const saved = await saveWorkflow({ id: `draft-${crypto.randomUUID()}`, name: workName.trim(), robotId, blocks, estimatedSeconds: estimateWorkflowSeconds(blocks), updatedAt: new Date().toISOString() });
      setSelectedWorkId(saved.id);
      onToast(`‘${workName.trim()}’ 작업을 저장했어요.`);
      return true;
    } catch (cause) {
      setNameError(cause instanceof Error ? cause.message : '저장하지 못했어요. 다시 시도해 주세요.');
      return false;
    }
    finally { setSaving(false); }
  };

  const loadWork = (workId: string) => {
    const work = savedWorks.find((item) => item.id === workId);
    if (!work) return;
    const loaded = structuredClone(work.blocks);
    /* 불러온 블록 ID와 새 블록 ID가 겹치지 않게 카운터를 끌어올린다. */
    const walk = (items: WorkflowBlock[]) => items.forEach((block) => { nextBlockId = Math.max(nextBlockId, block.id + 1); if (block.children) walk(block.children); });
    walk(loaded);
    setBlocks(loaded);
    setWorkName(work.name);
    if (robots.some((robot) => robot.id === work.robotId)) setRobotId(work.robotId);
    setSelectedWorkId(workId);
    setNameError('');
    onToast(`‘${work.name}’ 작업을 불러왔어요.`);
  };

  const startRun = async () => {
    if (!workName.trim()) { setNameError('작업 이름을 입력해 주세요.'); return; }
    if (!canStartRun) return;
    setNameError('');
    try {
      await deployment.execute();
      setRunningIndex(0);
      onToast('로봇에 작업 실행을 요청했어요.');
    } catch (cause) {
      setNameError(cause instanceof Error ? cause.message : '작업을 시작하지 못했어요.');
    }
  };

  const addBlock = (kind: BlockKind, repeatId?: number) => {
    const block = makeBlock(kind);
    if (repeatId) {
      if (kind === 'repeat') return;
      setBlocks((items) => items.map((item) => item.id === repeatId ? { ...item, children: [...(item.children ?? []), block] } : item));
    } else setBlocks((items) => [...items, block]);
  };

  const addSkillBlock = (skill: LearnedSkill, repeatId?: number) => {
    const block = makeSkillBlock(skill);
    if (repeatId) {
      setBlocks((items) => items.map((item) => item.id === repeatId ? { ...item, children: [...(item.children ?? []), block] } : item));
    } else setBlocks((items) => [...items, block]);
  };

  const updateBlock = (id: number, patch: Partial<WorkflowBlock>) => {
    const updateItems = (items: WorkflowBlock[]): WorkflowBlock[] => items.map((item) => {
      if (item.id === id) return { ...item, ...patch };
      if (item.children?.length) return { ...item, children: updateItems(item.children) };
      return item;
    });
    setBlocks(updateItems);
  };
  const removeBlock = (id: number) => setBlocks((items) => items.filter((item) => item.id !== id));
  const moveBlock = (index: number, direction: -1 | 1) => {
    const target = index + direction;
    if (target < 0 || target >= blocks.length) return;
    setBlocks((items) => { const next = [...items]; [next[index], next[target]] = [next[target], next[index]]; return next; });
  };
  const dropKind = (event: React.DragEvent, repeatId?: number) => {
    event.preventDefault(); event.stopPropagation();
    const skillId = event.dataTransfer.getData('skill-id');
    const skill = skills.find((item) => item.id === skillId);
    if (skill) { addSkillBlock(skill, repeatId); return; }
    const kind = event.dataTransfer.getData('block-kind') as BlockKind;
    if (kind) addBlock(kind, repeatId);
  };
  const removeChildBlock = (repeatId: number, childId: number) => setBlocks((items) => items.map((item) => item.id === repeatId ? { ...item, children: (item.children ?? []).filter((child) => child.id !== childId) } : item));
  const moveChildBlock = (repeatId: number, childIndex: number, direction: -1 | 1) => setBlocks((items) => items.map((item) => {
    if (item.id !== repeatId) return item;
    const children = [...(item.children ?? [])];
    const target = childIndex + direction;
    if (target < 0 || target >= children.length) return item;
    [children[childIndex], children[target]] = [children[target], children[childIndex]];
    return { ...item, children };
  }));

  if (!selectedRobot) {
    return <><div className="page-heading work-heading title-divider"><div><h1>작업 생성</h1></div></div><div className="center-panel"><section className="card state-card"><span className="state-icon">R</span><h2>사용할 로봇이 없어요</h2><p>작업을 만들기 전에 로봇을 등록해 주세요.</p><button className="primary-button" onClick={() => onNavigate('devices')}>장치 관리로 이동</button></section></div></>;
  }

  return (
    <>
      <div className="page-heading work-heading title-divider"><div><h1>작업 생성</h1></div></div>
      <ol className="work-stage-steps" aria-label="작업 생성 단계">
        {['작업 구성', '실행 준비', '실행'].map((label, index) => <li key={label} className={workStep === index + 1 ? 'current' : workStep > index + 1 ? 'done' : ''}><span>{workStep > index + 1 ? '✓' : index + 1}</span><strong>{label}</strong></li>)}
      </ol>

      {workStep === 1 && <>
        <div className="work-namebar"><label>작업 이름<input value={workName} disabled={running} onChange={(e) => { setWorkName(e.target.value); if (nameError && e.target.value.trim()) setNameError(''); }} />{nameError && <small className="field-error" role="alert">{nameError}</small>}</label><label>저장된 작업<select disabled={running} value={selectedWorkId} onChange={(e) => loadWork(e.target.value)}><option value="" disabled>작업 불러오기</option>{savedWorks.map((work) => <option key={work.id} value={work.id}>{work.name}</option>)}</select></label><button className="secondary-button" disabled={running || saving} onClick={() => void save()}>{saving ? '저장 중...' : '저장'}</button></div>
        <div className="block-workspace work-compose-layout">
        <aside className="block-library">
          <div className="library-heading"><h2>작업 블록</h2></div>
          <p className="block-category">학습된 동작 <span>{skills.length}</span></p>
          {skills.length ? skills.map((skill) => <button key={skill.id} className={`library-block ${skill.kind}`} draggable={!running} onDragStart={(e) => e.dataTransfer.setData('skill-id', skill.id)} onClick={() => !running && addSkillBlock(skill)}><span>{skill.mark}</span><div><strong>{skill.name}</strong></div><em>＋</em></button>) : <div className="block-library-empty"><p>사용할 수 있는 학습 동작이 없어요.</p><button className="text-button" onClick={() => onNavigate('learn')}>학습 생성하기 →</button></div>}
          <details className="advanced-blocks">
            <summary>고급 블록</summary>
            <p className="block-category">직접 제어</p>
            {blockCatalog.filter((item) => item.kind === 'motor').map((item) => <button key={item.kind} className={`library-block ${item.kind}`} draggable={!running} onDragStart={(e) => e.dataTransfer.setData('block-kind', item.kind)} onClick={() => !running && addBlock(item.kind)}><span>{item.mark}</span><div><strong>{item.title}</strong></div><em>＋</em></button>)}
            <p className="block-category">흐름 제어</p>
            {blockCatalog.filter((item) => item.kind === 'repeat').map((item) => <button key={item.kind} className={`library-block ${item.kind}`} draggable={!running} onDragStart={(e) => e.dataTransfer.setData('block-kind', item.kind)} onClick={() => !running && addBlock(item.kind)}><span>{item.mark}</span><div><strong>{item.title}</strong></div><em>＋</em></button>)}
          </details>
        </aside>

        <section className="canvas" onDragOver={(e) => e.preventDefault()} onDrop={(e) => dropKind(e)}>
          <div className="canvas-head"><div><h2>작업 흐름</h2></div><span>{blocks.length}개 블록</span></div>
          <div className="flow"><div className="start-block"><span>▶</span><div><strong>작업 시작</strong></div></div><div className="connector" />
            {blocks.map((block, index) => <div className="flow-unit" key={block.id}><WorkflowBlockCard block={block} active={runningIndex === index} disabled={running} onUpdate={updateBlock} onRemove={removeBlock} onMove={(direction) => moveBlock(index, direction)} onDropChild={(event) => dropKind(event, block.id)} onRemoveChild={(childId) => removeChildBlock(block.id, childId)} onMoveChild={(childIndex, direction) => moveChildBlock(block.id, childIndex, direction)} /><div className="connector" /></div>)}
            <div className="drop-zone"><span>＋</span>이곳에 작업 블록을 놓아주세요</div>
          </div>
        </section>
        </div>
        <div className="work-stage-actions"><button className="text-button" onClick={() => onNavigate('library')}>← 작업 목록</button><button className="primary-button" disabled={saving || !workName.trim() || !!workflowIssues.length} onClick={async () => { if (isSaved || await save()) { setWorkStep(2); window.scrollTo({ top: 0, behavior: 'smooth' }); } }}>{saving ? '저장 중...' : '저장 후 실행 준비 →'}</button></div>
      </>}

      {workStep === 2 && <section className="card work-stage-card">
        <div className="work-stage-heading"><div><p className="eyebrow">실행 준비</p><h2>{workName || '이름 없는 작업'}</h2><p>실행할 로봇과 작업 상태를 확인한 뒤 배포하세요.</p></div><span>{blocks.length}개 블록 · {formatWorkDuration(estimateWorkflowSeconds(blocks))}</span></div>
        <div className="work-tools stage-robot-select"><label>실행할 로봇<select value={robotId} disabled={running} onChange={(e) => setRobotId(e.target.value)}>{robots.slice(0, 3).map((robot) => <option key={robot.id} value={robot.id}>{robot.name} · {robot.status}</option>)}</select></label><span className={`robot-status status-${selectedRobot.status.replace(' ', '-')}`}><i />{selectedRobot.status}</span></div>
        <div className="preflight-grid stage-preflight"><div className={robotReady ? 'check-pass' : 'check-fail'}><span>{robotReady ? '✓' : '!'}</span><div><strong>로봇 상태</strong><small>{selectedRobot.name} · {selectedRobot.status}</small></div></div><div className={!workflowIssues.length ? 'check-pass' : 'check-fail'}><span>{!workflowIssues.length ? '✓' : '!'}</span><div><strong>작업 유효성</strong><small>{workflowIssues[0] ?? `${flattenWorkflowBlocks(blocks).length}개 블록을 실행할 수 있어요.`}</small></div></div><div className={isSaved ? 'check-pass' : 'check-fail'}><span>{isSaved ? '✓' : '!'}</span><div><strong>저장 상태</strong><small>{isSaved ? '최신 작업이 저장되어 있어요.' : '작업을 다시 저장해 주세요.'}</small></div></div></div>
        <div className="deployment-status"><strong>{deployment.ready ? `작업 v${deployment.version} · 실행 가능` : deployment.pending ? '배포 중' : '배포 필요'}</strong><p>{deployment.ready ? `${selectedRobot.name}에 현재 작업 버전이 준비됐어요.` : '확인을 마치고 선택한 로봇에 작업 버전을 배포하세요.'}</p>{deployment.error && <p role="alert">{deployment.error}</p>}{!deployment.ready && <button className="secondary-button" disabled={!isSaved || !robotReady || !!workflowIssues.length || deployment.pending} onClick={() => void deployment.deploy()}>{deployment.pending ? '배포 중...' : deployment.error ? '배포 다시 시도' : '작업 버전 배포'}</button>}</div>
        <div className="safety-note"><span>!</span><p><strong>실행 전 확인해 주세요</strong>로봇 주변에 사람이나 장애물이 없는지 확인하세요.</p></div>
        <div className="work-stage-actions inside"><button className="secondary-button" onClick={() => setWorkStep(1)}>← 작업 구성</button><button className="primary-button" disabled={!deployment.ready} onClick={() => { setWorkStep(3); window.scrollTo({ top: 0, behavior: 'smooth' }); }}>실행 단계로 →</button></div>
      </section>}

      {workStep === 3 && <section className="card work-stage-card execution-stage">
        <div className="work-stage-heading"><div><p className="eyebrow">작업 실행</p><h2>{workName}</h2><p>{selectedRobot.name}에서 작업을 실행합니다.</p></div><span>{formatWorkDuration(estimateWorkflowSeconds(blocks))}</span></div>
        <div className="run-robot"><span className="robot-illustration">R</span><div><strong>{selectedRobot.name}</strong><small>{selectedRobot.location}</small></div><span className={`status-dot ${robotReady ? 'online' : 'busy'}`} /></div>
        {running ? <div className="execution-focus"><strong>{deployment.execution?.currentStep ?? 0} / {blocks.length}</strong><p>로봇이 작업 실행 요청을 처리하고 있어요.</p><div className="mini-progress"><i style={{ width: `${deployment.execution?.progressPercent ?? 0}%` }} /></div><button className="danger-button" onClick={() => void deployment.cancel().then(() => { setRunningIndex(-1); onToast('작업 중지를 요청했어요.'); })}>■ 작업 중지</button></div> : <div className="execution-focus"><strong>실행 준비 완료</strong><p>작업 v{deployment.version}이 배포되었습니다. 주변 안전을 확인하고 시작하세요.</p><button className="primary-button run-button" disabled={!canStartRun} onClick={() => void startRun()}>▶ 작업 시작</button></div>}
        <div className="work-stage-actions inside"><button className="secondary-button" disabled={running} onClick={() => setWorkStep(2)}>← 실행 준비</button><button className="text-button" disabled={running} onClick={() => onNavigate('library')}>작업 목록으로</button></div>
      </section>}
    </>
  );
}

function MotorSettings({ block, disabled, nested = false, onUpdate }: { block: WorkflowBlock; disabled: boolean; nested?: boolean; onUpdate: (id: number, patch: Partial<WorkflowBlock>) => void }) {
  const range = motorRanges[block.motor ?? 'J1'];
  return <div className={`motor-config${nested ? ' nested-motor-config' : ''}`}>
    <label>모터<select value={block.motor} disabled={disabled} onChange={(e) => onUpdate(block.id, { motor: e.target.value, angle: 0 })}>{Object.keys(motorRanges).map((motor) => <option key={motor}>{motor}</option>)}</select></label>
    <label className="angle-field">회전 각도 <span>{block.angle}°</span><input aria-label={`${block.title} 회전 각도`} type="range" min={range[0]} max={range[1]} value={block.angle} disabled={disabled} onChange={(e) => onUpdate(block.id, { angle: Number(e.target.value) })} /><small>{range[0]}° <b>{range[1]}°</b></small></label>
  </div>;
}

function NestedWorkflowBlockCard({ block, index, count, disabled, onUpdate, onRemove, onMove }: { block: WorkflowBlock; index: number; count: number; disabled: boolean; onUpdate: (id: number, patch: Partial<WorkflowBlock>) => void; onRemove: (id: number) => void; onMove: (index: number, direction: -1 | 1) => void }) {
  const catalog = blockCatalog.find((item) => item.kind === block.kind)!;
  return <article className={`nested-workflow-block ${block.kind}`}>
    <div className="nested-block-head">
      <span className="block-mark">{catalog.mark}</span>
      <div><strong>{block.title}</strong></div>
      <div className="block-actions">
        <button disabled={disabled || index === 0} onClick={() => onMove(index, -1)} aria-label={`${block.title} 위로 이동`}>↑</button>
        <button disabled={disabled || index === count - 1} onClick={() => onMove(index, 1)} aria-label={`${block.title} 아래로 이동`}>↓</button>
        <button disabled={disabled} onClick={() => onRemove(block.id)} aria-label={`${block.title} 삭제`}>×</button>
      </div>
    </div>
    {block.kind === 'motor' && <MotorSettings block={block} disabled={disabled} nested onUpdate={onUpdate} />}
  </article>;
}

function WorkflowBlockCard({ block, active, disabled, onUpdate, onRemove, onMove, onDropChild, onRemoveChild, onMoveChild }: { block: WorkflowBlock; active: boolean; disabled: boolean; onUpdate: (id: number, patch: Partial<WorkflowBlock>) => void; onRemove: (id: number) => void; onMove: (direction: -1 | 1) => void; onDropChild: (event: React.DragEvent) => void; onRemoveChild: (id: number) => void; onMoveChild: (index: number, direction: -1 | 1) => void }) {
  const catalog = blockCatalog.find((item) => item.kind === block.kind)!;
  return <div className={`workflow-block ${block.kind} ${active ? 'executing' : ''}`}><div className="block-top"><span className="block-mark">{catalog.mark}</span><div><strong>{block.title}</strong></div>{active && <em className="executing-badge">실행 중</em>}<div className="block-actions"><button disabled={disabled} onClick={() => onMove(-1)} aria-label="위로 이동">↑</button><button disabled={disabled} onClick={() => onMove(1)} aria-label="아래로 이동">↓</button><button disabled={disabled} onClick={() => onRemove(block.id)} aria-label="삭제">×</button></div></div>
    {block.kind === 'motor' && <MotorSettings block={block} disabled={disabled} onUpdate={onUpdate} />}
    {block.kind === 'repeat' && <div className="repeat-config">
      <div className="repeat-toolbar"><label>반복 횟수<input aria-label="반복 횟수" type="number" min="1" max="20" value={block.repeat} disabled={disabled} onChange={(e) => onUpdate(block.id, { repeat: Math.max(1, Math.min(20, Number(e.target.value))) })} /></label><div><strong>반복 동작</strong></div><span>{block.children?.length ?? 0}개</span></div>
      <div className="repeat-drop" onDragOver={(e) => e.preventDefault()} onDrop={onDropChild}>
        {block.children?.length ? block.children.map((child, index) => <NestedWorkflowBlockCard key={child.id} block={child} index={index} count={block.children?.length ?? 0} disabled={disabled} onUpdate={onUpdate} onRemove={onRemoveChild} onMove={onMoveChild} />) : <p>반복할 블록을 이 안에 놓아주세요</p>}
        <div className="nested-drop-hint"><span>＋</span>이곳에 모터 또는 학습 동작 블록 놓기</div>
      </div>
    </div>}
  </div>;
}

function AdminView({ onToast }: { onToast: (message: string) => void }) {
  const { robots, users, permissions: storedPermissions } = useAppStore();
  const [userId, setUserId] = useState('user01');
  const [permissions, setPermissions] = useState<Record<string, string[]>>(storedPermissions);
  const allowed = permissions[userId] ?? [];
  const user = users.find((item) => item.id === userId)!;
  const toggleRobot = (robotId: string) => setPermissions((current) => ({ ...current, [userId]: allowed.includes(robotId) ? allowed.filter((id) => id !== robotId) : [...allowed, robotId] }));

  return <><div className="page-heading"><div><p className="eyebrow">관리자 전용</p><h1>사용자별 로봇 접근 권한</h1><p>각 사용자가 작업 탭에서 선택할 수 있는 로봇을 관리하세요.</p></div><span className="admin-badge">관리자</span></div><div className="admin-layout"><aside className="card user-list"><div className="admin-section-heading"><h2>일반 사용자</h2><span>{users.length}명</span></div><input className="search-input" placeholder="이름 또는 아이디 검색" />{users.map((item) => <button key={item.id} className={userId === item.id ? 'selected' : ''} onClick={() => setUserId(item.id)}><span className="avatar">{item.name[0]}</span><div><strong>{item.name}</strong><small>{item.id} · {item.team}</small></div><em>›</em></button>)}</aside><section className="card permission-panel"><div className="permission-heading"><div><span className="avatar large">{user.name[0]}</span><div><p className="eyebrow">선택한 사용자</p><h2>{user.name} <small>{user.id}</small></h2><p>{user.team} · 현재 {allowed.length}대 접근 가능</p></div></div><button className="primary-button" onClick={() => onToast(`${user.name}님의 로봇 권한을 저장했어요.`)}>변경 사항 저장</button></div><div className="permission-note"><span>i</span>권한을 해제해도 서버에 등록된 로봇은 삭제되지 않아요.</div><div className="robot-list-heading"><div><h3>서버에 등록된 로봇</h3><p>사용자가 작업 탭에서 선택할 로봇을 체크하세요.</p></div><span>{allowed.length}/{robots.length}대 허용</span></div><div className="robot-permission-list">{robots.map((robot) => <label key={robot.id} className={allowed.includes(robot.id) ? 'allowed' : ''}><input type="checkbox" checked={allowed.includes(robot.id)} onChange={() => toggleRobot(robot.id)} /><span className="robot-tile">R</span><div><strong>{robot.name}</strong><p>{robot.location}</p></div><span className={`status-pill status-${robot.status.replace(' ', '-')}`}><i />{robot.status}</span><em>{allowed.includes(robot.id) ? '접근 허용' : '접근 안 함'}</em></label>)}</div></section></div></>;
}


