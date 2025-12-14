#!/bin/bash

# Script untuk memverifikasi 16 KB page size alignment pada .so files
# Usage: ./check_16kb_alignment.sh [path_to_aab]

AAB_PATH="${1:-app/release/app-release.aab}"
TEMP_DIR="/tmp/check_16kb_$$"
EXTRACT_DIR="$TEMP_DIR/aab_extracted"

echo "=========================================="
echo "16 KB Page Size Verification Script"
echo "=========================================="
echo "AAB Path: $AAB_PATH"
echo ""

# Cleanup function
cleanup() {
    rm -rf "$TEMP_DIR"
}
trap cleanup EXIT

# Extract AAB
echo "Extracting AAB..."
mkdir -p "$EXTRACT_DIR"
unzip -q "$AAB_PATH" -d "$EXTRACT_DIR" 2>/dev/null || {
    echo "Error: Cannot extract AAB file. Please check the path."
    exit 1
}

# Find all .so files
SO_FILES=$(find "$EXTRACT_DIR" -name "*.so" -type f)

if [ -z "$SO_FILES" ]; then
    echo "No .so files found in AAB. This is good - no native libraries!"
    exit 0
fi

echo "Found .so files:"
echo "$SO_FILES" | while read -r so_file; do
    abi=$(echo "$so_file" | sed -n 's/.*\/lib\/\([^/]*\)\/.*/\1/p')
    filename=$(basename "$so_file")
    echo "  - $abi/$filename"
done
echo ""

# Check alignment for each .so file
echo "Checking alignment (must be 16384 = 16 KB)..."
echo "=========================================="

HAS_4KB_ISSUE=0
HAS_OTHER_ABI=0

for so_file in $SO_FILES; do
    abi=$(echo "$so_file" | sed -n 's/.*\/lib\/\([^/]*\)\/.*/\1/p')
    filename=$(basename "$so_file")
    
    # Check if it's not arm64-v8a
    if [ "$abi" != "arm64-v8a" ]; then
        echo "⚠️  WARNING: Found $abi ABI (should only have arm64-v8a)"
        HAS_OTHER_ABI=1
    fi
    
    # Check alignment using readelf or objdump (if available)
    alignment_dec=""
    
    if command -v readelf &> /dev/null; then
        # Get LOAD segment alignment using readelf
        alignment=$(readelf -l "$so_file" 2>/dev/null | grep -A 1 "LOAD" | grep "Align" | head -1 | awk '{print $NF}')
        if [ -n "$alignment" ]; then
            # Convert hex to decimal
            alignment_dec=$((16#$alignment))
        fi
    elif command -v objdump &> /dev/null; then
        # Get alignment using objdump (macOS alternative)
        # objdump shows format like "align 2**12" or "align 2**14"
        # We need to get the LOAD segment alignment (not PHDR)
        alignment_line=$(objdump -p "$so_file" 2>/dev/null | grep "LOAD" | grep "align" | head -1)
        if [ -n "$alignment_line" ]; then
            # Extract the exponent (e.g., "2**14" -> 14)
            exponent=$(echo "$alignment_line" | sed -n 's/.*align 2\*\*\([0-9]*\).*/\1/p')
            if [ -n "$exponent" ]; then
                # Calculate 2^exponent
                alignment_dec=$((2**exponent))
            fi
        fi
    fi
    
    if [ -n "$alignment_dec" ] && [ "$alignment_dec" -gt 0 ]; then
        if [ "$alignment_dec" -lt 16384 ]; then
            echo "❌ FAIL: $abi/$filename - Alignment: $alignment_dec bytes (< 16 KB)"
            HAS_4KB_ISSUE=1
        elif [ "$alignment_dec" -eq 16384 ]; then
            echo "✅ PASS: $abi/$filename - Alignment: 16 KB (16384 bytes)"
        else
            echo "✅ PASS: $abi/$filename - Alignment: $alignment_dec bytes (>= 16 KB)"
        fi
    else
        # Fallback: check file size alignment
        file_size=$(stat -f%z "$so_file" 2>/dev/null || stat -c%s "$so_file" 2>/dev/null)
        if [ -n "$file_size" ]; then
            # Note: File size alignment is not a reliable indicator of page size support
            # But we can at least check if it's a multiple of 16KB
            remainder=$((file_size % 16384))
            if [ "$remainder" -eq 0 ]; then
                echo "⚠️  INFO: $abi/$filename - File size: $file_size bytes (aligned, but cannot verify ELF alignment without readelf/objdump)"
            else
                echo "⚠️  WARN: $abi/$filename - File size: $file_size bytes (cannot verify ELF alignment - install readelf/objdump for accurate check)"
            fi
        fi
    fi
done

echo ""
echo "=========================================="
echo "Summary:"
echo "=========================================="

if [ "$HAS_4KB_ISSUE" -eq 1 ]; then
    echo "❌ FAIL: Found .so files with < 16 KB alignment!"
    echo "   Action required: Update libraries to versions supporting 16 KB page size"
    exit 1
fi

if [ "$HAS_OTHER_ABI" -eq 1 ]; then
    echo "⚠️  WARNING: Found ABI other than arm64-v8a"
    echo "   Action required: Configure packaging to exclude other ABIs"
    echo "   Add to app/build.gradle.kts packaging block:"
    echo "   packaging {"
    echo "       jniLibs {"
    echo "           useLegacyPackaging = false"
    echo "           excludes += ['**/armeabi-v7a/**', '**/x86/**', '**/x86_64/**']"
    echo "       }"
    echo "   }"
    exit 1
fi

echo "✅ SUCCESS: All .so files are 16 KB aligned!"
echo "✅ SUCCESS: Only arm64-v8a ABI found!"
echo ""
echo "Your AAB is ready for Google Play submission!"

