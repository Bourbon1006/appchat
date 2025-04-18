-- MySQL dump 10.13  Distrib 8.0.32, for Win64 (x86_64)
--
-- Host: localhost    Database: appchat_1
-- ------------------------------------------------------
-- Server version	8.0.32

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

--
-- Current Database: `appchat_1`
--

CREATE DATABASE /*!32312 IF NOT EXISTS*/ `appchat_1` /*!40100 DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci */ /*!80016 DEFAULT ENCRYPTION='N' */;

USE `appchat_1`;

--
-- Table structure for table `friend_group_members`
--

DROP TABLE IF EXISTS `friend_group_members`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `friend_group_members` (
  `group_id` bigint NOT NULL,
  `contact_id` bigint NOT NULL,
  PRIMARY KEY (`group_id`,`contact_id`),
  KEY `FK6ifa1nh96tefs4ckwn5jcudc6` (`contact_id`),
  CONSTRAINT `FK6ifa1nh96tefs4ckwn5jcudc6` FOREIGN KEY (`contact_id`) REFERENCES `users` (`id`),
  CONSTRAINT `FKgyx9wrrhfxkirok48ordewa6i` FOREIGN KEY (`group_id`) REFERENCES `friend_groups` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `friend_groups`
--

DROP TABLE IF EXISTS `friend_groups`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `friend_groups` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `user_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FK2kao0wtl56mte4xkci6p8tixp` (`user_id`),
  CONSTRAINT `FK2kao0wtl56mte4xkci6p8tixp` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `friend_requests`
--

DROP TABLE IF EXISTS `friend_requests`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
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
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `group_members`
--

DROP TABLE IF EXISTS `group_members`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `group_members` (
  `group_id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  `is_admin` bit(1) NOT NULL DEFAULT b'0',
  `joined_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`group_id`,`user_id`),
  KEY `FKnr9qg33qt2ovmv29g4vc3gtdx` (`user_id`),
  CONSTRAINT `FKnr9qg33qt2ovmv29g4vc3gtdx` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`),
  CONSTRAINT `FKrpgq4bl4kui39wk9mlkl26ib` FOREIGN KEY (`group_id`) REFERENCES `groups` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `groups`
--

DROP TABLE IF EXISTS `groups`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `groups` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `description` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `owner_id` bigint DEFAULT NULL,
  `avatar_url` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `creator_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FK_groups_owner_id` (`owner_id`),
  KEY `FKkgykkb697oqdgqwyty7d51sk9` (`creator_id`),
  CONSTRAINT `FK_groups_owner_id` FOREIGN KEY (`owner_id`) REFERENCES `users` (`id`),
  CONSTRAINT `FKkgykkb697oqdgqwyty7d51sk9` FOREIGN KEY (`creator_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `message_deletions`
--

DROP TABLE IF EXISTS `message_deletions`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `message_deletions` (
  `message_id` bigint NOT NULL,
  `user_id` bigint DEFAULT NULL,
  KEY `FK6gp0lghg7vk0h5cpsvxuxn8yp` (`message_id`),
  CONSTRAINT `FK6gp0lghg7vk0h5cpsvxuxn8yp` FOREIGN KEY (`message_id`) REFERENCES `messages` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `message_read_status`
--

DROP TABLE IF EXISTS `message_read_status`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
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
) ENGINE=InnoDB AUTO_INCREMENT=7 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `messages`
--

DROP TABLE IF EXISTS `messages`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `messages` (
  `group_id` bigint DEFAULT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `receiver_id` bigint DEFAULT NULL,
  `sender_id` bigint DEFAULT NULL,
  `timestamp` datetime(6) DEFAULT NULL,
  `content` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `file_url` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `type` enum('FILE','IMAGE','TEXT','VIDEO','AUDIO') COLLATE utf8mb4_unicode_ci DEFAULT 'TEXT',
  `group_name` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `is_starred` bit(1) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FKslb8msfikfxrexaaghc77botj` (`group_id`),
  KEY `FKt05r0b6n0iis8u7dfna4xdh73` (`receiver_id`),
  KEY `FK4ui4nnwntodh6wjvck53dbk9m` (`sender_id`),
  CONSTRAINT `FK4ui4nnwntodh6wjvck53dbk9m` FOREIGN KEY (`sender_id`) REFERENCES `users` (`id`),
  CONSTRAINT `FKslb8msfikfxrexaaghc77botj` FOREIGN KEY (`group_id`) REFERENCES `groups` (`id`),
  CONSTRAINT `FKt05r0b6n0iis8u7dfna4xdh73` FOREIGN KEY (`receiver_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=7 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `moment_comments`
--

DROP TABLE IF EXISTS `moment_comments`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `moment_comments` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `content` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `create_time` datetime(6) NOT NULL,
  `moment_id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FKg04l5309jcuoa6aqhflfaqc3u` (`moment_id`),
  KEY `FK2rk71y6opp954382yipb648em` (`user_id`),
  CONSTRAINT `FK2rk71y6opp954382yipb648em` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`),
  CONSTRAINT `FKg04l5309jcuoa6aqhflfaqc3u` FOREIGN KEY (`moment_id`) REFERENCES `moments` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `moment_likes`
--

DROP TABLE IF EXISTS `moment_likes`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `moment_likes` (
  `create_time` datetime(6) NOT NULL,
  `moment_id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  PRIMARY KEY (`moment_id`,`user_id`),
  KEY `FKcou8c1g1ck9s54y41r1484n5l` (`user_id`),
  CONSTRAINT `FKcou8c1g1ck9s54y41r1484n5l` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`),
  CONSTRAINT `FKii2q9dafab1m83y9mgs7dhugd` FOREIGN KEY (`moment_id`) REFERENCES `moments` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `moments`
--

DROP TABLE IF EXISTS `moments`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `moments` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `comment_count` int NOT NULL,
  `content` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `create_time` datetime(6) NOT NULL,
  `image_url` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `like_count` int NOT NULL,
  `user_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FK5m9nlo4taewuiux9uaala8i57` (`user_id`),
  CONSTRAINT `FK5m9nlo4taewuiux9uaala8i57` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `user_contacts`
--

DROP TABLE IF EXISTS `user_contacts`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `user_contacts` (
  `contact_id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  PRIMARY KEY (`contact_id`,`user_id`),
  KEY `FKqgbpf3rh5b6i7npvr2rf776rd` (`user_id`),
  CONSTRAINT `FKpv0xogldk4u85927p4492211j` FOREIGN KEY (`contact_id`) REFERENCES `users` (`id`),
  CONSTRAINT `FKqgbpf3rh5b6i7npvr2rf776rd` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `users`
--

DROP TABLE IF EXISTS `users`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `users` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `password` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `avatar_url` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `nickname` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `username` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `online_status` int DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKr43af9ap4edm43mmtq01oddj6` (`username`),
  CONSTRAINT `chk_online_status` CHECK ((`online_status` in (0,1,2)))
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2025-04-18 22:47:59
