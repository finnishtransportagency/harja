(ns harja.palvelin.palvelut.urakat
  (:require [com.stuartsierra.component :as component]
            [harja.domain.roolit :as roolit]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelu]]
            [harja.kyselyt.urakat :as q]
            [harja.kyselyt.konversio :as konv]
            [harja.geo :refer [muunna-pg-tulokset]]
            [clojure.string :as str]
            [taoensso.timbre :as log]))

(declare hallintayksikon-urakat
         urakan-tiedot
         hae-urakoita
         hae-urakan-organisaatio
         hae-organisaation-urakat
         tallenna-urakan-sopimustyyppi)


(defrecord Urakat []
  component/Lifecycle
  (start [this]
    (let [http (:http-palvelin this)]
      (julkaise-palvelu http :hallintayksikon-urakat
                        (fn [user hallintayksikko]
                          (hallintayksikon-urakat (:db this) user hallintayksikko)))
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
      this))

  (stop [this]
    (poista-palvelu (:http-palvelin this) :hallintayksikon-urakat)
    (poista-palvelu (:http-palvelin this) :hae-urakoita)
    (poista-palvelu (:http-palvelin this) :hae-organisaation-urakat)
    (poista-palvelu (:http-palvelin this) :tallenna-urakan-sopimustyyppi)
    this))

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
  ;;(Thread/sleep 2000) ;;; FIXME: this is to try out "ajax loading" ui
  (into []
        urakka-xf
        (q/listaa-urakat-hallintayksikolle db hallintayksikko-id)))

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
  (:sopimustyyppi (first (q/hae-urakan-sopimustyyppi db urakka-id))))

(defn tallenna-urakan-sopimustyyppi [db user {:keys  [urakka-id sopimustyyppi]}]
  (roolit/vaadi-rooli-urakassa user roolit/urakanvalvoja urakka-id)
  (q/tallenna-urakan-sopimustyyppi! db sopimustyyppi urakka-id)
  (hae-urakan-sopimustyyppi db user urakka-id))