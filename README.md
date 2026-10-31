# VLESS SPEED SCAN

Android app for managing proxy subscriptions and testing individual nodes without enabling Android's system VPN.

## Planned first release
- Multiple subscription URLs
- VLESS, VMess, Shadowsocks and Trojan parsing
- Subscription refresh and node de-duplication
- Per-node latency and throughput checks through isolated local proxy instances
- Android VPNService connection mode

## Build
Open this repository in Android Studio or run `./gradlew assembleDebug` after the Gradle wrapper is added.

> Early development scaffold. The proxy engine and real network tests are not implemented yet; displayed sample UI values are placeholders.
