(ns harja.palvelin.palvelut.raportit
  (:require [com.stuartsierra.component :as component]
            [clojure.string :as str]
            [taoensso.timbre :as log]

            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelut]]
            [harja.palvelin.komponentit.pdf-vienti :refer [rekisteroi-pdf-kasittelija! poista-pdf-kasittelija!]]
            [harja.kyselyt.konversio :as konv]
            [harja.domain.roolit :as roolit]
            [harja.palvelin.palvelut.toteumat :as toteumat]
            [harja.kyselyt.toteumat :as toteumat-q]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelut poista-palvelut]]
            [harja.palvelin.komponentit.pdf-vienti :as pdf-vienti]
            [harja.palvelin.raportointi :refer [hae-raportit suorita-raportti]]

            [harja.tyokalut.xsl-fo :as fo]

            [harja.kyselyt.urakat :as qu]
            [clj-time.core :as t]
            [clj-time.coerce :as coerce])
  (:import (java.sql Timestamp)))


(defn yhdista-saman-paivan-samat-tehtavat
  "Ottaa joukon toteuma_tehtava-taulun rivejä ja yhdistää sellaiset rivit, joiden tehtävän toimenpidekoodi ja aloituspvm
   ovat samat. Aloituspvm voi olla joko java.util.Date tai java.sql.Timestamp."
  [tehtavat]
  (let [; Ryhmittele tehtävät tyypin ja pvm:n mukaan
        saman-paivan-samat-tehtavat-map (group-by (fn [tehtava]
                                                    (let [tpk-id (:toimenpidekoodi_id tehtava)
                                                          alkanut (:alkanut tehtava)
                                                          pvm (.format (java.text.SimpleDateFormat. "dd.MM.yyyy") alkanut)]
                                                      [tpk-id pvm]))
                                                  tehtavat)
        ; Muuta map vectoriksi (jokainen item on vector jossa on päivän samat tehtävät, voi sisältää vain yhden)
        _ (log/debug "Saatiin aikaiseksi " (count (keys saman-paivan-samat-tehtavat-map)) " ryhmää. Ryhmät: " (keys saman-paivan-samat-tehtavat-map))
        saman-paivan-samat-tehtavat-vector (mapv
                                             #(get saman-paivan-samat-tehtavat-map %)
                                             (keys saman-paivan-samat-tehtavat-map))
        ; Käy vector läpi ja yhdistä saman päivän samat tehtävät
        yhdistetyt-tehtavat (mapv
                              (fn [tehtavat]
                                (if (> (count tehtavat) 1) ; Yhdistettäviä tehtäviä löytyi
                                  (let [yhdistettava (first tehtavat)] ; Kaikkia kentti ei ole mielekästä summata, summaa vain tarvittavat
                                    (-> yhdistettava
                                        (assoc :toteutunut_maara (reduce + (mapv :toteutunut_maara tehtavat)))
                                        (assoc :lisatieto (mapv :lisatieto tehtavat))))
                                  (first tehtavat)))
                              saman-paivan-samat-tehtavat-vector)]
    yhdistetyt-tehtavat))


(defn muodosta-yksikkohintaisten-toiden-kuukausiraportti [db user {:keys [urakka-id alkupvm loppupvm]}]
  (log/debug "Haetaan urakan toteutuneet tehtävät raporttia varten: " urakka-id alkupvm loppupvm)
  (roolit/vaadi-rooli user "tilaajan kayttaja")
  (let [toteutuneet-tehtavat (into []
                                   toteumat/muunna-desimaaliluvut-xf
                                   (toteumat-q/hae-urakan-toteutuneet-tehtavat-kuukausiraportille
                                     db
                                     urakka-id
                                     (konv/sql-timestamp alkupvm)
                                     (konv/sql-timestamp loppupvm)
                                     "yksikkohintainen"))
        toteutuneet-tehtavat-summattu (yhdista-saman-paivan-samat-tehtavat toteutuneet-tehtavat)]
    (log/debug "Haettu urakan toteutuneet tehtävät: " toteutuneet-tehtavat)
    (log/debug "Samana päivänä toteutuneet tehtävät summattu : " toteutuneet-tehtavat-summattu)
    toteutuneet-tehtavat-summattu))






(defrecord Raportit []
  component/Lifecycle
  (start [{raportointi :raportointi
           http        :http-palvelin
           db          :db
           pdf-vienti  :pdf-vienti
           :as         this}]

    (julkaise-palvelut http
                       :hae-raportit
                       (fn [user]
                         (reduce-kv (fn [acc nimi raportti]
                                      ;; Otetaan suoritus fn pois frontille lähetettävästä
                                      (assoc acc nimi (dissoc raportti :suorita)))
                                    {}
                                    (hae-raportit raportointi)))

                       :suorita-raportti
                       (fn [user raportti]
                         (suorita-raportti raportointi user raportti))

                       :yksikkohintaisten-toiden-kuukausiraportti
                       (fn [user tiedot]
                         (muodosta-yksikkohintaisten-toiden-kuukausiraportti db user tiedot)))

    this)

  (stop [{http :http-palvelin pdf-vienti :pdf-vienti :as this}]
    (poista-palvelut http
                     :hae-raportit
                     :yksikkohintaisten-toiden-kuukausiraportti
                     :suorita-raportti)
    this))
