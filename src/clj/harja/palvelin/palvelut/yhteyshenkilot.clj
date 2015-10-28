(ns harja.palvelin.palvelut.yhteyshenkilot
  "Yhteyshenkilöiden ja päivystysten hallinnan palvelut"

  (:require [com.stuartsierra.component :as component]
            [harja.kyselyt.yhteyshenkilot :as q]

            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelu]]
            [clojure.java.jdbc :as jdbc]
            [taoensso.timbre :as log]

            [harja.domain.roolit :as roolit]
            [harja.kyselyt.konversio :as konv]))

(declare hae-urakan-yhteyshenkilot
         hae-yhteyshenkilotyypit
         tallenna-urakan-yhteyshenkilot

         hae-urakan-paivystajat
         tallenna-urakan-paivystajat

         hae-urakan-kayttajat)

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
                          (tallenna-urakan-yhteyshenkilot (:db this) user tiedot)))
      (julkaise-palvelu :tallenna-urakan-paivystajat
                        (fn [user tiedot]
                          (tallenna-urakan-paivystajat (:db this) user tiedot)))
      (julkaise-palvelu :hae-urakan-kayttajat
                        (fn [user urakka-id]
                          (hae-urakan-kayttajat (:db this) user urakka-id))))

    
    this)

  (stop [this]
    (doseq [p [:hae-urakan-yhteyshenkilot
               :hae-yhteyshenkilotyypit
               :tallenna-urakan-yhteyshenkilot
               :hae-urakan-paivystajat
               :tallenna-urakan-paivystajat
               :hae-urakan-kayttajat]]
      (poista-palvelu (:http-palvelin this) p))
    this))

(defn hae-urakan-kayttajat [db user urakka-id]
  (roolit/vaadi-lukuoikeus-urakkaan user urakka-id)
  (into []
        (map konv/alaviiva->rakenne)
        (q/hae-urakan-kayttajat db urakka-id)))


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
                                                              :nimi (:organisaatio_nimi %)
                                                              :lyhenne (:organisaatio_lyhenne %)})
                                      %))
                              ;; Poistetaan kenttiä, joita emme halua frontille välittää
                              (map #(dissoc % :yu :organisaatio_id :organisaatio_nimi :organisaatio_tyyppi :organisaatio_lyhenne)))
                             tulokset)]
    ;; palauta yhteyshenkilöt ja päivystykset erikseen?
    yhteyshenkilot))

(defn tallenna-urakan-yhteyshenkilot [db user {:keys [urakka-id yhteyshenkilot poistettu]}]
  (assert (number? urakka-id) "Urakka-id:n pitää olla numero!")
  (assert (vector? yhteyshenkilot) "Yhteyshenkilöiden tulee olla vektori")
  (jdbc/with-db-transaction [c db]
    ;; käyttäjän oikeudet urakkaan

    (doseq [id poistettu]
      (log/info "POISTAN yhteyshenkilön " id " urakasta " urakka-id)
      (q/poista-yhteyshenkilo! c id urakka-id))
    
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
            (do (q/paivita-yhteyshenkilo<! c
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
                                               (:id (:organisaatio yht))
                                               nil
                                               nil
                                               nil))]
            (q/liita-yhteyshenkilo-urakkaan<! c (:rooli yht) id urakka-id))))

      ;; kaikki ok
      (hae-urakan-yhteyshenkilot c user urakka-id))))



(defn hae-urakan-paivystajat [db user urakka-id]
  (assert (number? urakka-id) "Urakka-id:n pitää olla numero!")
  (into []
        ;; munklaukset tässä
        (map #(if-let [org-id (:organisaatio_id %)]
                (assoc % :organisaatio {:tyyppi (keyword (str (:organisaatio_tyyppi %)))
                                        :id org-id
                                        :nimi (:organisaatio_nimi %)})
                %))
        (q/hae-urakan-paivystajat db urakka-id)))
                               

(defn tallenna-urakan-paivystajat [db user {:keys [urakka-id paivystajat poistettu] :as tiedot}]
  (jdbc/with-db-transaction [c db]

    (log/info "SAATIIN päivystäjät: " paivystajat)
    (doseq [id poistettu]
      (q/poista-paivystaja! c id urakka-id))

    (doseq [p paivystajat]
      (if (< (:id p) 0)
        ;; Luodaan uusi yhteyshenkilö
        (let [yht (q/luo-yhteyshenkilo<! c
                                         (:etunimi p) (:sukunimi p)
                                         (:tyopuhelin p) (:matkapuhelin p)
                                         (:sahkoposti p) (:organisaatio p)
                                         nil
                                         nil
                                         nil)]
          (q/luo-paivystys<! c
                             (java.sql.Date. (.getTime (:alku p))) (java.sql.Date. (.getTime (:loppu p)))
                             urakka-id (:id yht) ))

        ;; Päivitetään yhteyshenkilön / päivystyksen tietoja
        (let [yht-id (:yhteyshenkilo (first (q/hae-paivystyksen-yhteyshenkilo-id c (:id p) urakka-id)))]
          (log/info "PÄIVITETÄÄN PÄIVYSTYS: " yht-id " => " (pr-str p))
          (q/paivita-yhteyshenkilo<! c
                                    (:etunimi p) (:sukunimi p)
                                    (:tyopuhelin p) (:matkapuhelin p)
                                    (:sahkoposti p) (:id (:organisaatio p))
                                    yht-id)
          (q/paivita-paivystys! c
                                (java.sql.Date. (.getTime (:alku p))) (java.sql.Date. (.getTime (:loppu p)))
                                (:id p) urakka-id))))
                                
    ;; Haetaan lopuksi uuden päivystäjät
    (hae-urakan-paivystajat c user urakka-id)))







