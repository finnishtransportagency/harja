(def jenkinsissa? (= "harja-jenkins.solitaservices.fi"
                     (.getHostName (java.net.InetAddress/getLocalHost))))

(defproject harja "0.0.1-SNAPSHOT"
  :description "Väylän Harja"

  ;; Mitä tehdään versio-konfliktien tapahtuessa riippuvuuspuussa?
  ;; https://github.com/technomancy/leiningen/blob/24fb93936133bd7fc30c393c127e9e69bb5f2392/sample.project.clj#L82
  ;; Muuta asetusta, jos haluat nähdä varoitukset riippuvuuksien konflikteista
  :pedantic? false
  :dependencies [
                 ;; Clojure ja ClojureScript
                 [org.clojure/clojure "1.10.3"]
                 [org.clojure/clojurescript "1.10.520"]
                 [org.clojure/spec.alpha "0.2.176"]

                 ;;;;;;; Yleiset ;;;;;;;

                 [prismatic/schema "1.1.10"]
                 [org.clojure/core.async "0.3.443"]
                 ;; Transit tietomuoto asiakkaan ja palvelimen väliseen kommunikointiin
                 [com.cognitect/transit-cljs "0.8.280"]
                 [com.cognitect/transit-clj "0.8.319"]
                 ;; Pätevä yksinkertainen työkalu esimerkiksi config-tiedostojen mergeämiseen
                 [meta-merge "1.0.0"]

                 ;; Exif-kirjasto kuvien metadatan lukemiseen
                 [cljsjs/exif "2.1.1-1"]

                 ;;;;;;; Palvelin ;;;;;;;

                 ;; Komponenttituki palvelimen komponenttien hallintaan
                 [com.stuartsierra/component "1.1.0"]

                 ;; -- Lokitus
                 [com.taoensso/timbre "5.2.1"]
                 [org.apache.logging.log4j/log4j-core "2.22.1"]

                 ;; -- Metriikkadata
                 [org.clojure/java.jmx "0.3.4"]

                 ;; -- JSON encode/decode
                 [cheshire "5.13.0"]

                 ;; -- HTTP palvelin, reititys ja kyselyiden cahetus
                 [cljs-http "0.1.48"]
                 [http-kit "2.8.0"]
                 [compojure "1.7.1"]
                 [hiccup "1.0.5"]

                 [org.clojure/core.cache "0.7.2"]
                 [org.clojure/core.memoize "1.0.257"]

                 ;; Pattern match kirjasto
                 [org.clojure/core.match "1.0.0"]


                 ;; -- Tietokanta: ajuri, kirjastot ja -migraatiot --
                 ;; Ajuria päivittäessä, muista päivittää myös pom.xml, koska flyway käyttää sitä ajurin versiota
                 [org.postgresql/postgresql "42.7.4"]
                 [net.postgis/postgis-jdbc "2.5.0"]
                 [org.locationtech.jts/jts-core "1.19.0"]
                 ;; cp3p0 on tietokantayhteyksien hallintaan
                 [com.mchange/c3p0 "0.9.5.4"]
                 ;; Jeesql ja specql ovat SQL-kyselyjen generointiin
                 [webjure/jeesql "0.4.7"]
                 [io.github.tatut/specql "20230316" :exclusions [org.clojure/java.jdbc]]

                 ;; -- GeoTools kirjastot geospatiaalisten tietojen käsittelyyn
                 [org.geotools/gt-shapefile "29.1" :exclusions [org.eclipse.emf/org.eclipse.emf.common org.eclipse.emf/org.eclipse.emf.ecore]]
                 [org.geotools/gt-process-raster "29.1" :exclusions [org.eclipse.emf/org.eclipse.emf.common org.eclipse.emf/org.eclipse.emf.ecore com.google.guava/guava]]
                 [org.geotools/gt-epsg-wkt "29.1" :exclusions [org.eclipse.emf/org.eclipse.emf.common org.eclipse.emf/org.eclipse.emf.ecore]] ;; EPSG koordinaatistot
                 [org.geotools/gt-swing "29.1" :exclusions [org.eclipse.emf/org.eclipse.emf.common org.eclipse.emf/org.eclipse.emf.ecore com.google.guava/guava]] ;; just for experimentation, remove when no longer needed

                 ;; -- XML zipper XML-tietorakenteiden käsittelyyn
                 [org.clojure/data.zip "0.1.1"] ;; Jos päivittää uusimpaan, aiheuttaa parsintaongelmia https://dev.clojure.org/jira/browse/DZIP-6

                 ;; -- Kirjasto mappien avainten nimiavaruuksien käsittelyyn
                 [namespacefy "0.5.0"]

                 ;; -- Sähköposti lähetys
                 [org.apache.httpcomponents/httpcore "4.4.14"]
                 [org.apache.httpcomponents/httpmime "4.5.13" :exclusions [org.clojure/clojure commons-codec commons-logging org.apache.httpcomponents/httpcore]]
                 [com.draines/postal "2.0.5"]

                 ;; -- JMS-jonot (esim. tieliikenneilmoitukset)
                 [org.apache.activemq/activemq-client "5.18.3" :exclusions [org.slf4j/slf4j-api]]


                 ;; Ajax-kirjasto frontille
                 [cljs-ajax "0.8.4"]

                 ;; React-wrapper frontille
                 [reagent "0.9.1"]


                 ;; Local-storage apuri frontille
                 [alandipert/storage-atom "2.0.1"]

                 ;; -- Aika- ja päivämääräkäsittely
                 [clj-time "0.15.2"]
                 [com.andrewmcveigh/cljs-time "0.5.2"]

                 ;; -- Karttatasot front-end
                 ;; Kuvataso error tulee ol.source.Image inheritistä, jos päivittää neloseen
                 ;; TODO: Päivitys vaatii siirtymisen shadow-cljs:ään
                 [cljsjs/openlayers "3.15.1"] ; TODO Voisi päivittää, mutta laadunseurannan buildi hajoaa (4.4.1-1) puuttuviin requireihin

                 ;; Microsoft dokumenttimuotojen tuki
                 [org.apache.poi/poi "5.2.5"]
                 [org.apache.poi/poi-scratchpad "5.2.5"] ;; .ppt varten
                 [org.apache.poi/poi-ooxml "5.2.5"] ;; .xlsx tiedoston lukua varten
                 [org.clojure/data.json "0.2.6"]

                 ;; Chime -ajastuskirjasto periodisten tehtävien suorittamiseen
                 [jarohen/chime "0.2.2"]

                 ;; Pikkukuvien (thumbnail) muodostamiseen
                 [net.coobird/thumbnailator "0.4.20"]

                 ;; JSON -validointikirjastot
                 [webjure/json-schema "0.7.4"]

                 ;; Slingshot -kirjasto poikkeusten käsittelyyn
                 [slingshot "0.12.2"]

                 ;; PDF:n generointi
                 [org.apache.xmlgraphics/fop "2.9"]

                 ;; Kevyt Java 11 java.net.http wrapper WebSocket-testaukseen
                 [java-http-clj "0.4.3"]

                 ;; Apache ANT core (arkistoiden purku yms. org.apache.tools.tar)
                 [org.apache.ant/ant "1.10.15"]

                 ;; Clojure(Script) assertointi
                 [com.taoensso/truss "1.5.0"]

                 ;; Apache POI wrapper (Excel yms lukemiseen)
                 [dk.ative/docjure "1.19.0"]

                 ;; Performance metriikat
                 ;; TODO: Pilvisiirtymän jälkeen poistetaan tämä riippuvuus
                 [yleisradio/new-reliquary "1.1.0"]

                 ;; -- Front-end tilan hallinta
                 [webjure/tuck "0.4.4"]
                 [webjure/tuck-remoting "20190213" :exclusions [webjure/tuck]]

                 ;; Arbitrary precision math frontilla
                 [cljsjs/big "3.1.3-1"]

                 ;; Digest-algoritmeja (md5, sha-256, ...)
                 [org.clj-commons/digest "1.4.100"]

                 ;; data.xml tarvitaan mm. XML-tiedostojen parsimiseen ja pretty-printtaukseen
                 [org.clojure/data.xml "0.0.8"]]

  :managed-dependencies [[org.apache.poi/poi "5.2.5"]
                         [org.apache.poi/poi-scratchpad "5.2.5"]
                         [org.apache.poi/poi-ooxml "5.2.5"]
                         ;; Ratkaise: CVE-2024-26308 ja CVE-2024-25710
                         ;;  Päivitetään POI-ooxml mukana tullut transitiivinen kirjasto, joka sisältää korjauksen haavoittuvuuksiin.
                         ;;  (POI-ooxml ei kuitenkaan käytä haavoittuneen kirjaston version riskialtista osaa)
                         ;;  TODO: Tämä muutos voidaan poistaa, kunhan poi-ooxml ottaa mukaan uudemman version kirjastosta.
                         [org.apache.commons/commons-compress "1.26.1"]

                         ;; Ratkaise: https://security.snyk.io/vuln/SNYK-JAVA-COMMONSCODEC-561518
                         ;;   Pakotetaan commons-codec korkeampaan versioon
                         [commons-codec "1.16.1"]]

  :profiles {:dev {:test2junit-run-ant ~(not jenkinsissa?)}}

  :jvm-opts ^:replace ["-Xms256m" "-Xmx2g"]

  :repositories [["osgeo-geotools" "https://repo.osgeo.org/repository/geotools-releases/"]
                 ["osgeo" "https://repo.osgeo.org/repository/release/"]
                 ["atlassian" "https://maven.atlassian.com/content/repositories/atlassian-public/"]
                 ;; Tämä on tässä [org.clojure/data.zip "0.1.4-SNAPSHOT"] dependencyn takia
                 ;; ["sonatype-snapshots" "https://oss.sonatype.org/content/repositories/snapshots/"]
                 ]

  :plugins [[lein-cljsbuild "1.1.8"]
            ;; TODO: Pilvisiirtymän jälkeen poistetaan lein-less riippuvuus
            ;; Harjan pilviversiossa on luovuttu lein-lessistä, mutta on-prem harjassa
            ;; käytetään sitä vielä. Riippuvuus ja käyttö aliaksissa voidaan poistaa, kun
            ;; ollaan luovuttu on-premistä ja jenkinsin käytöstä.
            [lein-less "1.7.5"]
            [lein-ancient "0.7.0"]
            [lein-codox "0.10.8" :exclusions [org.clojure/clojure]]
            [lein-auto "0.1.3"]
            [lein-doo "0.1.11" :exclusions [org.clojure/clojure]]]

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
                                   :main harja.runner}}
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
                                    "resources/public/js/harja"]

  ;; Less CSS käännös tuotanto varten, käyttäen lein-lessiä on-prem-harjaa varten.
  :less {:source-paths ["dev-resources/less/application"
                        "dev-resources/less/laadunseuranta/application"]
         :target-path "resources/public/css/"}

  ;; Palvelimen buildin tietoja
  :source-paths ["src/clj" "src/cljc" "laadunseuranta/clj-src" "laadunseuranta/cljc-src" "src/shared-cljc"]
  :test-paths ["test/clj" "test/cljc" "laadunseuranta/test-src/clj"]
  :aot :all
  :main harja.palvelin.main
  :auto-clean false ;; for uberjar

  ;; Tehdään komentoaliakset ettei build-komento jää vain johonkin Jenkins jobin konfiguraatioon
  :aliases {"fig" ["trampoline" "with-profile" "+dev-ymparisto" "with-env-vars" "run" "-m" "figwheel.main"]
            "build-dev" ["with-profile" "+dev-ymparisto" "with-env-vars" "run" "-m" "figwheel.main" "-b" "figwheel_conf/dev" "-r"]
            "build-dev-no-env" ["run" "-m" "figwheel.main" "-b" "figwheel_conf/dev" "-r"]
            "compile-dev" ["with-profile" "+dev-ymparisto" "with-env-vars" "compile"]
            "repl-dev" ["with-profile" "+dev-ymparisto" "with-env-vars" "repl"]
            "compile-prod" ["run" "-m" "figwheel.main" "-O" "advanced" "-fw" "false" "-bo" "figwheel_conf/prod"]
            "compile-laadunseuranta-dev" ["run" "-m" "figwheel.main" "-O" "advanced" "-fw" "false" "-bo" "figwheel_conf/laadunseuranta-dev"]
            "compile-laadunseuranta-prod" ["run" "-m" "figwheel.main" "-O" "advanced" "-fw" "false" "-bo" "figwheel_conf/laadunseuranta-prod"]
            "tuotanto" ["do" "clean," "deps," "gitlog," "compile," "test2junit,"
                        ;; Harjan fronttibuildi ja LESS
                        "less" "once,"
                        "with-profile" "+prod-cljs" "compile-prod,"

                        ;; Harja mobiili laadunseuranta fronttibuildi
                        "with-profile" "+laadunseuranta-prod" "compile-laadunseuranta-prod,"

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
                               "with-profile" "+prod-cljs" "compile-prod,"
                               "with-profile" "+laadunseuranta-prod" "compile-laadunseuranta-prod,"
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

