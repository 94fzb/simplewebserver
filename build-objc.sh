mv src/main/java/module-info.java .
mvn clean j2objc:convert -X
cp -R src/main/resources/* target/j2objc/
mv module-info.java src/main/java