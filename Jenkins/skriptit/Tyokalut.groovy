import hudson.scm.ChangeLogSet
import hudson.plugins.git.GitChangeSet

def hoidaMahdollisenErrorinKorjaantuminen(stageNimi, viesti = 'Pipeline korjaantui') {
    // Jos edellinen buildi hajosi ja tämä buildi korjasi sen, niin lähetetään viesti Slackiin
    if (currentBuild.previousBuild.buildVariables.FAILED_STAGE == stageNimi) {
        slackSend([color  : 'good',
                   message: viesti])
    }
}

def hoidaErrori(eViesti, stageNimi, viesti = 'Pipelinessä tapahtui poikkeus') {
    env.FAILED_STAGE = stageNimi
    def virheKoodi
    try {
        virheKoodi = (eViesti =~ /code (\d+)/)[0][1]
    } catch (e) {
        virheKoodi = null
    }
    // Ei lähetetä viestiä Slackiin, jos käyttäjä on perunut buildin.
    if (virheKoodi != "143") {
        slackSend([color  : 'warning',
                   message: viesti])
    }
    error([message: stageNimi + " epäonnistui."])
}

@NonCPS
def changeSets2String() {
    def muutokset = currentBuild.changeSets
    def teksti = ""
    for (ChangeLogSet muutos : muutokset) {
        for (GitChangeSet kentta : muutos.getItems()) {
            def tekija
            def viesti
            try {
                tekija = kentta.getAuthor()
            } catch (e) {
                tekija = "Unknown"
            }
            try {
                viesti = kentta.getMsg()
            } catch (e) {
                viesti = "Unkown"
            }
            teksti = teksti + "[" + tekija + "] " + viesti + "\n"
        }
    }
    return teksti
}

def onkoTiedostoOlemassa(absolutePath) {
    loytyikoTiedosto = sh([script      : "[ -f " + absolutePath + " ]",
                           returnStatus: true])
    return loytyikoTiedosto == 0
}

@NonCPS
def muutosTapahtuiHarjaan(kaynnistaja) {
    println "KÄYNNISTÄJÄ: " + kaynnistaja
    def muutokset = currentBuild.changeSets
    println "MUUTOKSET: " + muutokset
    println "TYHYJÄ: " + muutokset.isEmpty()
    println kaynnistaja == "SCMTrigger"
    // Onko pollaus lauennu turhan takia
    return !(muutokset.isEmpty() && kaynnistaja == "SCMTrigger")
}

def buildNumberinEnv(buildNumber, envNimi) {
    def build = currentBuild
    try {
        while (build.number != buildNumber) {
            build = build.previousBuild
        }
    } catch (e) {
        error([message: "Oikean build numberin (" + buildNumber + ") etsiminen epäonnistui."])
    }
    return build.buildVariables."$envNimi"
}

def etsiKaytettavaJar(valitetaankoTestiBranchista, valitetaankoEpaonnistuneestaAjosta) {
    int nykyinenBuildNumber
    def loytynytJarBuild = null
    def onkoTestiBranch = null
    def onkoAjoEpaonnistunut = null
    for (nykyinenBuildNumber = currentBuild.number; nykyinenBuildNumber >= 0; nykyinenBuildNumber--) {
        if (valitetaankoTestiBranchista) {
            onkoTestiBranch = buildNumberinEnv(nykyinenBuildNumber, "TESTI_BRANCH")
        } else {
            onkoTestiBranch = false
        }
        if (valitetaankoEpaonnistuneestaAjosta) {
            onkoAjoEpaonnistunut = buildNumberinEnv(nykyinenBuildNumber, "FAILED_STAGE")
        } else {
            onkoAjoEpaonnistunut = false
        }
        if (!onkoTiedostoOlemassa("${env.JENKINS_HOME}/jobs/${env.JOB_BASE_NAME}/builds/" + nykyinenBuildNumber + "/archive/target/harja-*-standalone.jar") ||
                onkoAjoEpaonnistunut ||
                onkoTestiBranch) {
            continue
        }
        loytynytJarBuild = nykyinenBuildNumber
        break
    }
    println "loytynytJarBuild: " + loytynytJarBuild
    return loytynytJarBuild
}

///////////////////////////////////////
//////// ERI STAGEJEN AJAMISET ////////
///////////////////////////////////////

def ajaTestikannanLuonti (stagenNimi) {
    try {
        sh([script: "sh Jenkins/skriptit/testitietokanta.sh"])
        hoidaMahdollisenErrorinKorjaantuminen(stagenNimi, "Pipeline ei enää hajoa Jenkinsin testikannan luomiseen")
    } catch (e) {
        hoidaErrori(e.getMessage(), stagenNimi, "Pipeline hajosi Jenkinsin testikannan luomiseen")
    }
}

def ajaTestikannanLuontiTestStg (stagenNimi) {
    try {
        sh([script: "sh Jenkins/skriptit/testitietokanta_test_stg.sh"])
        hoidaMahdollisenErrorinKorjaantuminen(stagenNimi, "Pipeline ei enää hajoa Jenkinsin testikannan luomiseen")
    } catch (e) {
        hoidaErrori(e.getMessage(), stagenNimi, "Pipeline hajosi Jenkinsin testikannan luomiseen")
    }
}

def ajaJarJaTestit (stagenNimi) {
    try {
        // Luo API docsit
        sh([script: "sh Jenkins/skriptit/luo-API-docsit.sh"])
        // Luo jarri ja aja testit
        sh([script: "lein tuotanto"])
        // Säilötään se jarri
        archiveArtifacts([artifacts: 'target/harja-*-standalone.jar, doc/*'])
        hoidaMahdollisenErrorinKorjaantuminen(stagenNimi, "Pipeline ei enää hajoa testien ajamiseen/JAR:in luomiseen")
    } catch (e) {
        String muutokset = changeSets2String()
        mail([from   : params.LAHETTAJA_SPOSTI,
              replyTo: '',
              to     : params.VASTAANOTTAJAT_SPOSTI,
              cc     : '',
              bcc    : '',
              subject: "Pipelinen ajaminen epäonnistui ${env.BUILD_NUMBER}",
              body   : "Build: ${env.BUILD_URL}\n" + "Job name: ${env.JOB_BASE_NAME}" + muutokset])
        hoidaErrori(e.getMessage(), stagenNimi, "Pipeline hajosi testien ajamiseen/JAR:in luomiseen")
    } finally {
        // Testitulokset
        junit([testResults: 'test2junit/xml/*.xml'])
    }
}

def ajaTestiserverinKanta(stagenNimi) {
    try {
        // withCredentials plugari ei toimi vielä yhteen configFileProvider:in kanssa (http://jenkins-ci.361315.n4.nabble.com/Using-Config-File-Provider-Plugin-with-placeholders-and-Token-Macro-Plugin-td4908235.html)
        // Heti, kun tuo on korjattu, niin passun ja salasanan voi antaa suoraan tuonne conffi fileen tyylinsä näin: ${ENV, var="KAYTTAJA"}. Saattaa olla myös, että tuohon
        // config file plugariin lisätään feature, jossa noita kredentiaaleja voi antaa (JENKINS-43204)
        configFileProvider([configFile(fileId: 'Flyway_testiserverin_konfiguraatio', replaceTokens: true, variable: 'FLYWAY_SETTINGS')]) {
            withCredentials([usernamePassword(credentialsId: 'TESTIPANNU', passwordVariable: 'SALASANA', usernameVariable: 'KAYTTAJA')]) {
                // Tässä käytetään Dflyway.configFile, mutta jos flyway päivitetään >5.0, niin täytyy vaihtaa tuo Dflyway.configFiles (katso dokumentaatio)
                sh([script: 'mvn -f tietokanta/pom.xml clean compile flyway:migrate -Dflyway.configFile=$FLYWAY_SETTINGS' +
                        " -Dflyway.user=$KAYTTAJA -Dflyway.password=$SALASANA"])
            }
        }
        hoidaMahdollisenErrorinKorjaantuminen(stagenNimi, "Pipeline ei enää hajoa testiserverin kannan luomiseen")
    } catch (e) {
        hoidaErrori(stagenNimi, "Pipeline hajosi testiserverin kannan luomiseen")
    }
}

def ajaTestiserverinApp(stagenNimi, buildNumber) {
    try {
        ansiblePlaybook([installation: 'ansible 2.7',
                         inventory   : 'environments/testing/inventory',
			 playbook    : 'playbooks/nightly.yml',
			 vaultCredentialsId: 'Vault',
                         extraVars   : [
                jenkins_build_number: buildNumber,
                jenkins_job_name: env.JOB_BASE_NAME
            ]])
        hoidaMahdollisenErrorinKorjaantuminen(stagenNimi, "Pipeline ei enää hajoa testiserverin appiksen luomiseen")
    } catch (e) {
        hoidaErrori(stagenNimi, "Pipeline hajosi testiserverin appiksen luomiseen")
    }
}

def ajaE2ETestit(stagenNimi) {
    try {
        wrap([$class: 'Xvfb']) {
            retry(5) {
                timeout(20) {
                    sh([script: "lein do clean, compile, test2junit"])
                }
            }
        }
        hoidaMahdollisenErrorinKorjaantuminen(stagenNimi, "Pipeline ei enää hajoa E2E testeihin")
        env.E2E_ONNISTUI = true
    } catch (e) {
        env.E2E_ONNISTUI = false
        hoidaErrori(stagenNimi, "Pipeline hajosi E2E testeihin")
    } finally {
        junit([testResults: 'test2junit/xml/*.xml'])
    }
}

def ajaStagingserverinKanta(stagenNimi) {
    try {
        // withCredentials plugari ei toimi vielä yhteen configFileProvider:in kanssa (http://jenkins-ci.361315.n4.nabble.com/Using-Config-File-Provider-Plugin-with-placeholders-and-Token-Macro-Plugin-td4908235.html)
        // Heti, kun tuo on korjattu, niin passun ja salasanan voi antaa suoraan tuonne conffi fileen tyylinsä näin: ${ENV, var="KAYTTAJA"}. Saattaa olla myös, että tuohon
        // config file plugariin lisätään feature, jossa noita kredentiaaleja voi antaa (JENKINS-43204)
        configFileProvider([configFile(fileId: 'Flyway_stagingserverin_konfiguraatio', replaceTokens: true, variable: 'FLYWAY_SETTINGS')]) {
            withCredentials([usernamePassword(credentialsId: 'STAGEPANNU', passwordVariable: 'SALASANA', usernameVariable: 'KAYTTAJA')]) {
                // Tässä käytetään Dflyway.configFile, mutta jos flyway päivitetään >5.0, niin täytyy vaihtaa tuo Dflyway.configFiles (katso dokumentaatio)
                sh([script: 'mvn -f tietokanta/pom.xml clean compile flyway:migrate -Dflyway.configFile=$FLYWAY_SETTINGS' +
                        " -Dflyway.user=$KAYTTAJA -Dflyway.password=$SALASANA"])
            }
        }
        hoidaMahdollisenErrorinKorjaantuminen(stagenNimi, "Pipeline ei enää hajoa stagingserverin kannan luomiseen")
    } catch (e) {
        hoidaErrori(e.getMessage(), stagenNimi, "Pipeline hajosi stagingserverin kannan luomiseen")
    }
}

def ajaStagingserverinApp(stagenNimi, buildNumber) {
    try {
        ansiblePlaybook([installation: 'ansible 2.7',
                         inventory   : 'environments/staging/inventory',
                         playbook    : 'playbooks/staging.yml',
                         extraVars   : [
                                 jenkins_build_number: buildNumber,
                                 jenkins_job_name: env.JOB_BASE_NAME
                         ]])
        hoidaMahdollisenErrorinKorjaantuminen(stagenNimi, "Pipeline ei enää hajoa stagingserverin appiksen luomiseen")
    } catch (e) {
        hoidaErrori(e.getMessage(), stagenNimi, "Pipeline hajosi stagingserverin appiksen luomiseen")
    }
}

def ajaTuotantoserverinKanta(stagenNimi) {
    try {
        // withCredentials plugari ei toimi vielä yhteen configFileProvider:in kanssa (http://jenkins-ci.361315.n4.nabble.com/Using-Config-File-Provider-Plugin-with-placeholders-and-Token-Macro-Plugin-td4908235.html)
        // Heti, kun tuo on korjattu, niin passun ja salasanan voi antaa suoraan tuonne conffi fileen tyylinsä näin: ${ENV, var="KAYTTAJA"}. Saattaa olla myös, että tuohon
        // config file plugariin lisätään feature, jossa noita kredentiaaleja voi antaa (JENKINS-43204)
        configFileProvider([configFile(fileId: 'Flyway_tuotantoserverin_konfiguraatio', replaceTokens: true, variable: 'FLYWAY_SETTINGS')]) {
            withCredentials([usernamePassword(credentialsId: 'TUOTANTOPANNU', passwordVariable: 'SALASANA', usernameVariable: 'KAYTTAJA')]) {
                // Tässä käytetään Dflyway.configFile, mutta jos flyway päivitetään >5.0, niin täytyy vaihtaa tuo Dflyway.configFiles (katso dokumentaatio)
                sh([script: 'mvn -f tietokanta/pom.xml clean compile flyway:migrate -Dflyway.configFile=$FLYWAY_SETTINGS' +
                        " -Dflyway.user=$KAYTTAJA -Dflyway.password=$SALASANA"])
            }
        }
        hoidaMahdollisenErrorinKorjaantuminen(stagenNimi, "Pipeline ei enää hajoa tuotantoserverin kannan luomiseen")
    } catch (e) {
        hoidaErrori(e.getMessage(), stagenNimi, "Pipeline hajosi tuotantoserverin kannan luomiseen")
    }
}

def ajaTuotantoerverinApp(stagenNimi, buildNumber) {
    try {
        slackSend([color  : 'good',
                   message: 'Aloitetaan tuotanto deployment'])
        ansiblePlaybook([installation: 'ansible 2.7',
                         inventory   : 'environments/production/inventory',
                         playbook    : 'playbooks/production.yml',
                         extraVars   : [
                                 jenkins_build_number: buildNumber,
                                 jenkins_job_name: env.JOB_BASE_NAME
                         ]])
        hoidaMahdollisenErrorinKorjaantuminen(stagenNimi, "Pipeline ei enää hajoa tuotantoserverin appiksen luomiseen")
        slackSend([color  : 'good',
                   message: 'Tuotanto deployment onnistui'])
    } catch (e) {
        hoidaErrori(e.getMessage(), stagenNimi, "Pipeline hajosi tuotantoserverin appiksen luomiseen")
    }
}

///////////////////////////////////////
///////////// Checkoutit //////////////
///////////////////////////////////////

def checkoutHarja(haara) {
    checkout([poll: true,
              scm : [$class                           : 'GitSCM',
                     branches                         : [[name: haara]],
                     doGenerateSubmoduleConfigurations: false,
                     extensions                       : [[$class: 'CheckoutOption', timeout: 15],
                                                         [$class: 'CloneOption', depth: 0, noTags: false, reference: '', shallow: true, timeout: 15]],
                     submoduleCfg                     : [],
                     userRemoteConfigs                : [[url: 'https://github.com/finnishtransportagency/harja.git']]]])
}

def checkoutCI() {
    checkout([poll: false,
              scm : [$class                           : 'GitSCM',
                     branches                         : [[name: '*/master']],
                     doGenerateSubmoduleConfigurations: false,
                     extensions                       : [],
                     submoduleCfg                     : [],
                     userRemoteConfigs                : [[url: params.HARJA_CI_URL]]]])
}

def checkoutE2E() {
    checkout([poll: false,
              scm : [$class                           : 'GitSCM',
                     branches                         : [[name: '*/develop']],
                     doGenerateSubmoduleConfigurations: false,
                     extensions                       : [],
                     submoduleCfg                     : [],
                     userRemoteConfigs                : [[url: params.HARJA_E2E_URL]]]])
}
return this
