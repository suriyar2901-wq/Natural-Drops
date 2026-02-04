#!/bin/bash

# Setup script to create .env file from .env.example
# This helps configure environment variables for local development

cd "$(dirname "$0")"

echo "=========================================="
echo "Natural Drops Backend - Environment Setup"
echo "=========================================="
echo ""

if [ -f ".env" ]; then
    echo "⚠️  .env file already exists."
    read -p "Do you want to overwrite it? (y/N): " -n 1 -r
    echo
    if [[ ! $REPLY =~ ^[Yy]$ ]]; then
        echo "Setup cancelled."
        exit 0
    fi
fi

echo "Creating .env file from template..."
cp .env.example .env

echo ""
echo "✅ .env file created!"
echo ""
echo "⚠️  IMPORTANT: Please edit .env file and fill in your actual values:"
echo "   - DATABASE_USERNAME: Your Prisma Cloud database username"
echo "   - DATABASE_PASSWORD: Your Prisma Cloud database password"
echo "   - MAIL_USERNAME: Your Gmail address"
echo "   - MAIL_PASSWORD: Your Gmail app password"
echo "   - JWT_SECRET: A secure random string (minimum 32 characters)"
echo ""
echo "You can edit the file with:"
echo "   nano .env"
echo "   or"
echo "   vim .env"
echo ""


