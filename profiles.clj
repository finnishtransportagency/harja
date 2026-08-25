;; Profiilit mergetään https://github.com/technomancy/leiningen/blob/master/doc/PROFILES.md
;; ellei erikseen käytetä with-profile
;; Tarkemmat ohjeet: https://leiningen.org/profiles.html
{
 ;; Lopeta suoritus, jos overrideja tai versio rangeja löytyy riippuvuuspuusta
 ;;   Katso: https://github.com/technomancy/leiningen/blob/24fb93936133bd7fc30c393c127e9e69bb5f2392/sample.project.clj#L82
 :pedantic-abort {:pedantic? :abort}
 ;; Näytä varoitukset, jos overrideja tai rangeja löytyy riippuvuuspuusta
 :pedantic-warn {:pedantic? :warn}


 ;; CI-profiili: Yliajaa :jvm-opts suuremmalla heapilla ja G1GC:llä.
 ;; Annetaan korkeahko 4g heap alkuun, jotta sitä ei tarvitse heti kasvattaa ajon aikana.
 ;; GitHub Actions public repo runner (ubuntu-latest): 4 CPU / 16 GB RAM.
 ;; Käytä CI:ssä: lein with-profile +ci ...
 :ci {:jvm-opts ^:replace ["-Xms4g" "-Xmx8g" "-XX:+UseG1GC" "-XX:+TieredCompilation"]}
 :dev {:dependencies [
                      [com.bhauman/rebel-readline-cljs "0.1.5"]
                      [cider/piggieback "0.6.0"]

                      ;; Figwheeliä tarvitaan CLJS käännöksessä (dev, prod)
                      [com.bhauman/figwheel-main "0.2.20"]
                      [prismatic/dommy "1.1.0"]
                      [org.clojure/test.check "1.1.1"]
                      [data-frisk-reagent "0.4.5"]

                      ;; -- PDF-testaukseen --
                      [org.apache.pdfbox/pdfbox "3.0.5"]

                      ;; -- Testien ajamista varten (replillä ja ilman) --
                      ;; Fake-HTTP testaukseen
                      [http-kit.fake "0.2.2"]

                      ;; JMS API (esim. tieliikenneilmoitukset) JMS-jonojen testausta varten
                      [javax.jms/javax.jms-api "2.0.1"]

                      ;; Gatlingin logback versio ei ole vielä ehtinyt päivittyä, niin haetaan se erikseen
                      [ch.qos.logback/logback-classic "1.5.18" :exclusions [org.slf4j/slf4j-api]]
                      [clj-gatling "0.18.0" :exclusions [clj-time org.slf4j/slf4j-api org.clojure/core.memoize
                                                         org.clojure/tools.analyzer org.clojure/data.priority-map io.pebbletemplates/pebble]]]
                      
       :source-paths ["src/clj-dev" "src/cljs" "src/cljc" "src/cljs-dev" "src/shared-cljc" "script"]
       :resource-paths ["dev-resources/js" "dev-resources/tmp" "resources/public/css" "resources"]
       :plugins [[test2junit "1.4.4" :exclusions [org.clojure/clojure]]
                 [lein-eftest "0.6.0"]
                 ;; Pprint-pluginin avulla voit nähdä miten profiilit vaikuttavat konfiguraatioon
                 ;; Esim. lein with-profile +test pprint
                 [lein-pprint "1.3.2"]]}
       
 :dev-ymparisto {:plugins [[lein-with-env-vars "0.2.0"]]
                 :env-vars {:HARJA_DEV_YMPARISTO "true"
                            :HARJA_TIETOKANTA_HOST "localhost"
                            :HARJA_TIETOKANTA_HOST_KAANNOS "localhost"
                            :HARJA_SALLI_OLETUSKAYTTAJA "false"
                            :HARJA_DEV_RESOURCES_PATH "dev-resources"
                            ;; Testeihin devatessa
                            :HARJA_AJA_GATLING_RAPORTTI "false"
                            :HARJA_NOLOG "false"
                            :HARJA_ITMF_BROKER_PORT 61626
                            :HARJA_ITMF_BROKER_HOST "localhost"
                            :HARJA_ITMF_BROKER_AI_PORT 8171}}
 :dev-cljs {:source-paths ^:replace ["src/cljs" "src/cljc" "src/cljs-dev" "src/shared-cljc" "script" "laadunseuranta/cljc-src"]}
 :dev-container {:target-path #=(eval (str (System/getenv "DC_JAETTU_KANSIO") "/" (System/getenv "BRANCH") "/harja-target"))
                 :resource-paths ^:replace [#=(eval (str (System/getenv "DC_JAETTU_KANSIO") "/" (System/getenv "BRANCH") "/dev-resources"))
                                            "dev-resources/tmp"
                                            "resources"]
                 :jvm-opts ["-Xverify:none"]
                 :compile-path #=(eval (str (System/getenv "DC_JAETTU_KANSIO") "/" (System/getenv "BRANCH") "/harja-target/classes"))
                 :clean-targets ^{:protect false
                                  :replace true} [#=(eval (str (System/getenv "DC_JAETTU_KANSIO") "/" (System/getenv "BRANCH") "/dev-resources"))
                                                  "dev-resources/tmp"
                                                  :target-path]}
 :dev-emacs {:plugins [[cider/cider-nrepl "0.57.0"]
                       [refactor-nrepl "3.11.0"]]}
 :repl {:dependencies [[cider/piggieback "0.6.0"]]
        :plugins [[cider/cider-nrepl "0.57.0"]]
        :repl-options {:init-ns harja.palvelin.main
                       :init (harja.palvelin.main/-main)
                                                ;; Worktree-ajossa nREPL-portti pitää voida yliajaa,
                                                ;; jotta rinnakkaiset instanssit eivät törmää porttiin 4005.
                        :port #=(eval (let [p (System/getenv "HARJA_NREPL_PORTTI")]
                                                (if (and p (re-matches #"\d+" p))
                                                    (Integer/parseInt p)
                                                    4005)))
                       :timeout 120000
                       :nrepl-middleware [cider.piggieback/wrap-cljs-repl]}}
 ;; Test-dependencyt, joita ei tarvita dev-profiilissa
 :test {:dependencies [
                       ;; Selenium + webdriver depsut pois käytöstä, koska niitä ei enää ajeta
                       #_[clj-webdriver "0.7.2"]
                       #_[org.seleniumhq.selenium/selenium-java "3.8.1"]
                       #_[org.seleniumhq.selenium/selenium-firefox-driver "3.8.1"]]}

 :shadow-cljs-testit {:dependencies [[org.clojure/clojure "1.11.1"]
                                    [org.clojure/clojurescript "1.11.132"]
                                    [com.google.javascript/closure-compiler-unshaded "v20240317"]
                                    [org.clojure/google-closure-library "0.0-20230227-c7c0a541"]
                                    [thheller/shadow-cljs "2.28.23"]
                                    [org.clojars.olecve/react-testing-library-cljs "0.0.20"]]
                     :source-paths ^:replace ["src/clj" "src/cljs" "src/cljc" "src/cljs-dev"
                                              "src/shared-cljc" "test/shadow"]
                     :resource-paths ^:replace ["dev-resources/js" "dev-resources/tmp"
                                                "resources/public/css" "resources"]}

 ;; TODO: Hankkiudu eroon PhantomJS:stä
 ;; Phantomjs testejä varten tarvitaan erillinen profiili, koska se ei tue enää uudempia kirjastoversioita ja JavaScriptin
 ;; uudemmat ominaisuudet eivät toimi siinä.
 :phantomjs {:dependencies [
                            ;; TODO tuosta cljs-react-test riippuvuudesta pitäisi päästä eroon. Testit, jotka
                            ;; käyttää sitä, voi kirjoittaa uusiksi Cypressillä.
                            ;; Jotta frontti testit toimii, pitää säilyttää tuo riippuvuus, jonka takia myös
                            ;; reagentti pitää downgradeta testejä varten.
                            [reagent "0.7.0" :exclusions [[cljsjs/react :classifier "*"]]]
                            [cljsjs/react-with-addons "15.6.1-0"]
                            [cljsjs/react-dom "15.4.2-2" :exclusions [cljsjs/react]]
                            [cljs-react-test "0.1.4-SNAPSHOT"]

                            ;; Cloju(Script) assertointi ja lokitus
                            ;; Downgradetettu, koska PhantomJS ei tue uudempia versioita. Mukana tuleva encore.js
                            ;; käyttää uudempia JS ominaisuuksia, jotka eivät toimi PhantomJS:ssä.
                            [com.taoensso/truss "1.12.0"]
                            [com.taoensso/timbre "6.5.0"]]
             :source-paths ["test/cljs" "test/doo" "test/shared-cljs"]}
 :prod-cljs {:source-paths ^:replace ["src/clj" "src/cljs" "src/cljc" "src/cljs-prod" "src/shared-cljc"]}

 ;; -- Laadunseuranta --
 ;; Ainoastaan laadunseurantaan liittyvät riippuvuudet
 :laadunseuranta-common {:dependencies [[devcards "0.2.7" :exclusions [cljsjs/react]]]}
 :laadunseuranta-dev-paths {:source-paths ["laadunseuranta/src" "laadunseuranta/cljc-src" "src/shared-cljc"]}
 :laadunseuranta-test-paths {:source-paths ["laadunseuranta/src" "laadunseuranta/cljc-src" "src/shared-cljc"
                                            "laadunseuranta/test-src/cljs" "test/shared-cljs"]}
 :laadunseuranta-prod-paths {:source-paths ^:replace ["laadunseuranta/src" "laadunseuranta/cljc-src" "src/shared-cljc"
                                                      "src/cljs" "src/cljc" "src/cljs-prod" "src/shared-cljc"]}

 ;; Laadunseurantaan liittyvät profiilit (komposiittiprofiilit)
 :laadunseuranta-dev [:laadunseuranta-common :laadunseuranta-dev-paths]
 :laadunseuranta-test [:laadunseuranta-common :laadunseuranta-test-paths]
 :laadunseuranta-prod [:laadunseuranta-common :laadunseuranta-prod-paths]}
