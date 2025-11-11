# Docker Build SSL Certificate Issue - Diagnosis and Solutions

## Problem
The Docker build fails with the following error when Maven tries to download Spring Boot dependencies:

```
PKIX path building failed: sun.security.provider.certpath.SunCertPathBuilderException: 
unable to find valid certification path to requested target
```

## Root Cause
This error occurs when Maven inside the Docker container cannot validate SSL certificates for Maven Central repository (https://repo.maven.apache.org). This can happen due to:

1. **Outdated CA certificates** in the Docker base image
2. **Corporate proxies** intercepting HTTPS traffic with self-signed certificates
3. **Network configuration** in the build environment (Google Cloud Build, Docker Desktop, etc.)
4. **Java truststore** not containing the required certificate authorities

## Solutions

### Solution 1: Use Google Cloud Build (Recommended for GCP)
If you're deploying to Google Cloud, use Cloud Build instead of building locally:

1. Ensure `cloudbuild.yaml` is configured (already added to the repository)
2. Run the build in Google Cloud Build:
   ```bash
   gcloud builds submit --config cloudbuild.yaml .
   ```

Cloud Build's environment typically has proper SSL certificates configured.

### Solution 2: Update Dockerfile with Better Layer Caching
The Dockerfile has been updated to:
- Copy `pom.xml` first to enable better Docker layer caching
- Download dependencies in a separate step

This doesn't fix the SSL issue but improves build performance.

### Solution 3: Fix SSL Certificates Locally (If building locally)

If you need to build locally and encounter SSL issues:

**Option A: Use newer Docker image**
- Already using `maven:3.9-eclipse-temurin-21` which should have updated certificates

**Option B: Configure Maven to use HTTP mirror** (not recommended for production)
Create a `.m2/settings.xml` in the backend directory:
```xml
<settings>
  <mirrors>
    <mirror>
      <id>central-mirror</id>
      <url>http://insecure.repo1.maven.org/maven2</url>
      <mirrorOf>central</mirrorOf>
    </mirror>
  </mirrors>
</settings>
```

**Option C: If behind corporate proxy**
Add proxy settings to Dockerfile before the Maven build:
```dockerfile
ENV HTTP_PROXY=http://proxy.company.com:8080
ENV HTTPS_PROXY=http://proxy.company.com:8080
```

### Solution 4: Use Maven Wrapper with Local Maven Repository
Mount your local `.m2` repository to avoid re-downloading:
```bash
docker build -v ~/.m2:/root/.m2 -t gus-marketplace .
```

## Testing
After applying fixes, test the build:

```bash
# Local build
docker build -t gus-marketplace .

# Google Cloud Build
gcloud builds submit --config cloudbuild.yaml .
```

## Current Status
- ✅ `cloudbuild.yaml` added for Google Cloud Build
- ✅ Dockerfile optimized with better layer caching
- ✅ CMD updated to use JSON format (best practice)
- ⚠️  Local Docker build may fail due to SSL certificate issues (environment-specific)
- ✅ Google Cloud Build should work correctly with the provided configuration

## Recommendations
1. **For production deployments to Google Cloud**: Use Cloud Build (cloudbuild.yaml)
2. **For local development**: Use the backend directly with `./mvnw spring-boot:run`
3. **For local Docker testing**: Ensure Docker Desktop has internet access and updated CA certificates
