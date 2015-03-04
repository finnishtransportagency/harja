(ns harja.palvelin.palvelut.indeksit
  (:require [com.stuartsierra.component :as component]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelu]]
            [clojure.tools.logging :as log]
            [clojure.set :refer [intersection difference]]
            [clojure.string :as str]
            [clojure.java.jdbc :as jdbc]
            
            [harja.kyselyt.indeksit :as q]))

(declare hae-indeksit hae-indeksi tallenna-indeksi)
                        
(defrecord Indeksit []
  component/Lifecycle
  (start [this]
   (doto (:http-palvelin this)
     (julkaise-palvelu
       :indeksit (fn [user]
         (hae-indeksit (:db this) user)))
     (julkaise-palvelu :tallenna-indeksi
       (fn [user tiedot]
         (tallenna-indeksi (:db this) user tiedot))))
   this)

  (stop [this]
    (poista-palvelu (:http-palvelin this) :indeksit)
    (poista-palvelu (:http-palvelin this) :tallenna-indeksi)
    this))


(defn hae-indeksit
  "Palvelu, joka palauttaa indeksit."
  [db user]
        (let 
          [indeksit-vuosittain  (seq (group-by
                            (fn [rivi]
                              [(:nimi rivi) (:vuosi rivi)]
                              ) (q/listaa-indeksit db)))]
          
          (zipmap (map first indeksit-vuosittain)
                  (map (fn [[_ kuukaudet]]
                         (assoc (zipmap (map :kuukausi kuukaudet) (map #(float (:arvo %)) kuukaudet))
                                :vuosi (:vuosi (first kuukaudet))))
                       indeksit-vuosittain))))

(defn hae-indeksi
  "Sisäinen funktio joka palauttaa indeksin nimellä"
  [db nimi]
        (let 
          [indeksit-vuosittain  (seq (group-by
                            (fn [rivi]
                              [(:nimi rivi) (:vuosi rivi)]
                              ) (q/hae-indeksi db nimi)))]
          
          (zipmap (map first indeksit-vuosittain)
                  (map (fn [[_ kuukaudet]]
                         (assoc (zipmap (map :kuukausi kuukaudet) (map #(float (:arvo %)) kuukaudet))
                                :vuosi (:vuosi (first kuukaudet))))
                       indeksit-vuosittain))))

(defn tallenna-indeksi [db user {:keys [nimi indeksit]}]
  "Palvelu joka tallentaa nimellä tunnistetun indeksin tiedot"
  (assert (vector? indeksit) "indeksit tulee olla vektori")
  (let [nykyiset-arvot (hae-indeksi db nimi)]
    (jdbc/with-db-transaction [c db]
                              (doseq [indeksivuosi indeksit]
                                (let [nykyinen-indeksivuosi (dissoc (second (first (filter #(= (:vuosi indeksivuosi) 
                                                                                               (second (first %))) 
                                                                                           nykyiset-arvot))) :vuosi)
                                      vuosi (:vuosi indeksivuosi)
                                      indeksivuosi (dissoc indeksivuosi :vuosi :kannassa?)
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
                                                       nimi vuosi kk))
                                  (hae-indeksit c user))))
