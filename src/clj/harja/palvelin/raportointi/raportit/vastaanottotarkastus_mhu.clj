(ns harja.palvelin.raportointi.raportit.vastaanottotarkastus-mhu
  "MHU-urakoiden vastaanottotarkastusraportti.
  Koostuu Lupauksista, Ympäristöraportista, Talvisuolan kokonaiskäyttömäärästä, Tavoitehinnan muutoksista,
  Lisätöistä, Kirjallisista muistutuksista, sanktioista, arvonvähennyksistä ja poikkeamaraporteista, Tehtävämääristä,
  ja Laskutusyhteenvedosta."
  (:require [harja.kyselyt.urakat :as urakat-q]
            [harja.kyselyt.valikatselmus :as valikatselmus-q]
            [harja.domain.lupaus-domain :as lupaus-domain]
            [harja.palvelin.palvelut.lupaus.lupaus-palvelu :as lupaus-palvelu]
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

(defn suorita [db _ {:keys [urakka-id]}]
  (let [urakan-tiedot (first (urakat-q/hae-urakka db {:id urakka-id}))
        raportin-nimi (str "Vastaanottotarkastus - MHU " (:nimi urakan-tiedot))
        hoitokaudet (sort-by :alkupvm (urakat-q/hae-urakan-hoitokaudet db urakka-id))]
    [:raportti {:orientaatio :landscape
                :nimi raportin-nimi
                :urakan-nimi (:nimi urakan-tiedot)
                :otsikon-koko :iso
                :raportin-yleiset-tiedot raportin-nimi}
     (lupaukset-taulukko db urakka-id urakan-tiedot hoitokaudet)]))


