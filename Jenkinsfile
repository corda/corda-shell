@Library('corda-shared-build-pipeline-steps')
import static com.r3.build.BuildControl.killAllExistingBuildsForJob

killAllExistingBuildsForJob(env.JOB_NAME, env.BUILD_NUMBER.toInteger())

def extraGradleCommands = '-x :shell:javadoc'

boolean isReleaseBranch = (env.BRANCH_NAME =~ /^release\/.*/)
boolean isReleaseTag = (env.TAG_NAME =~ /^release-.*/)
boolean isRelease = isReleaseBranch || isReleaseTag

boolean isOSReleaseBranch = (env.BRANCH_NAME =~ /^release\/os\/.*/)
boolean isEntReleaseBranch = (env.BRANCH_NAME =~ /^release\/ent\/.*/)

boolean isOSReleaseTag = (env.BRANCH_NAME =~ /^release-OS-.*/)
boolean isENTReleaseTag = (env.TAG_NAME =~ /^release-ENT-.*/)

def buildEdition = "Corda Enterprise Edition"

String publishOptions = isRelease ? "${extraGradleCommands}" : "${extraGradleCommands} -PversionFromGit"
String artifactoryBuildName = "Corda-Shell"

// Artifactory build info links
if(!isReleaseTag && isOSReleaseBranch){
    artifactoryBuildName = "${artifactoryBuildName}-OS :: Jenkins :: snapshot :: ${env.BRANCH_NAME}"
}else if (isReleaseTag && isOSReleaseTag){
    artifactoryBuildName = "${artifactoryBuildName}-OS :: Jenkins :: ${env.BRANCH_NAME}"
}else if(!isReleaseTag && isEntReleaseBranch){
    artifactoryBuildName = "${artifactoryBuildName}-Ent :: Jenkins :: snapshot :: ${env.BRANCH_NAME}"
}else if(isReleaseTag && isENTReleaseTag){
    artifactoryBuildName = "${artifactoryBuildName}-Ent :: Jenkins :: ${env.BRANCH_NAME}"
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
        booleanParam defaultValue: (isReleaseBranch || isReleaseTag), description: 'Publish artifacts to Artifactory?', name: 'DO_PUBLISH'
    }

    triggers {
        cron (isReleaseBranch ? '@midnight' : '')
    }

    environment {
        ARTIFACTORY_BUILD_NAME = "${artifactoryBuildName}"
        MAVEN_LOCAL_PUBLISH = "${env.WORKSPACE}/${mavenLocal}"
        CORDA_BUILD_EDITION = "${buildEdition}"
        ARTIFACTORY_CREDENTIALS = credentials('artifactory-credentials')
        CORDA_ARTIFACTORY_USERNAME = "${env.ARTIFACTORY_CREDENTIALS_USR}"
        CORDA_ARTIFACTORY_PASSWORD = "${env.ARTIFACTORY_CREDENTIALS_PSW}"
        CORDA_USE_CACHE = "corda-remotes"
        SNYK_TOKEN = "c4-ent-snyk-shell"
        JAVA_HOME="/usr/lib/jvm/java-17-amazon-corretto"
    }

    stages {

        stage('Snyk Security') {
            when {
                expression { isReleaseTag || isReleaseBranch }
            }
            steps {
                script {
                    // Invoke Snyk for each Gradle sub project we wish to scan
                    def modulesToScan = ['standalone-shell', 'shell']
                    modulesToScan.each { module ->
                        snykSecurityScan(env.SNYK_TOKEN, "--sub-project=$module --configuration-matching='^runtimeClasspath\$' --prune-repeated-subdependencies --debug --target-reference='${env.BRANCH_NAME}' --project-tags=Branch='${env.BRANCH_NAME.replaceAll("[^0-9|a-z|A-Z]+","_")}'")
                    }
                }
            }
        }

        stage('Build') {
            steps {
                script{
                    sh "./gradlew clean assemble -Si ${extraGradleCommands}"
                }
            }
        }

        stage('Test') {
            steps {
                script{
                    sh "./gradlew test integrationTest -Si"
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
                rtServer(
                        id: 'R3-Artifactory',
                        url: 'https://software.r3.com/artifactory',
                        credentialsId: 'artifactory-credentials'
                )
                rtGradleDeployer(
                        id: 'deployer',
                        serverId: 'R3-Artifactory',
                        repo: isRelease ? 'r3-corda-releases' : 'r3-corda-dev'
                )
                rtGradleRun(
                        usesPlugin: true,
                        useWrapper: true,
                        switches: publishOptions,
                        tasks: 'artifactoryPublish',
                        deployerId: 'deployer',
                        buildName: env.ARTIFACTORY_BUILD_NAME
                )
                rtPublishBuildInfo(
                        serverId: 'R3-Artifactory',
                        buildName: env.ARTIFACTORY_BUILD_NAME
                )
            }
        }
    }

}
