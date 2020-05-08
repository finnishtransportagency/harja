(ns harja.palvelin.palvelut.yllapitokohteet.viestinta
  "Tässä namespacessa on palveluita ylläpidon urakoiden väliseen sähköpostiviestintään."
  (:require [taoensso.timbre :as log]
            [clojure.core.match :refer [match]]
            [harja.fmt :as fmt]
            [harja.kyselyt.urakat :as urakat-q]
            [taoensso.timbre :as log]
            [harja.tyokalut.html :as html-tyokalut]
            [harja.domain.tierekisteri :as tierekisteri]
            [harja.kyselyt.yllapitokohteet :as q]
            [harja.palvelin.palvelut.viestinta :as viestinta]
            [harja.palvelin.integraatiot.sahkoposti :as sahkoposti]
            [hiccup.core :refer [html]]
            [harja.palvelin.komponentit.fim :as fim]
            [harja.tyokalut.html :refer [sanitoi]]
            [harja.palvelin.palvelut.yllapitokohteet.yleiset :as yy]
            [harja.pvm :as pvm]
            [clj-time.coerce :as c]
            [harja.kyselyt.konversio :as konv])
  (:use [slingshot.slingshot :only [try+ throw+]])
  (:import (java.util Date)))

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

(defn- viesti-kohde-valmis-merkintaan-tai-valmius-peruttu [{:keys [paallystysurakka-nimi kohde-nimi kohde-osoite pituus
                                                                   tiemerkintapvm saate ilmoittaja tiemerkintaurakka-nimi]}]
  (let [peruminen? (nil? tiemerkintapvm)
        tiivistelma (if peruminen?
                      (format "Peruutus: kohde %s ei sittenkään ole vielä valmis tiemerkintään."
                              (or kohde-nimi (tierekisteri/tierekisteriosoite-tekstina kohde-osoite)))
                      (format "Kohde %s on valmis tiemerkintään %s."
                              (or kohde-nimi (tierekisteri/tierekisteriosoite-tekstina kohde-osoite))
                              (fmt/pvm tiemerkintapvm)))]
    (html
      [:div
       [:p (sanitoi tiivistelma)]
       (when saate [:p (sanitoi saate)])
       (html-tyokalut/tietoja [["Kohde" kohde-nimi]
                               ["TR-osoite" (tierekisteri/tierekisteriosoite-tekstina
                                              kohde-osoite
                                              {:teksti-tie? false})]
                               ["Pituus" pituus]
                               ["Valmis tiemerkintään" (if peruminen?
                                                         "Ei vielä tiedossa"
                                                         (fmt/pvm tiemerkintapvm))]
                               ["Tiemerkintäurakka" tiemerkintaurakka-nimi]
                               ["Merkitsijä" (formatoi-ilmoittaja ilmoittaja)]
                               ["Merkitsijän urakka" paallystysurakka-nimi]])])))

(defn- viesti-kohteen-tiemerkinta-valmis [{:keys [paallystysurakka-nimi kohde-nimi kohde-osoite sahkopostitiedot
                                                  tiemerkinta-valmis ilmoittaja tiemerkintaurakka-nimi] :as tiedot}]
  (when (some nil? [paallystysurakka-nimi kohde-nimi kohde-osoite tiemerkinta-valmis ilmoittaja
                    tiemerkintaurakka-nimi])
    (log/error "Lähetetään sähköposti tiemerkintäkohteen valmistumisesta puutteellisilla tiedoilla: " tiedot))

  (html
    [:div
     [:p (sanitoi (format "Kohteen %s tiemerkintä on valmistunut %s."
                          (or kohde-nimi (tierekisteri/tierekisteriosoite-tekstina kohde-osoite))
                          (fmt/pvm tiemerkinta-valmis)))]
     (html-tyokalut/tietoja [["Kohde" kohde-nimi]
                             ["Päällystysurakka" paallystysurakka-nimi]
                             ["Tiemerkintäurakka" tiemerkintaurakka-nimi]
                             ["TR-osoite" (tierekisteri/tierekisteriosoite-tekstina
                                            kohde-osoite
                                            {:teksti-tie? false})]
                             ["Tiemerkintä valmistunut" (fmt/pvm tiemerkinta-valmis)]
                             (when ilmoittaja ["Merkitsijä" (formatoi-ilmoittaja ilmoittaja)])
                             (when-let [saate (:saate sahkopostitiedot)]
                               ["Saate" saate])])]))

(defn- viesti-kohteiden-tiemerkinta-valmis [kohteet valmistumispvmt ilmoittaja]
  (html
    [:div
     [:p (if (every? #(pvm/tanaan? %) (vals valmistumispvmt))
           "Seuraavat kohteet on merkitty tiemerkityiksi tänään:"
           "Seuraaville kohteille on merkitty tiemerkinnän valmistumispäivämäärä:")]
     (for [{:keys [id kohde-nimi tr-numero tr-alkuosa tr-alkuetaisyys tr-loppuosa tr-loppuetaisyys
                   tiemerkintaurakka-nimi paallystysurakka-nimi sahkopostitiedot] :as kohde} kohteet]
       (do
         (when (some nil? [id kohde-nimi tr-numero tr-alkuosa tr-alkuetaisyys tr-loppuosa tr-loppuetaisyys
                           tiemerkintaurakka-nimi paallystysurakka-nimi])
           (log/error "Lähetetään sähköposti tiemerkintäkohteen valmistumisesta puutteellisilla tiedoilla: " kohde))
         [:div (html-tyokalut/tietoja [["Kohde" kohde-nimi]
                                       ["Päällystysurakka" paallystysurakka-nimi]
                                       ["Tiemerkintäurakka" tiemerkintaurakka-nimi]
                                       ["TR-osoite" (tierekisteri/tierekisteriosoite-tekstina
                                                      {:tr-numero tr-numero
                                                       :tr-alkuosa tr-alkuosa
                                                       :tr-alkuetaisyys tr-alkuetaisyys
                                                       :tr-loppuosa tr-loppuosa
                                                       :tr-loppuetaisyys tr-loppuetaisyys}
                                                      {:teksti-tie? false})]
                                       ["Tiemerkintä valmistunut" (fmt/pvm (get valmistumispvmt id))]
                                       (when-let [saate (:saate sahkopostitiedot)]
                                         ["Saate" saate])])
          [:br]]))
     [:div
      (when ilmoittaja
        (html-tyokalut/tietoja [["Merkitsijä" (formatoi-ilmoittaja ilmoittaja)]]))
      [:br]]]))

;; Sisäinen käsittely

(defn- kasittele-yhden-urakan-tiemerkityt-kohteet
  "Ottaa joko yhden päällystys- tai tiemerkintäurakan tiemerkityt kohteet.

  Params:
  nakokulma           :paallystys tai :tiemerkinta, kertoo mistä näkökulmasta valmistumista tarkastellaan,
                      vaikuttaa viestin ulkoasuun.
  urakka-sampo-id     on sen urakan sampo-id, jolle maili on tarkoitus lähettää"
  [{:keys [fim email yhden-urakan-kohteet ilmoittaja nakokulma urakka-sampo-id]}]
  (let [valmistumispvmt (fn [kohteet]
                          (zipmap (map :id kohteet)
                                  (map :aikataulu-tiemerkinta-loppu kohteet)))
        viestin-otsikko (fn [kohteet]
                          (let [eka-kohde (first kohteet)
                                eka-kohde-osoite {:numero (:tr-numero eka-kohde)
                                                  :alkuosa (:tr-alkuosa eka-kohde)
                                                  :alkuetaisyys (:tr-alkuetaisyys eka-kohde)
                                                  :loppuosa (:tr-loppuosa eka-kohde)
                                                  :loppuetaisyys (:tr-loppuetaisyys eka-kohde)}
                                nakokulma-urakka-nimi (case nakokulma
                                                        :paallystys (:paallystysurakka-nimi eka-kohde)
                                                        :tiemerkinta (:tiemerkintaurakka-nimi eka-kohde))]
                            (if (> (count kohteet) 1)
                              (format "Urakan '%s' kohteita merkitty tiemerkityksi"
                                      nakokulma-urakka-nimi)
                              (format "Urakan '%s' kohteen '%s' tiemerkintä on merkitty valmistuneeksi %s"
                                      nakokulma-urakka-nimi
                                      (or (:kohde-nimi eka-kohde)
                                          (tierekisteri/tierekisteriosoite-tekstina eka-kohde-osoite))
                                      (fmt/pvm-opt (get (valmistumispvmt kohteet) (:id eka-kohde)))))))
        viestin-vartalo (fn [kohteet]
                          (let [eka-kohde (first kohteet)]
                            (if (> (count kohteet) 1)
                              (viesti-kohteiden-tiemerkinta-valmis kohteet (valmistumispvmt kohteet) ilmoittaja)
                              (viesti-kohteen-tiemerkinta-valmis
                                {:paallystysurakka-nimi (:paallystysurakka-nimi eka-kohde)
                                 :tiemerkintaurakka-nimi (:tiemerkintaurakka-nimi eka-kohde)
                                 :urakan-nimi (:paallystysurakka-nimi eka-kohde)
                                 :kohde-nimi (:kohde-nimi eka-kohde)
                                 :sahkopostitiedot (:sahkopostitiedot eka-kohde)
                                 :kohde-osoite {:tr-numero (:tr-numero eka-kohde)
                                                :tr-alkuosa (:tr-alkuosa eka-kohde)
                                                :tr-alkuetaisyys (:tr-alkuetaisyys eka-kohde)
                                                :tr-loppuosa (:tr-loppuosa eka-kohde)
                                                :tr-loppuetaisyys (:tr-loppuetaisyys eka-kohde)}
                                 :tiemerkinta-valmis (get (valmistumispvmt kohteet) (:id eka-kohde))
                                 :ilmoittaja ilmoittaja}))))
        muut-vastaanottajat-set (set (mapcat #(get-in % [:sahkopostitiedot :muut-vastaanottajat]) yhden-urakan-kohteet))]


    ;; Laitetaan viesti valmistuneista kohteista FIM-käyttäjille.
    (viestinta/laheta-sposti-fim-kayttajarooleille
      {:fim fim
       :email email
       :urakka-sampoid urakka-sampo-id
       :fim-kayttajaroolit #{"ely urakanvalvoja" "urakan vastuuhenkilö" "ely rakennuttajakonsultti"}
       :viesti-otsikko (viestin-otsikko yhden-urakan-kohteet)
       :viesti-body (viestin-vartalo yhden-urakan-kohteet)})

    ;; Muut vastaanottajat voivat periaatteessa olla keitä tahansa. Laitetaan viesti tiemerkinnän näkökulmasta.
    ;; Laita yksi maili per vastaanottaja ja kerää siihen kaikki ne kohteet, joista tulee informoida ko. osoitetta
    (when (= nakokulma :tiemerkinta)
      (doseq [muu-vastaanottaja muut-vastaanottajat-set]
        (try
          (let [vastaanottajan-kohde-idt (set (map :id
                                                   (filter #((get-in % [:sahkopostitiedot :muut-vastaanottajat]) muu-vastaanottaja)
                                                           yhden-urakan-kohteet)))
                vastaanottajan-kohteet (filter #(vastaanottajan-kohde-idt (:id %)) yhden-urakan-kohteet)]

            (when (not (empty? vastaanottajan-kohteet))
              (sahkoposti/laheta-viesti!
                email
                (sahkoposti/vastausosoite email)
                muu-vastaanottaja
                (str "Harja: " (viestin-otsikko vastaanottajan-kohteet))
                (viestin-vartalo vastaanottajan-kohteet))))
          (catch Exception e
            (log/error (format "Sähköpostin lähetys muulle vastaanottajalle %s epäonnistui. Virhe: %s"
                               muu-vastaanottaja (pr-str e)))))))

    ;; Mikäli ilmoittaja halusi kopion jonkun kohteen valmistumisen sähköpostin lähetyksestä, tehdään näin:
    ;; - Kerätään kaikki valmistuneet kohteet, joista halutaan kopio
    ;; - Laitetaan ilmoittajalle yksi maili, jossa kerrotaan valmistuneet kohteet sekä se, että niistä on informoitu
    ;; haluttuja käyttäjiä.
    (when (= nakokulma :tiemerkinta)
      (let [kopio-maili-kohteet (filter (fn [kohde]
                                          (get-in kohde [:sahkopostitiedot :kopio-lahettajalle?]))
                                        yhden-urakan-kohteet)]
        (when (not (empty? kopio-maili-kohteet))
          (viestinta/laheta-sahkoposti-itselle
            {:email email
             :kopio-viesti "Tiedoksenne, että seuraavista valmistuneista tiemerkintäkohteita on välitetty sähköposti-ilmoitus Harjasta urakanvalvojalle, urakoitsijan vastuuhenkilölle, rakennuttajakonsultille sekä valituille muille vastaanottajille."
             :sahkoposti (:sahkoposti ilmoittaja)
             :viesti-otsikko (viestin-otsikko kopio-maili-kohteet)
             :viesti-body (viestin-vartalo kopio-maili-kohteet)}))))))

(defn- laheta-sposti-tiemerkinta-valmis
  "Käy kohteet läpi päällystys- sekä tiemerkintäurakkakohtaisesti ja
   lähettää urakoitsijalle sähköpostiviestillä ilmoituksen tiemerkityistä kohteista.

   Ilmoittaja on map, jossa ilmoittajan etunimi, sukunimi, puhelinnumero ja organisaation tiedot."
  [{:keys [fim email kohteiden-tiedot ilmoittaja]}]
  (log/debug (format "Lähetetään sähköposti tiemerkintäkohteiden %s valmistumisesta." (pr-str (map :id kohteiden-tiedot))))
  (let [paallystysurakoiden-kohteet (group-by :paallystysurakka-id kohteiden-tiedot)
        tiemerkintaurakoiden-kohteet (group-by :tiemerkintaurakka-id kohteiden-tiedot)]
    (doseq [urakan-kohteet (vals paallystysurakoiden-kohteet)]
      (kasittele-yhden-urakan-tiemerkityt-kohteet
        {:fim fim :email email
         :yhden-urakan-kohteet urakan-kohteet
         :nakokulma :paallystys
         :urakka-sampo-id (:paallystysurakka-sampo-id (first urakan-kohteet))
         :ilmoittaja ilmoittaja}))
    ;; Tiemerkintäurakkakohtaiset mailit
    (doseq [urakan-kohteet (vals tiemerkintaurakoiden-kohteet)]
      (kasittele-yhden-urakan-tiemerkityt-kohteet
        {:fim fim :email email
         :yhden-urakan-kohteet urakan-kohteet
         :nakokulma :tiemerkinta
         :urakka-sampo-id (:tiemerkintaurakka-sampo-id (first urakan-kohteet))
         :ilmoittaja ilmoittaja}))))

(defn- laheta-sposti-kohde-valmis-merkintaan-tai-valmius-peruttu
  "Lähettää tiemerkintäurakoitsijalle sähköpostiviestillä ilmoituksen
   ylläpitokohteen valmiudesta tiemerkintään tai tiedon valmiuden perumisesta jos tiemerkintapvm nil.

   Ilmoittaja on map, jossa ilmoittajan etunimi, sukunimi, puhelinnumero ja organisaation tiedot."
  [{:keys [fim email kohteen-tiedot tiemerkintapvm kopio-itselle? saate muut-vastaanottajat ilmoittaja]}]
  (let [{:keys [kohde-nimi tr-numero tr-alkuosa tr-alkuetaisyys tr-loppuosa tr-loppuetaisyys
                tiemerkintaurakka-sampo-id paallystysurakka-nimi
                tiemerkintaurakka-nimi pituus]} kohteen-tiedot
        kohde-osoite {:tr-numero tr-numero
                      :tr-alkuosa tr-alkuosa
                      :tr-alkuetaisyys tr-alkuetaisyys
                      :tr-loppuosa tr-loppuosa
                      :tr-loppuetaisyys tr-loppuetaisyys}
        valmiuden-peruminen? (nil? tiemerkintapvm)
        viestin-otsikko (if valmiuden-peruminen?
                          (format "Kohteen '%s' tiemerkintävalmius peruttu"
                                  (or kohde-nimi (tierekisteri/tierekisteriosoite-tekstina kohde-osoite)))
                          (format "Kohteen '%s' tiemerkinnän voi aloittaa %s"
                                  (or kohde-nimi (tierekisteri/tierekisteriosoite-tekstina kohde-osoite))
                                  (fmt/pvm tiemerkintapvm)))
        viestin-params {:paallystysurakka-nimi paallystysurakka-nimi
                        :kohde-nimi kohde-nimi
                        :kohde-osoite kohde-osoite
                        :pituus pituus
                        :tiemerkintapvm tiemerkintapvm
                        :saate saate
                        :ilmoittaja ilmoittaja
                        :tiemerkintaurakka-nimi tiemerkintaurakka-nimi}
        viestin-vartalo (viesti-kohde-valmis-merkintaan-tai-valmius-peruttu viestin-params)]
    (when tiemerkintaurakka-sampo-id ;; Kohteella ei välttämättä ole tiemerkintäurakkaa
      (log/debug (format "Lähetetään sähköposti: ylläpitokohde %s valmis tiemerkintään %s" kohde-nimi tiemerkintapvm))
      (viestinta/laheta-sposti-fim-kayttajarooleille
        {:fim fim
         :email email
         :urakka-sampoid tiemerkintaurakka-sampo-id
         :fim-kayttajaroolit #{"ely urakanvalvoja" "urakan vastuuhenkilö" "ely rakennuttajakonsultti"}
         :viesti-otsikko viestin-otsikko
         :viesti-body viestin-vartalo})
      (doseq [muu-vastaanottaja muut-vastaanottajat]
        (try
          (sahkoposti/laheta-viesti!
            email
            (sahkoposti/vastausosoite email)
            muu-vastaanottaja
            (str "Harja: " viestin-otsikko)
            viestin-vartalo)
          (catch Exception e
            (log/error (format "Sähköpostin lähetys muulle vastaanottajalle %s epäonnistui. Virhe: %s"
                               muu-vastaanottaja (pr-str e))))))
      (when (and kopio-itselle? (:sahkoposti ilmoittaja))
        (viestinta/laheta-sahkoposti-itselle
          {:email email
           :kopio-viesti "Tämä viesti on kopio sähköpostista, joka lähettiin Harjasta urakanvalvojalle, urakoitsijan vastuuhenkilölle ja rakennuttajakonsultille."
           :sahkoposti (:sahkoposti ilmoittaja)
           :viesti-otsikko viestin-otsikko
           :viesti-body viestin-vartalo})))))

;; Viestien lähetykset (julkinen rajapinta)

(defn suodata-tiemerkityt-kohteet-viestintaan
  "Ottaa ylläpitokohteiden viimeisimmät tiedot ja palauttaa ne kohteet, joiden tiemerkintä valmistuu
   (valmistumispvm on kannassa null ja uusissa tiedoissa annettu TAI uusi pvm on eri kuin kannassa)."
  [kohteet-kannassa uudet-kohdetiedot]
  (let [nyt-valmistuneet-kohteet
        (filter
          (fn [tallennettava-kohde]
            (let [kohde-kannassa (first (filter #(= (:id tallennettava-kohde) (:id %)) kohteet-kannassa))
                  tiemerkinta-loppupvm-kannassa (:aikataulu-tiemerkinta-loppu kohde-kannassa)
                  uusi-tiemerkinta-loppupvm (:aikataulu-tiemerkinta-loppu tallennettava-kohde)]
              (boolean (or
                         ;; Tiemerkintäpvm annetaan ensimmäistä kertaa
                         (and (nil? tiemerkinta-loppupvm-kannassa)
                              (some? uusi-tiemerkinta-loppupvm))
                         ;; Tiemerkintäpvm päivitetään
                         (and (some? tiemerkinta-loppupvm-kannassa)
                              (some? uusi-tiemerkinta-loppupvm)
                              (not (pvm/sama-tyyppiriippumaton-pvm?
                                     tiemerkinta-loppupvm-kannassa
                                     uusi-tiemerkinta-loppupvm)))))))
          uudet-kohdetiedot)]
    nyt-valmistuneet-kohteet))

(defn valita-tieto-valmis-tiemerkintaan? [vanha-tiemerkintapvm nykyinen-tiemerkintapvm]
  (boolean (or (and (nil? vanha-tiemerkintapvm)
                    (some? nykyinen-tiemerkintapvm))
               (and (some? vanha-tiemerkintapvm)
                    (some? nykyinen-tiemerkintapvm)
                    (not (pvm/sama-tyyppiriippumaton-pvm? vanha-tiemerkintapvm nykyinen-tiemerkintapvm))))))

(defn valita-tieto-peru-valmius-tiemerkintaan? [vanha-tiemerkintapvm nykyinen-tiemerkintapvm]
  (boolean (and (some? vanha-tiemerkintapvm)
                (nil? nykyinen-tiemerkintapvm))))

(defn valita-tieto-tiemerkinnan-valmistumisesta
  "Välittää tiedon annettujen kohteiden tiemerkinnän valmistumisesta.

   Kun käsittely on valmis, poistaa annettujen kohteiden odottavat sähköpostitiedot."
  [{:keys [db kayttaja fim email valmistuneet-kohteet viestit-lahetetty-fn]}]
  (laheta-sposti-tiemerkinta-valmis {:fim fim :email email
                                     :kohteiden-tiedot valmistuneet-kohteet
                                     :ilmoittaja kayttaja})
  (q/poista-valmistuneen-tiemerkinnan-sahkopostitiedot! db {:yllapitokohde_id (map :id valmistuneet-kohteet)}))

(defn valita-tieto-kohteen-valmiudesta-tiemerkintaan
  "Välittää tiedon kohteen valmiudesta tiemerkintään tai valmiuden perumisesta."
  [{:keys [fim email kohteen-tiedot tiemerkintapvm
           kopio-itselle? saate kayttaja muut-vastaanottajat]}]
  (laheta-sposti-kohde-valmis-merkintaan-tai-valmius-peruttu
    {:fim fim :email email :kohteen-tiedot kohteen-tiedot
     :tiemerkintapvm tiemerkintapvm
     :kopio-itselle? kopio-itselle?
     :saate saate
     :muut-vastaanottajat muut-vastaanottajat
     :ilmoittaja kayttaja}))


;; Lähetä viesti paikkauksien tarkastuksessa havaitusta virheestä
(defn- viesti-paikkaustoteumassa-virhe [{:keys [kohde-nimi rivien-lukumaara pinta-ala-summa massamenekki-summa
                                                saate ilmoittaja]}]
  (let [tiivistelma (format "Virhe kohteen '%s' paikkaustoteumassa. Tarkista ja korjaa tiedot urakoitsijan järjestelmässä ja lähetä kohteen tiedot uudelleen Harjaan." kohde-nimi)]
    (html
      [:div
       [:p (sanitoi tiivistelma)]
       [:h3 "Kohteen tiedot"]
       (html-tyokalut/tietoja [["Kohde" kohde-nimi]
                               ["Rivejä" rivien-lukumaara]
                               ["Pinta-ala yht." (str pinta-ala-summa "m\u00B2")]
                               ["Massamenekki yht." massamenekki-summa]
                               ["Ilmoittaja" (formatoi-ilmoittaja ilmoittaja)]])
       (when saate [:div
                    [:h3 "Lisätietoa virheestä"]
                    [:p (sanitoi saate)]
                    ])])))

(defn laheta-sposti-urakoitsijalle-paikkauskohteessa-virhe
  "Lähettää tiemerkintäurakoitsijalle sähköpostiviestillä ilmoituksen
   ylläpitokohteen valmiudesta tiemerkintään tai tiedon valmiuden perumisesta jos tiemerkintapvm nil.

   Ilmoittaja on map, jossa ilmoittajan etunimi, sukunimi, puhelinnumero ja organisaation tiedot."
  [{:keys [fim email kopio-itselle? muut-vastaanottajat ilmoittaja
           saate rivien-lukumaara pinta-ala-summa massamenekki-summa urakka-sampo-id] :as tiedot}]
  (let [viestin-otsikko (format "Virhe kohteen '%s' paikkaustoteumassa. Tarkista ja korjaa tiedot."
                                (:harja.domain.paikkaus/nimi tiedot))
        viestin-params {:kohde-nimi (:harja.domain.paikkaus/nimi tiedot)
                        :rivien-lukumaara rivien-lukumaara
                        :pinta-ala-summa pinta-ala-summa
                        :massamenekki-summa massamenekki-summa
                        :saate saate
                        :ilmoittaja ilmoittaja}
        viestin-vartalo (viesti-paikkaustoteumassa-virhe viestin-params)
        viestit-yhteensa (atom 0)
        epaonnistuneet-viestit-yhteensa (atom 0)]
    (when urakka-sampo-id
      (swap! viestit-yhteensa inc)
      (log/debug (format "Lähetetään sähköposti: paikkaustoteumassa %s virhe." (:harja.domain.paikkaus/nimi tiedot)))
      (let [vastaus (viestinta/laheta-sposti-fim-kayttajarooleille
        {:fim fim
         :email email
         :urakka-sampoid urakka-sampo-id
         :fim-kayttajaroolit #{"urakan vastuuhenkilö"}
         :viesti-otsikko viestin-otsikko
         :viesti-body        viestin-vartalo})]
        (if (false? (:onnistui? vastaus))
          (do
            (log/error "FIM käyttäjälle säpön lähetys epäonnistui")
            (swap! epaonnistuneet-viestit-yhteensa inc)))))
    (doseq [muu-vastaanottaja muut-vastaanottajat]
      (log/debug "Muu vastaanottaja menossa, osoite: " (pr-str muu-vastaanottaja))
      (if (not-empty muu-vastaanottaja) ; Tarkistus että säpo-kentässä on jotain sisältöä.
        (do
          (swap! viestit-yhteensa inc)
          (try
            (sahkoposti/laheta-viesti!
              email
              (sahkoposti/vastausosoite email)
              muu-vastaanottaja
              (str "Harja: " viestin-otsikko)
              viestin-vartalo)
            (catch Exception e
              (swap! epaonnistuneet-viestit-yhteensa inc)
              (log/error (format "Sähköpostin lähetys muulle vastaanottajalle %s epäonnistui. Virhe: %s"
                                 muu-vastaanottaja (pr-str e))))))))
    (when (and kopio-itselle? (:sahkoposti ilmoittaja))
      ;(swap! viestit-yhteensa inc) ; TODO: kopiolähetyksen seuraaminen on hyödytäntä koska laheta-sahkoposti-itselle ei palauta true/false flagia onnistumisesta.
      (log/debug (format "Lähetetään kopio paikkaus-virheilmoituksesta sähköpostiin: %s" (:sahkoposti ilmoittaja)))
      (viestinta/laheta-sahkoposti-itselle
        {:email email
         :kopio-viesti "Tämä viesti on kopio sähköpostista, joka lähettiin Harjasta urakoitsijan vastuuhenkilölle."
         :sahkoposti (:sahkoposti ilmoittaja)
         :viesti-otsikko viestin-otsikko
         :viesti-body viestin-vartalo}))
  ; TODO: kun-virhe ei saa täältä :virhe true - datan viestiä käyttöön, vaan näyttäisi että on nil?
  (cond
    (zero? @epaonnistuneet-viestit-yhteensa) {:viesti "Sähköpostit on lähetetty onnistuneesti."}
    (< @epaonnistuneet-viestit-yhteensa @viestit-yhteensa) {:virhe true :viesti "Sähköpostien lähetys epäonnistui osittain. Yritä myöhemmin uudelleen."}
    :else {:virhe true :viesti "Sähköpostien lähetys epäonnistui. Yritä myöhemmin uudelleen."})))