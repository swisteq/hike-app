FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY target/wyprawy-0.0.1-SNAPSHOT.jar app.jar
VOLUME /app/uploads
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
