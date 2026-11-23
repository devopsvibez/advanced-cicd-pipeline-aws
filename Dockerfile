FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
COPY --chown=10001:10001 target/pipeline-lab-1.0.0-SNAPSHOT.jar /app/app.jar
ENV PORT=8080
USER 10001:10001
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=70.0", "-jar", "/app/app.jar"]
