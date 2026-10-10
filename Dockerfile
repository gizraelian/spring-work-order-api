# Build the tested JAR first: ./mvnw clean verify
# Digest pinned; refresh deliberately for Java 21 security patches.
FROM eclipse-temurin:21-jre-noble@sha256:000fd431958bc81a24abe1e8e5f0f0fd3ae365a594bd50aadb20696805f9408c
WORKDIR /app
COPY --chown=10001:10001 target/work-order-api-0.0.1-SNAPSHOT.jar /app/app.jar
USER 10001:10001
EXPOSE 8080
HEALTHCHECK --interval=10s --timeout=6s --start-period=40s --retries=6 \
    CMD curl --fail --silent --show-error --max-time 5 --output /dev/null http://127.0.0.1:8080/actuator/health || exit 1
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
