const express = require('express');
const db = require('../config/db');
const { requireLogin } = require('../middleware/auth');

const router = express.Router();

router.get('/', requireLogin, async (req, res) => {
  try {
    const userId = req.session.user.id;

    const [userIngredients] = await db.query(
      `SELECT LOWER(name) AS name
       FROM ingredients
       WHERE user_id = ? AND expiry_date >= CURDATE()`,
      [userId]
    );

    const owned = new Set(userIngredients.map(x => x.name.trim().toLowerCase()));

    const [recipes] = await db.query(`
      SELECT
        r.id,
        r.name,
        r.description,
        r.cooking_time,
        r.difficulty,
        r.instructions,
        GROUP_CONCAT(ri.ingredient_name ORDER BY ri.id SEPARATOR '|||') AS ingredients
      FROM recipes r
      LEFT JOIN recipe_ingredients ri ON ri.recipe_id = r.id
      GROUP BY r.id
      ORDER BY r.name
    `);

    const ranked = recipes.map(r => {
      const list = (r.ingredients || '').split('|||').filter(Boolean);
      const matched = list.filter(i => owned.has(i.trim().toLowerCase()));
      const missing = list.filter(i => !owned.has(i.trim().toLowerCase()));
      const matchRate = list.length ? Math.round((matched.length / list.length) * 100) : 0;

      return { ...r, ingredientList: list, matched, missing, matchRate };
    }).sort((a, b) => b.matchRate - a.matchRate || a.cooking_time - b.cooking_time);

    res.render('recipes/list', { recipes: ranked, ownedCount: owned.size });
  } catch (err) {
    console.error(err);
    res.status(500).render('error', { message: '레시피 추천 정보를 불러오지 못했습니다.' });
  }
});

module.exports = router;
