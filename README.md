# AppChat & AppchatHandler 项目文档

## 项目概述
这是一个基于 Android 客户端和 Kotlin 服务器端的即时通讯应用项目。项目采用现代化的架构设计和开发技术，提供完整的即时通讯、社交和文件传输功能。

## 技术栈
### 客户端 (AppChat)
- 开发语言：Kotlin/Java
- 开发平台：Android
- 构建工具：Gradle
- 架构模式：MVP/MVVM
- 主要功能：即时通讯、文件传输、社交功能

### 服务器端 (AppchatHandler)
- 开发语言：Kotlin
- 框架：Spring Boot
- 构建工具：Gradle
- 架构模式：分层架构
- 主要功能：用户管理、消息处理、文件存储

## 项目结构

### AppChat (Android 客户端)
```
AppChat/
├── .idea/                      # IDE 配置文件
├── .gradle/                    # Gradle 构建缓存
├── app/                        # 主应用模块
│   └── src/
│       └── main/
│           ├── AndroidManifest.xml  # Android 应用清单
│           ├── java/           # Java 源代码目录
│           │   └── com/
│           │       └── example/
│           │           └── appchat/
│           │               ├── activity/          # 活动页面
│           │               │   ├── ChatActivity.kt         # 聊天界面
│           │               │   ├── MainActivity.kt         # 主界面
│           │               │   ├── LoginActivity.kt        # 登录界面
│           │               │   ├── RegisterActivity.kt     # 注册界面
│           │               │   ├── ProfileActivity.kt      # 个人资料界面
│           │               │   ├── GroupSettingsActivity.kt # 群组设置界面
│           │               │   ├── CreateMomentActivity.kt # 创建动态界面
│           │               │   ├── FilePreviewActivity.kt  # 文件预览界面
│           │               │   ├── ImagePreviewActivity.kt # 图片预览界面
│           │               │   ├── VideoPreviewActivity.kt # 视频预览界面
│           │               │   ├── SelectContactsActivity.kt # 选择联系人界面
│           │               │   └── NearbyTransferActivity.kt # 附近传输界面
│           │               ├── adapter/           # 适配器
│           │               ├── api/              # API 接口
│           │               ├── db/               # 数据库相关
│           │               ├── dialog/           # 对话框
│           │               ├── fragment/         # 碎片
│           │               ├── model/            # 数据模型
│           │               ├── service/          # 服务
│           │               ├── util/             # 工具类
│           │               ├── websocket/        # WebSocket 相关
│           │               └── AppChatApplication.kt # 应用程序入口
│           └── res/            # 资源文件目录
├── gradle/                     # Gradle 包装器
├── local.properties           # 本地环境配置
├── build.gradle.kts           # 项目级构建脚本
├── gradle.properties          # Gradle 属性配置
├── gradlew                    # Gradle 包装器脚本(Unix)
├── gradlew.bat               # Gradle 包装器脚本(Windows)
├── settings.gradle.kts        # Gradle 设置文件
└── .gitignore                # Git 忽略文件
```

### AppchatHandler (服务器端)
```
AppchatHandler/
├── .idea/                     # IDE 配置文件
├── build/                     # 构建输出目录
├── avatars/                   # 头像存储目录
├── .gradle/                   # Gradle 构建缓存
├── bin/                       # 二进制文件目录
├── src/                       # 源代码目录
│   └── main/
│       ├── kotlin/           # Kotlin 源代码目录
│       │   └── org/
│       │       └── example/
│       │           └── appchathandler/
│       │               ├── controller/          # 控制器层
│       │               │   ├── AuthController.kt      # 认证控制器
│       │               │   ├── UserController.kt      # 用户控制器
│       │               │   ├── MessageController.kt   # 消息控制器
│       │               │   ├── GroupController.kt     # 群组控制器
│       │               │   ├── FriendController.kt    # 好友控制器
│       │               │   ├── FriendGroupController.kt # 好友分组控制器
│       │               │   ├── FriendRequestController.kt # 好友请求控制器
│       │               │   ├── FileController.kt      # 文件控制器
│       │               │   └── MomentController.kt    # 动态控制器
│       │               ├── service/            # 服务层
│       │               ├── repository/         # 数据访问层
│       │               ├── model/              # 数据模型
│       │               ├── entity/             # 数据库实体
│       │               ├── dto/                # 数据传输对象
│       │               ├── websocket/          # WebSocket 相关
│       │               ├── event/              # 事件处理
│       │               ├── config/             # 配置类
│       │               └── AppchatHandlerApplication.kt # 应用程序入口
│       └── resources/        # 资源文件目录
├── uploads/                   # 上传文件目录
├── gradlew                    # Gradle 包装器脚本(Unix)
├── gradlew.bat               # Gradle 包装器脚本(Windows)
├── .gitattributes            # Git 属性配置
├── .gitignore                # Git 忽略文件
└── build.gradle.kts          # 项目构建脚本
```

## 主要功能模块

### 1. 用户系统
- 用户注册与登录
- 个人资料管理
- 好友管理
- 群组管理
- 权限控制

### 2. 消息系统
- 私聊消息
- 群聊消息
- 实时消息推送
- 消息历史记录
- WebSocket 实时通信

### 3. 文件系统
- 文件上传下载
- 图片预览
- 视频预览
- 附近文件传输
- 文件存储管理

### 4. 社交功能
- 好友请求
- 好友分组
- 动态发布
- 社交互动

## 技术特点
1. 采用现代化的架构设计
2. 使用 WebSocket 实现实时通信
3. 支持多种文件格式的传输和预览
4. 实现了完整的用户社交系统
5. 采用分层架构，代码结构清晰
6. 使用 Gradle 进行项目构建
7. 支持跨平台开发

## 开发环境要求
- Android Studio
- JDK 11+
- Gradle 7.0+
- Kotlin 1.5+
- Spring Boot 2.5+

## 部署说明
1. 客户端部署
   - 使用 Android Studio 打开 AppChat 项目
   - 配置 Gradle 环境
   - 构建并运行项目

2. 服务器端部署
   - 使用 IDE 打开 AppchatHandler 项目
   - 配置数据库连接
   - 运行 Spring Boot 应用 