(ns harja.palvelin.palvelut.yllapitokohteet.viestinta
  (:require [clojure.test :refer :all]
            [taoensso.timbre :as log]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.testi :refer :all]
            [clojure.core.match :refer [match]]
            [com.stuartsierra.component :as component]
            [harja.pvm :as pvm]
            [harja.fmt :as fmt]
            [harja.tyokalut.html :as html-tyokalut]
            [harja.domain.tierekisteri :as tierekisteri]
            [harja.kyselyt.yllapitokohteet :as q]
            [harja.palvelin.integraatiot.sahkoposti :as sahkoposti]
            [hiccup.core :refer [html]]
            [harja.palvelin.komponentit.fim :as fim])
  (:use [slingshot.slingshot :only [try+ throw+]]))

(defn- viesti-kohde-valmis-merkintaan [{:keys [paallystysurakka-nimi kohde-nimi kohde-osoite
                                               tiemerkintapvm ilmoittaja
                                               tiemerkintaurakka-nimi] :as tiedot}]
  (html
    [:div
     [:p (format "Kohde '%s' on valmis tiemerkintään %s."
                 (or kohde-nimi (tierekisteri/tierekisteriosoite-tekstina kohde-osoite))
                 (fmt/pvm tiemerkintapvm))]
     (html-tyokalut/taulukko [["Kohde" kohde-nimi]
                              ["TR-osoite" (tierekisteri/tierekisteriosoite-tekstina
                                             kohde-osoite
                                             {:teksti-tie? false})]
                              ["Valmis tiemerkintään" (fmt/pvm tiemerkintapvm)]
                              ["Tiemerkinnän suorittaja" tiemerkintaurakka-nimi]
                              ["Merkitsijä" (str (:etunimi ilmoittaja) " " (:sukunimi ilmoittaja)
                                                 (when-let [puhelin (:puhelin ilmoittaja)]
                                                   (str " (" puhelin ")")))]
                              ["Merkitsijän urakka" paallystysurakka-nimi]])]))

(defn sahkoposti-kohde-valmis-merkintaan
  "Lähettää tiemerkintäurakoitsijalle sähköpostiviestillä ilmoituksen
   ylläpitokohteen valmiudesta tiemerkintään."
  [db fim email kohde-id tiemerkintapvm ilmoittaja]
  (log/debug (format "Lähetetään sähköposti: ylläpitokohde %s valmis tiemerkintään %s" kohde-id tiemerkintapvm))
  (try+
    (let [{:keys [kohde-nimi tr-numero tr-alkuosa tr-alkuetaisyys tr-loppuosa tr-loppuetaisyys
                  tiemerkintaurakka-sampo-id paallystysurakka-nimi
                  tiemerkintaurakka-id tiemerkintaurakka-nimi]}
          (first (q/hae-kohteen-tiedot-sahkopostilahetykseen
                   db
                   {:id kohde-id}))
          kohde-osoite {:tr-numero tr-numero
                        :tr-alkuosa tr-alkuosa
                        :tr-alkuetaisyys tr-alkuetaisyys
                        :tr-loppuosa tr-loppuosa
                        :tr-loppuetaisyys tr-loppuetaisyys}
          ilmoituksen-saajat (fim/hae-urakan-kayttajat-jotka-roolissa
                               fim
                               tiemerkintaurakka-sampo-id
                               #{"ely urakanvalvoja" "urakan vastuuhenkilö"})]
      (if-not (empty? ilmoituksen-saajat)
        (doseq [henkilo ilmoituksen-saajat]
          (sahkoposti/laheta-viesti!
            email
            (sahkoposti/vastausosoite email)
            (:sahkoposti henkilo)
            (format "Harja: Kohteen '%s' tiemerkinnän voi aloittaa %s"
                    (or kohde-nimi (tierekisteri/tierekisteriosoite-tekstina kohde-osoite))
                    (fmt/pvm tiemerkintapvm))
            (viesti-kohde-valmis-merkintaan {:paallystysurakka-nimi paallystysurakka-nimi
                                             :kohde-nimi kohde-nimi
                                             :kohde-osoite kohde-osoite
                                             :tiemerkintapvm tiemerkintapvm
                                             :ilmoittaja ilmoittaja
                                             :tiemerkintaurakka-nimi tiemerkintaurakka-nimi})))
        (log/warn (format "Tiemerkintäurakalle %s ei löydy FIM:stä henkiöä, jolle ilmoittaa kohteen valmiudesta tiemerkintään."
                          tiemerkintaurakka-id))))
    (catch Object e
      (log/error (format "Sähköpostia ei voitu lähettää kohteen %s tiemerkitsijälle: %s %s"
                         kohde-id e (when (instance? java.lang.Throwable e)
                                      (.printStackTrace e)))))))


(defn- viesti-tiemerkinta-valmis [{:keys [kohde-nimi kohde-osoite
                                          tiemerkinta-valmis ilmoittaja tiemerkintaurakka-nimi] :as tiedot}]
  (html
    [:div
     [:p (format "Kohteen '%s' tiemerkintä on merkitty valmiiksi %s."
                 (or kohde-nimi (tierekisteri/tierekisteriosoite-tekstina kohde-osoite))
                 (fmt/pvm tiemerkinta-valmis))]
     (html-tyokalut/taulukko [["Kohde" kohde-nimi]
                              ["TR-osoite" (tierekisteri/tierekisteriosoite-tekstina
                                             kohde-osoite
                                             {:teksti-tie? false})]
                              ["Tiemerkintä valmistunut" (fmt/pvm tiemerkinta-valmis)]
                              ["Merkitsijä" (str (:etunimi ilmoittaja) " " (:sukunimi ilmoittaja)
                                                 (when-let [puhelin (:puhelin ilmoittaja)]
                                                   (str " (" puhelin ")")))]
                              ["Merkitsijän urakka" tiemerkintaurakka-nimi]])]))

(defn sahkoposti-tiemerkinta-valmis
  "Lähettää päällystysurakoitsijalle sähköpostiviestillä ilmoituksen
   ylläpitokohteen tiemerkinnän valmistumisesta."
  [db fim email kohde-id tiemerkinta-valmis-pvm ilmoittaja]
  (log/debug (format "Lähetetään sähköposti: ylläpitokohteen %s tiemerkintä valmis" kohde-id))
  (try+
    (let [{:keys [kohde-nimi tr-numero tr-alkuosa tr-alkuetaisyys tr-loppuosa tr-loppuetaisyys
                   paallystysurakka-sampo-id
                   paallystysurakka-id tiemerkintaurakka-nimi]}
           (first (q/hae-kohteen-tiedot-sahkopostilahetykseen
                    db
                    {:id kohde-id}))
          kohde-osoite {:tr-numero tr-numero
                        :tr-alkuosa tr-alkuosa
                        :tr-alkuetaisyys tr-alkuetaisyys
                        :tr-loppuosa tr-loppuosa
                        :tr-loppuetaisyys tr-loppuetaisyys}
          ilmoituksen-saajat (fim/hae-urakan-kayttajat-jotka-roolissa
                               fim
                               paallystysurakka-sampo-id
                               #{"ely urakanvalvoja" "urakan vastuuhenkilö"})]
      (if-not (empty? ilmoituksen-saajat)
        (doseq [henkilo ilmoituksen-saajat]
          (sahkoposti/laheta-viesti!
            email
            (sahkoposti/vastausosoite email)
            (:sahkoposti henkilo)
            (format "Harja: Kohteen '%s' tiemerkintä on valmistunut %s"
                    (or kohde-nimi (tierekisteri/tierekisteriosoite-tekstina kohde-osoite))
                    (fmt/pvm tiemerkinta-valmis-pvm))
            (viesti-tiemerkinta-valmis {:tiemerkintaurakka-nimi tiemerkintaurakka-nimi
                                        :kohde-nimi kohde-nimi
                                        :kohde-osoite kohde-osoite
                                        :tiemerkinta-valmis tiemerkinta-valmis-pvm
                                        :ilmoittaja ilmoittaja})))
        (log/warn (format "Päällystysurakalle %s ei löydy FIM:stä henkiöä, jolle ilmoittaa tiemerkinnän valmistumisesta."
                          paallystysurakka-id))))
    (catch Object e
      (log/error (format "Sähköpostia ei voitu lähettää kohteen %s päällystäjälle: %s %s"
                         kohde-id e (when (instance? java.lang.Throwable e)
                                      (.printStackTrace e)))))))
