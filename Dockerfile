FROM amazoncorretto:25 AS build
WORKDIR /app

RUN yum install -y maven && yum clean all

COPY pom.xml .
RUN mvn dependency:go-offline -q
COPY src ./src
RUN mvn package -DskipTests -q

FROM amazoncorretto:25
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]