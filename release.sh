export JAVA_HOME=$JDK9_HOME
export PATH=$JAVA_HOME/bin:$PATH
mvn release:clean && mvn release:prepare -Pjdk9 && mvn release:perform -Pjdk9