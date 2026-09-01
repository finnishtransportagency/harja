(ns harja.palvelin.raportointi.raportit.sanktio
  (:require [jeesql.core :refer [defqueries]]
            [clojure.string :as str]
            [harja.palvelin.raportointi.raportit.yleinen :refer [rivi]]
            [harja.kyselyt.urakat :as urakat-kyselyt]
            [harja.kyselyt.hallintayksikot :as hallintayksikot-q]
            [harja.pvm :as pvm]
            [harja.domain.urakka :as urakka-domain]
            [harja.domain.yllapitokohde :as yllapitokohteet-domain]
            [harja.domain.laadunseuranta.sanktiotyyppi :as sanktiotyyppi]))

(defqueries "harja/palvelin/raportointi/raportit/sanktiot.sql")

(declare hae-urakkataso-sanktiot hae-urakkataso-bonukset hae-urakkataso-sanktiolajit hae-urakkataso-bonuslajit
  hae-urakkataso-yllapito-sanktiot hae-urakkataso-yllapito-bonukset)

(defn- muodosta-rahasarake [otsikko]
  {:leveys 15
   :otsikko (str otsikko " (€)")
   :fmt :raha})

(defn- yksiloi-sanktiot [rivit]
  (loop [jaljella (seq rivit)
         tunnisteet #{}
         yksilolliset []]
    (if-let [rivi (first jaljella)]
      (let [tunniste (or (:sanktio_id rivi) (:id rivi))
            avain (if (nil? tunniste)
                    [:rivi (count yksilolliset)]
                    tunniste)]
        (if (contains? tunnisteet avain)
          (recur (next jaljella) tunnisteet yksilolliset)
          (recur (next jaljella)
            (conj tunnisteet avain)
            (conj yksilolliset rivi))))
      yksilolliset)))

(defn- koosta-arvonvahennys-taulukko [arvonvahennykset]
  (let [arvonvahennykset (yksiloi-sanktiot arvonvahennykset)
        rivit (if (seq arvonvahennykset)
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
        tyypit lajit
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
              :rivi (rivi (sanktiotyyppi/sanktiotyypin-nimi
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
   Data tulee hae-urakkataso-yllapito-sanktiot-kyselyltä."
  [sanktiot]
  (let [yllapitoluokan-nimi (fn [luokka]
                              (cond
                                (nil? luokka) "Ei PK-luokkaa"
                                (get yllapitokohteet-domain/yllapitoluokkanumero->lyhyt-nimi luokka)
                                (str "PK-luokka "
                                  (get yllapitokohteet-domain/yllapitoluokkanumero->lyhyt-nimi luokka))
                                (keyword? luokka) (name luokka)
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
    ;; Palautetaan suorat alkiot, jotta raportin kaikki esitysmuodot käsittelevät
    ;; osiot samalla tavalla.
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

(defn- koosta-yllapidon-bonustaulukko
  "Muodostaa ylläpidon legacy-bonuksille oman bonus-taulukon."
  [bonukset]
  (when (seq bonukset)
    (let [bonuslajit (->> bonukset
                       (group-by :bonuslaji_koodi)
                       (map (fn [[_ rivit]]
                              (let [ensimmainen (first rivit)]
                                {:bonuslaji_koodi (:bonuslaji_koodi ensimmainen)
                                 :bonuslaji_nimi (:bonuslaji_nimi ensimmainen)
                                 :bonuslaji_jarjestys (or (:bonuslaji_jarjestys ensimmainen) 0)})))
                       (sort-by (juxt :bonuslaji_jarjestys :bonuslaji_koodi)))
          bonus-data-map (reduce (fn [summa-map bonus]
                                   (update summa-map (:bonuslaji_koodi bonus)
                                     (fnil + 0) (or (:summa bonus) 0)))
                           {}
                           bonukset)]
      (muodosta-bonus-taulukko bonuslajit bonus-data-map))))

(defn- koosta-yllapidon-taulukot [sanktiot bonukset arvonvahennykset]
  (let [tunnetut (filterv #(some? (:sanktiolaji_koodi %)) sanktiot)
        tunnistamattomat (filterv #(nil? (:sanktiolaji_koodi %)) sanktiot)]
    (remove nil?
      [(koosta-yllapito-taulukko tunnetut)
       (when (seq tunnistamattomat)
         (koosta-tunnistamattomat-taulukko tunnistamattomat))
       (koosta-yllapidon-bonustaulukko bonukset)
       (into [:taulukko {:otsikko "Arvonvähennykset"
                         :sheet-nimi "Arvonvähennykset"
                         :excel-alkutekstit [[:otsikko-heading "Arvonvähennykset"]]
                         :viimeinen-rivi-yhteenveto? true
                         :tyhja "Ei arvonvähennyksiä."}]
         (koosta-arvonvahennys-taulukko arvonvahennykset))])))

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
        vuosi (fn [paivamaara] (when paivamaara (pvm/vuosi paivamaara)))
        kesto (when (and alkupvm loppupvm)
                (str (vuosi alkupvm) "-" (vuosi loppupvm)))]
    (str (urakan-nimi rivit)
      (when (and kesto (not (str/includes? (urakan-nimi rivit) kesto)))
        (str " " kesto)))))

(defn- urakan-raportin-tiedot
  [rivit]
  (let [rivi (first rivit)
        alkupvm (:urakan_alkupvm rivi)
        loppupvm (or (:urakan_loppupvm rivi) (:loppupvm rivi))]
    {:raportin-nimi (urakan-nimi rivit)
     :alkupvm (when alkupvm (pvm/pvm alkupvm))
     :loppupvm (when loppupvm (pvm/pvm loppupvm))}))

(defn- urakan-valilehtien-nimet
  [urakat]
  (let [nimet (mapv (fn [[_ rivit]] (urakan-valilehden-nimi rivit)) urakat)
        toistuvat-nimet (->> nimet
                          frequencies
                          (keep (fn [[nimi maara]]
                                  (when (> maara 1) nimi)))
                          set)]
    (mapv (fn [[[urakan-id _] nimi]]
            [urakan-id
             (if (contains? toistuvat-nimet nimi)
               (str nimi " (" urakan-id ")")
               nimi)])
      (map vector urakat nimet))))

(defn- merkitse-taulukot
  [taulukot nimi & [raportin-tiedot]]
  (loop [jaljella (seq taulukot)
         indeksi 0
         tulos []]
    (if-let [taulukko (first jaljella)]
      (if (= :taulukko (first taulukko))
        (recur (next jaljella)
          (inc indeksi)
          (conj tulos
            (update taulukko 1 merge
              {:sheet-nimi nimi
               :samalle-sheetille? (pos? indeksi)}
              (when raportin-tiedot
                {:raportin-tiedot raportin-tiedot}))))
        (recur (next jaljella)
          indeksi
          (conj tulos taulukko)))
      tulos)))

(defn- koosta-urakan-taulukot
  [sanktiot bonukset arvonvahennykset sanktiolajit bonuslajit]
  (let [sanktiot (->> sanktiot
                   (remove #(= "arvonvahennyssanktio" (:sanktiolaji_koodi %)))
                   yksiloi-sanktiot)
        arvonvahennykset (yksiloi-sanktiot arvonvahennykset)
        tunnistamattomat (filterv #(nil? (:sanktiolaji_koodi %)) sanktiot)
        tunnetut (filterv #(some? (:sanktiolaji_koodi %)) sanktiot)
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
        taulukot (concat
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
                     [(koosta-tunnistamattomat-taulukko tunnistamattomat)]))]
    {:sanktiot sanktiot
     :arvonvahennykset arvonvahennykset
     :tunnistamattomat tunnistamattomat
     :tunnetut tunnetut
     :sanktio-data-map sanktio-data-map
     :bonus-data-map bonus-data-map
     :taulukot taulukot}))

(defn- koosta-excel-yhteenvetotaulukko [yhteenveto-data excel-alkutekstit]
  [:taulukko {:otsikko "Yhteenveto"
              :sheet-nimi "Yhteenveto"
              :excel-alkutekstit excel-alkutekstit}
   [{:otsikko "Rivi"}
    {:otsikko "Arvo"}]
   (mapv (fn [{:keys [avain arvo fmt lihavoi?]}]
           {:rivi [avain arvo]
             :fmt fmt
             :lihavoi? lihavoi?})
      yhteenveto-data)])

(defn- koosta-urakka-erittely
  "Muodostaa urakkakohtaisen erittelyn litteänä jonona raporttielementtejä.

   HUOM: alkiot on palautettava raportin suorina sisaralkioina. Jos ne kiedotaan
   :otsikko-elementin lapsiksi, HTML- ja PDF-muodostimet destrukturoivat vain
   [_ teksti] ja pudottavat taulukot äänettömästi."
  [sanktiot bonukset arvonvahennykset sanktiolajit bonuslajit yllapitourakka?]
  (let [rivit (concat sanktiot bonukset arvonvahennykset)
        urakat (->> rivit
                 (group-by urakka-id)
                 (remove (comp nil? first))
                 (sort-by (juxt (comp urakan-nimi second) first)))]
    (when (seq urakat)
      (cons [:otsikko "Urakat"]
        (mapcat (fn [[[urakan-id urakan-rivit] [_ valilehden-nimi]]]
                  (let [urakan-nimi (urakan-nimi urakan-rivit)
                        urakan-sanktiolajit (filter #(= urakan-id (urakka-id %)) sanktiolajit)
                        urakan-bonuslajit (filter #(= urakan-id (urakka-id %)) bonuslajit)
                        sanktiot (filter #(and (or (:sanktio_id %) (:id %))
                                            (not= "arvonvahennyssanktio" (:sanktiolaji_koodi %)))
                                  urakan-rivit)
                        bonukset (filter :bonuslaji_koodi urakan-rivit)
                        arvonvahennykset (filter #(= "arvonvahennyssanktio" (:sanktiolaji_koodi %))
                                           urakan-rivit)
                        taulukot (if yllapitourakka?
                                   (koosta-yllapidon-taulukot sanktiot bonukset arvonvahennykset)
                                   (:taulukot (koosta-urakan-taulukot
                                                sanktiot
                                                bonukset
                                                arvonvahennykset
                                                urakan-sanktiolajit
                                                urakan-bonuslajit)))]
                    (into [[:otsikko urakan-nimi]]
                      (merkitse-taulukot taulukot valilehden-nimi
                        (urakan-raportin-tiedot urakan-rivit)))))
          (map vector urakat (urakan-valilehtien-nimet urakat)))))))

(defn- koosta-urakkataso-runko
  "Muodostaa urakkataso-sanktioraportin raporttirakennelman.

   Hoito-urakka: käyttää profiilivetoisia kyselyitä, jotka palauttavat
   sanktiolaji/bonuslaji-tiedot suoraan tietokannasta.

   Ylläpito-urakka: näyttää sakot ylläpitoluokittain ryhmiteltynä.
   Parametri yllapitourakka? määrittää käytettävän rakenteen."
  [urakan-nimi alkupvm loppupvm sanktiot bonukset arvonvahennykset sanktiolajit bonuslajit
   & [yllapitourakka? urakka-erittely? kasittelija]]
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
                                               bonukset-summa (reduce + 0 (map #(or (:summa %) 0) (or bonukset [])))
                                               arvonvahennykset-summa (reduce + 0 (map #(or (:summa %) 0)
                                                                                    (or arvonvahennykset [])))]
                                          [[{:avain "Sakot yhteensä" :arvo sakkosumma :fmt :raha}
                                            {:avain "Bonukset yhteensä" :arvo bonukset-summa :fmt :raha}
                                            {:avain "Muistutukset" :arvo (str muistutusten-maara " kpl")}
                                            {:avain "Suorasakot" :arvo suorasakkojen-summa :fmt :raha}
                                            {:avain "Arvovähennykset" :arvo arvonvahennykset-summa :fmt :raha}
                                            {:avain "Yhteensä" :arvo (+ sakkosumma bonukset-summa arvonvahennykset-summa)
                                              :fmt :raha :lihavoi? true}]
                                           (merkitse-taulukot
                                              (koosta-yllapidon-taulukot sanktiot bonukset arvonvahennykset)
                                              urakan-nimi)])

                                        ;; HOITO-URAKKA
                                        (let [kooste (koosta-urakan-taulukot
                                                       sanktiot
                                                       (or bonukset [])
                                                       arvonvahennykset
                                                       sanktiolajit
                                                       bonuslajit)
                                              sanktiot-uniikit (:sanktiot kooste)
                                              arvonvahennykset-summa (reduce + 0 (map #(or (:summa %) 0) (:arvonvahennykset kooste)))
                                              sanktiot-tunnistetut-uniikit (:tunnetut kooste)

                                              ;; HUOM: hae-urakkataso-sanktiot palauttaa yhden rivin per
                                              ;; sanktiolaji/sanktiotyyppi-yhdistelmä, joten samalla sanktio_id:llä
                                              ;; voi olla useita rivejä. Yhteenvetolukuja (summa, määrät) varten
                                              ;; sanktiot pitää ottaa kertaalleen per sanktio_id.
                                              ;; HUOM: sanktiolla ei välttämättä ole sanktio_profiili_riviä
                                              ;; (esim. vanhentunut/poistettu sanktiotyyppi). Nämä sanktiot
                                              ;; erotetaan omaan taulukkoonsa, mutta niiden summa lasketaan
                                              ;; mukaan "Sanktiot yhteensä" -lukuun - rahaa ei saa kadota.
                                              sanktiot-yhteensa (reduce + 0 (map #(or (:summa %) 0) sanktiot-uniikit))
                                              bonukset-yhteensa (reduce + 0 (map #(or (:summa %) 0) (or bonukset [])))
                                              muistutusten-maara (count (filterv #(= "muistutus" (:sanktiolaji_koodi %)) (:tunnetut kooste)))
                                              vastuuhenkilon-vaihto-summa (reduce + 0
                                                                            (map #(or (:summa %) 0)
                                                                              (filterv #(= "vastuuhenkilon-vaihto" (:sanktiotyyppi_koodi %)) sanktiot-tunnistetut-uniikit)))

                                              ]

                                          [[{:avain "Sanktiot yhteensä" :arvo sanktiot-yhteensa :fmt :raha}
                                            {:avain "Bonukset yhteensä" :arvo bonukset-yhteensa :fmt :raha}
                                            {:avain "Kirjalliset muistutukset" :arvo (str muistutusten-maara " kpl")}
                                            {:avain "Vastuuhenkilön vaihto" :arvo vastuuhenkilon-vaihto-summa :fmt :raha}
                                            {:avain "Arvovähennykset" :arvo arvonvahennykset-summa :fmt :raha}
                                            {:avain "Yhteensä" :arvo (+ sanktiot-yhteensa bonukset-yhteensa arvonvahennykset-summa) :fmt :raha :lihavoi? true}]

                                           (merkitse-taulukot (:taulukot kooste) urakan-nimi)]))]

    (into [:raportti {:nimi urakan-nimi
                      :orientaatio :landscape
                      :urakan-nimi urakan-nimi
                      :aikajakso aikajakso
                      :otsikon-koko :iso
                      :raportin-yleiset-tiedot {:raportin-nimi urakan-nimi}
                      :piilota-otsikko? true
                      :alkupvm alkupvm
                      :loppupvm loppupvm}
      [:otsikko-title raportin-otsikko]
      [:teksti (str urakan-nimi " | Aikaväli: " aikajakso)
       {:luokka "raportin-otsikkorivi"}]
      [:display-flex
       [:sininen-laatikko {:otsikko "Yhteenveto"}
        yhteenveto-data]]]
      (concat
        (when (and (= :excel kasittelija)
                (not urakka-erittely?))
          [(koosta-excel-yhteenvetotaulukko
             yhteenveto-data
             [[:otsikko-title raportin-otsikko]
              [:teksti (str urakan-nimi " | Aikaväli: " aikajakso)]])])
        (when-not (and (= :excel kasittelija)
                    urakka-erittely?)
          (remove nil? rungon-osat))
        (when urakka-erittely?
          (koosta-urakka-erittely sanktiot bonukset arvonvahennykset sanktiolajit bonuslajit
            yllapitourakka?))))))



(defn suorita
  "Raportin suoritin urakkataso-sanktioraportille (MHU25+).
   Käyttää profiilivetoisia SQL-kyselyitä: hae-urakkataso-sanktiot ja hae-urakkataso-bonukset.
   
   Tukee sekä hoito-urakkatyyppiä (profiilivetoiset kyselyt) että ylläpito-urakkatyyppiä 
   (ylläpitoluokka-ryhmittely)."
  [db _user {:keys [urakka-id elinvoimakeskus-id alkupvm loppupvm hoitovuosi urakkatyyppi kasittelija]}]
  (let [urakan-tiedot (when urakka-id
                        (first (urakat-kyselyt/hae-urakka db urakka-id)))
        urakan-nimi (or (:nimi urakan-tiedot)
                      (when elinvoimakeskus-id
                        (:nimi (first (hallintayksikot-q/hae-organisaatio db elinvoimakeskus-id))))
                      (when (and (nil? urakka-id) (nil? elinvoimakeskus-id))
                        "Koko maa")
                      "")

        ;; Tunnista urakkatyyppi (oletus: hoito)
        yllapitourakka? (urakka-domain/yllapitourakka? (or urakkatyyppi
                                                         (keyword (:tyyppi urakan-tiedot))
                                                         :hoito))
        hoitovuosi (or hoitovuosi
                      (when-let [urakan-alkupvm (:alkupvm urakan-tiedot)]
                        (pvm/paivamaara->mhu-hoitovuosi-nro urakan-alkupvm alkupvm)))
        kyselyparametrit (select-keys
                           {:urakka urakka-id
                            :elinvoimakeskus elinvoimakeskus-id
                            :urakkatyyppi (when urakkatyyppi (name urakkatyyppi))
                            :alku alkupvm
                            :loppu loppupvm
                            :hoitovuosi hoitovuosi}
                           [:urakka :elinvoimakeskus :urakkatyyppi :alku :loppu :hoitovuosi])

        ;; Valitse kyselyt urakkatyypin mukaan
        [sanktiot bonukset arvonvahennykset sanktiolajit bonuslajit]
        (if yllapitourakka?
          ;; Ylläpito-urakka: käytetään ylläpito-kyselyä
          (let [sanktiot-yllapito (hae-urakkataso-yllapito-sanktiot db kyselyparametrit)
                bonukset-yllapito (hae-urakkataso-yllapito-bonukset db kyselyparametrit)
                arvonvahennykset (filterv #(= "arvonvahennyssanktio" (:sanktiolaji_koodi %))
                                   sanktiot-yllapito)]
            [(remove #(= "arvonvahennyssanktio" (:sanktiolaji_koodi %))
               sanktiot-yllapito)
             bonukset-yllapito
             arvonvahennykset
             nil
             nil])
          ;; Hoito-urakka: käytetään profiilivetoisia kyselyitä
          (let [sanktiot (hae-urakkataso-sanktiot db kyselyparametrit)
                bonukset (hae-urakkataso-bonukset db kyselyparametrit)
                sanktiolajit (hae-urakkataso-sanktiolajit db kyselyparametrit)
                bonuslajit (hae-urakkataso-bonuslajit db kyselyparametrit)
                arvonvahennykset (filterv #(= "arvonvahennyssanktio" (:sanktiolaji_koodi %)) sanktiot)]
            [sanktiot bonukset arvonvahennykset sanktiolajit bonuslajit]))]
    (koosta-urakkataso-runko urakan-nimi alkupvm loppupvm sanktiot bonukset arvonvahennykset sanktiolajit bonuslajit
      yllapitourakka?
      (nil? urakka-id)
      kasittelija)))
