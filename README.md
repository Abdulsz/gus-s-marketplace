# Gus's Marketplace

A campus-exclusive e‑commerce platform for Augustana College students.

## Overview
Gus's Marketplace is designed as a private marketplace to connect students within the Augustana College community. The repository contains the backend service and supporting infrastructure/configuration for containerization and cloud CI/CD.

## Repository layout (high level)
- backend/ — Java backend implementation (service source code and resources)
- Dockerfile — container image definition for the application
- .dockerignore — files excluded from the Docker build context
- cloudbuild.yaml — Google Cloud Build configuration
- QUICK_START.md — quick start guide and runtime notes
- DOCKER_BUILD_TROUBLESHOOTING.md — common Docker build troubleshooting tips
- .vscode/, .idea/ — editor/IDE configuration files

## Technologies
- Language
  - Java (Spring) — backend implementation (see backend/ for source and dependencies)

- Containerization & Images
  - Docker — Dockerfile and .dockerignore included for building application images

- CI / CD / Cloud Build
  - Google Cloud Build (cloudbuild.yaml) — pipeline configuration for GCP builds

- Dev & Tooling
  - VS Code and IntelliJ IDEA workspace settings (.vscode, .idea)
  - Git / GitHub for source control and repository hosting

- Documentation 
  - QUICK_START.md — quick start and usage notes
  - DOCKER_BUILD_TROUBLESHOOTING.md — tips for resolving Docker build issues

## Notes
- The backend implementation with content moderation and build tooling details (build tool, frameworks, and dependencies) are located in the backend/ directory — consult that folder for exact frameworks (e.g., Spring, build system) and runtime requirements.
- CI/CD manifests are provided for Google Cloud Build for flexible cloud deployment/testing pipelines.