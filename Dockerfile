FROM eclipse-temurin:17-jdk
RUN apt-get update \
    && apt-get install -y --no-install-recommends awscli \
    && rm -rf /var/lib/apt/lists/*
VOLUME /tmp
ARG JAR_FILE=build/libs/*.jar
COPY ${JAR_FILE} app.jar
EXPOSE 8080
ENTRYPOINT ["java","-jar","/app.jar"]
