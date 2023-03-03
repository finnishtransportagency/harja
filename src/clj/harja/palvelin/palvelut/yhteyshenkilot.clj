(ns harja.palvelin.palvelut.yhteyshenkilot
  "Yhteyshenkilöiden ja päivystysten hallinnan palvelut"

  (:require [com.stuartsierra.component :as component]
            [harja.kyselyt.yhteyshenkilot :as q]
            [harja.kyselyt.urakat :as uq]

            [harja.palvelin.komponentit.http-palvelin
             :refer [julkaise-palvelut poista-palvelut async transit-vastaus]]
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

(defn hae-urakan-kayttajat [db fim urakka-id]
  (->> urakka-id
       (uq/hae-urakan-sampo-id db)
       (fim/hae-urakan-kayttajat fim)))

(defn hae-urakoiden-kayttajat-rooleissa
  "Palauttaa annetun urakan (sampo-id) haluttujen FIM-käyttäjäroolien tiedot.

  Parametrit:

  fim           FIM-komponentti
  urakka-idt    Niiden urakoiden idt, jonka käyttäjiä etsitään FIMistä
  fim-kayttajaroolit Setti rooleja, joissa oleville henkilöille viesti lähetetään. Huomioi kirjoitusasu!
    Esim. #{\"ely rakennuttajakonsultti\" \"urakan vastuuhenkilö\" \"ely urakanvalvoja\"}"
  [db fim {:keys [urakka-idt fim-kayttajaroolit]}]
  (let [rivit (vec
                (apply
                  concat
                  (for [u urakka-idt
                        :let [urakka (first (uq/hae-urakan-nimi db u))
                              kayttajat (fim/hae-urakan-kayttajat-jotka-roolissa fim
                                                                                 (:sampoid urakka)
                                                                                 fim-kayttajaroolit)]]
                    (map #(merge urakka %) kayttajat))))]
    rivit))

(defn hae-urakan-yhteyshenkilot [db user urakka-id salli-ristiinnakeminen?]
  (assert (number? urakka-id) "Urakka-id:n pitää olla numero!")
  ;; HAR-7872 Tilannekuvasta on kyettävä näkemään dialogissa eri urakoiden yhteystietoja
  (if salli-ristiinnakeminen?
    (oikeudet/ei-oikeustarkistusta!)
    (oikeudet/vaadi-lukuoikeus oikeudet/urakat-yleiset user urakka-id))
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
      (hae-urakan-yhteyshenkilot c user urakka-id false))))



(defn hae-urakan-paivystajat [db user urakka-id]
  (assert (number? urakka-id) "Urakka-id:n pitää olla numero!")
  (oikeudet/vaadi-lukuoikeus oikeudet/urakat-yleiset user urakka-id)
  (let [kaynnissaolevan-hoitokauden-alkupvm (c/from-date (first (pvm/paivamaaran-hoitokausi (pvm/nyt))))
        paivystajat (into []
                          (map #(as-> % rivi
                                  (if-let [org-id (:organisaatio_id rivi)]
                                    (assoc rivi :organisaatio
                                           {:tyyppi (keyword (str (:organisaatio_tyyppi rivi)))
                                            :id org-id
                                            :nimi (:organisaatio_nimi rivi)})
                                    rivi)

                                  (if-let [org-id (:urakoitsija_id rivi)]
                                    (assoc rivi :urakoitsija
                                           {:tyyppi (keyword (str (:urakoitsija_tyyppi rivi)))
                                            :id org-id
                                            :nimi (:urakoitsija_nimi rivi)})
                                    rivi)))
                          (q/hae-urakan-paivystajat db urakka-id nil nil))
        paivystajat (filterv #(pvm/sama-tai-jalkeen? (c/from-sql-time (:loppu %))
                                                     kaynnissaolevan-hoitokauden-alkupvm)
                             paivystajat)]
    paivystajat))

(defn- vaadi-alkuaika-ei-menneisyydessa [{:keys [alku etunimi sukunimi] :as paiv}]
  (when (pvm/ennen? alku (pvm/paivan-alussa (pvm/nyt)))
    (throw (IllegalArgumentException. (str "Päivystäjän "
                                           etunimi " " sukunimi
                                           " päivystysvuoroa ei saa asettaa alkamaan ennen tätä päivää.")))))
(defn- vaadi-olemassaolevaa-paivystyksen-alkuhetkea-ei-aikaisteta
  "Tarkistaa ettei olemassaolevan päivystysvuoron menneisyydessä olevaa alkuhetkeä yritetä jälkikäteen aikaistaa."
  [vanha-alkuhetki {:keys [alku etunimi sukunimi]}]
  (when (and (pvm/ennen? alku vanha-alkuhetki)
             (pvm/ennen? alku (pvm/paivan-alussa (pvm/nyt))))
    (throw (IllegalArgumentException. (str "Olemassaolevan päivystäjän "
                                           etunimi " " sukunimi
                                           " päivystysvuoron alkua ei saa takautuvasti siirtää kauemmaksi menneisyyteen.")))))

(defn tallenna-urakan-paivystajat [db user {:keys [urakka-id paivystajat poistettu] :as tiedot}]
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-yleiset user urakka-id)
  (try
    (jdbc/with-db-transaction [c db]
      (doseq [id poistettu]
        (q/poista-paivystaja! c id urakka-id))

      (doseq [p paivystajat
              :let [yhteyshenkilo {:etunimi (:etunimi p)
                                   :sukunimi (:sukunimi p)
                                   :tyopuhelin (puhelinnumero/kanonisoi (:tyopuhelin p))
                                   :matkapuhelin (puhelinnumero/kanonisoi (:matkapuhelin p))
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
          (let [_ (vaadi-alkuaika-ei-menneisyydessa p)
                yht (q/luo-yhteyshenkilo<! c yhteyshenkilo)]
            (q/luo-paivystys<! c
                               (assoc paivystys
                                 :yhteyshenkilo (:id yht)
                                 :ulkoinen_id nil
                                 :kayttaja_id (:id user))))

          ;; Päivitetään yhteyshenkilön / päivystyksen tietoja
          (let [yht-id (:yhteyshenkilo (first (q/hae-paivystyksen-yhteyshenkilo-id c {:id (:id p)
                                                                                      :urakka urakka-id})))
                alkupvm-kannassa-ennen-muutosta (q/hae-paivystyksen-alkupvm-idlla c {:id (:id p)
                                                                                     :urakka urakka-id})
                _ (vaadi-olemassaolevaa-paivystyksen-alkuhetkea-ei-aikaisteta alkupvm-kannassa-ennen-muutosta p)]
            (q/paivita-yhteyshenkilo<! c (assoc yhteyshenkilo
                                           :id yht-id))
            (q/paivita-paivystys! c (assoc paivystys
                                      :id (:id p)
                                      :yhteyshenkilo yht-id
                                      :kayttaja_id (:id user))))))


      ;; Haetaan lopuksi uuden päivystäjät
      (hae-urakan-paivystajat c user urakka-id))
    (catch IllegalArgumentException e
      (log/warn e "IllegalArgumentException pyynnössä tallenna-urakan-paivystajat " (pr-str e))
      (transit-vastaus 400 {:virhe (.getMessage e)}))))

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

(defrecord Yhteyshenkilot []
  component/Lifecycle
  (start [this]
    (julkaise-palvelut
      (:http-palvelin this)

      :hae-urakan-yhteyshenkilot
      (fn [user urakka-id]
        (hae-urakan-yhteyshenkilot (:db this) user urakka-id false))

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
          (hae-urakan-kayttajat (:db this) (:fim this) urakka-id)))

      :hae-urakoiden-kayttajat-rooleissa
      (fn [user tiedot]
        (oikeudet/vaadi-lukuoikeus oikeudet/urakat-yleiset user (:oman-urakan-id tiedot))
        (async
          (hae-urakoiden-kayttajat-rooleissa (:db this) (:fim this) tiedot)))

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
                     :hae-urakoiden-kayttajat-rooleissa
                     :hae-urakan-vastuuhenkilot
                     :tallenna-urakan-vastuuhenkilot-roolille)
    this))

