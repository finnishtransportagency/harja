(ns harja.palvelin.palvelut.laadunseuranta.viestinta
  "Tässä namespacessa on palveluita laadunseuranta-asioihin liittyvään teksti-/sähköpostiviestintään."
  (:require [taoensso.timbre :as log]
            [clojure.core.match :refer [match]]
            [harja.tyokalut.html :as html-tyokalut]
            [harja.kyselyt.urakat :as urakat-q]
            [harja.palvelin.integraatiot.sahkoposti :as sahkoposti]
            [hiccup.core :refer [html]]
            [harja.palvelin.komponentit.fim :as fim])
  (:use [slingshot.slingshot :only [try+ throw+]]))

;; Viestien muodostus

(defn- viesti-laatupoikkeamasta-pyydetty-selvitys [{:keys [raportoija kuvaus sijainti aika]}]
  (html
    [:div
     [:p "Seuraavasta laatupoikkeamasta on pyydetty selvitys urakoitsijalta"]
     (html-tyokalut/taulukko [["Raportoija" raportoija]
                              ["Kuvaus" kuvaus]
                              ["Sijainti" sijainti]
                              ["Aika" aika]])]))

;; Sisäinen käsittely

;; Viestien lähetykset (julkinen rajapinta)

(defn laheta-sposti-laatupoikkeamasta-selvitys-pyydetty
  "Lähettää urakoitsijan urakan vastuuhenkilölle tiedon siitä, että laatupoikkeamasta
   on pyydetty selvitys urakoitsijalta."
  [{:keys [db fim email urakka-id laatupoikkeama-id selvityksen-pyytaja]}]
  (log/debug (format "Lähetetään sähköposti: laatupoikkeamasta
  %s pyydetty selvitys" laatupoikkeama-id))
  (try+
    (let [urakka-nimi (:nimi (first (urakat-q/hae-urakka db urakka-id)))
          ilmoituksen-saajat (fim/hae-urakan-kayttajat-jotka-roolissa
                               fim
                               urakka-id
                               #{"urakan vastuuhenkilö"})]
      (if-not (empty? ilmoituksen-saajat)
        (doseq [henkilo ilmoituksen-saajat]
          (sahkoposti/laheta-viesti!
            email
            (sahkoposti/vastausosoite email)
            (:sahkoposti henkilo)
            (format "Harja: Laatupoikkeamasta tehty selvityspyyntö urakassa " urakka-nimi)
            (viesti-laatupoikkeamasta-pyydetty-selvitys {:raportoija selvityksen-pyytaja
                                                         :kuvaus "" ;; TODO
                                                         :sijainti nil ;; TODO
                                                         :aika nil ;; TODO
                                                         })))
        (log/warn (format "Urakalle %s ei löydy FIM:stä henkiöä, jolle ilmoittaa selvitystä vaativasta laatupoikkeamasta."
                          urakka-id))))
    (catch Object e
      (log/error (format "Sähköpostia ei voitu lähettää laatupoikkeaman %s urakan vastuuhenkilölle: %s %s"
                         laatupoikkeama-id e (when (instance? Throwable e)
                                      (.printStackTrace e)))))))