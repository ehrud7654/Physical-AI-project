'use client';

import { useEffect, useLayoutEffect, useRef, useState } from 'react';

const steps = [
  { label: '대시 보드', title: '전체 상태를 한눈에 봐요', description: '장치 연결과 학습 진행 상황을 모아 보여줘요.', items: ['연결된 UMI와 사용 가능한 로봇 수 확인', '로봇별 상태와 최근 학습 진행률 확인'] },
  { label: '데이터', title: '기록한 동작을 확인해요', description: 'UMI로 기록한 동작과 학습 가능 여부를 보여줘요.', items: ['동작·상태별로 데이터 찾기', '데이터 번호를 눌러 상세 정보와 확인 결과 보기'] },
  { label: '학습', title: '로봇에게 동작을 가르쳐요', description: '새로운 기록이나 기존 데이터로 학습을 만들어요.', items: ['학습 목록: 진행률과 학습 결과 확인', '학습 생성: 동작과 데이터를 골라 학습 시작'] },
  { label: '작업', title: '배운 동작을 묶어 실행해요', description: '로봇이 할 일을 순서대로 만들고 실행해요.', items: ['작업 목록: 저장한 작업의 상세 확인·수정·실행', '작업 생성: 동작을 순서대로 추가하고 실행 준비'] },
  { label: '장치 관리', title: 'UMI와 로봇을 관리해요', description: '동작을 기록할 UMI와 실행할 로봇을 보여줘요.', items: ['로봇·UMI 탭에서 장치 정보와 연결 상태 확인', '장치 등록으로 새로운 장치 추가'] },
];

function GuidePoint({ number, side, item, children }: { number: number; side: 'left' | 'right'; item: string[]; children: React.ReactNode }) {
  const [title, ...lines] = item;
  return <div className={`tour-guide-point tour-guide-point-${number} tour-note-${side}`}><div className="tour-point-target">{children}</div><div className="tour-anchor-note"><span>{number}</span><div><strong>{title}</strong><p>{lines.map((line) => <span key={line}>{line}</span>)}</p></div></div></div>;
}

function ScreenPreview({ index }: { index: number }) {
  const pages = steps.map((step) => step.label);
  const figure = useRef<HTMLElement>(null);
  const [connections, setConnections] = useState<{ path: string; x: number; y: number }[]>([]);
  const captions = [
    [['장치 현황', '연결된 UMI와', '사용 가능한 로봇 확인'], ['상태 확인', '로봇 상태와', '최근 학습 진행률 확인']],
    [['데이터 찾기', '동작과 상태로', '원하는 데이터 찾기'], ['데이터 상세', '데이터 번호를 눌러', '정보와 확인 결과 보기']],
    [['학습 목록', '학습 진행률과', '결과 확인'], ['학습 생성', '동작과 데이터를 골라', '새 학습 시작']],
    [['작업 목록', '저장한 작업 확인,', '수정 및 실행'], ['작업 생성', '동작을 순서대로 추가해', '실행 준비']],
    [['장치 확인', '로봇·UMI 정보와', '연결 상태 확인'], ['장치 등록', '새로운 로봇이나', 'UMI 추가']],
  ];
  useLayoutEffect(() => {
    const root = figure.current;
    if (!root) return;
    const measure = () => {
      if (window.innerWidth <= 800) { setConnections([]); return; }
      const bounds = root.getBoundingClientRect();
      setConnections(Array.from(root.querySelectorAll('.tour-guide-point')).map((point) => {
        const target = point.querySelector('.tour-point-target')!.getBoundingClientRect();
        const note = point.querySelector('.tour-anchor-note')!.getBoundingClientRect();
        const left = point.classList.contains('tour-note-left');
        const sx = (left ? note.right : note.left) - bounds.left;
        const sy = note.top + note.height / 2 - bounds.top;
        const x = (left ? target.left : target.right) - bounds.left;
        const y = target.top + target.height / 2 - bounds.top;
        return left
          ? { path: `M ${sx + 6} ${sy} C ${sx + 28} ${sy}, ${x - 28} ${y}, ${x} ${y}`, x, y }
          : { path: `M ${sx - 6} ${sy} C ${sx - 28} ${sy}, ${x + 28} ${y}, ${x} ${y}`, x, y };
      }));
    };
    const observer = new ResizeObserver(measure);
    observer.observe(root);
    root.querySelectorAll('.tour-point-target,.tour-anchor-note').forEach((element) => observer.observe(element));
    window.addEventListener('resize', measure);
    measure();
    return () => { observer.disconnect(); window.removeEventListener('resize', measure); };
  }, [index]);
  const guide = (number: number) => ({ number, side: ((index % 2 === 0 ? number === 1 : number === 2) ? 'left' : 'right') as 'left' | 'right', item: captions[index][number - 1] });
  return <figure ref={figure} className="tour-visual" aria-label={`${pages[index]} 안내용 화면 예시`}>
    <svg className="tour-connections" aria-hidden="true">{connections.map((connection, i) => <g key={i}><path d={connection.path} /><circle cx={connection.x} cy={connection.y} r="3" /></g>)}</svg>
    <div className="tour-browser"><div className="tour-browser-bar"><i /><i /><i /><span>S.S.A.F.Y</span></div>
      <div className="tour-screen"><div className="tour-mini-nav" aria-hidden="true"><b>S.</b>{pages.map((name, i) => <span key={name} className={index === i ? 'chosen' : ''}>{name}</span>)}</div>
        <div className="tour-mini-main"><h3>{index === 2 ? '학습 목록' : index === 3 ? '작업 목록' : pages[index]}</h3>
          {index === 0 && <><GuidePoint {...guide(1)}><div className="tour-mini-stats"><span>등록된 UMI<strong>2대 연결됨</strong></span><span>등록된 로봇<strong>1대 사용 가능</strong></span></div></GuidePoint><GuidePoint {...guide(2)}><div className="tour-highlight"><b>로봇 상태 · 최근 학습</b><span className="tour-green">로봇팔 07 · 온라인</span><small>집어서 옮기기 · 진행 중 68%</small><div className="tour-progress"><i /></div></div></GuidePoint></>}
          {index === 1 && <><GuidePoint {...guide(1)}><div className="tour-mini-filters"><span>전체 동작 ⌄</span><span>전체 상태 ⌄</span></div></GuidePoint><GuidePoint {...guide(2)}><div className="tour-highlight"><b>데이터 001</b><small>집어서 옮기기</small><span className="tour-green">● 학습 가능</span></div></GuidePoint><div className="tour-mini-card"><b>데이터 002</b><small>빨간 블록 집기 · 확인 중</small></div></>}
          {index === 2 && <><GuidePoint {...guide(1)}><div className="tour-highlight"><b>집어서 옮기기</b><span>진행 중</span><strong className="tour-percent">68%</strong><div className="tour-progress"><i /></div><small>••• → 상세 보기</small></div></GuidePoint><GuidePoint {...guide(2)}><div className="tour-mini-action">＋ 학습 생성</div></GuidePoint></>}
          {index === 3 && <><GuidePoint {...guide(1)}><div className="tour-mini-card"><b>작업 목록</b><span>빨간 블록 정리　›</span><small>작업 상세 · 수정하기 · 작업 실행</small></div></GuidePoint><GuidePoint {...guide(2)}><div className="tour-highlight"><b>작업 생성</b><span>빨간 블록 집기 → 옮기기</span><div className="tour-mini-action">저장 후 실행 준비 →</div></div></GuidePoint></>}
          {index === 4 && <><GuidePoint {...guide(1)}><div className="tour-mini-tabs">로봇 <strong>UMI</strong></div><div className="tour-highlight"><span className="tour-device-icon">U</span><b>내 UMI</b><span className="tour-green">● 온라인</span></div></GuidePoint><GuidePoint {...guide(2)}><div className="tour-mini-action">＋ 장치 등록</div></GuidePoint></>}
        </div></div></div>
  </figure>;
}

export default function Tutorial({ onClose }: { onClose: () => void }) {
  const [index, setIndex] = useState(0);
  const dialog = useRef<HTMLDialogElement>(null);
  const heading = useRef<HTMLHeadingElement>(null);
  const content = useRef<HTMLDivElement>(null);
  const step = steps[index];
  useEffect(() => {
    const previous = document.activeElement as HTMLElement | null;
    const overflow = document.body.style.overflow;
    const modal = dialog.current;
    modal?.showModal();
    document.body.style.overflow = 'hidden';
    return () => { modal?.close(); document.body.style.overflow = overflow; previous?.focus(); };
  }, []);
  useEffect(() => { content.current?.scrollTo(0, 0); heading.current?.focus(); }, [index]);
  return <dialog ref={dialog} className="tutorial-dialog" aria-labelledby="tutorial-title" onCancel={onClose}>
    <header className="tutorial-header"><span>S.S.A.F.Y <small>시작 가이드</small></span><button type="button" onClick={onClose} aria-label="튜토리얼 닫기">×</button></header>
    <div className="tutorial-body" ref={content}>
      <section className="tutorial-content"><p className="tutorial-counter">{String(index + 1).padStart(2, '0')} — {step.label}</p><h2 id="tutorial-title" ref={heading} tabIndex={-1}>{step.title}</h2><p className="tutorial-description">{step.description}</p>
      </section>
      <ScreenPreview index={index} />
    </div>
    <footer className="tutorial-footer"><nav className="tour-dots" aria-label="튜토리얼 단계">{steps.map((item, i) => <button key={item.label} type="button" aria-label={`${i + 1}단계 ${item.label}`} aria-current={index === i ? 'step' : undefined} onClick={() => setIndex(i)}><i /></button>)}</nav><span className="tour-page-count" aria-live="polite">{index + 1} / {steps.length}</span><div className="tour-footer-actions"><button type="button" className="tour-skip" onClick={onClose}>건너뛰기</button><button type="button" className="tour-back" disabled={index === 0} onClick={() => setIndex(index - 1)}>이전</button><button type="button" className="primary-button" onClick={() => index === steps.length - 1 ? onClose() : setIndex(index + 1)}>{index === steps.length - 1 ? '안내 마치기' : '다음'} <span aria-hidden="true">→</span></button></div></footer>
  </dialog>;
}
