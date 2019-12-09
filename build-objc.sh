mvn clean install
zip target/*sources.jar -d module-info.java
cp target/*sources.jar $ProxyClient_IOS_LIB/simplewebserver-sources.jar
