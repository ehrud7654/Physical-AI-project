import type { Metadata } from 'next';
import './globals.css';

export const metadata: Metadata = {
  metadataBase: new URL('https://umi-studio-wireframe.michael3252.chatgpt.site'),
  title: 'S.S.A.F.Y · 로봇 학습 플랫폼',
  description: '누구나 쉽게 로봇에게 새로운 작업을 가르치는 UMI 기반 학습 플랫폼',
  openGraph: {
    title: 'S.S.A.F.Y · 로봇 학습 플랫폼',
    description: '누구나 쉽게 가르치는 로봇 학습 플랫폼',
    images: [{ url: '/og.png', width: 1200, height: 630, alt: 'S.S.A.F.Y 로봇 학습 플랫폼' }],
  },
  twitter: {
    card: 'summary_large_image',
    title: 'S.S.A.F.Y · 로봇 학습 플랫폼',
    description: '누구나 쉽게 가르치는 로봇 학습 플랫폼',
    images: ['/og.png'],
  },
};

export default function RootLayout({ children }: Readonly<{ children: React.ReactNode }>) {
  return <html lang="ko"><head><link rel="preconnect" href="https://cdn.jsdelivr.net" crossOrigin="anonymous" /><link rel="stylesheet" href="https://cdn.jsdelivr.net/gh/sun-typeface/SUIT@2/fonts/variable/woff2/SUIT-Variable.css" /><script dangerouslySetInnerHTML={{ __html: "if(location.hash.includes('figmacapture='))document.documentElement.classList.add('figma-desktop-capture')" }} /></head><body>{children}<script src="https://mcp.figma.com/mcp/html-to-design/capture.js" async /></body></html>;
}
