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

USER root

RUN apt-get update \
    && apt-get upgrade -y \
    && rm -rf /var/lib/apt/lists/*

# The Android SDK command-line tools bundled in the base image currently ship
# Bouncy Castle 1.79 jars that are not used by this app build but are still
# detected by image scanners. Remove them from the final builder image so Trivy
# does not report CVE-2025-14813 from unused SDK tool jars.
RUN rm -rf /opt/android-sdk-linux/cmdline-tools/latest/lib/external/org/bouncycastle

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
    ./gradlew :app:validateEnvironmentIsolation :app:verifyAppCheckDependencyIsolation :app:assembleLocalDebug --no-daemon --console=plain
