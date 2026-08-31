# AI Notes Summarizer 📝🤖

A Spring Boot REST API where users register, log in with JWT, paste text or upload PDFs, and receive AI-generated summaries with key points — all saved per-user in MySQL.

**Built with:** Spring Boot 3.3 · Spring AI 1.1 · Spring Security (JWT) · Hibernate/MySQL · Groq (free LLM)

---

## Quick Start

### Prerequisites

| Tool | Version | Check |
|---|---|---|
| Java JDK | 17+ | `java -version` |
| Maven | 3.8+ | `mvn --version` |
| MySQL | 8.0+ | `mysql --version` |
| Groq API Key | Free | [console.groq.com](https://console.groq.com) |

### 1. Create the MySQL Database

```sql
CREATE DATABASE notesai_db;
```

### 2. Set Environment Variables

**Windows PowerShell:**
```powershell
$env:GROQ_API_KEY = "gsk_your_key_here"
$env:JWT_SECRET = "bXlTdXBlclNlY3JldEtleUZvckpXVFRva2VuU2lnbmluZzEyMzQ1Njc4OTA="
```

**Windows CMD:**
```cmd
set GROQ_API_KEY=gsk_your_key_here
set JWT_SECRET=bXlTdXBlclNlY3JldEtleUZvckpXVFRva2VuU2lnbmluZzEyMzQ1Njc4OTA=
```

### 3. Run the Application

```bash
mvn spring-boot:run
```

The app starts at **http://localhost:8080**

### 4. Access API Documentation

Open **http://localhost:8080/swagger-ui.html** in your browser for interactive API docs.

---

## Tech Stack

| Layer | Technology | Purpose |
|---|---|---|
| Language | Java 17 | LTS version |
| Framework | Spring Boot 3.3.13 | REST API framework |
| Security | Spring Security + JWT | Stateless authentication |
| ORM | Spring Data JPA + Hibernate | Database abstraction |
| Database | MySQL | Relational data storage |
| AI | Spring AI 1.1.8 + Groq | Text summarization |
| PDF Parsing | Spring AI PagePdfDocumentReader | PDF text extraction |
| API Docs | SpringDoc OpenAPI (Swagger) | Interactive API documentation |
| Build Tool | Maven | Dependency management |

---

## API Endpoints

| Method | Path | Description | Auth |
|---|---|---|---|
| `POST` | `/api/auth/register` | Register new user | ❌ Public |
| `POST` | `/api/auth/login` | Login, returns JWT | ❌ Public |
| `POST` | `/api/notes` | Summarize pasted text | ✅ JWT |
| `POST` | `/api/notes/upload` | Upload PDF → summarize | ✅ JWT |
| `GET` | `/api/notes` | List your notes | ✅ JWT |
| `GET` | `/api/notes/{id}` | View one note | ✅ JWT |
| `DELETE` | `/api/notes/{id}` | Delete a note | ✅ JWT |
| `POST` | `/api/notes/{id}/ask` | Ask AI about a note | ✅ JWT |

---

## Testing with Postman

### 1. Register
```
POST http://localhost:8080/api/auth/register
Content-Type: application/json

{
  "username": "john",
  "password": "password123"
}
```

### 2. Login
```
POST http://localhost:8080/api/auth/login
Content-Type: application/json

{
  "username": "john",
  "password": "password123"
}
```
Copy the `token` from the response.

### 3. Create a Note (use the token)
```
POST http://localhost:8080/api/notes
Authorization: Bearer <your-token>
Content-Type: application/json

{
  "text": "Spring Boot is a Java framework that simplifies the development of production-ready applications. It provides auto-configuration, embedded servers, and opinionated defaults to get projects running quickly. Spring Boot eliminates much of the boilerplate code required in traditional Spring applications."
}
```

### 4. Upload a PDF
```
POST http://localhost:8080/api/notes/upload
Authorization: Bearer <your-token>
Content-Type: multipart/form-data
Key: file (type: File) → select your PDF
```

### 5. List All Notes
```
GET http://localhost:8080/api/notes
Authorization: Bearer <your-token>
```

---

## Project Structure

```
com.anuj.notesai
├── config/          → SecurityConfig, AiConfig, SwaggerConfig
├── controller/      → AuthController, NoteController
├── dto/             → Request/Response DTOs (RegisterRequest, NoteResponse, etc.)
├── entity/          → JPA entities (User, Note, SourceType enum)
├── repository/      → Spring Data JPA repositories
├── security/        → JWT utilities (JwtUtil, JwtAuthenticationFilter)
├── service/         → Business logic (AuthService, NoteService, AiSummaryService)
└── exception/       → Global error handling
```

---

## Architecture

```
Client (Postman/Frontend)
    │
    ▼
[Spring Security Filter Chain]
    │
    ├── JwtAuthenticationFilter → validates JWT token
    │
    ▼
[Controller Layer] → handles HTTP request/response
    │
    ▼
[Service Layer] → business logic, orchestration
    │
    ├── AiSummaryService → calls Groq AI via Spring AI ChatClient
    │
    ▼
[Repository Layer] → Spring Data JPA auto-generated queries
    │
    ▼
[MySQL Database]
```

---


