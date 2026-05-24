#!/bin/sh
set -eu

# Render and Neon often provide DATABASE_URL as postgres:// or postgresql://
# Spring Boot requires jdbc:postgresql://
if [ -n "${DATABASE_URL:-}" ]; then
  case "$DATABASE_URL" in
    postgres://*)
      export DATABASE_URL="jdbc:postgresql://${DATABASE_URL#postgres://}"
      ;;
    postgresql://*)
      export DATABASE_URL="jdbc:postgresql://${DATABASE_URL#postgresql://}"
      ;;
  esac
fi

exec java \
  -Djava.security.egd=file:/dev/./urandom \
  -jar /app/app.jar \
  --server.port="${PORT:-8080}"
