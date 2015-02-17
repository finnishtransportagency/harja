(ns harja.palvelin.palvelut.yhteyshenkilot
  "Yhteyshenkilöiden ja päivystysten hallinnan palvelut"

  (:require [com.stuartsierra.component :as component]
            [harja.kyselyt.yhteyshenkilot :as q]

            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelu]]
            [clojure.java.jdbc :as jdbc]
            [clojure.tools.logging :as log]))

(declare hae-urakan-yhteyshenkilot
         hae-yhteyshenkilotyypit
         tallenna-urakan-yhteyshenkilot

         hae-urakan-paivystajat)

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
      (julkaise-palvelu :hae-urakan-paivystajat
                        (fn [user urakka-id]
                          (hae-urakan-paivystajat (:db this) user urakka-id)))
      (julkaise-palvelu :tallenna-urakan-yhteyshenkilot
                        (fn [user tiedot]
                          (tallenna-urakan-yhteyshenkilot (:db this) user tiedot))))
    this)

  (stop [this]
    (doseq [p [:hae-urakan-yhteyshenkilot
               :hae-yhteyshenkilotyypit
               :tallenna-urakan-yhteyshenkilot
               :hae-urakan-paivystajat]]
      (poista-palvelu (:http-palvelin this) p))
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
                             tulokset)]
    ;; palauta yhteyshenkilöt ja päivystykset erikseen?
    yhteyshenkilot))

(defn tallenna-urakan-yhteyshenkilot [db user {:keys [urakka-id yhteyshenkilot]}]
  (assert (number? urakka-id) "Urakka-id:n pitää olla numero!")
  (assert (vector? yhteyshenkilot) "Yhteyshenkilöiden tulee olla vektori")
  (jdbc/with-db-transaction [c db]
    ;; käyttäjän oikeudet urakkaan

    ;; ketä yhteyshenkilöitä tässä urakassa on
    (let [nykyiset-yhteyshenkilot (into #{} (map :yhteyshenkilo)
                                        (q/hae-urakan-yhteyshenkilo-idt db urakka-id))]
      
      ;; tallenna jokainen yhteyshenkilö
      (doseq [{:keys [id rooli] :as yht} yhteyshenkilot]
        (log/info "Tallennetaan yhteyshenkilö " id " urakkaan " urakka-id)
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



(defn hae-urakan-paivystajat [db user urakka-id]
  (assert (number? urakka-id) "Urakka-id:n pitää olla numero!")
  (into []
        ;; munklaukset tässä
        (q/hae-urakan-paivystajat db urakka-id)))
                               








