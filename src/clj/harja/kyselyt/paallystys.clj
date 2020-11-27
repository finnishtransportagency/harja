(ns harja.kyselyt.paallystys
  (:require [jeesql.core :refer [defqueries]]
            [harja.domain.paallystysilmoitus :as pot]
            [harja.kyselyt.konversio :as konv]
            [harja.kyselyt.yllapitokohteet :as yllapitokohteet-q]
            [harja.geo :as geo]))

(defqueries "harja/kyselyt/paallystys.sql")

(defn onko-olemassa-paallystysilmoitus? [db yllapitokohde-id]
  (:exists (first (yllapitokohteella-paallystysilmoitus
                    db
                    {:yllapitokohde yllapitokohde-id}))))

(defn hae-urakan-paallystysilmoitukset-kohteineen [db urakka-id sopimus-id vuosi]
  (let [hae-paallystysilmoitukset-sql (if (>= vuosi pot/pot2-vuodesta-eteenpain)
                                        hae-urakan-pot2-paallystysilmoitukset
                                        hae-urakan-paallystysilmoitukset)
        paallytysilmoitukset (into []
                                   (comp
                                     (map #(konv/string-poluista->keyword % [[:paatos-tekninen-osa]
                                                                             [:tila]])))

                                   (hae-paallystysilmoitukset-sql db {:urakka urakka-id
                                                                              :sopimus sopimus-id
                                                                              :vuosi vuosi}))
        paallytysilmoitukset (yllapitokohteet-q/liita-kohdeosat-kohteisiin
                               db paallytysilmoitukset :paallystyskohde-id)]
    paallytysilmoitukset))
