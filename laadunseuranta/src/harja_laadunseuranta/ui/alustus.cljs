(ns harja-laadunseuranta.ui.alustus
  (:require [reagent.core :as reagent :refer [atom]]
            [harja-laadunseuranta.tiedot.sovellus :as sovellus]
            [harja-laadunseuranta.utils :as utils]
            [harja-laadunseuranta.tiedot.asetukset.kuvat :as kuvat]
            [harja-laadunseuranta.ui.ikonit :as ikonit]))

(defn- checkmark [flag]
  [:img {:src (if flag kuvat/+check+ kuvat/+cross+)
         :width 36
         :height 36}])

(defn alustuskomponentti [{:keys [gps-tuettu ensimmainen-sijainti idxdb-tuettu
                                  kayttaja selain-tuettu verkkoyhteys selain-vanhentunut]}]
  [:div.alustuskomponentti-container
   [:div.alustuskomponentti
    [:div.liikenneturvallisuusmuistutus "Muista aina liikenne\u00ADturvallisuus tarkastuksia tehdessäsi."]
    [:p "Tarkistetaan..."]
    [:div {:class (when @selain-vanhentunut
                    "alustus-varoitus")}
           [checkmark @selain-tuettu] (if @selain-vanhentunut
                                        "Selain vaatii päivityksen"
                                        "Selain tuettu")]
    [:div [checkmark @verkkoyhteys] "Verkkoyhteys"]
    [:div [checkmark @gps-tuettu] "GPS-tuki"]
    [:div [checkmark @ensimmainen-sijainti] "Laite paikannettu"]
    [:div [checkmark @idxdb-tuettu] "Selaintietokanta-tuki"]
    [:div [checkmark @kayttaja] "Käyttäjä tunnistettu"]
    [:div.screenlock-muistutus (ikonit/ikoni-ja-teksti
                                 (ikonit/livicon-info-circle)
                                 "Muista asettaa näytön automaattilukitus pois päältä.")]]])
