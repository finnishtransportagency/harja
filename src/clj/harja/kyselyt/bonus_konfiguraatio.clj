(ns harja.kyselyt.bonus-konfiguraatio
  (:require [harja.kyselyt.konversio :as konv]
            [harja.tyokalut.muunnos :refer [keywordiksi]]
            [jeesql.core :refer [defqueries]]))

(declare hae-bonus-profiilit-admin hae-urakan-bonus-profiilit hae-bonus-profiili-admin
  hae-bonus-profiilin-rivit hae-bonus-profiilin-rivit-admin)

(defn- muunna-urakkatyyppi
  [rivi avainpolku]
  (if (get-in rivi avainpolku)
    (update-in rivi avainpolku keyword)
    rivi))

(defn- normalisoi-vektoriksi
  [arvo]
  (cond
    (nil? arvo) []
    (vector? arvo) arvo
    (instance? java.sql.Array arvo) (vec (.getArray ^java.sql.Array arvo))
    :else [arvo]))

(defn muunna-bonus-profiili-admin-listarivi
  [{:as rivi}]
  (-> rivi
    konv/alaviiva->rakenne
    (muunna-urakkatyyppi [:urakkatyyppi])))

(defn- muunna-profiili
  [{:keys [profiili_id profiili_nimi profiili_urakkatyyppi
           profiili_hoitovuosi_alku profiili_hoitovuosi_loppu
           profiili_alkupvm profiili_loppupvm profiili_aktiivinen]}]
  {:id profiili_id
   :nimi profiili_nimi
   :urakkatyyppi (keywordiksi profiili_urakkatyyppi)
   :hoitovuosi {:alku profiili_hoitovuosi_alku
                :loppu profiili_hoitovuosi_loppu}
   :alkupvm profiili_alkupvm
   :loppupvm profiili_loppupvm
   :aktiivinen profiili_aktiivinen})

(defn- muunna-laji
  [{:keys [laji_id laji_koodi laji_nimi laji_esitystiedot_nimi laji_esitystiedot_kuvaus
           laji_jarjestys laji_kirjaustapa laji_automaattinen]}]
  {:id laji_id
   :koodi (keywordiksi laji_koodi)
   :nimi laji_nimi
   :esitystiedot {:nimi laji_esitystiedot_nimi
                  :kuvaus laji_esitystiedot_kuvaus}
   :jarjestys laji_jarjestys
   :kirjaustapa laji_kirjaustapa
   :automaattinen laji_automaattinen})

(defn- muunna-profiilirivi
  "Muodostaa littean profiilirivin SQL:n palauttamista sarakkeista.
  Admin-kysely palauttaa lisaksi urakkarajausten maaran ja urakat, ei-admin ei."
  [{:keys [profiilirivi_id profiilirivi_jarjestys
           profiilirivi_toimenpiderajauksen_tyyppi profiilirivi_toimenpide_t2_koodi]
    urakkarajausten-maara :profiilirivi_urakkarajausten_maara
    urakat :profiilirivi_urakat
    :as rivi}]
  (cond-> {:id profiilirivi_id
           :jarjestys profiilirivi_jarjestys
           :toimenpiderajauksen-tyyppi (keywordiksi profiilirivi_toimenpiderajauksen_tyyppi)
           :toimenpide-t2-koodi profiilirivi_toimenpide_t2_koodi}
    (contains? rivi :profiilirivi_urakkarajausten_maara)
    (assoc :urakkarajausten-maara urakkarajausten-maara)

    (contains? rivi :profiilirivi_urakat)
    (assoc :urakat (normalisoi-vektoriksi urakat))))

(defn muunna-bonus-konfiguraatiorivi
  [{:as rivi}]
  {:profiili (muunna-profiili rivi)
   :laji (muunna-laji rivi)
   :profiilirivi (muunna-profiilirivi rivi)})

(defqueries "harja/kyselyt/bonus_konfiguraatio.sql")
