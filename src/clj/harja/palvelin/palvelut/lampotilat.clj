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


(defn hae-lampotilat-ilmatieteenlaitokselta [db integraatioloki user url apiavain vuosi]
  (log/debug "hae-lampotilat-ilmatieteenlaitokselta, url " url " vuosi " vuosi)
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/hallinta-lampotilat user) ;; vaatii KIRJOITUS oikeuden
  (assert (and url vuosi) "Annettava url ja vuosi kun haetaan ilmatieteenlaitokselta lämpötiloja.")
  ;; Ilmatieteenlaitos käyttää :urakka-id -kentässään Harjan :alueurakkanro -kenttää, siksi muunnoksia alla
  (let [hae-urakoiden-lampotilat (fn [url apiavain keskiarvo-alkuvuosi]
                                   (into {}
                                         (map (juxt :urakka-id #(dissoc % :urakka-id)))
                                         (ilmatieteenlaitos/hae-talvikausi db integraatioloki url apiavain vuosi keskiarvo-alkuvuosi)))
        hoidon-urakoiden-lampotilat-1971-2000 (hae-urakoiden-lampotilat
                                                (str/replace url "tieindeksi2" "tieindeksi") apiavain nil)
        hoidon-urakoiden-lampotilat-1981-2010 (hae-urakoiden-lampotilat url apiavain 1981)
        ;; Ei voida hakea 91-20 keskiarvoa ennen vuotta 2021, koska keskiarvoa ei ole muodostettu.
        hoidon-urakoiden-lampotilat-1991-2020 (when (> vuosi 2020)
                                                (hae-urakoiden-lampotilat url apiavain 1991))
        hoidon-urakka-ja-alueurakkanro-avaimet
        (urakat/hae-aktiivisten-hoitourakoiden-alueurakkanumerot db vuosi)
        tulos (into {}
                    (comp
                      (map (fn [urakka]
                             (merge urakka
                               ;; Urakan tiedot löytyvät usein vain toisesta result setistä minkä FMI API palauttaa.
                               ;; Otetaan kaikki kentät siitä mikä löytyy,
                               ;; sekä varmistetaan vielä erikseen että uuden vertailukauden ka on avaimessa :keskilampotila-1981-2010
                               ;; ja vanhan vertailukauden avaimessa :keskilampotila-1971-2000
                               (or
                                 (get hoidon-urakoiden-lampotilat-1991-2020
                                   (:alueurakkanro urakka))
                                 (get hoidon-urakoiden-lampotilat-1981-2010
                                   (:alueurakkanro urakka))
                                 (get hoidon-urakoiden-lampotilat-1971-2000
                                   (:alueurakkanro urakka)))
                               {:keskilampotila-1981-2010 (:pitkakeskilampotila
                                                            (get hoidon-urakoiden-lampotilat-1981-2010
                                                              (:alueurakkanro urakka)))}
                               {:keskilampotila-1971-2000 (:pitkakeskilampotila
                                                            (get hoidon-urakoiden-lampotilat-1971-2000
                                                              (:alueurakkanro urakka)))}
                               {:keskilampotila-1991-2020 (:pitkakeskilampotila
                                                            (get hoidon-urakoiden-lampotilat-1991-2020
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
                        (:keskilampotila-1991-2020 lt)
                        (:keskilampotila-1981-2010 lt)
                        (:keskilampotila-1971-2000 lt)]]
        (if id
          (apply q/paivita-lampotila<! db (concat parametrit [id]))
          (apply q/uusi-lampotila<! db parametrit))))
    (hae-teiden-hoitourakoiden-lampotilat db user hoitokausi)))

(defn hae-urakan-suolasakot-ja-lampotilat
  [db user urakka-id]
  (log/debug "hae-urakan-suolasakot-ja-lampotilat")
  (oikeudet/vaadi-lukuoikeus oikeudet/urakat-toteumat-suola user urakka-id)
  (let [pitkakeskilampotila (if (<= (urakat/hae-urakan-alkuvuosi db urakka-id) 2014)
                              :keskilampotila-1971-2000
                              :keskilampotila-1981-2010)]
    {:suolasakot (into []
                       (map #(konv/decimal->double % :maara))
                       (q/hae-urakan-suolasakot db urakka-id))
     :lampotilat (into []
                       (comp
                        (map #(assoc % :keskilampotila-1981-2010
                                     (get % pitkakeskilampotila)))
                        (map #(konv/decimal->double % :keskilampotila))
                        (map #(konv/decimal->double % :keskilampotila-1981-2010)))
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
  (when (zero? (q/paivita-pohjavesialue-talvisuola! db talvisuolaraja (:id user) urakka hoitokauden-alkuvuosi pohjavesialue tie))
    (q/tallenna-pohjavesialue-talvisuola<! db talvisuolaraja urakka hoitokauden-alkuvuosi pohjavesialue tie (:id user))))

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

(defrecord Lampotilat [ilmatieteenlaitos-url apiavain]
  component/Lifecycle
  (start [this]
    (let [http (:http-palvelin this)
          db (:db this)
          integraatioloki (:integraatioloki this)]
      (julkaise-palvelu http :hae-lampotilat-ilmatieteenlaitokselta
                        (fn [user {:keys [vuosi]}]
                          (hae-lampotilat-ilmatieteenlaitokselta db integraatioloki user ilmatieteenlaitos-url apiavain vuosi)))
      (julkaise-palvelu http :hae-teiden-hoitourakoiden-lampotilat
                        (fn [user {:keys [hoitokausi]}]
                          (hae-teiden-hoitourakoiden-lampotilat db user hoitokausi)))
      (julkaise-palvelu http :tallenna-teiden-hoitourakoiden-lampotilat
                        (fn [user tiedot]
                          (tallenna-teiden-hoitourakoiden-lampotilat db user tiedot)))
      (julkaise-palvelu http :hae-urakan-suolasakot-ja-lampotilat
                        (fn [user urakka-id]
                          (hae-urakan-suolasakot-ja-lampotilat db user urakka-id)))
      (julkaise-palvelu http :tallenna-suolasakko-ja-pohjavesialueet
                        (fn [user tiedot]
                          (tallenna-suolasakko-ja-pohjavesialueet db user tiedot)))
      this))

  (stop [this]
    (poista-palvelut (:http-palvelin this)
                     :hae-lampotilat-ilmatieteenlaitokselta
                     :hae-teiden-hoitourakoiden-lampotilat
                     :tallenna-teiden-hoitourakoiden-lampotilat
                     :hae-urakan-suolasakot-ja-lampotilat
                     :tallenna-suolasakko-ja-pohjavesialueet)
    this))
