;; Profiilit mergetään https://github.com/technomancy/leiningen/blob/master/doc/PROFILES.md
;; ellei erikseen käytetä with-profile
{:dev {:dependencies [[prismatic/dommy "1.1.0"]
                      [org.clojure/test.check "0.9.0"]
                      [org.apache.pdfbox/pdfbox "2.0.8"]

                      [com.bhauman/figwheel-main "0.2.1"]
                      [com.bhauman/rebel-readline-cljs "0.1.4"]]
       :source-paths ["src/cljs" "src/cljc" "src/cljs-dev" "src/shared-cljc" "script"]
       :resource-paths ["dev-resources/js" "dev-resources/tmp" "resources/public/css" "resources"]
       :plugins [[com.solita/lein-test-refresh-gui "0.10.3"]
                 [test2junit "1.4.2"]
                 [lein-eftest "0.5.0"]
                 [lein-with-env-vars "0.2.0"]]
       :clean-targets ["dev-resources/tmp"]
       :env-vars {:HARJA_DEV_YMPARISTO true}
       ;; Käytetään tätä hookkia, jotta ei tarvitse erikseen kirjoitella leiningenille "with-env-vars", jotta nuo env-vars:it olisi käytössä
       :hooks [leiningen.with-env-vars/auto-inject]
       ;; Sonic MQ:n kirjastot voi tarvittaessa lisätä paikallista testausta varten:
       ;; :resource-paths ["opt/sonic/7.6.2/*"]
       }
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
 :prod {:source-paths ^:replace ["src/cljs" "src/cljc" "src/cljs-prod" "src/shared-cljc"]}

 :laadunseuranta-dev {:source-paths ["laadunseuranta/src" "laadunseuranta/cljc-src" "src/shared-cljc"]}
 :laadunseuranta-test {:source-paths ["laadunseuranta/src" "laadunseuranta/cljc-src" "src/shared-cljc"
                                      "laadunseuranta/test-src/cljs" "test/shared-cljs"]}
 :laadunseuranta-prod {:source-paths ^:replace ["laadunseuranta/src" "laadunseuranta/cljc-src" "src/shared-cljc"
                                                "src/cljs" "src/cljc" "src/cljs-prod" "src/shared-cljc"]}}