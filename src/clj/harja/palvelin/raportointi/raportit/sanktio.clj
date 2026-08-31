(ns harja.palvelin.raportointi.raportit.sanktio
  (:require [jeesql.core :refer [defqueries]]
            [harja.palvelin.raportointi.raportit.yleinen :refer [rivi]]
            [harja.kyselyt.urakat :as urakat-kyselyt]
            [harja.kyselyt.hallintayksikot :as hallintayksikot-q]
            [harja.pvm :as pvm]
            [harja.domain.urakka :as urakka-domain]
            [harja.domain.laadunseuranta.sanktio :as domain-sanktio]))

(defqueries "harja/palvelin/raportointi/raportit/sanktiot.sql")

(declare hae-sanktiot hae-urakkataso-sanktiot hae-urakkataso-bonukset hae-urakkataso-sanktiolajit hae-urakkataso-bonuslajit
  hae-sanktiot-yllapidon-raportille hae-urakkataso-yllapito-sanktiot hae-urakkataso-yllapito-bonukset)

(defn- muodosta-rahasarake [otsikko]
  {:leveys 15
   :otsikko (str otsikko " (€)")
   :fmt :raha})

(defn- koosta-arvonvahennys-taulukko [arvonvahennykset]
  (let [rivit (if (seq arvonvahennykset)
                (mapv (fn [[laji-nimi lajit]]
                        (let [summa (reduce + 0 (map #(or (:summa %) 0) lajit))]
                          (rivi (or laji-nimi "Arvonvähennys") summa)))
                  (group-by :sanktiolaji_nimi arvonvahennykset))
                [(rivi "Arvonvähennys" 0)])
        yhteenveto [{:lihavoi? true
                     :korosta-hennosti? true
                     :rivi (rivi "Yhteensä" (reduce + 0 (map #(or (:summa %) 0) arvonvahennykset)))}]]
    [[{:leveys 12 :otsikko "Arvonvähennyslaji"}
      (muodosta-rahasarake "Arvonvähennys")]
     (into [] (concat rivit yhteenveto))]))

(defn- koosta-tunnistamattomat-taulukko
  "Muodostaa taulukon sanktioille, joille ei löytynyt sanktio_profiili_riviä
   (esim. vanhentunut/poistettu sanktiotyyppi jota ei ole koodattu profiiliin).
   Nämä sanktiot eivät saa kadota raportilta äänettömästi, joten ne näytetään
   omana taulukkonaan sanktiotyyppinimen mukaan ryhmiteltynä."
  [tunnistamattomat]
  (let [rivit (mapv (fn [[tyyppi-nimi rivit]]
                      (let [summa (reduce + 0 (map #(or (:summa %) 0) rivit))]
                        (rivi (or tyyppi-nimi "Tunnistamaton sanktiotyyppi") summa)))
                (group-by :sanktiotyyppi_nimi tunnistamattomat))
        yhteensa-summa (reduce + 0 (map #(or (:summa %) 0) tunnistamattomat))]
    [:taulukko {:otsikko "Tunnistamattomat sanktiot"
                :sheet-nimi "Tunnistamattomat"
                :viimeinen-rivi-yhteenveto? true
                :tyhja "Ei tunnistamattomia sanktioita."}
     [{:leveys 12 :otsikko "Tunnistamattomat sanktiot"}
                  (muodosta-rahasarake "Sanktio")]
     (into [] (concat rivit
                [{:lihavoi? true
                  :korosta-hennosti? true
                  :rivi (rivi "Yhteensä" yhteensa-summa)}]))]))


; ----------------------------------------- ;
;   Urakkataso-sanktioraportti (MHU25+)      ;
; ----------------------------------------- ;

(defn- muodosta-sanktio-taulukko
  "Muodostaa yhden sanktiolaji-ryhmän taulukon.
   Lajit ovat jo ryhmiteltyjä ja järjestettyjä tietokannan mukaisesti."
  [lajit sanktio-data-map]
  (let [ensimmainen (first lajit)
        laji-koodi (:sanktiolaji_koodi ensimmainen)
        laji-nimi (:sanktiolaji_nimi ensimmainen)
        ;; Jos lajilla on tyyppejä, näytetään kaikki profiilin tyyppirivit.
        tyypit (if (> (count lajit) 1) lajit [ensimmainen])
        ;; Laske lajin kokonaissumma
        laji-summa (reduce + 0
                     (map #(or (get sanktio-data-map [laji-koodi (:sanktiotyyppi_koodi %)]) 0)
                       tyypit))]
    [:taulukko {:sheet-nimi (or laji-koodi "sanktio")
                :otsikko laji-nimi
                :viimeinen-rivi-yhteenveto? true
                :tyhja "Ei tietoja."}
     [{:leveys 12 :otsikko "Tyyppi"}
                  (muodosta-rahasarake "Sanktio")]
     (concat
       ;; Kaikki profiilin tyyppirivit näytetään, myös yhden tyypin ryhmässä.
       (mapv
         (fn [tyyppi]
           (let [tyyppi-koodi (:sanktiotyyppi_koodi tyyppi)
                 summa (or (get sanktio-data-map [laji-koodi tyyppi-koodi]) 0)]
             {:himmennetty? (zero? summa)
              :rivi (rivi (domain-sanktio/sanktiotyypin-nimi
                            laji-nimi
                            {:koodi tyyppi-koodi
                             :nimi (:sanktiotyyppi_nimi tyyppi)})
                       summa)}))
         tyypit)
       [{:lihavoi? true
         :korosta-hennosti? true
         :rivi (rivi "Yhteensä" laji-summa)}])]))

(defn- lisaa-excel-osion-otsikko [taulukko otsikko]
  (update-in taulukko [1 :excel-alkutekstit]
    (fnil conj [])
    [:otsikko-heading otsikko]))

(defn- koosta-yllapito-taulukko
  "Muodostaa ylläpito-urakan sanktiotaulukon ylläpitoluokittain ryhmiteltynä.
   Data tulee hae-sanktiot-yllapidon-raportille -kyselyltä."
  [sanktiot]
  (let [yllapitoluokan-nimi (fn [luokka]
                              (cond
                                (nil? luokka) "Määrittelemätön"
                                (keyword? luokka) (name luokka)
                                (string? luokka) luokka
                                :else (str luokka)))
        ryhmitelty (group-by :yllapitoluokka sanktiot)
        jarjestetty (sort-by (fn [[luokka _]] (yllapitoluokan-nimi luokka))
                      (map (fn [[luokka rivit]]
                             [luokka (sort-by :indeksi rivit)])
                        ryhmitelty))
        rivit (mapv (fn [[luokka rivit]]
                      (let [summa (reduce + 0 (map #(or (:summa %) 0) rivit))
                            maara (count rivit)]
                        {:rivi (rivi (yllapitoluokan-nimi luokka) maara summa)}))
                jarjestetty)
        yhteensa-summa (reduce + 0 (map #(or (:summa %) 0) sanktiot))
        yhteensa-maara (count sanktiot)]
    [:taulukko {:sheet-nimi "Sakot"
                :otsikko "Sakot ylläpitoluokittain"
                :viimeinen-rivi-yhteenveto? true
                :tyhja "Ei sakkotietoja."}
     [{:leveys 12 :otsikko "Ylläpitoluokka"}
      {:leveys 8 :otsikko "Määrä" :fmt :numero}
      {:leveys 15 :otsikko "Summa (€)" :fmt :raha}]
     (into [] (concat rivit
                [{:lihavoi? true
                  :korosta-hennosti? true
                  :rivi (rivi "Yhteensä" yhteensa-maara yhteensa-summa)}]))]))


(defn- koosta-sanktio-taulukot
  "Ryhmittelee lajit tietokannan mukaisesti, muodostaa erillisen taulukon kutakin lajia vasten.
   Arvonalennukset (arvonvahennyssanktio) erotetaan omaksi taulukokseen.
   
   Lupaussanktio erotetaan omaksi 'Lupaussanktiot'-osiokseen, muut lajit yhteen 'Sanktiot'-osioon."
  [sanktiolajit sanktio-data-map]
  (let [;; Erotellaan lupaussanktio muista lajeista
        {lupaussanktio-lajit true
       muut-lajit false} (group-by #(= "lupaussanktio" (:sanktiolaji_koodi %))
            (remove #(= "arvonvahennyssanktio" (:sanktiolaji_koodi %)) sanktiolajit))

        ;; Ryhmitellään muut lajit koodin mukaan
        ryhmitelty (group-by :sanktiolaji_koodi muut-lajit)
        ;; Järjestetään jokainen ryhmä jarjestys-kentän mukaan
        jarjestetty (mapv (fn [[_ lajit]]
                            (sort-by :sanktiolaji_jarjestys lajit))
                      (sort-by (fn [[_ lajit]]
                                 (or (:sanktiolaji_jarjestys (first lajit)) 99))
                        ryhmitelty))

        ;; Muodosta taulukot muille lajeille
        muut-taulukot (mapv #(muodosta-sanktio-taulukko % sanktio-data-map) jarjestetty)
        muut-taulukot (if (seq muut-taulukot)
                        (update muut-taulukot 0 lisaa-excel-osion-otsikko "Sanktiot")
                        muut-taulukot)
        lupaussanktio-taulukko (when (seq lupaussanktio-lajit)
                                 (-> (muodosta-sanktio-taulukko lupaussanktio-lajit sanktio-data-map)
                                   (lisaa-excel-osion-otsikko "Lupaussanktiot")))]

    ;; Yhdistetään: lupaussanktio-osio (jos on) + muut sanktiot
    ;; Huom: palautetaan suorat alkiot (ei kietoutuneita vektoreita),
    ;; jotta testit löytävät [:otsikko "Sanktiot"] suoraan raportin alkioiden joukosta.
    (concat
      (when (seq lupaussanktio-lajit)
        ;; Lupaussanktiolle oma osio
        [[:otsikko "Lupaussanktiot"] lupaussanktio-taulukko])
      ;; Sanktiot-osio näytetään myös silloin, kun toteutuneita arvoja ei ole.
      [[:otsikko "Sanktiot"]]
      muut-taulukot)))

(defn- muodosta-bonus-taulukko
  "Muodostaa bonus-taulukon annetuista bonusriveistä."
  [bonus-lajit bonus-data-map]
  (let [bonus-rivit (mapv
                      (fn [laji]
                        (let [summa (or (get bonus-data-map (:bonuslaji_koodi laji)) 0)
                              nolla-summa? (zero? summa)]
                          {:himmennetty? nolla-summa?
                           :rivi (rivi (:bonuslaji_nimi laji) summa)}))
                      bonus-lajit)
        bonukset-yhteensa (reduce + 0 (vals bonus-data-map))]
    [:taulukko {:sheet-nimi "Bonukset"
                :otsikko "Bonukset"
                :excel-alkutekstit [[:otsikko-heading "Bonukset"]]
                :viimeinen-rivi-yhteenveto? true
                :tyhja (when (empty? bonus-lajit) "Ei bonuslajeja profiilissa.")}
     [{:leveys 12 :otsikko "Bonuslaji"}
                  (muodosta-rahasarake "Bonus")]
     (into [] (concat bonus-rivit
                [{:lihavoi? true
                  :korosta-hennosti? true
                  :rivi (rivi "Bonukset yhteensä" bonukset-yhteensa)}]))]))

(defn- urakka-id [rivi]
  (or (:urakka_id rivi) (:urakka-id rivi)))

(defn- urakan-nimi [rivit]
  (or (:urakan_nimi (first rivit))
    (:nimi (first rivit))
    "Tunnistamaton urakka"))

(defn- urakan-valilehden-nimi [rivit]
  (let [rivi (first rivit)
        alkupvm (:urakan_alkupvm rivi)
        loppupvm (or (:urakan_loppupvm rivi) (:loppupvm rivi))
        vuosi (fn [paivamaara]
                (when paivamaara
                  (.format (java.text.SimpleDateFormat. "yyyy") paivamaara)))
        kesto (when (and alkupvm loppupvm)
                (str (vuosi alkupvm) "-" (vuosi loppupvm)))]
    (str (urakan-nimi rivit)
      (when (and kesto (not (.contains (urakan-nimi rivit) kesto)))
        (str " " kesto)))))

(defn- yksiloi-sanktiot [rivit]
  (vals (group-by #(or (:sanktio_id %) (:id %)) rivit)))

(defn- merkitse-aggregaatin-taulukot [taulukot nimi]
  (map-indexed (fn [indeksi taulukko]
                 (update taulukko 1 merge
                   {:aggregaatin-urakkataulukko? true
                    :sheet-nimi nimi}
                   (when (pos? indeksi)
                     {:samalle-sheetille? true})))
    taulukot))

(defn- koosta-urakka-erittely [sanktiot bonukset arvonvahennykset sanktiolajit bonuslajit]
  (let [rivit (concat sanktiot bonukset arvonvahennykset)
        urakat (->> rivit
                 (group-by urakka-id)
                 (remove (comp nil? first))
                 (sort-by (comp urakan-nimi second)))]
    (when (seq urakat)
      (into [:otsikko "Urakat"]
        (mapcat (fn [[_ urakan-rivit]]
                  (let [urakan-nimi (urakan-nimi urakan-rivit)
                        valilehden-nimi (urakan-valilehden-nimi urakan-rivit)
                        sanktiot (->> (filter #(and (or (:sanktio_id %) (:id %))
                                                 (not= "arvonvahennyssanktio" (:sanktiolaji_koodi %)))
                                        urakan-rivit)
                                   yksiloi-sanktiot
                                   (mapcat identity))
                        bonukset (filter :bonuslaji_koodi urakan-rivit)
                        arvonvahennykset (->> (filter #(= "arvonvahennyssanktio" (:sanktiolaji_koodi %)) urakan-rivit)
                                           yksiloi-sanktiot
                                           (mapcat identity))
                        sanktiot-uniikit (map first (vals (group-by :sanktio_id sanktiot)))
                        tunnistamattomat (filterv #(nil? (:sanktiolaji_koodi %)) sanktiot-uniikit)
                        tunnetut (filterv #(some? (:sanktiolaji_koodi %)) sanktiot-uniikit)
                        sanktio-data-map (reduce (fn [summa-map sanktio]
                                                   (update summa-map
                                                     [(:sanktiolaji_koodi sanktio)
                                                      (:sanktiotyyppi_koodi sanktio)]
                                                     (fnil + 0) (or (:summa sanktio) 0)))
                                           {}
                                           tunnetut)
                        bonus-data-map (reduce (fn [summa-map bonus]
                                                 (update summa-map (:bonuslaji_koodi bonus)
                                                   (fnil + 0) (or (:summa bonus) 0)))
                                         {}
                                         bonukset)
                        taulukot (filter #(= :taulukko (first %))
                                   (concat
                                     (koosta-sanktio-taulukot sanktiolajit sanktio-data-map)
                                     [(muodosta-bonus-taulukko (sort-by :bonuslaji_jarjestys bonuslajit)
                                        bonus-data-map)
                                      (into [:taulukko {:otsikko "Arvonvähennykset"
                                                        :sheet-nimi "Arvonvähennykset"
                                                        :excel-alkutekstit [[:otsikko-heading "Arvonvähennykset"]]
                                                        :viimeinen-rivi-yhteenveto? true
                                                        :tyhja "Ei arvonvähennyksiä."}]
                                        (koosta-arvonvahennys-taulukko arvonvahennykset))]
                                     (when (seq tunnistamattomat)
                                       [(koosta-tunnistamattomat-taulukko tunnistamattomat)])))]
                    (into [[:otsikko urakan-nimi]]
                      (merkitse-aggregaatin-taulukot taulukot valilehden-nimi))))
          urakat)))))

(defn- koosta-urakkataso-runko
  "Muodostaa urakkataso-sanktioraportin raporttirakennelman.

   Hoito-urakka: käyttää profiili-driven kyselyitä jotka palauttavat
   sanktiolaji/bonuslaji-tiedot suoraan tietokannasta.

   Ylläpito-urakka: näyttää sakot ylläpitoluokittain ryhmiteltynä.
   Parametri yllapitourakka? määrittää käytettävän layoutin."
  [urakan-nimi alkupvm loppupvm sanktiot bonukset arvonvahennykset sanktiolajit bonuslajit & [yllapitourakka? urakka-erittely?]]
  (let [raportin-otsikko (if yllapitourakka?
                           "Sakko- ja bonusraportti"
                           "Sanktiot, bonukset ja arvonvähennykset")
        aikajakso (str (pvm/pvm alkupvm) " - " (pvm/pvm loppupvm))

        [yhteenveto-data rungon-osat] (if yllapitourakka?
                                        ;; YLLÄPITO-URAKKA
                                        (let [sakkosumma (reduce + 0 (map #(or (:summa %) 0) sanktiot))
                                              muistutukset (filterv #(= "yllapidon_muistutus"
                                                                        (or (:sanktiolaji_koodi %)
                                                                          (:sakkoryhma %)))
                                                             sanktiot)
                                              muistutusten-maara (count muistutukset)
                                              suorasakot (filterv :suorasanktio sanktiot)
                                              suorasakkojen-summa (reduce + 0 (map #(or (:summa %) 0) suorasakot))
                                              bonukset-summa (reduce + 0 (map #(or (:summa %) 0) (or bonukset [])))]
                                          [[{:avain "Sakot yhteensä" :arvo sakkosumma :fmt :raha}
                                            {:avain "Bonukset yhteensä" :arvo bonukset-summa :fmt :raha}
                                            {:avain "Muistutukset" :arvo (str muistutusten-maara " kpl")}
                                            {:avain "Suorasakot" :arvo suorasakkojen-summa :fmt :raha}
                                            {:avain "Yhteensä" :arvo (+ sakkosumma bonukset-summa) :fmt :raha :lihavoi? true}]
                                           (list (koosta-yllapito-taulukko sanktiot))])

                                        ;; HOITO-URAKKA
                                        (let [sanktiot-ilman-arvonvahennyksia (filterv #(not= "arvonvahennyssanktio" (:sanktiolaji_koodi %)) sanktiot)
                                              arvonvahennykset-summa (reduce + 0 (map #(or (:summa %) 0) arvonvahennykset))

                                              ;; HUOM: hae-urakkataso-sanktiot palauttaa yhden rivin per
                                              ;; sanktiolaji/sanktiotyyppi-yhdistelmä, joten samalla sanktio_id:llä
                                              ;; voi olla useita rivejä. Yhteenvetolukuja (summa, määrät) varten
                                              ;; sanktiot pitää ottaa kertaalleen per sanktio_id.
                                              sanktiot-uniikit (map first (vals (group-by :sanktio_id sanktiot-ilman-arvonvahennyksia)))

                                              ;; HUOM: sanktiolla ei välttämättä ole sanktio_profiili_riviä
                                              ;; (esim. vanhentunut/poistettu sanktiotyyppi). Nämä sanktiot
                                              ;; erotetaan omaan taulukkoonsa, mutta niiden summa lasketaan
                                              ;; mukaan "Sanktiot yhteensä" -lukuun - rahaa ei saa kadota.
                                              tunnistamattomat-uniikit (filterv #(nil? (:sanktiolaji_koodi %)) sanktiot-uniikit)
                                              sanktiot-tunnistetut-uniikit (filterv #(some? (:sanktiolaji_koodi %)) sanktiot-uniikit)

                                              sanktiot-yhteensa (reduce + 0 (map #(or (:summa %) 0) sanktiot-uniikit))
                                              bonukset-yhteensa (reduce + 0 (map #(or (:summa %) 0) (or bonukset [])))
                                              muistutusten-maara (count (filterv #(= "muistutus" (:sanktiolaji_koodi %)) sanktiot-tunnistetut-uniikit))
                                              vastuuhenkilon-vaihto-summa (reduce + 0
                                                                            (map #(or (:summa %) 0)
                                                                              (filterv #(= "vastuuhenkilon-vaihto" (:sanktiotyyppi_koodi %)) sanktiot-tunnistetut-uniikit)))

                                              sanktio-data-map (reduce
                                                                 (fn [m s]
                                                                   (let [key [(:sanktiolaji_koodi s) (:sanktiotyyppi_koodi s)]]
                                                                     (update m key (fnil + 0) (or (:summa s) 0))))
                                                                 {}
                                                                 sanktiot-tunnistetut-uniikit)

                                              bonus-data-map (reduce
                                                               (fn [m b]
                                                                 (update m (:bonuslaji_koodi b) (fnil + 0) (or (:summa b) 0)))
                                                               {}
                                                               (or bonukset []))

                                              bonus-lajit-jarjestyksessa (sort-by :bonuslaji_jarjestys bonuslajit)
                                              arvonvahennys-taulukko (koosta-arvonvahennys-taulukko arvonvahennykset)]

                                          [[{:avain "Sanktiot yhteensä" :arvo sanktiot-yhteensa :fmt :raha}
                                            {:avain "Bonukset yhteensä" :arvo bonukset-yhteensa :fmt :raha}
                                            {:avain "Kirjalliset muistutukset" :arvo (str muistutusten-maara " kpl")}
                                            {:avain "Vastuuhenkilön vaihto" :arvo vastuuhenkilon-vaihto-summa :fmt :raha}
                                            {:avain "Arvovähennykset" :arvo arvonvahennykset-summa :fmt :raha}
                                            {:avain "Yhteensä" :arvo (+ sanktiot-yhteensa bonukset-yhteensa arvonvahennykset-summa) :fmt :raha :lihavoi? true}]

                                           (concat
                                             (koosta-sanktio-taulukot sanktiolajit sanktio-data-map)
                                             [(muodosta-bonus-taulukko bonus-lajit-jarjestyksessa bonus-data-map)
                                              (into [:taulukko {:otsikko "Arvonvähennykset"
                                                                :sheet-nimi "Arvonvähennykset"
                                                                :excel-alkutekstit [[:otsikko-heading "Arvonvähennykset"]]
                                                                :viimeinen-rivi-yhteenveto? true
                                                                :tyhja (when (empty? arvonvahennykset) "Ei arvonvähennyksiä.")}]
                                                arvonvahennys-taulukko)]
                                             (when (seq tunnistamattomat-uniikit)
                                               [(koosta-tunnistamattomat-taulukko tunnistamattomat-uniikit)]))]))]

    (into [:raportti {:nimi urakan-nimi
                      :orientaatio :landscape
              :urakan-nimi urakan-nimi
              :aikajakso aikajakso
              :otsikon-koko :iso
          :raportin-yleiset-tiedot {:raportin-nimi urakan-nimi}
                :piilota-otsikko? true
              :alkupvm alkupvm
              :loppupvm loppupvm
                      :excel-vain-urakka-erittely? urakka-erittely?
                      :excel-urakkataso? (not urakka-erittely?)
                      :excel-detail-sheet-nimi (when-not urakka-erittely?
                                                 urakan-nimi)}
         [:otsikko-title raportin-otsikko]
         [:teksti (str urakan-nimi " | Aikaväli: " aikajakso)
          {:luokka "raportin-otsikkorivi"}]
           [:display-flex
            [:sininen-laatikko {:otsikko "Yhteenveto"}
             yhteenveto-data]]]
      (concat
        (remove nil? rungon-osat)
        (when urakka-erittely?
          [(koosta-urakka-erittely sanktiot bonukset arvonvahennykset sanktiolajit bonuslajit)])))))



(defn suorita
  "Raportin suoritin urakkataso-sanktioraportille (MHU25+).
   Käyttää profiili-driven SQL-kyselyitä: hae-urakkataso-sanktiot ja hae-urakkataso-bonukset.
   
   Tukee sekä hoito-urakkatyyppiä (profiilivetoiset kyselyt) että ylläpito-urakkatyyppiä 
   (ylläpitoluokka-ryhmittely)."
  [db _user {:keys [urakka-id elinvoimakeskus-id alkupvm loppupvm hoitovuosi urakkatyyppi]}]
  (let [urakan-tiedot (when urakka-id
                        (first (urakat-kyselyt/hae-urakka db urakka-id)))
        urakan-nimi (or (:nimi urakan-tiedot)
                      (when elinvoimakeskus-id
                        (:nimi (first (hallintayksikot-q/hae-organisaatio db elinvoimakeskus-id))))
                      (when (and (nil? urakka-id) (nil? elinvoimakeskus-id))
                        "Koko maa")
                      "")

        ;; Tunnista urakkatyyppi (Default: hoito)
        yllapitourakka? (urakka-domain/yllapitourakka? (or urakkatyyppi
                                                         (keyword (:tyyppi urakan-tiedot))
                                                         :hoito))

        ;; Valitse kyselyt urakkatyypin mukaan
        [sanktiot bonukset arvonvahennykset sanktiolajit bonuslajit]
        (if yllapitourakka?
          ;; Ylläpito-urakka: käytetään ylläpito-kyselyä
          (let [hoitovuosi (or hoitovuosi
                             (when-let [urakan-alkupvm (:alkupvm urakan-tiedot)]
                               (pvm/paivamaara->mhu-hoitovuosi-nro urakan-alkupvm alkupvm)))
                kyselyparametrit {:urakka urakka-id
                                  :elinvoimakeskus elinvoimakeskus-id
                                  :urakkatyyppi (when urakkatyyppi (name urakkatyyppi))
                                  :alku alkupvm
                                  :loppu loppupvm
                                  :hoitovuosi hoitovuosi}
                sanktiot-yllapito (hae-urakkataso-yllapito-sanktiot db kyselyparametrit)
                bonukset-yllapito (hae-urakkataso-yllapito-bonukset db kyselyparametrit)
                arvonvahennykset (filterv #(= "arvonvahennyssanktio" (:sanktiolaji_koodi %))
                                   sanktiot-yllapito)]
            [sanktiot-yllapito bonukset-yllapito arvonvahennykset nil nil])
          ;; Hoito-urakka: käytetään profiilivetoisia kyselyitä
          (let [hoitovuosi (or hoitovuosi
                             (when-let [urakan-alkupvm (:alkupvm urakan-tiedot)]
                               (pvm/paivamaara->mhu-hoitovuosi-nro urakan-alkupvm alkupvm)))
                vanhan-muodon-sanktiot (hae-sanktiot db {:urakka urakka-id
                                                         :elinvoimakeskus elinvoimakeskus-id
                                                         :urakkatyyppi (when urakkatyyppi (name urakkatyyppi))
                                                         :alku alkupvm
                                                         :loppu loppupvm})
                sanktiolajit (hae-urakkataso-sanktiolajit db {:urakka urakka-id
                                                              :elinvoimakeskus elinvoimakeskus-id
                                                              :hoitovuosi hoitovuosi
                                                              :alku alkupvm
                                                              :loppu loppupvm
                                                              :urakkatyyppi (when urakkatyyppi (name urakkatyyppi))})
                bonuslajit (hae-urakkataso-bonuslajit db {:urakka urakka-id
                                                          :elinvoimakeskus elinvoimakeskus-id
                                                          :hoitovuosi hoitovuosi
                                                          :urakkatyyppi (when urakkatyyppi (name urakkatyyppi))})
                profiili-sanktiot (hae-urakkataso-sanktiot db {:urakka urakka-id
                                                               :elinvoimakeskus elinvoimakeskus-id
                                                               :alku alkupvm
                                                               :loppu loppupvm
                                                               :hoitovuosi hoitovuosi
                                                               :urakkatyyppi (when urakkatyyppi (name urakkatyyppi))})
                tunnetut-sanktio-idt (into #{} (keep :sanktio_id) profiili-sanktiot)
                puuttuvat-sanktiot (->> vanhan-muodon-sanktiot
                                     (filter #(and (:id %)
                                                (not (contains? tunnetut-sanktio-idt (:id %)))))
                                     (map #(assoc %
                                             :sanktio_id (:id %)
                                             :sanktiolaji_koodi (when (= "arvonvahennyssanktio" (:sakkoryhma %))
                                                                  "arvonvahennyssanktio")
                                             :sanktiolaji_nimi (when (= "arvonvahennyssanktio" (:sakkoryhma %))
                                                                 "Arvonvähennys")))
                                     vec)
                sanktiot (into profiili-sanktiot puuttuvat-sanktiot)
                bonukset (hae-urakkataso-bonukset db {:urakka urakka-id
                                                      :elinvoimakeskus elinvoimakeskus-id
                                                      :alku alkupvm
                                                      :loppu loppupvm
                                                      :hoitovuosi hoitovuosi
                                                      :urakkatyyppi (when urakkatyyppi (name urakkatyyppi))})
                arvonvahennykset (filterv #(= "arvonvahennyssanktio" (:sanktiolaji_koodi %)) sanktiot)
                tunnetut-arvonvahennys-idt (into #{} (keep :sanktio_id) arvonvahennykset)
                puuttuvat-arvonvahennykset (->> vanhan-muodon-sanktiot
                                             (filter #(= "arvonvahennyssanktio" (:sakkoryhma %)))
                                             (remove #(contains? tunnetut-arvonvahennys-idt (:id %)))
                                             (map #(assoc %
                                                     :sanktio_id (:id %)
                                                     :sanktiolaji_koodi "arvonvahennyssanktio"
                                                     :sanktiolaji_nimi "Arvonvähennys")))]
            [sanktiot bonukset (into arvonvahennykset puuttuvat-arvonvahennykset) sanktiolajit bonuslajit]))]
    (koosta-urakkataso-runko urakan-nimi alkupvm loppupvm sanktiot bonukset arvonvahennykset sanktiolajit bonuslajit
      yllapitourakka?
      (nil? urakka-id))))
