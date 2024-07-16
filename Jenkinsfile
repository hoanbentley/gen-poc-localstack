#!groovy
pipeline {
    agent {
       docker {
            image 'maven:3.9.5-eclipse-temurin-17'
            args '--network host -u root -v /var/run/docker.sock:/var/run/docker.sock'
       }
    }
    stages {

        stage('Build') {
            steps {
                sh 'mvn -B -DskipTests clean package'
            }
        }

        stage('Test') {
            steps {
                sh 'mvn test'
            }

            post {
                always {
                    junit 'target/surefire-reports/*.xml'
                }
            }
        }

        stage('Push') {
            steps {
                echo 'Pushing'
            }
        }

        stage('Deploy') {
            steps {
                echo 'Deploying'
            }
        }
    }
}
