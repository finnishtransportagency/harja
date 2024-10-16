(ns harja.kyselyt.budjettisuunnittelu
  (:require [harja.domain.mhu :as mhu]
            [harja.pvm :as pvm]
            [jeesql.core :refer [defqueries]]))

(defqueries "harja/kyselyt/budjettisuunnittelu.sql"
  {:positional? false})

(declare lisaa-suunnitelmalle-tila)

(defn redusoi-suunnitelutilat
  [tilat tila]
  (let [{:keys [hoitovuosi osio vahvistettu]} tila
        osio (keyword osio)]
    (assoc-in tilat [osio hoitovuosi] vahvistettu)))

(defn budjettitavoite-vuodelle [db urakka-id hoitokauden-alkuvuosi]
  (->>
    (hae-budjettitavoite db {:urakka urakka-id})
    (filter #(= hoitokauden-alkuvuosi (:hoitokauden-alkuvuosi %)))
    first))

(defn paivita-kustannusuunnitelman-tila [db kayttaja-id urakka-id urakan-alkupvm kasiteltavat-vuodet osio-tyyppi]
  (doseq [vuosi kasiteltavat-vuodet
          :let [osion-tila (osio-tyyppi
                             (reduce redusoi-suunnitelutilat
                               {} (hae-suunnitelman-tilat db {:urakka urakka-id})))
                hoitokauden-nro (pvm/paivamaara->mhu-hoitovuosi-nro
                                  urakan-alkupvm
                                  (pvm/->pvm (str "01.11." vuosi)))
                vuoden-tila (when osion-tila
                              (get osion-tila hoitokauden-nro))
                _ (when-not vuoden-tila
                    ;; Lisätään vain rivi tauluun, ei vahvisteta mitään tiloja
                    (lisaa-suunnitelmalle-tila db {:urakka urakka-id
                                                     :hoitovuosi hoitokauden-nro
                                                     :osio (mhu/osio-kw->osio-str osio-tyyppi)
                                                     :luoja kayttaja-id
                                                     :vahvistaja nil
                                                     :vahvistettu false
                                                     :vahvistus_pvm nil}))]]))
