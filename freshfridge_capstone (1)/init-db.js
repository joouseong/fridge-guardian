require('dotenv').config();
const fs = require('fs');
const path = require('path');
const db = require('./config/db');

async function initDB() {
  try {
    console.log('SQL 파일 읽는 중...');
    const sqlPath = path.join(__dirname, 'sql', 'schema.sql');
    let sql = fs.readFileSync(sqlPath, 'utf8');

    // Aiven defaultdb 사용을 위해 CREATE DATABASE 및 USE 구문 제거
    sql = sql.replace(/CREATE DATABASE[\s\S]*?;/i, '');
    sql = sql.replace(/USE[\s\S]*?;/i, '');

    console.log('테이블 및 초기 데이터 생성 중...');
    // mysql2의 multipleStatements 옵션 또는 세미콜론 분할 실행
    const queries = sql.split(';').filter(query => query.trim() !== '');

    for (const query of queries) {
      if (query.trim()) {
        await db.query(query);
      }
    }

    console.log('✅ 성공적으로 DB 테이블 및 초기 데이터가 생성되었습니다!');
    process.exit(0);
  } catch (err) {
    console.error('❌ DB 초기화 실패:', err);
    process.exit(1);
  }
}

initDB();