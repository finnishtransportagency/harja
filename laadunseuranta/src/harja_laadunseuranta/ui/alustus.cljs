(ns harja-laadunseuranta.ui.alustus
  (:require [reagent.core :as reagent :refer [atom]]
            [harja-laadunseuranta.tiedot.asetukset.kuvat :as kuvat]
            [harja-laadunseuranta.ui.yleiset.yleiset :as yleiset]))

(defn- tarkistusrivi
  "Tila on keyword: :ok :virhe :tarkistetaan"
  ([teksti tila] (tarkistusrivi teksti tila {}))
  ([teksti tila optiot]
   [:div.tarkistusrivi
    [:div
     [:img {:src (case tila
                   :tarkistetaan kuvat/+spinner+
                   :ok kuvat/+check+
                   :virhe kuvat/+cross+
                   kuvat/+spinner+)
            :width 36
            :height 36}]
     [:div.tarkistusteksti teksti]]
    (when (:virhe optiot)
      [:div.alustus-varoitus "lol wtf"])]))

(defn alustuskomponentti [{:keys [gps-tuettu ensimmainen-sijainti idxdb-tuettu oikeus-urakoihin
                                  kayttaja selain-tuettu verkkoyhteys selain-vanhentunut]}]
  [:div.alustuskomponentti-container
   [:div.alustuskomponentti
    [:div.liikenneturvallisuusmuistutus "Muista aina liikenne\u00ADturvallisuus tarkastuksia tehdessäsi."]
    [:p "Tarkistetaan..."]
    [tarkistusrivi "Selain tuettu" selain-tuettu
     (when selain-vanhentunut
       (.log js/console "VANHA SELAIN!?" selain-vanhentunut)
       {:virhe "Selain vaatii päivityksen"})] ;; TODO JOS EI TUETTU NÄYTÄ VIESTI MIKÄ SELAIN
    [tarkistusrivi "Selaintietokanta-tuki" idxdb-tuettu]
    [tarkistusrivi "Verkkoyhteys" verkkoyhteys]
    [tarkistusrivi "GPS-tuki" gps-tuettu]
    [tarkistusrivi "Laite paikannettu" ensimmainen-sijainti]
    [tarkistusrivi "Käyttäjä tunnistettu" kayttaja]
    [tarkistusrivi "Oikeus tehdä tarkastuksia" oikeus-urakoihin]
    [:div.screenlock-muistutus
     [yleiset/vihje "Muista asettaa näytön automaattilukitus pois päältä."]]]])
