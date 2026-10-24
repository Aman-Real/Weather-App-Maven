// Declarative Jenkins pipeline: build + test with the Maven wrapper, build images, deploy with Helm.
pipeline {
    agent any

    options {
        timestamps()
        timeout(time: 45, unit: 'MINUTES')
    }

    environment {
        REGISTRY = 'docker.io/skycast'
        TAG = "${env.BUILD_NUMBER}"
    }

    stages {
        stage('Checkout') {
            steps { checkout scm }
        }

        stage('Build & Test') {
            steps {
                script {
                    if (isUnix()) {
                        sh 'chmod +x mvnw && ./mvnw -B verify'
                    } else {
                        bat 'mvnw.cmd -B verify'
                    }
                }
            }
            post {
                always { junit allowEmptyResults: true, testResults: '**/target/surefire-reports/*.xml' }
            }
        }

        stage('Docker images') {
            when { branch 'main' }
            steps {
                script {
                    def modules = ['discovery-server', 'auth-service', 'weather-service', 'alert-service', 'api-gateway']
                    for (m in modules) {
                        sh "docker build --build-arg MODULE=${m} -t ${REGISTRY}/${m}:${TAG} ."
                    }
                }
            }
        }

        stage('Push') {
            when { branch 'main' }
            steps {
                withCredentials([usernamePassword(credentialsId: 'dockerhub', usernameVariable: 'U', passwordVariable: 'P')]) {
                    sh 'echo "$P" | docker login -u "$U" --password-stdin'
                    sh 'for m in discovery-server auth-service weather-service alert-service api-gateway; do docker push $REGISTRY/$m:$TAG; done'
                }
            }
        }

        stage('Deploy to Kubernetes') {
            when { branch 'main' }
            steps {
                withCredentials([string(credentialsId: 'skycast-jwt-secret', variable: 'JWT_SECRET')]) {
                    sh '''
                      helm upgrade --install skycast ./helm/skycast -n skycast --create-namespace \
                        --set image.registry=$REGISTRY --set image.tag=$TAG \
                        --set secrets.jwtSecret=$JWT_SECRET
                    '''
                }
            }
        }
    }
}
