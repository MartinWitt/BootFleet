# 📋 TodoApp

A modern, production-ready todo application built with Spring Boot, featuring dark-mode UI, recurring task scheduling via Cron expressions, and professional task management capabilities.

## ✨ Features

### Core Features
- ✅ **Full Todo Lifecycle** — Create, read, update, delete tasks
- ✅ **Task Tagging** — Organize todos with custom tags
- ✅ **Deadlines** — Set task dates and optional times
- ✅ **Status Management** — Track task status (OPEN, ACTIVE, COMPLETED)
- ✅ **Drag & Drop Reordering** — Sortable task list with visual feedback
- ✅ **Tag Filtering** — Filter todos by tags

### Advanced Features
- ✅ **Recurring Tasks** — Schedule tasks with Cron expressions
  - Quick presets: Daily, Weekly, Monthly, 9 AM
  - Custom Cron format support
- ✅ **Dark Mode UI** — Modern, professional design with Indigo/Pink gradient
- ✅ **Responsive Layout** — Mobile-first design
- ✅ **Tag Management** — Create, view, and delete tags

## 🚀 Getting Started

### Prerequisites
- Java 25+
- Maven 3.8+
- H2 Database (development) or MariaDB (production)

### Development

```bash
cd todo-app
mvn clean install
mvn spring-boot:run
```

Open http://localhost:8080/todos

### Production (Cloud)

```bash
mvn package -DskipTests

# Run with MariaDB
java -Dspring.profiles.active=cloud \
  -Dspring.datasource.url=jdbc:mariadb://mariadb-host:3306/todoapp \
  -Dspring.datasource.username=todoapp \
  -DDB_PASSWORD=your_secure_password \
  -jar target/todo-app-0.0.1-SNAPSHOT.jar
```

#### Database Setup (MariaDB)

```sql
CREATE DATABASE todoapp CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'todoapp'@'%' IDENTIFIED BY 'your_secure_password';
GRANT ALL PRIVILEGES ON todoapp.* TO 'todoapp'@'%';
FLUSH PRIVILEGES;
```

Liquibase automatically creates all tables and indexes on first run.

## 📁 Project Structure

```
todo-app/
├── src/main/java/io/github/martinwitt/todoapp/
│   ├── domain/          # Todo, Tag entities
│   ├── service/         # TodoService, TagService
│   ├── repository/      # Spring JPA repositories
│   └── web/             # TodoController
├── src/main/resources/
│   ├── templates/       # Thymeleaf HTML (list, form, tags)
│   ├── static/
│   │   ├── css/         # Dark theme styling
│   │   └── js/          # Drag & drop (SortableJS)
│   ├── db/changelog/    # Liquibase migrations
│   ├── application.yaml         # Dev config (H2)
│   └── application-cloud.yaml   # Cloud config (MariaDB)
└── pom.xml
```

## 🎨 UI Features

- **Dark Theme** — Indigo & Pink gradient header
- **Todo List** — Sortable cards with metadata
- **Create/Edit** — Form with Cron scheduler for recurring tasks
- **Tags** — Manage task categories
- **Responsive** — Works on desktop & mobile

### Cron Scheduling

Quick presets:
- 📅 **Daily** — `0 0 * * *`
- 📅 **Weekly** — `0 0 * * 1` (Monday)
- 📅 **Monthly** — `0 0 1 * *` (1st of month)
- ⏰ **9 AM** — `0 9 * * *`

Or enter custom Cron format: `minute hour day month weekday`

## 🛠️ Tech Stack

- **Framework**: Spring Boot 4.x
- **Template**: Thymeleaf 3.1
- **Database**: MariaDB (Cloud) / H2 (Dev)
- **Migrations**: Liquibase
- **ORM**: Spring Data JPA
- **Frontend**: Vanilla JS + SortableJS
- **Styling**: Custom Dark CSS
- **Java**: 25

## 🔧 Configuration

### Development
```yaml
# application.yaml
spring.datasource.url: jdbc:h2:mem:todoapp
spring.jpa.hibernate.ddl-auto: update
spring.thymeleaf.cache: false
```

### Cloud (MariaDB)
```yaml
# application-cloud.yaml
spring.datasource.url: jdbc:mariadb://...
spring.datasource.username: todoapp
spring.datasource.password: ${DB_PASSWORD}
spring.liquibase.enabled: true
spring.jpa.hibernate.ddl-auto: none
```

## 📊 Database

**Liquibase-managed schema:**
- `todos` — id, title, text, deadline, status, position, cron_expression
- `tags` — id, name
- `todo_tags` — junction table with cascade delete
- Indexes on status, deadline, position, tag name

## 🧪 Testing

```bash
mvn test          # Run tests
mvn verify        # Full build with coverage
mvn package -DskipTests
```

## 🚢 Docker

```bash
docker build -t todo-app:latest .
docker run -e SPRING_PROFILES_ACTIVE=cloud \
           -e DB_PASSWORD=password \
           -p 8080:8080 \
           todo-app:latest
```

## 📝 Notes

- **Drag & Drop**: Uses SortableJS; reorder posts to `/todos/reorder` API
- **Tags**: Checkbox-based selection in form (no CSV input)
- **Cron**: Stored but requires external scheduler for execution
- **Spring JPA**: Uses method names instead of @Query (cleaner, type-safe)
- **Formatting**: Google Java Format via Spotless

## 👤 Author

[Martin Wittlinger](https://github.com/martinwitt)

---

**Last Updated:** 2026-04-05  
**Status:** ✨ Production Ready
