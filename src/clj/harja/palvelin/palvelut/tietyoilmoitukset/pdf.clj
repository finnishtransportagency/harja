(ns harja.palvelin.palvelut.tietyoilmoitukset.pdf
  "PDF-tulosteen muodostaminen tietyöilmoituksen tiedoista.
  Palauttaa XSL-FO kuvauksen hiccup muodossa."
  (:require [harja.tyokalut.xsl-fo :as xsl-fo]
            [harja.pvm :as pvm]
            [clojure.string :as str]
            [harja.domain.tietyoilmoitus :as t]
            [harja.domain.tierekisteri :as tr]
            [harja.domain.muokkaustiedot :as m]))


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
   [:fo:block {:font-weight "bold" :font-size 8} otsikko]
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

(defn checkbox [otsikko valittu?]
  [:fo:block
   (xsl-fo/checkbox 8 valittu?)
   " " otsikko])

(defn checkbox-lista [vaihtoehdot valitut]
  [:fo:block
   (for [[otsikko vaihtoehto & sisalto] vaihtoehdot]
     (into [:fo:block
            (xsl-fo/checkbox 8 (contains? valitut vaihtoehto))
            " " otsikko]
           sisalto))])

(defn- ilmoitus-koskee [ilm]
  (let [lahetetty? (:lahetetty? ilm)
        valinnat #{(if lahetetty?
                     :korjaus
                     :ensimmainen)
                   (if (::t/paatietyoilmoitus ilm)
                     :tyovaihe
                     :paailmoitus)
                   (if (pvm/ennen? (pvm/nyt) (::t/loppu ilm))
                     :menossa
                     :paattyy)}]
    (tietotaulukko
     [(checkbox-lista [["Ensimmäinen ilmoitus työstä" :ensimmainen]
                       ["Työvaihetta koskeva ilmoitus" :tyovaihe]]
                      valinnat)
      (checkbox-lista [["Korjaus/muutos aiempaan tietoon" :korjaus]
                       ["Työn päättymisilmoitus" :paattyy]]
                      valinnat)])))

(def tyotyypit ["Tienrakennus"
                "Päällystystyö"
                "Viimeistely"
                "Rakenteen parannus"
                "Jyrsintä-/stabilointityö"
                "Tutkimus/mittaus"
                "Alikulkukäytävän rak."
                "Kaidetyö"
                "Tienvarsilaitteiden huolto"
                "Kevyenliik. väylän rak."
                "Kaapelityö"
                "Silmukka-anturin asent."
                "Siltatyö"
                "Valaistustyö"
                "Tasoristeystyö"
                "Liittymä- ja kaistajärj."
                "Tiemerkintätyö"
                "Vesakonraivaus/niittotyö"
                "Räjäytystyö"
                "Muu, mikä?"])

(defn- tyon-tyyppi [{tyypit ::t/tyotyypit}]
  (let [valitut-tyypit (into {}
                             (map (juxt ::t/tyyppi ::t/kuvaus))
                             tyypit)]
    (apply tietotaulukko
           (for [tyypit (partition-all 3 tyotyypit)
                 :let [checkboxit (mapv #(checkbox % (contains? valitut-tyypit %)) tyypit)]]
             (if (< (count tyypit) 3)
               ;; Viimeinen rivi
               (conj checkboxit
                     [:fo:block (str (valitut-tyypit (last tyypit)))])
               checkboxit)))))

(defn- yhteyshenkilo [hlo]
  (str (::t/etunimi hlo) " " (::t/sukunimi hlo)
       (when-let [puh (::t/matkapuhelin hlo)]
         (str " (puh. " puh ")"))))

(defn- pituus [ilm]
  (some->> ilm ::t/pituus (format "%.1f m")))

(defn- kohteen-tiedot [ilm]
  (let [ilm (or (::t/paailmoitus ilm) ilm)
        osoite (::t/osoite ilm)]
    (tietotaulukko
     [(tieto "Projekti / Urakka" (::t/urakan-nimi ilm))
      (tieto "Urakoitsijan nimi" (::t/urakoitsijan-nimi ilm))]

     [(tieto "Urakoitsijan yhteyshenkilö ja puh."
             (yhteyshenkilo (::t/urakoitsijayhteyshenkilo ilm)))]

     [(tieto  "Tilaajan yhteyshenkilö ja puh."
              (yhteyshenkilo (::t/tilaajayhteyshenkilo ilm)))]

     [(tieto "Tien numero ja nimi"
             (str (::tr/tie osoite) " " (::t/tien-nimi ilm)))
      (tieto "Kunnat" (::t/kunnat ilm))]

     [(tieto "Työn alkupiste (osa/etäisyys) ja kuvaus"
             (str (::tr/aosa osoite) " / " (::tr/aet osoite) " " (::t/alkusijainnin-kuvaus ilm)))
      (tieto "Työn alku- ja loppupvm"
             (str (pvm/pvm-opt (::t/alku ilm)) " \u2013 " (pvm/pvm-opt (::t/loppu ilm))))]

     [(tieto "Työn loppupiste (osa/etäisyys) ja kuvaus"
             (str (::tr/losa osoite) " / " (::tr/let osoite) " " (::t/loppusijainnin-kuvaus ilm)))
      (tieto "Työn pituus"
             (pituus ilm))])))


(defn- tyovaihe [{osoite ::t/osoite paatietyoilmoitus ::t/paatietyoilmoitus :as ilm}]
  (if (nil? paatietyoilmoitus)
    "Ensimmäinen ilmoitus työstä"

    (tietotaulukko
     [(tieto "Työvaiheen alkupiste (osa/etäisyys) ja kuvaus"
             (str (::tr/aosa osoite) " / " (::tr/aet osoite) " " (::t/alkusijainnin-kuvaus ilm)))
      (tieto "Työvaiheen alku- ja loppupvm"
             (str (pvm/pvm-opt (::t/alku ilm)) " \u2013 " (pvm/pvm-opt (::t/loppu ilm))))]

     [(tieto "Työvaiheen loppupiste (osa/etäisyys) ja kuvaus"
             (str (::tr/losa osoite) " / " (::tr/let osoite) " " (::t/loppusijainnin-kuvaus ilm)))
      (tieto "Työvaiheen pituus"
             (pituus ilm))])))

(def viikonpaivan-jarjestys
  {"maanantai" 1 "tiistai" 2 "keskiviikko" 3 "torstai" 4 "perjantai" 5
   "lauantai" 6 "sunnuntai" 7})

(defn- tyoaika [{tyoajat ::t/tyoajat}]
  [:fo:block
   (for [ta tyoajat]
     [:fo:block (str/join ", " (sort-by viikonpaivan-jarjestys (::t/paivat ta))) ": "
      (str (::t/alkuaika ta)) " \u2013 " (str (::t/loppuaika ta))])])


(defn- sisennetty-arvo [arvo selite]
  [:fo:block {:margin-left "5mm"}
   [:fo:inline-container {:width "1cm"}
    [:fo:block
     [:fo:inline {:text-decoration "underline"}
      arvo]]]
   " " selite])

(defn- ajoneuvorajoitukset [ilm]
  (let [raj (::t/ajoneuvorajoitukset ilm)]
    [:fo:block
     (checkbox-lista [["Ulottumarajoituksia" true]]
                     #{(boolean (or (::t/max-leveys raj)
                                    (::t/max-korkeus raj)
                                    (::t/max-pituus raj)))})
     (sisennetty-arvo (::t/max-korkeus raj) "(m, ajoneuvon max. korkeus)")
     (sisennetty-arvo (::t/max-leveys raj) "(m, ajoneuvon max. leveys)")
     (sisennetty-arvo (::t/max-pituus raj) "(m, ajoneuvon max. pituus)")

     (checkbox-lista [["Painorajoitus" true
                       [:fo:inline
                        "  "
                        [:fo:inline {:text-decoration "underline"}
                         (::t/max-paino raj)]
                        " (kg)"]]]
                     #{(not (nil? (::t/max-paino raj)))})
     (checkbox-lista [["Kuumennin käytössä (avotuli)" "avotuli"]
                      ["Työkoneita liikenteen seassa" "tyokoneitaLiikenteenSeassa"]]
                     (into #{} (::t/huomautukset ilm)))]))

(defn- pvm-ja-aika [otsikko date]
  (let [pvm (and date (pvm/pvm date))
        aika (and date (pvm/aika date))]
    [:fo:block {:margin-left "5mm"}
     [:fo:inline-container {:width "2cm"}
      [:fo:block otsikko " "]]
     [:fo:inline {:text-decoration "underline"}
      pvm]
     " klo "
     [:fo:inline {:text-decoration "underline"}
      aika]]))

(defn- vaikutukset [{kaistajarj ::t/kaistajarjestelyt
                     nopeusrajoitukset ::t/nopeusrajoitukset
                     kiertotien-mutkaisuus ::t/kiertotien-mutkaisuus
                     kiertotien-pituus ::t/kiertotien-pituus
                     :as ilm}]
  (let [jarj (into {}
                   (map (juxt ::t/jarjestely ::t/selite))
                   [kaistajarj])
        nopeus (into {}
                     (map (juxt ::t/rajoitus ::t/matka))
                     nopeusrajoitukset)
        pinta (into {}
                    (map (juxt ::t/materiaali ::t/matka))
                    (::t/tienpinnat ilm))
        ktpinta (into {}
                      (map (juxt ::t/materiaali ::t/matka))
                      (::t/kiertotienpinnat ilm))]
    (tietotaulukko
     [;; Vasen puoli
      (tietotaulukko
       [(tieto "Kaistajärjestelyt"
               (checkbox-lista [["Yksi ajokaista suljettu" "ajokaistaSuljettu"]
                                ["Yksi ajorata suljettu" "ajorataSuljettu"]
                                ["Tie suljettu" "tieSuljettu"]
                                ["Muu" "muu" (get jarj "muu")]]
                               jarj))]
       [(tieto "Nopeusrajoitus"
               [:fo:block
                (checkbox (str "50 km/h "
                               (when (contains? nopeus "50")
                                 (str (get nopeus "50") " m"))) (contains? nopeus "50"))
                (for [[raj matka] (sort-by first nopeus)
                      :when (not= "50" raj)]
                  (checkbox (str raj " km/h " matka " m") true))])]
       [(tieto "Tien pinta työmaalla"
               [:fo:block
                (checkbox (str "Päällystetty "
                               (when-let [m (pinta "paallystetty")]
                                 (str m " m")))
                          (contains? pinta "paallystetty"))
                (checkbox (str "Jyrsitty "
                               (when-let [m (pinta "jyrsitty")]
                                 (str m " m")))
                          (contains? pinta "jyrsitty"))
                (checkbox (str "Murske "
                               (when-let [m (pinta "murske")]
                                 (str m " m")))
                          (contains? pinta "murske"))])]
       [(tieto (str "Kiertotien pituus " (::t/kiertotien-pituus ilm))
               [:fo:block

                (checkbox "Loivat mutkat"
                          (= kiertotien-mutkaisuus "loivatMutkat"))
                (checkbox "Jyrkät mutkat (erkanee yli 45\u00b0 kulmassa)"
                          (= kiertotien-mutkaisuus "jyrkatMutkat"))


                (checkbox (str "Päällystetty "
                               (when-let [m (ktpinta "paallystetty")]
                                 (str m " m")))
                          (contains? ktpinta "paallystetty"))
                (checkbox (str "Jyrsitty "
                               (when-let [m (ktpinta "jyrsitty")]
                                 (str m " m")))
                          (contains? ktpinta "jyrsitty"))
                (checkbox (str "Murske "
                               (when-let [m (ktpinta "murske")]
                                 (str m " m")))
                          (contains? ktpinta "murske"))

                ;"pitkä se on"
                ])])

      ;; Oikea puoli
      (tietotaulukko
       [(tieto "Pysäytyksiä"
               [:fo:block
                (checkbox-lista [["Liikennevalot" "liikennevalot"]
                                 ["Liikenteen ohjaaja" "liikenteenohjaaja"]
                                 ["Satunnaisia" "satunnaisia"]]
                                #{(::t/liikenteenohjaaja ilm)})
                (pvm-ja-aika "alkaa" (::t/pysaytysten-alku ilm))
                (pvm-ja-aika "päättyy" (::t/pysaytysten-loppu ilm))])]
       [(tieto "Liikenteenohjaus"
               [:fo:block
                (checkbox-lista [["Ohjataan vuorotellen" "ohjataanVuorotellen"]
                                 ["Ohjataan kaksisuuntaisena" "ohjataanKaksisuuntaisena"]]
                                #{(::t/liikenteenohjaus ilm)})])]
       [(tieto "Arvioitu viivytys"
               [:fo:block
                (sisennetty-arvo (::t/viivastys-normaali-liikenteessa ilm) "(min, normaali liikenne)")
                (sisennetty-arvo (::t/viivastys-ruuhka-aikana ilm) "(min, ruuhka-aika)")])]
       [(tieto "Kulkurajoituksia"
               [:fo:block
                (ajoneuvorajoitukset ilm)])])])))

(defn- vaikutussuunta [{vs ::t/vaikutussuunta}]
  (tietotaulukko
   [(checkbox "Haittaa molemmissa ajosuunnissa" (= "molemmat" vs))
    (tieto "Haittaa ajosuunnassa (lähin kaupunki)"
           [:fo:block
            (case vs
              "tienumeronKasvusuuntaan" "Tienumeron kasvusuuntaan"
              "vastenTienumeronKasvusuuntaa" "Vasten tienumeron kasvusuuntaa"
              "")])]))

(defn- muuta [{lisatietoja ::t/lisatietoja diaarinumero ::t/luvan-diaarinumero}]
  (tietotaulukko
    [(tieto "Lisätietoja"
            lisatietoja)
     (tieto "Diaarinumero"
            diaarinumero)]))

(defn- ilmoittaja [{ilmoittaja ::t/ilmoittaja luotu ::m/luotu muokattu ::m/muokattu}]
  (tietotaulukko
   [(tieto "Nimi, puh."
           (yhteyshenkilo ilmoittaja))
    (tieto "Pvm"
           (pvm/pvm (or muokattu luotu)))]))

(def ^:private osiot
  [["1 Ilmoitus koskee" #'ilmoitus-koskee]
   ["2 Tiedot koko kohteesta" #'kohteen-tiedot]
   ["3 Työvaihe" #'tyovaihe]
   ["4 Työn tyyppi" #'tyon-tyyppi]
   ["5 Työaika" #'tyoaika]
   ["6 Vaikutukset liikenteelle" #'vaikutukset]
   ["7 Vaikutussuunta" #'vaikutussuunta]
   ["8 Muuta" #'muuta]
   ["9 Ilmoittaja" #'ilmoittaja]])


(defn tietyoilmoitus-pdf [tietyoilmoitus]
  (with-meta
    (xsl-fo/dokumentti
     {:margin {:left "5mm" :right "5mm" :top "5mm" :bottom "5mm"
               :body "0mm"}}

     [:fo:wrapper {:font-size 8}
      (taulukko
       [:fo:block {:text-align "center"}
        [:fo:block {:font-weight "bold"}
         [:fo:block "ILMOITUS LIIKENNETTÄ HAITTAAVASTA TYÖSTÄ"]
         [:fo:block "ITM FINLANDIN TIELIIKENNEKESKUKSEEN"]]
        [:fo:block
         "Yllättävästä häiriöstä erikseen ilmoitus puhelimitse"
         " urakoitsijan linjalle 0200 21200"]]
       (for [[osio sisalto-fn] osiot]
         [osio (sisalto-fn tietyoilmoitus)]))])
    {:tiedostonimi (str "Tietyöilmoitus-"
                        (pvm/pvm-opt (::t/alku tietyoilmoitus)) "-"
                        (pvm/pvm-opt (::t/loppu tietyoilmoitus)) "-"
                        (::t/tien-nimi tietyoilmoitus)
                        ".pdf")}))
