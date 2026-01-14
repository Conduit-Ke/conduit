# SMS Routing Enterprise Gateway 🚀

An enterprise-grade, edge-computing SMS gateway system designed for high reliability, compliance, and multi-node scalability. This system consists of a **Lumen-based Management API** and **Android Edge Nodes**.

---

## 🏗️ System Architecture

The gateway operates using a dual-layered routing architecture. Local decisions (TPS, SIM health) are made at the Edge, while global decisions (Webhooks, Transformation, Multi-device coordination) are handled by the Server.

### 1. Ingress Flow (Receiving SMS)
**Flow Highlights:** Mobile Network → Edge Node → Server → External Integrations

```mermaid
sequenceDiagram
    participant P as Phone (External)
    participant N as Edge Node (SmsReceiver)
    participant D as Local Database (Room)
    participant E as RoutingEngine (Node)
    participant A as Management API
    participant W as External Webhook / CRM

    P->>N: Incoming SMS
    N->>N: Generate Content Fingerprint
    N->>D: Store (status: RECEIVED)
    
    E->>D: Acquire Atomic Lease
    E->>E: Evaluate Local Rules (DROP/HOLD/FORWARD)
    
    alt Action: SubmitToServer
        E->>A: POST /api/sms
        A->>A: global_deduplication_check()
        A->>A: SmsRouter (Rule Matching)
        A->>W: POST Webhook (HMAC-SHA256 signed v1)
        A-->>E: 200 OK (Processed)
        E->>D: Update status: DELIVERED
    else Action: Local Forward
        E->>E: Select healthy SIM
        E->>P: AT+CMGS (Forwarded SMS)
    end
```

### 2. Egress Flow (Sending SMS)
**Flow Highlights:** Dashboard/API → Management Server → Edge Node → Mobile Network

```mermaid
sequenceDiagram
    participant U as Dashboard / Client API
    participant A as Management API
    participant F as FCM (Push-to-Queue)
    participant N as Edge Node (FcmService)
    participant D as Local Database (Room)
    participant E as RoutingEngine (Node)
    participant R as Radio (Hardware)

    U->>A: POST /api/notifications/send-sms
    A->>A: Layer 1: AES-256-GCM Encrypt(Payload)
    A->>A: Layer 2: HMAC-SHA256 Sign(Envelope)
    A->>F: Data(Ciphertext, Signature, Nonce)
    
    F-->>N: Receive FCM Push
    N->>N: 1. Verify HMAC-SHA256 Signature
    N->>N: 2. Decrypt AES-256-GCM Payload
    N->>D: Insert Into Queue (status: QUEUED)
    
    loop Queue Processing
        E->>D: Acquire Lease
        E->>E: SimHealthManager: Check SIM Stats
        E->>E: RateLimiter: TPS Enforcement
        E->>R: Send SMS via Radio
        R-->>E: OnSent() / OnDelivered()
        E->>A: POST /api/sms/status (Sync DLR)
        A->>A: Update Master Logs
    end
```

---

## 📱 SMS Edge Node (Android Agent)

SMS Edge Node is a **carrier‑grade Android SMS routing agent** designed for extreme reliability (target SLA: 99.99999% under defined conditions).

### Core Guarantees
```mermaid
pie title SLA Continuity Guarantees
    "Zero Message Loss" : 25
    "Idempotent Processing" : 25
    "Deterministic Routing" : 25
    "Fail-Safe Security" : 25
```

### Agent State Machine
```mermaid
stateDiagram-v2
    [*] --> RECEIVED: Radio Ingress
    [*] --> QUEUED: FCM Injection
    
    RECEIVED --> SENDING: Acquire Lease
    QUEUED --> SENDING: Acquire Lease
    
    state SENDING {
        [*] --> Evaluating
        Evaluating --> Dispatching: Rules Match
        Dispatching --> Radio: Hardware TX
    }
    
    SENDING --> DELIVERED: 200 OK / Radio Success
    SENDING --> RETRY: Network Error
    RETRY --> SENDING: Backoff Expiry
    SENDING --> FAILED: Hard Failure
```

### Module Breakdown
```mermaid
graph TD
    subgraph "Ingress & Storage"
        ING[ingress/] --> DB[(data/db/)]
    end
    
    subgraph "Core Execution"
        SVC[service/] --> ENG[domain/ - Routing Engine]
        ENG --> SEND[sender/ - Outbound]
    end
    
    subgraph "Control & Security"
        CTRL[control/ - FCM] --> SEC[security/ - Crypto]
        SEC --> ENG
    end
    
    subgraph "Audit"
        OBS[observability/] <--> DB
    end
```

---

## 🔒 Security Model

```mermaid
graph LR
    subgraph "Transport Layer"
        FCM[FCM Control] --- |"AES-GCM + HMAC"| APP[Edge App]
        APP --- |"HMAC-SHA256 v1"| WH[Webhooks]
    end
    
    subgraph "At-Rest Layer"
        APP --- |"SQLCipher"| SQLite[(Encrypted DB)]
        APP --- |"Hardware-Backed"| KS[Android Keystore]
    end
```

### Abuse & Compliance
- **Premium Blocking**: Automatic rejection of 900x prefix outbound attempts.
- **Rate Limiting**: Enforced per-SIM TPS and daily/monthly quotas.
- **IMEI Binding**: Optional enforcement binding SIM cards to specific hardware IDs.

---

## 🚀 Deployment & Release

### Release Workflow (Private Source → Public Repo)
```mermaid
graph LR
    TAG[Git Tag v*] --> CI[GitHub Actions @ Private Repo]
    CI --> BUILD[Build Signed APK]
    BUILD --> PUSH[Push to Public Repo]
    PUSH --> REL[Public GitHub Release]
```

### Setup Requirements
1.  **Public Repo**: Create an empty public repository (e.g., `my-org/sms-gateway-public`).
2.  **Secret Token**:
    - Generate a Personal Access Token (PAT) with `repo` scope.
    - Add it to this private repo's secrets as `PUBLIC_REPO_TOKEN`.
3.  **Workflow Config**: Update `.github/workflows/android-release.yml` with the public repo name.

### 🔐 Required Secrets
Add these in your **Private Repo** under `Settings` → `Secrets and variables` → `Actions`.

| Secret Name | Description | How to Get It |
| :--- | :--- | :--- |
| `KEYSTORE_PASSWORD` | Password for your release keystore file (`.jks`). | You defined this when running `keytool` to generate the keystore. |
| `KEY_PASSWORD` | Password for the key alias inside the keystore. | Usually same as above, defined during `keytool` generation. |
| `PUBLIC_REPO_TOKEN` | GitHub Personal Access Token (PAT) to publish to the public repo. | GitHub → Settings → Developer settings → PATs (Classic) → New Token → Select `repo` scope. |
| `KEYSTORE_BASE64` | The actual keystore file, encoded in Base64. | Run `base64 -w 0 keystore.jks` in your terminal and copy the entire output. |

### Prerequisites
- Android Studio Hedgehog+ | API 26+ (Android 8.0+)
- Firebase project with FCM and Crashlytics.
- Correct `google-services.json` in `app/`.

---

## 🔋 Battery & Performance

**Target Drain: <5% per 24h**

```mermaid
graph TD
    Idle[Queue Empty] --> |"Increase Poll to 30s"| PowerSave[Low CPU State]
    Writing[DB Operations] --> |"Batch Writes"| DiskSave[Reduced I/O]
    FCM[FCM Ingress] --> |"High Priority Only"| Doze[Doze Bypass]
```

---

## 🧪 Testing & Verification

### Diagnostic SQL
```bash
# View recent queue activity
adb shell "run-as com.sms.edgenode sqlite3 /data/data/com.sms.edgenode/databases/edge_node_db 'SELECT id, status, attempts FROM sms_queue ORDER BY receivedAt DESC LIMIT 10;'"

# View security audit trail
adb shell "run-as com.sms.edgenode sqlite3 /data/data/com.sms.edgenode/databases/edge_node_db 'SELECT eventType, details FROM audit_log ORDER BY timestamp DESC LIMIT 5;'"
```

---

## ✅ Production Checklist
- [ ] **Firebase**: FCM enabled, `google-services.json` present.
- [ ] **Keystore**: Production keys generated and environment variables set.
- [ ] **Permissions**: SMS, Phone State, and Notifications granted on device.
- [ ] **Exclusions**: Battery optimization set to **Unrestricted** for the app.
- [ ] **Signatures**: Rotate rule-signing keys every 90 days.

---

## ⚠️ Warning
This is **infrastructure software** running on consumer hardware. Treat Android as a hostile runtime. Failure to disable battery optimizations or grant required permissions will result in delivery failure.
