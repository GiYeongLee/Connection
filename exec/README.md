# 포팅매뉴얼

# 🔨빌드 및 배포

## 개발환경

### Client

- react : 18.2.0
- typescript : 4.8.4
- chakra-ui : 2.3.6
- redux-toolkit : 8.0.4

### Server(Spring)

- Ubuntu 20.04
- Maven 3.8.6
- Tomcat 9.0.54
- openjdk 11.0.16
- mariaDB 10.6

### Server(Node.js)

- express : 4.8.2
- socket.io : 4.5.3

# 배포 방법

## 기본 환경 설정

- nginx 설치
    
    ```bash
    sudo apt-get update #운영체제에서 사용 가능한 패키지들과 그 버전에 대한 정보(리스트) 업데이트
    sudo apt install nginx -y #nginx 설치하기
    nginx -v #설치한 nginx 버전 확인
    sudo service nginx status #nginx running 상태 확인
    sudo service nginx start
    ```
    
- certbot 설치
    
    ```bash
    add-apt-repository ppa:certbot/certbot
    apt-get update
    apt-get install python3-certbot-nginx
    certbot certonly --nginx -d example.com #설치된 certbot을 이용하여 도메인(ex. example.com) 에 대한 SSL 인증서만 발급받는다.
    
    #optional 인증서 자동갱신(90일)
    sudo certbot renew --dry-run
    ```
    
- nginx 설정(최상단 설정)
    
    ```bash
    nano /etc/nginx/sites-available/co-nnection.conf
    수정은 깃랩 파일 참고
    #파일 문제없는지 확인
    sudo nginx -t
    #심볼릭링크 생성
    sudo ln -s /etc/nginx/sites-available/co-nnection.conf /etc/nginx/sites-enabled
    sudo service nginx restart
    ```
    
    - /etc/nginx/sites-available/co-nnection.conf
        
        ```bash
        server {
          listen 80;
        
          server_name k7c202.p.ssafy.io;
          return 301 https://k7c202.p.ssafy.io$request_uri;
        }
        
        server {
          listen 443;
          server_name k7c202.p.ssafy.io;
          ssl on;
        
          # ssl 인증서 적용하기
          ssl_certificate /etc/letsencrypt/live/k7c202.p.ssafy.io/fullchain.pem;
          ssl_certificate_key /etc/letsencrypt/live/k7c202.p.ssafy.io/privkey.pem;
        
          location / {
            proxy_pass http://k7c202.p.ssafy.io:3000/;
            proxy_set_header Host $http_host;
            proxy_set_header X-Real-IP $remote_addr;
            proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
            proxy_set_header X-Forwarded-Proto $scheme;
          }
        
                                #BE API
          location /api/ {
            proxy_pass http://k7c202.p.ssafy.io:8080/;
          }
        
                #Socket
          location /node {
            rewrite ^/node(.*)$ $1 break;
            proxy_pass http://localhost:8000;
            #Socket.IO
            proxy_http_version 1.1;
            proxy_set_header Upgrade $http_upgrade;
            proxy_set_header Connection "upgrade";
            proxy_set_header Host $http_host;
          }
        
        }
        
        server {
            if ($host = k7c202.p.ssafy.io) {
                return 301 https://$host$request_uri;
            }
        
            listen 80;
            server_name k7c202.p.ssafy.io;
              return 404; # managed by Certbot
        }
        ```
        
- Jenkins 설정
    
    ```bash
    docker run -itd --name jenkins -p 9090:8080 jenkins/jenkins:lts
    #초기패스워드 입력
    docker exec jenkins cat /var/jenkins_home/secrets/initialAdminPassword
    ```
    

## 백엔드 배포 환경설정

- 젠킨스 파이프라인 설정
    
    ![image](https://user-images.githubusercontent.com/82570138/203447290-e53b5ac8-1fc8-4f4e-8dc8-746cb0c03825.png)

    
- 젠킨스파일(backend/connection/Jenkinsfile)
    
    ```bash
    pipeline {
        agent any
        tools { 
          maven 'maven386'
          jdk 'java11'
        }
        stages {
            stage("build"){
                steps {
                    script{
                        try {
                            sh 'docker stop backend'
                            sh 'docker rm backend'
                            sh 'docker rmi backend-image'
                        } catch (e) {
                            sh 'echo "fail to stop and remove container"'
                        }
                    }
                    sh 'mvn clean package -f ./backend/connection/pom.xml'
                    sh 'docker build -t backend-image ./backend/connection'
                    echo 'Build image...'
                    sh 'docker run -d -p 8080:8080 --restart="always" --name backend backend-image'
                    echo 'docker run complete'
                }
            }    
        }    
    }
    ```
    
- 도커파일(backend/connection/Dockerfile)
    
    ```bash
    FROM maven:3.8.6-jdk-11
    VOLUME /tmp
    ADD target/connection-0.0.1-SNAPSHOT.jar app.jar
    ENV TZ=Asia/Seoul
    EXPOSE 8080
    ENTRYPOINT ["java","-Djava.security.egd=file:/dev/./urandom","-jar","/app.jar"]
    ```
    

## 프론트엔드 및 소켓서버 배포 환경설정

- 젠킨스 파이프라인 설정
    
    ![image](https://user-images.githubusercontent.com/82570138/203447348-bf17b9c5-78b8-4727-9fc0-6491c9c118bb.png)
    
- 젠킨스 환경변수 셋팅
    
    ![image](https://user-images.githubusercontent.com/82570138/203447535-80dff43c-8e95-4e23-b6ca-0eea34a3027a.png)
    
    ![image](https://user-images.githubusercontent.com/82570138/203447385-a0b4911d-63e7-440c-b11c-8dd47c31d7e6.png)
    

- 프론트 및 소켓서버 빌드 젠킨스파일(frontend/Jenkinsfile)
    
    ```jsx
    pipeline {
        agent any
        stages {
            stage("build"){
                steps {
                    sh 'docker image prune -a --force'
                    sh 'docker build -t wlsgh97/coalla-node-server ./node'
                    sh 'docker build -t wlsgh97/coalla-frontend ./frontend --build-arg EXTENSION_ID=${EXTENSION_ID} --build-arg EXTENSION_URL=${EXTENSION_URL} --build-arg OAUTH_REDIRECT_URL=${OAUTH_REDIRECT_URL} --build-arg API_URL=${API_URL}'
                    sh 'echo "$DOCKER_HUB_PASSWORD" | docker login -u "$DOCKER_HUB_ID" --password-stdin'
                    echo 'Build image...'
                    sh "docker push wlsgh97/coalla-frontend"
                    sh "docker push wlsgh97/coalla-node-server"
                    script{
                        try {
                            sh 'docker stop node-server'
                            sh 'docker rm node-server'
                            // sh 'docker rmi wlsgh97/coalla-node-server'
                        } catch (e) {
                            sh 'echo "fail to stop and remove node-server container"'
                        }
                        try {
                            sh 'docker stop frontend'
                            sh 'docker rm frontend'
                            // sh 'docker rmi wlsgh97/coalla-frontend'
                        } catch (e) {
                            sh 'echo "fail to stop and remove container"'
                        }
                    }
                    sh "docker run -d -p 3000:3000 --name frontend wlsgh97/coalla-frontend"
                    sh "docker run -d -p 8000:8000 --name node-server wlsgh97/coalla-node-server"
                }
            }    
        }    
    }
    ```
    

- 프론트 도커파일(/front/Dockerfile)
    
    ```jsx
    FROM node:14.19.2 as builder
    
    WORKDIR /app
    
    COPY package*.json ./
    
    RUN npm i
    
    COPY ./ ./
    
    ARG API_URL
    ARG OAUTH_REDIRECT_URL
    ARG EXTENSION_ID
    ARG EXTENSION_URL
    
    RUN REACT_APP_EXTENSION_ID=${EXTENSION_ID} REACT_APP_EXTENSION_URL=${EXTENSION_URL} REACT_APP_OAUTH_REDIRECT_URL=${OAUTH_REDIRECT_URL} REACT_APP_API_URL=${API_URL} npm run build 
    
    FROM nginx
    
    EXPOSE 3000
    
    COPY ./nginx/default.conf /etc/nginx/conf.d/default.conf
    
    COPY --from=builder /app/build /usr/share/nginx/html
    
    CMD ["nginx","-g","daemon off;"]
    ```
    

- 프론트단 엔진엑스 설정(/frontend/nginx/default.conf)
    
    ```jsx
    server {
      listen 3000;
    
      location / {
        root /usr/share/nginx/html;
    
        index index.html index.htm;
    
        try_files $uri $uri/ /index.html;
    
        gzip_static always;
      }
    }
    ```
    

- 프론트 (노드) 도커파일(/node/Dockerfile)
    
    ```jsx
    FROM node:alpine
    
    WORKDIR /usr/app
    
    COPY ./package*.json ./
    
    RUN npm i
    
    COPY ./ ./
    
    EXPOSE 8000
    
    USER node
    
    CMD [ "npm", "run", "start" ]
    ```
    

# 외부 서비스

## Github

- 웹훅 설정
    
    ![image](https://user-images.githubusercontent.com/82570138/203447416-69db9004-5f2c-4adc-bc96-5970dd242220.png)
    
- oauth앱 접근 설정
    
    ![image](https://user-images.githubusercontent.com/82570138/203447438-7873d85c-aa17-4794-9066-1da53f69787f.png)
