# Especifica la versión de la sintaxis de Dockerfile a utilizar
# syntax=docker/dockerfile:1.7

# Primera etapa: Construcción de la aplicación
# Utiliza Maven 3.9.11 con JDK Eclipse Temurin 25 como imagen base
FROM maven:3.9.11-eclipse-temurin-25 AS build

# Establece el directorio de trabajo dentro del contenedor
WORKDIR /workspace

# Copia los archivos de configuración de Maven para aprovechar el caché de capas de Docker
COPY pom.xml .
COPY mvnw .
COPY .mvn .mvn

# Descarga todas las dependencias del proyecto en modo batch (-B)
# Esto se hace antes de copiar el código fuente para aprovechar el caché de Docker
RUN mvn -B dependency:go-offline

# Copia el código fuente de la aplicación
COPY src src

# Compila y empaqueta la aplicación saltando los tests
# -B ejecuta en modo batch, clean elimina compilaciones previas, package genera el JAR
RUN mvn -B clean package -DskipTests

# Segunda etapa: Imagen de ejecución
# Utiliza JRE Eclipse Temurin 25 (más liviana que la imagen con JDK completo)
FROM eclipse-temurin:25-jre

# Establece el directorio de trabajo para la aplicación
WORKDIR /app

# Crea un usuario y grupo del sistema llamado 'spring' para ejecutar la aplicación
# Esto mejora la seguridad evitando ejecutar la aplicación como root
RUN addgroup --system spring && adduser --system spring --ingroup spring

# Copia el JAR compilado desde la etapa de construcción (build)
COPY --from=build /workspace/target/mto-configuration-0.0.1-SNAPSHOT.jar app.jar

# Cambia al usuario 'spring' para ejecutar la aplicación sin privilegios de root
USER spring:spring

# Expone el puerto 8080 en el que la aplicación escuchará
EXPOSE 8080

# Define el comando de inicio de la aplicación
ENTRYPOINT ["java", "-jar", "/app/app.jar"]