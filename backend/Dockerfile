# CampusHub API image (DEPLOYMENT §4). Multi-stage: the build stage compiles, the runtime stage holds only the JRE.
# Base images are pinned by digest (ADR-022). Run as a non-root user.

FROM eclipse-temurin:21-jdk@sha256:ebfbb18d11ff163ee83b21258acf12248af48d22a0fdfdc68fa1a1516c05976a AS build
WORKDIR /workspace
COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline
COPY src src
RUN ./mvnw -B -DskipTests package

FROM eclipse-temurin:21-jre@sha256:0b99b3376fbb50dbf9c0af8535fdd5b8eb0d3fdfc8b1e4967f383b6919f6901a
RUN groupadd --system campushub \
    && useradd --system --gid campushub --home-dir /app --shell /usr/sbin/nologin campushub \
    && mkdir -p /var/campushub/storage \
    && chown campushub:campushub /var/campushub/storage
WORKDIR /app
COPY --from=build --chown=campushub:campushub /workspace/target/campushub-api.jar app.jar
USER campushub
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
