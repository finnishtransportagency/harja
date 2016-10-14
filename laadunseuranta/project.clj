(defproject harja-laadunseuranta "0.1.0-SNAPSHOT"
  :description "Harjan laadunseurantatyökalu, jolla valvotaan tiestön kuntoa osana tieverkon kunnossapitoa."
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  :dependencies [[org.clojure/clojure "1.8.0"]
                 [org.clojure/clojurescript "1.7.228"]
                 [org.clojure/core.async "0.2.374"]
                 [org.clojure/core.match "0.3.0-alpha4"]
                 [devcards "0.2.1-4" :exclusions [cljsjs/react]]
                 [cljs-react-test "0.1.3-SNAPSHOT"]
                 [com.andrewmcveigh/cljs-time "0.4.0"]
                 [prismatic/schema "1.0.5"]
                 
                 [com.cognitect/transit-cljs "0.8.237"]
                 [com.cognitect/transit-clj "0.8.285"]
                 
                 [compojure "1.5.0"]
                 [metosin/compojure-api "1.0.1"]
                 [org.clojure/data.codec "0.1.0"]
                 [yesql "0.5.2"]
                 [org.postgresql/postgresql "9.4-1201-jdbc41"]
                 [clojunauts/postgis-jdbc "2.1.0SVN"]

                 [com.mchange/c3p0 "0.9.5"]
                 [http-kit "2.1.18"]
                 [org.clojure/core.match "0.3.0-alpha4"]
                 
                 [com.taoensso/timbre "4.3.1"]
                 [com.narkisr/gelfino-client "0.7.0"]

                 [reagent "0.6.0-alpha" :exclusions [[cljsjs/react :classifier "*"]]]
                 [cljsjs/react-with-addons "0.14.3-0"]
                 [cljs-ajax "0.5.3"]
                 [prismatic/dommy "1.1.0"]
                 [javax.servlet/servlet-api "2.5"]
                 
                 [cljsjs/openlayers "3.10.1"]
                 [net.coobird/thumbnailator "0.4.8"]]

  :plugins [[lein-cljsbuild "1.1.2"]
            [lein-figwheel "0.5.0-3" :exclusions [org.clojure/clojure]]
            [jonase/eastwood "0.2.3"]
            [lein-less "1.7.5"]
            [cider/cider-nrepl "0.10.2"]]

  :source-paths ["clj-src" "cljc-src"]
  :test-paths ["test-src/clj"]
  
  :aot :all
  :main harja-laadunseuranta.core

  :repl-options {:init-ns harja-laadunseuranta.core
                 :init (start-server)
                 :port 52510}
  
  :clean-targets ^{:protect false} ["resources/public/js/compiled" "target"]

  ;; Less CSS käännös tuotantoa varten (dev modessa selain tekee less->css muunnoksen)
  :less {:source-paths ["../dev-resources/less/laadunseuranta/application"]
         :target-path  "resources/public/laadunseuranta/css/"}

  :cljsbuild {:test-commands {"unit" ["phantomjs"
                                      "--local-storage-path=./tmp/"
                                      "--local-storage-quota=1024"
                                      "--offline-storage-path=./tmp/"
                                      "--offline-storage-quota=1024"
                                      "phantom/unit-test.js"
                                      "resources/private/unit-test.html"]}

              :builds
              [{:id "dev"
                :source-paths ["src" "cljc-src" "test-src/cljs"]

                :figwheel {:on-jsload "harja-laadunseuranta.dev-core/on-js-reload"}

                :compiler {:main harja-laadunseuranta.dev-core
                           :asset-path "js/compiled/out"
                           :output-to "resources/public/js/compiled/harja_laadunseuranta.js"
                           :output-dir "resources/public/js/compiled/out"
                           :source-map-timestamp true}}
               
               {:id "devcards"
                :source-paths ["src" "cljc-src"]

                :figwheel {:devcards true
                                        ;:nrepl-port 7889
                                        ;:server-port 3450
                           }

                :compiler {:main harja-laadunseuranta.devcards-core
                           :asset-path "js/compiled/devcards_out"
                           :output-to "resources/public/js/compiled/harja_laadunseuranta_devcards.js"
                           :output-dir "resources/public/js/compiled/devcards_out"
                           :source-map-timestamp true}}
               
               {:id "test"
                :source-paths ["src" "cljc-src" "test-src/cljs"]

                :compiler {:main harja-laadunseuranta.test-main
                           ;;:asset-path "js/out"
                           :output-to "resources/private/js/unit-test.js"
                           ;;:output-dir "resources/private/js/out"
                           :source-map-timestamp true}}
               
               ;; This next build is an compressed minified build for
               ;; production. You can build this with:
               ;; lein cljsbuild once min
               {:id "min"
                :source-paths ["src" "cljc-src"]
                :jar true
                :compiler {:output-to "resources/public/js/compiled/harja_laadunseuranta.js"
                           :closure-extra-annotations #{"api" "observable"}
                           :main harja-laadunseuranta.prod-core
                           :optimizations :advanced
                           :language-in  :ecmascript5
                           :language-out :ecmascript5
                           :externs ["externs.js"]
                           :parallel-build true
                           :pretty-print false}}]}

  :aliases {"tuotanto" ["do" "clean,"
                        "cljsbuild" "once" "min,"
                        "less" "once,"
                        "uberjar"]}

  :auto-clean false
  
  :figwheel {;; :http-server-root "public" ;; default and assumes "resources"
             :server-port 3001
             ;; :server-ip "127.0.0.1"

             :css-dirs ["resources/public/css"] ;; watch and update CSS

             ;; Start an nREPL server into the running figwheel process
             :nrepl-port 7889

             ;; Server Ring Handler (optional)
             ;; if you want to embed a ring handler into the figwheel http-kit
             ;; server, this is for simple ring servers, if this
             ;; doesn't work for you just run your own server :)
             ;; :ring-handler hello_world.server/handler

             ;; To be able to open files in your editor from the heads up display
             ;; you will need to put a script on your path.
             ;; that script will have to take a file path and a line number
             ;; ie. in  ~/bin/myfile-opener
             ;; #! /bin/sh
             ;; emacsclient -n +$2 $1
             ;;
             ;; :open-file-command "myfile-opener"

             ;; if you want to disable the REPL
             ;; :repl false

             ;; to configure a different figwheel logfile path
             ;; :server-logfile "tmp/logs/figwheel-logfile.log"
             }
  
  :profiles {:dev {:dependencies [[com.cemerick/piggieback "0.2.1"]
                                  [figwheel-sidecar "0.5.0-SNAPSHOT"]]
                   :repl-options {:nrepl-middleware [cemerick.piggieback/wrap-cljs-repl]}}})
