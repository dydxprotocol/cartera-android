#!/bin/sh

# search for the first line that starts with "version" in cartera/ build.gradle
# get the value in the quotes
VERSION=$(grep "^            version" cartera/build.gradle | sed -n 's/            version "\(.*\)"/\1/p')


echo "Current version is $VERSION. Enter new version (or press enter to skip):"
read NEW_VERSION

#if NEW_VERSION is not empty, replace the version in build.gradle
if [ ! -z "$NEW_VERSION" ]; then
    echo "Updating version to $NEW_VERSION"
    sed -i '' "s/version \"$VERSION\"/version \"$NEW_VERSION\"/" cartera/build.gradle
fi

