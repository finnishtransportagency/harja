;; Profiilit mergetään https://github.com/technomancy/leiningen/blob/master/doc/PROFILES.md
;; ellei erikseen käytetä with-profile
;; Tarkemmat ohjeet: https://leiningen.org/profiles.html
{:dev {:dependencies [
                      ;; Tarvitaan CLJS käännöksessä (dev, prod)
                      [com.bhauman/figwheel-main "0.2.18"]
                      [prismatic/dommy "1.1.0"]
                      [org.clojure/test.check "0.9.0"]
                      [org.apache.pdfbox/pdfbox "2.0.30"]
                      [data-frisk-reagent "0.4.5"]
                      [cider/piggieback "0.5.2"]
                      [com.bhauman/rebel-readline-cljs "0.1.4"]

                      ;; -- Testien ajamista varten (replillä ja ilman) --
                      ;; Fake-HTTP testaukseen
                      [http-kit.fake "0.2.2"]

                      ;; JMS API (esim. tieliikenneilmoitukset) JMS-jonojen testausta varten
                      [javax.jms/javax.jms-api "2.0.1"]

                      ;; Gatlingin logback versio ei ole vielä ehtinyt päivittyä, niin haetaan se erikseen
                      [ch.qos.logback/logback-classic "1.4.14" :exclusions [org.slf4j/slf4j-api]]
                      [clj-gatling "0.18.0" :exclusions [clj-time org.slf4j/slf4j-api org.clojure/core.memoize
                                                         org.clojure/tools.analyzer org.clojure/data.priority-map io.pebbletemplates/pebble]]
                      ]
       :source-paths ["src/clj-dev" "src/cljs" "src/cljc" "src/cljs-dev" "src/shared-cljc" "script"]
       :resource-paths ["dev-resources/js" "dev-resources/tmp" "resources/public/css" "resources"]
       :plugins [[test2junit "1.4.4"]
                 [lein-eftest "0.5.0"]
                 ;; Pprint-pluginin avulla voit nähdä miten profiilit vaikuttavat konfiguraatioon
                 ;; Esim. lein with-profile +test pprint
                 [lein-pprint "1.3.2"]]
       ;; Sonic MQ:n kirjastot voi tarvittaessa lisätä paikallista testausta varten:
       ;; :resource-paths ["opt/sonic/7.6.2/*"]
       }
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
                 :less ^:replace {:source-paths ["dev-resources/less/application"
                                                 "dev-resources/less/laadunseuranta/application"]
                                  :target-path #=(eval (str (System/getenv "DC_JAETTU_KANSIO") "/" (System/getenv "BRANCH") "/dev-resources/css"))}
                 :jvm-opts ["-Xverify:none"]
                 :compile-path #=(eval (str (System/getenv "DC_JAETTU_KANSIO") "/" (System/getenv "BRANCH") "/harja-target/classes"))
                 :clean-targets ^{:protect false
                                  :replace true} [#=(eval (str (System/getenv "DC_JAETTU_KANSIO") "/" (System/getenv "BRANCH") "/dev-resources"))
                                                  "dev-resources/tmp"
                                                  :target-path]}
 :dev-emacs {:plugins [[cider/cider-nrepl "0.25.3"]
                       [refactor-nrepl "2.5.0"]]}
 :repl {:dependencies [[cider/piggieback "0.5.2"]]
        :plugins [[cider/cider-nrepl "0.25.2"]]
        :repl-options {:init-ns harja.palvelin.main
                       :init (harja.palvelin.main/-main)
                       :port 4005
                       :timeout 120000
                       :nrepl-middleware [cider.piggieback/wrap-cljs-repl]}}
 ;; Loput test-dependencyt, joita ei haluta ottaa mukaan dev-profiiliin
 :test {:dependencies [[clj-webdriver "0.7.2"]
                       [org.seleniumhq.selenium/selenium-java "3.8.1"]
                       [org.seleniumhq.selenium/selenium-firefox-driver "3.8.1"]
                       ;; TODO tuosta cljs-react-test riippuvuudesta pitäisi päästä eroon. Testit, jotka
                       ;; käyttää sitä, voi kirjoittaa uusiksi Cypressillä.
                       ;; Jotta frontti testit toimii, pitää säilyttää tuo riippuvuus, jonka takia myös
                       ;; reagentti pitää downgradeta testejä varten.
                       [reagent "0.7.0" :exclusions [[cljsjs/react :classifier "*"]]]
                       [cljsjs/react-with-addons "15.6.1-0"]
                       [cljsjs/react-dom "15.4.2-2" :exclusions [cljsjs/react]]
                       [cljs-react-test "0.1.4-SNAPSHOT"]]
        :source-paths ["test/cljs" "test/doo" "test/shared-cljs"]}
 :prod-cljs {:source-paths ^:replace ["src/cljs" "src/cljc" "src/cljs-prod" "src/shared-cljc"]}

 ;; -- Laadunseuranta --
 ;; Ainoastaan laadunseurantaan liittyvät riippuvuudet
 :laadunseuranta-common {:dependencies [[devcards "0.2.4" :exclusions [cljsjs/react]]]}
 :laadunseuranta-dev-paths {:source-paths ["laadunseuranta/src" "laadunseuranta/cljc-src" "src/shared-cljc"]}
 :laadunseuranta-test-paths {:source-paths ["laadunseuranta/src" "laadunseuranta/cljc-src" "src/shared-cljc"
                                            "laadunseuranta/test-src/cljs" "test/shared-cljs"]}
 :laadunseuranta-prod-paths {:source-paths ^:replace ["laadunseuranta/src" "laadunseuranta/cljc-src" "src/shared-cljc"
                                                      "src/cljs" "src/cljc" "src/cljs-prod" "src/shared-cljc"]}

 ;; Laadunseurantaan liittyvät profiilit (komposiittiprofiilit)
 :laadunseuranta-dev [:laadunseuranta-common :laadunseuranta-dev-paths]
 :laadunseuranta-test [:laadunseuranta-common :laadunseuranta-test-paths]
 :laadunseuranta-prod [:laadunseuranta-common :laadunseuranta-prod-paths]}
