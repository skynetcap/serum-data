# serum-data
A web interface for viewing market data from [Project Serum](https://www.projectserum.com/), on the Solana blockchain.

Live deployments:
- [openserum.io](https://openserum.io/), [alpha.openserum.io](https://alpha.openserum.io/)

![serum-data](https://i.ibb.co/CJXrn4g/image.png)

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

## Running in Docker container (pre-built image)
```dockerfile
docker pull mmorrell/serum-data:latest
# Starts serum-data Docker image on port 8080
docker run -p 8080:8080 serum-data
```

## Running in Docker container (self-build)
```dockerfile
docker build -t serum-data .
# Starts serum-data Docker image on port 8080
docker run -p 8080:8080 serum-data
```

## Hosting - Ubuntu 20.04 (Docker, Nginx, UFW)
Full setup flow for a new Ubuntu server. This example is for the `staging.openserum.io` subdomain, but will work with any A/CNAME DNS record.

### Install Docker
```shell
# install docker
sudo apt-get update
sudo apt-get install \
ca-certificates \
curl \
gnupg \
lsb-release

sudo mkdir -p /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/ubuntu/gpg | sudo gpg --dearmor -o /etc/apt/keyrings/docker.gpg
echo \
"deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/ubuntu \
$(lsb_release -cs) stable" | sudo tee /etc/apt/sources.list.d/docker.list > /dev/null

sudo apt-get update
sudo apt-get install docker-ce docker-ce-cli containerd.io docker-compose-plugin
```

### Create update + start/restart script
```shell
sudo cat > /home/staging_start.sh <<EOF
sudo docker pull mmorrell/serum-data
sudo docker stop staging
sudo docker container prune -f
sudo docker run --name staging -d -p 8080:8080 mmorrell/serum-data:latest
EOF

sudo chmod +x /home/staging_start.sh
```
### Install Nginx (optional)
```shell
# subdomain is staging.openserum.io
# proxies port 80 to 8080 (spring is running on 8080)
sudo apt install nginx
sudo cat > /etc/nginx/sites-available/staging.openserum.io <<EOF
server {
listen 80;
listen [::]:80;
root /var/www/staging.openserum.io;
index index.html index.htm index.nginx-debian.html;
server_name staging.openserum.io;
location / {
#try_files $uri $uri/ =404;
proxy_pass http://127.0.0.1:8080/;
proxy_set_header X-Real-IP $remote_addr;
proxy_set_header HOST $http_host;
}
}
EOF

# blank index page
sudo mkdir /var/www/staging.openserum.io/
sudo echo "openserum" > /var/www/staging.openserum.io/index.html
sudo systemctl reload nginx
```

### Firewall/UFW (optional)
```shell
sudo ufw allow 80
sudo ufw allow 22
sudo ufw enable
```

### Pull latest + Start Openserum server
```shell
# Initiate startup, App will be live at: http://IP_ADDRESS/
./staging_start.sh
# Follow logs (optional)
sudo docker logs -f staging
```

## Contributing
Open an issue with details, or pull request with changes.

## License
MIT License