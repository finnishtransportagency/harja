(ns harja.palvelin.palvelut.laadunseuranta.viestinta
  "Tässä namespacessa on palveluita laadunseuranta-asioihin liittyvään tekstiviesti-/sähköpostiviestintään."
  (:require [taoensso.timbre :as log]
            [clojure.core.match :refer [match]]
            [harja.tyokalut.html :as html-tyokalut]
            [harja.kyselyt.urakat :as urakat-q]
            [harja.palvelin.palvelut.viestinta :as viestinta]
            [harja.palvelin.asetukset :refer [ominaisuus-kaytossa?]]
            [harja.tyokalut.sms :as sms-tyokalut]
            [hiccup.core :refer [html]]
            [hiccup.core :refer [html h]]
            [harja.domain.tierekisteri :as tierekisteri]
            [harja.pvm :as pvm])
  (:use [slingshot.slingshot :only [try+ throw+]]))

;; Viestien muodostus

(defn- laatupoikkeama-harja-url [urakka-id hallintayksikko-id]
  (str "https://extranet.vayla.fi/harja/#urakat/laadunseuranta/laatupoikkeamat?&hy="
       hallintayksikko-id "&u=" urakka-id))

(defn- sahkoposti-laatupoikkeamasta-pyydetty-selvitys
  [{:keys [urakka-id hallintayksikko-id urakka-nimi raportoija
           kuvaus tr-osoite aika]}]
  (let [linkki (laatupoikkeama-harja-url urakka-id hallintayksikko-id)]
    (html
      [:div
       [:p "Seuraavasta laatupoikkeamasta on pyydetty selvitys urakoitsijalta:"]
       (html-tyokalut/tietoja [["Urakka" urakka-nimi]
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
       [:p "Laatupoikkeamat Harjassa: "
        [:a {:href linkki} linkki]]])))

(defn- tekstiviesti-laatupoikkeamasta-pyydetty-selvitys
  [{:keys [urakka-id hallintayksikko-id urakka-nimi raportoija
           kuvaus tr-osoite aika]}]
  (let [linkki (laatupoikkeama-harja-url urakka-id hallintayksikko-id)]
    (str
      "Seuraavasta laatupoikkeamasta on pyydetty selvitys urakoitsijalta:\n"
      (sms-tyokalut/tietolista
        "Urakka" urakka-nimi
        "Raportoija" raportoija
        "Kuvaus" kuvaus
        "Sijainti" (tierekisteri/tierekisteriosoite-tekstina
                     {:tr-numero (:numero tr-osoite)
                      :tr-alkuosa (:alkuosa tr-osoite)
                      :tr-alkuetaisyys (:alkuetaisyys tr-osoite)
                      :tr-loppuosa (:loppuosa tr-osoite)
                      :tr-loppuetaisyys (:loppuetaisyys tr-osoite)}
                     {:teksti-tie? false})
        "Aika" (pvm/pvm-aika-opt aika))
      "Laatupoikkeamat Harjassa: " linkki)))

;; Sisäinen käsittely

(defn- laheta-sposti-laatupoikkeamasta-selvitys-pyydetty*
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
       :viesti-body (sahkoposti-laatupoikkeamasta-pyydetty-selvitys
                      {:raportoija selvityksen-pyytaja
                       :urakka-id urakka-id
                       :hallintayksikko-id hallintayksikko-id
                       :urakka-nimi urakka-nimi
                       :kuvaus (:kuvaus laatupoikkeama)
                       :kohde (:kohde laatupoikkeama)
                       :tr-osoite (:tr laatupoikkeama)
                       :aika (:aika laatupoikkeama)})})))

(defn- laheta-tekstiviesti-laatupoikkeamasta-selvitys-pyydetty*
  [{:keys [db fim sms urakka-id laatupoikkeama selvityksen-pyytaja]}]
  (log/debug (format "Lähetetään tekstiviesti: laatupoikkeamasta %s pyydetty selvitys" (:id laatupoikkeama)))

  (let [urakka-nimi (:nimi (first (urakat-q/hae-urakka db urakka-id)))
        hallintayksikko-id (:id (first (urakat-q/hae-urakan-ely db urakka-id)))
        urakka-sampoid (urakat-q/hae-urakan-sampo-id db urakka-id)]
    (viestinta/laheta-tekstiviesti-fim-kayttajarooleille
      {:fim fim
       :sms sms
       :urakka-sampoid urakka-sampoid
       :fim-kayttajaroolit #{"urakan vastuuhenkilö"}
       :viesti (tekstiviesti-laatupoikkeamasta-pyydetty-selvitys
                 {:raportoija selvityksen-pyytaja
                  :urakka-id urakka-id
                  :hallintayksikko-id hallintayksikko-id
                  :urakka-nimi urakka-nimi
                  :kuvaus (:kuvaus laatupoikkeama)
                  :kohde (:kohde laatupoikkeama)
                  :tr-osoite (:tr laatupoikkeama)
                  :aika (:aika laatupoikkeama)})})))

;; Viestien lähetykset (julkinen rajapinta)

(defn laheta-sposti-laatupoikkeamasta-selvitys-pyydetty
  "Lähettää urakoitsijan urakan vastuuhenkilölle sähköpostilla tiedon siitä, että laatupoikkeamasta
   on pyydetty selvitys urakoitsijalta."
  [{:keys [db fim email urakka-id laatupoikkeama selvityksen-pyytaja]}]
  (when (ominaisuus-kaytossa? :laatupoikkeaman-selvityspyynnosta-lahtee-viesti)
    (laheta-sposti-laatupoikkeamasta-selvitys-pyydetty*
      {:db db :fim fim :email email :laatupoikkeama laatupoikkeama
       :selvityksen-pyytaja selvityksen-pyytaja :urakka-id urakka-id})))

(defn laheta-tekstiviesti-laatupoikkeamasta-selvitys-pyydetty
  "Lähettää urakoitsijan urakan vastuuhenkilölle tekstiviestillä tiedon siitä, että laatupoikkeamasta
   on pyydetty selvitys urakoitsijalta."
  [{:keys [db fim sms urakka-id laatupoikkeama selvityksen-pyytaja]}]
  (when (ominaisuus-kaytossa? :laatupoikkeaman-selvityspyynnosta-lahtee-viesti)
    (laheta-tekstiviesti-laatupoikkeamasta-selvitys-pyydetty*
      {:db db :fim fim :sms sms :laatupoikkeama laatupoikkeama
       :selvityksen-pyytaja selvityksen-pyytaja :urakka-id urakka-id})))
