# DBWrapper

A Minecraft Fabric mod that provides a wrapper for running and managing embedded database service processes within Minecraft servers. This mod handles the extraction, installation, and lifecycle management of database binaries (like MariaDB), allowing other mods to connect to and use databases through standard database protocols.

## Features

- **Embedded Database Binaries**: Automatically extracts and manages database server binaries for different operating systems
- **Cross-Platform Support**: Supports Windows, and planned support for Linux and macOS
- **Process Management**: Handles database server lifecycle, startup, and shutdown integrated with Minecraft server events
- **Configuration System**: JSON-based configuration for database settings and mod preferences
- **Automatic Setup**: One-click database installation and configuration

## Supported Databases

- **MariaDB** (Primary implementation)
- **PostgreSQL** (Planned)
- **Redis** (Supported, but disabled by default)

## Installation

1. Download the mod JAR file
2. Place it in your Minecraft server's `mods` folder
3. Start the server - the mod will automatically extract and configure the database binaries
4. Configure database settings in the generated config files if needed

## Configuration

The mod generates a configuration file in the `config/dbwrapper` directory:

- `config.json` - Main mod configuration (enable/disable databases, auto-start, etc.)

## How It Works

This mod runs database servers as separate processes alongside your Minecraft server. Other mods can connect to these databases using standard database clients and protocols:

- Connect to MariaDB using JDBC or MySQL clients on the configured port
- Use standard SQL queries and database operations
- The mod handles the underlying process management automatically

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

## License

This project is licensed under CC0-1.0. However, it includes MariaDB binaries which are licensed under GPLv2. See NOTICE file for details.

## Contributing

Contributions are welcome! Please feel free to submit pull requests or open issues.

## Disclaimer

This mod is experimental and should be used with caution in production environments. Always backup your data before using database functionality.