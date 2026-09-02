(ns harja.palvelin.palvelut.suunnittelu.suunnittelu-apurit
  "Apureita uuden kustannussuunnitelman ja tehtävät ja määrät -palveluille."
  (:require [harja.pvm :as pvm]
            [harja.kyselyt.urakat :as urakat-kyselyt]
            [harja.kyselyt.tarjous-kyselyt :as tarjous-kyselyt]
            [harja.kyselyt.uusi-kustannussuunnitelma-kyselyt :as ks-kyselyt]))


(defn jasenna-tallennettavat-vuodet
  "Jäsentää talletettavat hoitovuodet listaksi.
  Jos kopioi-tuleville-vuosille? on true, niin palauttaa kaikki hoitovuodet urakan alkamisvuodesta urakan loppumisvuoteen asti.
  Muuten palauttaa vain hoitovuoden-alkuvuoden."
  [db urakka-id hoitovuoden-alkuvuosi kopioi-tuleville-vuosille?]
  (if kopioi-tuleville-vuosille?
    (let [urakan-tiedot (first (urakat-kyselyt/hae-urakan-tiedot db urakka-id))
          urakan-alkuvuosi (pvm/vuosi (:alkupvm urakan-tiedot))
          urakan-loppuvuosi (pvm/vuosi (:loppupvm urakan-tiedot))
          ;; Varmista, että hoitovuoden-alkuvuosi on urakan sisällä
          hoitovuoden-alkuvuosi (cond
                                  (< hoitovuoden-alkuvuosi urakan-alkuvuosi) urakan-alkuvuosi
                                  (>= hoitovuoden-alkuvuosi urakan-loppuvuosi) urakan-loppuvuosi
                                  :else hoitovuoden-alkuvuosi)
          vuodet (range hoitovuoden-alkuvuosi urakan-loppuvuosi)]
      vuodet)
    [hoitovuoden-alkuvuosi]))


(defn luo-oletusrivit-puuttuviin-osioihin [tarjous]
  (let [tarjous-tiedot (:tarjous tarjous)
        nollatut-arvot (mapv (fn [osio]
                               (update osio :hoitovuosittaiset-arvot
                                 (fn [arvot]
                                   (mapv #(update % :summa (fn [a] (if (nil? a) 0.00M a))) arvot))))
                         tarjous-tiedot)]
    (assoc tarjous :tarjous nollatut-arvot)))


(defn kustannussuunnitelman-vahvistukset [db urakka-id]
  (let [urakan-tiedot (first (urakat-kyselyt/hae-urakan-tiedot db urakka-id))
        urakan-alkuvuosi (pvm/vuosi (:alkupvm urakan-tiedot))
        ;; Varmista, että yhtenäkään vuonna koko urakan keston ajalta kustannussuunnitelmaa ei ole vahvistettu. Jos on, niin tarjousta ei voi enää muokata
        vuodet (jasenna-tallennettavat-vuodet db urakka-id urakan-alkuvuosi true)
        vahvistukset (reduce (fn [lista vuosi]
                               (let [vahvistettu? (ks-kyselyt/kustannussuunnitelma-vahvistettu? db urakka-id vuosi)]
                                 (conj lista {:vuosi vuosi :vahvistettu? vahvistettu?})))
                       [] vuodet)]
    vahvistukset))


(defn koosta-tarjouksen-tiedot [db urakka-id]
  (let [urakan-parametrit (first (urakat-kyselyt/hae-urakan-parametrit db {:urakkaid urakka-id}))
        tarjous (luo-oletusrivit-puuttuviin-osioihin (tarjous-kyselyt/hae-tarjous db urakka-id))
        vahvistukset (kustannussuunnitelman-vahvistukset db urakka-id)]
    (-> tarjous
      (assoc :muokkaa-kattohinta-kasin (:muokkaa_kattohinta_kasin urakan-parametrit))
      (assoc :vahvistetut-vuodet (into #{}
                                   (flatten (map (juxt :vuosi) (filter #(true? (:vahvistettu? %)) vahvistukset))))))))
