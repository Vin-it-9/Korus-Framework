# 🚀 Korus Framework

> A lightweight, modular, Spring Boot-inspired Java framework with conditional auto-configuration

lightweight Java framework that provides Spring Boot-like functionality with a modular architecture. Built for developers who want the power of enterprise features without the complexity.

## ✨ Features

### **Core Features**
- **IoC Container** with automatic dependency injection
- **Conditional Auto-configuration** - components load only when dependencies are present
- **Annotation-based Configuration** - familiar Spring-like annotations
- **Configuration Management** - properties-based configuration system
- **Professional Logging** - structured logging with multiple levels

### **Web Features**
- **Embedded Web Server** powered by Undertow
- **RESTful API Support** with automatic routing
- **Request/Response Handling** with JSON serialization
- **Template Engine Integration** (Thymeleaf support)
- **Static Resource Serving**
- **Parameter Binding** - @PathVariable, @RequestParam, @RequestBody

### **Data Features**
- **JPA/Hibernate Integration** with automatic configuration
- **Repository Pattern** with custom query methods
- **Transaction Management** with @Transactional support
- **Multiple Database Support** (MySQL, H2, PostgreSQL)
- **Connection Pooling** with HikariCP

### **Development Tools**
- **Hot Reload** - instant code changes without restart
- **Development Mode** - enhanced debugging and logging
- **Auto-compilation** - automatic Java source compilation
- **File Watching** - monitors source code changes

## Quick Start

### 1. Add Dependencies

```xml
<dependency>
  <groupId>com.korus</groupId>
  <artifactId>korus-framework</artifactId>
  <version>1.0.0</version>
</dependency>
```

### 2. Create Your Main Application

```java
package com.example.myapp;

import com.korus.framework.korus;
import io.korus.core.annotations.KorusApplication;

@KorusApplication
public class MyApplication {
    public static void main(String[] args) {
        korus.run(MyApplication.class, args);
    }
}
```

### 3. Create a REST Controller

```java
package com.example.myapp.controller;

import io.korus.web.annotations.*;
import java.util.List;

@RestController
public class UserController {

    @Autowired
    private UserService userService;

    @GetMapping("/users")
    public List getAllUsers() {
        return userService.findAll();
    }

    @GetMapping("/users/{id}")
    public User getUserById(@PathVariable Integer id) {
        return userService.findById(id);
    }

    @PostMapping("/users")
    public User createUser(@RequestBody User user) {
        return userService.save(user);
    }

    @DeleteMapping("/users/{id}")
    public void deleteUser(@PathVariable Integer id) {
        userService.deleteById(id);
    }
}
```

### 4. Create a Service

```java
package com.example.myapp.service;

import io.korus.web.annotations.*;

@Service
@Transactional
public class UserService {

    @Autowired
    private UserRepository userRepository;

    public List findAll() {
        return userRepository.findAll();
    }

    public User findById(Integer id) {
        return userRepository.findById(id);
    }

    public User save(User user) {
        return userRepository.save(user);
    }

    public void deleteById(Integer id) {
        userRepository.deleteById(id);
    }
}
```

### 5. Create a Repository

```java
package com.example.myapp.repository;

import io.korus.data.annotations.*;
import io.korus.data.*;


@Repository
public interface UserRepository extends JpaRepository {
    
    List findByName(String name);
    
    List findByEmail(String email);
    
    @Query("SELECT u FROM User u WHERE u.status = 'ACTIVE'")
    List findActiveUsers();
}
```

### 6. Create an Entity

```java
package com.example.myapp.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "users")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    
    private String name;
    private String email;
    private String status;
    
    // Constructors, getters, and setters...
}
```

### 7. Configure Database (application.properties)

```properties
# Database Configuration
hibernate.connection.url=jdbc:mysql://localhost:3306/myapp
hibernate.connection.username=root
hibernate.connection.password=password
hibernate.connection.driver_class=com.mysql.cj.jdbc.Driver
hibernate.dialect=org.hibernate.dialect.MySQLDialect
hibernate.hbm2ddl.auto=update

# Server Configuration
server.port=8080
```

## Module Structure

Korus Framework follows a modular architecture where you only include what you need:

### **korus-core** 
**Required for all applications**
- IoC Container and Dependency Injection
- Configuration Management
- Core Annotations (@Component, @Service, @Autowired, @Value)
- Application Context Management

### **korus-web** 
**For web applications**
- Embedded Undertow Web Server
- RESTful API Support
- Request/Response Handling
- Web Annotations (@RestController, @GetMapping, @PostMapping, etc.)
- Template Engine Integration
- Static Resource Serving

### **korus-data** 
**For database applications**
- JPA/Hibernate Integration  
- Repository Pattern Implementation
- Transaction Management (@Transactional)
- Custom Query Support (@Query)
- Multiple Database Support

### **korus-devtools** 
**For development**
- Hot Reload Functionality
- Auto-compilation
- File Change Detection
- Development Mode Enhancements

### **korus-autoconfigure** 
**Auto-included**
- Conditional Configuration
- Automatic Component Detection
- Module Integration

## Usage Examples

### REST API Example

```java
@RestController
@RequestMapping("/api/v1")
public class ProductController {

    @Autowired
    private ProductService productService;

    @GetMapping("/products")
    public List getProducts(@RequestParam(required = false) String category) {
        return category != null ? 
            productService.findByCategory(category) : 
            productService.findAll();
    }

    @PostMapping("/products")
    public Product createProduct(@RequestBody Product product) {
        return productService.save(product);
    }

    @PutMapping("/products/{id}")
    public Product updateProduct(@PathVariable Long id, @RequestBody Product product) {
        product.setId(id);
        return productService.save(product);
    }
}
```

### Service with Transactions

```java
@Service
@Transactional
public class OrderService {

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private InventoryService inventoryService;

    public Order processOrder(Order order) {
        // This method runs in a transaction
        Order savedOrder = orderRepository.save(order);
        inventoryService.updateStock(order.getItems());
        return savedOrder;
    }

    @Transactional(readOnly = true)
    public List getOrderHistory(Long userId) {
        return orderRepository.findByUserId(userId);
    }
}
```

### Custom Repository Queries

```java
@Repository
public interface OrderRepository extends JpaRepository {

    // Automatic query derivation
    List findByUserId(Long userId);
    
    List findByStatusAndCreatedDateAfter(String status, LocalDateTime date);
    
    // Custom JPQL query
    @Query("SELECT o FROM Order o WHERE o.total > :amount AND o.status = 'COMPLETED'")
    List findLargeCompletedOrders(@Param("amount") BigDecimal amount);
    
    // Native SQL query
    @Query(value = "SELECT * FROM orders WHERE DATE(created_date) = CURRENT_DATE", nativeQuery = true)
    List findTodaysOrders();
}
```

### Configuration Management

```java
@Component
@ConfigurationProperties(prefix = "app")
public class AppConfig {
    
    @Value("${app.name:My Application}")
    private String applicationName;
    
    @Value("${app.version:1.0.0}")  
    private String version;
    
    @Value("${app.debug:false}")
    private boolean debugMode;
    
    // Getters and setters...
}
```

## ⚙️ Configuration

### Application Properties

```properties
# Server Configuration
server.port=8080
server.host=localhost

# Database Configuration  
hibernate.connection.url=jdbc:mysql://localhost:3306/mydb
hibernate.connection.username=username
hibernate.connection.password=password
hibernate.connection.driver_class=com.mysql.cj.jdbc.Driver
hibernate.dialect=org.hibernate.dialect.MySQLDialect
hibernate.hbm2ddl.auto=update
hibernate.show_sql=false
hibernate.format_sql=false

# Connection Pool Configuration
hibernate.hikari.connectionTimeout=20000
hibernate.hikari.idleTimeout=300000
hibernate.hikari.maxLifetime=1200000
hibernate.hikari.minimumIdle=5
hibernate.hikari.maximumPoolSize=20

```

##  Running Your Application

### Production Optimized
```java

@KorusApplication
public static void main(String[] args) {
    korus.runProduction(MyApplication.class, args);
}

```

## API Reference

### Core Annotations

| Annotation | Description | Module |
|------------|-------------|---------|
| `@Component` | Marks class as a component for DI | korus-core |
| `@Service` | Marks class as a service layer component | korus-core |
| `@Repository` | Marks interface as a data repository | korus-data |
| `@Controller` | Marks class as a web controller | korus-web |
| `@RestController` | Marks class as a REST controller | korus-web |
| `@Autowired` | Enables automatic dependency injection | korus-core |
| `@Value` | Injects property values | korus-core |
| `@Transactional` | Enables transaction management | korus-data |

### Web Annotations

| Annotation | Description | Example |
|------------|-------------|---------|
| `@RequestMapping` | Maps HTTP requests | `@RequestMapping("/api")` |
| `@GetMapping` | Maps GET requests | `@GetMapping("/users")` |
| `@PostMapping` | Maps POST requests | `@PostMapping("/users")` |
| `@PutMapping` | Maps PUT requests | `@PutMapping("/users/{id}")` |
| `@DeleteMapping` | Maps DELETE requests | `@DeleteMapping("/users/{id}")` |
| `@PatchMapping` | Maps PATCH requests | `@PatchMapping("/users/{id}")` |
| `@PathVariable` | Extracts path variables | `@PathVariable Long id` |
| `@RequestParam` | Extracts query parameters | `@RequestParam String name` |
| `@RequestBody` | Maps request body to object | `@RequestBody User user` |

### Data Annotations

| Annotation | Description | Example |
|------------|-------------|---------|
| `@Query` | Custom JPQL/SQL query | `@Query("SELECT u FROM User u")` |
| `@Param` | Named query parameter | `@Param("name") String name` |
| `@Transactional` | Transaction boundary | `@Transactional(readOnly = true)` |


##  Development Setup

### Prerequisites
- **Java 21+** (OpenJDK or Oracle JDK)
- **Maven 3.8+** 
- **IDE** (IntelliJ IDEA, Eclipse, or VS Code)
- **Database** (MySQL, PostgreSQL, or H2 for testing)

### Hot Reload Development

Korus Framework includes powerful hot reload capabilities:

```java
// Enable hot reload in your main method
@KorusApplication
public static void main(String[] args) {
    // Hot reload automatically enabled in development mode
    korus.run(MyApplication.class, args);
}
```

**Hot Reload Features:**
- ✅ **Automatic Compilation** - Java files compiled on save
- ✅ **Instant Restart** - Application restarts with new code
- ✅ **Route Refresh** - New endpoints available immediately  
- ✅ **Bean Reload** - Dependency injection updates automatically
- ✅ **Configuration Reload** - Properties changes applied instantly

## Best Practices

### Project Structure
```
src/main/java/
├── com/example/myapp/
│   ├── MyApplication.java          # Main application class
│   ├── controller/                 # REST controllers
│   │   └── UserController.java
│   ├── service/                    # Business logic
│   │   └── UserService.java
│   ├── repository/                 # Data access
│   │   └── UserRepository.java
│   ├── entity/                     # JPA entities
│   │   └── User.java
│   ├── dto/                        # Data transfer objects
│   │   └── UserDto.java
│   └── config/                     # Configuration classes
│       └── AppConfig.java
src/main/resources/
├── application.properties          # Configuration
├── banner.txt                      # Custom banner
└── templates/                      # Thymeleaf templates
    └── index.html
```

### Coding Guidelines

1. **Use Proper Annotations**
```java
@RestController  // For REST APIs
@Controller      // For web pages
@Service         // For business logic
@Repository      // For data access
@Component       // For general components
```

2. **Handle Transactions Properly**
```java
@Service
@Transactional  // Class-level for all methods
public class UserService {
    
    @Transactional(readOnly = true)  // Read-only for queries
    public User findById(Long id) {
        return userRepository.findById(id);
    }
    
    // Write operations use class-level @Transactional
    public User save(User user) {
        return userRepository.save(user);
    }
}
```

3. **Use Proper Exception Handling**
```java
@RestController
public class UserController {
    
    @GetMapping("/users/{id}")
    public ResponseEntity getUser(@PathVariable Long id) {
        try {
            User user = userService.findById(id);
            return ResponseEntity.ok(user);
        } catch (UserNotFoundException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }
}
```

## Deployment

### Building for Production

```bash
# Clean build
mvn clean package

# Skip tests (if needed)
mvn clean package -DskipTests

# Build with specific profile
mvn clean package -Pproduction
```

## Performance Tuning

### JVM Options
```bash
java -Xms512m -Xmx2g -XX:+UseG1GC -jar myapp.jar
```

### Connection Pool Tuning
```properties
hibernate.hikari.minimumIdle=10
hibernate.hikari.maximumPoolSize=50
hibernate.hikari.connectionTimeout=30000
hibernate.hikari.idleTimeout=600000
hibernate.hikari.maxLifetime=1800000
```

### Database Optimization
```properties
hibernate.jdbc.batch_size=25
hibernate.order_inserts=true
hibernate.order_updates=true
hibernate.jdbc.batch_versioned_data=true
```

## FAQ

**Q: How is Korus different from Spring Boot?**
A: Korus is designed to be lighter and more modular. You only include the modules you need, resulting in smaller applications and faster startup times.

**Q: Can I use Korus with existing Spring libraries?**  
A: While Korus uses similar patterns, it's a separate framework. Migration would require changing imports and some configuration.

**Q: Does Korus support microservices?**
A: Yes! Korus's modular architecture makes it perfect for microservices. You can create lightweight services with just korus-core, or full web services with korus-web.

*Korus Framework - Building the future of Java development, one module at a time.*
