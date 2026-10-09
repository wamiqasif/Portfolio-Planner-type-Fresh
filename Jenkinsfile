pipeline {
    agent any

    options {
        timestamps()
        timeout(time: 30, unit: 'MINUTES')
        // Keeps a bounded history instead of accumulating every build's
        // artifacts/reports forever on the Jenkins controller's disk.
        buildDiscarder(logRotator(numToKeepStr: '30'))
        disableConcurrentBuilds()
    }

    parameters {
        choice(name: 'ENVIRONMENT', choices: ['qa', 'dev', 'stage', 'prod'], description: 'Target environment (maps to a Maven profile)')
        choice(name: 'SUITE', choices: [
            'src/test/resources/suites/smoke.xml',
            'src/test/resources/suites/sanity.xml',
            'src/test/resources/suites/regression.xml',
            'src/test/resources/suites/full.xml'
        ], description: 'Which suite tier to run')
    }

    triggers {
        // Nightly regression, mirroring the GitHub Actions schedule - only
        // meaningful if this job is also configured to build on this branch
        // without a parameter override; a parameterized run always wins.
        cron(env.BRANCH_NAME == 'main' ? '0 2 * * *' : '')
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
            }
        }

        stage('Build') {
            steps {
                // ./mvnw, not a Jenkins-managed Maven tool install: pins the
                // exact Maven version via .mvn/wrapper/maven-wrapper.properties,
                // so this behaves identically on every agent regardless of
                // what Maven (if any) is preinstalled there.
                sh './mvnw -B -q clean compile test-compile'
            }
        }

        stage('Test') {
            steps {
                sh "./mvnw -B test -P${params.ENVIRONMENT} -DsuiteXmlFile=${params.SUITE}"
            }
        }
    }

    post {
        always {
            // JUnit trend graph + per-build pass/fail breakdown from the
            // same Surefire XML Maven already produces - no extra plugin
            // config needed beyond pointing at it.
            junit testResults: 'target/surefire-reports/*.xml', allowEmptyResults: true

            // publishHTML wants a stable filename; the Extent report's name
            // is timestamped (one HTML file per run, never overwritten), so
            // copy it to a fixed name for the plugin to serve.
            sh 'cp reports/ExtentReport_*.html reports/latest-report.html || true'
            publishHTML(target: [
                reportDir: 'reports',
                reportFiles: 'latest-report.html',
                reportName: 'Extent Report',
                keepAll: true,
                alwaysLinkToLastBuild: true,
                allowMissing: true
            ])

            archiveArtifacts artifacts: 'reports/**, test-output/logs/**', allowEmptyArchive: true, fingerprint: false
        }
    }
}
