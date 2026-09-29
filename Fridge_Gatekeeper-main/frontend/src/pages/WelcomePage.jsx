import BrandMark from '../components/BrandMark.jsx'

// 실제 식재료나 추천 결과가 아닌, 앞으로 개발할 기능의 소개입니다.
const plannedFeatures = [
  {
    number: '01',
    title: '식재료를 한눈에',
    description: '무엇을 샀는지, 어디에 보관했는지. 나만의 냉장고에서 간편하게 관리해요.',
  },
  {
    number: '02',
    title: '유통기한을 놓치지 않게',
    description: '먼저 먹어야 할 재료를 확인하고, 냉장고 속 잊힌 식재료를 줄여요.',
  },
  {
    number: '03',
    title: '있는 재료로 맛있는 한 끼',
    description: '검색하기 전에 먼저 제안하는 메뉴. 내 냉장고에 맞는 요리를 만나요.',
  },
]

function FridgeIllustration() {
  // 장식용 그림이므로 스크린 리더가 같은 내용을 중복해서 읽지 않도록 합니다.
  return (
    <div className="fridge-scene" aria-hidden="true">
      <div className="scene-orbit" />
      <svg className="fridge-illustration" viewBox="0 0 340 380" fill="none">
        <ellipse cx="178" cy="346" rx="112" ry="15" fill="#dae4d7" />
        <rect x="80" y="36" width="178" height="300" rx="27" fill="#254d3e" />
        <rect x="69" y="30" width="178" height="300" rx="27" fill="#fdfcf6" stroke="#254d3e" strokeWidth="3" />
        <path d="M70 133h176" stroke="#254d3e" strokeWidth="3" />
        <path d="M91 61v32M91 158v45" stroke="#254d3e" strokeWidth="6" strokeLinecap="round" />
        <rect x="128" y="59" width="80" height="46" rx="10" fill="#e7eedf" />
        <path d="m148 80 12 10 25-20" stroke="#668952" strokeWidth="5" strokeLinecap="round" strokeLinejoin="round" />
        <path d="M101 331v13m116-13v13" stroke="#254d3e" strokeWidth="8" strokeLinecap="round" />
        <g transform="rotate(-8 176 218)">
          <rect x="135" y="177" width="83" height="88" rx="3" fill="#f0d289" />
          <rect x="157" y="171" width="39" height="12" rx="2" fill="#d8b763" />
          <path d="M154 207h40m-40 15h31m-31 15h23" stroke="#887443" strokeWidth="3" strokeLinecap="round" />
        </g>
        <path d="M40 138c-17-3-23-16-21-29 14 1 26 9 27 23 4-18 17-26 31-24-1 17-13 31-31 30" fill="#83a46e" />
        <path d="M45 128v36" stroke="#537a43" strokeWidth="3" strokeLinecap="round" />
        <circle cx="272" cy="277" r="28" fill="#dd8d65" />
        <path d="M272 250c0-11 7-18 17-17-1 11-9 17-17 17Z" fill="#668952" />
        <path d="M278 263c6 1 10 6 11 11" stroke="#f2bc9f" strokeWidth="4" strokeLinecap="round" />
        <path d="m277 76 4 9 10 1-8 7 2 10-8-5-9 5 2-10-7-7 10-1 3-9Z" fill="#d8b763" />
      </svg>
      <span className="scene-label scene-label-top">신선함을 더 오래</span>
      <span className="scene-label scene-label-bottom">먹을 만큼, 남김없이</span>
    </div>
  )
}

export default function WelcomePage() {
  return (
    <div className="site-shell">
      <a className="skip-link" href="#main-content">본문으로 이동</a>
      <header className="site-header">
        <div className="brand">
          <BrandMark />
          <span>냉장고 지킴이</span>
        </div>
        <span className="step-label">STEP 01 · 프로젝트 시작</span>
      </header>

      <main id="main-content">
        <section className="hero" aria-labelledby="welcome-title">
          <div className="hero-copy">
            <p className="eyebrow">FRIDGE GATEKEEPER</p>
            <h1 id="welcome-title">우리 집 냉장고를<br /><span>조금 더 똑똑하게.</span></h1>
            <p className="hero-description">
              냉장고 속 재료가 오늘의 메뉴가 되는 곳.<br className="desktop-break" />
              식재료 관리부터 맞춤 요리 추천까지, 하나씩 준비하고 있어요.
            </p>
            <div className="setup-note">
              <span className="status-dot" aria-hidden="true" />
              <p><strong>프로젝트 기본 구조 준비 완료</strong><br />현재는 실행을 확인하는 시작 화면입니다.</p>
            </div>
          </div>
          <FridgeIllustration />
        </section>

        <section className="feature-section" aria-labelledby="features-title">
          <div className="section-heading">
            <h2 id="features-title">함께 만들어 갈 냉장고 생활</h2>
            <span className="planned-label">개발 예정 기능</span>
          </div>
          <div className="feature-grid">
            {plannedFeatures.map((feature) => (
              <article className="feature-card" key={feature.number}>
                <span className="feature-number" aria-hidden="true">{feature.number}</span>
                <h3>{feature.title}</h3>
                <p>{feature.description}</p>
              </article>
            ))}
          </div>
        </section>
      </main>

      <footer className="site-footer">
        <span>Fridge Gatekeeper</span>
        <span>작은 관리로, 더 맛있는 일상.</span>
      </footer>
    </div>
  )
}
