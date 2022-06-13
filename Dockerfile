#
# Build stage
#
FROM maven:3.6.0-jdk-11-slim AS build

# solanaj (dependency, not in a public registry)
ADD https://github.com/skynetcap/solanaj/archive/refs/tags/v1.6-SNAPSHOT.zip /home/solanaj/solanaj.zip
RUN unzip /home/solanaj/solanaj.zip -d /home/solanaj/
RUN mvn -f /home/solanaj/solanaj-1.6-SNAPSHOT/pom.xml clean install -DskipTests

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
FROM openjdk:11-jre-slim
COPY --from=build /home/app/target/serumdata-0.0.1-SNAPSHOT.jar /usr/local/lib/serumdata.jar
EXPOSE 8080
ENTRYPOINT ["java","-jar","/usr/local/lib/serumdata.jar"]