(ns harja.palvelin.palvelut.raportit
  (:require [com.stuartsierra.component :as component]
            [clojure.string :as str]
            [taoensso.timbre :as log]

            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelut]]
            [harja.kyselyt.konversio :as konv]
            [harja.domain.roolit :as roolit]
            [harja.palvelin.palvelut.toteumat :as toteumat]
            [harja.kyselyt.laskutusyhteenveto :as laskutus-q]
            [harja.kyselyt.toteumat :as toteumat-q]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelut poista-palvelut]]
            [harja.palvelin.komponentit.pdf-vienti :as pdf-vienti]
            [harja.palvelin.raportointi :refer [hae-raportit suorita-raportti]]
            [clj-time.core :as t]
            [clj-time.coerce :as coerce])
  (:import (java.sql Timestamp)))


(defn hae-laskutusyhteenvedon-tiedot
  [db user {:keys [urakka-id hk-alkupvm hk-loppupvm aikavali-alkupvm aikavali-loppupvm] :as tiedot}]
  (log/debug "hae-urakan-laskutusyhteenvedon-tiedot" tiedot)
  (roolit/vaadi-lukuoikeus-urakkaan user urakka-id)
  (let [urakan-indeksi "MAKU 2010"]                         ;; indeksi jolla kok. ja yks. hint. työt korotetaan. Implementoidaan tässä tuki jos eri urakkatyyppi tarvii eri indeksiä
    (into []
          (comp
            (map #(konv/decimal->double %
                                        :kaikki_paitsi_kht_laskutettu_ind_korotus :kaikki_laskutettu_ind_korotus
                                        :kaikki_paitsi_kht_laskutetaan_ind_korotus :kaikki_laskutetaan_ind_korotus
                                        :kaikki_paitsi_kht_laskutettu :kaikki_laskutettu
                                        :kaikki_paitsi_kht_laskutetaan :kaikki_laskutetaan

                                        :kht_laskutettu :kht_laskutettu_ind_korotettuna :kht_laskutettu_ind_korotus
                                        :kht_laskutetaan :kht_laskutetaan_ind_korotettuna :kht_laskutetaan_ind_korotus
                                        :yht_laskutettu :yht_laskutettu_ind_korotettuna :yht_laskutettu_ind_korotus
                                        :yht_laskutetaan :yht_laskutetaan_ind_korotettuna :yht_laskutetaan_ind_korotus
                                        :sakot_laskutettu :sakot_laskutettu_ind_korotettuna :sakot_laskutettu_ind_korotus
                                        :sakot_laskutetaan :sakot_laskutetaan_ind_korotettuna :sakot_laskutetaan_ind_korotus
                                        :suolasakot_laskutettu :suolasakot_laskutettu_ind_korotettuna :suolasakot_laskutettu_ind_korotus
                                        :suolasakot_laskutetaan :suolasakot_laskutetaan_ind_korotettuna :suolasakot_laskutetaan_ind_korotus
                                        :muutostyot_laskutettu :muutostyot_laskutettu_ind_korotettuna :muutostyot_laskutettu_ind_korotus
                                        :muutostyot_laskutetaan :muutostyot_laskutetaan_ind_korotettuna :muutostyot_laskutetaan_ind_korotus
                                        :erilliskustannukset_laskutettu :erilliskustannukset_laskutettu_ind_korotettuna :erilliskustannukset_laskutettu_ind_korotus
                                        :erilliskustannukset_laskutetaan :erilliskustannukset_laskutetaan_ind_korotettuna :erilliskustannukset_laskutetaan_ind_korotus))
            )
          (laskutus-q/hae-laskutusyhteenvedon-tiedot db
                                                     (konv/sql-date hk-alkupvm)
                                                     (konv/sql-date hk-loppupvm)
                                                     (konv/sql-date aikavali-alkupvm)
                                                     (konv/sql-date aikavali-loppupvm)
                                                     urakka-id
                                                     urakan-indeksi))))

(defn yhdista-saman-paivan-samat-tehtavat
  "Ottaa joukon toteuma_tehtava-taulun rivejä ja yhdistää sellaiset rivit, joiden tehtävän toimenpidekoodi ja aloituspvm
   ovat samat. Aloituspvm voi olla joko java.util.Date tai java.sql.Timestamp."
  [tehtavat]
  (let [; Ryhmittele tehtävät tyypin ja pvm:n mukaan
        saman-paivan-samat-tehtavat-map (group-by (fn [tehtava]
                                                    (let [tpk-id (:toimenpidekoodi_id tehtava)
                                                          konvertoi-sql-timestamp? (instance? Timestamp (:alkanut tehtava))
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
  (roolit/vaadi-lukuoikeus-urakkaan user urakka-id)
  (let [toteutuneet-tehtavat (into []
                                   toteumat/muunna-desimaaliluvut-xf
                                   (toteumat-q/hae-urakan-toteutuneet-tehtavat-kuukausiraportille db
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
           :as         this}]

    (julkaise-palvelut http
                       :hae-raportit
                       (fn [user]
                         (reduce-kv (fn [raportit nimi raportti]
                                      (assoc raportit
                                        nimi (dissoc raportti :suorita)))
                                    {}
                                    (hae-raportit raportointi)))

                       :suorita-raportti
                       (fn [user raportti]
                         (suorita-raportti raportointi user raportti))

                       :yksikkohintaisten-toiden-kuukausiraportti
                       (fn [user tiedot]
                         (muodosta-yksikkohintaisten-toiden-kuukausiraportti db user tiedot))

                       :hae-laskutusyhteenvedon-tiedot
                       (fn [user tiedot]
                         (hae-laskutusyhteenvedon-tiedot db user tiedot)))
    this)

  (stop [{http :http-palvelin :as this}]
    (poista-palvelut http
                     :hae-raportit
                     :yksikkohintaisten-toiden-kuukausiraportti
                     :suorita-raportti
                     :hae-laskutusyhteenvedon-tiedot)
    this))
