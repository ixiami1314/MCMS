FROM openjdk:8-jdk
VOLUME /tmp
ADD ms-mcms.jar ms-mcms.jar
ENTRYPOINT ["java", "-Xms200m", "-Xmx1G", "-Duser.timezone=GMT+08", "-jar", "/ms-mcms.jar"]
