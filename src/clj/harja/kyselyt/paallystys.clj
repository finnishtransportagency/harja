(ns harja.kyselyt.paallystys
  (:require [jeesql.core :refer [defqueries]]
            [harja.kyselyt.konversio :as konv]
            [harja.kyselyt.yllapitokohteet :as yllapitokohteet-q]
            [harja.geo :as geo]))

(defqueries "harja/kyselyt/paallystys.sql")

(defn onko-olemassa-paallystysilmoitus? [db yllapitokohde-id]
  (:exists (first (yllapitokohteella-paallystysilmoitus
                    db
                    {:yllapitokohde yllapitokohde-id}))))

(defn hae-urakan-paallystysilmoitukset-kohteineen [db urakka-id sopimus-id vuosi]
  (let [paallytysilmoitukset (into []
                                   (comp
                                     (map #(konv/string-poluista->keyword % [[:paatos-tekninen-osa]
                                                                             [:tila]])))
                                   (hae-urakan-paallystysilmoitukset db {:urakka urakka-id
                                                                         :sopimus sopimus-id
                                                                         :vuosi vuosi}))
        paallytysilmoitukset (yllapitokohteet-q/liita-kohdeosat-kohteisiin
                               db paallytysilmoitukset :paallystyskohde-id)]
    paallytysilmoitukset))
