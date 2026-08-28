(def jenkinsissa? (= "harja-jenkins.solitaservices.fi"
                    (.getHostName (java.net.InetAddress/getLocalHost))))

(defproject harja "0.0.1-SNAPSHOT"
  :description "Väylän Harja"

  ;; Mitä tehdään versio-konfliktien tapahtuessa riippuvuuspuussa?
  ;; https://github.com/technomancy/leiningen/blob/24fb93936133bd7fc30c393c127e9e69bb5f2392/sample.project.clj#L82
  ;; Muuta asetusta, jos haluat nähdä varoitukset riippuvuuksien konflikteista
  :pedantic? false
  :dependencies [[org.clojure/clojure "1.10.3"]
                 [org.clojure/clojurescript "1.10.764"]
                 [org.clojure/spec.alpha "0.6.249"]

                 ;;;;;;; Yleiset ;;;;;;;

                 [prismatic/schema "1.4.1"]
                 [org.clojure/core.async "1.7.701"]
                 ;; Transit tietomuoto asiakkaan ja palvelimen väliseen kommunikointiin
                 [com.cognitect/transit-cljs "0.8.280"]
                 [com.cognitect/transit-clj "1.1.363"]
                 ;; Pätevä yksinkertainen työkalu esimerkiksi config-tiedostojen mergeämiseen
                 [meta-merge "1.0.0"]

                 ;; Exif-kirjasto kuvien metadatan lukemiseen
                 [cljsjs/exif "2.1.1-1"]

                 ;;;;;;; Palvelin ;;;;;;;

                 ;; Komponenttituki palvelimen komponenttien hallintaan
                 [com.stuartsierra/component "1.2.0"]

                 ;; -- Lokitus / assertointi / virheiden käsittely
                 ;;   Taoensson kirjastoissa täytyy ottaa huomioon, että ne käyttävät yhdessä Encore ja Truss -kirjastoja
                 ;;   Näistä voi tulla konflikteja, jotka täytyy ottaa huomioon: https://www.taoensso.com/dependency-conflicts

                 ;; Clojure ja ClojureScript assertointi
                 [com.taoensso/truss "2.3.0"]

                 ;; Lokitus
                 [com.taoensso/timbre "6.8.0"]

                 ;; Figwheel tarvitsee log4j-coren
                 [org.apache.logging.log4j/log4j-core "2.26.1"]

                 ;; --

                 ;; -- Metriikkadata
                 [org.clojure/java.jmx "1.1.1"]

                 ;; -- JSON encode/decode
                 [cheshire "6.2.0"]

                 ;; -- HTTP palvelin, reititys ja kyselyiden cahetus
                 [cljs-http "0.1.49"]
                 [http-kit "2.8.1"]
                 ;; Compojure päivittää ring versioita liian hitaasti, joten hallitaan niitä itse
                 ;; Varmista, että ring versiot ovat yhteensopivia niitä käyttävien kirjastojen kanssa
                 [ring/ring-codec "1.3.0"]
                 [ring/ring-core "1.15.5"]
                 [compojure "1.7.2"]
                 [hiccup "1.0.5"]

                 [org.clojure/core.cache "1.2.263"]
                 [org.clojure/core.memoize "1.2.281"]

                 ;; Pattern match kirjasto
                 [org.clojure/core.match "1.1.1"]

                 ;; Todennus / kirjautumisen allekirjoituksen varmistus 
                 ;; Täältä tulee java kirjaston kautta jwt signaturen vahvistus, joka tehdään käyttäjän tullessa Harjaan
                 [buddy/buddy-sign "3.6.1-359"]


                 ;; -- Tietokanta: ajuri, kirjastot ja -migraatiot --
                 ;; Ajuria päivittäessä, muista päivittää myös pom.xml, koska flyway käyttää sitä ajurin versiota
                 [org.postgresql/postgresql "42.7.13"]
                 [net.postgis/postgis-jdbc "2025.1.1"]
                 [org.locationtech.jts/jts-core "1.20.0"]
                 ;; cp3p0 on tietokantayhteyksien hallintaan
                 [com.mchange/c3p0 "0.14.1"]
                 ;; Jeesql ja specql ovat SQL-kyselyjen generointiin
                 [webjure/jeesql "0.4.7"]
                 [io.github.tatut/specql "20240920" :exclusions [org.clojure/java.jdbc]]

                 ;; -- GeoTools kirjastot geospatiaalisten tietojen käsittelyyn
                 [org.geotools/gt-shapefile "35.1" :exclusions [org.eclipse.emf/org.eclipse.emf.common org.eclipse.emf/org.eclipse.emf.ecore]]
                 [org.geotools/gt-process-raster "35.1" :exclusions [org.eclipse.emf/org.eclipse.emf.common org.eclipse.emf/org.eclipse.emf.ecore com.google.guava/guava]]
                 [org.geotools/gt-epsg-wkt "35.1" :exclusions [org.eclipse.emf/org.eclipse.emf.common org.eclipse.emf/org.eclipse.emf.ecore]] ;; EPSG koordinaatistot
                 [org.geotools/gt-swing "35.1" :exclusions [org.eclipse.emf/org.eclipse.emf.common org.eclipse.emf/org.eclipse.emf.ecore com.google.guava/guava]] ;; just for experimentation, remove when no longer needed

                 ;; -- XML zipper XML-tietorakenteiden käsittelyyn
                 [org.clojure/data.zip "0.1.1"] ;; Jos päivittää uusimpaan, aiheuttaa parsintaongelmia https://dev.clojure.org/jira/browse/DZIP-6

                 ;; -- Kirjasto mappien avainten nimiavaruuksien käsittelyyn
                 [namespacefy "0.5.0"]

                 ;; -- Sähköposti lähetys
                 [org.apache.httpcomponents/httpcore "4.4.16"]
                 [org.apache.httpcomponents/httpmime "4.5.14" :exclusions [org.clojure/clojure commons-codec commons-logging org.apache.httpcomponents/httpcore]]
                 [com.draines/postal "2.0.5"]

                 ;; -- JMS-jonot (esim. tieliikenneilmoitukset)
                 [org.apache.activemq/activemq-client "5.19.10" :exclusions [org.slf4j/slf4j-api]]


                 ;; Ajax-kirjasto frontille
                 [cljs-ajax "0.8.4"]

                 ;; React-wrapper frontille
                 [reagent "1.1.1"]
                 [cljsjs/react "17.0.2-0"]
                 [cljsjs/react-dom "17.0.2-0"]


                 ;; Local-storage apuri frontille
                 [alandipert/storage-atom "2.0.1"]

                 ;; -- Aika- ja päivämääräkäsittely
                 [clj-time "0.15.2"]
                 [com.andrewmcveigh/cljs-time "0.5.2"]

                 ;; -- Karttatasot front-end
                 ;; TODO: Päivitys suurempiin versioihin vaatii siirtymisen shadow-cljs:ään
                 [cljsjs/openlayers "4.4.1-1"]

                 ;; Microsoft dokumenttimuotojen tuki
                 [org.apache.poi/poi "5.5.1"]
                 [org.apache.poi/poi-scratchpad "5.5.1"] ;; .ppt varten
                 [org.apache.poi/poi-ooxml "5.5.1"] ;; .xlsx tiedoston lukua varten
                 [org.clojure/data.json "2.5.2"]

                 ;; Chime -ajastuskirjasto periodisten tehtävien suorittamiseen
                 [jarohen/chime "0.2.2"]

                 ;; Pikkukuvien (thumbnail) muodostamiseen
                 [net.coobird/thumbnailator "0.4.21"]

                 ;; JSON -validointikirjastot
                 [webjure/json-schema "0.7.4"]

                 ;; Slingshot -kirjasto poikkeusten käsittelyyn
                 [slingshot "0.12.2"]

                 ;; PDF:n generointi
                 [org.apache.xmlgraphics/fop "2.11"]

                 ;; Kevyt Java 11 java.net.http wrapper WebSocket-testaukseen
                 [java-http-clj "0.4.3"]

                 ;; Apache ANT core (arkistoiden purku yms. org.apache.tools.tar)
                 [org.apache.ant/ant "1.10.17"]

                 ;; Apache POI wrapper (Excel yms lukemiseen)
                 [dk.ative/docjure "1.22.0"]

                 ;; -- Front-end tilan hallinta
                 [webjure/tuck "20181204"]
                 [webjure/tuck-remoting "20190213" :exclusions [webjure/tuck figwheel]]

                 ;; Arbitrary precision math frontilla
                 [cljsjs/big "3.1.3-1"]

                 ;; Digest-algoritmeja (md5, sha-256, ...)
                 [org.clj-commons/digest "1.4.100"]

                 ;; data.xml tarvitaan mm. XML-tiedostojen parsimiseen ja pretty-printtaukseen
                 [org.clojure/data.xml "0.0.8"]]

  :managed-dependencies [[org.apache.poi/poi "5.5.1"]
                         [org.apache.poi/poi-scratchpad "5.5.1"]
                         [org.apache.poi/poi-ooxml "5.5.1"]
                         ;; Ratkaise: CVE-2024-26308 ja CVE-2024-25710
                         ;;  Päivitetään POI-ooxml mukana tullut transitiivinen kirjasto, joka sisältää korjauksen haavoittuvuuksiin.
                         ;;  (POI-ooxml ei kuitenkaan käytä haavoittuneen kirjaston version riskialtista osaa)
                         ;;  TODO: Tämä muutos voidaan poistaa, kunhan poi-ooxml ottaa mukaan uudemman version kirjastosta.
                         [org.apache.commons/commons-compress "1.28.0"]

                         ;; Ratkaise: https://security.snyk.io/vuln/SNYK-JAVA-COMMONSCODEC-561518
                         ;;   Pakotetaan commons-codec korkeampaan versioon
                         [commons-codec "1.22.1"]
                         ;; jackson-core tulee gt-shapefilen mukana (versio 3.1.2, jossa haavoittuvuus) uudempaa ei ole tarjolla. Joten niin pakotetaan se uudempi mukaan.
                         [tools.jackson.core/jackson-core "3.2.1"]
                         [com.fasterxml.jackson.core/jackson-core "2.21.4"]
                         ;; uudemmassa org.clojure/clojurescript voisi saada myös tähän päivityksen - Eli tarkista tämä kun clojurescript päivitetään
                         [com.google.code.gson/gson "2.14.0"]]

  :profiles {:uberjar {:aot :all}
             :dev {:test2junit-run-ant ~(not jenkinsissa?)}}

  :jvm-opts ^:replace ["-Xms256m" "-Xmx2g"]

  :repositories [["osgeo-geotools" "https://repo.osgeo.org/repository/geotools-releases/"]
                 ["osgeo" "https://repo.osgeo.org/repository/release/"]
                 ["atlassian" "https://maven.atlassian.com/content/repositories/atlassian-public/"]
                 ;; Tämä on tässä [org.clojure/data.zip "0.1.4-SNAPSHOT"] dependencyn takia
                 ;; ["sonatype-snapshots" "https://oss.sonatype.org/content/repositories/snapshots/"]
                 ]

  :plugins [[lein-cljsbuild "1.1.8"]
            [lein-ancient "1.0.0"]
            [lein-codox "0.10.8" :exclusions [org.clojure/clojure]]
            [lein-auto "0.1.3"]
            [lein-doo "0.1.11" :exclusions [org.clojure/clojure]]]

  ;; Näitä cljsbuild tarvitsee testaamista varten doo:n kanssa.
  :cljsbuild {:builds [{:id "test"
                        :source-paths ["src/clj" "src/cljs" "src/cljc" "src/cljs-dev" "src/shared-cljc"
                                       "test/cljs" "test/doo" "test/shared-cljs" "test/cljc"]
                        :compiler {:output-to "target/cljs/test/test.js"
                                   :output-dir "target/cljs/test"
                                   :optimizations :none
                                   :pretty-print true
                                   :source-map true
                                   ;:parallel-build false Failaa randomisti
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
                                    "resources/public/css"
                                    "resources/public/js/out"
                                    "resources/public/js/harja"
                                    "resources/public/js/harja.js"
                                    "resources/public/laadunseuranta/js/compiled/out"]

  ;; Palvelimen buildin tietoja
  :source-paths ["src/clj" "src/cljc" "laadunseuranta/clj-src" "laadunseuranta/cljc-src" "src/shared-cljc"]
  :test-paths ["test/clj" "test/cljc" "laadunseuranta/test-src/clj"]
  ;;     aot == Ahead of time compilation
  ;;     "..source code is compiled before the program is run, rather than at runtime"
  ;; Laitetaan tämä pois päältä harja main käynnistykseen
  ;; Backend ei aina käynnisty ensimmäisellä yrityksellä cleanin jälkeen, tämä korjaa tuon sirpaleisuuden
  :main ^:skip-aot harja.palvelin.main
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
                        "with-profile" "+prod-cljs" "compile-prod,"
                        ;; Harja mobiili laadunseuranta fronttibuildi
                        "with-profile" "+laadunseuranta-prod" "compile-laadunseuranta-prod,"
                        "uberjar," "codox"]
            "testit" ["do" "clean,"
                      "deps,"
                      "test,"
                      "with-profile" "+phantomjs" "doo" "phantom" "test" "once,"
                      "with-profile" "+phantomjs" "doo" "phantom" "laadunseuranta-test" "once"]

            ;; Työkaluja, joita devaamisessa ja asiakkaalta saadun datan hieromisessa oikeaan muotoon, tarvitaan
            "elyt" ["run" "-m" "harja.tyokalut.elyt"] ;; ELY rajojen SHP file => hallintayksikkö SQL inserteiksi
            "sampo" ["run" "-m" "harja.tyokalut.sampo"] ;; SAMPO tuotelista XLS file => toimenpidekoodi SQL inserteiksi
            "gitlog" ["run" "-m" "harja.tyokalut.gitlog"] ;; tekee gitlogin resources alle
            "selainrepl" ["run" "-m" "harja.tyokalut.selainrepl"]
            "tarkista-migraatiot" ["run" "-m" "harja.tyokalut.migraatiot"]
            "tuotanto-notest" ["do" "clean," "compile,"
                               "with-profile" "+prod-cljs" "compile-prod,"
                               "with-profile" "+laadunseuranta-prod" "compile-laadunseuranta-prod,"
                               "uberjar"]}
  :test-selectors {;; lein test :perf
                   ;; :all ajaa kaikki, älä kuitenkaan laita tänne :default :all, se ei toimi :)
                   :no-perf (complement :perf)
                   :perf :perf
                   :integraatio :integraatio
                   :hidas :hidas
                   :ohita :ohita
                   :default (fn [m]
                              (let [testit-joita-ei-ajeta #{:integraatio :hidas :ohita}]
                                (nil? (some #(true? (val %)) (select-keys m testit-joita-ei-ajeta)))))
                   ;; Shard-selektorit backend CI-testejä varten:
                   ;; Jaetaan :default-selektorin läpäisevät testit namespacen nimen hashin perusteella kahteen osaan
                   ;; rinnakkaista backend-testien ajoa varten (ks. reusable_run_app_tests.yml).
                   ;; HUOM: näiden pitää olla itsenäisiä (fn [m] ...) -literaaleja, koska lein test evaluoi
                   ;; test-selectors-arvot uudelleen erillisessä aliprosessissa, jossa project.clj:n
                   ;; muut top-level-määrittelyt (esim. defn-) eivät ole käytettävissä.
                   :shard-1 (fn [m]
                              (let [testit-joita-ei-ajeta #{:integraatio :hidas :ohita}]
                                (and (nil? (some #(true? (val %)) (select-keys m testit-joita-ei-ajeta)))
                                     (= 0 (mod (hash (str (:ns m))) 2)))))
                   :shard-2 (fn [m]
                              (let [testit-joita-ei-ajeta #{:integraatio :hidas :ohita}]
                                (and (nil? (some #(true? (val %)) (select-keys m testit-joita-ei-ajeta)))
                                     (= 1 (mod (hash (str (:ns m))) 2)))))}

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
