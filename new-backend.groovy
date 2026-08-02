pipeline {
    agent any

    environment {
        DOCKER_REPO  = "flight-backend"
        DOCKER_USER  = "aniiket2025"
        CLUSTER_NAME = "vanraj-cluster"
        REGION       = "ap-south-1"
    }

    stages {

        stage('Code Checkout') {
            steps {
                git branch: 'main',
                    credentialsId: 'github-credentials',
                    url: 'https://github.com/aniket01299/flight-reservation-backend.git'
            }
        }

        stage('Build') {
            steps {
                sh 'mvn clean package -DskipTests'
            }
        }

        stage('Docker Build') {
            steps {
                sh '''
                    docker build -t ${DOCKER_REPO}:${BUILD_NUMBER} .
                '''
            }
        }

        stage('Docker Login') {
            steps {
                withCredentials([
                    usernamePassword(
                        credentialsId: 'docker-hub-credentials',
                        usernameVariable: 'DOCKER_USERNAME',
                        passwordVariable: 'DOCKER_PASSWORD'
                    )
                ]) {
                    sh '''
                        echo $DOCKER_PASSWORD | docker login -u $DOCKER_USERNAME --password-stdin
                    '''
                }
            }
        }

        stage('Docker Push') {
            steps {
                sh '''
                    docker tag ${DOCKER_REPO}:${BUILD_NUMBER} ${DOCKER_USER}/${DOCKER_REPO}:${BUILD_NUMBER}
                    docker tag ${DOCKER_REPO}:${BUILD_NUMBER} ${DOCKER_USER}/${DOCKER_REPO}:latest

                    docker push ${DOCKER_USER}/${DOCKER_REPO}:${BUILD_NUMBER}
                    docker push ${DOCKER_USER}/${DOCKER_REPO}:latest

                    docker rmi -f ${DOCKER_USER}/${DOCKER_REPO}:${BUILD_NUMBER}
                    docker rmi -f ${DOCKER_USER}/${DOCKER_REPO}:latest
                '''
            }
        }

        stage('Deploy To EKS') {
            steps {

                withCredentials([
                    aws(
                        credentialsId: 'AWS-Cred',
                        accessKeyVariable: 'AWS_ACCESS_KEY_ID',
                        secretKeyVariable: 'AWS_SECRET_ACCESS_KEY'
                    )
                ]) {

                    sh '''
                        aws eks update-kubeconfig --region ${REGION} --name ${CLUSTER_NAME}

                        kubectl apply -f k8s/ns.yaml
                        kubectl apply -f k8s/deployment.yaml
                        kubectl apply -f k8s/service.yaml

                        kubectl rollout restart deployment/flight-reservation-app -n flight-reservation
                        kubectl rollout status deployment/flight-reservation-app -n flight-reservation

                        kubectl get pods -n flight-reservation
                        kubectl get svc -n flight-reservation
                    '''
                }
            }
        }
    }

    post {

        success {
            echo "Backend deployed successfully."

            build job: 'flight-reservation-frontend',
                  wait: false

            echo "Frontend pipeline triggered successfully."
        }

        failure {
            echo "Backend pipeline failed."
        }

        always {
            cleanWs()
        }
    }
}
