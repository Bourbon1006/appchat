-- 创建动态表
CREATE TABLE moments (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    content TEXT NOT NULL,
    image_url VARCHAR(255),
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    like_count INT NOT NULL DEFAULT 0,
    comment_count INT NOT NULL DEFAULT 0,
    FOREIGN KEY (user_id) REFERENCES users(id)
);

-- 创建点赞记录表
CREATE TABLE moment_likes (
    moment_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (moment_id, user_id),
    FOREIGN KEY (moment_id) REFERENCES moments(id) ON DELETE CASCADE,
    FOREIGN KEY (user_id) REFERENCES users(id)
);

-- 创建评论表
CREATE TABLE moment_comments (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    moment_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    content TEXT NOT NULL,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (moment_id) REFERENCES moments(id) ON DELETE CASCADE,
    FOREIGN KEY (user_id) REFERENCES users(id)
);

-- 添加索引以提高查询性能
CREATE INDEX idx_moments_user_id ON moments(user_id);
CREATE INDEX idx_moments_create_time ON moments(create_time);
CREATE INDEX idx_moment_comments_moment_id ON moment_comments(moment_id); 