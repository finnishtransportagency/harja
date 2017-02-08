(ns harja.palvelin.palvelut.indeksit
  (:require [com.stuartsierra.component :as component]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelu]]
            [taoensso.timbre :as log]
            [clojure.set :refer [intersection difference]]
            [clojure.java.jdbc :as jdbc]
            [harja.kyselyt.indeksit :as q]
            [harja.pvm :as pvm]
            [harja.domain.oikeudet :as oikeudet]
            [harja.kyselyt.konversio :as konv]))

(defn hae-urakan-kuukauden-indeksiarvo
  "Palvelu, joka palauttaa tietyn kuukauden indeksin arvon ja nimen urakalle"
  [db urakka-id vuosi kuukausi]
  (first (q/hae-urakan-kuukauden-indeksiarvo db {:urakka_id urakka-id
                                                 :vuosi     vuosi
                                                 :kuukausi  kuukausi})))

(defn- ryhmittele-indeksit [indeksit]
  (seq (group-by (fn [rivi]
                   [(:nimi rivi) (:vuosi rivi)])
                 indeksit)))

(defn- zippaa [indeksit-vuosittain]
  (zipmap (map first indeksit-vuosittain)
          (map (fn [[_ kuukaudet]]
                 (assoc (zipmap (map :kuukausi kuukaudet) (map #(float (:arvo %)) kuukaudet))
                        :vuosi (:vuosi (first kuukaudet))))
               indeksit-vuosittain)))

(defn hae-indeksit
  "Palvelu, joka palauttaa indeksit."
  [db user]
  (oikeudet/vaadi-lukuoikeus oikeudet/hallinta-indeksit user)
  (zippaa (ryhmittele-indeksit (q/listaa-indeksit db))))

(defn hae-indeksi
  "Sisäinen funktio joka palauttaa indeksin nimellä"
  [db nimi]
  (zippaa (ryhmittele-indeksit (q/hae-indeksi db nimi))))

(defn tallenna-indeksi
  "Palvelu joka tallentaa nimellä tunnistetun indeksin tiedot"
  [db user {:keys [nimi indeksit]}]
  (assert (vector? indeksit) "indeksit tulee olla vektori")
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/hallinta-indeksit user)
  (let [nykyiset-arvot (hae-indeksi db nimi)]
    (jdbc/with-db-transaction [c db]
      (doseq [indeksivuosi indeksit]
        (let [nykyinen-indeksivuosi (dissoc (second (first (filter #(= (:vuosi indeksivuosi)
                                                                       (second (first %)))
                                                                   nykyiset-arvot))) :vuosi)
              vuosi (:vuosi indeksivuosi)
              indeksivuosi (dissoc indeksivuosi :vuosi :kannassa? :id)
              indeksivuosi-kkt (difference (into #{} (keys indeksivuosi))
                                           (into #{} (keep (fn [[kk val]]
                                                             (when (not (number? val)) kk)) indeksivuosi)))
              nykyinen-indeksivuosi-kkt (into #{} (keys nykyinen-indeksivuosi))
              paivitettavat (intersection indeksivuosi-kkt nykyinen-indeksivuosi-kkt)
              paivitettavat-eri-sisalto (into #{} (keep (fn [kk]
                                                          (when-not (= (float (get indeksivuosi kk)) (float (get nykyinen-indeksivuosi kk)))
                                                            kk)) paivitettavat))
              lisattavat (difference indeksivuosi-kkt nykyinen-indeksivuosi-kkt)
              poistettavat (difference nykyinen-indeksivuosi-kkt indeksivuosi-kkt)
              ]

          ;; 1) update 2) insert 3) delete operaatiot tilanteen mukaan
          (doseq [kk paivitettavat-eri-sisalto]
            (q/paivita-indeksi! c
                                (get indeksivuosi kk) nimi vuosi kk))
          (doseq [kk lisattavat]
            (q/luo-indeksi<! c
                             nimi vuosi kk (get indeksivuosi kk)))
          (doseq [kk poistettavat]
            (q/poista-indeksi! c
                               nimi vuosi kk))))

      (hae-indeksit c user))))

(defn hae-urakkatyypin-indeksit
  "Palvelu, joka palauttaa kaikki urakkatyypin-indeksit taulun rivit."
  [db user]
  (oikeudet/vaadi-lukuoikeus oikeudet/hallinta-indeksit user)
  (let [indeksit (into []
                       (map #(konv/string->keyword % :urakkatyyppi))
                       (q/hae-urakkatyypin-indeksit db))]
    (log/debug "hae urakkatyypin indeksit: " indeksit)
    indeksit))


(defn hae-paallystysurakan-indeksitiedot
  "Palvelu, joka palauttaa annetun päällystysurakan indeksitiedot"
  [db user {:keys [urakka-id]}]
  (log/debug "hae-paallystysurakan-indeksit" urakka-id)
  (oikeudet/vaadi-lukuoikeus oikeudet/urakat-yleiset user urakka-id)
  (let [indeksit (into []
                       (map konv/alaviiva->rakenne)
                       (q/hae-paallystysurakan-indeksitiedot db {:urakka urakka-id}))]
    (log/debug "hae-paallystysurakan-indeksit: " indeksit)
    indeksit))


(defn tallenna-paallystysurakan-indeksitiedot
  "Palvelu joka tallentaa päällystysurakan indeksitiedot eli mihin arvoihin mikäkin raaka-ainehinta on sidottu.
  Esim. Bitumin arvo sidotaan usein raskaan polttoöljyn Platts-indeksiin, nestekaasulle ja kevyelle polttoöljylle on omat hintansta."
  [db user {:keys [urakka-id] :as indeksitiedot}]
  (log/debug "tallenna-paallystysurakan-indeksiindeksitiedot" indeksitiedot)
  (assert (map? indeksitiedot) "indeksitiedot tulee olla vektori")
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-yleiset user urakka-id)
  (jdbc/with-db-transaction [c db]
    (doseq [i indeksitiedot]
      (log/debug "kirjaan indeksitiedon: " i)
      (q/upsert-paallystysurakan-indeksitiedot c i))
    (hae-paallystysurakan-indeksitiedot c user {:urakka urakka-id})))


(defrecord Indeksit []
  component/Lifecycle
  (start [this]
    (doto (:http-palvelin this)
      (julkaise-palvelu :indeksit
                        (fn [user]
                          (hae-indeksit (:db this) user)))
      (julkaise-palvelu :tallenna-indeksi
                        (fn [user tiedot]
                          (tallenna-indeksi (:db this) user tiedot)))
      (julkaise-palvelu :urakkatyypin-indeksit
                        (fn [user]
                          (hae-urakkatyypin-indeksit (:db this) user)))
      (julkaise-palvelu :paallystysurakan-indeksitiedot
                        (fn [user tiedot]
                          (hae-paallystysurakan-indeksitiedot (:db this) user tiedot)))
      (julkaise-palvelu :tallenna-paallystysurakan-indeksitiedot
                        (fn [user tiedot]
                          (tallenna-paallystysurakan-indeksitiedot (:db this) user tiedot)))
      )
    this)

  (stop [this]
    (poista-palvelu (:http-palvelin this) :indeksit)
    (poista-palvelu (:http-palvelin this) :tallenna-indeksi)
    (poista-palvelu (:http-palvelin this) :urakkatyypin-indeksit)
    (poista-palvelu (:http-palvelin this) :paallystysurakan-indeksitiedot)
    (poista-palvelu (:http-palvelin this) :tallenna-paallystysurakan-indeksitiedot)
    this))
