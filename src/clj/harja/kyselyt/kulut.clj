(ns harja.kyselyt.kulut
  (:require [clojure.java.jdbc :as jdbc]
            [taoensso.timbre :as log]
            [jeesql.core :refer [defqueries]]
            [harja.domain.oikeudet :as oikeudet]
            [harja.kyselyt.konversio :as konv]
            [harja.kyselyt.rahavaraukset :as rahavaraus-kyselyt]
            [harja.kyselyt.kustannusarvioidut-tyot :as kustannusa-kyselyt]))

(defqueries "harja/kyselyt/kulut.sql"
  {:positional? false})

(declare luo-kulu<! hae-urakan-kulut hae-kulu hae-kulun-kohdistukset
  paivita-kulu<! linkita-kulu-ja-liite<! tarkista-kohdistuksen-yhteensopivuus
  hae-liitteet poista-kulun-ja-liitteen-linkitys! poista-kulu!
  poista-kulun-kohdistukset! hae-kulut-kohdistuksineen-tietoineen-vientiin
  hae-urakan-kulut-kohdistuksineen hae-urakan-toteutuneet-kustannukset
  hae-tehtavan-nimi hae-tehtavaryhman-nimi luo-kulun-kohdistus<!
  paivita-kulun-kohdistus<! hae-pvm-laskun-numerolla poista-kulun-kohdistus!
  hae-tehtavaryhman-tiedot-tunnisteella hae-urakan-hoidon-johdon-toimenpideinstanssi
  hae-toteutuneet-kustannukset-analytiikalle hae-urakan-laskutusraja hae-urakan-alkuperainen-laskutusraja
  paivita-urakan-laskutusraja! hae-kaikkien-tehtavaryhmien-nimet)

(defn hae-kulu-kohdistuksineen
  "Hakee yksittäisen kulun tiedot kohdistuksineen."
  [db user {:keys [urakka-id id]}]
  (oikeudet/vaadi-lukuoikeus oikeudet/urakat-kulut-laskunkirjoitus user urakka-id)
  (let [kulu (first (hae-kulu db {:urakka urakka-id
                                    :id id}))
        kohdistukset (hae-kulun-kohdistukset db {:kulu (:id kulu)
                                                   :urakka_id urakka-id})
        ;; Poista nil rahavaraukset
        kohdistukset (mapv (fn [kohdistus]
                             (let [kohdistus (if (:rahavaraus_id kohdistus)
                                               kohdistus
                                               (dissoc kohdistus :rahavaraus_id :rahavaraus_nimi))
                                   ;; Muutetaan tavoitehintainen keywordiksi
                                   kohdistus (-> kohdistus
                                               (update :tavoitehintainen #(keyword (str %)))
                                               (update :tehtava konv/jsonb->clojuremap))]
                               kohdistus))
                       kohdistukset)
        ;; Frontilla kulun muokkauksessa on olennaista, että kulun kohdistuksen rahavaraus sisältää kaikki mahdolliset tehtäväryhmät
        kohdistukset (mapv (fn [kohdistus]
                             (if (:rahavaraus_id kohdistus)
                               (let [tehtavaryhmat (rahavaraus-kyselyt/hae-rahavarauksen-tehtavaryhmat db {:rahavarausid (:rahavaraus_id kohdistus)
                                                                                                           :urakkaid urakka-id})]
                                 (assoc-in kohdistus [:rahavaraus :tehtavaryhmat] tehtavaryhmat))
                               kohdistus))
                       kohdistukset)
        kulun-kohdistukset (into []
                             (map konv/alaviiva->rakenne)
                             kohdistukset)
        liitteet (into [] (hae-liitteet db {:kulu-id id}))]
    (if-not (empty? kulu)
      (assoc kulu :kohdistukset kulun-kohdistukset :liitteet liitteet)
      kulu)))

(defn poista-kulu-tietokannasta
  "Merkitsee kulun sekä kaikki siihen liittyvät kohdistukset poistetuksi."
  [db user {:keys [urakka-id id]}]
  (log/debug "kulut :: poista-kulu-tietokannasta" id urakka-id)
  (jdbc/with-db-transaction [db db]
   (let [liitteet (into [] (hae-liitteet db {:kulu-id id}))
         poistettu-kulu (hae-kulu-kohdistuksineen db user {:id id})]
     (when (not (empty? liitteet))
       (doseq [{liite-id :liite-id} liitteet]
         (poista-kulun-ja-liitteen-linkitys! db {:kulu-id id :liite-id liite-id :kayttaja (:id user)})))
     (poista-kulu! db {:urakka urakka-id
                       :id id
                       :kayttaja (:id user)})
     (poista-kulun-kohdistukset! db {:urakka urakka-id
                                     :id id
                                     :kayttaja (:id user)})
     (kustannusa-kyselyt/merkitse-maksuerat-likaisiksi! db {:toimenpideinstanssi
                                                            (:toimenpideinstanssi poistettu-kulu)})
     poistettu-kulu)))
