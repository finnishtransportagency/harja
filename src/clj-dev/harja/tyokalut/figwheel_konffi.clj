(ns harja.tyokalut.figwheel-konffi
  "Työkalu Figwheelin build-konffin generointiin siten, että lähdekonffi pysyy puhtaana EDN:nä.

  Käyttö:
    lein run -m harja.tyokalut.figwheel-konffi <input> <output> <portti>

  Esim:
    lein run -m harja.tyokalut.figwheel-konffi figwheel_conf/dev.cljs.edn figwheel_conf/luodut/dev-portti.cljs.edn 3450"
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.pprint :as pprint])
  (:gen-class))

(defn lue-edn
  [polku]
  (with-open [r (java.io.PushbackReader. (io/reader polku))]
    (edn/read r)))

(defn paivita-ring-server-portti
  "Päivittää Figwheel build-konffin metadataan ring-server portin.

  Figwheel build-konffi on map, jossa metadataan on tallennettu asetuksia.
  Tässä repo:ssa ring-server portti elää metadata-avaimessa :ring-server-options." 
  [build portti]
  (let [meta0 (or (meta build) {})
        meta1 (assoc-in meta0 [:ring-server-options :port] portti)]
    (with-meta build meta1)))

(defn kirjoita-edn
  [polku data]
  (io/make-parents polku)
  (with-open [w (io/writer polku)]
    (binding [*print-meta* true
              pprint/*print-right-margin* 120]
      (pprint/pprint data w))))

(defn -main
  [& args]
  (when-not (= 3 (count args))
    (binding [*out* *err*]
      (println "Käyttö: lein run -m harja.tyokalut.figwheel-konffi <input> <output> <portti>")
      (println "Esim:  lein run -m harja.tyokalut.figwheel-konffi figwheel_conf/dev.cljs.edn figwheel_conf/luodut/dev-portti.cljs.edn 3450"))
    (System/exit 1))
  (let [[input output portti-str] args
        portti (Integer/parseInt portti-str)
        build (lue-edn input)
        build (paivita-ring-server-portti build portti)]
    (kirjoita-edn output build)))
