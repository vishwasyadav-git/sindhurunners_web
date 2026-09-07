# Sindhu Runners — Event Registration System

Production-ready event registration with Razorpay payment and AWS S3 Aadhaar document storage.

---

## Table of Contents

1. [Architecture](#architecture)
2. [Prerequisites](#prerequisites)
3. [Local Setup](#local-setup)
4. [Environment Variables](#environment-variables)
5. [Database Setup](#database-setup)
6. [AWS S3 Setup](#aws-s3-setup)
7. [Razorpay Setup](#razorpay-setup)
8. [Razorpay Webhook Setup](#razorpay-webhook-setup)
9. [Running Locally](#running-locally)
10. [API Reference](#api-reference)
11. [Testing](#testing)
12. [Production Deployment](#production-deployment)
13. [Known Limitations / Future Improvements](#known-limitations--future-improvements)

---

## Architecture

```
frontend/
  index.html                    ← Existing Sindhu Runners landing page
  register.html                 ← NEW: event registration form + Razorpay Checkout
  registration-success.html     ← NEW: post-payment confirmation page

backend/sindhueventpay/         ← Spring Boot 3.4.1 · Java 21 · MySQL 8
  config/                       ← AppProperties, CorsConfig, RazorpayConfig, S3Config
  models/                       ← Event, Registration (UUID PK), Payment, User
  enums/                        ← RegistrationStatus, PaymentStatus
  dto/                          ← ApiResponse<T>, EventResponse, RegistrationResponse, ...
  Repository/                   ← JPA repositories with pessimistic-lock queries
  services/                     ← EventService, RegistrationService, PaymentService,
  │                                 RazorpayService, S3Service
  controllers/                  ← EventController, RegistrationController, PaymentController
  exceptions/                   ← Domain exceptions + GlobalExceptionHandler
  filter/                       ← RequestCorrelationFilter (correlation IDs)
  resources/db/migration/       ← Flyway V1–V4 migration scripts

deployment/
  docker-compose.yml
  .env.example
```

### Key Design Decisions

| Decision | Rationale |
|---|---|
| **UUID PK** for Registration | Prevents sequential enumeration; safe to share in URLs |
| **Sequential registration number** (`EVT2026-000042`) | User-friendly, generated only after verified payment |
| **Pessimistic write lock** on Event row | Guarantees atomic counter increment — no duplicate numbers under concurrency |
| **Flyway** instead of `ddl-auto=update` | Safe, auditable schema migrations in production |
| **Webhook + verify both process payment** | Frontend verify handles immediate UX; webhook is the authoritative source |
| **Layered idempotency** | Application check (status == PAID) + DB UNIQUE constraint on `razorpay_payment_id` |
| **Apache Tika for MIME detection** | Rejects extension-spoofed files (e.g. `evil.pdf.exe`) |
| **S3 SSE-S3 encryption** | Aadhaar documents encrypted at rest |
| **Pre-signed URLs for admin access** | No public S3 URLs ever; access requires authorisation |

---

## Prerequisites

| Tool | Version |
|---|---|
| Java JDK | 21+ |
| Maven | 3.9+ (or use `./mvnw`) |
| MySQL | 8.0+ |
| Docker + Docker Compose | Any recent version |
| Razorpay account | [dashboard.razorpay.com](https://dashboard.razorpay.com) |
| AWS account | S3 access |

---

## Local Setup

```bash
# 1. Clone the repo
git clone https://github.com/your-org/sindhurunners_web.git
cd sindhurunners_web

# 2. Copy env template
cp deployment/.env.example deployment/.env
# Edit deployment/.env with your actual values

# 3. Start MySQL via Docker Compose
cd deployment
docker compose up mysql -d

# 4. Wait for MySQL to be healthy, then run the backend
cd ../backend/sindhueventpay
./mvnw spring-boot:run
```

The application starts on **http://localhost:8080**.

---

## Environment Variables

> All secrets MUST be supplied via environment variables.  
> Never hardcode them in source files.

| Variable | Required | Description |
|---|---|---|
| `DATABASE_URL` | ✅ | JDBC URL, e.g. `jdbc:mysql://localhost:3306/sindhu_event_db?...` |
| `DATABASE_USERNAME` | ✅ | MySQL username |
| `DATABASE_PASSWORD` | ✅ | MySQL password |
| `RAZORPAY_KEY_ID` | ✅ | Razorpay Key ID (public, sent to frontend) |
| `RAZORPAY_KEY_SECRET` | ✅ | Razorpay Key Secret (server-side only, never logged) |
| `RAZORPAY_WEBHOOK_SECRET` | ✅ | Razorpay Webhook Secret for HMAC verification |
| `AWS_REGION` | ✅ | AWS region, e.g. `ap-south-1` |
| `AWS_S3_BUCKET` | ✅ | Private S3 bucket name |
| `AWS_ACCESS_KEY_ID` | ⚠️ Dev only | IAM access key (blank if using IAM role) |
| `AWS_SECRET_ACCESS_KEY` | ⚠️ Dev only | IAM secret key (blank if using IAM role) |
| `CORS_ALLOWED_ORIGINS` | ✅ | Comma-separated frontend origin(s) |

---

## Database Setup

Flyway automatically runs migrations on startup. No manual schema creation needed.

### For a fresh install:

1. Create the database and user:

```sql
CREATE DATABASE sindhu_event_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'sindhu_user'@'%' IDENTIFIED BY 'your_strong_password';
GRANT ALL PRIVILEGES ON sindhu_event_db.* TO 'sindhu_user'@'%';
FLUSH PRIVILEGES;
```

2. Start the Spring Boot application — Flyway applies V1–V4 migrations automatically.

### Migrations applied:

| Version | Description |
|---|---|
| V1 | Creates `events` table |
| V2 | Creates `registrations` table (UUID PK) |
| V3 | Creates `payments` table |
| V4 | Adds performance indexes |

### Seed an event for testing:

```sql
INSERT INTO events (event_code, event_name, description, registration_fee, currency, max_registrations, registration_open, start_date, end_date)
VALUES (
  'EVT2026',
  'Goa to Sawantwadi Intercity Run 2026',
  'The annual 100 km intercity run from Goa to Sawantwadi through scenic coastal roads.',
  500.00, 'INR', 1000, 1, '2026-04-30', '2026-04-30'
);
```

---

## AWS S3 Setup

### 1. Create a private S3 bucket

```bash
aws s3api create-bucket \
  --bucket sindhu-runners-aadhaar-documents \
  --region ap-south-1 \
  --create-bucket-configuration LocationConstraint=ap-south-1

# Block all public access
aws s3api put-public-access-block \
  --bucket sindhu-runners-aadhaar-documents \
  --public-access-block-configuration \
    "BlockPublicAcls=true,IgnorePublicAcls=true,BlockPublicPolicy=true,RestrictPublicBuckets=true"

# Enable default SSE-S3 encryption
aws s3api put-bucket-encryption \
  --bucket sindhu-runners-aadhaar-documents \
  --server-side-encryption-configuration '{
    "Rules":[{"ApplyServerSideEncryptionByDefault":{"SSEAlgorithm":"AES256"}}]
  }'
```

### 2. Add a lifecycle policy (delete abandoned documents after 30 days)

```json
{
  "Rules": [{
    "ID": "delete-pending-payment-docs",
    "Status": "Enabled",
    "Filter": { "Prefix": "events/" },
    "Expiration": { "Days": 30 }
  }]
}
```

### 3. IAM permissions (minimum required)

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": ["s3:PutObject", "s3:GetObject", "s3:DeleteObject"],
      "Resource": "arn:aws:s3:::sindhu-runners-aadhaar-documents/*"
    }
  ]
}
```

In production, attach this policy to an IAM role (EC2 instance profile or ECS task role).

---

## Razorpay Setup

### 1. Create an account
Go to [dashboard.razorpay.com](https://dashboard.razorpay.com) and create an account.

### 2. Get API keys
- Navigate to **Settings → API Keys**
- Generate a **Test mode** key pair for development
- Copy `Key ID` → `RAZORPAY_KEY_ID`
- Copy `Key Secret` → `RAZORPAY_KEY_SECRET`

### 3. Switch to Live mode for production
- Complete KYC verification
- Generate **Live mode** key pair
- Update environment variables

---

## Razorpay Webhook Setup

Webhooks ensure payment is confirmed even if the frontend callback fails.

### 1. Create a webhook

In Razorpay Dashboard:
- **Settings → Webhooks → Add New Webhook**
- **Webhook URL**: `https://your-domain.com/api/v1/payments/webhook`
- **Secret**: Generate a random secret → set as `RAZORPAY_WEBHOOK_SECRET`
- **Events to subscribe**:
  - ✅ `payment.captured`
  - ✅ `payment.failed`

### 2. For local development (ngrok)

```bash
# Install ngrok: https://ngrok.com/download
ngrok http 8080

# Use the https ngrok URL as your webhook URL
# e.g. https://abc123.ngrok.io/api/v1/payments/webhook
```

### 3. Verify webhook is working

Razorpay provides a **Test Webhook** button in the dashboard to send a test event.

---

## Running Locally

```bash
# Terminal 1: Start MySQL
cd deployment && docker compose up mysql -d

# Terminal 2: Run backend
cd backend/sindhueventpay
export RAZORPAY_KEY_ID=rzp_test_xxx
export RAZORPAY_KEY_SECRET=your_secret
export RAZORPAY_WEBHOOK_SECRET=your_webhook_secret
export AWS_REGION=ap-south-1
export AWS_S3_BUCKET=sindhu-runners-aadhaar-documents-dev
export AWS_ACCESS_KEY_ID=AKIA...
export AWS_SECRET_ACCESS_KEY=...
export CORS_ALLOWED_ORIGINS=http://localhost:8080
./mvnw spring-boot:run

# Access the frontend
open http://localhost:8080/register.html?event=EVT2026
```

---

## API Reference

### GET /api/v1/events/{eventCode}

Returns event details.

```bash
curl http://localhost:8080/api/v1/events/EVT2026
```

**Response 200:**
```json
{
  "success": true,
  "data": {
    "eventCode": "EVT2026",
    "eventName": "Goa to Sawantwadi Intercity Run 2026",
    "registrationFee": 500.00,
    "currency": "INR",
    "registrationOpen": true,
    "availableSlots": 950
  }
}
```

---

### POST /api/v1/events/{eventCode}/registrations

Create registration (multipart form).

```bash
curl -X POST http://localhost:8080/api/v1/events/EVT2026/registrations \
  -F "fullName=Prasad Korgaonkar" \
  -F "email=prasad@example.com" \
  -F "mobileNumber=9876543210" \
  -F "aadhaarDocument=@/path/to/aadhaar.jpg"
```

**Response 201:**
```json
{
  "success": true,
  "data": {
    "registrationId": "8d8c7c3a-...",
    "razorpayOrderId": "order_xxx",
    "razorpayKeyId": "rzp_test_xxx",
    "amountInPaise": 50000,
    "currency": "INR"
  }
}
```

---

### POST /api/v1/payments/verify

Verify payment after Razorpay Checkout.

```bash
curl -X POST http://localhost:8080/api/v1/payments/verify \
  -H "Content-Type: application/json" \
  -d '{
    "registrationId": "8d8c7c3a-...",
    "razorpayOrderId": "order_xxx",
    "razorpayPaymentId": "pay_xxx",
    "razorpaySignature": "abc123..."
  }'
```

**Response 200:**
```json
{
  "success": true,
  "data": {
    "registrationNumber": "EVT2026-000042",
    "status": "PAID",
    "eventName": "Goa to Sawantwadi Intercity Run 2026"
  }
}
```

---

### GET /api/v1/registrations/{registrationId}

Poll registration status.

```bash
curl http://localhost:8080/api/v1/registrations/8d8c7c3a-...
```

---

## Testing

```bash
cd backend/sindhueventpay

# Run all tests
./mvnw test

# Run only unit tests (no DB required)
./mvnw test -Dtest="RegistrationServiceTest,PaymentServiceTest,RazorpayServiceTest"
```

### Test coverage:

| Test class | What it tests |
|---|---|
| `RegistrationServiceTest` | Full registration flow, validation, S3 failures, event errors |
| `PaymentServiceTest` | Idempotency, registration number generation, payment failed handling |
| `RazorpayServiceTest` | HMAC signature verification, tampered payload detection |

---

## Production Deployment

### Recommended AWS architecture:

```
Internet → ALB (HTTPS) → EC2 / ECS (Spring Boot) → RDS MySQL 8.0
                                                  → S3 (Aadhaar docs)
                                                  → Razorpay API
```

### Steps:

1. **Build the JAR**:
   ```bash
   cd backend/sindhueventpay
   ./mvnw package -DskipTests
   ```

2. **Set environment variables** in your EC2 user data or ECS task definition.

3. **Use IAM roles** for S3 access (no static credentials in production).

4. **Use RDS MySQL** (Multi-AZ recommended for production).

5. **Enable HTTPS** on the ALB with an ACM certificate.

6. **Set Razorpay webhook URL** to `https://your-domain.com/api/v1/payments/webhook`.

7. **Backup strategy**: Enable automated RDS snapshots (daily) and S3 versioning.

8. **Rollback strategy**: Flyway migrations are versioned. If a deployment fails, the previous JAR can be redeployed — Flyway will not re-run already-applied migrations.

### Health check:

```bash
curl https://your-domain.com/actuator/health
```

---

## Known Limitations / Future Improvements

| Item | Notes |
|---|---|
| **Rate limiting** | Not implemented. Consider adding Spring-Redis or AWS WAF rate limiting in production. |
| **Admin API** | No admin UI. Presigned URL generation endpoint needs an admin auth mechanism (API key or JWT). |
| **Email confirmation** | No confirmation email is sent on successful registration. Consider integrating AWS SES or SendGrid. |
| **Duplicate registrations** | The system does not prevent the same person from registering multiple times. A soft check by email + event could be added. |
| **Aadhaar cleanup** | S3 lifecycle policy handles deletion of abandoned documents, but PAID documents are retained indefinitely. Add an admin deletion workflow. |
| **Refunds** | Razorpay refund flow is not implemented. |
| **Observability** | No APM integration (consider AWS CloudWatch, Datadog, or Sentry). |
| **Webhook retry** | Razorpay retries webhooks up to 15 times. The idempotency layer handles this correctly. |
