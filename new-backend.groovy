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

                docker push ${DOCKER_USER}/${DOCKER_REPO}:${BUILD_NUMBER}

                docker rmi -f ${DOCKER_USER}/${DOCKER_REPO}:${BUILD_NUMBER}
                '''
            }
        }

        stage('Update Image') {
            steps {
                sh '''
                sed -i "s|aniiket2025/flight-backend:latest|${DOCKER_USER}/${DOCKER_REPO}:${BUILD_NUMBER}|g" k8s/deployment.yaml

                cat k8s/deployment.yaml
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

                    kubectl get nodes

                    kubectl apply -f k8s/deployment.yaml
                    kubectl apply -f k8s/service.yaml

                    kubectl rollout status deployment/flight-reservation-app

                    kubectl get deployments
                    kubectl get pods
                    kubectl get svc
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
            echo "Pipeline failed."
        }

        always {
            cleanWs()
        }
    }
}
