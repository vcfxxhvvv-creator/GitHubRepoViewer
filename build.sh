#!/bin/bash
set -e
echo "🔨 Building APK..."
./gradlew assembleDebug --stacktrace
echo "✅ Build complete!"
echo "📁 APK location: app/build/outputs/apk/debug/app-debug.apk"
ls -lh app/build/outputs/apk/debug/
