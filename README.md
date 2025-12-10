# DBWrapper

A Minecraft Fabric mod that simplifies running remote databases like MariaDB, PostgreSQL, and Redis for Minecraft servers. This mod acts as a wrapper that manages database server binaries and processes, making it easy to integrate database functionality into Minecraft mods.

## Features

- **Embedded Database Binaries**: Automatically manages database server binaries for different operating systems
- **Cross-Platform Support**: Supports Windows, Linux, and macOS
- **Process Management**: Handles database server lifecycle, startup, and shutdown
- **Configuration System**: JSON-based configuration for database settings
- **Modular Design**: Extensible architecture for adding new database types

## Supported Databases

- **MariaDB** (Primary implementation)
- **PostgreSQL** (Planned)
- **Redis** (Planned)

## Installation

1. Download the mod JAR file
2. Place it in your Minecraft server's `mods` folder
3. Start the server - the mod will automatically extract and configure the database binaries
4. Configure database settings in the generated config files

## Configuration

The mod generates configuration files in the `config/dbwrapper` directory:

- `dbwrapper.json` - Main mod configuration
- `database.json` - Database-specific settings

## Usage

The mod provides a simple API for other mods to interact with databases:

```java
// Get database manager instance
DatabaseManager dbManager = DBWrapper.getDatabaseManager();

// Check if database is running
if (dbManager.isRunning()) {
    // Database is ready for connections
}
```

## Development

### Prerequisites

- Java 21
- Minecraft 1.21.10
- Fabric Loader

### Building

```bash
./gradlew build
```

### Testing

```bash
./gradlew test
```

## Architecture

The mod uses a modular architecture:

- **DatabaseManager**: Abstract interface for database operations
- **MariaDBManager**: MariaDB-specific implementation
- **ProcessUtils**: Cross-platform process management
- **OSUtils**: Operating system detection utilities
- **ConfigLoader**: JSON configuration management

## License

This project is licensed under CC0-1.0. However, it includes MariaDB binaries which are licensed under GPLv2. See NOTICE file for details.

## Contributing

Contributions are welcome! Please feel free to submit pull requests or open issues.

## Disclaimer

This mod is experimental and should be used with caution in production environments. Always backup your data before using database functionality.