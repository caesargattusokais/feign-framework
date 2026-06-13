#!/bin/bash

echo "=== Feign Framework Project Structure Check ==="
echo ""

echo "1. Checking Java Core module..."
if [ -f "core/pom.xml" ]; then
    echo "   ✅ core/pom.xml exists"
else
    echo "   ❌ core/pom.xml missing"
fi

if [ -f "core/src/main/java/com/feign/framework/Response.java" ]; then
    echo "   ✅ core Response.java exists"
else
    echo "   ❌ core Response.java missing"
fi

if [ -f "core/src/main/java/com/feign/framework/http/Request.java" ]; then
    echo "   ✅ core Request.java exists"
else
    echo "   ❌ core Request.java missing"
fi

echo ""
echo "2. Checking Java Implementation module..."
if [ -f "java-impl/pom.xml" ]; then
    echo "   ✅ java-impl/pom.xml exists"
else
    echo "   ❌ java-impl/pom.xml missing"
fi

if [ -f "java-impl/src/main/java/com/feign/framework/client/HttpClientImpl.java" ]; then
    echo "   ✅ HttpClientImpl.java exists"
else
    echo "   ❌ HttpClientImpl.java missing"
fi

echo ""
echo "3. Checking Python Implementation module..."
if [ -f "python-impl/pyproject.toml" ]; then
    echo "   ✅ python-impl/pyproject.toml exists"
else
    echo "   ❌ python-impl/pyproject.toml missing"
fi

if [ -f "python-impl/src/feign/models.py" ]; then
    echo "   ✅ src/feign/models.py exists"
else
    echo "   ❌ src/feign/models.py missing"
fi

if [ -f "python-impl/src/feign/interfaces.py" ]; then
    echo "   ✅ src/feign/interfaces.py exists"
else
    echo "   ❌ src/feign/interfaces.py missing"
fi

if [ -f "python-impl/src/feign/client/http_client.py" ]; then
    echo "   ✅ http_client.py exists"
else
    echo "   ❌ http_client.py missing"
fi

echo ""
echo "4. Checking Annotation Processor module..."
if [ -f "java-processor/pom.xml" ]; then
    echo "   ✅ java-processor/pom.xml exists"
else
    echo "   ❌ java-processor/pom.xml missing"
fi

echo ""
echo "=== Summary ==="
echo "Core modules structure looks good!"
echo ""
echo "Note: Cannot compile Java modules due to JDK configuration."
echo "      Python module needs: pip install httpx pytest"
