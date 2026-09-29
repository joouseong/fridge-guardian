const express = require('express');
const bcrypt = require('bcrypt');
const db = require('../config/db');
const { requireLogin } = require('../middleware/auth');

const router = express.Router();

// GET /profile - 프로필 조회 페이지
router.get('/', requireLogin, async (req, res) => {
  try {
    const [rows] = await db.query(
      'SELECT id, name, email, password_hash, created_at FROM users WHERE id = ?',
      [req.session.user.id]
    );

    if (rows.length === 0) {
      req.session.flash = { type: 'danger', message: '사용자 정보를 찾을 수 없습니다.' };
      return res.redirect('/auth/login');
    }

    res.render('auth/profile', { profileUser: rows[0] });
  } catch (err) {
    console.error(err);
    req.session.flash = { type: 'danger', message: '프로필 정보를 불러오는데 실패했습니다.' };
    res.redirect('/dashboard');
  }
});

// POST /profile - 프로필 수정 (이름 변경 및 비밀번호 변경)
router.post('/', requireLogin, async (req, res) => {
  const userId = req.session.user.id;
  const { name, currentPassword, newPassword, newPasswordConfirm } = req.body;

  if (!name || name.trim() === '') {
    req.session.flash = { type: 'danger', message: '이름을 입력해주세요.' };
    return res.redirect('/profile');
  }

  try {
    // 1. 현재 사용자 DB 정보 조회
    const [rows] = await db.query('SELECT * FROM users WHERE id = ?', [userId]);
    const user = rows[0];

    let updateQuery = 'UPDATE users SET name = ?';
    let queryParams = [name.trim()];

    // 2. 비밀번호 변경 시도 여부 확인 (신규 비밀번호 입력 시)
    if (newPassword || currentPassword || newPasswordConfirm) {
      // 소셜 가입자 체크
      if (!user.password_hash) {
        req.session.flash = { type: 'danger', message: '소셜 로그인 계정은 비밀번호를 변경할 수 없습니다.' };
        return res.redirect('/profile');
      }

      // 현재 비밀번호 검증
      const isMatch = await bcrypt.compare(currentPassword, user.password_hash);
      if (!isMatch) {
        req.session.flash = { type: 'danger', message: '현재 비밀번호가 일치하지 않습니다.' };
        return res.redirect('/profile');
      }

      // 새 비밀번호 유효성 검사
      if (!newPassword || newPassword.length < 6) {
        req.session.flash = { type: 'danger', message: '새 비밀번호는 6자 이상이어야 합니다.' };
        return res.redirect('/profile');
      }

      if (newPassword !== newPasswordConfirm) {
        req.session.flash = { type: 'danger', message: '새 비밀번호 확인이 일치하지 않습니다.' };
        return res.redirect('/profile');
      }

      // 비밀번호 해싱 및 쿼리 추가
      const newPasswordHash = await bcrypt.hash(newPassword, 10);
      updateQuery += ', password_hash = ?';
      queryParams.push(newPasswordHash);
    }

    // DB 업데이트 실행
    updateQuery += ' WHERE id = ?';
    queryParams.push(userId);

    await db.query(updateQuery, queryParams);

    // 세션 정보 갱신 (네비게이션 바 이름 변경 반영)
    req.session.user.name = name.trim();

    req.session.flash = { type: 'success', message: '회원 정보가 성공적으로 수정되었습니다.' };
    res.redirect('/profile');

  } catch (err) {
    console.error(err);
    req.session.flash = { type: 'danger', message: '회원 정보 수정 중 오류가 발생했습니다.' };
    res.redirect('/profile');
  }
});

module.exports = router;