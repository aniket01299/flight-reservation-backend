pipeline {
    agent any

    environment {
        DOCKER_REPO  = "flight-backend"
        DOCKER_USER  = "aniiket2025"
        CLUSTER_NAME = "vanraj-cluster"
        REGION       = "ap-south-1"
    }

    stages {

        stage('Code-checkout') {
            steps {
                git branch: 'main',
                    url: 'https://github.com/aniket01299/flight-reservation-backend.git'
            }
        }

        stage('Code-build') {
            steps {
                sh 'mvn clean package -DskipTests'
            }
        }

        stage('Docker-build') {
            steps {
                sh 'docker build -t ${DOCKER_REPO}:${BUILD_NUMBER} .'
            }
        }

        stage('Docker-login') {
            steps {
                withCredentials([
                    usernamePassword(
                        credentialsId: 'dockerhub_creds',
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

        stage('Docker-push') {
            steps {
                sh '''
                docker tag ${DOCKER_REPO}:${BUILD_NUMBER} ${DOCKER_USER}/${DOCKER_REPO}:${BUILD_NUMBER}

                docker push ${DOCKER_USER}/${DOCKER_REPO}:${BUILD_NUMBER}

                docker rmi -f ${DOCKER_USER}/${DOCKER_REPO}:${BUILD_NUMBER}
                '''
            }
        }

        stage('Image-Name-change') {
            steps {

                sh '''
                sed -i "s|aniiket2025/flight-backend:latest|${DOCKER_USER}/${DOCKER_REPO}:${BUILD_NUMBER}|g" k8s/deployment.yaml
                '''

                sh 'cat k8s/deployment.yaml'
            }
        }

        stage('EKS-deploy') {
            steps {

                withCredentials([
                    aws(
                        credentialsId: 'aws_creds',
                        accessKeyVariable: 'AWS_ACCESS_KEY_ID',
                        secretKeyVariable: 'AWS_SECRET_ACCESS_KEY'
                    )
                ]) {

                    sh '''
                    aws eks update-kubeconfig --name ${CLUSTER_NAME} --region ${REGION}

                    kubectl get nodes

                    kubectl apply -f k8s/ns.yaml
                    kubectl apply -f k8s/deployment.yaml
                    kubectl apply -f k8s/service.yaml
                    kubectl apply -f k8s/ingress.yaml

                    kubectl get pods -n flight-reservation
                    kubectl get deployment -n flight-reservation
                    kubectl get svc -n flight-reservation
                    kubectl get ingress -n flight-reservation
                    '''
                }
            }
        }

    }

    post {

        success {
            echo "Pipeline executed successfully."
        }

        failure {
            echo "Pipeline execution failed."
        }

        always {
            cleanWs()
        }
    }
}
