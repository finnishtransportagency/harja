(ns harja.palvelin.integraatiot.yha.yllapitokohteet
  (:require [taoensso.timbre :as log]
            [harja.palvelin.palvelut.yllapitokohteet.maaramuutokset :as maaramuutokset]
            [harja.kyselyt.konversio :as konv]
            [harja.kyselyt.paallystys :as q-paallystys]
            [harja.kyselyt.yha :as q-yha-tiedot]
            [harja.kyselyt.yllapitokohteet :as q-yllapitokohteet])
  (:use [slingshot.slingshot :only [throw+ try+]]))

(defn hae-kohteen-paallystysilmoitus [db kohde-id]
  (let [ilmoitus (first (q-paallystys/hae-paallystysilmoitus-kohdetietoineen-paallystyskohteella db {:paallystyskohde kohde-id}))]
    (konv/jsonb->clojuremap ilmoitus :ilmoitustiedot)))

(defn hae-alikohteet [db geometriat? kohde-id paallystysilmoitus]
  (let [alikohteet (q-yha-tiedot/hae-yllapitokohteen-kohdeosat db {:yllapitokohde kohde-id})
        osoitteet (get-in paallystysilmoitus [:ilmoitustiedot :osoitteet])]
    (mapv (fn [alikohde]
            (let [id (:id alikohde)
                  ilmoitustiedot (first (filter #(= id (:kohdeosa-id %)) osoitteet))
                  alikohde (if geometriat?
                             (assoc alikohde :geometria (q-yllapitokohteet/hae-yllapitokohdeosan-geometria db {:id id}))
                             alikohde)]
              (apply merge ilmoitustiedot alikohde)))
          alikohteet)))

(defn hae-kohteen-tiedot
  ([db kohde-id] (hae-kohteen-tiedot db kohde-id false))
  ([db kohde-id geometriat?]
   (if-let [kohde (first (q-yllapitokohteet/hae-yllapitokohde db {:id kohde-id}))]
     (let [maaramuutokset (:tulos (maaramuutokset/hae-ja-summaa-maaramuutokset
                                    db {:urakka-id (:urakka kohde) :yllapitokohde-id kohde-id}))
           paallystysilmoitus (hae-kohteen-paallystysilmoitus db kohde-id)
           paallystysilmoitus (assoc paallystysilmoitus :maaramuutokset maaramuutokset)
           alikohteet (hae-alikohteet db geometriat? kohde-id paallystysilmoitus)]
       {:kohde kohde
        :alikohteet alikohteet
        :paallystysilmoitus paallystysilmoitus})
     (let [virhe (format "Tuntematon kohde (id: %s)." kohde-id)]
       (log/error virhe)
       (throw+
         {:type ::yha-virhe-kohteen-lahetyksessa
          :virheet {:virhe virhe}})))))
