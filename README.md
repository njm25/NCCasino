# NCCasino Plugin

## Requirements

- **Java Development Kit (JDK) 17 or higher**: [Download here](https://www.oracle.com/java/technologies/downloads/)
- **Gradle build tool**: Included via the Gradle Wrapper (`gradlew`)

## Compiling and Deploying the Plugin on Windows

This process uses a batch script to automate compiling and deploying your plugin to a Minecraft server.

### Step 1: Create a Configuration File

1. **Create `config.txt`**

   - Place a file named `config.txt` in the root directory of your project, alongside the `deploy.bat` script.

   - Add the following lines to `config.txt`, replacing the placeholders with your actual directory paths and filenames:

     ```plaintext
     PROJECT_DIR=C:\Path\To\Your\Project
     SERVER_DIR=C:\Path\To\Your\Minecraft\Server
     PLUGIN_NAME=YourPluginName.jar
     SERVER_JAR=YourServerJarFile.jar
     ```

   - **Configuration Keys**:
     - `PROJECT_DIR`: Path to your NCCasino project's root directory.
     - `SERVER_DIR`: Path to your Minecraft server directory.
     - `PLUGIN_NAME`: Name of your plugin's JAR file (e.g., `nccasino-1.0-SNAPSHOT.jar`).
     - `SERVER_JAR`: Filename of your Minecraft server JAR (e.g., `paper-1.21-124.jar`).

### Step 2: Run the Batch Script

2. **Execute `deploy.bat`**

   - The `deploy.bat` script is located in the root directory of your project.

   - **To run the script**:
     - **Double-click** `deploy.bat` in File Explorer.
     - **Or run it from the command prompt** by navigating to the project directory and entering:

       ```bash
       deploy.bat
       ```

   - The script will:
     - Build the project using Gradle.
     - Replace the old plugin JAR with the newly compiled version in your server's `plugins` directory.
     - Start the Minecraft server with the updated plugin.

### Notes

- **Ensure `config.txt` and `deploy.bat` are in the same directory**.
- **Verify the paths in `config.txt`** to ensure they are correct for your environment.
- **Consider backing up your server data** before running the script to prevent data loss during updates.

## Usage

Once the plugin is deployed, you can use the following commands in-game:

- **`/ncc help`**: Displays help information for the plugin.
- **`/ncc create <name>`**: Spawns a dealer where the player is standing.
- **`/ncc list (page)`**: Lists the dealers.
- **`/ncc reload`**: Reloads the config.
