FROM eclipse-temurin:25-jdk AS build
WORKDIR /build
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
COPY src/ src/
RUN sh mvnw --batch-mode -DskipTests package

FROM eclipse-temurin:25-jre
WORKDIR /app
COPY --from=build --chown=10001:10001 /build/target/carrim-api-*.jar app.jar
COPY --chown=10001:10001 config/supabase-ca.crt /app/supabase-ca.crt
USER 10001:10001
ENV SPRING_PROFILES_ACTIVE=prod
EXPOSE 8082
ENTRYPOINT ["java","-XX:MaxRAMPercentage=60","-XX:ActiveProcessorCount=2","-jar","/app/app.jar"]
