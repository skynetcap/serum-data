# serum-data
A web interface for viewing market data from [Project Serum](https://www.projectserum.com/), on the Solana blockchain.

![serum-data](https://i.ibb.co/CJXrn4g/image.png)

## Building
### Requirements (if not using Docker)
* Java 17
* Maven

### Building with Maven
```
mvn clean install
```

## Running in Docker container (pre-built image)
```dockerfile
docker pull mmorrell/serum-data:latest
docker run -p 8080:8080 serum-data
```

## Running in Docker container (self-build)
```dockerfile
docker build -t serum-data .
docker run -p 8080:8080 serum-data
```

With custom Solana RPC:
```dockerfile
docker build -t serum-data .
docker run -e OPENSERUM_ENDPOINT="http://localhost:8899/" -p 8080:8080 serum-data
```

With one of the preset RPC validators (GENESYSGO, PROJECT_SERUM). Default is GENEYSGO:
```dockerfile
docker build -t serum-data .
docker run -e OPENSERUM_ENDPOINT=PROJECT_SERUM -p 8080:8080 serum-data
```

### Entire server setup (3 scripts) (Docker, Nginx, Ufw, Blue + Green)
These scripts will install NGINX, Docker, and enable UFW on port 22 and 80. It will also start a Blue and Green instance of the application.
```shell
curl -sSL https://raw.githubusercontent.com/skynetcap/serum-data/main/scripts/setup.sh | sh
curl -sSL https://raw.githubusercontent.com/skynetcap/serum-data/main/scripts/blue_start.sh | sh
curl -sSL https://raw.githubusercontent.com/skynetcap/serum-data/main/scripts/green_start.sh | sh
```

## Special Thanks
YourKit for providing us with a free profiler open source license.

YourKit supports open source projects with innovative and intelligent tools
for monitoring and profiling Java and .NET applications.
YourKit is the creator of <a href="https://www.yourkit.com/java/profiler/">YourKit Java Profiler</a>,
<a href="https://www.yourkit.com/.net/profiler/">YourKit .NET Profiler</a>,
and <a href="https://www.yourkit.com/youmonitor/">YourKit YouMonitor</a>.

![YourKit Logo](https://www.yourkit.com/images/yklogo.png)

## Contributing
Open an issue with details, or pull request with changes.

## License
MIT License