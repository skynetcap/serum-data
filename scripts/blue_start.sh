sudo docker pull mmorrell/serum-data
sudo docker stop blue
sudo docker container prune -f
sudo docker run --name blue -d -p 8080:8080 mmorrell/serum-data:latest