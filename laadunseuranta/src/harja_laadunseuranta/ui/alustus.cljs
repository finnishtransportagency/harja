(ns harja-laadunseuranta.ui.alustus
  (:require [reagent.core :as reagent :refer [atom]]
            [harja-laadunseuranta.tiedot.asetukset.kuvat :as kuvat]
            [harja-laadunseuranta.ui.yleiset.yleiset :as yleiset]
            [harja-laadunseuranta.utils :as utils]))

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

(defn alustuskomponentti [{:keys [gps-tuettu ensimmainen-sijainti-saatu idxdb-tuettu oikeus-urakoihin
                                  kayttaja-tunnistettu selain-tuettu verkkoyhteys selain-vanhentunut?
                                  ensimmainen-sijainti-virhekoodi]}]
  [:div.alustuskomponentti-container
   [:div.alustuskomponentti
    [:div.liikenneturvallisuusmuistutus "Muista aina liikenne\u00ADturvallisuus tarkastuksia tehdessäsi."]
    [:p "Tarkistetaan..."]

    [tarkistusrivi "Selain tuettu" selain-tuettu
     (cond selain-vanhentunut?
           {:virhe "Selain vaatii päivityksen uudempaan versioon."}

           (= selain-tuettu :virhe)
           {:virhe (str "Selain ei ole tuettu. Tuetut selaimet: " (utils/tuetut-selaimet-tekstina))}

           :default {})]
    [tarkistusrivi "Selaintietokanta-tuki" idxdb-tuettu
     (when (= idxdb-tuettu :virhe)
       {:virhe "Selaintietokantaa ei voida käyttää. Ethän käytä selainta yksityisyystilassa?"})]
    [tarkistusrivi "Verkkoyhteys" verkkoyhteys]
    [tarkistusrivi "GPS-tuki" gps-tuettu]
    [tarkistusrivi "Laite paikannettu" ensimmainen-sijainti-saatu
     (when (= ensimmainen-sijainti-saatu :virhe)
       {:virhe (case ensimmainen-sijainti-virhekoodi
                 1 "GPS:n käyttö on estetty. Varmista, että laitteen paikannus on päällä ja että GPS:n käyttö on sallittu selaimen asetuksissa."
                 2 "Laitetta ei voitu paikantaa, yritä hetken kuluttua uudelleen."
                 3 "Laitetta ei voitu paikantaa, yritä hetken kuluttua uudelleen."
                 "Tuntematon virhe.")})]
    [tarkistusrivi "Käyttäjä tunnistettu" kayttaja-tunnistettu]
    [tarkistusrivi "Oikeus tehdä tarkastuksia" oikeus-urakoihin
     (when (= oikeus-urakoihin :virhe)
       {:virhe "Käyttäjällä ei ole oikeutta luoda tarkastuksia yhteenkään urakkaan."})]

    [:div.screenlock-muistutus
     [yleiset/vihje "Muista asettaa näytön automaattilukitus pois päältä."]]]])
