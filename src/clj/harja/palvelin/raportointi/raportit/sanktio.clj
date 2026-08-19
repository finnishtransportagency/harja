(ns harja.palvelin.raportointi.raportit.sanktio
  (:require [jeesql.core :refer [defqueries]]
            [harja.palvelin.raportointi.raportit.yleinen :refer [rivi]]
            [harja.kyselyt.urakat :as urakat-kyselyt]
            [harja.pvm :as pvm]
            [harja.domain.urakka :as urakka-domain]))

(defqueries "harja/palvelin/raportointi/raportit/sanktiot.sql")

(declare hae-urakkataso-sanktiot hae-urakkataso-bonukset hae-urakkataso-sanktiolajit hae-urakkataso-bonuslajit hae-sanktiot-yllapidon-raportille)

(defn- koosta-arvonvahennys-taulukko [arvonvahennykset]
  (let [rivit (mapv (fn [[laji-nimi lajit]]
                      (let [summa (reduce + 0 (map #(or (:summa %) 0) lajit))]
                        (rivi (or laji-nimi "Arvonvähennys") summa)))
                (group-by :sanktiolaji_nimi arvonvahennykset))
        yhteenveto [{:lihavoi? true
                     :korosta-hennosti? true
                     :rivi (rivi "Yhteensä" (reduce + 0 (map #(or (:summa %) 0) arvonvahennykset)))}]]
    [[{:leveys 12 :otsikko "Arvonvähennyslaji"}
      {:leveys 15 :otsikko "Summa (€)" :fmt :raha}]
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
     [{:leveys 12 :otsikko "Sanktiotyyppi"}
      {:leveys 15 :otsikko "Summa (€)" :fmt :raha}]
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
    [:taulukko {:otsikko laji-nimi
                :sheet-nimi (or laji-koodi "sanktio")
                :viimeinen-rivi-yhteenveto? true
                :tyhja "Ei tietoja."}
     [{:leveys 12 :otsikko "Tyyppi"}
      {:leveys 15 :otsikko "Summa (€)" :fmt :raha}]
     (concat
       ;; Laji yhteensä -rivi ensin
       [{:lihavoi? true
         :korosta-hennosti? true
         :rivi (rivi laji-nimi laji-summa)}]
       ;; Kaikki profiilin tyyppirivit näytetään, myös yhden tyypin ryhmässä.
       (mapv
         (fn [tyyppi]
           (let [tyyppi-koodi (:sanktiotyyppi_koodi tyyppi)
                 summa (or (get sanktio-data-map [laji-koodi tyyppi-koodi]) 0)]
             {:himmennetty? (zero? summa)
              :rivi (rivi (:sanktiotyyppi_nimi tyyppi) summa)}))
         tyypit))]))

(defn- koosta-yllapito-taulukko
  "Muodostaa ylläpito-urakan sanktiotaulukon ylläpitoluokittain ryhmiteltynä.
   Data tulee hae-sanktiot-yllapidon-raportille -kyselyltä."
  [sanktiot]
  (let [ryhmitelty (group-by :yllapitoluokka sanktiot)
        jarjestetty (sort-by (fn [[luokka _]] (or (name luokka) ""))
                      (map (fn [[luokka rivit]]
                             [luokka (sort-by :indeksi rivit)])
                        ryhmitelty))
        rivit (mapv (fn [[luokka rivit]]
                      (let [summa (reduce + 0 (map #(or (:summa %) 0) rivit))
                            maara (count rivit)]
                        {:rivi (rivi (or (name luokka) "Määrittelemätön") maara summa)}))
                jarjestetty)
        yhteensa-summa (reduce + 0 (map #(or (:summa %) 0) sanktiot))
        yhteensa-maara (count sanktiot)]
    [:taulukko {:otsikko "Sakot ylläpitoluokittain"
                :sheet-nimi "Sakot"
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
         muut-lajit false} (group-by #(= "lupaussanktio" (:sanktiolaji_koodi %)) sanktiolajit)

        ;; Ryhmitellään muut lajit koodin mukaan
        ryhmitelty (group-by :sanktiolaji_koodi muut-lajit)
        ;; Järjestetään jokainen ryhmä jarjestys-kentän mukaan
          jarjestetty (mapv (fn [[_ lajit]]
                  (sort-by :sanktiolaji_jarjestys lajit))
                (sort-by (fn [[_ lajit]]
                   (or (:sanktiolaji_jarjestys (first lajit)) 99))
              ryhmitelty))

        ;; Muodosta taulukot muille lajeille
        muut-taulukot (mapv #(muodosta-sanktio-taulukko % sanktio-data-map) jarjestetty)]

    ;; Yhdistetään: lupaussanktio-osio (jos on) + muut sanktiot
    ;; Huom: palautetaan suorat alkiot (ei kietoutuneita vektoreita),
    ;; jotta testit löytävät [:otsikko "Sanktiot"] suoraan raportin alkioiden joukosta.
    (concat
      (when (seq lupaussanktio-lajit)
        ;; Lupaussanktiolle oma osio
        (list* [:otsikko "Lupaussanktiot"]
          (muodosta-sanktio-taulukko lupaussanktio-lajit sanktio-data-map)))
      ;; Sanktiot-osio näytetään myös silloin, kun toteutuneita arvoja ei ole.
      (list* [:otsikko "Sanktiot"]
        muut-taulukot))))

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
    [:taulukko {:otsikko "Bonukset"
                :sheet-nimi "Bonukset"
                :viimeinen-rivi-yhteenveto? true
                :tyhja (when (empty? bonus-lajit) "Ei bonuslajeja profiilissa.")}
     [{:leveys 12 :otsikko "Bonuslaji"}
      {:leveys 15 :otsikko "Summa (€)" :fmt :raha}]
     (into [] (concat bonus-rivit
                [{:lihavoi? true
                  :korosta-hennosti? true
                  :rivi (rivi "Bonukset yhteensä" bonukset-yhteensa)}]))]))

(defn- koosta-urakkataso-runko
  "Muodostaa urakkataso-sanktioraportin raporttirakennelman.

   Hoito-urakka: käyttää profiili-driven kyselyitä jotka palauttavat
   sanktiolaji/bonuslaji-tiedot suoraan tietokannasta.

   Ylläpito-urakka: näyttää sakot ylläpitoluokittain ryhmiteltynä.
   Parametri yllapitourakka? määrittää käytettävän layoutin."
  [urakan-nimi alkupvm loppupvm sanktiot bonukset arvonvahennykset sanktiolajit bonuslajit & [yllapitourakka?]]
  (let [otsikko (if yllapitourakka?
                  (str "Sakko- ja bonusraportti — " urakan-nimi
                    " " (pvm/pvm alkupvm) "-" (pvm/pvm loppupvm))
                  (str "Sanktiot, bonukset ja arvovähennykset — " urakan-nimi
                    " " (pvm/pvm alkupvm) "-" (pvm/pvm loppupvm)))

        [yhteenveto-data rungon-osat] (if yllapitourakka?
                                        ;; YLLÄPITO-URAKKA
                                        (let [sakkosumma (reduce + 0 (map #(or (:summa %) 0) sanktiot))
                                              muistutukset (filterv #(= "Muistutus" (:sanktiotyyppi_nimi %)) sanktiot)
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
                                              sanktiot-tunnistetut (filterv #(some? (:sanktiolaji_koodi %)) sanktiot-ilman-arvonvahennyksia)

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
                                                                 sanktiot-tunnistetut)

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
                                                                :viimeinen-rivi-yhteenveto? true
                                                                :tyhja (when (empty? arvonvahennykset) "Ei arvonvähennyksiä.")}]
                                                arvonvahennys-taulukko)]
                                             (when (seq tunnistamattomat-uniikit)
                                               [(koosta-tunnistamattomat-taulukko tunnistamattomat-uniikit)]))]))]

    (into [:raportti {:nimi urakan-nimi :orientaatio :landscape}
           [:otsikko otsikko]
           [:jakaja nil]
           [:display-flex
            [:sininen-laatikko {:otsikko "Yhteenveto"}
             yhteenveto-data]]]
      (remove nil? rungon-osat))))



(defn suorita
  "Raportin suoritin urakkataso-sanktioraportille (MHU25+).
   Käyttää profiili-driven SQL-kyselyitä: hae-urakkataso-sanktiot ja hae-urakkataso-bonukset.
   
   Tukee sekä hoito-urakkatyyppiä (profiilivetoiset kyselyt) että ylläpito-urakkatyyppiä 
   (ylläpitoluokka-ryhmittely)."
  [db _user {:keys [urakka-id elinvoimakeskus-id alkupvm loppupvm hoitovuosi urakkatyyppi]}]
  (let [urakan-tiedot (when urakka-id
                        (first (urakat-kyselyt/hae-urakka db urakka-id)))
        urakan-nimi (or (:nimi urakan-tiedot) "")

        ;; Tunnista urakkatyyppi (Default: hoito)
        yllapitourakka? (urakka-domain/yllapitourakka? (or urakkatyyppi
                                                         (keyword (:tyyppi urakan-tiedot))
                                                         :hoito))

        ;; Valitse kyselyt urakkatyypin mukaan
        [sanktiot bonukset arvonvahennykset sanktiolajit bonuslajit]
        (if yllapitourakka?
          ;; Ylläpito-urakka: käytetään ylläpito-kyselyä
          (let [sanktiot-yllapito (hae-sanktiot-yllapidon-raportille db
                                    {:urakka urakka-id
                                     :elinvoimakeskus nil
                                     :urakkatyyppi (when urakkatyyppi (name urakkatyyppi))
                                     :alku alkupvm
                                     :loppu loppupvm})]
            [sanktiot-yllapito nil nil nil nil])
          ;; Hoito-urakka: käytetään profiilivetoisia kyselyitä
          (let [hoitovuosi (or hoitovuosi
                             (when-let [urakan-alkupvm (:alkupvm urakan-tiedot)]
                               (pvm/paivamaara->mhu-hoitovuosi-nro urakan-alkupvm alkupvm)))
                sanktiolajit (hae-urakkataso-sanktiolajit db {:urakka urakka-id
                                                              :elinvoimakeskus elinvoimakeskus-id
                                                              :hoitovuosi hoitovuosi
                                                              :alku alkupvm
                                                              :loppu loppupvm})
                bonuslajit (hae-urakkataso-bonuslajit db {:urakka urakka-id
                                                          :elinvoimakeskus elinvoimakeskus-id
                                                          :hoitovuosi hoitovuosi})
                sanktiot (hae-urakkataso-sanktiot db {:urakka urakka-id
                                                      :elinvoimakeskus elinvoimakeskus-id
                                                      :alku alkupvm
                                                      :loppu loppupvm
                                                      :hoitovuosi hoitovuosi})
                bonukset (hae-urakkataso-bonukset db {:urakka urakka-id
                                                      :elinvoimakeskus elinvoimakeskus-id
                                                      :alku alkupvm
                                                      :loppu loppupvm
                                                      :hoitovuosi hoitovuosi})
                arvonvahennykset (filterv #(= "arvonvahennyssanktio" (:sanktiolaji_koodi %)) sanktiot)]
            [sanktiot bonukset arvonvahennykset sanktiolajit bonuslajit]))]
    (koosta-urakkataso-runko urakan-nimi alkupvm loppupvm sanktiot bonukset arvonvahennykset sanktiolajit bonuslajit yllapitourakka?)))
