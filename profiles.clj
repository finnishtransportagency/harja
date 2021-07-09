;; Profiilit mergetään https://github.com/technomancy/leiningen/blob/master/doc/PROFILES.md
;; ellei erikseen käytetä with-profile
{:dev {:dependencies [[prismatic/dommy "1.1.0"]
                      [org.clojure/test.check "0.9.0"]
                      [org.apache.pdfbox/pdfbox "2.0.8"]
                      [data-frisk-reagent "0.4.5"]

                      [com.bhauman/rebel-readline-cljs "0.1.4"]]
       :source-paths ["src/clj-dev" "src/cljs" "src/cljc" "src/cljs-dev" "src/shared-cljc" "script"]
       :resource-paths ["dev-resources/js" "dev-resources/tmp" "resources/public/css" "resources"]
       :plugins [[com.solita/lein-test-refresh-gui "0.10.3"]
                 [test2junit "1.4.2"]
                 [lein-eftest "0.5.0"]]
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
                            :HARJA_SONJA_BROKER_HOST "localhost"
                            :HARJA_SONJA_BROKER_PORT 61616
                            :HARJA_ITMF_BROKER_PORT 61626
                            :HARJA_ITMF_BROKER_HOST "localhost"
                            :HARJA_SONJA_BROKER_AI_PORT 8161
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
 :repl {:repl-options {:init-ns harja.palvelin.main
                       :init (harja.palvelin.main/-main)
                       :port 4005
                       :timeout 120000}}
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
        :source-paths ["test/cljs" "test/doo" "test/shared-cljs"]
        :notify-command ["./run-karma.sh"]}
 :prod-cljs {:source-paths ^:replace ["src/cljs" "src/cljc" "src/cljs-prod" "src/shared-cljc"]}

 :laadunseuranta-dev {:source-paths ["laadunseuranta/src" "laadunseuranta/cljc-src" "src/shared-cljc"]}
 :laadunseuranta-test {:source-paths ["laadunseuranta/src" "laadunseuranta/cljc-src" "src/shared-cljc"
                                      "laadunseuranta/test-src/cljs" "test/shared-cljs"]}
 :laadunseuranta-prod {:source-paths ^:replace ["laadunseuranta/src" "laadunseuranta/cljc-src" "src/shared-cljc"
                                                "src/cljs" "src/cljc" "src/cljs-prod" "src/shared-cljc"]}}
