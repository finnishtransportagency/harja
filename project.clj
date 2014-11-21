(defproject harja "0.0.1-SNAPSHOT"
  :description "Liikenneviraston Harja"
  :dependencies [[org.clojure/clojure "1.7.0-alpha4"] ; siirrytään 1.7.0 heti kun valmis
                 [org.clojure/clojurescript "0.0-2371"]

                 ;; Palvelin
                 
                 ;; HTTP palvelin
                 [http-kit "2.1.16"]

                 ;; Tietokanta: ajuri, kirjastot ja -migraatiot
                 [org.postgresql/postgresql "9.3-1102-jdbc41"]
                 [yesql "0.4.0"]
                 [ragtime "0.3.7"]
                 
                 ;; Asiakas
                 
                 ;[figwheel "0.1.5-SNAPSHOT"]
                 [reagent "0.4.3"]]
  
  :plugins [[lein-cljsbuild "1.0.3"]
            ;;[lein-figwheel "0.1.5-SNAPSHOT"]
            [ragtime/ragtime.lein "0.3.7"]]

  ;; Asiakaspuolen cljs buildin tietoja
  :cljsbuild {:builds [{:id "dev"
                        :source-paths ["src/cljs"]
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
