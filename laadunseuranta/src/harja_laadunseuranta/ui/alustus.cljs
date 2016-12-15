(ns harja-laadunseuranta.ui.alustus
  (:require [reagent.core :as reagent :refer [atom]]
            [harja-laadunseuranta.tiedot.asetukset.kuvat :as kuvat]))

(defn- checkmark [flag]
  [:img {:src (if flag kuvat/+check+ kuvat/+cross+)
         :width 36
         :height 36}])

(defn alustuskomponentti [{:keys [gps-tuettu ensimmainen-sijainti idxdb-tuettu oikeus-urakoihin
                                  kayttaja selain-tuettu verkkoyhteys selain-vanhentunut]}]
  [:div.alustuskomponentti-container
   [:div.alustuskomponentti
    [:div.liikenneturvallisuusmuistutus "Muista aina liikenne\u00ADturvallisuus tarkastuksia tehdessäsi."]
    [:p "Tarkistetaan..."]
    [:div {:class (when selain-vanhentunut
                    "alustus-varoitus")}
           [checkmark selain-tuettu] (if selain-vanhentunut
                                        "Selain vaatii päivityksen"
                                        "Selain tuettu")]
    [:div [checkmark verkkoyhteys] "Verkkoyhteys"]
    [:div [checkmark idxdb-tuettu] "Selaintietokanta-tuki"]
    [:div [checkmark gps-tuettu] "GPS-tuki"]
    [:div [checkmark ensimmainen-sijainti] "Laite paikannettu"]
    [:div [checkmark kayttaja] "Käyttäjä tunnistettu"]
    [:div [checkmark (not (empty? oikeus-urakoihin))] "Oikeus tehdä tarkastuksia"]
    [:div.screenlock-muistutus
     "Muista asettaa näytön automaattilukitus pois päältä."]]])
