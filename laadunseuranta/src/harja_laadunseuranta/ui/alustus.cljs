(ns harja-laadunseuranta.ui.alustus
  (:require [reagent.core :as reagent :refer [atom]]
            [harja-laadunseuranta.tiedot.asetukset.kuvat :as kuvat]
            [harja-laadunseuranta.ui.yleiset.yleiset :as yleiset]))

(defn- checkmark
  "Tila on keyword: :ok :virhe :tarkistetaan"
  ([teksti tila] (checkmark teksti tila {}))
  ([teksti tila optiot]
   [:div (when (= (:varoitus optiot)) {:class "alustus-varoitus"})
    [:img {:src (case tila
                  :tarkistetaan kuvat/+spinner+
                  :ok kuvat/+check+
                  :virhe kuvat/+cross+
                  kuvat/+spinner+)
           :width 36
           :height 36}]
    teksti]))

(defn alustuskomponentti [{:keys [gps-tuettu ensimmainen-sijainti idxdb-tuettu oikeus-urakoihin
                                  kayttaja selain-tuettu verkkoyhteys selain-vanhentunut]}]
  [:div.alustuskomponentti-container
   [:div.alustuskomponentti
    [:div.liikenneturvallisuusmuistutus "Muista aina liikenne\u00ADturvallisuus tarkastuksia tehdessäsi."]
    [:p "Tarkistetaan..."]
    [checkmark "Selain tuettu" selain-tuettu
     (when selain-vanhentunut
       {:varoitus "Selain vaatii päivityksen"})] ;; TODO JOS EI TUETTU NÄYTÄ VIESTI MIKÄ SELAIN
    [checkmark "Selaintietokanta-tuki" idxdb-tuettu]
    [checkmark "Verkkoyhteys" verkkoyhteys]
    [checkmark "GPS-tuki" gps-tuettu]
    [checkmark "Laite paikannettu" ensimmainen-sijainti]
    [checkmark "Käyttäjä tunnistettu" kayttaja]
    [checkmark "Oikeus tehdä tarkastuksia" oikeus-urakoihin]
    [:div.screenlock-muistutus
     [yleiset/vihje "Muista asettaa näytön automaattilukitus pois päältä."]]]])
