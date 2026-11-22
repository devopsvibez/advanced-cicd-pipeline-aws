FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /work
COPY app/App.java app/AppTest.java ./
RUN mkdir classes && javac -Xlint:all -Werror -d classes App.java AppTest.java \
    && java -cp classes AppTest \
    && rm classes/AppTest.class

FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
COPY --from=build --chown=10001:10001 /work/classes/ /app/
ENV PORT=8080
USER 10001:10001
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=70.0", "-cp", "/app", "App"]
