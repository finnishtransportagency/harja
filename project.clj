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
                 [com.stuartsierra/component "1.0.0"]

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
                 [net.postgis/postgis-jdbc "2.5.0"]
                 [org.locationtech.jts/jts-core "1.16.1"]
                 [com.mchange/c3p0 "0.9.5.4"]
                 [webjure/jeesql "0.4.7"]
                 [specql "20190301"]

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

                 [reagent "0.9.1"]



                 [alandipert/storage-atom "2.0.1"]

                 [clj-time "0.15.0"]
                 [com.andrewmcveigh/cljs-time "0.5.2"]

                 ;; Kuvataso error tulee ol.source.Image inheritistä, jos päivittää neloseen
                 [cljsjs/openlayers "3.15.1"] ; TODO Voisi päivittää, mutta laadunseurannan buildi hajoaa (4.4.1-1) puuttuviin requireihin

                 ;; Microsoft dokumenttimuotojen tuki
                 [org.apache.poi/poi "4.1.0"]
                 [org.apache.poi/poi-scratchpad "4.1.0"] ;; .ppt varten
                 [org.apache.poi/poi-ooxml "4.1.0"] ;; .xlsx tiedoston lukua varten
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
                 [dk.ative/docjure "1.14.0"]

                 ;; Performance metriikat
                 [yleisradio/new-reliquary "1.1.0"]

                 ;; Tuck UI apuri
                 [webjure/tuck "0.4.1"] ; TODO Voisi päivittää, mutta 0.4.3:n kanssa tietyöilmoitukset-näkymä ei enää hae mitään kun tullaan sinne etusivun kautta

                 ;; Laadunseurantatyökalua varten
                 [org.clojure/data.codec "0.1.1"]
                 [devcards "0.2.4" :exclusions [cljsjs/react]]

                 ;; Arbitrary precision math frontilla
                 [cljsjs/big "3.1.3-1"]

                 [clj-gatling "0.13.0" :exclusions [[clj-time]]]
                 ;; Tarvitaan käännöksessä
                 [com.bhauman/figwheel-main "0.2.11"]
                 [digest "1.4.9"]]
  :managed-dependencies [[org.apache.poi/poi "4.1.0"]
                         [org.apache.poi/poi-scratchpad "4.1.0"]
                         [org.apache.poi/poi-ooxml "4.1.0"]]
  :profiles {:dev {:test2junit-run-ant ~(not jenkinsissa?)}}

  :jvm-opts ^:replace ["-Xms256m" "-Xmx2g"]

  :repositories [["osgeo" "https://repo.osgeo.org/repository/release/"]
                 ["atlassian" "https://maven.atlassian.com/content/repositories/atlassian-public/"]
                 ;; Tämä on tässä [org.clojure/data.zip "0.1.4-SNAPSHOT"] dependencyn takia
                 ;; ["sonatype-snapshots" "https://oss.sonatype.org/content/repositories/snapshots/"]
                 ]

  :plugins [[lein-cljsbuild "1.1.7"]
            [lein-less "1.7.5"]
            [lein-ancient "0.6.15"]
            [lein-codox "0.10.6"]
            [jonase/eastwood "0.3.5"]
            [lein-auto "0.1.2"]
            [lein-pdo "0.1.1"]
            [lein-doo "0.1.10"]]

  ;; Näitä cljsbuild tarvitsee testaamista varten doo:n kanssa.
  :cljsbuild {:builds [{:id "test"
                        :source-paths ["src/cljs" "src/cljc" "src/cljs-dev" "src/shared-cljc"
                                       "test/cljs" "test/doo" "test/shared-cljs" "test/cljc"]
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
                                     :provides ["epsg3067"]}]}}]}


  :clean-targets ^{:protect false} ["dev-resources/js/out"
                                    "dev-resources/js/harja.js"
                                    "dev-resources/tmp"
                                    "target"
                                    "resources/public/js/harja.js"
                                    "resource/public/js/harja"]

  ;; Less CSS käännös tuotanto varten (dev modessa selain tekee less->css muunnoksen)
  :less {:source-paths ["dev-resources/less/application"
                        "dev-resources/less/laadunseuranta/application"]
         :target-path "resources/public/css/"}

  ;; Palvelimen buildin tietoja
  :source-paths ["src/clj" "src/cljc" "laadunseuranta/clj-src" "laadunseuranta/cljc-src" "src/shared-cljc"]
  :test-paths ["test/clj" "test/cljc" "laadunseuranta/test-src/clj"]
  :aot :all
  :main harja.palvelin.main
  :auto-clean false ;; for uberjar

  :test-refresh {:notify-command ["terminal-notifier" "-title" "Harja tests" "-message"]}
  

  ;; Tehdään komentoaliakset ettei build-komento jää vain johonkin Jenkins jobin konfiguraatioon
  :aliases {"fig" ["trampoline" "with-profile" "+dev-ymparisto" "with-env-vars" "run" "-m" "figwheel.main"]
            "build-dev" ["with-profile" "+dev-ymparisto" "with-env-vars" "run" "-m" "figwheel.main" "-b" "figwheel_conf/dev" "-r"]
            "compile-dev" ["with-profile" "+dev-ymparisto" "with-env-vars" "compile"]
            "repl-dev" ["with-profile" "+dev-ymparisto" "with-env-vars" "repl"]
            "compile-prod" ["run" "-m" "figwheel.main" "-O" "advanced" "-fw" "false" "-bo" "figwheel_conf/prod"]
            "compile-laadunseuranta-dev" ["run" "-m" "figwheel.main" "-O" "advanced" "-fw" "false" "-bo" "figwheel_conf/laadunseuranta-dev"]
            "compile-laadunseuranta-prod" ["run" "-m" "figwheel.main" "-O" "advanced" "-fw" "false" "-bo" "figwheel_conf/laadunseuranta-prod"]
            "tuotanto" ["do" "clean," "deps," "gitlog," "compile," "test2junit,"
                        ;; Harjan fronttibuildi ja LESS
                        "less" "once,"
                        "with-profile" "prod-cljs" "compile-prod,"

                        ;; Harja mobiili laadunseuranta fronttibuildi
                        "with-profile" "laadunseuranta-prod" "compile-laadunseuranta-prod,"

                        "uberjar," "codox"]
            "testit" ["do" "clean,"
                      "deps,"
                      "test,"
                      "with-profile" "+test" "doo" "phantom" "test" "once,"
                      "with-profile" "+test" "doo" "phantom" "laadunseuranta-test" "once"]

            ;; Työkaluja, joita devaamisessa ja asiakkaalta saadun datan hieromisessa oikeaan muotoon, tarvitaan
            "elyt" ["run" "-m" "harja.tyokalut.elyt"] ;; ELY rajojen SHP file => hallintayksikkö SQL inserteiksi
            "sampo" ["run" "-m" "harja.tyokalut.sampo"] ;; SAMPO tuotelista XLS file => toimenpidekoodi SQL inserteiksi
            "gitlog" ["run" "-m" "harja.tyokalut.gitlog"] ;; tekee gitlogin resources alle

            "selainrepl" ["run" "-m" "harja.tyokalut.selainrepl"]
            "tarkista-migraatiot" ["run" "-m" "harja.tyokalut.migraatiot"]
            "tuotanto-notest" ["do" "clean," "compile,"
                               "less" "once,"
                               "with-profile" "prod-cljs" "compile-prod,"
                               "with-profile" "laadunseuranta-prod" "compile-laadunseuranta-prod,"
                               "uberjar"]}
  :test-selectors {;; lein test :perf
                   ;; :all ajaa kaikki, älä kuitenkaan laita tänne :default :all, se ei toimi :)
                   :no-perf (complement :perf)
                   :perf :perf
                   :integraatio :integraatio
                   :hidas :hidas
                   :default (fn [m]
                              (let [testit-joita-ei-ajeta #{:integraatio :hidas}]
                                (nil? (some #(true? (val %)) (select-keys m testit-joita-ei-ajeta)))))
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

