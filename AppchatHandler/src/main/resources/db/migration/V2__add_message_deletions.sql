-- 创建消息删除记录表
CREATE TABLE message_deletions (
    message_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    PRIMARY KEY (message_id, user_id),
    FOREIGN KEY (message_id) REFERENCES messages(id),
    FOREIGN KEY (user_id) REFERENCES users(id)
);

-- 添加索引以提高查询性能
CREATE INDEX idx_message_deletions_user_id ON message_deletions(user_id); 