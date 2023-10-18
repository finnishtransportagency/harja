(ns harja.palvelin.palvelut.varuste-ulkoiset-excel
  (:require [harja.domain.oikeudet :as oikeudet]
            [harja.palvelin.raportointi.excel :as excel]
            [harja.kyselyt.urakat :as urakat-q]
            [harja.pvm :as pvm]
            [harja.domain.tierekisteri :as tierekisteri]
            [harja.palvelin.integraatiot.velho.varusteet :as velho]))

(def sarakkeet
  [{:otsikko "Ajankohta" :fmt :pvm}
   {:otsikko "Tierekisteriosoite"}
   {:otsikko "Toimenpide"}
   {:otsikko "Varustetyyppi"}
   {:otsikko "Varusteen lisätieto"}
   {:otsikko "Kuntoluokitus"}
   {:otsikko "Tekijä"}])

(defn- muodosta-excelrivit [varustetoimenpiteet]
  (map (fn [{:keys [alkupvm lisatieto kuntoluokka muokkaaja toimenpide tyyppi] :as vtp}]
         {:rivi
          [alkupvm (tierekisteri/tierekisteriosoite-tekstina vtp)
           toimenpide
           tyyppi
           lisatieto
           kuntoluokka
           muokkaaja]}) varustetoimenpiteet))

(defn vie-ulkoiset-varusteet-exceliin
  [{:keys [db] :as velho-integraatio} workbook user
   {:keys [urakka-id hoitovuoden-kuukausi hoitokauden-alkuvuosi] :as tiedot}]
  (oikeudet/vaadi-lukuoikeus oikeudet/urakat-toteumat-varusteet user urakka-id)
  (let [urakka (first (urakat-q/hae-urakka db urakka-id))
        varusteet (:toteumat (velho/hae-urakan-varustetoteumat velho-integraatio tiedot))
        hoitokauden-alkupvm (pvm/hoitokauden-alkupvm hoitokauden-alkuvuosi)
        hoitokauden-loppupvm (pvm/hoitokauden-loppupvm (inc hoitokauden-alkuvuosi))
        kuukausi-pvm (when hoitovuoden-kuukausi (pvm/hoitokauden-alkuvuosi-kk->pvm hoitokauden-alkuvuosi hoitovuoden-kuukausi))
        tiedostonimi (str "Varustetoimenpiteet "
                       (if hoitovuoden-kuukausi
                         (pvm/urakan-kuukausi-str hoitovuoden-kuukausi hoitokauden-alkuvuosi)
                         (pvm/hoitokausi-str-alkuvuodesta hoitokauden-alkuvuosi)))
        optiot {:nimi "Varustetoimenpiteet"
                :tyhja (when (empty? varusteet) "Ei varustetoimenpiteitä")}
        rivit (muodosta-excelrivit varusteet)
        taulukot [[:taulukko optiot sarakkeet rivit]]
        taulukko (vec (concat [:raportti
                               {:nimi tiedostonimi
                                :raportin-yleiset-tiedot {:raportin-nimi "Varustetoimenpiteet"
                                                          :urakka (:nimi urakka)
                                                          :alkupvm (if hoitovuoden-kuukausi (pvm/paiva-kuukausi kuukausi-pvm) (pvm/pvm hoitokauden-alkupvm))
                                                          :loppupvm (if hoitovuoden-kuukausi (pvm/pvm (pvm/kuukauden-viimeinen-paiva kuukausi-pvm)) (pvm/pvm hoitokauden-loppupvm))}
                                :orientaatio :landscape}]
                        taulukot))]
    (excel/muodosta-excel taulukko workbook)))
