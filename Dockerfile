#
# Build stage
#
FROM maven:3.8.6-eclipse-temurin-17 AS build

# Yourkit
#RUN apt-get update \
#    && apt-get install unzip -y \
#    && apt-get install wget -y
#
#RUN wget https://www.yourkit.com/download/docker/YourKit-JavaProfiler-2022.3-docker.zip -P /home/ && \
#  unzip /home/YourKit-JavaProfiler-2022.3-docker.zip -d /usr/local && \
#  rm /home/YourKit-JavaProfiler-2022.3-docker.zip

# serum-data
COPY src /home/app/src
COPY pom.xml /home/app
RUN mvn -T 1C -f /home/app/pom.xml clean package -DskipTests -Dmaven.test.skip


#
# Package stage
#
FROM openjdk:17.0.2-slim

# Yourkit
# COPY --from=build /usr/local/ /usr/local/
#ENV JAVA_TOOL_OPTIONS -agentpath:/usr/local/YourKit-JavaProfiler-2022.3/bin/linux-x86-64/libyjpagent.so=port=10001,listen=all

# Remote Debugging
#ENV JAVA_TOOL_OPTIONS -agentlib:jdwp=transport=dt_socket,address=*:8000,server=y,suspend=n

COPY --from=build /home/app/target/serum-data-2.0.0-SNAPSHOT.jar /usr/local/lib/serumdata.jar
EXPOSE 8080
ENTRYPOINT ["java","-jar","/usr/local/lib/serumdata.jar"]