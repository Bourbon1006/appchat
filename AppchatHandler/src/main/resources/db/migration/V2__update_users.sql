-- 删除旧的 status 列
ALTER TABLE users DROP COLUMN status;

-- 确保 is_online 列类型正确
ALTER TABLE users MODIFY COLUMN is_online BOOLEAN DEFAULT FALSE;

-- 更新现有数据
UPDATE users SET is_online = FALSE WHERE is_online IS NULL; 