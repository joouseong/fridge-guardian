require('dotenv').config();

const express = require('express');
const path = require('path');
const session = require('express-session');
const multer = require('multer');
const fs = require('fs');

const authRoutes = require('./routes/auth');
const ingredientRoutes = require('./routes/ingredients');
const recipeRoutes = require('./routes/recipes');
const { requireLogin } = require('./middleware/auth');
const db = require('./config/db');

const app = express();
const PORT = process.env.PORT || 3000;

const uploadDir = path.join(__dirname, 'uploads');
if (!fs.existsSync(uploadDir)) fs.mkdirSync(uploadDir, { recursive: true });

app.set('view engine', 'ejs');
app.set('views', path.join(__dirname, 'views'));

app.use(express.urlencoded({ extended: true }));
app.use(express.json());
app.use('/public', express.static(path.join(__dirname, 'public')));
app.use('/uploads', express.static(uploadDir));

app.use(session({
  secret: process.env.SESSION_SECRET || 'freshfridge-secret',
  resave: false,
  saveUninitialized: false,
  cookie: {
    httpOnly: true,
    maxAge: 1000 * 60 * 60 * 4
  }
}));

app.use((req, res, next) => {
  res.locals.user = req.session.user || null;
  res.locals.flash = req.session.flash || null;
  delete req.session.flash;
  next();
});

app.get('/', (req, res) => {
  if (req.session.user) return res.redirect('/dashboard');
  res.render('home');
});

app.get('/dashboard', requireLogin, async (req, res) => {
  try {
    const userId = req.session.user.id;
    const [ingredients] = await db.query(
      `SELECT *,
        DATEDIFF(expiry_date, CURDATE()) AS dday
       FROM ingredients
       WHERE user_id = ?
       ORDER BY expiry_date ASC, created_at DESC`,
      [userId]
    );

    const total = ingredients.length;
    const expiringSoon = ingredients.filter(i => i.dday >= 0 && i.dday <= 3).length;
    const expired = ingredients.filter(i => i.dday < 0).length;

    res.render('dashboard', {
      ingredients: ingredients.slice(0, 6),
      stats: { total, expiringSoon, expired }
    });
  } catch (err) {
    console.error(err);
    res.status(500).render('error', { message: '대시보드 정보를 불러오지 못했습니다.' });
  }
});

app.use('/auth', authRoutes);
app.use('/ingredients', ingredientRoutes);
app.use('/recipes', recipeRoutes);

app.use((req, res) => {
  res.status(404).render('error', { message: '페이지를 찾을 수 없습니다.' });
});

app.use((err, req, res, next) => {
  console.error(err);
  res.status(500).render('error', { message: '서버에서 오류가 발생했습니다.' });
});

app.listen(PORT, async () => {
  try {
    await db.query('SELECT 1');
    console.log(`FreshFridge server: http://localhost:${PORT}`);
    console.log('MySQL connection: OK');
  } catch (err) {
    console.error('MySQL connection failed:', err.message);
  }
});
