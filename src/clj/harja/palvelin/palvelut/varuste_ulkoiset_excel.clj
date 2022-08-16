(ns harja.palvelin.palvelut.varuste-ulkoiset-excel
  (:require [harja.domain.oikeudet :as oikeudet]
            [harja.palvelin.raportointi.excel :as excel]
            [harja.kyselyt.toteumat :as toteumat-q]
            [harja.kyselyt.konversio :as konv]
            [harja.kyselyt.urakat :as urakat-q]
            [harja.pvm :as pvm]
            [harja.domain.tierekisteri :as tierekisteri]
            [harja.domain.varuste-ulkoiset :as v-yhteiset]))

(def sarakkeet
  [{:otsikko "Ajankohta" :fmt :pvm}
   {:otsikko "Tierekisteriosoite"}
   {:otsikko "Toimenpide"}
   {:otsikko "Varustetyyppi"}
   {:otsikko "Varusteen lisätieto"}
   {:otsikko "Kuntoluokitus"}
   {:otsikko "Tekijä"}])

(defn- muodosta-excelrivit [varustetoimenpiteet]
  (map (fn [{:keys [alkupvm toteuma tietolaji lisatieto kuntoluokka muokkaaja] :as vtp}]
         {:rivi
          [alkupvm (tierekisteri/tierekisteriosoite-tekstina vtp)
           (v-yhteiset/toteuma->toimenpide toteuma)
           (v-yhteiset/tietolaji->varustetyyppi tietolaji)
           lisatieto
           kuntoluokka
           muokkaaja]}) varustetoimenpiteet))

(defn vie-ulkoiset-varusteet-exceliin
  [db workbook user {:keys [urakka-id hoitovuosi tietolajit kuntoluokat kuukausi] :as tiedot}]
  (println tiedot)
  (oikeudet/vaadi-lukuoikeus oikeudet/urakat-toteumat-varusteet user urakka-id)
  (let [urakka (first (urakat-q/hae-urakka db urakka-id))
        hoitokauden-alkupvm (pvm/hoitokauden-alkupvm hoitovuosi)
        hoitokauden-loppupvm (pvm/hoitokauden-loppupvm (inc hoitovuosi))
        tiedot (assoc tiedot
                 :urakka urakka-id
                 :hoitokauden_alkupvm (konv/sql-date hoitokauden-alkupvm)
                 :hoitokauden_loppupvm (konv/sql-date hoitokauden-loppupvm)
                 :tietolajit (or tietolajit [])
                 :kuntoluokat (or kuntoluokat []))
        varustetoimenpiteet (toteumat-q/hae-uusimmat-varustetoteuma-ulkoiset db tiedot)
        kuukausi-pvm (when kuukausi (pvm/hoitokauden-alkuvuosi-kk->pvm hoitovuosi kuukausi))
        tiedostonimi (str "Varustetoimenpiteet "
                       (if kuukausi
                         (pvm/urakan-kuukausi-str kuukausi hoitovuosi)
                         (pvm/hoitokausi-str-alkuvuodesta hoitovuosi)))
        optiot {:nimi "Varustetoimenpiteet"
                :tyhja (when (empty? varustetoimenpiteet) "Ei varustetoimenpiteitä")}
        rivit (muodosta-excelrivit varustetoimenpiteet)
        taulukot [[:taulukko optiot sarakkeet rivit]]
        taulukko (vec (concat [:raportti
                               {:nimi tiedostonimi
                                :raportin-yleiset-tiedot {:raportin-nimi "Varustetoimenpiteet"
                                                          :urakka (:nimi urakka)
                                                          :alkupvm (if kuukausi (pvm/paiva-kuukausi kuukausi-pvm) (pvm/pvm hoitokauden-alkupvm))
                                                          :loppupvm (if kuukausi (pvm/pvm (pvm/kuukauden-viimeinen-paiva kuukausi-pvm)) (pvm/pvm hoitokauden-loppupvm))
                                                          }
                                :orientaatio :landscape}]
                        taulukot))]
    (excel/muodosta-excel taulukko workbook)))
