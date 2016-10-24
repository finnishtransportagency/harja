(def jenkinsissa? (= "harja-jenkins.solitaservices.fi"
                     (.getHostName (java.net.InetAddress/getLocalHost))))

(defproject harja "0.0.1-SNAPSHOT"
  :description "Liikenneviraston Harja"

  :dependencies [[org.clojure/clojure "1.8.0"]
                 [org.clojure/clojurescript "1.8.51"]

                 ;;;;;;; Yleiset ;;;;;;;
                 [prismatic/schema "1.0.4"]
                 [org.clojure/core.async "0.2.374"]
                 ;; Transit tietomuoto asiakkaan ja palvelimen väliseen kommunikointiin
                 [com.cognitect/transit-cljs "0.8.237"]
                 [com.cognitect/transit-clj "0.8.285"]

                 ;;;;;;; Palvelin ;;;;;;;

                 ;; Komponenttituki
                 [com.stuartsierra/component "0.3.1"]

                 ;; Lokitus
                 ;;[org.clojure/tools.logging "0.3.1"]
                 ;;[ch.qos.logback/logback-classic "1.1.3"]
                 [com.taoensso/timbre "3.4.0"]              ;; FIXME: päivitä v4, jossa myös CLJS tuki

                 [com.narkisr/gelfino-client "0.7.0"]

                 ;; JSON encode/decode
                 [cheshire "5.6.1"]

                 ;; HTTP palvelin ja reititys
                 [http-kit "2.1.19"]
                 [compojure "1.5.0"]
                 [javax.servlet/servlet-api "2.5"]
                 [hiccup "1.0.5"]


                 [org.clojure/core.cache "0.6.5"]

                 ;; Tietokanta: ajuri, kirjastot ja -migraatiot
                 [org.postgresql/postgresql "9.4.1209"]
                 ;;[org.postgresql/postgresql "9.3-1102-jdbc41"]
                 [webjure/postgis-jdbc "2.1.7"]
                 ;;[org.postgis/postgis-jdbc "2.1.4dev"] ;; mvnrepossa vain 1.3.3 versio, piti buildata itse!
                 [com.mchange/c3p0 "0.9.5.2"]
                 [webjure/jeesql "0.4.2"]


                 ;; GeoTools
                 [org.geotools/gt-shapefile "12.2"]
                 [org.geotools/gt-process-raster "12.2"]
                 [org.geotools/gt-epsg-wkt "12.2"]          ;; EPSG koordinaatistot
                 [org.geotools/gt-swing "12.2"]             ;; just for experimentation, remove when no longer needed

                 ;; XML zipper
                 [org.clojure/data.zip "0.1.1"]

                 ;; Match
                 [org.clojure/core.match "0.3.0-alpha4"]

                 ;; Sähköposti lähetys
                 [com.draines/postal "1.11.3"]

                 [javax.jms/jms-api "1.1-rev-1"]
                 [org.apache.activemq/activemq-client "5.14.1"]


                 ;; Asiakas
                 [spyscope "0.1.5"]
                 ;[spellhouse/clairvoyant "0.0-48-gf5e59d3"]

                 [cljs-ajax "0.5.3"]
                 [figwheel "0.5.3"]

                 [reagent "0.6.0-rc" :exclusions [[cljsjs/react :classifier "*"]]]
                 [cljsjs/react-with-addons "15.2.1-1"]
                 [cljsjs/react-dom "15.2.1-1" :exclusions [cljsjs/react]]

                 [alandipert/storage-atom "1.2.4"]

                 [clj-time "0.11.0"]
                 [com.andrewmcveigh/cljs-time "0.3.14"]      ;; tämän uusi versio aiheuttaa vertailuongelmia(?!)

                 [cljsjs/openlayers "3.15.1"]

                 ;; Microsoft dokumenttimuotojen tuki
                 [org.apache.poi/poi "3.14"]
                 [org.apache.poi/poi-scratchpad "3.14"]     ;; .ppt varten
                 [org.apache.poi/poi-ooxml "3.14"]          ;; .xlsx tiedoston lukua varten
                 [org.clojure/data.json "0.2.6"]

                 ;; Chime -ajastuskirjastoe
                 [jarohen/chime "0.1.9"]

                 ;; Pikkukuvien muodostamiseen
                 [net.coobird/thumbnailator "0.4.8"]

                 ;; JSON -validointikirjastot
                 [webjure/json-schema "0.7.2"]

                 ;; Slingshot -kirjasto poikkeusten käsittelyyn
                 [slingshot "0.12.2"]

                 ;; PDF:n generointi
                 [org.apache.xmlgraphics/fop "2.1"]

                 ;; Fake-HTTP testaukseen
                 [http-kit.fake "0.2.2"]

                 ;; Apache ANT core
                 [org.apache.ant/ant "1.9.6"]

                 ;; Clojure(Script) assertointi
                 [com.taoensso/truss "1.0.0"]

                 ;; Apache POI wrapper (Excel yms lukemiseen)
                 [dk.ative/docjure "1.10.0"]

                 [com.cemerick/piggieback "0.2.1"]
                 [figwheel-sidecar "0.5.3"]

                 ;; Performance metriikat
                 [yleisradio/new-reliquary "1.0.0"]

                 ;; Tuck UI apuri
                 [webjure/tuck "0.2.1"]

                 ;; Laadunseurantatyökalua varten
                 [org.clojure/data.codec "0.1.0"]
                 [devcards "0.2.1-4" :exclusions [cljsjs/react]]

                 ]


  :profiles {:dev  {:dependencies [[prismatic/dommy "1.1.0"]
                                   [cljs-react-test "0.1.4-SNAPSHOT"]
                                   [org.clojure/test.check "0.9.0"]]
                    :plugins      [[com.solita/lein-test-refresh-gui "0.10.3"]
                                   [test2junit "1.1.0"]]
                    :test2junit-run-ant ~(not jenkinsissa?)
                    ;; Sonic MQ:n kirjastot voi tarvittaessa lisätä paikallista testausta varten:
                    ;; :resource-paths ["opt/sonic/7.6.2/*"]
                    }
             :test {:dependencies [[clj-webdriver "0.6.0"]
                                   [org.seleniumhq.selenium/selenium-java "2.44.0"]
                                   [org.seleniumhq.selenium/selenium-firefox-driver "2.44.0"]]}}

  :repositories [["harja-data" "http://185.26.50.104/mvn/"]
                 ["osgeo" "http://download.osgeo.org/webdav/geotools/"] ;; FIXME: move artifacts to mvn.solita.fi
                 ["boundlessgeo" "https://repo.boundlessgeo.com/main/"]]


  :plugins [[lein-cljsbuild "1.1.2"]
            [lein-less "1.7.5"]
            [lein-ancient "0.5.5"]
            [lein-figwheel "0.5.3"]
            [codox "0.8.11"]
            [jonase/eastwood "0.2.3"]
            [lein-auto "0.1.2"]
            [lein-pdo "0.1.1"]
            [lein-doo "0.1.6"]]

  ;; Asiakaspuolen cljs buildin tietoja
  :cljsbuild {:builds
              [{:id           "dev"
                :source-paths ["src/cljs" "src/cljc" "src/cljs-dev"]
                :compiler     {:optimizations :none
                               :source-map    true
                               ;;:preamble ["reagent/react.js"]
                               :output-to     "dev-resources/js/harja.js"
                               :output-dir    "dev-resources/js/out"
                               :libs ["src/js/kuvataso.js"]
                               :closure-output-charset "US-ASCII"
                               :recompile-dependents false
                               }}
               {:id             "test"
                :source-paths   ["src/cljs" "src/cljc" "src/cljs-dev"
                                 "test/cljs" "test/doo"]
                :compiler       {:output-to     "target/cljs/test/test.js"
                                 :output-dir    "target/cljs/test"
                                 :optimizations :none
                                 :pretty-print  true
                                 :source-map    true
                                 :libs ["src/js/kuvataso.js"]
                                 :closure-output-charset "US-ASCII"
                                 :main harja.runner
                                 }
                :notify-command ["./run-karma.sh"]}
               ;;:warning-handlers [utils.cljs-warning-handler/handle]}


               {:id           "prod"
                :source-paths ["src/cljs" "src/cljc" "src/cljs-prod"]
                :compiler     {:optimizations :advanced
                               ;; korjaa pitkän buildiajan http://dev.clojure.org/jira/browse/CLJS-1228
                               :recompile-dependents false
                               ;;:preamble ["reagent/react.min.js"]
                               :output-to                 "resources/public/js/harja.js"
                               :closure-extra-annotations #{"api" "observable"}

                               ;; Nämä voi ottaa käyttöön, jos advanced compilation buildia pitää debugata
                               :source-map                "resources/public/js/harja.js.map"
                               :output-dir                "resources/public/js/"

                               :parallel-build true
                               :libs ["src/js/kuvataso.js"]
                               :closure-output-charset "US-ASCII"
                               }}

               ;; Laadunseurannan buildit
               {:id "laadunseuranta-dev"
                :source-paths ["laadunseuranta/src" "laadunseuranta/cljc-src"]

                :figwheel {:on-jsload "harja-laadunseuranta.dev-core/on-js-reload"}

                :compiler {:main harja-laadunseuranta.dev-core
                           :asset-path "js/compiled/out"
                           :output-to "resources/public/laadunseuranta/js/compiled/harja_laadunseuranta.js"
                           :output-dir "resources/public/laadunseuranta/js/compiled/out"
                           :source-map-timestamp true}}

               {:id "laadunseuranta-devcards"
                :source-paths ["laadunseuranta/src" "laadunseuranta/cljc-src"]

                :figwheel {:devcards true
                                        ;:nrepl-port 7889
                                        ;:server-port 3450
                           }

                :compiler {:main harja-laadunseuranta.devcards-core
                           :asset-path "js/compiled/devcards_out"
                           :output-to "resources/public/laadunseuranta/js/compiled/harja_laadunseuranta_devcards.js"
                           :output-dir "resources/public/laadunseuranta/js/compiled/devcards_out"
                           :source-map-timestamp true}}

               {:id "laadunseuranta-test"
                :source-paths ["laadunseuranta/src" "laadunseuranta/cljc-src"
                               "laadunseuranta/test-src/cljs"]

                :compiler {:main harja-laadunseuranta.test-main
                           ;;:asset-path "laadunseuranta/js/out"
                           :output-to "resources/private/laadunseuranta/js/unit-test.js"
                           ;;:output-dir "resources/private/laadunseuranta/js/out"
                           :source-map-timestamp true
                           :foreign-libs
                           [{:file "resources/public/laadunseuranta/js/proj4.js"
                             :provides ["proj4"]}
                            {:file "resources/public/laadunseuranta/js/epsg3067.js"
                             :provides ["epsg3067"]}]}}

               ;; This next build is an compressed minified build for
               ;; production. You can build this with:
               ;; lein cljsbuild once min
               {:id "laadunseuranta-min"
                :source-paths ["laadunseuranta/src" "laadunseuranta/cljc-src"]
                :jar true
                :compiler {:output-to "resources/public/laadunseuranta/js/compiled/harja_laadunseuranta.js"
                           :closure-extra-annotations #{"api" "observable"}
                           :main harja-laadunseuranta.prod-core
                           :optimizations :advanced
                           :language-in  :ecmascript5
                           :language-out :ecmascript5
                           :externs ["laadunseuranta/externs.js"]
                           :parallel-build true
                           :pretty-print false}}]}

  :clean-targets #^{:protect false} ["dev-resources/js/out" "target"
                                     "resources/public/js/harja.js"
                                     "resource/public/js/harja"]

  ;; Less CSS käännös tuotanto varten (dev modessa selain tekee less->css muunnoksen)
  :less {:source-paths ["dev-resources/less/application"
                        "dev-resources/less/laadunseuranta/application"]
         :target-path  "resources/public/css/"}


  ;; Palvelimen buildin tietoja
  :source-paths ["src/clj" "src/cljc" "laadunseuranta/clj-src" "laadunseuranta/cljc-src"]
  :test-paths ["test/clj" "laadunseuranta/test-src/clj"]
  :aot :all
  :main harja.palvelin.main
  :auto-clean false                                         ;; for uberjar

  :test-refresh {:notify-command ["terminal-notifier" "-title" "Harja tests" "-message"]}

  ;; REPL kehitys
  :repl-options {:init-ns harja.palvelin.main
                 :init    (harja.palvelin.main/-main)
                 :port    4005
                 :timeout 120000
                 :nrepl-middleware [cemerick.piggieback/wrap-cljs-repl]}


  ;; Clientin reload ja REPL
  :figwheel {:server-port 3449
             :reload-clj-files false
             :css-dirs ["resources/public/laadunseuranta/css"]}

  ;; Tehdään komentoaliakset ettei build-komento jää vain johonkin Jenkins jobin konfiguraatioon
  :aliases {"tuotanto"            ["do" "clean," "deps," "gitlog," "compile," "test2junit,"
                                   ;; Harjan fronttibuildi ja LESS
                                   "cljsbuild" "once" "prod,"
                                   "less" "once,"

                                   ;; Harja mobiili laadunseuranta fronttibuildi
                                   "cljsbuild" "once" "laadunseuranta-min,"

                                   "uberjar," "doc"]
            "testit"             ["do" "clean,"
                                  "deps,"
                                  "test,"
                                  "doo" "phantom" "test" "once,"
                                  "doo" "phantom" "laadunseuranta-test" "once"]

            ;; työkaluja, joita devaamisessa ja asiakkaalta saadun datan hieromisessa oikeaan muotoon, tarvitaan
            "elyt"                ["run" "-m" "harja.tyokalut.elyt"] ;; ELY rajojen SHP file => hallintayksikkö SQL inserteiksi
            "sampo"               ["run" "-m" "harja.tyokalut.sampo"] ;; SAMPO tuotelista XLS file => toimenpidekoodi SQL inserteiksi
            "gitlog"              ["run" "-m" "harja.tyokalut.gitlog"] ;; tekee gitlogin resources alle

            "selainrepl"          ["run" "-m" "harja.tyokalut.selainrepl"]
            "tarkista-migraatiot" ["run" "-m" "harja.tyokalut.migraatiot"]
            "tuotanto-notest"     ["do" "compile,"
                                   "cljsbuild" "once" "prod,"
                                   "cljsbuild" "once" "laadunseuranta-min,"
                                   "less" "once," "uberjar"]}

  ;; JAI ImageIO tarvitsee MANIFEST arvoja toimiakseen
  ;; Normaalisti ne tulevat sen omasta paketista, mutta uberjar tapauksessa
  ;; ne pitää kopioida
  :manifest {"Specification-Title"    "Java Advanced Imaging Image I/O Tools"
             "Specification-Version"  "1.1"
             "Specification-Vendor"   "Sun Microsystems, Inc."
             "Implementation-Title"   "com.sun.media.imageio"
             "Implementation-Version" "1.1"
             "Implementation-Vendor"  "Sun Microsystems, Inc."
             "Extension-Name"         "com.sun.media.imageio"}


  ;;:doo {:paths {:phantom "phantomjs --local-storage-path=/tmp --local-storage-quota=1024 --offline-storage-path=/tmp --offline-storage-quota=1024"}}
  )
