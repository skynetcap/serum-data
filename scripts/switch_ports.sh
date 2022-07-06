sudo sed -i 's/8081/HOLDER/g' /etc/nginx/sites-available/openserum.io
sudo sed -i 's/8080/8081/g' /etc/nginx/sites-available/openserum.io
sudo sed -i 's/HOLDER/8080/g' /etc/nginx/sites-available/openserum.io
