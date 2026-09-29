const express = require('express');
const bcrypt = require('bcrypt');
const db = require('../config/db');

const router = express.Router();

router.get('/register', (req, res) => {
  if (req.session.user) return res.redirect('/dashboard');
  res.render('auth/register');
});

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
  req.session.destroy(() => res.redirect('/'));
});

module.exports = router;
