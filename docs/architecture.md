# Architecture

Vault T follows **Clean Architecture** principles combined with the **MVVM** pattern.

## Layers

### 1. Presentation Layer (UI)
- **Jetpack Compose**: Used for all UI components.
- **Material 3**: The design system.
- **ViewModels**: Manage UI state and interact with Use Cases.

### 2. Domain Layer
- **Models**: Plain Kotlin objects representing business entities.
- **Repositories (Interfaces)**: Define data access contracts.
- **Use Cases**: Contain business logic (e.g., `EncryptFileUseCase`, `UnlockVaultUseCase`).

### 3. Data Layer
- **Repositories (Implementation)**: Coordinate data from various sources.
- **Room Database**: Stores encrypted metadata and vault state.
- **Storage**: Manages encrypted files in application-private storage.
- **Security**: Handles encryption/decryption and key management.

## Dependency Injection
- **Hilt**: Used for dependency injection across the application.

## State Management
- **Kotlin Coroutines & Flow**: Used for asynchronous operations and reactive UI updates.
