const express = require('express');
const bcrypt = require('bcrypt');
const db = require('../config/db');
const passport = require('../config/passport');
const { requireLogin } = require('../middleware/auth');

const router = express.Router();

router.get('/register', (req, res) => {
  if (req.session.user) return res.redirect('/dashboard');
  res.render('auth/register');
});

// 1. 구글 로그인
// 1-1. 구글 로그인 버튼 클릭 시 구글 인증 페이지로 이동
router.get('/google', passport.authenticate('google', { scope: ['profile', 'email'], prompt: 'select_account' }));

// 1-2. 구글 인증 완료 후 콜백 처리
router.get('/google/callback',
  passport.authenticate('google', { session: false, failureRedirect: '/auth/login' }),
  (req, res) => {
    // 인증 성공 시 req.user 정보를 기존 세션에 저장
    req.session.user = {
      id:req.user.id,
      email: req.user.email,
      name: req.user.name
    };

    // 메인 화면 또는 대시 보드로 이동
    res.redirect('/');
  }
);

// 2. 카카오 로그인
// 2-1. 카카오 로그인 버튼 클릭 시 카카오 인증 페이지로 이동
router.get('/kakao', (req, res) => {
  const clientId = process.env.KAKAO_CLIENT_ID;
  const redirectUri = encodeURIComponent(process.env.KAKAO_CALLBACK_URL || 'http://localhost:3000/auth/kakao/callback');

  const kakaoAuthUrl = `https://kauth.kakao.com/oauth/authorize?response_type=code&client_id=${clientId}&redirect_uri=${redirectUri}&prompt=login`;

  res.redirect(kakaoAuthUrl);
});

// 2-1. 카카오 인증 완료 후 콜백 처리
router.get('/kakao/callback',
  passport.authenticate('kakao', {session: false, failureRedirect: '/auth/login' }),
  (req, res) => {
    // 인증 성공 시 req.user 정보를 기존 세션에 저장(구글과 동일 방식)
    req.session.user = {
      id: req.user.id,
      email: req.user.email,
      name: req.user.name
    };

    // 메인 화면 또는 대시 보드로 이동
    res.redirect('/');
  }
)

router.post('/register', async (req, res) => {
  const { name, email, password, passwordConfirm } = req.body;

  if (!name || !email || !password || !passwordConfirm) {
    req.session.flash = { type: 'danger', message: '모든 항목을 입력해주세요.' };
    return res.redirect('/auth/register');
  }

  if (password.length < 6) {
    req.session.flash = { type: 'danger', message: '비밀번호는 6자 이상으로 입력해주세요.' };
    return res.redirect('/auth/register');
  }

  if (password !== passwordConfirm) {
    req.session.flash = { type: 'danger', message: '비밀번호 확인이 일치하지 않습니다.' };
    return res.redirect('/auth/register');
  }

  try {
    const [exists] = await db.query('SELECT id FROM users WHERE email = ?', [email]);
    if (exists.length > 0) {
      req.session.flash = { type: 'danger', message: '이미 가입된 이메일입니다.' };
      return res.redirect('/auth/register');
    }

    const passwordHash = await bcrypt.hash(password, 10);
    await db.query(
      'INSERT INTO users (name, email, password_hash) VALUES (?, ?, ?)',
      [name, email, passwordHash]
    );

    req.session.flash = { type: 'success', message: '회원가입이 완료되었습니다. 로그인해주세요.' };
    res.redirect('/auth/login');
  } catch (err) {
    console.error(err);
    req.session.flash = { type: 'danger', message: '회원가입 중 오류가 발생했습니다.' };
    res.redirect('/auth/register');
  }
});

router.get('/login', (req, res) => {
  if (req.session.user) return res.redirect('/dashboard');
  res.render('auth/login');
});

router.post('/login', async (req, res) => {
  const { email, password } = req.body;

  try {
    const [rows] = await db.query('SELECT * FROM users WHERE email = ?', [email]);
    if (rows.length === 0) {
      req.session.flash = { type: 'danger', message: '이메일 또는 비밀번호가 올바르지 않습니다.' };
      return res.redirect('/auth/login');
    }

    const user = rows[0];
    const ok = await bcrypt.compare(password, user.password_hash);

    if (!ok) {
      req.session.flash = { type: 'danger', message: '이메일 또는 비밀번호가 올바르지 않습니다.' };
      return res.redirect('/auth/login');
    }

    req.session.user = { id: user.id, name: user.name, email: user.email };
    res.redirect('/dashboard');
  } catch (err) {
    console.error(err);
    req.session.flash = { type: 'danger', message: '로그인 중 오류가 발생했습니다.' };
    res.redirect('/auth/login');
  }
});

router.post('/logout', (req, res) => {
  req.session.destroy((err) => {
    if (err) console.error(err);
    res.clearCookie('connect.sid'); // 세션 쿠키 삭제
    res.redirect('/');
  });
});

router.get('/profile', requireLogin, async (req, res) => {
  try {
    const [rows] = await db.query('SELECT id, name, email, created_at FROM users WHERE id = ?', [req.session.user.id]);
    res.render('auth/profile', { profileUser: rows[0] });
  } catch (err) {
    console.error(err);
    res.status(500).render('error', { message: '프로필 정보를 불러올 수 없습니다.' });
  }
});

module.exports = router;
