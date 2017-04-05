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
            [harja.palvelin.komponentit.fim :as fim]
            [harja.palvelin.palvelut.yllapitokohteet.yleiset :as yy])
  (:use [slingshot.slingshot :only [try+ throw+]]))

(defn formatoi-ilmoittaja [{:keys [etunimi sukunimi puhelin organisaatio] :as merkitsija}]
  (cond (and etunimi sukunimi)
        (str etunimi " " sukunimi
             (when puhelin
               (str " (puh. " puhelin ")"))
             (when organisaatio
               (str " (org. " (:nimi organisaatio) ")")))

        organisaatio
        (:nimi organisaatio)
        :default ""))

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
                              ["Merkitsijä" (formatoi-ilmoittaja ilmoittaja)]
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
                              ["Merkitsijä" (formatoi-ilmoittaja ilmoittaja)]
                              ["Merkitsijän urakka" tiemerkintaurakka-nimi]])]))

(defn- viesti-kohteiden-tiemerkinta-valmis [kohteet valmistumispvmt ilmoittaja]
  (html
    [:div
     [:p "Seuraaville tiemerkintäkohteille on merkitty valmistumispäivämäärä:"]
     (for [{:keys [id kohde-nimi tr-numero tr-alkuosa tr-alkuetaisyys tr-loppuosa tr-loppuetaisyys
                   tiemerkintaurakka-nimi paallystysurakka-nimi] :as kohteet} kohteet]
       [:div (html-tyokalut/taulukko [["Tiemerkintäurakka" paallystysurakka-nimi]
                                      ["Kohde" kohde-nimi]
                                      ["TR-osoite" (tierekisteri/tierekisteriosoite-tekstina
                                                     {:tr-numero tr-numero
                                                      :tr-alkuosa tr-alkuosa
                                                      :tr-alkuetaisyys tr-alkuetaisyys
                                                      :tr-loppuosa tr-loppuosa
                                                      :tr-loppuetaisyys tr-loppuetaisyys}
                                                     {:teksti-tie? false})]
                                      ["Tiemerkintä valmistunut" (fmt/pvm (get valmistumispvmt id))]
                                      ["Tiemerkintäurakka" tiemerkintaurakka-nimi]])
        [:br]])
     [:div
      (html-tyokalut/taulukko [["Merkitsijä" (formatoi-ilmoittaja ilmoittaja)]])
      [:br]]]))

;; Sisäinen käsittely

(defn- kasittele-yhden-paallystysurakan-tiemerkityt-kohteet
  [{:keys [db fim email paallystysurakka-id yhden-paallystysurakan-kohde-idt ilmoittaja valmistumispvmt]}]
  (let [urakka-sampoid (urakat-q/hae-urakan-sampo-id db {:urakka paallystysurakka-id})
        kohteiden-tiedot (q/hae-yllapitokohteiden-tiedot-sahkopostilahetykseen
                           db
                           {:idt yhden-paallystysurakan-kohde-idt})
        eka-kohde (first kohteiden-tiedot)
        eka-kohde-osoite {:numero (:tr-numero eka-kohde)
                          :alkuosa (:tr-alkuosa eka-kohde)
                          :alkuetaisyys (:tr-alkuetaisyys eka-kohde)
                          :loppuosa (:tr-loppuosa eka-kohde)
                          :loppuetaisyys (:tr-loppuetaisyys eka-kohde)}]
    (viestinta/laheta-sposti-fim-kayttajarooleille
      {:fim fim
       :email email
       :urakka-sampoid urakka-sampoid
       :fim-kayttajaroolit #{"ely urakanvalvoja" "urakan vastuuhenkilö"}
       :viesti-otsikko
       (if (> (count yhden-paallystysurakan-kohde-idt) 1)
         (format "Urakan '%s' tiemerkintäkohteita merkitty valmistuneeksi" (:paallystysurakka-nimi eka-kohde))
         (format "Urakan '%s' kohteen '%s' tiemerkintä on merkitty valmistuneeksi %s"
                 (:paallystysurakka-nimi eka-kohde)
                 (or (:kohde-nimi eka-kohde)
                     (tierekisteri/tierekisteriosoite-tekstina eka-kohde-osoite))
                 (get valmistumispvmt (:id eka-kohde))))
       :viesti-body (if (> (count yhden-paallystysurakan-kohde-idt) 1)
                      (viesti-kohteiden-tiemerkinta-valmis kohteiden-tiedot valmistumispvmt ilmoittaja)
                      (viesti-kohteen-tiemerkinta-valmis
                        {:paallystysurakka-nimi (:paallystysurakka-nimi eka-kohde)
                         :tiemerkintaurakka-nimi (:tiemerkintaurakka-nimi eka-kohde)
                         :urakan-nimi (:paallystysurakka-nimi eka-kohde)
                         :kohde-nimi (:kohde-nimi eka-kohde)
                         :kohde-osoite (:kohde-osoite eka-kohde)
                         :tiemerkinta-valmis (get valmistumispvmt (:id eka-kohde))
                         :ilmoittaja (:ilmoittaja eka-kohde)}))})))

(defn- laheta-sposti-tiemerkinta-valmis
  "Lähettää päällystysurakoitsijalle sähköpostiviestillä ilmoituksen
   ylläpitokohteen tiemerkinnän valmistumisesta.

   Ilmoittaja on map, jossa ilmoittajan etunimi, sukunimi, puhelinnumero ja organisaation tiedot."
  [{:keys [db fim email kohde-idt ilmoittaja valmistumispvmt]}]
  (when-not (empty? kohde-idt)
    (log/debug (format "Lähetetään sähköposti tiemerkintäkohteiden %s valmistumisesta." (pr-str kohde-idt)))
    (let [kohteiden-tiedot
          (into [] (q/hae-yllapitokohteiden-tiedot-sahkopostilahetykseen
                     db
                     {:idt kohde-idt}))
          paallystysurakoiden-kohteet (group-by :paallystysurakka-id kohteiden-tiedot)]
      (doseq [kohteet (vals paallystysurakoiden-kohteet)]
        (kasittele-yhden-paallystysurakan-tiemerkityt-kohteet
          {:db db :fim fim :email email :paallystysurakka-id (:paallystysurakka-id (first kohteet))
           :yhden-paallystysurakan-kohde-idt (map :id kohteet)
           :ilmoittaja ilmoittaja :valmistumispvmt valmistumispvmt})))))

(defn- laheta-sposti-kohde-valmis-merkintaan
  "Lähettää tiemerkintäurakoitsijalle sähköpostiviestillä ilmoituksen
   ylläpitokohteen valmiudesta tiemerkintään.

   Ilmoittaja on map, jossa ilmoittajan etunimi, sukunimi, puhelinnumero ja organisaation tiedot."
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

;; Viestien lähetykset (julkinen rajapinta)

(defn suodata-tiemerkityt-kohteet-viestintaan
  "Ottaa ylläpitokohteiden viimeisimmät tiedot ja palauttaa ne kohteet, joiden tiemerkintä valmistuu
   (valmistumispvm on kannassa null ja uusissa tiedoissa annettu TAI uusi pvm on eri kuin kannassa).
   HUOM! Täytyy suorittaa ennen kuin viimeisimmät tiedot on ajettu kantaan."
  [db uudet-kohdetiedot]
  (let [kohde-idt (map :id uudet-kohdetiedot)
        kohteet-kannassa (into [] (q/hae-yllapitokohteiden-tiedot-sahkopostilahetykseen
                                    db {:idt kohde-idt}))
        nyt-valmistuneet-kohteet (filter (fn [tallennettava-kohde]
                                           (let [kohde-kannassa (first (filter #(= (:id tallennettava-kohde) (:id %))
                                                                               kohteet-kannassa))]
                                             (or (and (nil? (:aikataulu-tiemerkinta-loppu kohde-kannassa))
                                                      (some? (:aikataulu-tiemerkinta-loppu tallennettava-kohde)))
                                                 (and (some? (:aikataulu-tiemerkinta-loppu kohde-kannassa))
                                                      (some? (:aikataulu-tiemerkinta-loppu tallennettava-kohde))
                                                      (not= (:aikataulu-tiemerkinta-loppu tallennettava-kohde)
                                                            (:aikataulu-tiemerkinta-loppu kohde-kannassa))))))
                                         uudet-kohdetiedot)]
    nyt-valmistuneet-kohteet))

(defn valita-tieto-valmis-tiemerkintaan? [vanha-tiemerkintapvm nykyinen-tiemerkintapvm]
  (boolean (or (and (nil? vanha-tiemerkintapvm)
                    (some? nykyinen-tiemerkintapvm))
               (and (some? vanha-tiemerkintapvm)
                    (some? nykyinen-tiemerkintapvm)
                    (not= vanha-tiemerkintapvm nykyinen-tiemerkintapvm)))))

(defn valita-tieto-tiemerkinnan-valmistumisesta
  "Välittää tiedon annettujen kohteiden tiemerkinnän valmitumisesta.."
  [{:keys [db kayttaja fim email valmistuneet-kohteet]}]
  (when (not (empty? valmistuneet-kohteet))
    (laheta-sposti-tiemerkinta-valmis {:db db :fim fim :email email
                                       :kohde-idt (mapv :id valmistuneet-kohteet)
                                       :valmistumispvmt
                                       (zipmap (map :id valmistuneet-kohteet)
                                               (map :aikataulu-tiemerkinta-loppu valmistuneet-kohteet))
                                       :ilmoittaja kayttaja})))

(defn valita-tieto-kohteen-valmiudesta-tiemerkintaan
  "Välittää tiedon kohteen valmiudesta tiemerkintään."
  [{:keys [db fim email kohde-id tiemerkintapvm kayttaja]}]
  (laheta-sposti-kohde-valmis-merkintaan {:db db :fim fim :email email
                                          :kohde-id kohde-id
                                          :tiemerkintapvm tiemerkintapvm
                                          :ilmoittaja kayttaja}))