(ns harja-laadunseuranta.tiedot.kamera
  (:require [reagent.core :as reagent :refer [atom]]
            [cljs.core.async :as async :refer [timeout <!]]
            [harja-laadunseuranta.tiedot.sovellus :as s]
            [cljs-time.core :as t])
  (:require-macros [cljs.core.async.macros :refer [go go-loop]]))

(def kuvanottoprosessi-id-atom (atom nil))
(def +kuvanottoprosessin-timeout-ms+ (* 1000 30))

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
      (reset! kuvaa-otetaa-atom false)
      (reset! kuvanottoprosessi-id-atom nil))

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

(defn- timeouttaa-kuvaa-otetaan-flag
  "Asettaa kuvaa otetaan -flägin falseksi, mikäli
   saman kuvan ottaminen on kestänyt poikkeuksellisen kauan"
  [timeoutattava-kuvaprosessi-id
   nykyinen-kuvanottoprosessi-id-atom
   kuvaa-otetaan-atom]
  ;; Tämä tehdään siksi, että "kuvanotto peruttu" eventtiä ei
  ;; ilmeisesti ole olemassa.
  ;; Tradeoff:
  ;; - Flägi saatetaan asettaa virheellisesti falseksi
  ;; jos käyttäjä ottaa kuvaa poikkeuksellisen kauan.
  ;; - Jos käyttäjä peruu kuvan ottamisen heti, flägi
  ;; otetaan pois päältä viiveellä.
  (go (<! (timeout +kuvanottoprosessin-timeout-ms+))
      ;; Käyttäjä ei ole aloittanut uutta kuvanottoprosessia,
      ;; aseta kuvaa otetaan - flägi falseksi.
      (when (= timeoutattava-kuvaprosessi-id
               @nykyinen-kuvanottoprosessi-id-atom)
        (reset! kuvaa-otetaan-atom false))))

(defn ota-kuva
  "Käynnistää kuvan ottamisen klikkaamalla ohjelmallisesti sivulla piilossa
   olevaa file input -kenttää. Desktop-laitteilla laukaisee yleensä normaalin
   tiedostonvalintadialogin, mobiilissa tarjoaa mahdollisuuden ottaa
   kuva laitteen kameralla."
  [kuvaa-otetaan-atom]
  (reset! kuvanottoprosessi-id-atom (hash (t/now)))
  (let [file-input (.getElementById js/document "file-input")]
    (when file-input
      (reset! kuvaa-otetaan-atom true)
      (timeouttaa-kuvaa-otetaan-flag
        @kuvanottoprosessi-id-atom
        kuvanottoprosessi-id-atom
        kuvaa-otetaan-atom)
      (.click file-input))))