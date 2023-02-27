# Light Kotlin Multiplatform Database
[![](https://jitpack.io/v/com.darkyen/lkmp-database.svg)](https://jitpack.io/#com.darkyen/lkmp-database)

Supported platforms:
- JavaScript through IndexedDB

### Running tests

This command ensures that everything works, all caching and daemons are disabled.
Otherwise, the build is completely inconsistent.
```
GRADLE_OPTS="-XX:MaxMetaspaceSize=256m -Xms256m -Xmx2g" ./gradlew :check --rerun-tasks --info
```