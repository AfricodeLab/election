# Election Backend (Modular Monolith)

This project is the backend for a full-featured election management platform. It complements a React/TypeScript frontend and provides all server-side functionality for creating and managing elections, verifying voters, casting votes, monitoring polling stations and aggregating results. The backend is designed as a **modular monolith** using Java and Spring Boot, with clear domain boundaries to ease future migration to microservices.

## Features

- **Authentication & Authorization:** User registration, login, multi-factor authentication (SMS/biometrics), role-based access control.
- **Organization & Role Management:** Create organisations, roles and permissions; assign users to roles.
- **Election Management:** CRUD operations for elections; schedule start/end times; support multiple election types and statuses (draft, upcoming, live, ended).
- **Candidate Management:** Manage candidate profiles, including unstructured data (bios, images) stored in a document database.
- **Voter Registry & Assignment:** Import voter lists, verify identities via Ghana Card ID and SMS/biometrics, assign voters to elections and polling stations.
- **Voting:** Handle vote casting, enforce one vote per voter and generate cryptographic proofs; integrate with a blockchain ledger for immutable vote recording.
- **Launch & Polling Station Management:** Group elections into launches, configure authentication methods and manage polling stations and devices.
- **Election Presets:** Define reusable configurations (table structures, voter authentication requirements) to streamline election setup.
- **Results & Analytics:** Aggregate votes, calculate turnout statistics and provide GraphQL endpoints for dashboards.
- **Audit & System Logs:** Record user actions and system events for compliance and debugging.
- **Notifications:** Send email/SMS/passcodes and broadcast election status changes asynchronously via a message broker.

## Technology Stack

- **Java 17+** and **Spring Boot 3**.
- **Maven** for dependency management and build.
- **Spring Security** for authentication & authorization.
- **Spring Data JPA** (PostgreSQL) for relational data; **MongoDB** for document data; **TimescaleDB/InfluxDB** for time-series metrics.
- **GraphQL Java** for flexible read APIs.
- **WebSockets** for real-time voting and device monitoring.
- **RabbitMQ or Kafka** for asynchronous event messaging.
- **Flyway** for database migrations (scripts located under `src/main/resources/db/migration`).
- **Protobuf** for gRPC definitions (generated sources under `target/generated-sources/protobuf`).

## Project Structure

```
.  (project root)
│
├── .mvn/                   # Maven wrapper
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/brandedhustlers/electionbackend/
│   │   │       ├── common/       # Shared infrastructure (security config, exceptions)
│   │   │       ├── auth/         # Authentication & user management
│   │   │       ├── election/     # Election & candidate management
│   │   │       ├── voting/       # Vote casting & blockchain interface
│   │   ├── resources/
│   │   │   ├── db/migration/     # Flyway migration scripts
│   │   │   ├── graphql/          # GraphQL schema definitions
│   │   │   └── application.yml   # Default Spring Boot configuration
│   └── test/java/…              # Unit and integration tests
└── pom.xml                       # Maven build file
```

## Prerequisites

- **Java 21** or higher installed.
- **Maven 3.8+** (the project includes a Maven wrapper, so `./mvnw` can be used without a local Maven installation).
- **PostgreSQL**, **MongoDB**, and **Redis** running locally or accessible via connection strings.
- **RabbitMQ** or **Kafka** for messaging (optional unless you enable asynchronous features).
- Optional: **Docker** and **Docker Compose** to run dependencies locally.

## Running the Application

1. **Clone the repository**:

   ```bash
   git clone https://github.com/branded-hustlers/election.git
   cd election-backend
   ```

2. **Configure environment variables** (override defaults in `application.yml`). At minimum, set database connection properties:

   ```bash
   export SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/election
   export SPRING_DATASOURCE_USERNAME=postgres
   export SPRING_DATASOURCE_PASSWORD=secret

   export MONGODB_URI=mongodb://localhost:27017/election
   export REDIS_HOST=localhost
   export REDIS_PORT=6379
   # Add RabbitMQ/Kafka settings if used
   ```

3. **Run database migrations** (Flyway runs automatically on application startup). Alternatively, you can run `mvn flyway:migrate` before starting.

4. **Start the application**:

   ```bash
   ./mvnw clean spring-boot:run
   ```

   The application will be accessible at `http://localhost:8080` by default. Swagger or GraphiQL endpoints may be exposed depending on configuration.

5. **Run tests**:

   ```bash
   ./mvnw test
   ```

6. **Docker (optional)**: A `docker-compose.yml` file can be provided to start PostgreSQL, MongoDB, Redis, and RabbitMQ locally. After dependencies are up, run the app as above.

## Development Notes

- The project is currently structured as a single Spring Boot module with internal package boundaries. Each package encapsulates its own controllers, services and repositories. This makes it easy to extract a package into a standalone microservice later.
- For blockchain integration, a dedicated package under `voting` will hold smart contract interfaces and Web3 client code. Initially, blockchain calls can be stubbed or mocked.
- Protobuf definitions for gRPC or inter-service communication reside in `src/main/proto`. The Maven build generates Java sources in `target/generated-sources/protobuf`.
- Database migrations live under `src/main/resources/db/migration` and follow Flyway naming conventions (`V1__initial.sql`, etc.).
- GraphQL schemas are defined under `src/main/resources/graphql`. The project uses GraphQL Java to expose query and mutation endpoints.

## Contributing & Further Work

To contribute to this project:

1. Fork the repository and create a feature branch.
2. Adhere to the established package structure and naming conventions.
3. Write unit/integration tests for new features.
4. Submit a pull request with a clear description of changes.

Future enhancements include completing blockchain integration with zero-knowledge proofs, implementing advanced analytics and dashboards, and gradually migrating to a microservices architecture as load increases.