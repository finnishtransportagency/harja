(ns harja.palvelin.palvelut.urakat
  (:require [com.stuartsierra.component :as component]
            [harja.domain.roolit :as roolit]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelu]]
            [harja.kyselyt.urakat :as q]
            [harja.kyselyt.konversio :as konv]
            [harja.geo :refer [muunna-pg-tulokset]]
            [clojure.string :as str]
            [harja.pvm :as pvm]
            [taoensso.timbre :as log]))

(def urakka-xf
  (comp (muunna-pg-tulokset :alue :alueurakan_alue)
        
        ;; Jos alueurakan alue on olemassa, käytetään sitä alueena
        (map #(if-let [alueurakka (:alueurakan_alue %)]
                (-> %
                    (dissoc :alueurakan_alue)
                    (assoc  :alue alueurakka))
                (dissoc % :alueurakan_alue)))
        
        (map #(assoc % :urakoitsija {:id (:urakoitsija_id %)
                                     :nimi (:urakoitsija_nimi %)
                                     :ytunnus (:urakoitsija_ytunnus %)}))

        (map #(assoc % :loppupvm (pvm/aikana (:loppupvm %) 23 59 59 999))) ; Automaattikonversiolla aika on 00:00
        
        ;; :sopimukset kannasta muodossa ["2=8H05228/01" "3=8H05228/10"] ja 
        ;; tarjotaan ulos muodossa {:sopimukset {"2" "8H05228/01", "3" "8H05228/10"}
        (map #(update-in % [:sopimukset] (fn [jdbc-array]
                                           (if (nil? jdbc-array)
                                             {}
                                             (into {} (map (fn [s](let [[id sampoid] (str/split s #"=")]
                                                                    [(Long/parseLong id) sampoid]
                                                                    )) (.getArray jdbc-array)))))))
        (map #(assoc % :hallintayksikko {:id (:hallintayksikko_id %)
                                         :nimi (:hallintayksikko_nimi %)
                                         :lyhenne (:hallintayksikko_lyhenne %)}))
        (map #(assoc %
                :tyyppi (keyword (:tyyppi %))
                :sopimustyyppi (and (:sopimustyyppi %) (keyword (:sopimustyyppi %)))))
        
        (map #(dissoc % :urakoitsija_id :urakoitsija_nimi :urakoitsija_ytunnus
                      :hallintayksikko_id :hallintayksikko_nimi :hallintayksikko_lyhenne))))

(defn hallintayksikon-urakat [db user hallintayksikko-id]
  ;; PENDING: Mistä tiedetään kuka saa katso vai saako perustiedot nähdä kuka vaan (julkista tietoa)?
  (log/debug "Haetaan hallintayksikön urakat: " hallintayksikko-id)
  (into []
        urakka-xf
        (q/listaa-urakat-hallintayksikolle db hallintayksikko-id
                                           (name (get-in user [:organisaatio :tyyppi]))
                                           (get-in user [:organisaatio :id]))))

(defn hae-urakoita [db user teksti]
  (log/debug "Haetaan urakoita tekstihaulla: " teksti)
  (into []
        urakka-xf
        (q/hae-urakoita db (str "%" teksti "%"))))

(defn hae-organisaation-urakat [db user organisaatio-id]
  (log/debug "Haetaan urakat organisaatiolle: " organisaatio-id)
  []
  (into []
        urakka-xf
        (q/hae-organisaation-urakat db organisaatio-id)))

(defn hae-urakan-organisaatio [db user urakka-id]
  (log/debug "Haetaan organisaatio urakalle: " urakka-id)
  (let [organisaatio (first (into []
        (q/hae-urakan-organisaatio db urakka-id)))]
    (log/debug "Urakan organisaatio saatu: " (pr-str organisaatio))
    organisaatio))

(defn hae-urakan-sopimustyyppi [db user urakka-id]
  (keyword (:sopimustyyppi (first (q/hae-urakan-sopimustyyppi db urakka-id)))))

(defn hae-urakan-tyyppi [db user urakka-id]
  (keyword (:tyyppi (first (q/hae-urakan-tyyppi db urakka-id)))))

(defn tallenna-urakan-sopimustyyppi [db user {:keys  [urakka-id sopimustyyppi]}]
  (roolit/vaadi-rooli-urakassa user roolit/urakanvalvoja urakka-id)
  (q/tallenna-urakan-sopimustyyppi! db (name sopimustyyppi) urakka-id)
  (hae-urakan-sopimustyyppi db user urakka-id))

(defn tallenna-urakan-tyyppi [db user {:keys  [urakka-id urakkatyyppi]}]
  (roolit/vaadi-rooli-urakassa user roolit/urakanvalvoja urakka-id)
  (q/tallenna-urakan-tyyppi! db urakkatyyppi urakka-id)
  (hae-urakan-tyyppi db user urakka-id))

(defn hae-yksittainen-urakka [db user urakka-id]
  (log/debug "Haetaan urakoita urakka-id:llä: " urakka-id)
  (roolit/lukuoikeus-urakassa? user urakka-id)
  (first (into []
               urakka-xf
               (q/hae-yksittainen-urakka db urakka-id))))

(defrecord Urakat []
  component/Lifecycle
  (start [this]
    (let [http (:http-palvelin this)]
      (julkaise-palvelu http :hallintayksikon-urakat
                        (fn [user hallintayksikko]
                          (hallintayksikon-urakat (:db this) user hallintayksikko)))
      (julkaise-palvelu http :hae-urakka
                        (fn [user urakka-id]
                          (hae-yksittainen-urakka (:db this) user urakka-id)))
      (julkaise-palvelu http :hae-urakoita
                        (fn [user teksti]
                          (hae-urakoita (:db this) user teksti)))
      (julkaise-palvelu http :hae-organisaation-urakat
                        (fn [user organisaatio-id]
                          (hae-organisaation-urakat (:db this) user organisaatio-id)))
      (julkaise-palvelu http :hae-urakan-organisaatio
                        (fn [user urakka-id]
                          (hae-urakan-organisaatio (:db this) user urakka-id)))
      (julkaise-palvelu http :tallenna-urakan-sopimustyyppi
                        (fn [user tiedot]
                          (tallenna-urakan-sopimustyyppi (:db this) user tiedot)))
      (julkaise-palvelu http :tallenna-urakan-tyyppi
                        (fn [user tiedot]
                          (tallenna-urakan-tyyppi (:db this) user tiedot)))
      this))

  (stop [this]
    (poista-palvelu (:http-palvelin this) :hallintayksikon-urakat)
    (poista-palvelu (:http-palvelin this) :hae-urakka)
    (poista-palvelu (:http-palvelin this) :hae-urakoita)
    (poista-palvelu (:http-palvelin this) :hae-organisaation-urakat)
    (poista-palvelu (:http-palvelin this) :tallenna-urakan-sopimustyyppi)
    (poista-palvelu (:http-palvelin this) :tallenna-urakan-tyyppi)
    this))