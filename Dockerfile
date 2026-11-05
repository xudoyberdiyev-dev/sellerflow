# --- build ---
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -q -B dependency:go-offline
COPY src ./src
# Testlar alohida bosqichda ishga tushiriladi (README: "Testlar"); image yig'ishda o'tkazib yuboriladi
RUN mvn -q -B -DskipTests package

# --- run ---
FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd --system --uid 10001 sellerflow
COPY --from=build /app/target/sellerflow-*.jar /app/app.jar
USER sellerflow
EXPOSE 8080
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -Duser.timezone=UTC"
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
