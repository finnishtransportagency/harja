(ns harja-laadunseuranta.ui.kamera
  (:require [reagent.core :as reagent :refer [atom]]
            [harja-laadunseuranta.tiedot.kamera :as tiedot]
            [harja-laadunseuranta.tiedot.asetukset.kuvat :as kuvat]
            [harja-laadunseuranta.tiedot.asetukset.asetukset :as asetukset])
  (:require-macros
    [harja-laadunseuranta.macros :as m]
    [cljs.core.async.macros :refer [go go-loop]]
    [devcards.core :as dc :refer [defcard deftest]]))

(defn file-input [on-change]
  [:div.file-input-container
   [:input {:id "file-input"
            :type "file"
            :accept "image/*"
            :capture true
            ;; on-change kutsutaan kun käyttäjä on valinnut kuvan
            ;; Yleensä tätä _ei_ kutsuta silloin kun käyttäjä
            ;; peruu kuvan ottamisen, tällöinhän kentän arvo ei muutu.

            ;; Toisaalta jos käyttäjä on aiemmin ottanut kuvan,
            ;; ja tämän jälkeen lähtee ottamaan uutta ja peruu oton,
            ;; niin kentän arvoksi tulee nil ja on-change laukeaa.
            ;; Puhdasta "kuvanotto peruttu" eventtiä ei ilmeisesti
            ;; ole olemassa.
            :on-change on-change}]])

(defn kamerakomponentti [{:keys [esikatselukuva-atom kuvaa-otetaan-atom]}]
  [:div.kameranappi {:on-click #(tiedot/ota-kuva kuvaa-otetaan-atom)}
   [:div.kameranappi-sisalto
    (if @esikatselukuva-atom
     [:img {:width "100px" :src @esikatselukuva-atom}]
     [:div.kamera-eikuvaa
      [kuvat/svg-sprite "kamera-24"]
      "Lisää kuva"])]])