(ns harja.palvelin.palvelut.indeksit
  (:require [com.stuartsierra.component :as component]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelu]]
            [clojure.tools.logging :as log]
            [harja.kyselyt.indeksit :as q]))

(declare hae-indeksit)
                        
(defrecord Indeksit []
  component/Lifecycle
  (start [this]
   (julkaise-palvelu (:http-palvelin this)
     :indeksit (fn [user]
       (hae-indeksit (:db this) user)))
   this)

  (stop [this]
    (poista-palvelu (:http-palvelin this) :indeksit)
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

(defn tallenna-indeksit [db user {:keys [indeksit poistettu]}]
  (assert (vector? indeksit) "indeksit tulee olla vektori")
  (jdbc/with-db-transaction [c db]
    ;; käyttäjän oikeudet urakkaan

    (doseq [nimi poistettu]
      (log/info "POISTAN indeksin " id)
      (q/poista-indeksi! c nimi))
    
    ;; ketä yhteyshenkilöitä tässä urakassa on
    (let [nykyiset-yhteyshenkilot (into #{} (map :yhteyshenkilo)
                                        (q/hae-urakan-yhteyshenkilo-idt db urakka-id))]
      
      ;; tallenna jokainen yhteyshenkilö
      (doseq [{:keys [id rooli] :as yht} yhteyshenkilot]
        (log/info "Tallennetaan yhteyshenkilö " yht " urakkaan " urakka-id)
        (if (> id 0)
          ;; Olemassaoleva yhteyshenkilö, päivitetään kentät
          (if-not (nykyiset-yhteyshenkilot id)
            (log/warn "Yritettiin päivittää urakan " urakka-id " yhteyshenkilöä " id", joka ei ole liitetty urakkaan!")
            (do (q/paivita-yhteyshenkilo! c
                                          (:etunimi yht) (:sukunimi yht)
                                          (:tyopuhelin yht) (:matkapuhelin yht)
                                          (:sahkoposti yht)
                                          (:id (:organisaatio yht))
                                          id)
                (q/aseta-yhteyshenkilon-rooli! c (:rooli yht) id urakka-id)))
          
          ;; Uusi yhteyshenkilö, luodaan rivi
          (let [id (:id (q/luo-yhteyshenkilo<! c
                                               (:etunimi yht) (:sukunimi yht)
                                               (:tyopuhelin yht) (:matkapuhelin yht)
                                               (:sahkoposti yht)
                                               (:id (:organisaatio yht))))]
            (q/liita-yhteyshenkilo-urakkaan<! c (:rooli yht) id urakka-id))))

      ;; kaikki ok
      (hae-urakan-yhteyshenkilot c user urakka-id))))
  
  
