FROM eclipse-temurin:25-jre
WORKDIR /app
COPY --chown=10001:10001 target/carrim-api-0.0.8.jar app.jar
USER 10001:10001
EXPOSE 8082
ENTRYPOINT ["java","-XX:MaxRAMPercentage=70","-jar","/app/app.jar","--spring.profiles.active=prod"]
