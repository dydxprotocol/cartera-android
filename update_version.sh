#!/bin/sh

# find the version number from the line LIBRARY_VERSION_NAME=0.0.1 in gradle.properties
# and assign it to the variable VERSION
VERSION=$(grep "^LIBRARY_VERSION_NAME=" gradle.properties | sed -n 's/LIBRARY_VERSION_NAME=\(.*\)/\1/p')

echo "Current version is $VERSION. Enter new version (or press enter to skip):"
read NEW_VERSION

#if NEW_VERSION is not empty, replace the version in build.gradle
if [ ! -z "$NEW_VERSION" ]; then
    echo "Updating version to $NEW_VERSION"
    sed -i '' "s/LIBRARY_VERSION_NAME=$VERSION/LIBRARY_VERSION_NAME=$NEW_VERSION/" gradle.properties
fi
