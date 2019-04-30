FROM openjdk:11-jdk-slim

ENV ENV docker

RUN mkdir -p /opt/icu

COPY build/libs/icu.jar /opt/icu/icu.jar

WORKDIR /opt/icu
ENTRYPOINT ["java", "-jar", "-Xmx256m", "icu.jar"]
