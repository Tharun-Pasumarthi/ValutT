# Threat Model

## Asset Definition
- **User Media**: Photos, videos, audio files, and documents stored in the vault.
- **Metadata**: Filenames, album names, tags, and timestamps.
- **Cryptographic Keys**: Master key and derived keys used for encryption.
- **Authentication Credentials**: User PIN/Passcode.

## Threats & Protections

### 1. Device Theft / Unauthorized Physical Access
- **Threat**: An attacker gains physical access to the device and tries to view files.
- **Protection**: 
    - Full encryption of all vault content at rest.
    - PIN/Biometric authentication required for access.
    - Hardware-backed key protection (Android Keystore).

### 2. Malicious Third-Party Applications
- **Threat**: Another app on the device tries to read Vault T's data.
- **Protection**: 
    - Android Sandbox (app-private storage).
    - Even if sandbox is breached, all data is encrypted.
    - No public storage permissions used for the vault.

### 3. Forensic Extraction
- **Threat**: Attacker uses forensic tools to extract data from flash storage.
- **Protection**: 
    - Authenticated encryption (XChaCha20-Poly1305) ensures confidentiality and integrity.
    - Keys are not stored in plaintext.

### 4. Shoulder Surfing / Screenshots
- **Threat**: Someone watches the user or takes a screenshot/screen recording.
- **Protection**: 
    - `FLAG_SECURE` enabled on all sensitive screens.
    - Auto-lock on app backgrounding.

### 5. Cloud/ADB Backup
- **Threat**: Private data is leaked via Google Drive backup or `adb backup`.
- **Protection**: 
    - Explicitly disabled backup for all vault directories in `AndroidManifest.xml`.

## Limitations
- **Rooted Devices**: A compromised OS can bypass Android Sandbox and potentially intercept keys in memory while the vault is unlocked.
- **Hardware Attacks**: Sophisticated physical attacks against the secure element/processor.
- **Key Logging**: OS-level malware could capture PIN entry.
