(ns harja.palvelin.raportointi.raportit.vastaanottotarkastus-mhu
  "MHU-urakoiden vastaanottotarkastusraportti.
  Koostuu Lupauksista, Ympäristöraportista, Talvisuolan kokonaiskäyttömäärästä, Tavoitehinnan muutoksista,
  Lisätöistä, Kirjallisista muistutuksista, sanktioista, arvonvähennyksistä ja poikkeamaraporteista, Tehtävämääristä,
  ja Laskutusyhteenvedosta."
  (:require [jeesql.core :refer [defqueries]]
            [harja.kyselyt.materiaalit :as materiaalit-kyselyt]
            [harja.kyselyt.rahavaraukset :as rahavaraus-kyselyt]
            [harja.kyselyt.urakat :as urakat-q]
            [harja.kyselyt.valikatselmus :as valikatselmus-q]
            [harja.domain.lupaus-domain :as lupaus-domain]
            [harja.domain.laadunseuranta.sanktio :as sanktio-domain]
            [harja.palvelin.palvelut.lupaus.lupaus-palvelu :as lupaus-palvelu]
            [harja.palvelin.palvelut.valikatselmus.valikatselmukset :as valikatselmus-palvelu]
            [harja.palvelin.palvelut.muutos.muutos-palvelu :as muutos-palvelu]
            [harja.palvelin.raportointi.raportit.yleinen :as yleinen]
            [harja.palvelin.raportointi.raportit.talvihoitosuolan-kokonaiskayttomaara :as talvisuola]
            [harja.palvelin.raportointi.raportit.muutos-ja-lisatyoraportti :as muutos-ja-lisatyoraportti]
            [harja.pvm :as pvm]))

(defqueries "harja/palvelin/raportointi/raportit/vastaanottotarkastus_mhu.sql"
  {:positional? true})

(declare hae-viranomaistehtavamaarat)


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
                       (lupaus-palvelu/hae-urakan-lupaustiedot-hoitokaudelle db lupaus-parametrit))
        valikatselmus-tehty? (get-in lupaustiedot [:yhteenveto :valikatselmus-tehty-urakalle?])]
    [(str hoitovuosi "-" (pvm/vuosi loppupvm))
     (get-in lupaustiedot [:lupaus-sitoutuminen :pisteet])
     ;; Ei näytetä toteutuneita pisteitä, vaikka ne olisi tiedossa, ennenkuin päätös on tehty ja välikatselmus on lopullinen
     (when valikatselmus-tehty? (get-in lupaustiedot [:yhteenveto :pisteet :toteuma]))
     (hae-bonus-sanktiot db urakka-id hoitokausi hoitovuosi)]))

(defn lupaukset-taulukko [db urakka-id urakan-tiedot hoitokaudet]
  (let [vanha-urakka? (lupaus-domain/urakka-19-20? urakan-tiedot)]
    [:taulukko {:otsikko "Lupaukset"
                :tyhja (when (empty? hoitokaudet) "Ei hoitovuosia.")
                :sheet-nimi "Lupaukset"
                :samalle-sheetille? false}
     [{:otsikko "Hoitovuosi" :leveys 5}
      {:otsikko "Tarjouksen lupauspisteet" :leveys 5}
      {:otsikko "Toteutuneet lupauspisteet" :leveys 5}
      {:otsikko "Bonus/Sanktiot (€)" :leveys 5 :fmt :raha}]
     (mapv #(lupausrivi db urakka-id vanha-urakka? %) hoitokaudet)]))

(defn- talvisuolan-erittely
  "Talvisuolan kokonaiskäyttömäärä osion sisään tulee yhteenveto ja hoitovuosikotainen erittely."
  [db urakka-id kasittelija]
  (let [;; Yritetään hyödyntää olemassa oleva raportti täysimääräisesti
        talvisuolan-raportin-osiot (yleinen/osat (talvisuola/suorita db nil {:urakka-id urakka-id
                                                                             :kasittelija kasittelija} false))
        infolaatikko (some #(when (= :infolaatikko (first %)) %) talvisuolan-raportin-osiot)
        yhteenveto (some #(when (= "Koko urakka-ajan yhteenveto (kuivatonneina)"
                                  (get-in % [1 :otsikko])) %)
                     talvisuolan-raportin-osiot)
        erittely (some #(when (= "Erittely hoitovuosittain" (get-in % [1 :otsikko])) %)
                   talvisuolan-raportin-osiot)
        taulukko (cond-> [infolaatikko]
                   true (conj yhteenveto)
                   erittely (conj erittely))]
    taulukko))

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

(defn muodosta-virhanomaistehtavat-taulukko
  "Tehtävän nimi on muuttunut aikojen saatosa. -22 vuoteen asti kerättiin dataa toiseen ja lennosta vaihdettiin toiseen.
  Piirretään siis tarvittaessa kaksi taulukkoa."
  [db urakka-id hoitokaudet kasittelija]
  (let [viranomais-rivit (mapv (fn [hoitokausi]
                                 (let [{:keys [alkupvm loppupvm]} hoitokausi
                                       vuosi (pvm/vuosi alkupvm)
                                       viranomaistehtavat (hae-viranomaistehtavamaarat db {:urakka-id urakka-id
                                                                                           :hoitovuosi vuosi
                                                                                           :alkupvm alkupvm
                                                                                           :loppupvm loppupvm
                                                                                           :nimi "Viranomaistehtävissä avustaminen"})
                                       yhteensa (reduce + 0 (map #(or (:tuntia %) 0) viranomaistehtavat))]
                                   [(str vuosi "-" (pvm/vuosi loppupvm))
                                    yhteensa]))
                           hoitokaudet)
        osallistuminen-rivit (mapv (fn [hoitokausi]
                                     (let [{:keys [alkupvm loppupvm]} hoitokausi
                                           vuosi (pvm/vuosi alkupvm)
                                           osallistuminen (hae-viranomaistehtavamaarat db {:urakka-id urakka-id
                                                                                           :hoitovuosi vuosi
                                                                                           :alkupvm alkupvm
                                                                                           :loppupvm loppupvm
                                                                                           :nimi "Osallistuminen tilaajalle kuuluvien viranomaistehtävien hoitoon"})
                                           yhteensa (reduce + 0 (map #(or (:tuntia %) 0) osallistuminen))]
                                       [(str vuosi "-" (pvm/vuosi loppupvm))
                                        yhteensa]))
                               hoitokaudet)
        viranomaistehtavat-yhteensa (reduce + 0 (map #(or (second %) 0) viranomais-rivit))
        viranomaistehtavat-yhteensarivi [{:lihavoi? true
                                          :korosta-hennosti? true
                                          :rivi ["Yhteensä" viranomaistehtavat-yhteensa]}]
        osallistuminen-yhteensa (reduce + 0 (map #(or (second %) 0) osallistuminen-rivit))
        osallistuminen-yhteensarivi [{:lihavoi? true
                                      :korosta-hennosti? true
                                      :rivi ["Yhteensä" osallistuminen-yhteensa]}]]
    (vec
      (concat
        ;; Näytetään jos viranomaistehtäviä on kirjattu tai jos kumpaakaan ei ole kirjattu
        (when (or (> viranomaistehtavat-yhteensa 0) (and (= viranomaistehtavat-yhteensa 0) (= osallistuminen-yhteensa 0)))
          [[:taulukko {:otsikko "Viranomaistehtävät"
                       :leveysprosentti 50
                       :viimeinen-rivi-yhteenveto? true
                       :sheet-nimi "Viranomaistehtävät"
                       :excel-alkutekstit (when (= kasittelija :excel) [[:otsikko-title "Viranomaistehtävissä avustaminen"]])}
            [{:leveys 5 :otsikko "Hoitovuosi"}
             {:leveys 5 :otsikko "Viranomaistehtävissä avustaminen (h)" :fmt :kokonaisluku}]
            (into [] (concat viranomais-rivit (when-not (empty? viranomais-rivit) viranomaistehtavat-yhteensarivi)))]])
        (when (> osallistuminen-yhteensa 0)
          [[:taulukko {:otsikko "Viranomaistehtävät"
                       :leveysprosentti 50
                       :viimeinen-rivi-yhteenveto? true
                       :sheet-nimi "Viranomaistehtävät"
                       :excel-alkutekstit (when (= kasittelija :excel) [[:otsikko-title "Osallistuminen tilaajalle kuuluvien viranomaistehtävien hoitoon"]])}
            [{:leveys 5 :otsikko "Hoitovuosi"}
             {:leveys 5 :otsikko "Osallistuminen tilaajalle kuuluvien viranomaistehtävien hoitoon (h)" :fmt :kokonaisluku}]
            (into [] (concat osallistuminen-rivit (when-not (empty? osallistuminen-rivit) osallistuminen-yhteensarivi)))]])))))

(defn muodosta-tavoitehintaan-kuuluvat-kustannukset-taulukko
  "Taulukossa ei ole erikseen kohtaa arvonvähennyksille tai muille kuluille, kuten Välikatselmuksessa. Tässä ne lisätään hankintakustannuksiin, kuten rahanvarauksetkin."
  [db urakan-tiedot hoitokaudet kasittelija]
  (let [urakka-id (:id urakan-tiedot)
        rivit (mapv (fn [hoitokausi]
                      (let [{:keys [alkupvm loppupvm]} hoitokausi
                            vuosi (pvm/vuosi alkupvm)
                            kustannukset (:taulukon-rivit (valikatselmus-palvelu/hae-kustannukset-jarjestettyna db urakka-id vuosi alkupvm loppupvm))
                            hankintakustannukset (+ (or (:hankintakustannukset-toteutunut kustannukset) 0)
                                                   (or (:rahavaraukset-toteutunut kustannukset) 0)
                                                   (or (:arvonvahennykset-toteutunut kustannukset) 0)
                                                   (or (:muukulu-tavoitehintainen-toteutunut kustannukset) 0))
                            erilliskustannukset (or (:erillishankinnat-toteutunut kustannukset) 0)
                            jjh-korvaukset (or (:johto-ja-hallintokorvaus-toteutunut kustannukset) 0)
                            hoidonjohtopalkkiot (or (:hoidonjohdonpalkkio-toteutunut kustannukset) 0)
                            yhteensa (+ hankintakustannukset erilliskustannukset jjh-korvaukset hoidonjohtopalkkiot)]
                        [(str vuosi "-" (pvm/vuosi loppupvm)) hankintakustannukset erilliskustannukset jjh-korvaukset hoidonjohtopalkkiot yhteensa]))
                hoitokaudet)
        hankintakustannukset-yhteensa (reduce + 0 (map #(or (second %) 0) rivit))
        erilliskustannukset-yhteensa (reduce + 0 (map #(or (nth % 2) 0) rivit))
        jjh-korvaukset-yhteensa (reduce + 0 (map #(or (nth % 3) 0) rivit))
        hoidonjohtopalkkiot-yhteensa (reduce + 0 (map #(or (nth % 4) 0) rivit))
        kaikki-yhteensa (reduce + 0 (map #(or (last %) 0) rivit))
        kustannukset-yhteensarivi [{:lihavoi? true
                                    :korosta-hennosti? true
                                    :rivi ["Yhteensä"
                                           hankintakustannukset-yhteensa
                                           erilliskustannukset-yhteensa
                                           jjh-korvaukset-yhteensa
                                           hoidonjohtopalkkiot-yhteensa
                                           kaikki-yhteensa]}]
        otsikko-title [:otsikko-title "Urakan tavoitehintaan kuuluvat kustannukset"]]
    [[:taulukko {:otsikko "Urakan tavoitehintaan kuuluvat kustannukset"
                 :viimeinen-rivi-yhteenveto? true
                 :sheet-nimi "Urakan tavoitehintaan kuuluvat kustannukset"
                 :excel-alkutekstit (when (= kasittelija :excel) [otsikko-title])}
      [{:leveys 5 :otsikko "Hoitovuosi"}
       {:leveys 5 :otsikko "Hankintakustannukset sis.rahavaraukset (€)" :fmt :raha}
       {:leveys 5 :otsikko "Erillishankinnat (€)" :fmt :raha}
       {:leveys 5 :otsikko "Johto- ja hallintokorvaus (€)" :fmt :raha}
       {:leveys 5 :otsikko "Hoidonjohtopalkkio (€)" :fmt :raha}
       {:leveys 5 :otsikko "Yhteensä (€)" :fmt :raha}]
      (into [] (concat rivit (when-not (empty? rivit) kustannukset-yhteensarivi)))]]))

(defn muodosta-urakan-tavoitehinat-taulukko [db user urakan-tiedot urakan-parametrit hoitokaudet kasittelija]
  (let [urakka-id (:id urakan-tiedot)
        ota-paatos (fn [paatokset avain] (first (vals (first (filter #(= (ffirst %) avain) paatokset)))))
        rivit (mapv (fn [hoitokausi]
                      (let [{:keys [alkupvm loppupvm]} hoitokausi
                            vuosi (pvm/vuosi alkupvm)
                            hoitovuoden-tiedot (valikatselmus-palvelu/hae-valikatselmuksen-tiedot-hoitovuodelle
                                                 db user {:urakkaid urakka-id :hoitovuosi vuosi})
                            budjettitavoite-vuodelle (get-in hoitovuoden-tiedot [:yhteenveto :budjettitavoite])
                            toteuma-yht (or (get-in budjettitavoite-vuodelle [:yhteenveto :kustannukset-yhteensa :yht-toteutunut-summa]) 0)
                            kirjallisesti-sovitut-muutokset (when (:muutosten_hallinta urakan-parametrit)
                                                              (get-in hoitovuoden-tiedot [:yhteenveto :budjettitavoite :kirjallisesti-sovitut-muutokset]))
                            toteumiin-perustuvat-muutokset-yht (when (:muutosten_hallinta urakan-parametrit)
                                                                 (get-in hoitovuoden-tiedot [:yhteenveto :toteumiin-perustuvat-muutokset-yht]))
                            thv-arvonvahennykset-yht (apply + (map #(or (:maara %) 0) (get-in hoitovuoden-tiedot [:yhteenveto :tavoitehintaan-vaikuttavat-arvonvahennykset])))
                            pysyvat-muutokset-toteuma-muutokset-yht (+ (or kirjallisesti-sovitut-muutokset 0) (or toteumiin-perustuvat-muutokset-yht 0))
                            ;; Hoitovuoden lopun tavoitehintaan vaikuttavat myös mahdolliset kirjallisesti sovitut muutokset ja toteumiin perustuvat muutokset
                            ;; Sekä arvonvähennykset
                            hoitovuoden-lopun-tavoitehinta (or (:hoitovuoden-lopun-tavoitehinta budjettitavoite-vuodelle) 0)
                            ;; Hoitovuoden lopun indeksikorjaus -päätös vaikuttaa myös hoitovuoden lopun tavoitehintaan.
                            hv-lopun-indkorjaus-paatos (ota-paatos (:paatokset hoitovuoden-tiedot) :hoitovuoden-lopun-indeksikorjaus)
                            hoitokauden_lopun_indeksikorjaus (or (:hoitokauden_lopun_indeksikorjaus hv-lopun-indkorjaus-paatos) 0)
                            hoitovuoden-lopun-tavoitehinta (+ hoitovuoden-lopun-tavoitehinta
                                                             ;; Jos päätös on tehty, niin indeksikorjaus on jo luvuissa mukana
                                                             (if (:id hv-lopun-indkorjaus-paatos) 0 hoitokauden_lopun_indeksikorjaus)
                                                             pysyvat-muutokset-toteuma-muutokset-yht
                                                             thv-arvonvahennykset-yht)

                            hoitovuoden-lopun-kattohinta (or (get-in hoitovuoden-tiedot [:yhteenveto :budjettitavoite :hoitovuoden-lopun-kattohinta]) 0)
                            ;; Hoitovuoden lopun tavoitehintaan vaikuttavat myös mahdolliset kirjallisesti sovitut muutokset ja toteumiin perustuvat muutokset
                            ;; Sekä arvonvähennykset
                            hoitovuoden-lopun-kattohinta (+ hoitovuoden-lopun-kattohinta
                                                           (* (if (:id hv-lopun-indkorjaus-paatos) 0 hoitokauden_lopun_indeksikorjaus) (:hoitokauden_lopun_kattohinta_kerroin urakan-parametrit))
                                                           (* pysyvat-muutokset-toteuma-muutokset-yht (:hoitokauden_lopun_kattohinta_kerroin urakan-parametrit))
                                                           (* thv-arvonvahennykset-yht (:hoitokauden_lopun_kattohinta_kerroin urakan-parametrit)))

                            tavoitehinnan-ylityspaatos (ota-paatos (:paatokset hoitovuoden-tiedot) :tavoitehinnan-ylitys)
                            tavoitehinnan-alituspaatos (ota-paatos (:paatokset hoitovuoden-tiedot) :tavoitehinnan-alitus)
                            kattohinnan-ylityspaatos (ota-paatos (:paatokset hoitovuoden-tiedot) :kattohinnan-ylitys)
                            tavoitepalkkio (or (:tavoitepalkkio tavoitehinnan-alituspaatos) 0)
                            hyvitys-tavoitehinnan-ylityksesta (or (:urakoitsija_maksaa tavoitehinnan-ylityspaatos) 0)
                            kattohinnan-ylitys (if (and (not (:id kattohinnan-ylityspaatos)) (> toteuma-yht hoitovuoden-lopun-kattohinta))
                                                 (- toteuma-yht hoitovuoden-lopun-kattohinta)
                                                 (or (:ylityksen_maara kattohinnan-ylityspaatos) 0))
                            hyvitys-kattohinnan-ylityksesta (or (:urakoitsija_maksaa kattohinnan-ylityspaatos) 0)

                            yhteensa (+ hoitovuoden-lopun-tavoitehinta hoitovuoden-lopun-kattohinta tavoitepalkkio hyvitys-tavoitehinnan-ylityksesta kattohinnan-ylitys hyvitys-kattohinnan-ylityksesta)]
                        [(str vuosi "-" (pvm/vuosi loppupvm)) hoitovuoden-lopun-tavoitehinta hoitovuoden-lopun-kattohinta tavoitepalkkio hyvitys-tavoitehinnan-ylityksesta kattohinnan-ylitys hyvitys-kattohinnan-ylityksesta yhteensa]))
                hoitokaudet)
        lopun-tavoitehinta-yhteensa (reduce + 0 (map #(or (second %) 0) rivit))
        lopun-kattohinta-yhteensa (reduce + 0 (map #(or (nth % 2) 0) rivit))
        tavoitepalkkio-yhteensa (reduce + 0 (map #(or (nth % 3) 0) rivit))
        hyvitys-tavoitehinnan-ylityksesta-yhteensa (reduce + 0 (map #(or (nth % 4) 0) rivit))
        kattohinnan-ylitys-yhteensa (reduce + 0 (map #(or (nth % 5) 0) rivit))
        hyvitys-kattohinnan-ylityksesta-yhteensa (reduce + 0 (map #(or (nth % 6) 0) rivit))
        kaikki-yhteensa (reduce + 0 (map #(or (last %) 0) rivit))
        hinnat-yhteensarivi [{:lihavoi? true
                              :korosta-hennosti? true
                              :rivi ["Yhteensä"
                                     lopun-tavoitehinta-yhteensa
                                     lopun-kattohinta-yhteensa
                                     tavoitepalkkio-yhteensa
                                     hyvitys-tavoitehinnan-ylityksesta-yhteensa
                                     kattohinnan-ylitys-yhteensa
                                     hyvitys-kattohinnan-ylityksesta-yhteensa
                                     kaikki-yhteensa]}]
        otsikko-title [:otsikko-title "Urakan lopullinen tavoite- ja kattohinta"]]
    [[:taulukko {:otsikko "Urakan lopullinen tavoite- ja kattohinta"
                 :viimeinen-rivi-yhteenveto? true
                 :sheet-nimi "Urakan lopullinen tavoite- ja kattohinta"
                 :excel-alkutekstit (when (= kasittelija :excel) [otsikko-title])}
      [{:leveys 5 :otsikko "Hoitovuosi"}
       {:leveys 5 :otsikko "Hoitovuoden lopun tavoitehinta (€)" :fmt :raha}
       {:leveys 5 :otsikko "Hoitovuoden lopun kattohinta (€)" :fmt :raha}
       {:leveys 5 :otsikko "Urakoitsijan tavoitepalkkio (€)" :fmt :raha}
       {:leveys 5 :otsikko "Urakoitsija hyvittää tavoitehinnan ylityksestä (€)" :fmt :raha}
       {:leveys 5 :otsikko "Kattohinnan ylitys (€)" :fmt :raha}
       {:leveys 5 :otsikko "Urakoitsija hyvittää kattohinnan ylityksestä (€)" :fmt :raha}]
      (into [] (concat rivit (when-not (empty? rivit) hinnat-yhteensarivi)))]]))

(defn suorita [db user {:keys [urakka-id kasittelija]}]
  (let [urakan-tiedot (first (urakat-q/hae-urakka db {:id urakka-id}))
        urakan-parametrit (first (urakat-q/hae-urakan-parametrit db urakka-id))
        raportin-nimi (str "Vastaanottotarkastus - MHU " (:nimi urakan-tiedot))
        hoitokaudet (sort-by :alkupvm (urakat-q/hae-urakan-hoitokaudet db urakka-id))
        talvisuolan-erittely (talvisuolan-erittely db urakka-id kasittelija)]
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

        (muodosta-lisatyo-taulukko db urakka-id hoitokaudet kasittelija)

        (muodosta-virhanomaistehtavat-taulukko db urakka-id hoitokaudet kasittelija)

        (muodosta-tavoitehintaan-kuuluvat-kustannukset-taulukko db urakan-tiedot hoitokaudet kasittelija)

        (muodosta-urakan-tavoitehinat-taulukko db user urakan-tiedot urakan-parametrit hoitokaudet kasittelija)))))


