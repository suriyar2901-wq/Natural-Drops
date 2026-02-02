# Natural Drops - Spring Boot Backend API

## Overview
RESTful API backend for Natural Drops Water Supply Management System built with Spring Boot 3.x and MySQL.

## Technologies
- Java 17
- Spring Boot 3.2.0
- Spring Data JPA
- MySQL 8.0+
- Maven
- Lombok

## Prerequisites
- JDK 17 or higher
- Maven 3.6+
- MySQL 8.0+
- IDE (IntelliJ IDEA / Eclipse / VS Code)

## Setup Instructions

### 1. Database Setup
```sql
CREATE DATABASE natural_drops;
```

Update `src/main/resources/application.properties` with your MySQL credentials:
```properties
spring.datasource.username=root
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
- Session-based authentication
- CORS enabled for Angular frontend (localhost:4200)
- Auto-creates default admin and menu items on first run

## Author
Natural Drops Water Supply System

# Natural-Drops
