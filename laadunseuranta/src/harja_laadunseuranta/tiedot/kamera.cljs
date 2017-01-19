(ns harja-laadunseuranta.tiedot.kamera
  (:require [reagent.core :as reagent :refer [atom]]
            [cljs.core.async :as async :refer [timeout <!]]
            [harja-laadunseuranta.tiedot.sovellus :as s])
  (:require-macros [cljs.core.async.macros :refer [go go-loop]]))

(defn- tee-kuva [raw]
  (let [kuva (clojure.string/split raw ";base64,")
        mime (get (clojure.string/split (get kuva 0) ":") 1)
        data (get kuva 1)]
    {:data data
     :mime-type mime}))

(defn kuva-otettu [event kuvaa-otetaa-atom]
  ;; Mobiililaitteilla kuvaa otettaessa document.hidden = true.
  ;; Aseta kuvanotto-flägi pois päältä pienellä viiveellä,
  ;; jotta "visibilitychange" eventti ehditetään varmasti käsitellä
  ;; ensin.
  (go (<! (timeout 1000))
      (reset! kuvaa-otetaa-atom false))
  (when-let [file (aget (-> event .-target .-files) 0)]
    (let [data-url (.createObjectURL js/URL file)
          reader (js/FileReader.)]
      (reset! s/havaintolomake-esikatselukuva data-url)
      (reset! s/havaintolomake-auki? true)
      (set! (.-onload reader)
            (fn [e]
              (reset! s/havaintolomake-kuva
                      (tee-kuva (-> e .-target .-result)))))
      (.readAsDataURL reader file))))

(defn ota-kuva
  "Käynnistää kuvan ottamisen klikkaamalla ohjelmallisesti sivulla piilossa
   olevaa file input -kenttää. Desktop-laitteilla laukaisee yleensä normaalin
   tiedostonvalintadialogin, mobiilissa tarjoaa lisäksi mahdollisuuden ottaa
   kuva laitteen kameralla."
  [kuvaa-otetaa-atom]
  (let [file-input (.getElementById js/document "file-input")]
    (when file-input
      (reset! kuvaa-otetaa-atom true)
      (.click file-input))))