# Security Policy

## Security Principles
Vault T is built on the following core security principles:
1. **Local-Only**: No data ever leaves the device via network requests or cloud sync.
2. **Encryption by Default**: All sensitive data (media, metadata, database) is encrypted at rest.
3. **Hardware-Backed Security**: Cryptographic keys are protected by the Android Keystore system.
4. **Defense in Depth**: Multiple layers of protection (Sandbox -> Keystore -> Biometric/PIN).

## Cryptography
- **Algorithm**: XChaCha20-Poly1305 (Authenticated Encryption).
- **Library**: Google Tink.
- **Key Storage**: Android Keystore (Master Key).

## Reporting Vulnerabilities
If you discover a security vulnerability, please do not disclose it publicly. Report it directly to the development team.
