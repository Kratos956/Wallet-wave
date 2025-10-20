FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

# Copy built jar from target/ (after mvn package)
COPY target/*.jar app.jar

EXPOSE 8000
ENTRYPOINT ["java","-jar","/app/app.jar"]
