sudo apt-get update -y
sudo apt-get install -y \
ca-certificates \
curl \
gnupg \
lsb-release

sudo mkdir -p /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/ubuntu/gpg | sudo gpg --dearmor -o /etc/apt/keyrings/docker.gpg
echo \
"deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/ubuntu \
$(lsb_release -cs) stable" | sudo tee /etc/apt/sources.list.d/docker.list > /dev/null

sudo apt-get update -y
sudo apt-get install docker-ce docker-ce-cli containerd.io docker-compose-plugin -y

sudo cat > /home/prod_start.sh <<EOF
sudo docker pull mmorrell/serum-data
sudo docker stop production
sudo docker container prune -f
sudo docker run --name production -d -p 8080:8080 mmorrell/serum-data:latest
EOF

sudo chmod +x /home/prod_start.sh

sudo apt install nginx -y
sudo cat > /etc/nginx/sites-available/openserum.io <<EOF
server {
listen 80;
listen [::]:80;
root /var/www/openserum.io;
index index.html index.htm index.nginx-debian.html;
server_name openserum.io;
location / {
                proxy_pass http://127.0.0.1:8080/;
                proxy_set_header X-Real-IP \$remote_addr;
                proxy_set_header HOST \$http_host;
                # try_files \$uri \$uri/ =404;
        }
}
EOF

sudo ln -s /etc/nginx/sites-available/openserum.io /etc/nginx/sites-enabled/

sudo rm /etc/nginx/sites-enabled/default
sudo rm /etc/nginx/sites-available/default

# blank index page
sudo mkdir /var/www/openserum.io/
sudo echo "" > /var/www/openserum.io/index.html
sudo systemctl reload nginx

sudo ufw allow 80
sudo ufw allow 22
sudo echo "y" | sudo ufw enable