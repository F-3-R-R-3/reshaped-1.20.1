# Contributing to ReShaped

Thank you for considering contributing to ReShaped. Contributions are welcome and appreciated.

## Getting Started

1. Fork this repository on GitHub
2. Clone your fork:
```bash
   git clone https://github.com/F-3-R-R-3/reshaped-1.20.1.git
```
4. Enter the project folder:
```
   cd reshaped-1.20.1
```
6. Create a new branch:
```
   git checkout -b feature/your-change
```
> [!TIP]
> Or alternatively, you can use the bultin Git functionallity of IDE's like Intellij (<- reccomended) or VSCode to clone this repo locally and create a branch.
> You can always ask me for help setting up a coding environment and git branch by creating a Github issue!

## Development Setup

This project is a Fabric mod for Minecraft 1.20.1.

Requirements:
- Java 17 or higher
- Gradle (wrapper included)

Run the client in development mode:
./gradlew runClient

Build the mod:
./gradlew build

The output jar will be located in:
build/libs

## Code Guidelines

- Keep code clean and readable
- Follow existing project structure
- Use clear naming for classes and methods
- Avoid large unrelated changes in one pull request

## What You Can Contribute

- Bug fixes
- Performance improvements
- New block or shape logic
- Compatibility fixes with other mods
- UI or usability improvements
- Refactoring and cleanup

## Issues

Before opening an issue:
- Check if it already exists
- Include Minecraft version and mod version
- Add logs or screenshots if possible
- Provide steps to reproduce the problem

## Pull Requests

When submitting a pull request:
- Clearly describe what changed
- Keep changes focused
- Ensure the mod still runs and builds
- Test in-game before submitting

## Important Systems

Be careful when modifying:
- Block associations
- Block registration

These parts affect global mod behavior.

## AI Usage

AI-generated code is allowed.

Please review, test, and understand any AI-generated code before submitting it. Contributions are judged by their quality and maintainability, not by whether AI was used.

I use AI tools myself, but I try to keep their use to a minimum whenever possible.

## License

By contributing, you agree that your code will be licensed under the same license as the project (LGPL-3.0).
