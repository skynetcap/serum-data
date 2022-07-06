sudo docker pull mmorrell/serum-data
sudo docker stop green
sudo docker container prune -f
sudo docker run --name green -d -p 8081:8080 mmorrell/serum-data:latest