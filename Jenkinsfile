pipeline {
    agent { label 'docker-agent' }
    options {
        skipDefaultCheckout(true)
        disableConcurrentBuilds()
        timestamps()
        timeout(time: 25, unit: 'MINUTES')
        buildDiscarder(logRotator(numToKeepStr: '10'))
    }
    parameters {
        booleanParam(name: 'PUBLISH', defaultValue: false, description: 'Push to Docker Hub using dockerhub credentials')
        booleanParam(name: 'DEPLOY', defaultValue: false, description: 'Deploy to an existing approved Kubernetes cluster; requires PUBLISH')
        string(name: 'IMAGE_REPOSITORY', defaultValue: '', description: 'Docker Hub namespace/repository, no tag')
        booleanParam(name: 'SONAR_SCAN', defaultValue: false, description: 'Run the configured SonarQube quality gate')
    }
    stages {
        stage('Checkout') {
            steps {
                deleteDir()
                checkout scm
                script {
                    env.IMAGE_TAG = sh(script: 'git rev-parse HEAD', returnStdout: true).trim()
                    env.LOCAL_IMAGE = "pipeline-lab:${env.IMAGE_TAG}"
                    env.SMOKE_NAME = "lab-${env.BUILD_NUMBER}"
                    if (params.DEPLOY && !params.PUBLISH) { error('DEPLOY requires PUBLISH') }
                    if (params.PUBLISH && !(params.IMAGE_REPOSITORY ==~ /[a-z0-9][a-z0-9_-]*\/[a-z0-9][a-z0-9_.-]*/)) {
                        error('Set IMAGE_REPOSITORY to a Docker Hub namespace/repository')
                    }
                }
            }
        }
        stage('Maven build and HTTP checks') {
            steps {
                sh '''
                    set -eu
                    java -version
                    mvn -version
                    docker version
                    mvn -B clean package
                '''
                archiveArtifacts artifacts: 'target/*.jar', fingerprint: true
            }
        }
        stage('SonarQube quality gate') {
            when { expression { params.SONAR_SCAN } }
            steps {
                script {
                    def scannerHome = tool 'SonarScanner'
                    sh 'mkdir -p build/classes && javac -d build/classes app/App.java'
                    withSonarQubeEnv('lab-sonarqube') {
                        sh "${scannerHome}/bin/sonar-scanner"
                    }
                }
                timeout(time: 5, unit: 'MINUTES') {
                    waitForQualityGate abortPipeline: true
                }
            }
        }
        stage('Build container') {
            steps { sh 'docker build --pull -t "$LOCAL_IMAGE" .' }
        }
        stage('Container smoke test') {
            steps {
                sh '''
                    set -eu
                    docker run -d --name "$SMOKE_NAME" -p 127.0.0.1::8080 "$LOCAL_IMAGE"
                    trap 'docker rm -f "$SMOKE_NAME"' EXIT HUP INT TERM
                    port=$(docker port "$SMOKE_NAME" 8080/tcp | sed 's/.*://')
                    n=0
                    until curl -fsS "http://127.0.0.1:$port/healthz"; do
                        n=$((n + 1)); [ "$n" -lt 30 ] || exit 1
                        sleep 1
                    done
                    curl -fsS "http://127.0.0.1:$port/" | grep -F 'Hello from DevOpsVibez'
                '''
            }
        }
        stage('Publish image') {
            when { expression { params.PUBLISH } }
            steps {
                withCredentials([usernamePassword(credentialsId: 'dockerhub', usernameVariable: 'REGISTRY_USER', passwordVariable: 'REGISTRY_TOKEN')]) {
                    sh '''
                        set -eu
                        set +x
                        export DOCKER_CONFIG=$(mktemp -d)
                        trap 'rm -rf "$DOCKER_CONFIG"' EXIT HUP INT TERM
                        printf '%s' "$REGISTRY_TOKEN" | docker login --username "$REGISTRY_USER" --password-stdin
                        docker tag "$LOCAL_IMAGE" "$IMAGE_REPOSITORY:$IMAGE_TAG"
                        docker push "$IMAGE_REPOSITORY:$IMAGE_TAG"
                    '''
                }
            }
        }
        stage('Approve and deploy') {
            when { expression { params.DEPLOY } }
            steps {
                input message: 'Deploy this image to the cluster in lab-kubeconfig? Check the context and cost first.'
                withCredentials([file(credentialsId: 'lab-kubeconfig', variable: 'KUBECONFIG')]) {
                    sh 'sh deploy.sh "$IMAGE_REPOSITORY:$IMAGE_TAG"'
                }
            }
        }
    }
    post {
        always { deleteDir() }
    }
}
