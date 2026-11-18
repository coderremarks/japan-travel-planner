# Container image of the Spring Boot API.
#
#   docker build -t tabiji-api ./backend
#
# The image build only compiles and packages. Tests, static analysis and coverage gates run in CI
# (./mvnw verify) before an image is ever built. Base images are pinned by digest; Dependabot
# proposes updates.

# ---- Build ------------------------------------------------------------------------------------------
# Maven 3.9.16 is the version the Maven wrapper pins, so local, CI and image builds agree.
FROM maven:3.9.16-eclipse-temurin-21-alpine@sha256:308cba8b638ed7e4658cea3f8399066219466211c805f6d5728c3c9c7614661b AS build
WORKDIR /workspace

COPY pom.xml ./
COPY .mvn/ .mvn/
COPY config/ config/
COPY optimizer/ optimizer/
# The reactor lists the benchmarks module; its POM is enough because it is not built here.
COPY benchmarks/pom.xml benchmarks/
COPY app/ app/

RUN --mount=type=cache,target=/root/.m2/repository \
    mvn --batch-mode --no-transfer-progress --projects app --also-make package -DskipTests \
 && cp app/target/tabiji-app-*.jar application.jar \
 && java -Djarmode=tools -jar application.jar extract --layers --destination extracted

# ---- Runtime ----------------------------------------------------------------------------------------
FROM eclipse-temurin:21.0.13_9-jre-alpine-3.24@sha256:ac05c3c42b7c2f1a52c9bd52f01041fa1958231b15ebeae7420429cbc5baf474

RUN addgroup -S -g 10001 tabiji \
 && adduser -S -D -H -u 10001 -G tabiji -s /sbin/nologin tabiji

WORKDIR /app
# Least to most frequently changing, so a code change only replaces the small last layer. The files
# stay owned by root: the process can read its code but not modify it.
COPY --from=build /workspace/extracted/dependencies/ ./
COPY --from=build /workspace/extracted/spring-boot-loader/ ./
COPY --from=build /workspace/extracted/snapshot-dependencies/ ./
COPY --from=build /workspace/extracted/application/ ./

USER 10001:10001
ENV SPRING_PROFILES_ACTIVE=prod \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
EXPOSE 8080

HEALTHCHECK --interval=10s --timeout=3s --start-period=90s --retries=6 \
  CMD wget -q -O /dev/null http://127.0.0.1:8080/actuator/health/readiness || exit 1

ENTRYPOINT ["java", "-jar", "application.jar"]
