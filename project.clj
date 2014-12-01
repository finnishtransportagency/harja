(defproject harja "0.0.1-SNAPSHOT"
  :description "Liikenneviraston Harja"
  :dependencies [[org.clojure/clojure "1.7.0-alpha4"] ; siirrytään 1.7.0 heti kun valmis
                 [org.clojure/clojurescript "0.0-2371"]

                 ;;;;;;; Yleiset ;;;;;;;
                 [prismatic/schema "0.3.3"]
                 
                 ;;;;;;; Palvelin ;;;;;;;

                 ;; Komponenttituki
                 [com.stuartsierra/component "0.2.2"]
                 
                 ;; Lokitus
                 [org.clojure/tools.logging "0.3.1"]
                 [ch.qos.logback/logback-classic "1.1.2"]

                 ;; HTTP palvelin ja reititys
                 [http-kit "2.1.16"]
                 [compojure "1.2.1"]
                 ;;[com.domkm/silk "0.0.2" :exclusions [org.clojure/clojure]]
                 
                 ;; Tietokanta: ajuri, kirjastot ja -migraatiot
                 [org.postgresql/postgresql "9.3-1102-jdbc41"]
                 [com.mchange/c3p0 "0.9.2.1"]
                 [yesql "0.4.0"]
                 [ragtime "0.3.7"]
                 
                 ;; Asiakas
                 
                 [lively "0.1.2"] 
                 [reagent "0.4.3"]]
  
  :plugins [[lein-cljsbuild "1.0.3"]
            [ragtime/ragtime.lein "0.3.7"]
            [cider/cider-nrepl "0.8.1"]]
  
  ;; Asiakaspuolen cljs buildin tietoja
  :cljsbuild {:builds [{:id "dev"
                        :source-paths ["src/cljs" "src/cljs-dev"]
                        :compiler {:optimizations :none
                                   :source-map true
                                   :preamble ["reagent/react.js"]
                                   :output-to "target/public/js/harja.js"
                                   :output-dir "target/public/js/out"}}
                       
                       ;; FIXME: add production build
                       ]}



  ;; Palvelimen buildin tietoja
  :source-paths ["src/clj"]
  :main harja.palvelin.main
  )
