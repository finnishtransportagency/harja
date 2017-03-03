(ns harja.palvelin.palvelut.laadunseuranta.viestinta
  "Tässä namespacessa on palveluita laadunseuranta-asioihin liittyvään tekstiviesti-/sähköpostiviestintään."
  (:require [taoensso.timbre :as log]
            [clojure.core.match :refer [match]]
            [harja.tyokalut.html :as html-tyokalut]
            [harja.kyselyt.urakat :as urakat-q]
            [harja.palvelin.palvelut.viestinta :as viestinta]
            [harja.palvelin.integraatiot.sahkoposti :as sahkoposti]
            [hiccup.core :refer [html]]
            [harja.palvelin.komponentit.fim :as fim]
            [harja.domain.tierekisteri :as tierekisteri]
            [harja.pvm :as pvm])
  (:use [slingshot.slingshot :only [try+ throw+]]))

;; Viestien muodostus

(defn- laatupoikkeama-harja-url [urakka-id hallintayksikko-id]
  (str "https://extranet.liikennevirasto.fi/harja#urakat/laadunseuranta/laatupoikkeamat?&hy="
       hallintayksikko-id "&u=" urakka-id))

(defn- viesti-laatupoikkeamasta-pyydetty-selvitys [{:keys [urakka-id hallintayksikko-id urakka-nimi raportoija
                                                           kuvaus tr-osoite aika]}]
  (let [linkki (laatupoikkeama-harja-url urakka-id hallintayksikko-id)]
    (html
      [:div
       [:p "Seuraavasta laatupoikkeamasta on pyydetty selvitys urakoitsijalta:"]
       (html-tyokalut/taulukko [["Urakka" urakka-nimi]
                                ["Raportoija" raportoija]
                                ["Kuvaus" kuvaus]
                                ["Sijainti" (tierekisteri/tierekisteriosoite-tekstina
                                              {:tr-numero (:numero tr-osoite)
                                               :tr-alkuosa (:alkuosa tr-osoite)
                                               :tr-alkuetaisyys (:alkuetaisyys tr-osoite)
                                               :tr-loppuosa (:loppuosa tr-osoite)
                                               :tr-loppuetaisyys (:loppuetaisyys tr-osoite)}
                                              {:teksti-tie? false})]
                                ["Aika" (pvm/pvm-aika-opt aika)]])
       [:p "Laatupoikkeama Harjassa: "
        [:a {:href linkki} linkki]]])))

;; Viestien lähetykset (julkinen rajapinta)

(defn laheta-sposti-laatupoikkeamasta-selvitys-pyydetty
  "Lähettää urakoitsijan urakan vastuuhenkilölle tiedon siitä, että laatupoikkeamasta
   on pyydetty selvitys urakoitsijalta."
  [{:keys [db fim email urakka-id laatupoikkeama selvityksen-pyytaja]}]
  (log/debug (format "Lähetetään sähköposti: laatupoikkeamasta %s pyydetty selvitys" (:id laatupoikkeama)))

  (let [urakka-nimi (:nimi (first (urakat-q/hae-urakka db urakka-id)))
        hallintayksikko-id (:id (first (urakat-q/hae-urakan-ely db urakka-id)))
        urakka-sampoid (urakat-q/hae-urakan-sampo-id db urakka-id)]
    (viestinta/laheta-sposti-fim-kayttajarooleille
      {:fim fim
       :email email
       :urakka-sampoid urakka-sampoid
       :fim-kayttajaroolit #{"urakan vastuuhenkilö"}
       :viesti-otsikko (format "Laatupoikkeamasta tehty selvityspyyntö urakassa %s" urakka-nimi)
       :viesti-body (viesti-laatupoikkeamasta-pyydetty-selvitys
                      {:raportoija selvityksen-pyytaja
                       :urakka-id urakka-id
                       :hallintayksikko-id hallintayksikko-id
                       :urakka-nimi urakka-nimi
                       :kuvaus (:kuvaus laatupoikkeama)
                       :kohde (:kohde laatupoikkeama)
                       :tr-osoite (:tr laatupoikkeama)
                       :aika (:aika laatupoikkeama)})})))