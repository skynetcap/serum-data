# serum-data
A web interface for viewing market data from [Project Serum](https://www.projectserum.com/), on the Solana blockchain.

![serum-data](https://i.ibb.co/gMZJVX2/ss.png)

## Building
### Requirements (if not using Docker)
* Java 11
* Maven

### Building with Maven
```
mvn clean install
```

## Running in IDE
Use the Spring Boot Run Configuration included with IntelliJ.

## Running in Docker container
Docker Build
```dockerfile
# Build's serum-data Docker image
docker build -t serum-data .
```
Docker Run
```dockerfile
# Starts serum-data Docker image on port 8080
docker run -p 8080:8080 serum-data
```

## Contributing
Open an issue with details, or pull request with changes.

## License
MIT License