(defproject harja "0.0.1-SNAPSHOT"
  :description "Liikenneviraston Harja"
  :eval-in-leiningen true
  :dependencies [[org.clojure/clojure "1.7.0-alpha4"] ; siirrytään 1.7.0 heti kun valmis
                 [org.clojure/clojurescript "0.0-2371"]

                 ;;;;;;; Yleiset ;;;;;;;
                 [prismatic/schema "0.3.3"]
                 [org.clojure/core.async "0.1.346.0-17112a-alpha"]

                 ;; Transit tietomuoto asiakkaan ja palvelimen väliseen kommunikointiin
                 [com.cognitect/transit-cljs "0.8.194"]
                 [com.cognitect/transit-clj "0.8.259"]
                 
                 ;;;;;;; Palvelin ;;;;;;;

                 ;; Komponenttituki
                 [com.stuartsierra/component "0.2.2"]
                 
                 ;; Lokitus
                 [org.clojure/tools.logging "0.3.1"]
                 [ch.qos.logback/logback-classic "1.1.2"]

                 ;; HTTP palvelin ja reititys
                 [http-kit "2.1.19"]
                 [compojure "1.2.1"]

                 
                 ;; Tietokanta: ajuri, kirjastot ja -migraatiot
                 [org.postgresql/postgresql "9.3-1102-jdbc41"]
                 [com.mchange/c3p0 "0.9.2.1"]
                 [yesql "0.4.0"]

                 ;; GeoTools
                 [org.geotools/gt-shapefile "12.1"]
                 [org.geotools/gt-process-raster "12.1"]
                 [org.geotools/gt-swing "12.1"] ;; just for experimentation, remove when no longer needed

                 ;; Microsoft dokumenttimuotojen tuki
                 [org.apache.poi/poi "3.10.1"]
                 [org.apache.poi/poi-scratchpad "3.10.1"]
                 
                 
                 ;; Asiakas
                 [spyscope "0.1.5"]

                 [cljs-ajax "0.3.3"]
                 [lively "0.1.2"] 
                 [reagent "0.4.3"]

                 [org.clojure/core.async "0.1.346.0-17112a-alpha"]
 
                 
                 
                 ]

  :dev-dependencies [;; Selain REPL
                     [com.cemerick/piggieback "0.1.3"]
                     ;; Testaus
                     [clj-webdriver "0.6.0"]
                     [org.seleniumhq.selenium/selenium-java "2.44.0"]
                     [org.seleniumhq.selenium/selenium-firefox-driver "2.44.0"]]

                 
  :repositories [["osgeo" "http://download.osgeo.org/webdav/geotools/"]] ;; FIXME: move artifacts to mvn.solita.fi

  
  :plugins [[com.keminglabs/cljx "0.4.0"]
            [lein-cljsbuild "1.0.3"]
            [cider/cider-nrepl "0.8.1"]]

  ;; Asiakas- ja palvelinpuolen jaettu koodi
  :cljx {:builds [{:source-paths ["src/cljx"]
                   :output-path "target/classes"
                   :rules :clj}
                  
                  {:source-paths ["src/cljx"]
                   :output-path "target/classes"
                   :rules :cljs}]}
  :prep-tasks [["cljx" "once"]]
  
  ;; Asiakaspuolen cljs buildin tietoja
  :cljsbuild {:builds [{:id "dev"
                        :source-paths ["src/cljs" "src/cljs-dev"]
                        :compiler {:optimizations :none
                                   :source-map true
                                   :preamble ["reagent/react.js"]
                                   :output-to "dev-resources/js/harja.js"
                                   :output-dir "dev-resources/js/out"}}
                       
                       ;; FIXME: add production build
                       ]}



  ;; Palvelimen buildin tietoja
  :source-paths ["src/clj"]
  :main harja.palvelin.main

  ;; REPL kehitys
  :repl-options {:init-ns harja.palvelin.main
                 :init (harja.palvelin.main/-main)}
  )
