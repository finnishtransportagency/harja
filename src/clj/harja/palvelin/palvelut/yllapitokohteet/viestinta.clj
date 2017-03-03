(ns harja.palvelin.palvelut.yllapitokohteet.viestinta
  "Tässä namespacessa on palveluita ylläpidon urakoiden väliseen sähköpostiviestintään."
  (:require [taoensso.timbre :as log]
            [clojure.core.match :refer [match]]
            [harja.fmt :as fmt]
            [harja.kyselyt.urakat :as urakat-q]
            [harja.tyokalut.html :as html-tyokalut]
            [harja.domain.tierekisteri :as tierekisteri]
            [harja.kyselyt.yllapitokohteet :as q]
            [harja.palvelin.palvelut.viestinta :as viestinta]
            [harja.palvelin.integraatiot.sahkoposti :as sahkoposti]
            [hiccup.core :refer [html]]
            [harja.palvelin.komponentit.fim :as fim])
  (:use [slingshot.slingshot :only [try+ throw+]]))

;; Viestien muodostukset

(defn- viesti-kohde-valmis-merkintaan [{:keys [paallystysurakka-nimi kohde-nimi kohde-osoite
                                               tiemerkintapvm ilmoittaja
                                               tiemerkintaurakka-nimi]}]
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
                              ["Tiemerkintäurakka" tiemerkintaurakka-nimi]
                              ["Merkitsijä" (str (:etunimi ilmoittaja) " " (:sukunimi ilmoittaja)
                                                 (when-let [puhelin (:puhelin ilmoittaja)]
                                                   (str " (" puhelin ")")))]
                              ["Merkitsijän urakka" paallystysurakka-nimi]])]))

(defn- viesti-kohteen-tiemerkinta-valmis [{:keys [paallystysurakka-nimi kohde-nimi kohde-osoite
                                                  tiemerkinta-valmis ilmoittaja tiemerkintaurakka-nimi] :as tiedot}]
  (html
    [:div
     [:p (format "Päällystysurakan '%s' kohteen '%s' tiemerkintä on valmistunut %s."
                 paallystysurakka-nimi
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

(defn- viesti-kohteiden-tiemerkinta-valmis [kohteet ilmoittaja]
  (html
    [:div
     [:p "Seuraavat tiemerkintäkohteet on merkitty valmiiksi:"]
     (for [{:keys [kohde-nimi tr-numero tr-alkuosa tr-alkuetaisyys tr-loppuosa tr-loppuetaisyys
                   aikataulu-tiemerkinta-loppu tiemerkintaurakka-nimi paallystysurakka-nimi] :as kohteet} kohteet]
       [:div (html-tyokalut/taulukko [["Tiemerkintäurakka" paallystysurakka-nimi]
                                      ["Kohde" kohde-nimi]
                                      ["TR-osoite" (tierekisteri/tierekisteriosoite-tekstina
                                                     {:tr-numero tr-numero
                                                      :tr-alkuosa tr-alkuosa
                                                      :tr-alkuetaisyys tr-alkuetaisyys
                                                      :tr-loppuosa tr-loppuosa
                                                      :tr-loppuetaisyys tr-loppuetaisyys}
                                                     {:teksti-tie? false})]
                                      ["Tiemerkintä valmistunut" (fmt/pvm aikataulu-tiemerkinta-loppu)]
                                      ["Tiemerkintäurakka" tiemerkintaurakka-nimi]])
        [:br]])
     [:div
      (html-tyokalut/taulukko [["Merkitsijä" (str (:etunimi ilmoittaja) " " (:sukunimi ilmoittaja)
                                                  (when-let [puhelin (:puhelin ilmoittaja)]
                                                    (str " (" puhelin ")")))]])
      [:br]]]))

;; Sisäinen käsittely

(defn- kasittele-yhden-paallystysurakan-tiemerkityt-kohteet
  [db fim email paallystysurakka-id yhden-paallystysurakan-kohde-idt ilmoittaja]
  (let [urakka-sampoid (urakat-q/hae-urakan-sampo-id db {:urakka paallystysurakka-id})
        kohteiden-tiedot (q/hae-yllapitokohteiden-tiedot-sahkopostilahetykseen
                           db
                           {:idt yhden-paallystysurakan-kohde-idt})
        eka-kohde (first kohteiden-tiedot)]
    (viestinta/laheta-sposti-fim-kayttajarooleille
      {:fim fim
       :email email
       :urakka-sampoid urakka-sampoid
       :fim-kayttajaroolit #{"ely urakanvalvoja" "urakan vastuuhenkilö"}
       :viesti-otsikko
       (if (> (count yhden-paallystysurakan-kohde-idt) 1)
         (format "Urakan '%s' tiemerkintäkohteita valmistunut" (:paallystysurakka-nimi eka-kohde))
         (format "Urakan '%s' kohteen '%s' tiemerkintä on valmistunut %s"
                 (:paallystysurakka-nimi eka-kohde)
                 (or (:kohde-nimi eka-kohde)
                     (tierekisteri/tierekisteriosoite-tekstina (:kohde-osoite eka-kohde)))
                 (fmt/pvm (:aikataulu-tiemerkinta-loppu eka-kohde)))
         :viesti-body) (if (> (count yhden-paallystysurakan-kohde-idt) 1)
                         (viesti-kohteiden-tiemerkinta-valmis kohteiden-tiedot ilmoittaja)
                         (viesti-kohteen-tiemerkinta-valmis
                           {:paallystysurakka-nimi (:paallystysurakka-nimi eka-kohde)
                            :tiemerkintaurakka-nimi (:tiemerkintaurakka-nimi eka-kohde)
                            :urakan-nimi (:paallystysurakka-nimi eka-kohde)
                            :kohde-nimi (:kohde-nimi eka-kohde)
                            :kohde-osoite (:kohde-osoite eka-kohde)
                            :tiemerkinta-valmis (:aikataulu-tiemerkinta-loppu eka-kohde)
                            :ilmoittaja (:ilmoittaja eka-kohde)}))})))

;; Viestien lähetykset (julkinen rajapinta)

(defn laheta-sposti-tiemerkinta-valmis
  "Lähettää päällystysurakoitsijalle sähköpostiviestillä ilmoituksen
   ylläpitokohteen tiemerkinnän valmistumisesta."
  [{:keys [db fim email kohde-idt ilmoittaja]}]
  (when-not (empty? kohde-idt)
    (log/debug (format "Lähetetään sähköposti tiemerkintäkohteiden %s valmistumisesta." (pr-str kohde-idt)))
    (let [kohteiden-tiedot
          (into [] (q/hae-yllapitokohteiden-tiedot-sahkopostilahetykseen
                     db
                     {:idt kohde-idt}))
          paallystysurakoiden-kohteet (group-by :paallystysurakka-id kohteiden-tiedot)]
      (doseq [kohteet (vals paallystysurakoiden-kohteet)]
        (kasittele-yhden-paallystysurakan-tiemerkityt-kohteet
          db fim email (:paallystysurakka-id (first kohteet)) (map :id kohteet) ilmoittaja)))))

(defn laheta-sposti-kohde-valmis-merkintaan
  "Lähettää tiemerkintäurakoitsijalle sähköpostiviestillä ilmoituksen
   ylläpitokohteen valmiudesta tiemerkintään."
  [{:keys [db fim email kohde-id tiemerkintapvm ilmoittaja]}]
  (log/debug (format "Lähetetään sähköposti: ylläpitokohde %s valmis tiemerkintään %s" kohde-id tiemerkintapvm))
  (let [{:keys [kohde-nimi tr-numero tr-alkuosa tr-alkuetaisyys tr-loppuosa tr-loppuetaisyys
                tiemerkintaurakka-sampo-id paallystysurakka-nimi
                tiemerkintaurakka-nimi]}
        (first (q/hae-yllapitokohteiden-tiedot-sahkopostilahetykseen
                 db
                 {:idt [kohde-id]}))
        kohde-osoite {:tr-numero tr-numero
                      :tr-alkuosa tr-alkuosa
                      :tr-alkuetaisyys tr-alkuetaisyys
                      :tr-loppuosa tr-loppuosa
                      :tr-loppuetaisyys tr-loppuetaisyys}]
    (viestinta/laheta-sposti-fim-kayttajarooleille
      {:fim fim
       :email email
       :urakka-sampoid tiemerkintaurakka-sampo-id
       :fim-kayttajaroolit #{"ely urakanvalvoja" "urakan vastuuhenkilö"}
       :viesti-otsikko (format "Kohteen '%s' tiemerkinnän voi aloittaa %s"
                               (or kohde-nimi (tierekisteri/tierekisteriosoite-tekstina kohde-osoite))
                               (fmt/pvm tiemerkintapvm))
       :viesti-body (viesti-kohde-valmis-merkintaan {:paallystysurakka-nimi paallystysurakka-nimi
                                                     :kohde-nimi kohde-nimi
                                                     :kohde-osoite kohde-osoite
                                                     :tiemerkintapvm tiemerkintapvm
                                                     :ilmoittaja ilmoittaja
                                                     :tiemerkintaurakka-nimi tiemerkintaurakka-nimi})})))