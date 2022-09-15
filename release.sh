export JAVA_TOOL_OPTIONS="--add-opens=java.base/java.lang=ALL-UNNAMED"
./mvnw release:clean && ./mvnw  release:prepare && ./mvnw release:perform