#
# Build stage
#
FROM maven:3.8.6-eclipse-temurin-17 AS build

RUN apt-get update \
    && apt-get install -y unzip

# solanaj (dependency, not in a public registry)
ADD https://github.com/skynetcap/solanaj/archive/refs/heads/main.zip /home/solanaj/solanaj.zip
RUN unzip /home/solanaj/solanaj.zip -d /home/solanaj/
RUN mvn -f /home/solanaj/solanaj-main/pom.xml clean install -DskipTests

# solanaj-programs (dependency, not in a public registry)
ADD https://github.com/skynetcap/solanaj-programs/archive/refs/heads/master.zip /home/solanaj-programs/solanaj-programs.zip
RUN unzip /home/solanaj-programs/solanaj-programs.zip -d /home/solanaj-programs/
RUN mvn -f /home/solanaj-programs/solanaj-programs-master/pom.xml clean install -DskipTests

# serum-data
COPY src /home/app/src
COPY pom.xml /home/app
RUN mvn -f /home/app/pom.xml clean package -DskipTests


#
# Package stage
#
FROM openjdk:17.0.2-slim
COPY --from=build /home/app/target/serum-data-1.2.0-SNAPSHOT.jar /usr/local/lib/serumdata.jar
ENV JAVA_TOOL_OPTIONS -agentlib:jdwp=transport=dt_socket,address=*:8000,server=y,suspend=n
ENV OPENSERUM_ENDPOINT=GENESYSGO
ENV SKIP_CACHE_DELAY=FALSE
EXPOSE 8080
ENTRYPOINT ["java","-jar","/usr/local/lib/serumdata.jar"]