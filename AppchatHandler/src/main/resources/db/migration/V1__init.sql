-- Table structure for table `friend_requests`
DROP TABLE IF EXISTS `friend_requests`;
CREATE TABLE `friend_requests` (
                                   `id` bigint NOT NULL AUTO_INCREMENT,
                                   `receiver_id` bigint DEFAULT NULL,
                                   `sender_id` bigint DEFAULT NULL,
                                   `timestamp` datetime(6) NOT NULL,
                                   `status` enum('ACCEPTED','PENDING','REJECTED') COLLATE utf8mb4_unicode_ci DEFAULT NULL,
                                   PRIMARY KEY (`id`),
                                   KEY `FKtcmqalc5v4qdt1slgcsa544i5` (`receiver_id`),
                                   KEY `FKcchlh48b4347amfvmke793bg7` (`sender_id`),
                                   CONSTRAINT `FKcchlh48b4347amfvmke793bg7` FOREIGN KEY (`sender_id`) REFERENCES `users` (`id`),
                                   CONSTRAINT `FKtcmqalc5v4qdt1slgcsa544i5` FOREIGN KEY (`receiver_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=57 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Table structure for table `group_members`
DROP TABLE IF EXISTS `group_members`;
CREATE TABLE `group_members` (
                                 `group_id` bigint NOT NULL,
                                 `user_id` bigint NOT NULL,
                                 PRIMARY KEY (`group_id`,`user_id`),
                                 KEY `FKnr9qg33qt2ovmv29g4vc3gtdx` (`user_id`),
                                 CONSTRAINT `FKnr9qg33qt2ovmv29g4vc3gtdx` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`),
                                 CONSTRAINT `FKrpgq4bl4kui39wk9mlkl26ib` FOREIGN KEY (`group_id`) REFERENCES `groups` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Table structure for table `groups`
DROP TABLE IF EXISTS `groups`;
CREATE TABLE `groups` (
                          `creator_id` bigint DEFAULT NULL,
                          `id` bigint NOT NULL AUTO_INCREMENT,
                          `name` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
                          `avatar_url` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
                          PRIMARY KEY (`id`),
                          KEY `FKkgykkb697oqdgqwyty7d51sk9` (`creator_id`),
                          CONSTRAINT `FKkgykkb697oqdgqwyty7d51sk9` FOREIGN KEY (`creator_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Table structure for table `message_deleted_users`
DROP TABLE IF EXISTS `message_deleted_users`;
CREATE TABLE `message_deleted_users` (
                                         `message_id` bigint NOT NULL,
                                         `user_id` bigint DEFAULT NULL,
                                         KEY `FK73sopyjcsu8cu67n4dncssxqw` (`message_id`),
                                         CONSTRAINT `FK73sopyjcsu8cu67n4dncssxqw` FOREIGN KEY (`message_id`) REFERENCES `messages` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Table structure for table `message_read_status`
DROP TABLE IF EXISTS `message_read_status`;
CREATE TABLE `message_read_status` (
                                       `id` bigint NOT NULL AUTO_INCREMENT,
                                       `read_time` datetime(6) NOT NULL,
                                       `message_id` bigint NOT NULL,
                                       `user_id` bigint NOT NULL,
                                       PRIMARY KEY (`id`),
                                       KEY `FK6v2gsesvjkhu2n73ye3qig78` (`message_id`),
                                       KEY `FKc4c87dvt76csu6c8lwog8hkfq` (`user_id`),
                                       CONSTRAINT `FK6v2gsesvjkhu2n73ye3qig78` FOREIGN KEY (`message_id`) REFERENCES `messages` (`id`),
                                       CONSTRAINT `FKc4c87dvt76csu6c8lwog8hkfq` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=410 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Table structure for table `messages`
DROP TABLE IF EXISTS `messages`;
CREATE TABLE `messages` (
                            `group_id` bigint DEFAULT NULL,
                            `id` bigint NOT NULL AUTO_INCREMENT,
                            `receiver_id` bigint DEFAULT NULL,
                            `sender_id` bigint DEFAULT NULL,
                            `timestamp` datetime(6) DEFAULT NULL,
                            `content` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
                            `file_url` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
                            `type` enum('FILE','IMAGE','TEXT','VIDEO') COLLATE utf8mb4_unicode_ci DEFAULT NULL,
                            PRIMARY KEY (`id`),
                            KEY `FKslb8msfikfxrexaaghc77botj` (`group_id`),
                            KEY `FKt05r0b6n0iis8u7dfna4xdh73` (`receiver_id`),
                            KEY `FK4ui4nnwntodh6wjvck53dbk9m` (`sender_id`),
                            CONSTRAINT `FK4ui4nnwntodh6wjvck53dbk9m` FOREIGN KEY (`sender_id`) REFERENCES `users` (`id`),
                            CONSTRAINT `FKslb8msfikfxrexaaghc77botj` FOREIGN KEY (`group_id`) REFERENCES `groups` (`id`),
                            CONSTRAINT `FKt05r0b6n0iis8u7dfna4xdh73` FOREIGN KEY (`receiver_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=134 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Table structure for table `user_contacts`
DROP TABLE IF EXISTS `user_contacts`;
CREATE TABLE `user_contacts` (
                                 `contact_id` bigint NOT NULL,
                                 `user_id` bigint NOT NULL,
                                 PRIMARY KEY (`contact_id`,`user_id`),
                                 KEY `FKqgbpf3rh5b6i7npvr2rf776rd` (`user_id`),
                                 CONSTRAINT `FKpv0xogldk4u85927p4492211j` FOREIGN KEY (`contact_id`) REFERENCES `users` (`id`),
                                 CONSTRAINT `FKqgbpf3rh5b6i7npvr2rf776rd` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Table structure for table `users`
DROP TABLE IF EXISTS `users`;
CREATE TABLE `users` (
                         `is_online` int DEFAULT NULL,
                         `id` bigint NOT NULL AUTO_INCREMENT,
                         `password` varchar(60) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
                         `avatar_url` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
                         `nickname` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
                         `username` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
                         PRIMARY KEY (`id`),
                         UNIQUE KEY `UKr43af9ap4edm43mmtq01oddj6` (`username`)
) ENGINE=InnoDB AUTO_INCREMENT=8 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
