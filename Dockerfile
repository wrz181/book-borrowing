# 后端镜像：多阶段构建（构建环境自带 Maven + JDK17，无需本机安装）
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build
# 先拷 pom 下载依赖，利用 Docker 层缓存加速二次构建
COPY pom.xml .
RUN mvn -q -e dependency:go-offline
COPY src ./src
RUN mvn -q package -DskipTests

# 运行镜像：仅 JRE，体积小
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /build/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
