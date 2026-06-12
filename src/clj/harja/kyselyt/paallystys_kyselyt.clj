(ns harja.kyselyt.paallystys-kyselyt
  (:require [jeesql.core :refer [defqueries]]
            [harja.kyselyt.konversio :as konv]
            [harja.kyselyt.yllapitokohteet :as yllapitokohteet-q]))

(defqueries "harja/kyselyt/paallystys.sql")

(declare yllapitokohteella-paallystysilmoitus hae-urakan-paallystysilmoitukset hae-paikkauskohde-yllapitokohde-idlla
  hae-hoidon-paallystyksen-kulut-analytiikalle hae-paallystyskohteet-analytiikalle
  hae-paallystyksen-alikohteet-analytiikalle hae-paallystyskohteiden-aikataulut-analytiikalle
  hae-paallystysilmoitukset-analytiikalle hae-paallystysilmoitusten-kulutuskerroksen-toimenpiteet-analytiikalle
  hae-paallystysilmoitusten-alustan-toimenpiteet-analytiikalle hae-yllapitokohteen-maaramuutokset
  hae-yllapitokohteiden-maaramuutokset luo-yllapitokohteen-maaramuutos<!
  poista-yllapitokohteen-jarjestelman-kirjaamat-maaramuutokset!)

(defn onko-olemassa-paallystysilmoitus? [db yllapitokohde-id]
  (:exists (first (yllapitokohteella-paallystysilmoitus
                    db
                    {:yllapitokohde yllapitokohde-id}))))

(defn hae-urakan-paallystysilmoitukset-kohteineen [db {:keys [urakka-id sopimus-id vuosi paikkauskohteet? tilat evkt]}]
  (let [ilmoitukset (hae-urakan-paallystysilmoitukset db {:urakka urakka-id
                                                          :sopimus sopimus-id
                                                          :vuosi vuosi
                                                          :paikkauskohteet paikkauskohteet?})

        paallytysilmoitukset (into []
                               (mapv #(konv/string-poluista->keyword % [[:paatos-tekninen-osa]
                                                                        [:tila]])

                                 ilmoitukset))
        filtter-fnt [(when (and (seq tilat)
                             (not (contains? tilat "Kaikki")))
                       (filter #(or
                                  (and (contains? tilat :aloittamatta)
                                    (nil? (:tila %)))
                                  (contains? tilat (:tila %)))))
                     (when (and (seq evkt)
                             (not (contains? evkt 0)))
                       (filter #(or (empty? evkt) (contains? evkt (:evk %)))))]
        filtteri-xform (apply comp
                         (vec
                           (keep identity
                             filtter-fnt)))
        paallytysilmoitukset (into [] filtteri-xform paallytysilmoitukset)

        paallytysilmoitukset (map #(update % :yha-tr-osoite konv/lue-tr-osoite) paallytysilmoitukset)
        paallytysilmoitukset (yllapitokohteet-q/liita-kohdeosat-kohteisiin
                               db paallytysilmoitukset :paallystyskohde-id)]
    paallytysilmoitukset))

(defn yllapitokohde-paikkauskohde? [db yllapitokohde-id]
  (let [paikkauskohde (hae-paikkauskohde-yllapitokohde-idlla db {:yllapitokohde-id yllapitokohde-id})]
    (boolean (seq paikkauskohde))))
