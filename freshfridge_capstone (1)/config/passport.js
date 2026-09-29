const passport = require('passport');
const GoogleStrategy = require('passport-google-oauth20').Strategy;
const KakaoStrategy = require('passport-kakao').Strategy;
const db = require('./db'); // 기존 데이터베이스 연동 파일 경로에 맞춰 확인해주세요

// 1. Google OAuth 전략
passport.use(new GoogleStrategy({
    clientID: process.env.GOOGLE_CLIENT_ID,
    clientSecret: process.env.GOOGLE_CLIENT_SECRET,
    callbackURL: process.env.GOOGLE_CALLBACK_URL || '/auth/google/callback'
  },
  async (accessToken, refreshToken, profile, done) => {
    try {
      const snsId = profile.id;
      const email = profile.emails && profile.emails[0] ? profile.emails[0].value : null;
      const name = profile.displayName || 'Google User';

      // 1. 구글로 이미 가입된 유저인지 확인
      const [existingUsers] = await db.query(
        'SELECT * FROM users WHERE provider = ? AND sns_id = ?',
        ['google', snsId]
      );

      if (existingUsers.length > 0) {
        return done(null, existingUsers[0]);
      }

      // 1-2. 구글 계정이 없더라도, 동일한 이메일로 가입된 유저가 있는지 확인
      if (email) {
        const [existingEmailUsers] = await db.query(
            'SELECT * FROM users WHERE email = ?',
            [email]
        );

        // 동일 이메일 유저가 있다면 -> 구글 소셜 연동[UPDATE] 진행
        if (existingEmailUsers.length > 0) {
            const user = existingEmailUsers[0];
            await db.query(
                'UPDATE users SET provider = ?, sns_id = ? WHERE id = ?',
                ['google', snsId, user.id]
            );
            user.provider = 'google';
            user.sns_id = snsId;
            return done(null, user);
        }
      }

      // 2. 가입되지 않은 경우 신규 유저 등록 (INSERT)
      const [result] = await db.query(
        'INSERT INTO users (email, name, provider, sns_id) VALUES (?, ?, ?, ?)',
        [email, name, 'google', snsId]
      );

      const newUser = {
        id: result.insertId,
        email,
        name,
        provider: 'google',
        sns_id: snsId
      };

      return done(null, newUser);
    } catch (err) {
      return done(err, null);
    }
  }
));

// 2. Kakao OAuth 전략
passport.use(new KakaoStrategy({
    clientID: process.env.KAKAO_CLIENT_ID,
    clientSecret: process.env.KAKAO_CLIENT_SECRET, // 클라이언트 시크릿을 발급받지 않았다면 빈 문자열로 유지
    callbackURL: process.env.KAKAO_CALLBACK_URL || '/auth/kakao/callback'
  },
  async (accessToken, refreshToken, profile, done) => {
    try {
      const snsId = String(profile.id);
      const email = profile._json?.kakao_account?.email || null;
      const name = profile.displayName || profile._json?.properties?.nickname || 'Kakao User';

      // 1. 카카오로 이미 가입된 유저인지 확인
      const [existingUsers] = await db.query(
        'SELECT * FROM users WHERE provider = ? AND sns_id = ?',
        ['kakao', snsId]
      );

      if (existingUsers.length > 0) {
        return done(null, existingUsers[0]);
      }

      // 1-2. 카카오 계정이 없더라도, 동일한 이메일로 가입된 유저가 있는지 확인
      if (email) {
        const [existingEmailUsers] = await db.query(
          'SELECT * FROM users WHERE email = ?',
          [email]
        );

        // 동일 이메일 유저가 있다면 -> 카카오 소셜 연동[UPDATE] 진행
        if (existingEmailUsers.length > 0) {
          const user = existingEmailUsers[0];
          await db.query(
            'UPDATE users SET provider = ?, sns_id = ? WHERE id = ?',
            ['kakao', snsId, user.id]
          );
          user.provider = 'kakao';
          user.sns_id = snsId;
          return done(null, user);
        }
      }

      // 2. 가입되지 않은 경우 신규 유저 등록 (INSERT)
      const [result] = await db.query(
        'INSERT INTO users (email, name, provider, sns_id) VALUES (?, ?, ?, ?)',
        [email, name, 'kakao', snsId]
      );

      const newUser = {
        id: result.insertId,
        email,
        name,
        provider: 'kakao',
        sns_id: snsId
      };

      return done(null, newUser);
    } catch (err) {
      return done(err, null);
    }
  }
));

module.exports = passport;