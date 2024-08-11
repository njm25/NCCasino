# NCCasino Plugin

## Requirements

- **Java Development Kit (JDK) 17 or higher**: [Download here](https://www.oracle.com/java/technologies/downloads/)
- **Gradle build tool**: Included via the Gradle Wrapper (`gradlew` on Windows, `./gradlew` on Linux)

## Compiling and Deploying the Plugin on Windows and Linux

This process uses a script to automate compiling and deploying your plugin to a Minecraft server.

### Step 1: Create a Configuration File

1. **Create `config.txt`**

   - Place a file named `config.txt` in the root directory of your project, alongside the `deploy.bat` (for Windows) and `deploy.sh` (for Linux) scripts.

   - Add the following lines to `config.txt`, replacing the placeholders with your actual directory paths and filenames:

     ```plaintext
      PROJECT_DIR=/path/to/your/project
      SERVER_DIR=/path/to/your/minecraft/server
      PLUGIN_NAME=nccasino-1.0-SNAPSHOT.jar
      SERVER_JAR=paper-1.21-124.jar
     ```

   - **Configuration Keys**:
     - `PROJECT_DIR`: Path to your NCCasino project's root directory.
     - `SERVER_DIR`: Path to your Minecraft server directory.
     - `PLUGIN_NAME`: Name of your plugin's JAR file (e.g., `nccasino-1.0-SNAPSHOT.jar`).
     - `SERVER_JAR`: Filename of your Minecraft server JAR (e.g., `paper-1.21-124.jar`).

     **Note**: On Windows, replace `/path/to/your/project` with `C:\Path\To\Your\Project` format, and on Linux, use `/path/to/your/project` format.

### Step 2: Run the Deployment Script

2. **Execute the Deployment Script**

   - The deployment scripts are located in the root directory of your project.

   - **For Windows**:
     - The script file is `deploy.bat`.
     - **To run the script**:
       - **Double-click** `deploy.bat` in File Explorer.
       - **Or run it from the command prompt** by navigating to the project directory and entering:

         ```bash
         deploy.bat
         ```

   - **For Linux**:
     - The script file is `deploy.sh`.
     - **To run the script**:
       - Open a terminal and navigate to the project directory.
       - Make sure the script has executable permissions:

         ```bash
         chmod +x deploy.sh
         ```

       - **Run the script**:

         ```bash
         ./deploy.sh
         ```

   - The script will:
     - Build the project using Gradle.
     - Replace the old plugin JAR with the newly compiled version in your server's `plugins` directory.
     - Start the Minecraft server with the updated plugin.

### Notes

- **Ensure `config.txt` and the appropriate deployment script (`deploy.bat` for Windows, `deploy.sh` for Linux) are in the same directory**.
- **Verify the paths in `config.txt`** to ensure they are correct for your environment.
- **Consider backing up your server data** before running the script to prevent data loss during updates.

