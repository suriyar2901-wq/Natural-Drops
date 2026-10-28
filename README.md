# Natural Drops - Spring Boot Backend API

## Overview
RESTful API backend for Natural Drops Water Supply Management System built with Spring Boot 3.x and MySQL.

## Latest changes
- Seller shop support: customers, phone orders, 20L can ledger, and daily regular-buyer orders.
- Orders store a future delivery date and time. A reminder goes out the day before.
- New seller accounts are active immediately. Only an admin can deactivate a seller or a buyer.
- Deactivated buyers are rejected on login, token refresh, and later API calls, the same as sellers.
- Partial bills keep the collected amount and the remaining balance. The seller dashboard splits earnings into fully paid, partial collected, and yet to collect.
- Admin product lists include the seller shop name.

## Technologies
- Java 8 (JDK 1.8)
- Spring Boot 2.7.18
- Spring Data JPA
- PostgreSQL
- Maven
- Lombok

## Prerequisites
- JDK 8 or higher (Java 1.8+)
- Maven 3.6+
- MySQL 8.0+
- IDE (IntelliJ IDEA / Eclipse / VS Code)

## Setup Instructions

### 1. Database Setup
```sql
CREATE DATABASE natural_drops;
```

**Note**: This application uses PostgreSQL, not MySQL.

Update `src/main/resources/application.properties` with your PostgreSQL credentials:
```properties
spring.datasource.username=your_username
spring.datasource.password=your_password
```

### 2. Build and Run
```bash
# Navigate to project directory
cd spring-boot-backend

# Build the project
mvn clean install

# Run the application
mvn spring-boot:run
```

The API will start on `http://localhost:8080`

### 3. Default Credentials
- **Username**: admin
- **Password**: admin123
- **Role**: seller

## API Endpoints

### Authentication (`/api/auth`)
- `POST /api/auth/register` - Register new user
- `POST /api/auth/login` - Login
- `POST /api/auth/logout` - Logout
- `GET /api/auth/current` - Get current logged-in user

### Users (`/api/users`)
- `GET /api/users` - Get all users
- `GET /api/users/{id}` - Get user by ID
- `POST /api/users` - Create user
- `PUT /api/users/{id}` - Update user
- `DELETE /api/users/{id}` - Delete user

### Menu (`/api/menu`)
- `GET /api/menu` - Get all menu items
- `GET /api/menu/{id}` - Get item by ID
- `POST /api/menu` - Create item
- `PUT /api/menu/{id}` - Update item
- `DELETE /api/menu/{id}` - Delete item

### Orders (`/api/orders`)
- `GET /api/orders` - Get all orders
- `GET /api/orders/{id}` - Get order by ID
- `GET /api/orders/buyer/{buyerId}` - Get buyer orders
- `POST /api/orders` - Place order
- `PUT /api/orders/{id}/status?status={pending|delivered}` - Update status

### Notifications (`/api/notifications`)
- `GET /api/notifications/admin` - Admin notifications
- `GET /api/notifications/admin/count` - Unread count
- `GET /api/notifications/buyer/{buyerId}` - Buyer notifications

### Settings (`/api/settings`)
- `GET /api/settings` - Get all settings
- `PUT /api/settings` - Update settings

## Database Schema
- `users` - User accounts
- `menu_items` - Product catalog
- `orders` - Customer orders
- `order_items` - Order line items
- `notifications` - Admin notifications
- `buyer_notifications` - Buyer notifications
- `settings` - Application settings

## Testing with curl

### Register
```bash
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"username":"testuser","password":"password123","role":"buyer"}'
```

### Login
```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}' \
  -c cookies.txt
```

### Get Menu (with session)
```bash
curl -X GET http://localhost:8080/api/menu \
  -b cookies.txt
```

## Project Structure
```
src/main/java/com/naturaldrops/
├── NaturalDropsApplication.java
├── config/
├── controller/
├── dto/
├── entity/
├── exception/
├── repository/
└── service/
```

## Development Notes
- Uses BCrypt for password encryption
- JWT authentication for `/api/**` (see `JwtTokenFilter`)
- CORS enabled for Expo web / Vercel
- Auto-creates default admin and menu items on first run
- Manual UPI bill claims (`/api/payments/order/...`) remain the default buyer flow

## Cashfree Payment Gateway (Sandbox — Phase 1 backend + Phase 2 frontend)

Buyer bill payments can also be collected via Cashfree. Orders are created **only on the server**. Credentials never leave the backend. Manual UPI/QR/UTR claim + seller confirm is unchanged.

**Frontend (Phase 2)** in `natural-drops-frontend`: Buyer Payments → Cashfree checkout on web (`cashfree-js` modal) and native (`react-native-cashfree-pg-sdk` web checkout). Client calls `POST /api/payments/cashfree/orders` then `GET .../status` after the SDK returns. Native requires an Expo dev / EAS build (not Expo Go).

### Environment variables (Render / Docker / local)

| Variable | Required | Description |
|----------|----------|-------------|
| `CASHFREE_CLIENT_ID` | Yes (for Cashfree APIs) | Cashfree App ID from Merchant Dashboard |
| `CASHFREE_CLIENT_SECRET` | Yes (for Cashfree APIs) | Cashfree Secret Key — never commit |
| `CASHFREE_ENV` | No | `SANDBOX` (default) or `PRODUCTION` |
| `CASHFREE_API_VERSION` | No | Defaults to `2023-08-01` |

### New API endpoints

| Method | Path | Auth | Purpose |
|--------|------|------|---------|
| `POST` | `/api/payments/cashfree/orders` | Buyer JWT | Body `{ "orderId": <ndOrderId> }`. Amount = server unpaid balance. Returns `paymentSessionId`, `cfOrderId`, `environment`. |
| `GET` | `/api/payments/cashfree/orders/{cfOrderId}/status` | Buyer JWT | Fetches status from Cashfree; applies bill payment only when `PAID` (idempotent). |
| `POST` | `/api/payments/cashfree/webhook` | Public + HMAC signature | Verifies `x-webhook-signature` / `x-webhook-timestamp` on raw body; confirms with Cashfree before marking paid. |

### Sandbox setup

1. Create a Cashfree test account and generate Sandbox API keys.
2. Set `CASHFREE_CLIENT_ID`, `CASHFREE_CLIENT_SECRET`, and `CASHFREE_ENV=SANDBOX` on Render (or local env / `application-local.properties` mapped via env).
3. In Cashfree Dashboard → Developers → Webhooks (Sandbox), set notify URL to:  
   `https://<your-render-host>/api/payments/cashfree/webhook`
4. Redeploy the backend so Hibernate can create the `cashfree_payments` table (`ddl-auto=update`).
5. Call create-order with a buyer JWT for an unpaid order, then complete payment in Cashfree checkout (frontend Phase 2) or use Cashfree’s test tools.
6. Confirm order `paymentStatus` becomes `PAID` only after webhook or status poll shows Cashfree `PAID`. Pending/failed never marks paid.

### Tests

```bash
mvn -Dtest=CashfreeSignatureVerifierTest,CashfreePaymentServiceTest,CashfreePaymentControllerTest,HealthControllerTest test
```

## Author
Natural Drops Water Supply System

# Natural-Drops
