(ns harja.palvelin.raportointi.raportit.muutos-ja-lisatyoraportti
  "Muutos- ja lisätyöraportti"
  (:require [harja.palvelin.raportointi.raportit.yleinen :as yleinen
             :refer [raportin-otsikko rivi]]
            [harja.kyselyt.urakat :as urakat-q]
            [harja.kyselyt.hallintayksikot :as hallintayksikot-q]
            [harja.kyselyt.muutos-kyselyt :as muutos-kyselyt]
            [harja.kyselyt.rahavaraukset :as rahavaraus-kyselyt]
            [harja.pvm :as pvm]
            [harja.fmt :as fmt]
            [jeesql.core :refer [defqueries]]
            [taoensso.timbre :as log]))

(defqueries "harja/palvelin/raportointi/raportit/muutos_ja_lisatyoraportti.sql"
  {:positional? true})

(declare hae-aiempien-vuosien-pysyvat-muutokset-raportille hae-kirjallisesti-sovitut-muutokset-raportille)

(defn tyypin-nimi
  "Palauttaa muutostyypin selkokielisen nimen"
  [tyyppi]
  (case tyyppi
    "johto-ja-hallintokorvaus" "Johto- ja hallintokorvauksen muutos"
    "muutostyo" "Muutostyöt"
    "pysyva" "Pysyvä muutos"
    tyyppi))

(defn muutoksen-tavoitehinnan-muutos
  "Laskee yksittäisen muutoksen tavoitehinnan muutoksen. JJH-muutosten summa tulee kuluista,
   muiden kustannusvaikutuksista."
  [muutos]
  (if (= (:tyyppi muutos) "johto-ja-hallintokorvaus")
    (or (:jjh-muutosten-summa muutos) 0)
    (or (:kustannusvaikutusten-summa muutos) 0)))

(defn- laske-tavoitehinnan-muutos
  "Laskee tehtävän tavoitehinnan muutoksen. Soveltaa talvisuola-kerrointa tarvittaessa."
  [{:keys [tavoitehinnan_muutos talvisuola talvisuola_kerroin maara suunniteltu_maara] :as _rivi}]
  (let [tav-muutos (or tavoitehinnan_muutos 0)
        kayta-talvisuola-kerrointa? (and talvisuola
                                        (> (or maara 0) 0)
                                        (> (or suunniteltu_maara 0) (or maara 0)))]
    (if kayta-talvisuola-kerrointa?
      (* tav-muutos (or talvisuola_kerroin 0.7))            ;; Kovakoodattu luku. Mutta niin se on muutostenkin puolella. Olisi ehkä hyvä yhtenäistää.
      tav-muutos)))

(defn suorita [db user {:keys [urakka-id hallintayksikko-id alkupvm loppupvm] :as parametrit}]
  (log/info "Suoritetaan muutos- ja lisätyöraportti parametreilla: " (pr-str parametrit))
  (let [konteksti (cond urakka-id :urakka
                        hallintayksikko-id :hallintayksikko
                        :default :koko-maa)
        otsikko (raportin-otsikko
                  (case konteksti
                    :urakka (:nimi (first (urakat-q/hae-urakka db urakka-id)))
                    :hallintayksikko (:nimi (first (hallintayksikot-q/hae-organisaatio db hallintayksikko-id)))
                    :koko-maa "KOKO MAA")
                  "Muutos- ja lisätyöraportti" alkupvm loppupvm)

        ;; 1. Kirjallisesti sovitut muutokset
        muutokset (when urakka-id
                    (hae-kirjallisesti-sovitut-muutokset-raportille
                      db {:urakka-id urakka-id
                          :alkupvm alkupvm
                          :loppupvm loppupvm}))
        muutosrivit (mapv (fn [m]
                            (rivi (tyypin-nimi (:tyyppi m))
                                  (or (:syy m) "")
                                  (pvm/pvm (:voimassa_alkaen m))
                                  (muutoksen-tavoitehinnan-muutos m)))
                          muutokset)
        muutokset-yhteensa (reduce + 0 (map muutoksen-tavoitehinnan-muutos muutokset))
        muutokset-yhteensarivi [{:lihavoi? true
                                 :korosta-hennosti? true
                                 :rivi (rivi "Yhteensä" "" "" muutokset-yhteensa)}]

        ;; 2. Aikaisempien vuosien pysyvien muutosten vaikutukset
        aiemmat-pysyvat (when urakka-id
                          (hae-aiempien-vuosien-pysyvat-muutokset-raportille
                            db {:urakka-id urakka-id
                                :alkupvm alkupvm}))
        aiemmat-rivit (mapv (fn [m]
                              (rivi (or (:syy m) "Pysyvä muutos")
                                    (pvm/pvm (:voimassa_alkaen m))
                                    (or (:kustannusvaikutusten-summa m) 0)))
                            aiemmat-pysyvat)
        aiemmat-yhteensa (reduce + 0 (map #(or (:kustannusvaikutusten-summa %) 0) aiemmat-pysyvat))
        aiemmat-yhteensarivi [{:lihavoi? true
                               :korosta-hennosti? true
                               :rivi (rivi "Yhteensä" "" aiemmat-yhteensa)}]

        ;; 3. Tehtävä- ja määrätoteumiin perustuvat tavoitehintamuutokset
        hoitokauden-alkuvuosi (pvm/vuosi alkupvm)
        maaramuutokset (when urakka-id
                         (muutos-kyselyt/hae-tehtava-maaramuutokset
                           db {:urakka urakka-id
                               :alkupvm alkupvm
                               :loppupvm loppupvm
                               :hoitokauden_alkuvuosi hoitokauden-alkuvuosi
                               :tehtava nil
                               :tehtavaryhma nil
                               :laskenta-automatiikka? true}))
        maaramuutosrivit (mapv (fn [r]
                                 (rivi (or (:tehtava r) "")
                                       (or (:yksikko r) "")
                                       (or (:syy r) "")
                                       (:suunniteltu_maara r)
                                       (or (:maara r) 0)
                                       (or (:maaramuutos r) 0)
                                       (or (:kirjatut_kulut_summa r) 0)
                                       (laske-tavoitehinnan-muutos r)))
                               maaramuutokset)
        maaramuutokset-yhteensa (reduce + 0 (map laske-tavoitehinnan-muutos maaramuutokset))
        maaramuutokset-yhteensarivi [{:lihavoi? true
                                      :korosta-hennosti? true
                                      :rivi (rivi "Yhteensä" "" "" "" "" "" "" maaramuutokset-yhteensa)}]

        ;; 4. Rahavarausten muutokset
        rahavaraukset (when urakka-id
                        (rahavaraus-kyselyt/muutosten-rahavaraukset db urakka-id hoitokauden-alkuvuosi))
        ;; Viimeinen rivi on yhteenveto (:id :yhteenveto)
        rahavaraus-datarivit (filterv #(not= (:id %) :yhteenveto) rahavaraukset)
        rahavaraus-yhteenveto (first (filter #(= (:id %) :yhteenveto) rahavaraukset))
        rahavarausrivit (mapv (fn [r]
                                (rivi (or (:nimi r) "")
                                      (or (:syy r) "")
                                      (or (:summa-indeksikorjattu r) 0)
                                      (or (:toteumat r) 0)
                                      (or (:tavoitehinnan-muutos r) 0)))
                              rahavaraus-datarivit)
        rahavaraukset-yhteensarivi [{:lihavoi? true
                                     :korosta-hennosti? true
                                     :rivi (rivi "Yhteensä"
                                                 ""
                                                 (or (:summa-indeksikorjattu rahavaraus-yhteenveto) 0)
                                                 (or (:toteumat rahavaraus-yhteenveto) 0)
                                                 (or (:tavoitehinnan-muutos rahavaraus-yhteenveto) 0))}]]
    [:raportti {:nimi "Muutos- ja lisätyöraportti"
                :orientaatio :landscape}
     [:taulukko {:otsikko (str otsikko " — Kirjallisesti sovitut muutokset")
                 :viimeinen-rivi-yhteenveto? true
                 :sheet-nimi "Kirjallisesti sovitut"}
      [{:leveys 10 :otsikko "Tyyppi"}
       {:leveys 15 :otsikko "Muutoksen syy"}
       {:leveys 5 :otsikko "Voimassa alkaen"}
       {:leveys 5 :otsikko "Tavoitehinnan muutos (€)" :fmt :raha}]
      (into [] (concat muutosrivit muutokset-yhteensarivi))]

     [:taulukko {:otsikko "Aikaisempien vuosien pysyvien muutosten vaikutukset"
                 :viimeinen-rivi-yhteenveto? true
                 :sheet-nimi "Aiemmat pysyvät muutokset"}
      [{:leveys 15 :otsikko "Muutoksen syy"}
       {:leveys 5 :otsikko "Voimassa alkaen"}
       {:leveys 5 :otsikko "Tavoitehinnan muutos (€)" :fmt :raha}]
      (into [] (concat aiemmat-rivit aiemmat-yhteensarivi))]

     [:taulukko {:otsikko "Tehtävä- ja määrätoteumiin perustuvat tavoitehintamuutokset"
                 :viimeinen-rivi-yhteenveto? true
                 :sheet-nimi "Tehtävä- ja määrämuutokset"}
      [{:leveys 10 :otsikko "Tehtävä"}
       {:leveys 4 :otsikko "Yksikkö"}
       {:leveys 8 :otsikko "Muutoksen syy / lisätieto"}
       {:leveys 4 :otsikko "Suunniteltu määrä"}
       {:leveys 4 :otsikko "Toteutunut määrä"}
       {:leveys 4 :otsikko "Määrämuutos (+/-)"}
       {:leveys 5 :otsikko "Kohdistetut kulut (€)" :fmt :raha}
       {:leveys 5 :otsikko "Tavoitehinnan muutos (€)" :fmt :raha}]
      (into [] (concat maaramuutosrivit maaramuutokset-yhteensarivi))]

     [:taulukko {:otsikko "Rahavarausten muutokset"
                 :viimeinen-rivi-yhteenveto? true
                 :sheet-nimi "Rahavarausten muutokset"}
      [{:leveys 10 :otsikko "Rahavaraus"}
       {:leveys 10 :otsikko "Muutoksen syy"}
       {:leveys 5 :otsikko "Suunniteltu määrä (€)" :fmt :raha}
       {:leveys 5 :otsikko "Toteutunut määrä (€)" :fmt :raha}
       {:leveys 5 :otsikko "Tavoitehinnan muutos (€)" :fmt :raha}]
      (into [] (concat rahavarausrivit rahavaraukset-yhteensarivi))]]))
