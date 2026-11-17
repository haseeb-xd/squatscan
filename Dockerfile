FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -q dependency:go-offline
COPY src ./src
RUN mvn -q -DskipTests package

FROM eclipse-temurin:17-jre
LABEL io.modelcontextprotocol.server.name="io.github.haseeb-xd/squatscan"
COPY --from=build /app/target/squatscan-*.jar /app/squatscan.jar
ENTRYPOINT ["java", "-jar", "/app/squatscan.jar"]
