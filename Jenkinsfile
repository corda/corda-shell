@Library('corda-shared-build-pipeline-steps')
import static com.r3.build.BuildControl.killAllExistingBuildsForJob
killAllExistingBuildsForJob(env.JOB_NAME, env.BUILD_NUMBER.toInteger())

boolean isReleaseBranch = (env.BRANCH_NAME =~ /^release\/.*/)
boolean isRelease = (env.TAG_NAME =~ /^release-.*/)

boolean isOSReleaseBranch = (env.BRANCH_NAME =~ /^release\/os\/.*/)
boolean isEntReleaseBranch = (env.BRANCH_NAME =~ /^release\/ent\/.*/)

boolean isOSReleaseTag = (env.BRANCH_NAME =~ /^release-OS-.*/)
boolean isENTReleaseTag = (env.TAG_NAME =~ /^release-ENT-.*/)

def buildEdition = "Corda Enterprise Edition"

String artifactoryBuildName = "Corda-Shell"

// Artifactory build info links
if(!isRelease && isOSReleaseBranch){
    artifactoryBuildName = "${artifactoryBuildName}-OS :: Jenkins :: snapshot :: ${env.BRANCH_NAME}"
}else if (isRelease && isOSReleaseTag){
    artifactoryBuildName = "${artifactoryBuildName}-OS :: Jenkins :: ${env.BRANCH_NAME}"
}else if(!isRelease && isEntReleaseBranch){
    artifactoryBuildName = "${artifactoryBuildName}-Ent :: Jenkins :: snapshot :: ${env.BRANCH_NAME}"
}else if(isRelease && isENTReleaseTag){
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
        booleanParam defaultValue: (isReleaseBranch || isRelease), description: 'Publish artifacts to Artifactory?', name: 'DO_PUBLISH'
    }

    triggers {
        cron (isReleaseBranch ? '@midnight' : '')
    }

    environment {
        ARTIFACTORY_BUILD_NAME = "${artifactoryBuildName}"
        ARTIFACTORY_CREDENTIALS = credentials('artifactory-credentials')
        CORDA_ARTIFACTORY_USERNAME = "${env.ARTIFACTORY_CREDENTIALS_USR}"
        CORDA_ARTIFACTORY_PASSWORD = "${env.ARTIFACTORY_CREDENTIALS_PSW}"
        CORDA_BUILD_EDITION = "${buildEdition}"
        CORDA_USE_CACHE = "corda-remotes"
        SNYK_TOKEN = "c4-os-snyk-shell"
        ARTIFACTORY_CREDENTIALS = credentials('artifactory-credentials')
    }

    stages { 

        stage('Snyk Security') {
            when {
                expression { isRelease || isReleaseBranch }
            }
            steps {
                script {
                        sh 'rm -rf $MAVEN_LOCAL_PUBLISH'
                        sh 'mkdir -p $MAVEN_LOCAL_PUBLISH'
                        sh './gradlew publishToMavenLocal -Dmaven.repo.local="${MAVEN_LOCAL_PUBLISH}" -x :shell:javadoc' 
                        sh 'ls -lR "${MAVEN_LOCAL_PUBLISH}"'
                    }
                }
            }
        }

        stage('Build') {
            steps {
                script{
                    sh "./gradlew clean assemble -Si -x :shell:javadoc"
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
                        def props = readProperties file: 'gradle.properties'
                        def groupId = props['cordaReleaseGroup']   
                        boolean isOpenSource = groupId.equals("net.corda") ? true : false
                        def snapshotRepo
                        def releasesRepo

                        if(isOpenSource){
                             releasesRepo = "corda-releases"
                             snapshotRepo = "corda-dev"
                        }else {
                             releasesRepo = "r3-corda-releases"
                             snapshotRepo = "r3-corda-dev"
                        }
                        rtServer (
                                id: 'R3-Artifactory',
                                url: 'https://software.r3.com/artifactory',
                                credentialsId: 'artifactory-credentials'
                        )
                        rtGradleDeployer (
                                id: 'deployer',
                                serverId: 'R3-Artifactory',
                                repo: isRelease ? releasesRepo : snapshotRepo
                        )

                        withCredentials([
                                usernamePassword(credentialsId: 'artifactory-credentials',
                                                usernameVariable: 'CORDA_ARTIFACTORY_USERNAME',
                                                passwordVariable: 'CORDA_ARTIFACTORY_PASSWORD')]) {
                            rtGradleRun (
                                    usesPlugin: true,
                                    useWrapper: true,
                                    switches: "--no-daemon -Si -x :shell:javadoc",
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
