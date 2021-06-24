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

(defn hae-urakan-paallystysilmoitukset-kohteineen [db urakka-id sopimus-id vuosi paikkauskohteet?]
  (let [_ (println "hae-urakan-paallystysilmoitukset-kohteineen params" urakka-id sopimus-id vuosi paikkauskohteet?)
        ilmoitukset (hae-urakan-paallystysilmoitukset db {:urakka urakka-id
                                                          :sopimus sopimus-id
                                                          :vuosi vuosi
                                                          :paikkauskohteet paikkauskohteet?})
        ilmoitukset (if (and false paikkauskohteet?)
                      (concat [{:tila nil,
                                :lahetys-onnistunut false,
                                :kohdenumero 1, ; pk.ulkoinen-id
                                :paatos-tekninen-osa nil,
                                :yha-tr-osoite nil,
                                :tunnus nil,
                                :sopimuksen-mukaiset-tyot nil,
                                :nimi "paikkauskohde", ; pk.nimi
                                :kaasuindeksi nil,
                                :paallystyskohde-id (rand-int 12345),
                                :bitumi-indeksi nil,
                                :lahetetty nil,
                                :id nil,
                                :takuupvm nil, ;pk.takuupvm
                                :versio nil,
                                :tr-numero 837
                                :tr-alkuosa 12
                                ;; Vaikea sanoa, että mitä nämä kohdeosat on tässä vaiheessa
                                :kohdeosat [{:tr-numero 837 ;pk.tie
                                             :tr-alkuosa 12}], ;pk.aosa
                                :arvonvahennykset nil,
                                :muokattu nil,
                                :yhaid nil}]
                            ilmoitukset)
                      ilmoitukset)
        _ (println "hae-urakan-paallystysilmoitukset-kohteineen :: ilmoitukset1 " ilmoitukset)
        paallytysilmoitukset (into []
                                   (mapv #(konv/string-poluista->keyword % [[:paatos-tekninen-osa]
                                                                           [:tila]])

                                        ilmoitukset))
        paallytysilmoitukset (map #(update % :yha-tr-osoite konv/lue-tr-osoite) paallytysilmoitukset)
        paallytysilmoitukset (yllapitokohteet-q/liita-kohdeosat-kohteisiin
                               db paallytysilmoitukset :paallystyskohde-id)
        _ (println "hae-urakan-paallystysilmoitukset-kohteineen :: paallytysilmoitukset2 " paallytysilmoitukset)]
    paallytysilmoitukset))
