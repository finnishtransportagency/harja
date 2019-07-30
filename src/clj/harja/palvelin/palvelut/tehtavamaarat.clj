(ns harja.palvelin.palvelut.tehtavamaarat
  (:require [com.stuartsierra.component :as component]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelu]]
            [taoensso.timbre :as log]
            [clojure.set :refer [intersection difference]]
            [clojure.java.jdbc :as jdbc]
            [harja.id :refer [id-olemassa?]]
            [harja.kyselyt.tehtavamaarat :as q]
            [harja.kyselyt.konversio :as konv]
            [harja.domain.oikeudet :as oikeudet]))



(declare hae-tehtavamaarat tallenna-tehtavamaarat)


(def muutoshintaiset-xf
  (comp
    (map konv/alaviiva->rakenne)
    (map #(assoc %
            :yksikkohinta (if (:yksikkohinta %) (double (:yksikkohinta %)))))))


(defn hae-tehtavamaarat
  "Palvelu, joka palauttaa urakan hoitokausikohtaiset tehtävämäärät."
  [db user {:keys [urakka-id hoitokauden-aloitusvuosi]}]
 ;;TODO: tarkista oikeudet (oikeudet/vaadi-lukuoikeus oikeudet/urakat-suunnittelu-tehtava-ja-maaraluettelo user urakka-id)
  (into []
        muutoshintaiset-xf
        (q/hae-hoitokauden-tehtavamaarat-urakassa db {:urakka     urakka-id
                                                      :hoitokausi hoitokauden-aloitusvuosi})))

(defn tallenna-tehtavamaarat
  "Palvelu joka tallentaa urakan hoitokauden tehtävämäärät."
  [db user {:keys [urakka-id hoitokauden-aloitusvuosi tehtavamaarat]}]
  ;;TODO: tarkista oikeudet (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-suunnittelu-tehtava-ja-maaraluettelo user urakka-id)
  (assert (vector? tehtavamaarat) "tehtavamaarat tulee olla vektori")

  (jdbc/with-db-transaction [c db]
    (doseq [maara tehtavamaarat]
      (let [nykyiset-arvot (hae-tehtavamaarat c user [urakka-id hoitokauden-aloitusvuosi])
            tehtavamaara-avain (fn [rivi]
                                 [(:hoitokauden-alkuvuosi rivi) (:tehtava rivi) (:urakka rivi)])
            tehtavamaarat-kannassa (into #{} (map tehtavamaara-avain nykyiset-arvot))
            parametrit [c {:urakka     urakka-id
                           :hoitokausi hoitokauden-aloitusvuosi
                           :tehtava    (:tehtava maara)
                           :maara      (:maara maara)
                           :kayttaja   (:id user)}]]


        (if-not (tehtavamaarat-kannassa (tehtavamaara-avain maara))
          ;; insert
          (do
            (apply q/lisaa-tehtavamaara<! parametrit))
          ;;  update
          (do
            (apply q/paivita-tehtavamaara! parametrit))))))

  (q/hae-hoitokauden-tehtavamaarat-urakassa db {:urakka     urakka-id
                                               :hoitokausi hoitokauden-aloitusvuosi}))

(defrecord Tehtavamaarat []
  component/Lifecycle
  (start [this]
    (doto (:http-palvelin this)
      (julkaise-palvelu
        :tehtavamaarat
        (fn [user tiedot]
          (hae-tehtavamaarat (:db this) user tiedot)))
      (julkaise-palvelu
        :tallenna-tehtavamaarat
        (fn [user tiedot]
          (tallenna-tehtavamaarat (:db this) user tiedot))))
    this)

  (stop [this]
    (poista-palvelu (:http-palvelin this) :tehtavamaarat)
    (poista-palvelu (:http-palvelin this) :tallenna-tehtavamaarat)
    this))
