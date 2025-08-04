(ns harja.palvelin.palvelut.hallinta.toimenkuvat-palvelu
  (:require [clojure.string :as str]
            [com.stuartsierra.component :as component]
            [clojure.spec.alpha :as s]
            [taoensso.timbre :as log]
            [harja.domain.oikeudet :as oikeudet]
            [harja.kyselyt.toimenkuvat-kyselyt :as toimenkuvat-kyselyt]
            [harja.kyselyt.urakat :as urakat-q]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelut]]))

(s/def ::urakka-id #(and (not (nil? %)) (pos? %)))
(s/def ::toimenkuva-id #(and (not (nil? %)) (pos? %)))
(s/def ::tehtava-id #(and (not (nil? %)) (pos? %)))

(defn onko-urakka-olemassa?
  "Tarkistaa, että urakka löytyy Harjan tietokannasta"
  [db urakka-id]
  (when urakka-id
    (if-not (urakat-q/onko-olemassa? db urakka-id)
      (throw (SecurityException. (str "Urakkaa " urakka-id " ei ole olemassa.")))
      urakka-id)))

(defn onko-toimenkuva-olemassa?
  "Tarkistaa, että toimenkuva löytyy Harjan tietokannasta"
  [db toimenkuva-id]
  (when toimenkuva-id
    (if-not (toimenkuvat-kyselyt/onko-toimenkuva-olemassa? db {:toimenkuva-id toimenkuva-id})
      (throw (SecurityException. (str "Toimenkuvaa " toimenkuva-id " ei ole olemassa.")))
      toimenkuva-id)))

(defn hae-toimenkuvat
  "Haetaan urakat, toimenkuvat ja urakoiden toimenkuvat. Harjassa vain -25 alkavat urakat voivat sisältää tietokannasta
  peräisin olevia toimenkuvia. Aiemmat urakat käyttävät kovakoodattuja toimenkuvia."
  [db kayttaja]
  (oikeudet/vaadi-lukuoikeus oikeudet/hallinta-rahavaraukset kayttaja)
  {:toimenkuvat (toimenkuvat-kyselyt/hae-toimenkuvat db)
   :urakoiden-toimenkuvat (toimenkuvat-kyselyt/hae-2025-urakoiden-toimenkuvat db)})

(defn paivita-urakan-toimenkuva [db kayttaja {:keys [id nimi urakkakohtainen-nimi urakka valittu?] :as tiedot}]
  (log/info "paivita-urakan-toimenkuva :: tiedot:" tiedot)
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/hallinta-rahavaraukset kayttaja urakka)
  (let [;validoidaan urakka ja toimenkuva
        urakkakohtainen-nimi (if (empty? urakkakohtainen-nimi) nil urakkakohtainen-nimi)
        urakka-valid? (and
                        (s/valid? ::urakka-id urakka)
                        (onko-urakka-olemassa? db urakka))
        toimenkuva-valid? (and
                            (s/valid? ::toimenkuva-id id)
                            (onko-toimenkuva-olemassa? db id))
        ;; Uuden rivin syötössä id on -1
        uusi-rivi (and (not (nil? id)) (= -1 id))
        ;; Haetaan urakan toimenkuva
        urakan-toimenkuva (first (toimenkuvat-kyselyt/hae-urakan-toimenkuva db {:urakkaid urakka
                                                                                :nimi nimi}))
        ;; Käyttöliittymän yksinkertaistamiseksi logiikkamuutos tänne bäackendiin.
        ;; Jos käyttäjä lisää urakkakohtainen-nimi arvon, niin se on päätös ottaa toimenkuva käyttöön.
        valittu? (cond
                   ;; Jos valittu? on nil, mutta urakkakohtainen-nimi on annettu, niin tulkitaan, että se haluttiin valita.
                   (and (nil? valittu?) (not (nil? urakkakohtainen-nimi)))
                   true
                   ;; Jos valittu on false ja urakkokohtainen nimi on annettu, niin sitä ei ole valittu, vaan käyttöliittymästä on poistettu valinta
                   (false? valittu?)
                   false
                   :else
                   valittu?)]
    ;; Muokataan toimenkuvata tai lisätään se urakalle
    (if (and urakka-valid? toimenkuva-valid? (not uusi-rivi))
      (cond
        ;; Jos toimenkuva löytyy id:llä ja edelleen käyttäjä on valinnut sen, niin päivitetään.
        (and urakan-toimenkuva valittu?)
        (toimenkuvat-kyselyt/paivita-urakan-toimenkuva<! db {:urakkaid urakka
                                                             :toimenkuvaid (:id urakan-toimenkuva)
                                                             :urakkakohtainen-nimi (or urakkakohtainen-nimi nil)})
        ;; Jos toimenkuvasta ei ole kannassa, mutta käyttäjä on valinnut sen, niin lisätään.
        (and (not urakan-toimenkuva) valittu?)
        (toimenkuvat-kyselyt/lisaa-urakan-toimenkuva<! db {:urakkaid urakka
                                                           :toimenkuva nimi
                                                           :urakkakohtainen-nimi (or urakkakohtainen-nimi nil)})
        ;; Muussa tapauksessa se poistetaan
        :else
        (toimenkuvat-kyselyt/poista-urakan-toimenkuva<! db {:urakkaid urakka
                                                            :toimenkuvaid (:id urakan-toimenkuva)}))
      ;; Lisätään kokonaan uusi toimenkuva, mutta ei merkitä sitä vielä käyttöön urakalle. Jos urakkakohtainen-nimi on syötetty, niin sitä ei hyödynnetä
      (if (and (not (nil? nimi)) (not (empty? (str/trim nimi))))

        ;; Lisätään uusi toimenkuva tietokantaan
        (toimenkuvat-kyselyt/lisaa-uusi-toimenkuva<! db {:toimenkuva nimi})
        ;; Jos nimi on tyhjä tai nil niin kerrotaan siitä käyttäjälle
        (throw (IllegalArgumentException. "Toimenkuvan nimi ei voi olla tyhjä tai nil."))))

    ;; Palauta sen jälkeen tietokannasta tuoreet urakan toimenkuvat
    (hae-toimenkuvat db kayttaja)))


(defn poista-toimenkuva [db kayttaja {:keys [id]}]
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/hallinta-rahavaraukset kayttaja)
  (log/debug "poista-toimenkuva :: toimenkuva-id: " id)
  (let [toimenkuva-valid? (and
                            (s/valid? ::toimenkuva-id id)
                            (onko-toimenkuva-olemassa? db id))
        ;; Tarkistetaan, onko johto_ja_hallintokorvaus taulussa tällä toimenkuva id:llä merkintöjä
        ;; Jos on, niin poistoa ei voi tehdä
        onko-kaytossa? (toimenkuvat-kyselyt/onko-toimenkuva-kaytossa? db id)]
    (if (and toimenkuva-valid? (not onko-kaytossa?))
      (do ;; Poista toimenkuva kaikilta urakoilta
        (toimenkuvat-kyselyt/poista-toimenkuva-urakoilta! db id)
        ;; Poista toimenkuva lopullisesti tietokannasta
        (toimenkuvat-kyselyt/poista-toimenkuva! db id)
        (log/info "Toimenkuva poistettu onnistuneesti."))
      (throw (SecurityException. (str "Toimenkuva on käytössä. Eikä sitä voi poistaa"))))

    ;; Välitä ui:lle muuttunut tilanne
    (hae-toimenkuvat db kayttaja)))

(defrecord ToimenkuvatHallinta []
  component/Lifecycle
  (start [{:keys [http-palvelin db] :as this}]
    (julkaise-palvelu http-palvelin :hae-toimenkuvat
      (fn [kayttaja _]
        (hae-toimenkuvat db kayttaja)))
    (julkaise-palvelu http-palvelin :paivita-urakan-toimenkuva
      (fn [kayttaja tiedot]
        (paivita-urakan-toimenkuva db kayttaja tiedot)))
    (julkaise-palvelu http-palvelin :poista-toimenkuva
      (fn [kayttaja tiedot]
        (poista-toimenkuva db kayttaja tiedot)))
    this)
  (stop [{:keys [http-palvelin] :as this}]
    (poista-palvelut http-palvelin
      :hae-toimenkuvat
      :paivita-urakan-toimenkuva
      :poista-toimenkuva)
    this))
