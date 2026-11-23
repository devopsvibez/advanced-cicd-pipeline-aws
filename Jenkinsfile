pipeline {
    agent { label 'docker-agent' }
    options {
        skipDefaultCheckout(true)
        disableConcurrentBuilds()
        timestamps()
        timeout(time: 30, unit: 'MINUTES')
        buildDiscarder(logRotator(numToKeepStr: '10'))
    }
    parameters {
        booleanParam(name: 'PUBLISH', defaultValue: true, description: 'Publish trusted branch image to Docker Hub')
        booleanParam(name: 'DEPLOY', defaultValue: true, description: 'Deploy trusted branch image to the approved project-eks lab')
    }
    environment {
        IMAGE_REPOSITORY = 'devopsvibezlab/pipeline-lab'
        AWS_DEFAULT_REGION = 'us-east-1'
        EKS_CLUSTER = 'project-eks'
    }
    stages {
        stage('Checkout') {
            steps {
                deleteDir()
                checkout scm
                script {
                    env.IMAGE_TAG = sh(script: 'git rev-parse HEAD', returnStdout: true).trim()
                    env.LOCAL_IMAGE = "pipeline-lab:${env.IMAGE_TAG}"
                    env.SMOKE_NAME = "lab-${env.BUILD_NUMBER}-${env.IMAGE_TAG.take(8)}"
                    if (!env.CHANGE_ID && params.DEPLOY && !params.PUBLISH) { error('DEPLOY requires PUBLISH') }
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
                    mvn -B clean verify
                '''
                archiveArtifacts artifacts: 'target/*.jar,target/site/jacoco/jacoco.xml', fingerprint: true
            }
        }
        stage('SonarCloud quality gate') {
            when { anyOf { changeRequest target: 'main'; branch 'main' } }
            steps {
                withSonarQubeEnv('SonarCloud') {
                    withCredentials([string(credentialsId: 'sonarcloud-token', variable: 'SONAR_TOKEN')]) {
                        sh '''
                            set -eu
                            set +x
                            if [ -n "${CHANGE_ID:-}" ]; then
                              mvn -B org.sonarsource.scanner.maven:sonar-maven-plugin:5.9.0.7382:sonar \
                                -Dsonar.host.url=https://sonarcloud.io \
                                -Dsonar.organization=devopsvibez \
                                -Dsonar.projectKey=devopsvibez_advanced-cicd-pipeline-aws \
                                -Dsonar.sources=app -Dsonar.tests=app \
                                -Dsonar.inclusions=app/App.java -Dsonar.exclusions=app/AppTest.java \
                                -Dsonar.test.inclusions=app/AppTest.java \
                                -Dsonar.java.binaries=target/classes \
                                -Dsonar.java.test.binaries=target/test-classes \
                                -Dsonar.coverage.jacoco.xmlReportPaths=target/site/jacoco/jacoco.xml \
                                -Dsonar.pullrequest.key="$CHANGE_ID" \
                                -Dsonar.pullrequest.branch="$CHANGE_BRANCH" \
                                -Dsonar.pullrequest.base="$CHANGE_TARGET" \
                                -Dsonar.qualitygate.wait=true -Dsonar.qualitygate.timeout=300
                            else
                              mvn -B org.sonarsource.scanner.maven:sonar-maven-plugin:5.9.0.7382:sonar \
                                -Dsonar.host.url=https://sonarcloud.io \
                                -Dsonar.organization=devopsvibez \
                                -Dsonar.projectKey=devopsvibez_advanced-cicd-pipeline-aws \
                                -Dsonar.sources=app -Dsonar.tests=app \
                                -Dsonar.inclusions=app/App.java -Dsonar.exclusions=app/AppTest.java \
                                -Dsonar.test.inclusions=app/AppTest.java \
                                -Dsonar.java.binaries=target/classes \
                                -Dsonar.java.test.binaries=target/test-classes \
                                -Dsonar.coverage.jacoco.xmlReportPaths=target/site/jacoco/jacoco.xml \
                                -Dsonar.qualitygate.wait=true -Dsonar.qualitygate.timeout=300
                            fi
                        '''
                    }
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
                    curl -fsS "http://127.0.0.1:$port/" | grep -F 'Chapter &amp; Co.'
                '''
            }
        }
        stage('Publish image') {
            when {
                allOf {
                    not { changeRequest() }
                    anyOf { branch 'maven-pipeline'; branch 'main' }
                    expression { params.PUBLISH }
                }
            }
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
        stage('Deploy to EKS') {
            when {
                allOf {
                    not { changeRequest() }
                    anyOf { branch 'maven-pipeline'; branch 'main' }
                    expression { params.DEPLOY }
                }
            }
            steps { sh 'sh deploy.sh "$IMAGE_REPOSITORY:$IMAGE_TAG"' }
        }
    }
    post {
        always { deleteDir() }
    }
}
