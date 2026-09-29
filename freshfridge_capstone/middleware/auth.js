function requireLogin(req, res, next) {
  if (!req.session.user) {
    req.session.flash = { type: 'warning', message: '로그인이 필요한 기능입니다.' };
    return res.redirect('/auth/login');
  }
  next();
}

module.exports = { requireLogin };
