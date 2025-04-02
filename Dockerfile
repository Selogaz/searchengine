# Базовый образ
FROM openjdk:17-jdk-slim

# Установка рабочей директории внутри контейнера
WORKDIR /app

# Копирование JAR-файла (предполагается, что он создан в папке target)
COPY target/SearchEngine-1.0-SNAPSHOT.jar app.jar

# Открытие порта для приложения
EXPOSE 8081

# Команда для запуска приложения
CMD ["java", "-jar", "app.jar"]
