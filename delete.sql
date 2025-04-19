-- 禁用外键检查
SET FOREIGN_KEY_CHECKS = 0;

-- 清空消息相关表
DELETE FROM message_read_status;
DELETE FROM message_deletions;
DELETE FROM messages;

-- 清空朋友圈相关表
DELETE FROM moment_likes;
DELETE FROM moment_comments;
DELETE FROM moments;

-- 清空群组相关表
DELETE FROM group_members;
DELETE FROM groups;

-- 清空好友相关表
DELETE FROM friend_group_members;
DELETE FROM friend_groups;
DELETE FROM friend_requests;
DELETE FROM user_contacts;

-- 清空用户表
DELETE FROM users;

-- 重置自增ID
ALTER TABLE message_read_status AUTO_INCREMENT = 1;
ALTER TABLE messages AUTO_INCREMENT = 1;
ALTER TABLE moment_comments AUTO_INCREMENT = 1;
ALTER TABLE moments AUTO_INCREMENT = 1;
ALTER TABLE friend_requests AUTO_INCREMENT = 1;
ALTER TABLE groups AUTO_INCREMENT = 1;
ALTER TABLE users AUTO_INCREMENT = 1;

-- 启用外键检查
SET FOREIGN_KEY_CHECKS = 1;
