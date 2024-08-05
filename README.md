# NCCasino Plugin

## Requirements

- [Java Development Kit (JDK) 17 or higher](https://www.oracle.com/java/technologies/downloads/)
- Gradle build tool

## Compiling the Plugin

Follow the instructions below to compile the plugin on Windows and Linux.

### Windows

1. **Open a Command Prompt**: Navigate to the root directory of the project where the `build.gradle` file is located.

2. **Run Gradle Wrapper**: Execute the following command to clean and build the project:

   ```bash
   .\gradlew.bat clean build
   ```

3. **Locate the JAR File**: After the build process completes, the compiled JAR file will be located in the `build/libs` directory.

### Linux

1. **Open a Terminal**: Navigate to the root directory of the project where the `build.gradle` file is located.

2. **Run Gradle Wrapper**: Execute the following command to clean and build the project:

   ```bash
   ./gradlew clean build
   ```

3. **Locate the JAR File**: After the build process completes, the compiled JAR file will be located in the `build/libs` directory.

## Deploying the Plugin

1. **Copy the JAR File**: Locate the compiled JAR file in the `build/libs` directory and copy it.

2. **Paste the JAR File into the Plugins Folder**: Navigate to your Minecraft server's `plugins` folder and paste the JAR file there.

3. **Reload the Server**: Restart or reload your Minecraft server to enable the plugin. You can use the following command in the server console to reload the server:

   ```bash
   reload
   ```

   **Note**: It's generally recommended to restart the server instead of reloading to ensure stability:

   ```bash
   stop
   # Start the server again using your server start script
   ```

## Usage

Once the plugin is loaded on the server, players can use the following commands:

- **/ncc help**: Displays help information for the plugin.
- **/ncc create**: Spawns a dealer where the player is looking.
