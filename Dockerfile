FROM eclipse-temurin:21-jdk
MAINTAINER napster@npstr.space

ENV ENV docker

WORKDIR /opt/icu

ENTRYPOINT ["java", "-Xmx256m", "-jar", "icu.jar"]

COPY build/libs/icu.jar /opt/icu/icu.jar
