---
description: "Run Selenium tests on Jenkins: a ready Jenkinsfile pipeline that runs the suite, publishes JUnit results, and archives the TestFly HTML report."
id: jenkins
title: Jenkins
sidebar_position: 2
---

# Jenkins

Run TestFly tests on Jenkins using a `Jenkinsfile`. The pipeline below checks out the code, runs the tests, publishes JUnit results, and archives the HTML report.

---

## Declarative pipeline

```groovy title="Jenkinsfile"
pipeline {
    agent any

    tools {
        jdk 'JDK21'
        maven 'Maven3'
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
            }
        }

        stage('Test') {
            steps {
                sh 'mvn clean test -B'
            }
            post {
                always {
                    junit '**/surefire-reports/TEST-*.xml'
                    archiveArtifacts artifacts: 'target/testfly-report.html',
                                     allowEmptyArchive: true
                }
            }
        }
    }

    post {
        always {
            publishHTML(target: [
                allowMissing         : false,
                alwaysLinkToLastBuild: true,
                keepAll              : true,
                reportDir            : 'target',
                reportFiles          : 'testfly-report.html',
                reportName           : 'TestFly Report'
            ])
        }
    }
}
```

> The `publishHTML` step requires the **HTML Publisher Plugin** installed in Jenkins.

---

## Headless Chrome on Jenkins agents

Add Chrome to the agent and configure headless mode:

```yaml title="testfly.yml"
browser:
  name: chrome
  headless: true
```

Install Chrome in a standard location discoverable by Selenium Manager. `browser.binaryPath` is not a supported TestFly configuration field.

---

## Parallel stages

Run multiple browser or test-group configurations in parallel:

```groovy
stage('Test') {
    parallel {
        stage('Chrome') {
            steps {
                withEnv(["TESTFLY_BROWSER=chrome"]) {
                    sh 'mvn test -B'
                }
            }
        }
        stage('Firefox') {
            steps {
                withEnv(["TESTFLY_BROWSER=firefox"]) {
                    sh 'mvn test -B'
                }
            }
        }
    }
}
```

---

## Environment variables

Pass configuration values without modifying `testfly.yml`:

```groovy
environment {
    BASE_URL = 'https://staging.example.com'
}

stage('Test') {
    steps {
        sh 'mvn test -B'
    }
}
```

---

## Triggering on SCM changes

```groovy
triggers {
    pollSCM('H/5 * * * *')   // poll every 5 minutes
}
```

Or use a GitHub webhook to trigger the pipeline on push.


### Connect environment values to YAML

The parallel browser stages and `BASE_URL` example require these placeholders in the selected complete configuration. No automatic `-Dbrowser.name` or `-DbaseUrl` override is applied. Install both browsers on their agents and use separate workspaces for parallel Maven runs to avoid competing writes to `target`.

```yaml title="testfly.yml"
execution:
  mode: local
  baseUrl: ${BASE_URL:-https://example.com}
browser:
  name: ${TESTFLY_BROWSER:-chrome}
  headless: true
```
