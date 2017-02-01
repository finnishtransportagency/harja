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
    (when-let [virheviesti (:virhe optiot)]
      [:div.alustus-varoitus virheviesti])]))

(defn alustuskomponentti [{:keys [gps-tuettu ensimmainen-sijainti idxdb-tuettu oikeus-urakoihin
                                  kayttaja-tunnistettu selain-tuettu verkkoyhteys selain-vanhentunut?]}]
  [:div.alustuskomponentti-container
   [:div.alustuskomponentti
    [:div.liikenneturvallisuusmuistutus "Muista aina liikenne\u00ADturvallisuus tarkastuksia tehdessäsi."]
    [:p "Tarkistetaan..."]

    [tarkistusrivi "Selain tuettu" selain-tuettu
     (when selain-vanhentunut?
       {:virhe "Selain vaatii päivityksen"})]
    [tarkistusrivi "Selaintietokanta-tuki" idxdb-tuettu
     (when (= idxdb-tuettu :virhe)
       {:virhe "Selaintietokantaa ei voida käyttää. Ethän käytä selainta yksityisyystilassa?"})]
    [tarkistusrivi "Verkkoyhteys" verkkoyhteys]
    [tarkistusrivi "GPS-tuki" gps-tuettu
     (when (= gps-tuettu :virhe)
       {:virhe "GPS:ää ei voida käyttää. Varmista, että laitteen paikannus on päällä ja että GPS:n käyttö on sallittu selaimen asetuksissa."})]
    [tarkistusrivi "Laite paikannettu" ensimmainen-sijainti]
    [tarkistusrivi "Käyttäjä tunnistettu" kayttaja-tunnistettu]
    [tarkistusrivi "Oikeus tehdä tarkastuksia" oikeus-urakoihin
     (when (= oikeus-urakoihin :virhe)
       {:virhe "Käyttäjällä ei ole oikeutta luoda tarkastuksia yhteenkään urakkaan."})]

    [:div.screenlock-muistutus
     [yleiset/vihje "Muista asettaa näytön automaattilukitus pois päältä."]]]])
