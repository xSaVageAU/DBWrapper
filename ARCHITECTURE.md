# DBWrapper Architecture Design

## Overview
DBWrapper is a Fabric mod designed to simplify running remote databases like MariaDB/MySQL, PostgreSQL, and Redis within Minecraft servers. The mod will manage database binaries, handle process lifecycle, and provide a unified interface for different database systems.

## Core Components

### 1. Database Binary Management
- **Binary Storage**: Embed database binaries in mod resources or fetch them dynamically
- **OS Detection**: Detect operating system and architecture to select appropriate binaries
- **Binary Extraction**: Extract and manage database binaries in the config directory
- **Version Management**: Support multiple database versions

### 2. Process Management
- **Process Lifecycle**: Start, stop, and monitor database processes
- **Port Management**: Handle port allocation and conflicts
- **Process Monitoring**: Monitor database process health and logs
- **Graceful Shutdown**: Ensure proper shutdown on server exit

### 3. Configuration System
- **Database Configuration**: Store connection details, credentials, and settings
- **Installation Tracking**: Track installation progress and status
- **Mod Configuration**: General mod settings and preferences

### 4. Database-Specific Implementations
- **MariaDB/MySQL**: Windows, Linux, and macOS support
- **PostgreSQL**: Cross-platform support (future)
- **Redis**: Cross-platform support (future)

## Class Structure

```
savage.dbwrapper
├── DBWrapper.java (Main mod class)
├── config
│   ├── DBWrapperConfig.java (Main configuration)
│   ├── DatabaseConfig.java (Database-specific config)
│   └── InstallationProgress.java (Installation tracking)
├── database
│   ├── DatabaseManager.java (Abstract base class)
│   ├── mariadb
│   │   ├── MariaDBManager.java (MariaDB implementation)
│   │   └── MariaDBInstaller.java (MariaDB installer)
│   ├── postgres
│   │   ├── PostgreSQLManager.java (Future)
│   │   └── PostgreSQLInstaller.java (Future)
│   └── redis
│       ├── RedisManager.java (Future)
│       └── RedisInstaller.java (Future)
├── utils
│   ├── OSUtils.java (OS detection)
│   ├── ProcessUtils.java (Process management)
│   └── FileUtils.java (File operations)
└── mixin
    └── ExampleMixin.java (Example mixin)
```

## Implementation Plan

### Phase 1: MariaDB Support
1. **Binary Management**: Embed MariaDB binaries for Windows, Linux, and macOS
2. **Installation Process**: Extract binaries, run installation commands
3. **Process Management**: Start/stop MariaDB server
4. **Configuration**: Store MariaDB settings and progress

### Phase 2: Cross-Platform Support
1. **OS Detection**: Implement robust OS and architecture detection
2. **Binary Selection**: Choose appropriate binaries based on OS
3. **Process Adaptation**: Handle different process management across platforms

### Phase 3: Additional Databases (Future)
1. **PostgreSQL**: Add PostgreSQL support
2. **Redis**: Add Redis support
3. **Unified Interface**: Create abstract interface for all databases

## Key Features

1. **Automatic Installation**: One-click database setup
2. **Process Management**: Automatic start/stop with Minecraft server
3. **Configuration Persistence**: Save settings between server restarts
4. **Error Handling**: Robust error handling and recovery
5. **Logging**: Comprehensive logging for debugging

## Technical Details

- **Java Version**: Java 21 (as per Fabric requirements)
- **Minecraft Version**: 1.21.10
- **Build System**: Gradle with Fabric Loom
- **Dependencies**: Fabric API

## Implementation Notes

1. Use Fabric's PreLaunchEntrypoint for early initialization
2. Store database files in the mod's config directory
3. Implement proper cleanup in shutdown hooks
4. Use coroutines or async tasks for long-running operations
5. Provide clear user feedback through logging