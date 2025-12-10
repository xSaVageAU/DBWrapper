# DBWrapper Development Journey: Lessons Learned

## Overview

This document captures the challenges, solutions, and lessons learned during the development of the DBWrapper Fabric mod. It serves as a reference for future improvements and clean implementations.

## Major Challenges and Solutions

### 1. Binary Extraction Issues

**Problem**: The ZIP file was being copied but not extracted, leaving the `bin` directory empty.

**Root Cause**: The extraction code was commented out as a TODO.

**Solution**: Implemented proper ZIP extraction using Java's built-in `java.util.zip` package.

**Lesson**: Always implement complete functionality, even for \"simple\" operations like file extraction.

### 2. Path Resolution Problems

**Problem**: `mariadb-install-db.exe` was trying to create directories at incorrect paths like:
```
D:\...\config\dbwrapper\mariadb\bin\config\dbwrapper\mariadb\data
```

**Root Cause**: The executable was appending the `--datadir` path to the current working directory.

**Solution**: Created the exact path structure that the executable expected, working with its behavior rather than against it.

**Lesson**: Understand how third-party executables handle paths before assuming standard behavior.

### 3. Parameter Compatibility

**Problem**: The `--basedir` parameter was not recognized by this version of MariaDB.

**Root Cause**: Different MariaDB versions have different parameter sets.

**Solution**: Reverted to using `--datadir` and worked around the path issues.

**Lesson**: Test parameters manually before implementing them in code.

### 4. Timeout Issues

**Problem**: MariaDB startup was failing due to 30-second timeout being too short.

**Root Cause**: First-time database initialization takes longer than expected.

**Solution**: Increased timeout to 60 seconds for first-time initialization.

**Lesson**: Account for longer initialization times, especially for first runs.

### 5. Working Directory Confusion

**Problem**: Confusion about whether to run from `bin` directory or MariaDB root.

**Root Cause**: Different approaches tried without consistent strategy.

**Solution**: Settled on running from `bin` directory with absolute paths.

**Lesson**: Be consistent with working directory strategy from the beginning.

## Technical Debt and Improvements

### Current Technical Debt

1. **Hardcoded Paths**: Some paths are constructed in ways that may not be portable
2. **Error Handling**: Some error cases could be handled more gracefully
3. **Configuration**: Configuration system could be more robust
4. **Logging**: Logging could be more consistent and detailed
5. **Testing**: Lack of comprehensive test coverage

### Recommended Improvements

1. **Proper Path Handling**:
   - Use `Path` objects consistently
   - Implement cross-platform path resolution
   - Validate all paths before use

2. **Configuration System**:
   - Make database version configurable
   - Support multiple database types
   - Implement proper config validation

3. **Error Handling**:
   - More specific error messages
   - Better recovery strategies
   - User-friendly error reporting

4. **Process Management**:
   - Proper process cleanup
   - Resource monitoring
   - Graceful shutdown handling

5. **Testing**:
   - Unit tests for core functionality
   - Integration tests
   - Mock testing for external dependencies

## Architecture Lessons

### What Worked Well

1. **Modular Design**: Separation of concerns between configuration, database management, and utilities
2. **Interface-Based**: `DatabaseManager` interface allows for multiple implementations
3. **Lifecycle Integration**: Proper integration with Fabric's server lifecycle events
4. **Configuration**: JSON-based configuration with automatic loading/saving

### What Could Be Improved

1. **Dependency Management**: Better handling of external dependencies
2. **Resource Management**: More robust resource cleanup
3. **Cross-Platform**: Better OS-specific handling
4. **Performance**: Optimization for large-scale deployments

## Development Process Insights

### Successful Strategies

1. **Incremental Testing**: Testing each component as it was developed
2. **Log Analysis**: Careful examination of error logs to understand issues
3. **Iterative Approach**: Trying different solutions and learning from failures
4. **Manual Testing**: Running commands manually to understand behavior

### Challenges in Process

1. **Assumptions**: Initial assumptions about MariaDB behavior were incorrect
2. **Debugging**: Complex path resolution issues were hard to diagnose
3. **Documentation**: Lack of clear MariaDB documentation for this use case
4. **Environment**: Differences between development and production environments

## Recommendations for Clean Implementation

### 1. Design First

- **Complete Architecture**: Design the full architecture before implementation
- **Interface Contracts**: Define clear interfaces and contracts
- **Error Handling**: Plan error handling strategy upfront

### 2. Robust Implementation

- **Path Handling**: Use proper path resolution libraries
- **Configuration**: Implement comprehensive configuration system
- **Testing**: Write tests alongside implementation

### 3. Production Considerations

- **Logging**: Comprehensive logging for debugging
- **Monitoring**: Resource monitoring and alerts
- **Security**: Proper security considerations
- **Documentation**: Complete documentation for users

### 4. Future Extensions

- **Multiple Databases**: Design for PostgreSQL, Redis support
- **Management Interface**: In-game or web management
- **Performance**: Connection pooling and optimization
- **Backup/Restore**: Integrated backup functionality

## Conclusion

This development journey demonstrated the importance of:
- Understanding third-party tool behavior
- Robust error handling and logging
- Iterative development and testing
- Working with tool behavior rather than against it

The current implementation serves as a functional prototype that can be refined into a production-ready system with the lessons learned.