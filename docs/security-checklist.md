# Security Checklist (OWASP MASVS/MASTG)

## V1: Storage and Privacy
- [x] **MSTG-STORAGE-1**: No sensitive data is stored in the app-private directory without encryption (XChaCha20-Poly1305).
- [x] **MSTG-STORAGE-2**: No sensitive data is leaked through logs.
- [x] **MSTG-STORAGE-3**: No sensitive data is leaked through IPC (Local-only).
- [x] **MSTG-STORAGE-5**: Screenshot protection is enabled (`FLAG_SECURE`).
- [x] **MSTG-STORAGE-7**: No sensitive data is included in backups (`allowBackup="false"`).

## V2: Cryptography
- [x] **MSTG-CRYPTO-1**: App does not use broken or weak cryptographic algorithms (Tink XChaCha20-Poly1305).
- [x] **MSTG-CRYPTO-2**: Cryptographic keys are protected by Android Keystore.
- [x] **MSTG-CRYPTO-3**: Keys are not hardcoded.

## V3: Authentication
- [x] **MSTG-AUTH-1**: User is authenticated before accessing the vault (PIN/Biometric).
- [x] **MSTG-AUTH-4**: Sensitive operations (Export, Destroy) require re-authentication foundation.
- [x] **MSTG-AUTH-8**: The application locks when placed in the background.

## V4: Network
- [x] **MSTG-NETWORK-1**: The application does not use network permissions (Offline-only).

## V7: Code Quality
- [x] **MSTG-CODE-1**: App is signed with a valid certificate.
- [x] **MSTG-CODE-4**: Proguard/R8 is configured for release builds.
