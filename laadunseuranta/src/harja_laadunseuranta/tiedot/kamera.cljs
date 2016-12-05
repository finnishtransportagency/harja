(ns harja-laadunseuranta.tiedot.kamera
  (:require [reagent.core :as reagent :refer [atom]]
            [harja-laadunseuranta.tiedot.sovellus :as s]))

(defn- tee-kuva [raw]
  (let [kuva (clojure.string/split raw ";base64,")
        mime (get (clojure.string/split (get kuva 0) ":") 1)
        data (get kuva 1)]
    {:data data
     :mime-type mime}))

(defn kuva-otettu [event]
  (let [file (aget (-> event .-target .-files) 0)
        data-url (.createObjectURL js/URL file)
        reader (js/FileReader.)]
    (reset! s/havaintolomake-esikatselukuva data-url)
    (reset! s/havaintolomake-auki true)
    (set! (.-onload reader)
          (fn [e]
            (reset! s/havaintolomake-kuva
                    (tee-kuva (-> e .-target .-result)))))
    (.readAsDataURL reader file)))

(defn ota-kuva []
  (let [file-input (.getElementById js/document "file-input")]
    (when file-input
      (.log js/console "Klikataan file inputtia ohjelmallisesti.")
      (.click file-input))))