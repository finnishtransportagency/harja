(def jenkinsissa? (= "harja-jenkins.solitaservices.fi"
                     (.getHostName (java.net.InetAddress/getLocalHost))))

(defproject harja "0.0.1-SNAPSHOT"
  :description "Väylän Harja"

  :dependencies [[org.clojure/clojure "1.10.0"]
                 [org.clojure/clojurescript "1.10.520"]
		 [org.clojure/spec.alpha "0.2.176"]
                 ;;;;;;; Yleiset ;;;;;;;

                 [prismatic/schema "1.1.10"]
                 [org.clojure/core.async "0.3.443"]
                 ;; Transit tietomuoto asiakkaan ja palvelimen väliseen kommunikointiin
                 [com.cognitect/transit-cljs "0.8.256"]
                 [com.cognitect/transit-clj "0.8.313"]

                 [cljsjs/exif "2.1.1-1"]

                 ;;;;;;; Palvelin ;;;;;;;

                 ;; Komponenttituki
                 [com.stuartsierra/component "0.4.0"]

                 ;; Lokitus
                 [com.taoensso/timbre "4.10.0"]

                 ;; Metriikkadata
                 [org.clojure/java.jmx "0.3.4"]

                 ;; JSON encode/decode
                 [cheshire "5.8.1"]

                 ;; HTTP palvelin ja reititys
                 [http-kit "2.4.0-alpha3"]
                 [compojure "1.6.1"]
                 ;; Ring tarvitsee
                 ;[javax.servlet/servlet-api "2.5"]
                 [javax.servlet/javax.servlet-api "3.1.0"]
                 [hiccup "1.0.5"]

                 [org.clojure/core.cache "0.7.2"]

                 ;; Tietokanta: ajuri, kirjastot ja -migraatiot
                 ;; Ajuria päivittäessä, muista päivittää myös pom.xml, koska flyway käyttää sitä ajurin versiota
                 [org.postgresql/postgresql "42.2.5"]
                 [net.postgis/postgis-jdbc "2.3.0"]
                 [org.locationtech.jts/jts-core "1.16.1"]
                 [com.mchange/c3p0 "0.9.5.4"]
                 [webjure/jeesql "0.4.7"]
                 [specql "20180706"]

                 ;; GeoTools
                 [org.geotools/gt-shapefile "21.0"]
                 [org.geotools/gt-process-raster "21.0"]
                 [org.geotools/gt-epsg-wkt "21.0"] ;; EPSG koordinaatistot
                 [org.geotools/gt-swing "21.0"] ;; just for experimentation, remove when no longer needed

                 ;; XML zipper
                 [org.clojure/data.zip "0.1.1"] ;; Jos päivittää uusimpaan, aiheuttaa parsintaongelmia https://dev.clojure.org/jira/browse/DZIP-6

                 ;; Match
                 [org.clojure/core.match "0.3.0-alpha5"]

                 [namespacefy "0.4"]

                 ;; Sähköposti lähetys
                 [com.draines/postal "2.0.3"]

                 [javax.jms/jms-api "1.1-rev-1"]
                 [org.apache.activemq/activemq-client "5.15.9"]

                 ;; Fileyard  liitetiedostojen tallennus
                 [fileyard "0.2"]

                 ;; Asiakas
                                        ; Tämä dev riippuvuuksiin
                                        ; [spyscope "0.1.6"]
                                        ;[spellhouse/clairvoyant "0.0-48-gf5e59d3"]

                 [cljs-ajax "0.8.0"]
                 [figwheel "0.5.19-SNAPSHOT"]

                 [reagent "0.7.0" :exclusions [[cljsjs/react :classifier "*"]]]
                 [cljsjs/react-with-addons "15.6.1-0"] ; TODO Voisi päivittää, mutta tämä ja react-dom aiheuttaa ongelman: Undefined nameToPath for react
                 [cljsjs/react-dom "15.4.2-2" :exclusions [cljsjs/react]]

                 [alandipert/storage-atom "2.0.1"]

                 [clj-time "0.15.0"]
                 [com.andrewmcveigh/cljs-time "0.5.2"]

                 ;; Kuvataso error tulee ol.source.Image inheritistä, jos päivittää neloseen
                 [cljsjs/openlayers "3.15.1"] ; TODO Voisi päivittää, mutta laadunseurannan buildi hajoaa (4.4.1-1) puuttuviin requireihin

                 ;; Microsoft dokumenttimuotojen tuki
                 [org.apache.poi/poi "3.17"] ; TODO Voisi päivittää, mutta dk.ative/docjure käyttää 3.17, ja tulee ikäviä erroreita jos tän päivittää
                                        ; docjuressa on kyllä pullero, joka tuon hoitaisi, niin jospa se joskus mergettäisiin
                 [org.apache.poi/poi-scratchpad "3.17"] ;; .ppt varten
                 [org.apache.poi/poi-ooxml "3.17"] ;; .xlsx tiedoston lukua varten
                 [org.clojure/data.json "0.2.6"]

                 ;; Chime -ajastuskirjasto
                 [jarohen/chime "0.2.2"]

                 ;; Pikkukuvien muodostamiseen
                 [net.coobird/thumbnailator "0.4.8"]

                 ;; JSON -validointikirjastot
                 [webjure/json-schema "0.7.4"]

                 ;; Slingshot -kirjasto poikkeusten käsittelyyn
                 [slingshot "0.12.2"]

                 ;; PDF:n generointi
                 [org.apache.xmlgraphics/fop "2.3"]

                 ;; Fake-HTTP testaukseen
                 [http-kit.fake "0.2.2"]

                 ;; Apache ANT core
                 [org.apache.ant/ant "1.10.5"]

                 ;; Clojure(Script) assertointi
                 [com.taoensso/truss "1.5.0"]

                 ;; Apache POI wrapper (Excel yms lukemiseen)
                 [dk.ative/docjure "1.14.0-SNAPSHOT"] ; TODO Päivitä tämä heti, kun https://github.com/mjul/docjure/pull/81 mergetty tai joku vastaava tehty
                                        ; Päivitä samalla apache poi

                 ;; Performance metriikat
                 [yleisradio/new-reliquary "1.1.0"]

                 ;; Tuck UI apuri
                 [webjure/tuck "0.4.1"] ; TODO Voisi päivittää, mutta 0.4.3:n kanssa tietyöilmoitukset-näkymä ei enää hae mitään kun tullaan sinne etusivun kautta

                 ;; Laadunseurantatyökalua varten
                 [org.clojure/data.codec "0.1.1"]
                 [devcards "0.2.4" :exclusions [cljsjs/react]]

                 ;; Parsi sourcemapit
                 [com.atlassian.sourcemap/sourcemap "1.7.7"]

                 ;; Arbitrary precision math frontilla
                 [cljsjs/big "3.1.3-1"]

                 [clj-gatling "0.13.0" :exclusions [[clj-time]]]]

  :managed-dependencies [[org.apache.poi/poi "3.17"]
                         [org.apache.poi/poi-scratchpad "3.17"]
                         [org.apache.poi/poi-ooxml "3.17"]]
  :profiles {:dev {:dependencies [[prismatic/dommy "1.1.0"]
                                  [cljs-react-test "0.1.4-SNAPSHOT"]
                                  [org.clojure/test.check "0.9.0"]
                                  [org.apache.pdfbox/pdfbox "2.0.8"]
                                  [figwheel-sidecar "0.5.19-SNAPSHOT"]
                                  [cider/piggieback "0.4.0"]]
                   :plugins [[com.solita/lein-test-refresh-gui "0.10.3"]
                             [test2junit "1.4.2"]
                             [lein-eftest "0.5.0"]]
                   :test2junit-run-ant ~(not jenkinsissa?)
                   ;; Sonic MQ:n kirjastot voi tarvittaessa lisätä paikallista testausta varten:
                   ;; :resource-paths ["opt/sonic/7.6.2/*"]
                   }
             :test {:dependencies [[clj-webdriver "0.7.2"]
                                   [org.seleniumhq.selenium/selenium-java "3.8.1"]
                                   [org.seleniumhq.selenium/selenium-firefox-driver "3.8.1"]]}}
  
  :jvm-opts ^:replace ["-Xms256m" "-Xmx2g"]

  :repositories [["osgeo" "https://download.osgeo.org/webdav/geotools/"]
                 ;; Tämä on kaiketi org.geotools/* dependencyjä varten. Nykyinen  versio
                 ;; on kyllä mavenissakin, mutta jos tarvii joskus SNPASHOTIN, niin tuolta
                 ;; löytyy
                 ["boundlessgeo" "https://repo.boundlessgeo.com/main/"]
                 ["atlassian" "https://maven.atlassian.com/content/repositories/atlassian-public/"]
                 ;; Tämä on tässä [org.clojure/data.zip "0.1.4-SNAPSHOT"] dependencyn takia
                 ;; ["sonatype-snapshots" "https://oss.sonatype.org/content/repositories/snapshots/"]
                 ]

  :plugins [[lein-cljsbuild "1.1.7"]
            [lein-less "1.7.5"]
            [lein-ancient "0.6.15"]
            [lein-figwheel "0.5.19-SNAPSHOT"]
            [lein-codox "0.10.6"]
            [jonase/eastwood "0.3.5"]
            [lein-auto "0.1.2"]
            [lein-pdo "0.1.1"]
            [lein-doo "0.1.8"]]


  ;; Asiakaspuolen cljs buildin tietoja
  :cljsbuild {:builds
              [{:id "dev"
                :source-paths ["src/cljs" "src/cljc" "src/cljs-dev" "src/shared-cljc" "script"]
                ;; Clientin reload ja REPL
                :figwheel true
                :compiler {:optimizations :none
                           :source-map true
                                        ;:parallel-build false Failaa randomisti
                           ;;:preamble ["reagent/react.js"]
                           :output-to "dev-resources/js/harja.js"
                           :output-dir "dev-resources/js/out"
                           :libs ["src/js/kuvataso.js"]
                           :closure-output-charset "US-ASCII"
                           :recompile-dependents false
                           }}
               {:id "test"
                :source-paths ["src/cljs" "src/cljc" "src/cljs-dev" "src/shared-cljc"
                               "test/cljs" "test/doo" "test/shared-cljs"]
                :compiler {:output-to "target/cljs/test/test.js"
                           :output-dir "target/cljs/test"
                           :optimizations :none
                           :pretty-print true
                           :source-map true
                                        ;:parallel-build false Failaa randomisti
                           :libs ["src/js/kuvataso.js"]
                           :closure-output-charset "US-ASCII"
                           :main harja.runner}
                :notify-command ["./run-karma.sh"]}
               ;;:warning-handlers [utils.cljs-warning-handler/handle]}

               {:id "prod"
                :source-paths ["src/cljs" "src/cljc" "src/cljs-prod" "src/shared-cljc"]
                :compiler {:optimizations :advanced
                           ;; korjaa pitkän buildiajan http://dev.clojure.org/jira/browse/CLJS-1228
                           :recompile-dependents false
                           ;;:preamble ["reagent/react.min.js"]
                           :output-to "resources/public/js/harja.js"

                           ;; Nämä voi ottaa käyttöön, jos advanced compilation buildia pitää debugata
                           :source-map "resources/public/js/harja.js.map"
                           :output-dir "resources/public/js/"

                                        ;:parallel-build false Failaa randomisti
                           :libs ["src/js/kuvataso.js"]
                           :closure-output-charset "US-ASCII"}}

               ;; Laadunseurannan buildit
               {:id "laadunseuranta-dev"
                :source-paths ["laadunseuranta/src" "laadunseuranta/cljc-src" "src/shared-cljc"]
                :figwheel true
                :compiler {:main harja-laadunseuranta.dev-core
                           :asset-path "js/compiled/dev_out"
                           :output-to "resources/public/laadunseuranta/js/compiled/harja_laadunseuranta_dev.js"
                           :output-dir "resources/public/laadunseuranta/js/compiled/dev_out"
                           :source-map-timestamp true}}

               {:id "laadunseuranta-devcards"
                :source-paths ["laadunseuranta/src" "laadunseuranta/cljc-src" "src/shared-cljc"]

                :figwheel {:devcards true}

                :compiler {:main harja-laadunseuranta.devcards-core
                           :asset-path "js/compiled/devcards_out"
                           :output-to "resources/public/laadunseuranta/js/compiled/harja_laadunseuranta_devcards.js"
                           :output-dir "resources/public/laadunseuranta/js/compiled/devcards_out"
                           :source-map-timestamp true}}

               {:id "laadunseuranta-test"
                :source-paths ["laadunseuranta/src" "laadunseuranta/cljc-src" "src/shared-cljc"
                               "laadunseuranta/test-src/cljs" "test/shared-cljs"]

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

               {:id "laadunseuranta-min"
                :source-paths ["laadunseuranta/src" "laadunseuranta/cljc-src" "src/shared-cljc"]
                :jar true
                :compiler {:output-to "resources/public/laadunseuranta/js/compiled/harja_laadunseuranta.js"
                           :output-dir "resources/public/laadunseuranta/js/compiled/out"
                           :main harja-laadunseuranta.prod-core
                           :optimizations :advanced
                           :language-in :ecmascript5
                           :language-out :ecmascript5
                           :externs ["laadunseuranta/externs.js"]
                                        ;:parallel-build false Failaa randomisti
                           :pretty-print false}}]}

  :clean-targets #^{:protect false} ["dev-resources/js/out" "target"
                                     "resources/public/js/harja.js"
                                     "resource/public/js/harja"]

  ;; Less CSS käännös tuotanto varten (dev modessa selain tekee less->css muunnoksen)
  :less {:source-paths ["dev-resources/less/application"
                        "dev-resources/less/laadunseuranta/application"]
         :target-path "resources/public/css/"}
  
  :figwheel {:server-port 3449
             :reload-clj-files false}

  ;; Palvelimen buildin tietoja
  :source-paths ["src/clj" "src/cljc" "laadunseuranta/clj-src" "laadunseuranta/cljc-src" "src/shared-cljc"]
  :test-paths ["test/clj" "laadunseuranta/test-src/clj"]
  :aot :all
  :main harja.palvelin.main
  :auto-clean false ;; for uberjar

  :test-refresh {:notify-command ["terminal-notifier" "-title" "Harja tests" "-message"]}

  ;; REPL kehitys
  :repl-options {:init-ns harja.palvelin.main
                 :init (harja.palvelin.main/-main)
                 :port 4005
                 :timeout 120000
                 :nrepl-middleware [cider.piggieback/wrap-cljs-repl]}

  

  ;; Tehdään komentoaliakset ettei build-komento jää vain johonkin Jenkins jobin konfiguraatioon
  :aliases {"tuotanto" ["do" "clean," "deps," "gitlog," "compile," "test2junit,"
                        ;; Harjan fronttibuildi ja LESS
                        "cljsbuild" "once" "prod,"
                        "less" "once,"

                        ;; Harja mobiili laadunseuranta fronttibuildi
                        "cljsbuild" "once" "laadunseuranta-min,"

                        "uberjar," "codox"]
            "testit" ["do" "clean,"
                      "deps,"
                      "test,"
                      "doo" "phantom" "test" "once,"
                      "doo" "phantom" "laadunseuranta-test" "once"]

            ;; Työkaluja, joita devaamisessa ja asiakkaalta saadun datan hieromisessa oikeaan muotoon, tarvitaan
            "elyt" ["run" "-m" "harja.tyokalut.elyt"] ;; ELY rajojen SHP file => hallintayksikkö SQL inserteiksi
            "sampo" ["run" "-m" "harja.tyokalut.sampo"] ;; SAMPO tuotelista XLS file => toimenpidekoodi SQL inserteiksi
            "gitlog" ["run" "-m" "harja.tyokalut.gitlog"] ;; tekee gitlogin resources alle

            "selainrepl" ["run" "-m" "harja.tyokalut.selainrepl"]
            "tarkista-migraatiot" ["run" "-m" "harja.tyokalut.migraatiot"]
            "tuotanto-notest" ["do" "clean," "compile,"
                               "cljsbuild" "once" "prod,"
                               "cljsbuild" "once" "laadunseuranta-min,"
                               "less" "once," "uberjar"]}
  :test-selectors { ;; lein test :perf
                   ;; :all ajaa kaikki, älä kuitenkaan laita tänne :default :all, se ei toimi :)
                   :no-perf (complement :perf)
                   :perf :perf
                   :integraatio :integraatio
                   :default (complement :integraatio)
                   }

  ;; JAI ImageIO tarvitsee MANIFEST arvoja toimiakseen
  ;; Normaalisti ne tulevat sen omasta paketista, mutta uberjar tapauksessa
  ;; ne pitää kopioida
  :manifest {"Specification-Title" "Java Advanced Imaging Image I/O Tools"
             "Specification-Version" "1.1"
             "Specification-Vendor" "Sun Microsystems, Inc."
             "Implementation-Title" "com.sun.media.imageio"
             "Implementation-Version" "1.1"
             "Implementation-Vendor" "Sun Microsystems, Inc."
             "Extension-Name" "com.sun.media.imageio"}


  ;;:doo {:paths {:phantom "phantomjs --local-storage-path=/tmp --local-storage-quota=1024 --offline-storage-path=/tmp --offline-storage-quota=1024"}}
  )

