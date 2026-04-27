.PHONY: up down emit verify test clean

# Build the app
build:
    mvn clean package -DskipTests

# Run the Spring Boot app
run:
    mvn spring-boot:run

# Start verifier infrastructure (dashboard, verifier, etc)
up:
    docker-compose up -d

# Stop verifier infrastructure
down:
    docker-compose down

# Run the emitter + verifier test
emit:
    docker-compose exec verifier ./emit.sh

# Run full verification
verify: up emit down

# Clean up all
clean:
    mvn clean
    docker-compose down -v

# Help
help:
    @echo "Available commands:"
    @echo "  make build     - Build the app"
    @echo "  make run       - Start Spring Boot app"
    @echo "  make up        - Start verifier infrastructure"
    @echo "  make down      - Stop verifier infrastructure"
    @echo "  make emit      - Run emitter + verifier"
    @echo "  make verify    - Full test cycle (up, emit, down)"
    @echo "  make clean     - Clean everything"