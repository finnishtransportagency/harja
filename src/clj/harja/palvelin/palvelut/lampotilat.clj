(ns harja.palvelin.palvelut.lampotilat
  (:require [com.stuartsierra.component :as component]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelut]]
            [harja.kyselyt.lampotilat :as q]
            [harja.kyselyt.pohjavesialueet :as pohjavesialueet-q]
            [harja.kyselyt.urakat :as urakat]
            [harja.kyselyt.konversio :as konv]
            [clojure.java.jdbc :as jdbc]
            [taoensso.timbre :as log]
            [harja.geo :as geo]
            [harja.palvelin.integraatiot.ilmatieteenlaitos :as ilmatieteenlaitos]
            [harja.pvm :as pvm]
            [harja.domain.oikeudet :as oikeudet]
            [clojure.string :as str]))


(defn hae-lampotilat-ilmatieteenlaitokselta [db user url vuosi]
  (log/debug "hae-lampotilat-ilmatieteenlaitokselta, url " url " vuosi " vuosi)
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/hallinta-lampotilat user) ;; vaatii KIRJOITUS oikeuden
  (assert (and url vuosi) "Annettava url ja vuosi kun haetaan ilmatieteenlaitokselta lämpötiloja.")
  ;; Ilmatieteenlaitos käyttää :urakka-id -kentässään Harjan :alueurakkanro -kenttää, siksi muunnoksia alla
  (let [hae-urakoiden-lampotilat (fn [url]
                                   (into {}
                                         (map (juxt :urakka-id #(dissoc % :urakka-id)))
                                         (ilmatieteenlaitos/hae-talvikausi url vuosi)))
        hoidon-urakoiden-lampotilat-1981-2010 (hae-urakoiden-lampotilat url)
        hoidon-urakoiden-lampotilat-1971-2000 (hae-urakoiden-lampotilat
                                                (str/replace url "tieindeksi2" "tieindeksi"))
        hoidon-urakka-ja-alueurakkanro-avaimet
        (urakat/hae-aktiivisten-hoitourakoiden-alueurakkanumerot db vuosi)
        tulos (into {}
                    (comp
                      (map (fn [urakka]
                             (merge urakka
                                    ;; Urakan tiedot löytyvät usein vain toisesta result setistä minkä FMI API palauttaa.
                                    ;; Otetaan kaikki kentät siitä mikä löytyy,
                                    ;; sekä varmistetaan vielä erikseen että uuden vertailukauden ka on avaimessa :pitkakeskilampotila
                                    ;; ja vanhan vertailukauden avaimessa :pitkakeskilampotila_vanha
                                    (or
                                      (get hoidon-urakoiden-lampotilat-1981-2010
                                          (:alueurakkanro urakka))
                                      (get hoidon-urakoiden-lampotilat-1971-2000
                                           (:alueurakkanro urakka)))
                                    {:pitkakeskilampotila (:pitkakeskilampotila
                                                            (get hoidon-urakoiden-lampotilat-1981-2010
                                                                 (:alueurakkanro urakka)))}
                                    {:pitkakeskilampotila_vanha (:pitkakeskilampotila
                                                                  (get hoidon-urakoiden-lampotilat-1971-2000
                                                                       (:alueurakkanro urakka)))})))
                      (map (juxt :id identity)))
                    hoidon-urakka-ja-alueurakkanro-avaimet)]
    (log/debug "VASTAUS: " (pr-str tulos))
    tulos))

(defn hae-teiden-hoitourakoiden-lampotilat [db user hoitokausi]
  (log/debug "hae-teiden-hoitourakoiden-lampotilat hoitokaudella: " hoitokausi)
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/hallinta-lampotilat user)
  (let [alkupvm (first hoitokausi)
        loppupvm (second hoitokausi)]
    (into {}
          (map (juxt :urakka identity)
               (q/hae-teiden-hoitourakoiden-lampotilat db alkupvm loppupvm)))))

(defn tallenna-teiden-hoitourakoiden-lampotilat [db user {:keys [hoitokausi lampotilat]}]
  (log/debug "tallenna-teiden-hoitourakoiden-lampotilat, hoitokausi " hoitokausi ", lämpötilat: " lampotilat)
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/hallinta-lampotilat user)
  (jdbc/with-db-transaction [db db]
    (doseq [lt lampotilat]
      (let [id (:lampotilaid lt)
            parametrit [(:urakka lt) (:alkupvm lt) (:loppupvm lt)
                        (:keskilampotila lt)
                        (:pitkakeskilampotila lt)
                        (:pitkakeskilampotila_vanha lt)]]
        (if id
          (apply q/paivita-lampotila<! db (concat parametrit [id]))
          (apply q/uusi-lampotila<! db parametrit))))
    (hae-teiden-hoitourakoiden-lampotilat db user hoitokausi)))

(defn hae-urakan-suolasakot-ja-lampotilat
  [db user urakka-id]
  (log/debug "hae-urakan-suolasakot-ja-lampotilat")
  (oikeudet/vaadi-lukuoikeus oikeudet/urakat-toteumat-suola user urakka-id)
  (let [pitkakeskilampotila (if (<= (urakat/hae-urakan-alkuvuosi db urakka-id) 2014)
                              :pitkakeskilampotila_vanha
                              :pitkakeskilampotila)]
    {:suolasakot (into []
                       (map #(konv/decimal->double % :maara))
                       (q/hae-urakan-suolasakot db urakka-id))
     :lampotilat (into []
                       (comp
                        (map #(assoc % :pitkakeskilampotila
                                     (get % pitkakeskilampotila)))
                        (map #(konv/decimal->double % :keskilampotila))
                        (map #(konv/decimal->double % :pitkakeskilampotila)))
                       (q/hae-urakan-lampotilat db urakka-id))
     :pohjavesialueet (into []
                            (geo/muunna-pg-tulokset :alue)
                            (pohjavesialueet-q/hae-urakan-pohjavesialueet-teittain db urakka-id))
     :pohjavesialue-talvisuola (q/hae-urakan-pohjavesialue-talvisuolarajat-teittain db urakka-id)}))

(defn tallenna-suolasakko
  [db user urakka hoitokauden-alkuvuosi tiedot]
  (log/debug "tallenna suolasakko" tiedot)
  (let [suolasakon-id (:id (first (q/hae-suolasakko-id db urakka hoitokauden-alkuvuosi)))
        params {:maara (:maara tiedot)
                :vainsakkomaara (:vainsakkomaara tiedot)
                :maksukuukausi (:maksukuukausi tiedot)
                :indeksi (:indeksi tiedot)
                :kayttaja (:id user)
                :talvisuolaraja (:talvisuolaraja tiedot)
                :urakka urakka
                :kaytossa (boolean (:kaytossa tiedot))
                :hoitokauden_alkuvuosi hoitokauden-alkuvuosi
                :id suolasakon-id}]
    (if suolasakon-id
      (do
        (q/paivita-suolasakko! db params)
          suolasakon-id)
      (:id (q/luo-suolasakko<! db params)))))


(defn tallenna-pohjavesialue-talvisuola
  "Päivittää pohjavesialueen talvisuolarajan, tai luo uuden jos rajaa ei ole"
  [db user urakka hoitokauden-alkuvuosi pohjavesialue talvisuolaraja tie]
  (when (zero? (q/paivita-pohjavesialue-talvisuola! db talvisuolaraja urakka hoitokauden-alkuvuosi pohjavesialue tie))
    (q/tallenna-pohjavesialue-talvisuola<! db talvisuolaraja urakka hoitokauden-alkuvuosi pohjavesialue tie)))

(defn tallenna-suolasakko-ja-pohjavesialueet
  [db user {:keys [hoitokauden-alkuvuosi urakka suolasakko pohjavesialue-talvisuola] :as tiedot}]
  (log/debug"tallenna-suolasakko-ja-pohjavesialueet tiedot: " (pr-str tiedot))
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-suunnittelu-suola user urakka)
  (jdbc/with-db-transaction
    [db db]
    (let [suolasakon-id (tallenna-suolasakko db user urakka
                                             hoitokauden-alkuvuosi suolasakko)]
      (doseq [{:keys [pohjavesialue tie talvisuolaraja]} pohjavesialue-talvisuola]
        (tallenna-pohjavesialue-talvisuola db user urakka
                                           hoitokauden-alkuvuosi pohjavesialue talvisuolaraja tie)))

    (hae-urakan-suolasakot-ja-lampotilat db user urakka)))

(defrecord Lampotilat [ilmatieteenlaitos-url]
  component/Lifecycle
  (start [this]
    (let [http (:http-palvelin this)]
      (julkaise-palvelu http :hae-lampotilat-ilmatieteenlaitokselta
                        (fn [user {:keys [vuosi]}]
                          (hae-lampotilat-ilmatieteenlaitokselta (:db this) user ilmatieteenlaitos-url vuosi)))
      (julkaise-palvelu http :hae-teiden-hoitourakoiden-lampotilat
                        (fn [user {:keys [hoitokausi]}]
                          (hae-teiden-hoitourakoiden-lampotilat (:db this) user hoitokausi)))
      (julkaise-palvelu http :tallenna-teiden-hoitourakoiden-lampotilat
                        (fn [user tiedot]
                          (tallenna-teiden-hoitourakoiden-lampotilat (:db this) user tiedot)))
      (julkaise-palvelu http :hae-urakan-suolasakot-ja-lampotilat
                        (fn [user urakka-id]
                          (hae-urakan-suolasakot-ja-lampotilat (:db this) user urakka-id)))
      (julkaise-palvelu http :tallenna-suolasakko-ja-pohjavesialueet
                        (fn [user tiedot]
                          (tallenna-suolasakko-ja-pohjavesialueet (:db this) user tiedot)))
      this))

  (stop [this]
    (poista-palvelut (:http-palvelin this)
                     :hae-lampotilat-ilmatieteenlaitokselta
                     :hae-teiden-hoitourakoiden-lampotilat
                     :tallenna-teiden-hoitourakoiden-lampotilat
                     :hae-urakan-suolasakot-ja-lampotilat
                     :tallenna-suolasakko-ja-pohjavesialueet)
    this))
