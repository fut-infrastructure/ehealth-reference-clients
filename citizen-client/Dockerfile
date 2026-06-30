# syntax=docker/dockerfile:1.7
FROM eclipse-temurin:21-jdk AS build
WORKDIR /src
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
COPY common/pom.xml common/pom.xml
COPY clinician-client/pom.xml clinician-client/pom.xml
COPY citizen-client/pom.xml citizen-client/pom.xml
RUN ./mvnw -B -q -pl citizen-client -am dependency:go-offline || true
COPY common/ common/
COPY citizen-client/ citizen-client/
RUN ./mvnw -B -pl citizen-client -am -DskipTests package

FROM eclipse-temurin:21-jre
RUN groupadd --gid 1001 spring && useradd --uid 1001 --gid spring --no-create-home spring
WORKDIR /app
COPY --from=build /src/citizen-client/target/citizen-client-*.jar /app/app.jar
RUN chown spring:spring /app/app.jar
USER 1001
EXPOSE 8090
ENTRYPOINT ["java","-jar","/app/app.jar"]
