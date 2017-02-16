(ns harja.palvelin.palvelut.yhteyshenkilot
  "Yhteyshenkilöiden ja päivystysten hallinnan palvelut"

  (:require [com.stuartsierra.component :as component]
            [harja.kyselyt.yhteyshenkilot :as q]
            [harja.kyselyt.urakat :as uq]

            [harja.palvelin.komponentit.http-palvelin
             :refer [julkaise-palvelut poista-palvelut async]]
            [clojure.java.jdbc :as jdbc]
            [taoensso.timbre :as log]

            [harja.domain.roolit :as roolit]
            [harja.kyselyt.konversio :as konv]
            [harja.domain.oikeudet :as oikeudet]
            [harja.palvelin.komponentit.fim :as fim]
            [harja.domain.puhelinnumero :as puhelinnumero]
            [harja.pvm :as pvm]
            [clj-time.core :as t]
            [clj-time.coerce :as c]
            [harja.fmt :as fmt])
  (:import (java.sql Date)))

(declare hae-urakan-yhteyshenkilot
         hae-yhteyshenkilotyypit
         tallenna-urakan-yhteyshenkilot

         hae-urakan-paivystajat
         tallenna-urakan-paivystajat

         hae-urakan-kayttajat
         hae-urakan-vastuuhenkilot
         tallenna-urakan-vastuuhenkilot-roolille)

(defrecord Yhteyshenkilot []
  component/Lifecycle
  (start [this]
    (julkaise-palvelut
     (:http-palvelin this)

     :hae-urakan-yhteyshenkilot
     (fn [user urakka-id]
       (hae-urakan-yhteyshenkilot (:db this) user urakka-id))

     :hae-urakan-paivystajat
     (fn [user urakka-id]
       (hae-urakan-paivystajat (:db this) user urakka-id))

     :tallenna-urakan-yhteyshenkilot
     (fn [user tiedot]
       (tallenna-urakan-yhteyshenkilot (:db this) user tiedot))

     :tallenna-urakan-paivystajat
     (fn [user tiedot]
       (tallenna-urakan-paivystajat (:db this) user tiedot))

     :hae-urakan-kayttajat
     (fn [user urakka-id]
       (oikeudet/vaadi-lukuoikeus oikeudet/urakat-yleiset user urakka-id)
       (async
        (hae-urakan-kayttajat (:db this) (:fim this) user urakka-id)))

     :hae-urakan-vastuuhenkilot
     (fn [user urakka-id]
       (hae-urakan-vastuuhenkilot (:db this) user urakka-id))

     :tallenna-urakan-vastuuhenkilot-roolille
     (fn [user tiedot]
       (tallenna-urakan-vastuuhenkilot-roolille (:db this) user tiedot)))

    this)

  (stop [this]
    (poista-palvelut (:http-palvelin this)
                     :hae-urakan-yhteyshenkilot
                     :tallenna-urakan-yhteyshenkilot
                     :hae-urakan-paivystajat
                     :tallenna-urakan-paivystajat
                     :hae-urakan-kayttajat
                     :hae-urakan-vastuuhenkilot
                     :tallenna-urakan-vastuuhenkilot-roolille)
    this))

(defn hae-urakan-kayttajat [db fim user urakka-id]
  (->> urakka-id
       (uq/hae-urakan-sampo-id db)
       (fim/hae-urakan-kayttajat fim)))

(defn hae-urakan-yhteyshenkilot [db user urakka-id]
  (assert (number? urakka-id) "Urakka-id:n pitää olla numero!")
  (oikeudet/vaadi-lukuoikeus oikeudet/urakat-yleiset user urakka-id)
  (let [tulokset (q/hae-urakan-yhteyshenkilot db urakka-id)
        yhteyshenkilot
        (into []
              (comp
               ;; Muodostetaan organisaatiosta parempi
               (map #(if-let [org-id (:organisaatio_id %)]
                       (assoc % :organisaatio {:tyyppi (keyword (str (:organisaatio_tyyppi %)))
                                               :id org-id
                                               :nimi (:organisaatio_nimi %)
                                               :lyhenne (:organisaatio_lyhenne %)})
                       %))
               ;; Poistetaan kenttiä, joita emme halua frontille välittää
               (map #(dissoc % :yu :organisaatio_id :urakoitsija_nimi
                             :organisaatio_tyyppi :organisaatio_lyhenne)))
              tulokset)]
    ;; palauta yhteyshenkilöt ja päivystykset erikseen?
    yhteyshenkilot))

(defn tallenna-urakan-yhteyshenkilot [db user {:keys [urakka-id yhteyshenkilot poistettu]}]
  (assert (number? urakka-id) "Urakka-id:n pitää olla numero!")
  (assert (vector? yhteyshenkilot) "Yhteyshenkilöiden tulee olla vektori")
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-yleiset user urakka-id)
  (jdbc/with-db-transaction [c db]
    ;; käyttäjän oikeudet urakkaan

    (doseq [id poistettu]
      (log/debug "POISTAN yhteyshenkilön " id " urakasta " urakka-id)
      (q/poista-yhteyshenkilo! c id urakka-id))

    ;; ketä yhteyshenkilöitä tässä urakassa on
    (let [nykyiset-yhteyshenkilot (into #{} (map :yhteyshenkilo)
                                        (q/hae-urakan-yhteyshenkilo-idt db urakka-id))]

      ;; tallenna jokainen yhteyshenkilö
      (doseq [{:keys [id rooli] :as yht} yhteyshenkilot]
        (log/debug "Tallennetaan yhteyshenkilö " yht " urakkaan " urakka-id)
        (if (> id 0)
          ;; Olemassaoleva yhteyshenkilö, päivitetään kentät
          (if-not (nykyiset-yhteyshenkilot id)
            (log/warn "Yritettiin päivittää urakan " urakka-id " yhteyshenkilöä " id
                      ", joka ei ole liitetty urakkaan!")
            (do (q/paivita-yhteyshenkilo c
                                          (:etunimi yht) (:sukunimi yht)
                                          (:tyopuhelin yht) (:matkapuhelin yht)
                                          (:sahkoposti yht)
                                          (:id (:organisaatio yht))
                                          id)
                (q/aseta-yhteyshenkilon-rooli! c (:rooli yht) id urakka-id)))

          ;; Uusi yhteyshenkilö, luodaan rivi
          (let [id (:id (q/luo-yhteyshenkilo c
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
  (oikeudet/vaadi-lukuoikeus oikeudet/urakat-yleiset user urakka-id)
  (let [kaynnissaolevan-hoitokauden-alkupvm (c/from-date (first (pvm/paivamaaran-hoitokausi (pvm/nyt))))
        paivystajat (into []
                          (map #(if-let [org-id (:organisaatio %)]
                                 (assoc % :organisaatio {:tyyppi (keyword (str (:urakoitsija_tyyppi %)))
                                                         :id org-id
                                                         :nimi (:urakoitsija_nimi %)})
                                 %))
                          (q/hae-urakan-paivystajat db urakka-id nil nil))
        paivystajat (filterv #(pvm/sama-tai-jalkeen? (c/from-sql-time (:loppu %))
                                                     kaynnissaolevan-hoitokauden-alkupvm)
                             paivystajat)]
    paivystajat))


(defn tallenna-urakan-paivystajat [db user {:keys [urakka-id paivystajat poistettu] :as tiedot}]
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-yleiset user urakka-id)
  (jdbc/with-db-transaction [c db]

    (log/debug "Päivystäjät: " paivystajat)
    (doseq [id poistettu]
      (q/poista-paivystaja! c id urakka-id))

    (doseq [p paivystajat
            :let [yhteyshenkilo {:etunimi (:etunimi p)
                                 :sukunimi (:sukunimi p)
                                 :tyopuhelin (puhelinnumero/kanonisoi (:tyopuhelin p))
                                 :matkapuhelin (puhelinnumero/kanonisoi(:matkapuhelin p))
                                 :sahkoposti (:sahkoposti p)
                                 :organisaatio (:id (:organisaatio p))
                                 :sampoid nil
                                 :kayttajatunnus nil
                                 :ulkoinen_id nil}
                  paivystys {:alku (Date. (.getTime (:alku p)))
                             :loppu (Date. (.getTime (:loppu p)))
                             :urakka urakka-id
                             :varahenkilo (not (:vastuuhenkilo p))
                             :vastuuhenkilo (:vastuuhenkilo p)}]]
      (if (< (:id p) 0)
        ;; Luodaan uusi yhteyshenkilö
        (let [yht (q/luo-yhteyshenkilo<! c yhteyshenkilo)]
          (q/luo-paivystys<! c
                             (assoc paivystys
                                    :yhteyshenkilo (:id yht)
                                    :ulkoinen_id nil
                                    :kayttaja_id (:id user))))

        ;; Päivitetään yhteyshenkilön / päivystyksen tietoja
        (let [yht-id (:yhteyshenkilo (first (q/hae-paivystyksen-yhteyshenkilo-id c (:id p)
                                                                                 urakka-id)))]
          (log/debug "PÄIVITETÄÄN PÄIVYSTYS: " yht-id " => " (pr-str p))
          (q/paivita-yhteyshenkilo<! c (assoc yhteyshenkilo
                                              :id yht-id))
          (q/paivita-paivystys! c (assoc paivystys
                                         :id (:id p)
                                         :yhteyshenkilo yht-id)))))

    ;; Haetaan lopuksi uuden päivystäjät
    (hae-urakan-paivystajat c user urakka-id)))

(defn hae-urakan-vastuuhenkilot [db user urakka-id]
  (oikeudet/vaadi-lukuoikeus oikeudet/urakat-yleiset user urakka-id)
  (q/hae-urakan-vastuuhenkilot db urakka-id))

(defn tallenna-urakan-vastuuhenkilot-roolille
  [db user {:keys [urakka-id rooli vastuuhenkilo varahenkilo] :as tiedot}]
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-yleiset user urakka-id)
  (when (and (= (roolit/osapuoli user) :urakoitsija)
             (not= rooli "vastuuhenkilo"))
    (log/error "Käyttäjä " user " yritti luoda vastuuhenkilön urakkaan "
               urakka-id " roolilla " rooli)
    (throw (SecurityException. "Ei oikeutta luoda vastuuhenkilö annetulle roolille")))

  (let [luo<! (fn [c kayttaja ensisijainen]
                (q/luo-urakan-vastuuhenkilo<! c {:urakka urakka-id
                                                 :rooli rooli
                                                 :etunimi  (:etunimi kayttaja)
                                                 :sukunimi (:sukunimi kayttaja)
                                                 :puhelin (:puhelin kayttaja)
                                                 :sahkoposti (:sahkoposti kayttaja)
                                                 :kayttajatunnus (:kayttajatunnus kayttaja)
                                                 :ensisijainen ensisijainen}))]
    (jdbc/with-db-transaction [c db]
      (q/poista-urakan-vastuuhenkilot-roolille! c {:urakka urakka-id :rooli rooli})
      (when vastuuhenkilo
        (luo<! c vastuuhenkilo true))
      (when varahenkilo
        (luo<! c varahenkilo false)))
    (hae-urakan-vastuuhenkilot db user urakka-id)))
