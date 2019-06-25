(ns harja.palvelin.integraatiot.api.sanomat.paikkaustoteumasanoma
  (:require [harja.domain.paikkaus :as paikkaus]
            [harja.palvelin.integraatiot.api.tyokalut.json :as json-tyokalut]
            [taoensso.timbre :as log]))

(defn kokonaishintainen-toteuma [urakka-id tunniste paikkauskohde kirjausaika {:keys [selite hinta]}]
  {::paikkaus/urakka-id urakka-id
   ::paikkaus/ulkoinen-id (:id tunniste)
   ::paikkaus/tyyppi "kokonaishintainen"
   ::paikkaus/kirjattu (json-tyokalut/aika-string->java-sql-date kirjausaika)
   ::paikkaus/paikkauskohde {::paikkaus/nimi (:nimi paikkauskohde)
                             ::paikkaus/ulkoinen-id (get-in paikkauskohde [:tunniste :id])}
     ::paikkaus/hinta (json-tyokalut/nil-turvallinen-bigdec hinta)
   ::paikkaus/selite selite})

(defn yksikkohintainen-toteuma [urakka-id tunniste paikkauskohde kirjausaika {:keys [selite
                                                                                      yksikko
                                                                                      yksikkohinta
                                                                                      maara]}]
  {::paikkaus/urakka-id urakka-id
   ::paikkaus/ulkoinen-id (:id tunniste)
   ::paikkaus/tyyppi "yksikkohintainen"
   ::paikkaus/kirjattu (json-tyokalut/aika-string->java-sql-date kirjausaika)
   ::paikkaus/paikkauskohde {::paikkaus/nimi (:nimi paikkauskohde)
                             ::paikkaus/ulkoinen-id (get-in paikkauskohde [:tunniste :id])}
   ::paikkaus/selite selite
   ::paikkaus/yksikko yksikko
   ::paikkaus/yksikkohinta  (json-tyokalut/nil-turvallinen-bigdec yksikkohinta)
   ::paikkaus/maara (json-tyokalut/nil-turvallinen-bigdec maara)
   })

(defn api->domain [urakka-id {:keys [tunniste
                                     paikkauskohde
                                     kirjausaika
                                     kokonaishintaiset-kustannukset
                                     yksikkohintaiset-kustannukset]}]
  (let [yksikkohintaiset (map #(yksikkohintainen-toteuma
                                 urakka-id
                                 tunniste
                                 paikkauskohde
                                 kirjausaika
                                 (:yksikkohintainen-kustannus %))
                              yksikkohintaiset-kustannukset)
        kokonaishintaiset (map #(kokonaishintainen-toteuma
                                  urakka-id
                                  tunniste
                                  paikkauskohde
                                  kirjausaika
                                  (:kokonaishintainen-kustannus %))
                               kokonaishintaiset-kustannukset)]
    (concat yksikkohintaiset kokonaishintaiset)))
