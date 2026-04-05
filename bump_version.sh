#!/bin/bash
# bump_version.sh
# Automates bumping the app version in app/version.properties

PROPS_FILE="app/version.properties"

if [ ! -f "$PROPS_FILE" ]; then
    echo "Error: $PROPS_FILE not found!"
    exit 1
fi

BUMP_TYPE=$1

if [ -z "$BUMP_TYPE" ]; then
    echo "Usage: ./bump_version.sh [major|minor|patch|code]"
    echo "Defaults to 'code' if no type provided, but we require one for clarity."
    exit 1
fi

# Read current values
VERSION_CODE=$(grep 'VERSION_CODE=' $PROPS_FILE | cut -d'=' -f2)
VERSION_MAJOR=$(grep 'VERSION_MAJOR=' $PROPS_FILE | cut -d'=' -f2)
VERSION_MINOR=$(grep 'VERSION_MINOR=' $PROPS_FILE | cut -d'=' -f2)
VERSION_PATCH=$(grep 'VERSION_PATCH=' $PROPS_FILE | cut -d'=' -f2)

case $BUMP_TYPE in
    major)
        VERSION_MAJOR=$((VERSION_MAJOR + 1))
        VERSION_MINOR=0
        VERSION_PATCH=0
        VERSION_CODE=$((VERSION_CODE + 1))
        ;;
    minor)
        VERSION_MINOR=$((VERSION_MINOR + 1))
        VERSION_PATCH=0
        VERSION_CODE=$((VERSION_CODE + 1))
        ;;
    patch)
        VERSION_PATCH=$((VERSION_PATCH + 1))
        VERSION_CODE=$((VERSION_CODE + 1))
        ;;
    code)
        VERSION_CODE=$((VERSION_CODE + 1))
        ;;
    *)
        echo "Invalid bump type. Use major, minor, patch, or code."
        exit 1
        ;;
esac

# Write new values back to properties file
echo "VERSION_CODE=$VERSION_CODE" > $PROPS_FILE
echo "VERSION_MAJOR=$VERSION_MAJOR" >> $PROPS_FILE
echo "VERSION_MINOR=$VERSION_MINOR" >> $PROPS_FILE
echo "VERSION_PATCH=$VERSION_PATCH" >> $PROPS_FILE

echo "Bumped version to $VERSION_MAJOR.$VERSION_MINOR.$VERSION_PATCH (Code: $VERSION_CODE)"
