#!/bin/bash
# Vehicular Edge Computing Simulator - Build & Run Script

echo ""
echo "========================================"
echo "   VEC Simulator Build & Run"
echo "========================================"
echo ""

PROJECT_DIR="D:/JavaDev/iFogSim"
SRC_DIR="$PROJECT_DIR/src"
BIN_DIR="$PROJECT_DIR/bin"
MAIN_CLASS="org.fog.test.VEC.StartSimulation"

# Create bin directory if not exists
if [ ! -d "$BIN_DIR" ]; then
    echo "[INFO] Creating bin directory..."
    mkdir -p "$BIN_DIR"
fi

# Compile
echo "[1/2] Compiling source files..."
cd "$PROJECT_DIR"

javac -d "$BIN_DIR" \
    "$SRC_DIR"/org/fog/test/VEC/*.java \
    "$SRC_DIR"/org/fog/test/VEC/infrastructure/*.java \
    "$SRC_DIR"/org/fog/test/VEC/task/*.java \
    "$SRC_DIR"/org/fog/test/VEC/scheduler/*.java 2>&1

if [ $? -ne 0 ]; then
    echo "[ERROR] Compilation failed!"
    exit 1
fi

echo "[SUCCESS] Compilation completed."
echo ""

# Run
echo "[2/2] Starting simulator..."
echo ""
java -cp "$BIN_DIR" "$MAIN_CLASS"

echo ""
echo "[INFO] Simulator finished."

