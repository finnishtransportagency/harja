(ns harja.palvelin.raportointi.raportit.vastaanottotarkastus-mhu
  "MHU-urakoiden vastaanottotarkastusraportti.
  Koostuu Lupauksista, Ympäristöraportista, Talvisuolan kokonaiskäyttömäärästä, Tavoitehinnan muutoksista,
  Lisätöistä, Kirjallisista muistutuksista, sanktioista, arvonvähennyksistä ja poikkeamaraporteista, Tehtävämääristä,
  ja Laskutusyhteenvedosta."
  (:require [harja.kyselyt.materiaalit :as materiaalit-kyselyt]
            [harja.kyselyt.rahavaraukset :as rahavaraus-kyselyt]
            [harja.kyselyt.urakat :as urakat-q]
            [harja.kyselyt.valikatselmus :as valikatselmus-q]
            [harja.domain.lupaus-domain :as lupaus-domain]
            [harja.palvelin.palvelut.lupaus.lupaus-palvelu :as lupaus-palvelu]
            [harja.palvelin.palvelut.muutos.muutos-palvelu :as muutos-palvelu]
            [harja.palvelin.raportointi.raportit.yleinen :as yleinen]
            [harja.palvelin.raportointi.raportit.talvihoitosuolan-kokonaiskayttomaara :as talvisuola]
            [harja.palvelin.raportointi.raportit.muutos-ja-lisatyoraportti :as muutos-ja-lisatyoraportti]
            [harja.pvm :as pvm]))

(defn- summa [rivit avain]
  (reduce + 0 (keep avain rivit)))

(defn- hae-bonus-sanktiot [db urakka-id hoitokausi hoitovuosi]
  (let [[alkupvm loppupvm] hoitokausi
        bonukset (valikatselmus-q/hae-bonukset db {:urakka-id urakka-id
                                                   :alkupvm alkupvm
                                                   :loppupvm loppupvm})
        sanktiot (valikatselmus-q/hae-sanktiot db {:urakka-id urakka-id
                                                   :alkupvm alkupvm
                                                   :loppupvm loppupvm
                                                   :hoitokauden-alkuvuosi hoitovuosi})]
    (+ (summa bonukset :rahasumma)
      (summa sanktiot :maara))))

(defn- lupausrivi [db urakka-id vanha-urakka? {:keys [alkupvm loppupvm]}]
  (let [hoitovuosi (pvm/vuosi alkupvm)
        hoitokausi [alkupvm loppupvm]
        lupaus-parametrit {:urakka-id urakka-id
                           :valittu-hoitokausi hoitokausi
                           :nykyhetki (pvm/nyt)}
        lupaustiedot (if vanha-urakka?
                       (lupaus-palvelu/hae-kuukausittaiset-pisteet-hoitokaudelle db lupaus-parametrit)
                       (lupaus-palvelu/hae-urakan-lupaustiedot-hoitokaudelle db lupaus-parametrit))]
    [(str hoitovuosi "-" (pvm/vuosi loppupvm))
     (get-in lupaustiedot [:lupaus-sitoutuminen :pisteet])
     (get-in lupaustiedot [:yhteenveto :pisteet :toteuma])
     (hae-bonus-sanktiot db urakka-id hoitokausi hoitovuosi)]))

(defn lupaukset-taulukko [db urakka-id urakan-tiedot hoitokaudet]
  (let [vanha-urakka? (lupaus-domain/urakka-19-20? urakan-tiedot)]
    [:taulukko {:otsikko "Lupaukset"
                :tyhja (when (empty? hoitokaudet) "Ei hoitovuosia.")
                :sheet-nimi "Lupaukset"}
     [{:otsikko "Hoitovuosi" :leveys 5}
      {:otsikko "Tarjouksen lupauspisteet" :leveys 5}
      {:otsikko "Toteutuneet lupauspisteet" :leveys 5}
      {:otsikko "Bonus/Sanktiot (€)" :leveys 5 :fmt :raha}]
     (mapv #(lupausrivi db urakka-id vanha-urakka? %) hoitokaudet)]))

(defn- talvisuolan-erittely
  "Talvisuolan kokonaiskäyttömäärä osion sisään tulee yhteenveto ja hoitovuosikotainen erittely."
  [db urakka-id urakan-alkupvm urakan-loppupvm]
  (let [talvisuolan-kokonaismaara (first (materiaalit-kyselyt/hae-talvisuolan-kokonaismaara db
                                           {:urakka-id urakka-id
                                            :alkupvm urakan-alkupvm
                                            :loppupvm urakan-loppupvm}))
        talvisuolan-raportin-osiot (yleinen/osat (talvisuola/suorita db nil {:urakka-id urakka-id
                                                                             :kasittelija :excel}))
        yhteenveto (some #(when (= "Koko urakka-ajan yhteenveto (kuivatonneina)"
                                  (get-in % [1 :otsikko])) %)
                     talvisuolan-raportin-osiot)
        kohtuullistettu-kayttoraja-plus-viisi-prosenttia
        (some #(when (= "Suurin urakassa sallittu käyttömäärä + 5 %" (first %))
                 (get-in % [1 1 :arvo]))
          (nth yhteenveto 3))
        sanktiot (valikatselmus-q/hae-sanktiot db {:urakka-id urakka-id
                                                   :alkupvm urakan-alkupvm
                                                   :loppupvm urakan-loppupvm
                                                   :hoitokauden-alkuvuosi (pvm/vuosi urakan-alkupvm)})
        kirjattu-sakon-maara (summa (filter #(= :talvisuolan_ylitys (:sakkoryhma %)) sanktiot) :maara)
        toteuma (or (:kokonaismaara talvisuolan-kokonaismaara) 0)
        erotus (- toteuma (or kohtuullistettu-kayttoraja-plus-viisi-prosenttia 0))
        yhteenveto-taulukko [:taulukko {:otsikko "Yhteenveto"
                                        :leveysprosentti 50
                                        :viimeinen-rivi-yhteenveto? false
                                        :sheet-nimi "Talvisuolan yhteenveto"
                                        :piilota-otsikot? true}
                             [{:otsikko "" :leveys 8}
                              {:otsikko "" :leveys 2 :tasaa :oikea}]
                             [["Kohtuullistettu käyttöraja + 5% (tonnia)"
                               [:arvo {:arvo kohtuullistettu-kayttoraja-plus-viisi-prosenttia
                                       :desimaalien-maara 2}]]
                              ["Toteuma (tonnia)"
                               [:arvo {:arvo toteuma
                                       :desimaalien-maara 2}]]
                              ["Erotus (tonnia)"
                               [:arvo {:arvo erotus
                                       :desimaalien-maara 2}]]
                              ["Kirjattu sakon määrä (euroa)"
                               [:arvo {:arvo kirjattu-sakon-maara
                                       :fmt :raha
                                       :desimaalien-maara 2}]]]]
        erittely (some #(when (= "Erittely hoitovuosittain" (get-in % [1 :otsikko])) %)
                   talvisuolan-raportin-osiot)]
    [yhteenveto-taulukko erittely]))

(defn rahavarausten-tavoitehinnan-muutokset-taulukko [db urakka-id hoitokaudet]
  (let [urakan-rahavaraukset (rahavaraus-kyselyt/hae-urakan-rahavaraukset db {:urakka_id urakka-id})
        rivit (mapv (fn [{:keys [alkupvm loppupvm]}]
                      (let [hoitokauden-alkuvuosi (pvm/vuosi alkupvm)
                            rahavaraukset (rahavaraus-kyselyt/muutosten-rahavaraukset
                                            db urakka-id hoitokauden-alkuvuosi)
                            rahavaraukset-idlla (into {}
                                                  (map (juxt :id identity)
                                                    (remove #(= :yhteenveto (:id %)) rahavaraukset)))

                            rivit (into [(str hoitokauden-alkuvuosi "-" (pvm/vuosi loppupvm))]
                                    (let [tavoitehinnan-muutos (:tavoitehinnan-muutos (last rahavaraukset))]
                                      (concat
                                        (mapcat (fn [{:keys [id]}]
                                                  (let [rahavaraus (get rahavaraukset-idlla id)]
                                                    [(or (:summa-indeksikorjattu rahavaraus) 0)
                                                     (or (:toteumat rahavaraus) 0)]))
                                          urakan-rahavaraukset)
                                        [tavoitehinnan-muutos])))]
                        rivit))
                hoitokaudet)
        otsikot (into [{:otsikko "Hoitokausi" :leveys 5}]
                  (concat (mapcat (fn [_]
                                    [{:otsikko "Suunniteltu määrä (€)" :leveys 5 :fmt :raha}
                                     {:otsikko "Toteutunut määrä (€)" :leveys 5 :fmt :raha}])
                            urakan-rahavaraukset)
                    [{:otsikko "Tavoitehinnan muutos (€)" :leveys 5 :fmt :raha}]))]
    [:taulukko {:otsikko "Rahavarausten tavoitehintamuutokset"
                :tyhja (when (empty? hoitokaudet) "Ei hoitovuosia.")
                :sheet-nimi "Rahavarausten tavoitehintamuutokset"
                :rivi-ennen (into [{:sarakkeita 1}]
                              (concat
                                (map (fn [{:keys [nimi]}]
                                       {:teksti nimi
                                        :sarakkeita 2
                                        :luokka "paallystys-tausta-tumma"
                                        :tasaa :oikea})
                                  urakan-rahavaraukset)
                                [{:sarakkeita 1}]))}
     otsikot
     rivit]))

(defn muodosta-tavoitehinnan-muutokset [db user urakka-id hoitokaudet kasittelija]
  (let [rivit (mapv (fn [hoitokausi]
                      (let [{:keys [alkupvm loppupvm]} hoitokausi

                            vuosi (pvm/vuosi alkupvm)
                            kirjallisesti-sovitut-muutokset (muutos-ja-lisatyoraportti/hae-kirjallisesti-sovitut-muutokset-raportille
                                                              db {:urakka-id urakka-id
                                                                  :alkupvm alkupvm
                                                                  :loppupvm loppupvm
                                                                  :hoitokauden-alkuvuosi vuosi})
                            kirjallisesti-sovitut-yht (reduce + 0 (map
                                                                    muutos-ja-lisatyoraportti/muutoksen-tavoitehinnan-muutos
                                                                    kirjallisesti-sovitut-muutokset))
                            maaramuutokset (when urakka-id
                                             (muutos-palvelu/hae-tehtava-maaramuutokset db user {:urakka-id urakka-id
                                                                                                 :valittu-hoitokausi [alkupvm loppupvm]
                                                                                                 ;; Muutokset kaipaa hoitokaudet eri formaatissa
                                                                                                 :hoitokaudet (map (fn [rivi] [(:alkupvm rivi) (:loppupvm rivi)]) hoitokaudet)
                                                                                                 :laskenta-automatiikka? true}))

                            maaramuutokset-yht (reduce + 0 (map muutos-ja-lisatyoraportti/laske-tavoitehinnan-muutos maaramuutokset))

                            rahavaraukset (when urakka-id
                                            (rahavaraus-kyselyt/muutosten-rahavaraukset db urakka-id vuosi))
                            rahavaraus-yhteenveto (first (filter #(= (:id %) :yhteenveto) rahavaraukset))
                            rahavaraukset-yht (or (:tavoitehinnan-muutos rahavaraus-yhteenveto) 0)

                            toteumiin-perustuvat-yht (+ maaramuutokset-yht rahavaraukset-yht)

                            ;; Yhteensä
                            yhteensa (+ kirjallisesti-sovitut-yht
                                       toteumiin-perustuvat-yht)]
                        [(str vuosi "-" (pvm/vuosi loppupvm))
                         yhteensa]))
                hoitokaudet)

        muutokset-yhteensa (reduce + 0 (map #(or (second %) 0) rivit))
        muutokset-yhteensarivi [{:lihavoi? true
                                 :korosta-hennosti? true
                                 :rivi ["Yhteensä" muutokset-yhteensa]}]
        otsikko-title [:otsikko-title "Harjaan kirjatut tavoitehinnan muutokset"]]


    [[:taulukko {:viimeinen-rivi-yhteenveto? true
                 :leveysprosentti 50
                 :otsikko "Harjaan kirjatut tavoitehinnan muutokset"
                 :sheet-nimi "Harjaan kirjatut tavoitehinnan muutokset"
                 :excel-alkutekstit (when (= kasittelija :excel) [otsikko-title])}
      [{:leveys 5 :otsikko "Hoitovuosi"}
       {:leveys 5 :otsikko "Kirjatut tavoitehinnan muutokset yhteensä (€)" :fmt :raha}]
      (into [] (concat rivit (when-not (empty? rivit) muutokset-yhteensarivi)))]]))

(defn muodosta-tavoitehinnan-oikaisut [db urakka-id hoitokaudet kasittelija]
  (let [rivit (mapv (fn [hoitokausi]
                      (let [{:keys [alkupvm loppupvm]} hoitokausi

                            vuosi (pvm/vuosi alkupvm)
                            oikaisut (muutos-ja-lisatyoraportti/hae-tavoitehinnan-oikaisut db {:urakka-id urakka-id
                                                                                               :hoitovuosi vuosi})
                            tavoitehinnan-muutos (reduce + 0 (map #(or (:tavoitehinnan_muutos %) 0) oikaisut))]
                        [(str vuosi "-" (pvm/vuosi loppupvm))
                         tavoitehinnan-muutos]))
                hoitokaudet)

        oikaisut-yhteensa (reduce + 0 (map #(or (second %) 0) rivit))
        oikaisut-yhteensarivi [{:lihavoi? true
                                :korosta-hennosti? true
                                :rivi ["Yhteensä" oikaisut-yhteensa]}]
        otsikko-title [:otsikko-title "Harjaan kirjatut tavoitehinnan muutokset"]]


    [[:taulukko {:viimeinen-rivi-yhteenveto? true
                 :leveysprosentti 50
                 :otsikko "Harjaan kirjatut tavoitehinnan muutokset"
                 :sheet-nimi "Harjaan kirjatut tavoitehinnan muutokset"
                 :excel-alkutekstit (when (= kasittelija :excel) [otsikko-title])}
      [{:leveys 5 :otsikko "Hoitovuosi"}
       {:leveys 5 :otsikko "Kirjatut tavoitehinnan muutokset yhteensä (€)" :fmt :raha}]
      (into [] (concat rivit (when-not (empty? rivit) oikaisut-yhteensarivi)))]]))

(defn muodosta-lisatyo-taulukko [db urakka-id hoitokaudet kasittelija]
  (let [rivit (mapv (fn [hoitokausi]
                      (let [{:keys [alkupvm loppupvm]} hoitokausi
                            vuosi (pvm/vuosi alkupvm)
                            lisatyot (muutos-ja-lisatyoraportti/hae-lisatoiden-kulukohdistukset db {:urakka-id urakka-id
                                                                                                    :alkupvm alkupvm
                                                                                                    :loppupvm loppupvm})
                            yhteensa (reduce + 0 (map #(or (:summa %) 0) lisatyot))]
                        [(str vuosi "-" (pvm/vuosi loppupvm))
                         yhteensa]))
                hoitokaudet)
        lisatyot-yhteensa (reduce + 0 (map #(or (second %) 0) rivit))
        lisatyot-yhteensarivi [{:lihavoi? true
                                :korosta-hennosti? true
                                :rivi ["Yhteensä" lisatyot-yhteensa]}]
        otsikko-title [:otsikko-title "Lisätyöt"]]
    [[:taulukko {:otsikko "Lisätyöt"
                 :leveysprosentti 50
                 :viimeinen-rivi-yhteenveto? true
                 :sheet-nimi "Lisätyöt"
                 :excel-alkutekstit (when (= kasittelija :excel) [otsikko-title])}
      [{:leveys 5 :otsikko "Hoitovuosi"}
       {:leveys 5 :otsikko "Lisätyöt (€)" :fmt :raha}]
      (into [] (concat rivit (when-not (empty? rivit) lisatyot-yhteensarivi)))]]))

(defn suorita [db user {:keys [urakka-id kasittelija]}]
  (let [urakan-tiedot (first (urakat-q/hae-urakka db {:id urakka-id}))
        urakan-parametrit (first (urakat-q/hae-urakan-parametrit db urakka-id))
        raportin-nimi (str "Vastaanottotarkastus - MHU " (:nimi urakan-tiedot))
        hoitokaudet (sort-by :alkupvm (urakat-q/hae-urakan-hoitokaudet db urakka-id))
        talvisuolan-erittely (talvisuolan-erittely db urakka-id (:alkupvm urakan-tiedot) (:loppupvm urakan-tiedot))]
    (into [:raportti {:orientaatio :landscape
                      :nimi raportin-nimi
                      :urakan-nimi (:nimi urakan-tiedot)
                      :otsikon-koko :iso
                      :raportin-yleiset-tiedot raportin-nimi}
           (lupaukset-taulukko db urakka-id urakan-tiedot hoitokaudet)]
      (concat
        (when talvisuolan-erittely
          (into [[:otsikko "Talvisuolan kokonaiskäyttömäärä"]]
            talvisuolan-erittely))
        [[:otsikko "Tavoitehinnan muutokset"]
         (rahavarausten-tavoitehinnan-muutokset-taulukko db urakka-id hoitokaudet)]

        (if (:muutosten_hallinta urakan-parametrit)
          ;; Käytännössä -25 ja sitä vanhemmilla urakoilla
          (muodosta-tavoitehinnan-muutokset db user urakka-id hoitokaudet kasittelija)
          ;; Käytännössä -24 ja sitä nuoremmilla urakoilla
          (muodosta-tavoitehinnan-oikaisut db urakka-id hoitokaudet kasittelija))

        (muodosta-lisatyo-taulukko db urakka-id hoitokaudet kasittelija)))))


