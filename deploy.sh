#!/bin/bash

# Read configuration values from config.txt
while IFS='=' read -r key value; do
    # Trim leading and trailing whitespace from key and value
    key=$(echo "$key" | xargs)
    value=$(echo "$value" | xargs)

    if [[ -n "$key" && -n "$value" ]]; then
        export "$key=$value"
    fi
done < config.txt

# Navigate to the project directory
cd "$PROJECT_DIR" || exit 1

# Build the project using Gradle
sudo ./gradlew clean build || exit 1

# Navigate to the build output directory
cd "$PROJECT_DIR/build/libs" || exit 1

# Delete the old plugin JAR from the server plugins directory
sudo rm -f "$SERVER_DIR/plugins/$PLUGIN_NAME" || exit 1

# Copy the new plugin JAR to the server plugins directory
sudo cp -f "$PLUGIN_NAME" "$SERVER_DIR/plugins/" || exit 1

# Navigate to the server directory
cd "$SERVER_DIR" || exit 1

# Start the Minecraft server
sudo java -Xms2G -Xmx2G -jar "$SERVER_JAR" --nogui || exit 1
