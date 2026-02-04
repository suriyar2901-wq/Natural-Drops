# Quick Start Guide

## Setting Up Configuration

The application requires database credentials and other configuration. For local development, we recommend using `application-local.properties` (most reliable).

### Option 1: Using application-local.properties (Recommended for Local Development)

This is the most reliable method for local development. The file is already created and gitignored for security.

1. **Edit the local properties file:**
   ```bash
   nano src/main/resources/application-local.properties
   # or
   vim src/main/resources/application-local.properties
   ```

2. **Fill in your actual database credentials:**
   - Replace `spring.datasource.username` with your Prisma Cloud database username
   - Replace `spring.datasource.password` with your Prisma Cloud database password
   - Update other values as needed (mail, JWT secret, etc.)

3. **The file is already gitignored** - your credentials won't be committed to version control.

**Benefits:**
- ✅ Most reliable - Spring Boot loads it automatically
- ✅ No dependency on bash environment variables
- ✅ Works consistently across different shells and IDEs
- ✅ Already configured in the start script

### Option 2: Using .env file

1. **Create .env file:**
   ```bash
   ./setup-env.sh
   ```

2. **Edit .env file with your credentials:**
   ```bash
   nano .env
   # or
   vim .env
   ```

3. **Fill in the required values:**
   - `DATABASE_USERNAME`: Your Prisma Cloud database username
   - `DATABASE_PASSWORD`: Your Prisma Cloud database password
   - `MAIL_USERNAME`: Your Gmail address (optional)
   - `MAIL_PASSWORD`: Your Gmail app password (optional)
   - `JWT_SECRET`: A secure random string, minimum 32 characters

### Option 3: Export environment variables directly

```bash
export DATABASE_USERNAME="your_username"
export DATABASE_PASSWORD="your_password"
export JWT_SECRET="your-secure-random-secret-key-minimum-32-characters"
export MAIL_USERNAME="your_email@gmail.com"  # Optional
export MAIL_PASSWORD="your_app_password"     # Optional
```

## Starting the Application

After configuring your credentials (using any of the methods above):

```bash
./start-backend.sh
```

The application will:
- **Automatically detect** `application-local.properties` and use the `local` profile (recommended)
- **OR** load environment variables from `.env` if it exists
- **OR** use exported environment variables
- Build and start the Spring Boot server
- Run on `http://localhost:8080`

**Note:** If both `application-local.properties` and `.env` exist, the script will prefer `application-local.properties`.

### If you run Maven manually

If you use `mvn spring-boot:run` directly, make sure the local profile is enabled:

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

## Troubleshooting

### Error: "Database credentials not set" or "No configuration found"
- **Recommended fix:** Edit `src/main/resources/application-local.properties` and fill in your database credentials
- **Alternative:** Create `.env` file: `./setup-env.sh` and fill in values
- **Alternative:** Export environment variables before running the script

### Error: "The server requested password-based authentication"
- Check that your database password is set correctly in `application-local.properties` or `.env`
- Verify your Prisma Cloud database credentials are correct
- Ensure there are no extra spaces around the `=` sign in your configuration

### Error: "Could not resolve placeholder"
- If using `.env`: Ensure all required environment variables are set
- Check that `.env` file doesn't have syntax errors (no spaces around `=`)
- **Try using `application-local.properties` instead** - it's more reliable

### Application starts but can't connect to database
- Verify your Prisma Cloud database is accessible
- Check that SSL is enabled (required for Prisma Cloud)
- Ensure the database URL, username, and password are correct

## Required Environment Variables

| Variable | Required | Description |
|----------|----------|-------------|
| `DATABASE_USERNAME` | Yes | Prisma Cloud database username |
| `DATABASE_PASSWORD` | Yes | Prisma Cloud database password |
| `JWT_SECRET` | Yes | Secret key for JWT tokens (min 32 chars) |
| `MAIL_USERNAME` | No | Gmail address for email service |
| `MAIL_PASSWORD` | No | Gmail app password |
| `FRONTEND_BASE_URL` | No | Frontend URL (defaults to http://localhost:8081) |
| `PORT` | No | Server port (defaults to 8080) |

