# FriendlySearch
**Описание**: Перед вами Spring Boot приложение, работающее с локально установленной базой данных MySQL, имеющее простой веб-интерфейс и API, через который им можно управлять и получать результаты поисковой выдачи по запросу.

## ⚙️ Технологии
- **Backend**: Spring Boot, [![Java](https://img.shields.io/badge/Java-17-red)](https://openjdk.org/), REST API, Hibernate, MySQL
- **Frontend**: HTML, CSS, JavaScript
- **Инструменты**: Maven, Thymeleaf

## Установка
1. Клонируйте репозиторий:
```bash
https://github.com/Selogaz/searchengine.git
```
2. В корневой директории проекта запустите через терминал:
```bash
 java -jar target/SearchEngine-1.0-SNAPSHOT.jar  
```

Для запуска приложения необходимо:
- Создать MySQL базу данных search_engine
- Установить JDK 17 для исключения конфликтов версий
- Запустить на локальном хосте на порте 8081
