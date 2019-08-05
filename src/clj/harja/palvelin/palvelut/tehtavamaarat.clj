(ns harja.palvelin.palvelut.tehtavamaarat
  (:require [com.stuartsierra.component :as component]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelu]]
            [taoensso.timbre :as log]
            [clojure.set :refer [intersection difference]]
            [clojure.java.jdbc :as jdbc]
            [harja.id :refer [id-olemassa?]]
            [harja.kyselyt.tehtavamaarat :as q]
            [harja.kyselyt.urakat :as urakat-q]
            [harja.kyselyt.konversio :as konv]
            [harja.domain.oikeudet :as oikeudet]))


(defn hae-validit-tehtavat
  "Palauttaa tehtava-id:t niille tehtäville, joille teiden hoidon urakoissa (MHU) voi kirjata."
  [db]
  (into []
        (q/hae-validit-tehtava-idt db)))

(defn hae-tehtavamaarat
  "Palvelu, joka palauttaa urakan hoitokausikohtaiset tehtävämäärät."
  [db user {:keys [urakka-id hoitokauden-alkuvuosi]}]
  (oikeudet/vaadi-lukuoikeus oikeudet/urakat-suunnittelu-tehtava-ja-maaraluettelo user urakka-id)
  (into []
        (q/hae-hoitokauden-tehtavamaarat-urakassa db {:urakka     urakka-id
                                                      :hoitokausi hoitokauden-alkuvuosi})))
(defn hae-tehtavahierarkia
  [db user]
  ;;TODO: tarkista oikeudet (oikeudet/vaadi-lukuoikeus oikeudet/urakat-suunnittelu-tehtava-ja-maaraluettelo user urakka-id)
  (into []
        (q/hae-tehtavahierarkia db)))




(defn otsikko-rivi
  []

  )


(defn max-pair
  [v]
  (loop [i 0
         j 1
         result 0]
    (if (= i (dec (count v)))
      result
      (let [new-result (max result (+ (nth v i) (nth v j)))]
        (if (= j (dec (count v)))
          (recur (inc i) (-> i inc inc) new-result)
          (recur i (inc j) new-result))))))


(defn- jarjesta-tehtavahierarkia
  [hierarkia]

  ; rivi
  ; {:id "rivin-id-2" :nimi "Laajenna-valitaso" :tehtavaryhmatyyppi "valitaso" :maara 50 :vanhempi "rivin-id-1" :piillotettu? true}


  (let [rivinro 1
        otsikko nil
        tehtavalista (group-by :otsikko hierarkia)
        tehtavat-palauta []]


    (doseq [x *gtm]
      (let [cnt (atom 1)]
        (println "emo" (first x))
        (doseq [y(second x) ]
          (swap! cnt + 1)
          (println cnt "       lapsi " y)
          )))

    (map
      #(
         (join (join ({:id nil
                 :nimi (:otsikko %)
                 :tehtavaryhmataso :otsikko
                 :maara nil
                 :vanhempi nil}) tehtavat-palauta)


         )
      tehtavalista)


    (loop [osio tehtavalista]
      (println "otsikko " (first osio))
      (loop [tehtava (second osio)]
        (println "       tehtava " tehtava))

    ) )
  )




(defn hae-tehtavahierarkia-maarineen
  [db user {:keys [urakka-id hoitokauden-alkuvuosi]}]
  ;;TODO: tarkista oikeudet (oikeudet/vaadi-lukuoikeus oikeudet/urakat-suunnittelu-tehtava-ja-maaraluettelo user urakka-id)
  (into []
        (q/hae-tehtavahierarkia-maarineen db {:urakka     urakka-id
                                              :hoitokausi hoitokauden-alkuvuosi})))

(defn tallenna-tehtavamaarat
  "Palvelu joka tallentaa urakan hoitokauden tehtävämäärät."
  [db user {:keys [urakka-id hoitokauden-alkuvuosi tehtavamaarat]}]
  ;;TODO: tarkista oikeudet (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-suunnittelu-tehtava-ja-maaraluettelo user urakka-id)
  (assert (vector? tehtavamaarat) "tehtavamaarat tulee olla vektori")




  (let [urakkatyyppi (keyword (:tyyppi
                                (first (urakat-q/hae-urakan-tyyppi db urakka-id))))
        validit-tehtavat (hae-validit-tehtavat db)]

    (if-not (= urakkatyyppi :teiden-hoito)
      (throw (IllegalArgumentException. (str "Urakka " urakka-id " on tyyppiä: " urakkatyyppi ". Urakkatyypissä ei suunnitella tehtävä- ja määäräluettelon tietoja."))))

    (jdbc/with-db-transaction [c db]
                              (doseq [tm tehtavamaarat]
                                (let [nykyiset-arvot (hae-tehtavamaarat c user {:urakka-id             urakka-id
                                                                                :hoitokauden-alkuvuosi hoitokauden-alkuvuosi})
                                      tehtavamaara-avain (fn [rivi]
                                                           [(:hoitokauden-alkuvuosi rivi) (:tehtava-id rivi) (:urakka rivi)])
                                      tehtavamaarat-kannassa (into #{} (map tehtavamaara-avain nykyiset-arvot))
                                      parametrit [c {:urakka     urakka-id
                                                     :hoitokausi hoitokauden-alkuvuosi
                                                     :tehtava    (:tehtava-id tm)
                                                     :maara      (:maara tm)
                                                     :kayttaja   (:id user)}]]

                                  ;; TODO: Kaikki feilaa jos yksi feilaa. Olisiko parempi tallentaa ne mitkä voidaan.
                                  (when (empty?
                                          (filter #(= (:tehtava-id tm) (:tehtava-id %)) validit-tehtavat))
                                    (throw (IllegalArgumentException. (str "Tehtävälle " (:tehtava-id tm) " ei voi tallentaa määrätietoja."))))

                                  (if-not (tehtavamaarat-kannassa (tehtavamaara-avain (merge tm {:urakka                urakka-id
                                                                                                 :hoitokauden-alkuvuosi hoitokauden-alkuvuosi})))
                                    ;; insert
                                    (do
                                      (apply q/lisaa-tehtavamaara<! parametrit))
                                    ;;  update
                                    (do
                                      (apply q/paivita-tehtavamaara! parametrit)))))))

  (hae-tehtavahierarkia-maarineen db {:urakka     urakka-id
                                      :hoitokausi hoitokauden-alkuvuosi}))

(defrecord Tehtavamaarat []
  component/Lifecycle
  (start [this]
    (doto (:http-palvelin this)
      (julkaise-palvelu
        :tehtavahierarkia
        (fn [user]
          (hae-tehtavahierarkia (:db this) user)))
      (julkaise-palvelu
        :tehtavamaarat-hierarkiassa
        (fn [user tiedot]
          (hae-tehtavahierarkia-maarineen (:db this) user tiedot)))
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
    (poista-palvelu (:http-palvelin this) :tehtavahierarkia)
    (poista-palvelu (:http-palvelin this) :tehtavamaarat-hierarkiassa)
    (poista-palvelu (:http-palvelin this) :tehtavamaarat)
    (poista-palvelu (:http-palvelin this) :tallenna-tehtavamaarat)
    this))
