# Anton Distributed Storage Platform

A production-grade distributed object storage platform inspired by Amazon S3 and Google Drive — built with Spring Boot, PostgreSQL, Redis, and React.

## Architecture

```
┌──────────────────────────────────────────────────────────────┐
│                        Client (React)                        │
└────────────────────────────┬─────────────────────────────────┘
                             │ HTTP / REST
┌────────────────────────────▼─────────────────────────────────┐
│                   Spring Boot API (JWT Auth)                  │
│  ┌─────────────┐  ┌──────────────┐  ┌────────────────────┐  │
│  │  FileService│  │ ShareService │  │ ChunkUploadService │  │
│  └──────┬──────┘  └──────┬───────┘  └─────────┬──────────┘  │
│         │                │                     │             │
│  ┌──────▼──────────────────────────────────────▼──────────┐  │
│  │              StorageEngine (Local Disk → S3/MinIO)     │  │
│  └────────────────────────────────────────────────────────┘  │
└──────┬───────────────────────┬───────────────────────────────┘
       │                       │
┌──────▼───────┐   ┌──────────▼─────────┐
│  PostgreSQL  │   │  Redis (Cache/TTL)  │
│  (Metadata)  │   │  Metadata + tokens  │
└──────────────┘   └────────────────────┘
```

## Features

| Feature | Implementation |
|---|---|
| File Upload | Multipart POST with MIME detection |
| Chunked Upload | Init → Chunk → Complete (resumable) |
| File Versioning | Per-file version history with rollback |
| Share Links | Token-based public URLs with expiry |
| Access Control | JWT authentication, owner-scoped ops |
| Metadata Service | PostgreSQL + Redis cache (5-min TTL) |
| Storage Quota | Per-user limits enforced on upload |
| Integrity Checks | SHA-256 checksum on every write |
| Containerization | Docker Compose + Kubernetes manifests |
| Auto-scaling | HPA on CPU (2–10 replicas) |

## System Design Concepts Demonstrated

- **Consistent storage key scheme** — `ownerId/virtualPath.vN` enables shard-aware routing
- **Metadata / data separation** — PostgreSQL holds metadata; disk (or S3) holds bytes
- **Cache-aside pattern** — Redis caches file lists with `@Cacheable`, evicted on writes
- **Chunked transfer** — large files split into 5MB chunks, assembled server-side
- **Resume points** — chunk session tracks received-chunk bitmask; client re-uploads missing chunks only
- **Replication-ready** — `StorageEngine` interface hides storage backend; swap local → S3/MinIO with one config change

## Quick Start

### Option A — Local dev (H2 + no Redis)

```bash
cd backend
mvn spring-boot:run
# API available at http://localhost:8080

cd frontend
npm install && npm run dev
# UI at http://localhost:3000
```

### Option B — Full Docker stack (Postgres + Redis)

```bash
docker-compose up --build
# UI at http://localhost:80
# API at http://localhost:8080
```

### Option C — Kubernetes

```bash
kubectl apply -f k8s/manifests.yaml
```

## API Reference

```
POST /api/auth/register        Register
POST /api/auth/login           Login → JWT

GET  /api/files                List files
POST /api/files                Upload file
GET  /api/files/{id}/download  Download (version param optional)
GET  /api/files/{id}/versions  Version history
DELETE /api/files/{id}         Soft delete
GET  /api/files/stats          Storage quota stats

POST /api/uploads/init                         Init chunked upload
PUT  /api/uploads/{id}/chunks/{index}          Upload chunk
POST /api/uploads/{id}/complete                Assemble file
GET  /api/uploads/{id}/status                  Resume info

POST /api/share                Create share link
GET  /api/share                My share links
DELETE /api/share/{id}         Revoke link
GET  /api/share/public/{token}/download  Public download
```

## Migration to AWS S3 / MinIO

The `StorageEngine` class is the only file-I/O abstraction. To migrate:

1. Add the AWS S3 SDK or MinIO SDK to `pom.xml`
2. Replace `StorageEngine.write/read/delete` with `S3Client.putObject/getObject/deleteObject`
3. Update `storage.root-dir` → `storage.bucket-name` in `application.properties`
4. No other changes needed — services and controllers are unchanged

## Tech Stack

- **Backend**: Spring Boot 3.2, Spring Security (JWT), Spring Data JPA
- **Database**: H2 (dev) / PostgreSQL 16 (prod)
- **Cache**: Redis 7 (Spring Cache abstraction)
- **Storage**: Local disk engine (S3/MinIO-ready interface)
- **Frontend**: React 18, Vite
- **Infrastructure**: Docker, Docker Compose, Kubernetes + HPA
