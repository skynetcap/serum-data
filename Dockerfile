#
# Build stage
#
FROM maven:3.8.6-eclipse-temurin-17 AS build

# serum-data
COPY src /home/app/src
COPY pom.xml /home/app
RUN mvn -f /home/app/pom.xml clean package -DskipTests


#
# Package stage
#
FROM ghcr.io/graalvm/jdk:22.2.0
COPY --from=build /home/app/target/serum-data-1.2.0-SNAPSHOT.jar /usr/local/lib/serumdata.jar
#ENV JAVA_TOOL_OPTIONS -agentlib:jdwp=transport=dt_socket,address=*:8000,server=y,suspend=n
ENV OPENSERUM_ENDPOINT=GENESYSGO
EXPOSE 8080
ENTRYPOINT ["java","-jar","/usr/local/lib/serumdata.jar"]