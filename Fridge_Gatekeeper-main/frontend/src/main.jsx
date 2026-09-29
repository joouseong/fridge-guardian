import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import App from './App.jsx'
import './styles/global.css'

// index.html의 root 요소 안에 React 화면을 그립니다.
// StrictMode는 개발 중에 잠재적인 문제를 찾는 데 도움을 줍니다.
createRoot(document.getElementById('root')).render(
  <StrictMode>
    <App />
  </StrictMode>,
)
