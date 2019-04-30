FROM openjdk:11-jdk-slim
MAINTAINER napster@npstr.space

ENV ENV docker

WORKDIR /opt/icu

ENTRYPOINT ["java", "-Xmx256m", "-jar", "icu.jar"]

COPY build/libs/icu.jar /opt/icu/icu.jar
