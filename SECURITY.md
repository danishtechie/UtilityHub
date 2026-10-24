# Security Policy & Safeguards

Security and user data safety are central to **UtilityHub**.

---

## 🛡️ Implemented Safeguards

1. **Local Sandboxed Processing**:
   - File processing operations take place locally on the device using standard Android Storage Access Framework (SAF) and internal cache directories.
2. **Input Validation & Content Sniffing**:
   - Validates magic bytes rather than relying solely on file extensions.
   - Enforces per-file size limits and decompression limits for ZIP archives (protecting against Zip-Slip and Zip bombs).
3. **Authentication & Identity Security**:
   - Accounts use Firebase Authentication with client-side input sanitization (`AuthRules`).
   - Enforces minimum 10-character passwords with letters and numbers.
   - Device brute-force lockout (`AttemptLimiter`) locks login attempts after 5 consecutive failed tries for 60 seconds.
   - Email verification required for all newly registered accounts.
4. **Zero Unintended Exposure**:
   - Passwords and file contents are never logged.
   - EXIF and GPS location metadata are stripped from output photos by default during image processing.
5. **Release Build Security**:
   - Minification (R8 / Proguard) and resource shrinking enabled for release builds.
   - Release signing keys and secrets stored outside the codebase in local environment properties.

---

## 📬 Reporting Vulnerabilities

If you discover a potential security issue in UtilityHub, please report it directly to **Danish Baigh**:

- **Email**: [dmaqbool006@gmail.com](mailto:dmaqbool006@gmail.com)
- **Instagram**: [@dan.ishh6](https://www.instagram.com/dan.ishh6/)

Please allow 48 hours for a response before making any public disclosure.
