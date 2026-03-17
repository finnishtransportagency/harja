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

(def ei-hoitovuosirajausta-teksti "Ei hoitovuosirajausta")

(defn- normalisoi-hoitovuosirajaus [{:keys [hoitokauden-alkuvuosi] :as tiedot}]
  (if hoitokauden-alkuvuosi
    tiedot
    (assoc tiedot :hoitovuoden-kuukausi nil)))

(defn- muodosta-raportin-aikatiedot [hoitokauden-alkuvuosi hoitovuoden-kuukausi]
  (if hoitokauden-alkuvuosi
    (let [hoitokauden-alkupvm (pvm/hoitokauden-alkupvm hoitokauden-alkuvuosi)
          hoitokauden-loppupvm (pvm/hoitokauden-loppupvm (inc hoitokauden-alkuvuosi))
          kuukausi-pvm (when hoitovuoden-kuukausi
                         (pvm/hoitokauden-alkuvuosi-kk->pvm hoitokauden-alkuvuosi hoitovuoden-kuukausi))]
      {:alkupvm (if hoitovuoden-kuukausi
                  (pvm/paiva-kuukausi kuukausi-pvm)
                  (pvm/pvm hoitokauden-alkupvm))
       :loppupvm (if hoitovuoden-kuukausi
                   (pvm/pvm (pvm/kuukauden-viimeinen-paiva kuukausi-pvm))
                   (pvm/pvm hoitokauden-loppupvm))})
    {:alkupvm nil
     :loppupvm nil}))

(defn- muodosta-tiedostonimi [hoitokauden-alkuvuosi hoitovuoden-kuukausi]
  (str "Varustetoimenpiteet "
    (cond
      hoitovuoden-kuukausi (pvm/urakan-kuukausi-str hoitovuoden-kuukausi hoitokauden-alkuvuosi)
      hoitokauden-alkuvuosi (pvm/hoitokausi-str-alkuvuodesta hoitokauden-alkuvuosi)
      :else ei-hoitovuosirajausta-teksti)))

(defn- muodosta-raportin-lisatiedot [hoitokauden-alkuvuosi]
  (when-not hoitokauden-alkuvuosi
    [["Hoitovuosi" ei-hoitovuosirajausta-teksti]]))

(defn- muodosta-raportin-yleiset-tiedot [urakka hoitokauden-alkuvuosi hoitovuoden-kuukausi]
  (let [{:keys [alkupvm loppupvm]} (muodosta-raportin-aikatiedot hoitokauden-alkuvuosi hoitovuoden-kuukausi)]
    (cond-> {:raportin-nimi (if hoitokauden-alkuvuosi
                              "Varustetoimenpiteet"
                              (str "Varustetoimenpiteet, " ei-hoitovuosirajausta-teksti))
             :urakka (:nimi urakka)
             :alkupvm alkupvm
             :loppupvm loppupvm}
      (nil? hoitokauden-alkuvuosi)
      (assoc :custom-ylin-rivi (str "Varustetoimenpiteet, " (:nimi urakka) ", " ei-hoitovuosirajausta-teksti)))))

(defn vie-ulkoiset-varusteet-exceliin
  [{:keys [db] :as velho-integraatio} workbook user
   tiedot]
  (let [{:keys [urakka-id hoitovuoden-kuukausi hoitokauden-alkuvuosi] :as tiedot}
        (normalisoi-hoitovuosirajaus tiedot)
        _ (oikeudet/vaadi-lukuoikeus oikeudet/urakat-toteumat-varusteet user urakka-id)
        urakka (first (urakat-q/hae-urakka db urakka-id))
        varusteet (:toteumat (velho/hae-urakan-varustetoteumat velho-integraatio tiedot))
        raportin-yleiset-tiedot (muodosta-raportin-yleiset-tiedot urakka hoitokauden-alkuvuosi hoitovuoden-kuukausi)
        tiedostonimi (muodosta-tiedostonimi hoitokauden-alkuvuosi hoitovuoden-kuukausi)
        raportin-lisatiedot (muodosta-raportin-lisatiedot hoitokauden-alkuvuosi)
        optiot {:nimi "Varustetoimenpiteet"
                :tyhja (when (empty? varusteet) "Ei varustetoimenpiteitä")}
        rivit (muodosta-excelrivit varusteet)
        taulukot [[:taulukko optiot sarakkeet rivit]]
        taulukko (vec (concat [:raportti
                               {:nimi tiedostonimi
                                :raportin-yleiset-tiedot raportin-yleiset-tiedot
                                :tietoja raportin-lisatiedot
                                :orientaatio :landscape}]
                        taulukot))]
    (excel/muodosta-excel taulukko workbook)))
