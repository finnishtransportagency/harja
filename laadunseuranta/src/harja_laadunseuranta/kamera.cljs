(ns harja-laadunseuranta.kamera
  (:require [reagent.core :as reagent :refer [atom]]
            [harja-laadunseuranta.kuvat :as kuvat]
            [harja-laadunseuranta.asetukset :as asetukset]
            [harja-laadunseuranta.virhekasittely :as virhekasittely])
  (:require-macros
   [harja-laadunseuranta.macros :as m]
   [cljs.core.async.macros :refer [go go-loop]]
   [devcards.core :as dc :refer [defcard deftest]]))

(defn- tee-kuva [raw]
  (let [kuva (clojure.string/split raw ";base64,")
        mime (get (clojure.string/split (get kuva 0) ":") 1)
        data (get kuva 1)]
    {:data data
     :mime-type mime}))

(defn- kuva-otettu [esikatselukuva-atom kuva-atom event]
  (let [file (aget (-> event .-target .-files) 0)
        data-url (.createObjectURL js/URL file)
        reader (js/FileReader.)]
    (reset! esikatselukuva-atom data-url)
    (set! (.-onload reader)
          (fn [e]
            (reset! kuva-atom
                    (tee-kuva (-> e .-target .-result)))))
    (.readAsDataURL reader file)))

(defn kamerakomponentti [kuva-atom]
  (let [esikatselukuva (atom nil)]
    (fn [kuva-atom]
      [:nav.kameranappi
       [:label.kuvan-otto 
        [:input {:type "file"
                 :accept "image/*"
                 :capture true
                 :style {:display "none"}
                 :on-change #(kuva-otettu esikatselukuva kuva-atom %)}]
        (if @esikatselukuva
          [:img {:width "100px" :src @esikatselukuva}]
          [:div.kamera-eikuvaa
           [:p.livicon-upload]
           "Lisää kuva"])]])))

(defonce testikuva (atom nil))

(defcard kamerakomponentti-card
  "Kamerakomponentti"
  (fn [kuva _]
    (reagent/as-element
     [:div
      [:img {:src (or @kuva "")
             :width "500px"
             :height "400px"}]
      [kamerakomponentti kuva]]))
  testikuva
  {:watch-atom true})
