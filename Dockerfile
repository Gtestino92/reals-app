# syntax=docker/dockerfile:1.7

FROM ghcr.io/cirruslabs/android-sdk:36 AS build

ARG IMAGE_REPOSITORY="ghcr.io/gtestino92/reals-app"
ARG IMAGE_TAG="local"
ARG IMAGE_REVISION="unknown"

LABEL org.opencontainers.image.source="https://github.com/Gtestino92/reals-app"
LABEL org.opencontainers.image.description="Reals Android app build image"
LABEL org.opencontainers.image.revision="${IMAGE_REVISION}"

ENV IMAGE_REPOSITORY="${IMAGE_REPOSITORY}" \
    IMAGE_TAG="${IMAGE_TAG}" \
    IMAGE_REVISION="${IMAGE_REVISION}" \
    GRADLE_USER_HOME="/home/gradle/.gradle"

WORKDIR /workspace

COPY gradle gradle
COPY gradlew gradlew.bat settings.gradle.kts build.gradle.kts gradle.properties ./
COPY app/build.gradle.kts app/build.gradle.kts

RUN chmod +x ./gradlew

RUN --mount=type=cache,target=/home/gradle/.gradle \
    ./gradlew :app:dependencies --configuration localDebugRuntimeClasspath --no-daemon --console=plain

COPY app app
COPY docs docs

RUN --mount=type=cache,target=/home/gradle/.gradle \
    ./gradlew :app:assembleLocalDebug --no-daemon --console=plain
