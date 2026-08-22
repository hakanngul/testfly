---
description: "Selenium testlerini Jenkins'te çalıştırın: takımı çalıştıran, JUnit sonuçlarını yayınlayan ve TestFly HTML raporunu arşivleyen hazır bir Jenkinsfile pipeline'ı."
id: jenkins
title: Jenkins
sidebar_position: 2
---

# Jenkins

Bir `Jenkinsfile` kullanarak TestFly testlerini Jenkins'te çalıştırın. Aşağıdaki pipeline kodu çeker (checkout), testleri çalıştırır, JUnit sonuçlarını yayınlar ve HTML raporunu arşivler.

---

## Declarative pipeline

```groovy title="Jenkinsfile"
pipeline {
    agent any

    tools {
        jdk 'JDK17'
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

> `publishHTML` adımı, Jenkins'te **HTML Publisher Plugin** eklentisinin kurulu olmasını gerektirir.

---

## Jenkins aracılarında Headless Chrome

Aracıya Chrome ekleyin ve headless modu yapılandırın:

```yaml title="testfly.yml"
browser:
  type: chrome
  headless: true
```

Aracınızdaki `PATH` içinde Chrome yoksa, ikili dosya yolunu ayarlayın:

```yaml
browser:
  type: chrome
  headless: true
  binaryPath: /usr/bin/google-chrome
```

---

## Paralel aşamalar

Birden çok tarayıcı veya test grubu yapılandırmasını paralel çalıştırın:

```groovy
stage('Test') {
    parallel {
        stage('Chrome') {
            steps {
                sh 'mvn test -B -Dbrowser.type=chrome'
            }
        }
        stage('Firefox') {
            steps {
                sh 'mvn test -B -Dbrowser.type=firefox'
            }
        }
    }
}
```

---

## Ortam değişkenleri

`testfly.yml` dosyasını değiştirmeden yapılandırma değerlerini iletin:

```groovy
environment {
    BASE_URL = 'https://staging.example.com'
}

stage('Test') {
    steps {
        sh "mvn test -B -DbaseUrl=${env.BASE_URL}"
    }
}
```

---

## SCM değişikliklerinde tetikleme

```groovy
triggers {
    pollSCM('H/5 * * * *')   // her 5 dakikada bir yokla
}
```

Ya da push'ta pipeline'ı tetiklemek için bir GitHub webhook'u kullanın.