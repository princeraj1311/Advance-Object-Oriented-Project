#!/bin/sh
# Build and run CineSmart from the command line.
#
#   ./run.sh              compile, copy resources, run
#   ./run.sh clean        delete the out folder and the data folder, then rebuild
#   ./run.sh jar          do all that and also write dist/cinesmart.jar
#   RELEASE=17 ./run.sh   compile for an older JDK (17 or later) so the classes and the jar
#                         run on other machines; leave RELEASE empty to use this JDK
#
# The jar is self-contained: classes plus src/cinesmart/assets, with Main-Class set, so it
# starts anywhere with
#
#   java -jar dist/cinesmart.jar
#
# Data files are always created in the data/ folder of the FOLDER YOU RUN FROM, not the folder
# the jar sits in, so the same jar can be carried to a machine and used there untouched.
#
# Resources are copied next to the classes because Logo looks for /cinesmart/assets/logo.png on
# the classpath first - that is also how it works from inside the jar.
set -e

PROJECT_DIR=$(cd "$(dirname "$0")" && pwd)
cd "$PROJECT_DIR"

if [ "$1" = "clean" ]; then
    echo "Cleaning out/ and data/"
    rm -rf out data
fi

JAVAC_FLAGS=""
if [ -n "$RELEASE" ]; then
    echo "Targeting Java $RELEASE"
    JAVAC_FLAGS="--release $RELEASE"
fi

echo "Compiling sources ..."
mkdir -p out
find src -name '*.java' > out/sources.txt
javac $JAVAC_FLAGS -d out @out/sources.txt
rm -f out/sources.txt

echo "Copying resources ..."
mkdir -p out/cinesmart
rm -rf out/cinesmart/assets
cp -R src/cinesmart/assets out/cinesmart/assets

if [ "$1" = "jar" ]; then
    echo "Packaging dist/cinesmart.jar ..."
    mkdir -p dist
    jar --create --file dist/cinesmart.jar --main-class cinesmart.CineSmartApp -C out .
    echo "Done: java -jar dist/cinesmart.jar"
    exit 0
fi

echo "Starting CineSmart (splash screen, then the main window) ..."
exec java -cp out cinesmart.CineSmartApp
