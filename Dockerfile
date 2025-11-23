FROM eclipse-temurin:25-jdk
LABEL org.opencontainers.image.authors="napster@npstr.space"

ENV ENV=docker

WORKDIR /opt/icu

ENTRYPOINT ["java", "-Xmx256m", "-jar", "icu.jar"]

COPY build/libs/icu.jar /opt/icu/icu.jar
