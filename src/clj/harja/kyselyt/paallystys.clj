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

(defn hae-urakan-paallystysilmoitukset-kohteineen [db {:keys [urakka-id sopimus-id vuosi paikkauskohteet? tilat elyt]}]
  (let [_ (println "hae-urakan-paallystysilmoitukset-kohteineen params" urakka-id sopimus-id vuosi paikkauskohteet?)

        _ (println "TILAT " tilat)
        _ (println "ELYT " elyt)
        ilmoitukset (hae-urakan-paallystysilmoitukset db {:urakka urakka-id
                                                          :sopimus sopimus-id
                                                          :vuosi vuosi
                                                          :paikkauskohteet paikkauskohteet?})
        
        _ (println ilmoitukset)
        paallytysilmoitukset (into []
                                   (mapv #(konv/string-poluista->keyword % [[:paatos-tekninen-osa]
                                                                           [:tila]])

                                        ilmoitukset))
        _ (println paallytysilmoitukset)
        filtter-fnt [(when (and (seq tilat)
                                (not (contains? tilat :kaikki)))
                       (filter #(or 
                                 (and (contains? tilat :aloittamatta)
                                      (nil? (:tila %)))
                                 (contains? tilat (:tila %)))))
                     (when (and (seq elyt)
                                (not (contains? elyt 0))) 
                       (filter #(do
                                  (println "-> " (:ely %))
                                  (or (empty? elyt)
                                      (contains? elyt (:ely %))))))]
        _ (println "filtterit" filtter-fnt)
        filtteri-xform (apply comp 
                              (vec
                               (keep identity 
                                     filtter-fnt)))
        paallytysilmoitukset (into [] filtteri-xform paallytysilmoitukset)
        
        paallytysilmoitukset (map #(update % :yha-tr-osoite konv/lue-tr-osoite) paallytysilmoitukset)
        paallytysilmoitukset (yllapitokohteet-q/liita-kohdeosat-kohteisiin
                               db paallytysilmoitukset :paallystyskohde-id)
        _ (println "hae-urakan-paallystysilmoitukset-kohteineen :: paallytysilmoitukset2 " paallytysilmoitukset)]
    paallytysilmoitukset))
