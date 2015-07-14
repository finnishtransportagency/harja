(defproject harja "0.0.1-SNAPSHOT"
  :description "Liikenneviraston Harja"

  :dependencies [[org.clojure/clojure "1.7.0"]
                 [org.clojure/clojurescript "0.0-3308"]

                 ;;;;;;; Yleiset ;;;;;;;
                 [prismatic/schema "0.4.3"]
                 [org.clojure/core.async "0.1.346.0-17112a-alpha"]

                 ;; Transit tietomuoto asiakkaan ja palvelimen väliseen kommunikointiin
                 [com.cognitect/transit-cljs "0.8.220"]
                 [com.cognitect/transit-clj "0.8.275"]

                 ;;;;;;; Palvelin ;;;;;;;

                 ;; Komponenttituki
                 [com.stuartsierra/component "0.2.3"]

                 ;; Lokitus
                 ;;[org.clojure/tools.logging "0.3.1"]
                 ;;[ch.qos.logback/logback-classic "1.1.3"]
                 [com.taoensso/timbre "3.4.0"] ;; FIXME: päivitä v4, jossa myös CLJS tuki
                 
                 [com.narkisr/gelfino-client "0.7.0"]

                 ;; HTTP palvelin ja reititys
                 [http-kit "2.1.19"]
                 [compojure "1.3.4"]
                 [javax.servlet/servlet-api "2.5"]
                 [hiccup "1.0.5"]

                 
                 [org.clojure/core.cache "0.6.4"]

                 ;; Tietokanta: ajuri, kirjastot ja -migraatiot
                 [org.postgresql/postgresql "9.3-1102-jdbc41"]
                 [clojunauts/postgis-jdbc "2.1.0SVN"]
                 ;;[org.postgis/postgis-jdbc "2.1.4dev"] ;; mvnrepossa vain 1.3.3 versio, piti buildata itse!
                 [com.mchange/c3p0 "0.9.5"]
                 [yesql "0.4.2" :exclusions [[instaparse :classifier "*"]]]
                 [instaparse "1.4.1"]

                 ;; GeoTools
                 [org.geotools/gt-shapefile "12.2"]
                 [org.geotools/gt-process-raster "12.2"]
                 [org.geotools/gt-epsg-wkt "12.2"] ;; EPSG koordinaatistot
                 [org.geotools/gt-swing "12.2"] ;; just for experimentation, remove when no longer needed

                 ;; XML zipper
                 [org.clojure/data.zip "0.1.1"]

                 ;; Sonja-väylän JMS riippuvuudet
                 [progress/sonic-client "7.6.2"]
                 [progress/sonic-crypto "7.6.2"]
                 [progress/sonic-xmessage "7.6.2"]
                 ;; ActiveMQ testejä varten
                 [org.apache.activemq/activemq-client "5.11.1"]

                 ;; Sähköposti lähetys
                 [com.draines/postal "1.11.3"]

                 ;; Asiakas
                 [spyscope "0.1.5"]
                 ;[spellhouse/clairvoyant "0.0-48-gf5e59d3"]

                 [cljs-ajax "0.3.13"]
                 ;;[lively "0.2.0"]
                 [figwheel "0.3.5"]

                 [reagent "0.5.0" :exclusions [[cljsjs/react :classifier "*"]]]
                 [cljsjs/react-with-addons "0.13.3-0"]

                 [org.clojure/core.async "0.1.346.0-17112a-alpha"]

                 [com.andrewmcveigh/cljs-time "0.3.3"] ;; tämän uusi versio aiheuttaa vertailuongelmia(?!)

                 [cljsjs/openlayers "3.5.0-1"]

                 ;; Microsoft dokumenttimuotojen tuki
                 [org.apache.poi/poi "3.12"] ;; siirrä oikeisiin depseihin, kun tarvitaan XLS export feature
                 [org.apache.poi/poi-scratchpad "3.12"] ;; .ppt varten
                 [org.apache.poi/poi-ooxml "3.12"] ;; .xlsx tiedoston lukua varten
                 [org.clojure/data.json "0.2.6"]

                 ;; Chime -ajastuskirjasto
                 [jarohen/chime "0.1.6"]

                 ;; Pikkukuvien muodostamiseen
                 [net.coobird/thumbnailator "0.4.8"]

                 ;; JSON -validointikirjastot
                 [com.github.fge/json-schema-validator "2.2.6"]
                 [com.fasterxml.jackson.core/jackson-databind "2.5.3"]

                 [org.clojure/test.check "0.7.0"]

                 ;; Slingshot -kirjasto poikkeusten käsittelyyn
                 [slingshot "0.12.2"]

                 ;; Joda Timen Clojure toteutus
                 [clojure.joda-time "0.2.0"]]


  :dev-dependencies [;; Selain REPL

                     ;; Testaus


                     ]


  :profiles {:dev {:dependencies []
                   :plugins [[com.jakemccrary/lein-test-refresh "0.10.0"]
                             [test2junit "1.1.0"]]}
             :test {:dependencies [[clj-webdriver "0.6.0"]
                                   [org.seleniumhq.selenium/selenium-java "2.44.0"]
                                   [org.seleniumhq.selenium/selenium-firefox-driver "2.44.0"]]}}

  :repositories [["osgeo" "http://download.osgeo.org/webdav/geotools/"]  ;; FIXME: move artifacts to mvn.solita.fi
                 ["solita" "http://mvn.solita.fi/archiva/repository/solita/"]
                 ]


  :plugins [[lein-cljsbuild "1.0.5"]
            [lein-less "1.7.2"]
            [lein-ancient "0.5.5"]
            [lein-figwheel "0.3.3"]
            [cider/cider-nrepl "0.9.1"]
            [codox "0.8.11"]
            [jonase/eastwood "0.2.1"]
            [lein-auto "0.1.2"]
            ;;[mvxcvi/whidbey "0.5.1"]
            ]  ;; Asiakaspuolen cljs buildin tietoja
  :cljsbuild {
              :builds [{:id "dev"
                        :source-paths ["src/cljs" "src/cljc" "src/cljs-dev" "test/cljs"]
                        :compiler {:optimizations :none
                                   :source-map true
                                   ;;:preamble ["reagent/react.js"]
                                   :output-to "dev-resources/js/harja.js"
                                   :output-dir "dev-resources/js/out"


                                   }}
                       {:id "test"
                        :source-paths ["src/cljs" "src/cljc" "src/cljs-dev" "test/cljs"]
                        :compiler {:output-to "target/cljs/test/test.js"
                                   :output-dir "target/cljs/test"
                                   :optimizations :none
                                   :pretty-print true
                                   :source-map "target/cljs/test/test.js.map"}
                        :notify-command ["./run-karma.sh"]}
                       ;;:warning-handlers [utils.cljs-warning-handler/handle]}


                       {:id "prod"
                        :source-paths ["src/cljs" "src/cljc" "src/cljs-prod"]
                        :compiler {:optimizations :advanced

                                   ;;:preamble ["reagent/react.min.js"]
                                   :output-to "resources/public/js/harja.js"
                                   :closure-extra-annotations #{"api" "observable"}

                                   ;; Nämä voi ottaa käyttöön, jos advanced compilation buildia pitää debugata
                                   :source-map "resources/public/js/harja.js.map"
                                   :output-dir "resources/public/js/"
                                   }}

                       ]}

  :clean-targets #^{:protect false} ["dev-resources/js/out" "target"
                                     "resources/public/js/harja.js"
                                     "resource/public/js/harja"]

  ;; Less CSS käännös tuotanto varten (dev modessa selain tekee less->css muunnoksen)
  :less {:source-paths ["dev-resources/less/application"]
         :target-path "resources/public/css/"}


  ;; Palvelimen buildin tietoja
  :source-paths ["src/clj" "src/cljc"]
  :test-paths ["test/clj"]
  :aot :all
  :main harja.palvelin.main
  :auto-clean false ;; for uberjar

  :test-refresh {:notify-command ["terminal-notifier" "-title" "Harja tests" "-message"]}

  ;; REPL kehitys
  :repl-options {:init-ns harja.palvelin.main
                 :init (harja.palvelin.main/-main)
                 :port 4005
                 :timeout 120000}


  ;; Clientin reload ja REPL
  :figwheel {:server-port 3449

             }
  ;; Tehdään komentoaliakset ettei build-komento jää vain johonkin Jenkins jobin konfiguraatioon
  :aliases {"tuotanto" ["do" "clean," "deps," "gitlog," "compile," "test2junit," "cljsbuild" "once" "prod," "less" "once," "uberjar," "doc"]

            ;; työkaluja, joita devaamisessa ja asiakkaalta saadun datan hieromisessa oikeaan muotoon, tarvitaan
            "elyt" ["run" "-m" "harja.tyokalut.elyt"] ;; ELY rajojen SHP file => hallintayksikkö SQL inserteiksi
            "sampo" ["run" "-m" "harja.tyokalut.sampo"] ;; SAMPO tuotelista XLS file => toimenpidekoodi SQL inserteiksi
            "gitlog" ["run" "-m" "harja.tyokalut.gitlog"] ;; tekee gitlogin resources alle
            "testit" ["with-profiles" "test" "do" "clean," "compile," "test"]

            "selainrepl" ["run" "-m" "harja.tyokalut.selainrepl"]
            }


  )
