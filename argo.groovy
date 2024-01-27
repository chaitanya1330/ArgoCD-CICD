pipeline {
    agent { label 'master' }
    // agent any
    environment {
        AWS_ACCOUNT_ID="350515911022"
        AWS_DEFAULT_REGION="us-east-2"
        IMAGE_REPO_NAME="new_chatapp"
        IMAGE_TAG="""${BUILD_NUMBER}"""
        REPOSITORY_URL = "350515911022.dkr.ecr.us-east-2.amazonaws.com/new_chatapp"
        // GIT_URL = 'git@github.com:chaitanya1330/new_chatapp.git'
    }
   
    stages {
	
        stage('git') {
            steps {
                // Get some code from a GitHub repository using SSH URL
                git credentialsId: 'GitHub', url: 'git@github.com:chaitanya1330/new_chatapp.git', branch: 'master'
            }
        }   

        
        stage('SonarQube Analysis') {
            
            steps {
                script {
                    def scannerHome = tool 'sonar-qube'
                    withSonarQubeEnv('sonar-server') {
                        // Run the SonarQube Scanner with project key and authentication token
                        sh "${scannerHome}/bin/sonar-scanner -Dsonar.login=sqp_6b5e8f3c7fbea2004a8d59b27baad19c284b1245 -Dsonar.projectKey=new_chatapp"
                    }
                }
            }
        }
        
        stage("Quality Gate") {
           
            steps {
              timeout(time: 1, unit: 'HOURS') {
                waitForQualityGate abortPipeline: true
              }
            }
        }


         stage('Logging into AWS ECR') {
            steps {
                script {
                sh """aws ecr get-login-password --region ${AWS_DEFAULT_REGION} | docker login --username AWS --password-stdin ${AWS_ACCOUNT_ID}.dkr.ecr.${AWS_DEFAULT_REGION}.amazonaws.com"""

                }
            }
        }
        
        
  
        // Building Docker images
        stage('Building image') {
          steps{
            script {
              dockerImage = docker.build "${IMAGE_REPO_NAME}:${IMAGE_TAG}"
            }
          }
        }
   
        // Uploading Docker images into AWS ECR
        stage('Pushing to ECR') {
         steps{  
             script {
                    sh """docker tag ${IMAGE_REPO_NAME}:${IMAGE_TAG} ${REPOSITORY_URL}:$IMAGE_TAG"""
                    sh """docker push ${AWS_ACCOUNT_ID}.dkr.ecr.${AWS_DEFAULT_REGION}.amazonaws.com/${IMAGE_REPO_NAME}:${IMAGE_TAG}"""
                }
            }
        }
        
        // pulling the image from Amazon Elastic container registry
        stage('Pull Image from ECR') {
                steps {
                    script {
                        // Pull the Docker image from ECR
                        sh """docker pull ${REPOSITORY_URL}:${IMAGE_TAG}"""
                    }
                }
        }
        

        stage('Run Docker Container') {
                steps {
                    script {
                        // Run the Docker container
                        sh """docker rm -f app_cont"""
                        sh """docker run -d --name app_cont --network=docker_network_comp -p 8000:8000 ${REPOSITORY_URL}:${IMAGE_TAG}"""
                    }
                }
            }
// ------------------------------------------------------------------------------------------------------------------------------



        // stage('Deploy to Kubernetes') {
        //     agent {
        //         label 'kubernetes'
        //     }
        //     steps {
        //         script {
        //             dir('/root/k8s/manifest_examples/appserver/') {
                    // Replace the placeholder in the deployment YAML with the actual image URL
                        // def updatedDeploymentYAML = sh(script: "cat backend.yml | sed 's|REPLACE_ME|${REPOSITORY_URL}:${IMAGE_TAG}|g'", returnStdout: true).trim()
    
                        // Write the updated YAML to a temporary file
                        // writeFile file: 'updated-deployment.yaml', text: updatedDeploymentYAML
    
                        // Use kubectl to apply the updated Kubernetes deployment YAML
        //                 sh "kubectl apply -f updated-deployment.yaml"
        //             }
        //         }
        //     }
        // }

// #-----------------------------------------------------------------------------------------------------------------------------------
        
        stage('Checkout K8S manifest SCM'){
            steps {
                git credentialsId: 'GitHub', 
                url: 'git@github.com:chaitanya1330/new_chatapp.git',
                branch: 'master'
            }
        }


        stage('Update Kubernetes Manifest and Push to Git') {
            steps {
                script {
                    withCredentials([usernamePassword(credentialsId: 'GitHub', passwordVariable: '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', usernameVariable: 'chaitanya chaitu')]) {
                        // Updated the Kubernetes manifest file with the new image tag
                        sh """
                        cat backend.yml
                        sed 's|image: REPLACE_ME|image: ${REPOSITORY_URL}:${IMAGE_TAG}|' backend.yml > argo-manifests/backend.yml.tmp
                        mv argo-manifests/backend.yml.tmp argo-manifests/backend.yml
                        cat argo-manifests/backend.yml
                        git add .
                        git commit -m 'Updated Kubernetes manifest with new image tag | Jenkins Pipeline'
        
                        # Set Git username and email
                        git config user.email 'chaitanyavantaku@gmail.com'
                        git config user.name 'chaitanya chaitu'
                        git remote -v
                        git remote set-url origin git@github.com:chaitanya1330/new_chatapp.git
                        git push -u origin HEAD:master
                        """
                    }
                }
            }
        }
    }
}
