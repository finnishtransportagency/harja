(ns harja.palvelin.palvelut.yhteyshenkilot
  "Yhteyshenkilöiden ja päivystysten hallinnan palvelut"

  (:require [com.stuartsierra.component :as component]
            [harja.kyselyt.yhteyshenkilot :as q]

            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelu]]
            [clojure.java.jdbc :as jdbc]
            [clojure.tools.logging :as log]))

(declare hae-urakan-yhteyshenkilot
         hae-yhteyshenkilotyypit
         tallenna-urakan-yhteyshenkilot)

(defrecord Yhteyshenkilot []
  component/Lifecycle
  (start [this]
    (doto (:http-palvelin this)
      (julkaise-palvelu :hae-urakan-yhteyshenkilot
                        (fn [user urakka-id]
                          (hae-urakan-yhteyshenkilot (:db this) user urakka-id)))
      (julkaise-palvelu :hae-yhteyshenkilotyypit
                        (fn [user _]
                          (hae-yhteyshenkilotyypit (:db this) user)))

      (julkaise-palvelu :tallenna-urakan-yhteyshenkilot
                        (fn [user tiedot]
                          (tallenna-urakan-yhteyshenkilot (:db this) user tiedot))))
    this)

  (stop [this]
    (doto (:http-palvelin this)
      (poista-palvelu :hae-urakan-yhteyshenkilot)
      (poista-palvelu :hae-yhteyshenkilotyypit)
      (poista-palvelu :tallenna-urakan-yhteyshenkilot))
    this))

(defn hae-yhteyshenkilotyypit [db user]
  (into #{}
        (map :rooli)
        (q/hae-yhteyshenkilotyypit db)))
                               
(defn hae-urakan-yhteyshenkilot [db user urakka-id]
  (assert (number? urakka-id) "Urakka-id:n pitää olla numero!")
  (let [tulokset (q/hae-urakan-yhteyshenkilot db urakka-id)
        yhteyshenkilot (into []
                             (comp
                              ;; Muodostetaan organisaatiosta parempi
                              (map #(if-let [org-id (:organisaatio_id %)]
                                      (assoc % :organisaatio {:tyyppi (keyword (str (:organisaatio_tyyppi %)))
                                                              :id org-id
                                                              :nimi (:organisaatio_nimi %)})
                                      %))
                              ;; Poistetaan kenttiä, joita emme halua frontille välittää
                              (map #(dissoc % :yu :organisaatio_id :organisaatio_nimi :organisaatio_tyyppi)))
                             tulokset)
        linkit (into #{} (map :yu) tulokset)
        paivystykset (if (empty? linkit) [] (q/hae-paivystykset db linkit))]
    ;; palauta yhteyshenkilöt ja päivystykset erikseen?
    yhteyshenkilot))

(defn tallenna-urakan-yhteyshenkilot [db user {:keys [urakka-id yhteyshenkilot]}]
  (assert (number? urakka-id) "Urakka-id:n pitää olla numero!")
  (assert (vector? yhteyshenkilot) "Yhteyshenkilöiden tulee olla vektori")
  (jdbc/with-db-transaction [c db]
    ;; käyttäjän oikeudet urakkaan

    ;; ketä yhteyshenkilöitä tässä urakassa on
    (let [nykyiset-yhteyshenkilot (into #{} (q/hae-urakan-yhteyshenkilo-idt db urakka-id))]
      
      ;; tallenna jokainen yhteyshenkilö
      (doseq [{:keys [id rooli] :as yht} yhteyshenkilot]
        (log/info "Tallennetaan yhteyshenkilö " id " urakkaan " urakka-id)
        (if (> id 0)
          ;; Olemassaoleva yhteyshenkilö, päivitetään kentät
          (if-not (nykyiset-yhteyshenkilot id)
            (log/warn "Yritettiin päivittää urakan " urakka-id " yhteyshenkilöä " id", joka ei ole liitetty urakkaan!")
            (q/paivita-yhteyshenkilo! db
                                      (:etunimi yht) (:sukunimi yht)
                                      (:tyopuhelin yht) (:matkapuhelin yht)
                                      (:sahkoposti yht)
                                      (:id (:organisaatio yht))
                                      id))
          
          ;; Uusi yhteyshenkilö, luodaan rivi
          (let [id (:id (q/luo-yhteyshenkilo<! db
                                               (:etunimi yht) (:sukunimi yht)
                                               (:tyopuhelin yht) (:matkapuhelin yht)
                                               (:sahkoposti yht)
                                               (:id (:organisaatio yht))))]
            (q/liita-yhteyshenkilo-urakkaan<! db id urakka-id)))))))












