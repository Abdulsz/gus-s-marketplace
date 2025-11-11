# Quick Start Guide - Docker Build Fix

## Problem Fixed
Docker build was failing with SSL certificate validation error when Maven tried to download dependencies.

## Solution: Use Google Cloud Build

### To build and deploy to Google Cloud:

```bash
# Submit build to Google Cloud Build
gcloud builds submit --config cloudbuild.yaml .

# This will:
# 1. Build the Docker image using Dockerfile
# 2. Push to Google Container Registry as gcr.io/YOUR_PROJECT_ID/gus-marketplace
# 3. Tag as both :COMMIT_SHA and :latest
```

### Files Added:
- **cloudbuild.yaml** - Google Cloud Build configuration
- **DOCKER_BUILD_TROUBLESHOOTING.md** - Detailed troubleshooting guide
- **Dockerfile** - Optimized with better caching

### For Local Development (Recommended):
Instead of Docker, run the app directly:
```bash
cd backend
./mvnw spring-boot:run
```

### Why This Fix Works:
- Google Cloud Build has properly configured SSL certificates
- The build environment is optimized for Maven downloads
- No local Docker/SSL configuration needed

### Next Steps:
1. Commit these changes (already done)
2. Run `gcloud builds submit --config cloudbuild.yaml .`
3. Deploy the built image to Cloud Run or GKE

For detailed troubleshooting, see DOCKER_BUILD_TROUBLESHOOTING.md
