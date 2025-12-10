# DBWrapper MariaDB Binaries

This directory should contain MariaDB binaries for different platforms. The binaries should be named using the following convention:

```
mariadb-{version}-{os}-{architecture}.zip
```

## Supported Platforms

- **Windows**: `mariadb-11.3.2-winx64.zip`
- **Linux**: `mariadb-11.3.2-linux-x64.tar.gz`
- **macOS**: `mariadb-11.3.2-macos-x64.tar.gz`

## Binary Requirements

Each binary archive should contain:
- The MariaDB server executable (`mysqld` or `mysqld.exe`)
- The MariaDB installation script (`mariadb-install-db` or `mariadb-install-db.exe`)
- The MariaDB admin tool (`mysqladmin` or `mysqladmin.exe`)
- Other necessary binaries and libraries

## Notes

1. The binaries should be obtained from official MariaDB sources
2. For production use, consider using the latest stable version
3. The mod will automatically detect the appropriate binary for the current platform
4. Binaries are extracted to the mod's config directory during first run

## Legal

Ensure you comply with MariaDB's licensing terms when distributing binaries with this mod.