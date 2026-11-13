# --- 1. Superadmin UI ---
FROM node:22-alpine AS ui
WORKDIR /ui
COPY admin-ui/package.json admin-ui/package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY admin-ui/ ./
RUN npm run build

# --- 2. Backend ---
FROM maven:3.9-eclipse-temurin-26 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -q -B dependency:go-offline
COPY src ./src
COPY --from=ui /ui/dist ./src/main/resources/static/admin
# Testlar CI'da ishga tushiriladi (.github/workflows/ci.yml); image yig'ishda o'tkazib yuboriladi
RUN mvn -q -B -DskipTests package

# --- 3. Run ---
FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd --system --uid 10001 sellerflow
COPY --from=build /app/target/sellerflow-*.jar /app/app.jar
USER sellerflow
EXPOSE 8080
# N-7: OOM bo'lsa JVM chiqadi (Docker restart: unless-stopped qayta ishga tushiradi); heap 65% — non-heap uchun joy
ENV JAVA_OPTS="-XX:MaxRAMPercentage=65 -XX:+ExitOnOutOfMemoryError -Duser.timezone=UTC"
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
