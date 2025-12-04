#!/bin/bash

# Script to generate PNG images from PlantUML diagrams

set -e

DOCS_DIR="docs"
PLANTUML_JAR="plantuml.jar"
PLANTUML_URL="https://github.com/plantuml/plantuml/releases/download/v1.2024.3/plantuml-1.2024.3.jar"

echo "🎨 FairLaunch - PlantUML Diagram Generator"
echo ""

# Check if Java is installed
if ! command -v java &> /dev/null; then
    echo "❌ Error: Java is not installed. Please install Java to generate diagrams."
    exit 1
fi

echo "✅ Java found: $(java -version 2>&1 | head -n 1)"

# Download PlantUML if not present
if [ ! -f "$PLANTUML_JAR" ]; then
    echo "📥 Downloading PlantUML..."
    curl -L -o "$PLANTUML_JAR" "$PLANTUML_URL"
    echo "✅ PlantUML downloaded"
else
    echo "✅ PlantUML already present"
fi

# Generate PNG images from all .puml files
echo ""
echo "🖼️  Generating PNG images..."
echo ""

PUML_FILES=$(find "$DOCS_DIR" -name "*.puml")
COUNT=0

for file in $PUML_FILES; do
    filename=$(basename "$file" .puml)
    echo "   Processing: $filename.puml"
    java -jar "$PLANTUML_JAR" "$file" -tpng -charset UTF-8
    COUNT=$((COUNT + 1))
done

echo ""
echo "✅ Generated $COUNT diagram(s) successfully!"
echo ""
echo "📁 PNG files are in: $DOCS_DIR/"
echo "🔗 You can now commit the PNG files to make them visible on GitHub"
