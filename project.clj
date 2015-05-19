(defproject harja "0.0.1-SNAPSHOT"
  :description "Liikenneviraston Harja"
  
  :dependencies [[org.clojure/clojure "1.7.0-beta1"] ; siirrytään 1.7.0 heti kun valmis
                 [org.clojure/clojurescript "0.0-3196"]

                 ;;;;;;; Yleiset ;;;;;;;
                 [prismatic/schema "0.4.0"]
                 [org.clojure/core.async "0.1.346.0-17112a-alpha"]

                 ;; Transit tietomuoto asiakkaan ja palvelimen väliseen kommunikointiin
                 [com.cognitect/transit-cljs "0.8.207"]
                 [com.cognitect/transit-clj "0.8.271"]
                 
                 ;;;;;;; Palvelin ;;;;;;;

                 ;; Komponenttituki
                 [com.stuartsierra/component "0.2.3"]
                 
                 ;; Lokitus
                 ;;[org.clojure/tools.logging "0.3.1"]
                 ;;[ch.qos.logback/logback-classic "1.1.3"]
                 [com.taoensso/timbre "3.4.0"]
                 [com.narkisr/gelfino-client "0.7.0"]

                 ;; HTTP palvelin ja reititys
                 [http-kit "2.1.19"]
                 [compojure "1.3.3"]
                 [hiccup "1.0.5"]

                 [org.clojure/core.cache "0.6.4"]
                 
                 ;; Tietokanta: ajuri, kirjastot ja -migraatiot
                 [org.postgresql/postgresql "9.3-1102-jdbc41"]
                 [clojunauts/postgis-jdbc "2.1.0SVN"]
                 ;;[org.postgis/postgis-jdbc "2.1.4dev"] ;; mvnrepossa vain 1.3.3 versio, piti buildata itse!
                 [com.mchange/c3p0 "0.9.5"]
                 [yesql "0.4.0" :exclusions [[instaparse :classifier "*"]]]
                 [instaparse "1.3.6"]

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

                 ;; Sähköpostin lähetys
                 [com.draines/postal "1.11.3"]
                 
                 ;; Asiakas
                 [spyscope "0.1.5"]
                 [spellhouse/clairvoyant "0.0-48-gf5e59d3"]
                 [binaryage/devtools "0.2.0"] ;; cljs data chrome inspectoriin
                 
                 [cljs-ajax "0.3.11"]
                 ;;[lively "0.2.0"]
                 [figwheel "0.2.7"]

                 [reagent "0.5.0" :exclusions [[cljsjs/react :classifier "*"]]]
                 [cljsjs/react-with-addons "0.13.1-0"]
                 
                 [org.clojure/core.async "0.1.346.0-17112a-alpha"]

                 [com.andrewmcveigh/cljs-time "0.3.3"]

                 ;; Microsoft dokumenttimuotojen tuki
                 [org.apache.poi/poi "3.11"] ;; siirrä oikeisiin depseihin, kun tarvitaan XLS export feature
                 [org.apache.poi/poi-scratchpad "3.11"] ;; .ppt varten
                 [org.apache.poi/poi-ooxml "3.11"] ;; .xlsx tiedoston lukua varten
                 [org.clojure/data.json "0.2.6"]

                 
                 ]
  
  :dev-dependencies [;; Selain REPL
                     
                     ;; Testaus
                     

                     ]
  
  
  :profiles {:dev {:dependencies []
                   :plugins [[com.jakemccrary/lein-test-refresh "0.9.0"]]}
             :test {:dependencies [[clj-webdriver "0.6.0"]
                                   [org.seleniumhq.selenium/selenium-java "2.44.0"]
                                   [org.seleniumhq.selenium/selenium-firefox-driver "2.44.0"]]}}
    
  :repositories [["osgeo" "http://download.osgeo.org/webdav/geotools/"]  ;; FIXME: move artifacts to mvn.solita.fi
                 ["solita" "http://mvn.solita.fi/archiva/repository/solita/"]
                 ]

  
  :plugins [[lein-cljsbuild "1.0.5"]
            [cider/cider-nrepl "0.8.2"]
            [lein-less "1.7.2"]
            [lein-ancient "0.5.5"]
            [lein-figwheel "0.2.7"]
            [codox "0.8.11"]
            ;;[mvxcvi/whidbey "0.5.1"]
            ]  ;; Asiakaspuolen cljs buildin tietoja
  :cljsbuild {
              :builds [{:id "dev"
                        :source-paths ["src/cljs" "src/cljc" "src/cljs-dev" "test/cljs"]
                        :compiler {:optimizations :none
                                   :source-map true
                                   ;;:preamble ["reagent/react.js"]
                                   :output-to "dev-resources/js/harja.js"
                                   :output-dir "dev-resources/js/out"}}
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
                                   :externs ["externs/leaflet.js"]

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
  :aliases {"tuotanto" ["do" "clean," "deps," "gitlog," "compile," "cljsbuild" "once" "prod," "less" "once," "uberjar," "doc"]

            ;; työkaluja, joita devaamisessa ja asiakkaalta saadun datan hieromisessa oikeaan muotoon, tarvitaan
            "elyt" ["run" "-m" "harja.tyokalut.elyt"] ;; ELY rajojen SHP file => hallintayksikkö SQL inserteiksi
            "sampo" ["run" "-m" "harja.tyokalut.sampo"] ;; SAMPO tuotelista XLS file => toimenpidekoodi SQL inserteiksi
            "gitlog" ["run" "-m" "harja.tyokalut.gitlog"] ;; tekee gitlogin resources alle
            "testit" ["with-profiles" "test" "do" "clean," "compile," "test"]

            "selainrepl" ["run" "-m" "harja.tyokalut.selainrepl"]
            }
  
  )
