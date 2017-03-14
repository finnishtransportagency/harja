(ns harja.palvelin.palvelut.tietyoilmoitukset.pdf
  "PDF-tulosteen muodostaminen tietyöilmoituksen tiedoista.
  Palauttaa XSL-FO kuvauksen hiccup muodossa."
  (:require [harja.tyokalut.xsl-fo :as xsl-fo]
            [harja.pvm :as pvm]
            [clojure.string :as str]))


(def ^:private border "solid 0.1mm black")

(def ^:private borders {:border-bottom border
                        :border-top border
                        :border-left border
                        :border-right border})

(defn- taulukko [otsikko osiot-ja-sisalto]
  [:fo:table (merge borders {:table-layout "fixed"})
   [:fo:table-column {:column-width "4cm"}]
   [:fo:table-column]
   [:fo:table-body
    (when otsikko
      [:fo:table-row borders
       [:fo:table-cell (merge borders {:number-columns-spanned 2})
        [:fo:block otsikko]]])
    (for [[osio sisalto] osiot-ja-sisalto]
      [:fo:table-row borders
       [:fo:table-cell borders
        [:fo:block {:font-weight "bold"} osio]]
       [:fo:table-cell
        borders
        [:fo:block sisalto]]])]])

(defn- tieto [otsikko sisalto]
  [:fo:block {:margin-bottom "2mm"}
   [:fo:block {:font-weight "bold" :font-size "10pt"} otsikko]
   [:fo:block sisalto]])

(defn- tietotaulukko [& tietorivi]
  (let [max-columns (reduce max 0 (map count tietorivi))
        w (str (int (/ 100.0 max-columns)) "%")]
    [:fo:table
     (for [i (range max-columns)]
       [:fo:table-column {:column-width w}])
     [:fo:table-body
      (for [rivi tietorivi
            :let [columns (count rivi)
                  colspan (if (= columns max-columns) 1
                              (/ max-columns columns))]]
        [:fo:table-row
         (for [solu rivi]
           [:fo:table-cell (merge borders {:number-columns-spanned colspan})
            solu])])]]))

(defn- ilmoitus-koskee [_] "FIXME: koskee mitä?")

(defn- tyon-tyyppi [{tyypit :tyotyypit}]
  [:fo:block
   (for [{:keys [tyyppi selite]} tyypit]
     [:fo:block
      [:fo:inline {:font-weight "bold"} tyyppi]
      " "
      selite])])

(defn- yhteyshenkilo [etunimi sukunimi matkapuhelin]
  (str etunimi " " sukunimi
       (when matkapuhelin
         (str " (puh. " matkapuhelin ")"))))

(defn- kohteen-tiedot [{:keys [tien_nimi
                               tr_numero tr_alkuosa tr_alkuetaisyys tr_loppuosa tr_loppuetaisyys
                               alkusijainnin_kuvaus loppusijainnin_kuvaus
                               urakka_nimi
                               tilaajayhteyshenkilo_etunimi tilaajayhteyshenkilo_sukunimi
                               tilaajayhteyshenkilo_matkapuhelin
                               urakoitsijayhteyshenkilo_etunimi urakoitsijayhteyshenkilo_sukunimi
                               urakoitsijayhteyshenkilo_matkapuhelin
                               alku loppu
                               kunnat] :as tietyoilmoitus}]
  (tietotaulukko
   [(tieto "Projekti / Urakka" urakka_nimi)]

   [(tieto "Urakoitsijan yhteyshenkilö ja puh."
           (yhteyshenkilo urakoitsijayhteyshenkilo_etunimi urakoitsijayhteyshenkilo_sukunimi
                          urakoitsijayhteyshenkilo_matkapuhelin))]

   [(tieto  "Tilaajan yhteyshenkilö ja puh."
            (yhteyshenkilo tilaajayhteyshenkilo_etunimi tilaajayhteyshenkilo_sukunimi
                           tilaajayhteyshenkilo_matkapuhelin))]

   [(tieto "Tien numero ja nimi"
           (str tr_numero " " tien_nimi))
    (tieto "Kunnat" kunnat)]

   [(tieto "Työn alkupiste (osa/etäisyys) ja kuvaus"
           (str tr_alkuosa " / " tr_alkuetaisyys " " alkusijainnin_kuvaus))
    (tieto "Työn alku- ja loppupvm"
           (str (pvm/pvm-opt alku) " \u2013 " (pvm/pvm-opt loppu)))]

   [(tieto "Työn loppupiste (osa/etäisyys) ja kuvaus"
           (str tr_loppuosa " / " tr_loppuetaisyys " " loppusijainnin_kuvaus))
    (tieto "Työn pituus"
           "FIXME: laske pituus metreinä")]
   ))


(defn- tyovaihe [tietyoilmoitus]
  "FOO")

(defn- tyoaika [{tyoajat :tyoajat}]
  (apply tietotaulukko
         (for [{:keys [alku loppu viikonpaivat]} tyoajat]
           [(tieto "Alku" (pvm/pvm-opt alku))
            (tieto "Loppu" (pvm/pvm-opt loppu))
            (tieto "Viikonpäivät" (str/join ", " viikonpaivat))])))

(defn checkbox-lista [vaihtoehdot valitut]
  [:fo:block
   (for [vaihtoehto vaihtoehdot]
     [:fo:block
      (xsl-fo/checkbox 12 (valitut vaihtoehto))
      " " vaihtoehto])])

(defn- vaikutukset [{:keys [kaistajarjestelyt nopeusrajoitukset tienpinnat kiertotien_mutkaisuus
                            kiertotienpinnat liikenteenohjaus liikenteenohjaaja
                            viivastys_normaali_liikenteessa viivastys_ruuhka_aikana
                            ajoneuvo_max_korkeys ajoneuvo_max_leveys
                            ajoneuvo_max_pituus ajoneuvo_max_paino] :as tietyoilmoitus}]
  (tietotaulukko
   [;; Vasen puoli
    (tietotaulukko
     [(tieto "Kaistajärjestelyt"
             (checkbox-lista ["Yksi ajokaista suljettu"
                              "Yksi ajorata suljettu"
                              "Muu"]
                             #{"Yksi ajokaista suljettu"}))]
     [(tieto "Nopeusrajoitus" "bar")]
     [(tieto "Tien pinta työmaalla" "baz")]
     [(tieto "Kiertotien pituus" "pitkä se on")])

    ;; Oikea puoli
    (tietotaulukko
     [(tieto "Pysäytyksiä"
              "suomirokkia liikennevaloissa")]
     [(tieto "Arvioitu viivytys"
              "montako minsaa")]
     [(tieto "Kulkurajoituksia" "onko niitä")])]))

(def ^:private osiot
  [["1 Ilmoitus koskee" #'ilmoitus-koskee]
   ["2 Tiedot koko kohteesta" #'kohteen-tiedot]
   ["3 Työvaihe" #'tyovaihe]
   ["4 Työn tyyppi" #'tyon-tyyppi]
   ["5 Työaika" #'tyoaika]
   ["6 Vaikutukset liikenteelle" #'vaikutukset]
   ["7 Vaikutussuunta" (constantly "vaikutussuuntaa")]
   ["8 Muuta" (constantly "ei mitään muuta")]
   ["9 Ilmoittaja" (constantly "ilmoittajan tiedot")]])

(defn tietyoilmoitus-pdf [tietyoilmoitus]
  (println "ILMOTUS: " (pr-str tietyoilmoitus))
  (xsl-fo/dokumentti
   {}

   (taulukko
    [:fo:block {:text-align "center"}
     "Tämähän on tietyöilmoitus"]
    (for [[osio sisalto-fn] osiot]
      [osio (sisalto-fn tietyoilmoitus)]))))
