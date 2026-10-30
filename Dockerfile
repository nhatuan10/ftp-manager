# --- GIAI ĐOẠN 1: Build file JAR bằng Maven (Dùng Java 21) ---
FROM maven:3.9.6-eclipse-temurin-21 AS build
WORKDIR /app
COPY . .
RUN mvn clean package -DskipTests

# --- GIAI ĐOẠN 2: Chạy ứng dụng ---
FROM eclipse-temurin:21-jre-alpine
VOLUME /tmp
# Copy file jar từ giai đoạn build sang
COPY --from=build /app/target/*.jar app.jar
ENTRYPOINT ["java","-jar","/app.jar"]