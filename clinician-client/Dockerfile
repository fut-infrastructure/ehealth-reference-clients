# syntax=docker/dockerfile:1.7
# Multi-stage build for clinician-client.
# TODO §5.5: switch runtime to distroless once layered JAR is wired through (spring-boot:build-image is the alternative).

FROM eclipse-temurin:21-jdk AS build
WORKDIR /src
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
COPY common/pom.xml common/pom.xml
COPY clinician-client/pom.xml clinician-client/pom.xml
COPY citizen-client/pom.xml citizen-client/pom.xml
RUN ./mvnw -B -q -pl clinician-client -am dependency:go-offline || true
COPY common/ common/
COPY clinician-client/ clinician-client/
RUN ./mvnw -B -pl clinician-client -am -DskipTests package

FROM eclipse-temurin:21-jre
RUN groupadd --gid 1001 spring && useradd --uid 1001 --gid spring --no-create-home spring
WORKDIR /app
COPY --from=build /src/clinician-client/target/clinician-client-*-exec.jar /app/app.jar
RUN chown spring:spring /app/app.jar
USER 1001
EXPOSE 8080
ENTRYPOINT ["java","-jar","/app/app.jar"]
