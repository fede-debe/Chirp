# Chirp

> Real-time chat backend built with Kotlin and Spring Boot.

![Kotlin](https://img.shields.io/badge/Kotlin-2.2-7F52FF?logo=kotlin&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring_Boot-4.0.2-6DB33F?logo=springboot&logoColor=white)
![Java](https://img.shields.io/badge/Java-21-ED8B00?logo=openjdk&logoColor=white)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-Supabase-4169E1?logo=postgresql&logoColor=white)
![Firebase](https://img.shields.io/badge/Firebase-FCM-FFCA28?logo=firebase&logoColor=black)

---

## What it does

Chirp is a production-ready chat API that supports:

- **JWT authentication** — register, login, refresh tokens, email verification, password reset
- **Real-time messaging** — WebSocket with per-user session management
- **Push notifications** — Firebase Cloud Messaging for Android and iOS
- **Profile pictures** — direct-to-Supabase uploads via signed URLs
- **Async event propagation** — RabbitMQ decouples user, chat, and notification flows

---

## Architecture

```
┌─────────────────────────────────────────────┐
│                   app                        │  ← entry point, security, caching
│  ┌──────────┐  ┌──────┐  ┌──────────────┐  │
│  │   user   │  │ chat │  │ notification │  │  ← feature modules
│  └────┬─────┘  └──┬───┘  └──────┬───────┘  │
│       └───────────┴──────────────┘          │
│                 common                       │  ← JWT, events, RabbitMQ, exceptions
└─────────────────────────────────────────────┘

         RabbitMQ (events)
         Redis    (cache)
         Supabase (PostgreSQL + Storage)
         Firebase (push notifications)
         Mailgun  (transactional email)
```

---

## Modules

| Module         | Responsibility                                                            |
|----------------|---------------------------------------------------------------------------|
| `app`          | Application entry point, Spring Security config, Redis cache setup        |
| `user`         | Registration, login, JWT refresh, email verification, password reset      |
| `chat`         | Chat rooms, messages, WebSocket handler, profile pictures                 |
| `notification` | Email delivery (Mailgun), push notifications (Firebase), device tokens    |
| `common`       | Shared JWT service, RabbitMQ infrastructure, domain events, type-safe IDs |

---

## Tech Stack

| Layer              | Technology               |
|--------------------|--------------------------|
| Language           | Kotlin 2.2 / JVM 21      |
| Framework          | Spring Boot 4.0.2        |
| Database           | PostgreSQL via Supabase  |
| Caching            | Redis (RedisLabs)        |
| Messaging          | RabbitMQ (CloudAMQP)     |
| File storage       | Supabase Storage         |
| Push notifications | Firebase Cloud Messaging |
| Email              | Mailgun SMTP             |
| Auth               | Stateless JWT (HS256)    |
| Real-time          | Raw WebSocket            |

---

## Running locally

**Prerequisites:** Java 21, a running instance of each external service (or use the cloud-hosted ones).

**1. Set environment variables**

```bash
export JWT_SECRET_BASE64=<base64-encoded-secret>
export POSTGRES_PASSWORD=<password>
export REDIS_PASSWORD=<password>
export RABBITMQ_PASSWORD=<password>
export MAILGUN_PASSWORD=<password>
export SUPABASE_SERVICE_KEY=<key>
```

**2. Build the jar**

```bash
./gradlew :app:bootJar
```

**3. Run**

```bash
java -jar app/build/libs/app-0.0.1-SNAPSHOT.jar --spring.profiles.active=dev
```

Server starts on `http://localhost:8080`.

> If you use SDKMAN, `cd`-ing into the project auto-selects Java 21 via `.sdkmanrc`.

---

## API Overview

| Area          | Base path           | Key endpoints                                                            |
|---------------|---------------------|--------------------------------------------------------------------------|
| Auth          | `/api/auth`         | `POST /register`, `POST /login`, `POST /refresh`, `POST /logout`         |
| Password      | `/api/auth`         | `POST /forgot-password`, `POST /reset-password`, `POST /change-password` |
| Email         | `/api/auth`         | `POST /resend-verification`, `GET /verify?token=`                        |
| Chats         | `/api/chat`         | `GET /`, `POST /`, `GET /{id}/messages`                                  |
| Participants  | `/api/participants` | `GET /`, `POST /profile-picture-upload`, `POST /confirm-profile-picture` |
| Notifications | `/api/notification` | `POST /register`, `DELETE /{token}`                                      |
| Real-time     | `ws://host/ws/chat` | WebSocket — JWT auth on upgrade                                          |

---

## Project Java version

This project targets **Java 21**. If you manage multiple Java versions with SDKMAN, the `.sdkmanrc` file at the root
will auto-switch when you enter the directory.
