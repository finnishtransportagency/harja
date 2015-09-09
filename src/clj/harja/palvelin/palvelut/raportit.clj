(ns harja.palvelin.palvelut.raportit
  (:require [com.stuartsierra.component :as component]
            [clojure.string :as str]
            [taoensso.timbre :as log]

            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelut]]
            [harja.kyselyt.konversio :as konv]
            [harja.domain.roolit :as roolit]
            [harja.kyselyt.laskutusyhteenveto :as laskutus-q]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelut poista-palvelut]]
            [harja.palvelin.raportointi :refer [hae-raportit suorita-raportti]]))


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

                       :hae-laskutusyhteenvedon-tiedot
                       (fn [user tiedot]
                         (hae-laskutusyhteenvedon-tiedot db user tiedot)))
    this)

  (stop [{http :http-palvelin :as this}]
    (poista-palvelut http
                     :hae-raportit
                     :suorita-raportti
                     :hae-laskutusyhteenvedon-tiedot)
    this))
