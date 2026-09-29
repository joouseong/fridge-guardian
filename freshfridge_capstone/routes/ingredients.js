const express = require('express');
const path = require('path');
const fs = require('fs');
const multer = require('multer');
const db = require('../config/db');
const { requireLogin } = require('../middleware/auth');

const router = express.Router();

const storage = multer.diskStorage({
  destination: (req, file, cb) => cb(null, path.join(__dirname, '..', 'uploads')),
  filename: (req, file, cb) => {
    const safeExt = path.extname(file.originalname).toLowerCase().slice(0, 10);
    cb(null, `${Date.now()}-${Math.round(Math.random() * 1E9)}${safeExt}`);
  }
});

const upload = multer({
  storage,
  limits: { fileSize: 5 * 1024 * 1024 },
  fileFilter: (req, file, cb) => {
    if (file.mimetype.startsWith('image/')) cb(null, true);
    else cb(new Error('이미지 파일만 업로드할 수 있습니다.'));
  }
});

router.get('/', requireLogin, async (req, res) => {
  const userId = req.session.user.id;
  const keyword = (req.query.keyword || '').trim();
  const status = req.query.status || '';

  let sql = `
    SELECT *, DATEDIFF(expiry_date, CURDATE()) AS dday
    FROM ingredients
    WHERE user_id = ?
  `;
  const params = [userId];

  if (keyword) {
    sql += ' AND (name LIKE ? OR category LIKE ? OR storage_location LIKE ?)';
    const q = `%${keyword}%`;
    params.push(q, q, q);
  }

  if (status === 'soon') sql += ' AND DATEDIFF(expiry_date, CURDATE()) BETWEEN 0 AND 3';
  if (status === 'expired') sql += ' AND DATEDIFF(expiry_date, CURDATE()) < 0';
  if (status === 'safe') sql += ' AND DATEDIFF(expiry_date, CURDATE()) > 3';

  sql += ' ORDER BY expiry_date ASC, created_at DESC';

  try {
    const [ingredients] = await db.query(sql, params);
    res.render('ingredients/list', { ingredients, keyword, status });
  } catch (err) {
    console.error(err);
    res.status(500).render('error', { message: '식재료 목록을 불러오지 못했습니다.' });
  }
});

router.get('/new', requireLogin, (req, res) => {
  res.render('ingredients/form', { ingredient: null });
});

router.post('/', requireLogin, upload.single('image'), async (req, res) => {
  const { name, category, quantity, unit, purchase_date, expiry_date, storage_location, memo } = req.body;
  const userId = req.session.user.id;
  const imagePath = req.file ? `/uploads/${req.file.filename}` : null;

  if (!name || !expiry_date) {
    req.session.flash = { type: 'danger', message: '식재료 이름과 유통기한은 필수입니다.' };
    return res.redirect('/ingredients/new');
  }

  try {
    await db.query(
      `INSERT INTO ingredients
       (user_id, name, category, quantity, unit, purchase_date, expiry_date, storage_location, memo, image_path)
       VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`,
      [
        userId,
        name,
        category || null,
        Number(quantity || 1),
        unit || '개',
        purchase_date || null,
        expiry_date,
        storage_location || '냉장',
        memo || null,
        imagePath
      ]
    );

    req.session.flash = { type: 'success', message: '식재료를 등록했습니다.' };
    res.redirect('/ingredients');
  } catch (err) {
    console.error(err);
    req.session.flash = { type: 'danger', message: '식재료 등록 중 오류가 발생했습니다.' };
    res.redirect('/ingredients/new');
  }
});

router.get('/:id/edit', requireLogin, async (req, res) => {
  try {
    const [rows] = await db.query(
      'SELECT * FROM ingredients WHERE id = ? AND user_id = ?',
      [req.params.id, req.session.user.id]
    );

    if (rows.length === 0) return res.status(404).render('error', { message: '식재료를 찾을 수 없습니다.' });
    res.render('ingredients/form', { ingredient: rows[0] });
  } catch (err) {
    console.error(err);
    res.status(500).render('error', { message: '식재료 정보를 불러오지 못했습니다.' });
  }
});

router.post('/:id/edit', requireLogin, upload.single('image'), async (req, res) => {
  const { name, category, quantity, unit, purchase_date, expiry_date, storage_location, memo } = req.body;

  try {
    const [rows] = await db.query(
      'SELECT * FROM ingredients WHERE id = ? AND user_id = ?',
      [req.params.id, req.session.user.id]
    );
    if (rows.length === 0) return res.status(404).render('error', { message: '식재료를 찾을 수 없습니다.' });

    const current = rows[0];
    const imagePath = req.file ? `/uploads/${req.file.filename}` : current.image_path;

    if (req.file && current.image_path) {
      const oldPath = path.join(__dirname, '..', current.image_path.replace(/^\//, ''));
      if (fs.existsSync(oldPath)) fs.unlinkSync(oldPath);
    }

    await db.query(
      `UPDATE ingredients
       SET name=?, category=?, quantity=?, unit=?, purchase_date=?, expiry_date=?, storage_location=?, memo=?, image_path=?
       WHERE id=? AND user_id=?`,
      [
        name,
        category || null,
        Number(quantity || 1),
        unit || '개',
        purchase_date || null,
        expiry_date,
        storage_location || '냉장',
        memo || null,
        imagePath,
        req.params.id,
        req.session.user.id
      ]
    );

    req.session.flash = { type: 'success', message: '식재료 정보를 수정했습니다.' };
    res.redirect('/ingredients');
  } catch (err) {
    console.error(err);
    req.session.flash = { type: 'danger', message: '수정 중 오류가 발생했습니다.' };
    res.redirect('/ingredients');
  }
});

router.post('/:id/delete', requireLogin, async (req, res) => {
  try {
    const [rows] = await db.query(
      'SELECT image_path FROM ingredients WHERE id=? AND user_id=?',
      [req.params.id, req.session.user.id]
    );

    if (rows.length > 0 && rows[0].image_path) {
      const filePath = path.join(__dirname, '..', rows[0].image_path.replace(/^\//, ''));
      if (fs.existsSync(filePath)) fs.unlinkSync(filePath);
    }

    await db.query(
      'DELETE FROM ingredients WHERE id = ? AND user_id = ?',
      [req.params.id, req.session.user.id]
    );

    req.session.flash = { type: 'success', message: '식재료를 삭제했습니다.' };
    res.redirect('/ingredients');
  } catch (err) {
    console.error(err);
    req.session.flash = { type: 'danger', message: '삭제 중 오류가 발생했습니다.' };
    res.redirect('/ingredients');
  }
});

module.exports = router;
