(ns harja.palvelin.palvelut.tietyoilmoitukset.pdf
  "PDF-tulosteen muodostaminen tietyöilmoituksen tiedoista.
  Palauttaa XSL-FO kuvauksen hiccup muodossa."
  (:require [harja.tyokalut.xsl-fo :as xsl-fo]
            [harja.pvm :as pvm]
            [clojure.string :as str]
            [harja.domain.tietyoilmoitukset :as t]))


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
       [:fo:table-cell (merge borders {:padding "1mm"})
        [:fo:block {:font-weight "bold"} osio]]
       [:fo:table-cell (merge borders {:padding "1mm"})
        [:fo:block sisalto]]])]])

(defn- tieto [otsikko sisalto]
  [:fo:block {:margin-bottom "2mm"}
   [:fo:block {:font-weight "bold" :font-size "10pt"} otsikko]
   [:fo:block sisalto]])

(defn- tietotaulukko [& tietorivi]
  (let [max-columns (reduce max 1 (map count tietorivi))
        w (str (int (/ 100.0 max-columns)) "%")
        riveja (count tietorivi)]
    [:fo:table
     (for [i (range max-columns)]
       [:fo:table-column {:column-width w}])
     [:fo:table-body
      (map-indexed
       (fn [rivi-idx rivi]
         (let [soluja (count rivi)
               border-bottom (when (< rivi-idx (dec riveja))
                               border)
               columns (count rivi)
               colspan (if (= columns max-columns) 1
                           (/ max-columns columns))]
           [:fo:table-row
            (map-indexed
             (fn [solu-idx solu]
               (let [border-right (when (< solu-idx (dec soluja))
                                    border)]
                 [:fo:table-cell {:padding "1mm"
                                  :border-right border-right
                                  :border-bottom border-bottom
                                  :number-columns-spanned colspan}
                  solu]))
             rivi)]))
       tietorivi)]]))

(defn checkbox-lista [vaihtoehdot valitut]
  [:fo:block
   (for [[otsikko vaihtoehto & sisalto] vaihtoehdot]
     (into [:fo:block
            (xsl-fo/checkbox 12 (contains? valitut vaihtoehto))
            " " otsikko]
           sisalto))])

(defn- ilmoitus-koskee [_]
  (tietotaulukko
   [(checkbox-lista [["Ensimmäinen ilmoitus työstä" true]
                     ["Työvaihetta koskeva ilmoitus" false]]
                    #{true})
    (checkbox-lista [["Korjaus/muutos aiempaan tietoon" true]
                     ["Työn päättymisilmoitus" false]]
                    #{false})]))

(defn- tyon-tyyppi [{tyypit :tyotyypit}]
  [:fo:block
   (for [{:keys [tyyppi selite]} tyypit]
     [:fo:block
      [:fo:inline {:font-weight "bold"} tyyppi]
      " "
      selite])])

(defn- yhteyshenkilo [hlo]
  (str (::t/etunimi hlo) " " (::t/sukunimi hlo)
       (when-let [puh (::t/matkapuhelin hlo)]
         (str " (puh. " puh ")"))))

(defn- kohteen-tiedot [ilm]
  (tietotaulukko
   [(tieto "Projekti / Urakka" (::t/urakka_nimi ilm))]

   [(tieto "Urakoitsijan yhteyshenkilö ja puh."
           (yhteyshenkilo (::t/urakoitsijayhteyshenkilo ilm)))]

   [(tieto  "Tilaajan yhteyshenkilö ja puh."
            (yhteyshenkilo (::t/tilaajayhteyshenkilo ilm)))]

   [(tieto "Tien numero ja nimi"
           (str (::t/tr_numero ilm) " " (::t/tien_nimi ilm)))
    (tieto "Kunnat" (::t/kunnat ilm))]

   [(tieto "Työn alkupiste (osa/etäisyys) ja kuvaus"
           (str (::t/tr_alkuosa ilm) " / " (::t/tr_alkuetaisyys ilm) " " (::t/alkusijainnin_kuvaus ilm)))
    (tieto "Työn alku- ja loppupvm"
           (str (pvm/pvm-opt (::t/alku ilm)) " \u2013 " (pvm/pvm-opt (::t/loppu ilm))))]

   [(tieto "Työn loppupiste (osa/etäisyys) ja kuvaus"
           (str (::t/tr_loppuosa ilm) " / " (::t/tr_loppuetaisyys ilm) " " (::t/loppusijainnin_kuvaus ilm)))
    (tieto "Työn pituus"
           "FIXME: laske pituus metreinä")]
   ))


(defn- tyovaihe [tietyoilmoitus]
  "FOO")

(defn- tyoaika [{tyoajat ::t/tyoajat}]
  (apply tietotaulukko
         (for [ta tyoajat]
           [(tieto "Alku" (pvm/pvm-opt (::t/alku ta)))
            (tieto "Loppu" (pvm/pvm-opt (::t/loppu ta)))
            (tieto "Viikonpäivät" (str/join ", " (::t/paivat ta)))])))



(defn- ajoneuvorajoitukset [ilm]
  (let [raj (::t/ajoneuvorajoitukset ilm)]
    [:fo:block
     (checkbox-lista [["Ulottumarajoituksia" true]]
                     #{(boolean (or (::t/max-leveys raj)
                                    (::t/max-korkeus raj)))})
     [:fo:block {:margin-left "5mm"}
      [:fo:inline-container {:width "1cm"}
       [:fo:block
        [:fo:inline {:text-decoration "underline"}
         (::t/max-korkeus raj)]]]
      " (m, ajoneuvon max. korkeus)"]
     [:fo:block {:margin-left "5mm"}
      [:fo:inline-container {:width "1cm"}
       [:fo:block
        [:fo:inline {:text-decoration "underline"}
         (::t/max-leveys raj)]]]
      " (m, ajoneuvon max. leveys)"]
     (checkbox-lista [["Painorajoitus" true
                       [:fo:inline
                        "  "
                        [:fo:inline {:text-decoration "underline"}
                         (::t/max-paino raj)]
                        " (tonnia)"]]]
                     #{(not (nil? (::t/max-paino raj)))})
     (checkbox-lista [["Kuumennin käytössä (avotuli)" "avotuli"]
                      ["Työkoneita liikenteen seassa" "tyokoneitaLiikenteenSeassa"]]
                     (into #{} (::t/huomautukset ilm)))]))

(defn- vaikutukset [ilm]
  (tietotaulukko
   [;; Vasen puoli
    (tietotaulukko
     [(tieto "Kaistajärjestelyt"
             (checkbox-lista [["Yksi ajokaista suljettu" "ajokaistaSuljettu"]
                              ["Yksi ajorata suljettu" "ajorataSuljettu"]
                              ["Tie suljettu" "tieSuljettu"]]
                             #{(::t/kaistajarjestelyt ilm)}))]
     [(tieto "Nopeusrajoitus" "bar")]
     [(tieto "Tien pinta työmaalla" "baz")]
     [(tieto "Kiertotien pituus" "pitkä se on")])

    ;; Oikea puoli
    (tietotaulukko
     [(tieto "Pysäytyksiä"
              "suomirokkia liikennevaloissa")]
     [(tieto "Arvioitu viivytys"
              "montako minsaa")]
     [(tieto "Kulkurajoituksia"
             [:fo:block
              (ajoneuvorajoitukset ilm)
              ])])]))

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
   {:margin {:left "5mm" :right "5mm" :top "5mm" :bottom "5mm"
             :body "0mm"}}

   (taulukko
    [:fo:block {:text-align "center"}
     [:fo:block {:font-weight "bold"}
      [:fo:block "ILMOITUS LIIKENNETTÄ HAITTAAVASTA TYÖSTÄ"]
      [:fo:block "LIIKENNEVIRASTON LIIKENNEKESKUKSEEN"]]
     [:fo:block
      "Yllättävästä häiriöstä erikseen ilmoitus puhelimitse"
      " urakoitsijan linjalle 0200 21200"]]
    (for [[osio sisalto-fn] osiot]
      [osio (sisalto-fn tietyoilmoitus)]))))
