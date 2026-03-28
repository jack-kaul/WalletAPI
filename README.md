# 💳 Payment Wallet API

A REST API for managing digital wallets — built with **Java 17**, **Spring Boot 3**, **MySQL**, and **Docker**.  
This is Month 1 of a progressive DevOps learning project.

---

## Architecture (Steps 1–3)

```
Client (REST Client / curl)
        │
        ▼
┌─────────────────────┐
│  Spring Boot App    │  :8080
│  ─────────────────  │
│  WalletController   │  ← HTTP layer (thin)
│  WalletService      │  ← Business logic
│  Repositories       │  ← DB access via JPA
└────────┬────────────┘
         │ JDBC
         ▼
┌─────────────────────┐
│  MySQL 8.0          │  :3306
│  ─────────────────  │
│  users              │
│  wallets            │
│  transactions       │
└─────────────────────┘
```

> **Coming in Step 4:** Redis cache layer between the app and MySQL for balance reads.  
> **Coming in Step 5:** Full `docker-compose` wrapping the Spring Boot app too.

---

## Project Structure

```
wallet-api/
├── .devcontainer/
│   └── devcontainer.json        # GitHub Codespaces config
├── src/main/java/com/wallet/api/
│   ├── WalletApiApplication.java
│   ├── controller/
│   │   └── WalletController.java    # 5 REST endpoints
│   ├── service/
│   │   └── WalletService.java       # All business logic
│   ├── repository/
│   │   ├── UserRepository.java
│   │   ├── WalletRepository.java
│   │   └── TransactionRepository.java
│   ├── entity/
│   │   ├── User.java
│   │   ├── Wallet.java
│   │   └── Transaction.java
│   ├── dto/
│   │   ├── CreateWalletRequest.java
│   │   ├── DepositRequest.java
│   │   ├── TransferRequest.java
│   │   ├── WalletResponse.java
│   │   └── TransactionResponse.java
│   └── exception/
│       ├── WalletNotFoundException.java
│       ├── InsufficientFundsException.java
│       └── GlobalExceptionHandler.java
├── src/main/resources/
│   └── application.yml
├── docker-compose.yml           # MySQL only (Steps 1–3)
├── requests.http                # Test all endpoints in VS Code
└── pom.xml
```

---

## Database Schema

```sql
-- Auto-created by Hibernate on startup (ddl-auto: update)

CREATE TABLE users (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    name       VARCHAR(255) NOT NULL,
    email      VARCHAR(255) NOT NULL UNIQUE,
    created_at DATETIME
);

CREATE TABLE wallets (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id    BIGINT NOT NULL,
    balance    DECIMAL(19,4) NOT NULL DEFAULT 0.0000,
    created_at DATETIME,
    updated_at DATETIME,
    FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE TABLE transactions (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    from_wallet_id BIGINT,           -- NULL for DEPOSIT
    to_wallet_id   BIGINT NOT NULL,
    amount         DECIMAL(19,4) NOT NULL,
    type           VARCHAR(20) NOT NULL,  -- DEPOSIT | TRANSFER
    created_at     DATETIME,
    FOREIGN KEY (from_wallet_id) REFERENCES wallets(id),
    FOREIGN KEY (to_wallet_id)   REFERENCES wallets(id)
);
```

---

## API Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| `POST` | `/api/v1/wallets` | Create user + wallet |
| `POST` | `/api/v1/wallets/{id}/deposit` | Add money to wallet |
| `POST` | `/api/v1/transfers` | Transfer between wallets |
| `GET`  | `/api/v1/wallets/{id}/balance` | Get wallet balance |
| `GET`  | `/api/v1/wallets/{id}/transactions` | Transaction history |

### Request / Response Examples

**POST /api/v1/wallets**
```json
// Request
{ "name": "Alice", "email": "alice@example.com" }

// Response 201
{
  "walletId": 1,
  "userId": 1,
  "userName": "Alice",
  "userEmail": "alice@example.com",
  "balance": 0.0000,
  "createdAt": "2024-03-01T10:00:00"
}
```

**POST /api/v1/wallets/1/deposit**
```json
// Request
{ "amount": 1000.00 }

// Response 200
{ "walletId": 1, "balance": 1000.0000, ... }
```

**POST /api/v1/transfers**
```json
// Request
{ "fromWalletId": 1, "toWalletId": 2, "amount": 250.00 }

// Response 200
{
  "transactionId": 2,
  "fromWalletId": 1,
  "toWalletId": 2,
  "amount": 250.0000,
  "type": "TRANSFER",
  "createdAt": "2024-03-01T10:05:00"
}
```

### Error Responses

| Scenario | HTTP Status |
|----------|-------------|
| Wallet not found | `404 Not Found` |
| Insufficient funds | `422 Unprocessable Entity` |
| Same-wallet transfer | `400 Bad Request` |
| Validation failure | `400 Bad Request` |

---

## Running Locally (GitHub Codespaces)

### Prerequisites
Everything is pre-installed in the Codespace via `.devcontainer/devcontainer.json`.

### Step 1 — Start MySQL
```bash
cd wallet-api
docker compose up -d
```

Verify MySQL is ready:
```bash
docker compose ps
# wallet-mysql should show "healthy"
```

### Step 2 — Build and Run the App
```bash
mvn spring-boot:run
```

On first run, Hibernate reads your entities and **auto-creates the 3 tables** in MySQL.  
You'll see `HHH000490: Using dialect: org.hibernate.dialect.MySQLDialect` in the logs — that means it connected.

### Step 3 — Test the Endpoints

Open `requests.http` in VS Code and click **Send Request** above each block.

Or use curl:
```bash
# Create wallet
curl -s -X POST http://localhost:8080/api/v1/wallets \
  -H "Content-Type: application/json" \
  -d '{"name":"Alice","email":"alice@example.com"}' | jq

# Deposit
curl -s -X POST http://localhost:8080/api/v1/wallets/1/deposit \
  -H "Content-Type: application/json" \
  -d '{"amount": 1000}' | jq

# Get balance
curl -s http://localhost:8080/api/v1/wallets/1/balance | jq
```

### Stopping Everything
```bash
# Stop the app: Ctrl+C in the terminal running mvn spring-boot:run

# Stop MySQL
docker compose down

# Stop MySQL AND wipe the DB volume (fresh start)
docker compose down -v
```

---

## Key Concepts Demonstrated

| Concept | Where |
|---------|-------|
| REST endpoint design | `WalletController.java` |
| Controller → Service → Repository layering | All three layers |
| JPA entity relationships (`@ManyToOne`) | `Wallet.java`, `Transaction.java` |
| Custom JPQL query | `TransactionRepository.java` |
| DTO pattern (never expose entities directly) | `dto/` package |
| Bean validation (`@Valid`, `@NotNull`) | Request DTOs + Controller |
| Global exception handling | `GlobalExceptionHandler.java` |
| `@Transactional` for atomic operations | `transfer()` in `WalletService.java` |
| `BigDecimal` for money (never float/double) | `Wallet.java`, `Transaction.java` |

---

## What's Next

- **Step 4:** Add Redis — cache wallet balance with the cache-aside pattern. The `getBalance()` method in `WalletService` will check Redis before hitting MySQL.
- **Step 5:** Write a `Dockerfile` for the Spring Boot app. Extend `docker-compose.yml` to spin up app + MySQL + Redis with one command.
- **Step 6:** Push image to Docker Hub. Add architecture diagram to this README.
