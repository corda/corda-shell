@Library('corda-shared-build-pipeline-steps')
import static com.r3.build.BuildControl.killAllExistingBuildsForJob
import groovy.transform.Field

killAllExistingBuildsForJob(env.JOB_NAME, env.BUILD_NUMBER.toInteger())

@Field
String mavenLocal = 'tmp/mavenlocal'

def nexusDefaultIqStage = "build"


/**
 * make sure calculated default value of NexusIQ stage is first in the list
 * thus making it default for the `choice` parameter
 */
def nexusIqStageChoices = [nexusDefaultIqStage].plus(
                [
                        'develop',
                        'build',
                        'stage-release',
                        'release',
                        'operate'
                ].minus([nexusDefaultIqStage]))
                
boolean isReleaseBranch = (env.BRANCH_NAME =~ /^release\/.*/)
boolean isRelease = (env.TAG_NAME =~ /^release-.*/) 

boolean isOSReleaseBranch = (env.BRANCH_NAME =~ /^release\/os\/.*/)
boolean isEntReleaseBranch = (env.BRANCH_NAME =~ /^release\/ent\/.*/)

boolean isOSReleaseTag = (env.BRANCH_NAME =~ /^release-OS-\/.*/)
boolean isENTReleaseTag = (env.TAG_NAME =~ /^release-ENT-.*/) 

String artifactoryBuildName = "Corda-Shell"

// Artifactory build info links
if(!isRelease && isOSReleaseBranch){
   artifactoryBuildName = "${artifactoryBuildName}-OS/Jenkins/snapshot/:"${env.BRANCH_NAME}
}else if (isRelease && isOSReleaseTag){
    artifactoryBuildName = "${artifactoryBuildName}-OS/Jenkins/:"${env.BRANCH_NAME}
}else if(!isRelease && isEntReleaseBranch){
    artifactoryBuildName = "${artifactoryBuildName}-Ent/Jenkins/snapshot:"${env.BRANCH_NAME}
}else if(isRelease && isENTReleaseTag){
    artifactoryBuildName = "${artifactoryBuildName}-Ent/Jenkins/:"${env.BRANCH_NAME}    
}


pipeline {
    agent { label 'standard' }

    options {
        timestamps()
        ansiColor('xterm')
        overrideIndexTriggers(false)
        timeout(time: 1, unit: 'HOURS')
        buildDiscarder(logRotator(daysToKeepStr: '14', artifactDaysToKeepStr: '14'))
    }

    parameters {
        choice choices: nexusIqStageChoices, description: 'NexusIQ stage for code evaluation', name: 'nexusIqStage'
        booleanParam defaultValue: (isReleaseBranch || isRelease), description: 'Publish artifacts to Artifactory?', name: 'DO_PUBLISH'
    }

    triggers {
        cron '@midnight'
    }

    environment {
        ARTIFACTORY_BUILD_NAME = "${artifactoryBuildName}"
        MAVEN_LOCAL_PUBLISH = "${env.WORKSPACE}/$mavenLocal"

    }

    stages {
        stage('Local Publish') {
            steps {
                script {
                        sh 'rm -rf $MAVEN_LOCAL_PUBLISH'
                        sh 'mkdir -p $MAVEN_LOCAL_PUBLISH'
                        sh './gradlew publishToMavenLocal -Dmaven.repo.local="${MAVEN_LOCAL_PUBLISH}"' 
                        sh 'ls -lR "${MAVEN_LOCAL_PUBLISH}"'

                    }
                }
            }

        stage('Sonatype Check') {
            steps {
                
              script{
                    def props = readProperties file: 'gradle.properties'
                    version = props['cordaShellReleaseVersion']
                    groupId = props['cordaReleaseGroup']
                    def artifactId = 'corda-shell'
                    nexusAppId = "${groupId}-${artifactId}-${version}"
                    echo "${groupId}-${artifactId}-${version}"                  
              }
                        
                dir(mavenLocal) {                        
                        script {
                            fileToScan = findFiles(
                                excludes: '**/*-javadoc.jar',
                                glob: '**/*.jar, **/*.zip'
                            ).collect { f -> [scanPattern: f.path] }
                        }
                        nexusPolicyEvaluation(
                            failBuildOnNetworkError: true,
                            iqApplication: nexusAppId, // application *has* to exist before a build starts!
                            iqScanPatterns: fileToScan,
                            iqStage:  params.nexusIqStage
                        )
                }
             }
        }

        stage('Build') {
            steps {
                script{
                    sh "./gradlew clean assemble -Si"
                }
            }
        }

        stage('Test') {
            steps {
                script{
                    sh "./gradlew test -Si"
                }
            }
            post {
                always {
                        junit allowEmptyResults: true, testResults: '**/build/test-results/**/TEST-*.xml'
                        archiveArtifacts artifacts: '**/build/test-results/**/TEST-*.xml', fingerprint: true
                }
            }
        }

        stage('Publish to Artifactory') {
            when {
                expression { params.DO_PUBLISH }
            }
            steps {
                script{
                        boolean isOpenSource = groupId.equals("net.corda") ? true : false        
                        rtServer (
                                id: 'R3-Artifactory',
                                url: 'https://software.r3.com/artifactory',
                                credentialsId: 'artifactory-credentials'
                        )

                        if(!isOpenSource){
                            rtGradleDeployer (
                                    id: 'deployer',
                                    serverId: 'R3-Artifactory',
                                    repo: isRelease ? 'r3-corda-releases' : 'r3-corda-dev'
                            )
                        }else{
                            rtGradleDeployer (
                                    id: 'deployer',
                                    serverId: 'R3-Artifactory',
                                    repo: isRelease ? 'corda-releases' : 'corda-dev'
                            )
                        }

                        withCredentials([
                                usernamePassword(credentialsId: 'artifactory-credentials',
                                                usernameVariable: 'CORDA_ARTIFACTORY_USERNAME',
                                                passwordVariable: 'CORDA_ARTIFACTORY_PASSWORD')]) {
                            rtGradleRun (
                                    usesPlugin: true,
                                    useWrapper: true,
                                    switches: "--no-daemon -Si",
                                    tasks: 'artifactoryPublish',
                                    deployerId: 'deployer',
                                    buildName: env.ARTIFACTORY_BUILD_NAME
                            )
                        }
                        rtPublishBuildInfo (
                                serverId: 'R3-Artifactory',
                                buildName: env.ARTIFACTORY_BUILD_NAME
                        )
                    }
                }
        }
    }

}
