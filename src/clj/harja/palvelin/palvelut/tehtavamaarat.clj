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
  "Palauttaa urakan hoitokausikohtaiset tehtävämäärät."
  [db user {:keys [urakka-id hoitokauden-alkuvuosi]}]
  (oikeudet/vaadi-lukuoikeus oikeudet/urakat-suunnittelu-tehtava-ja-maaraluettelo user urakka-id)
  (into []
        (q/hae-hoitokauden-tehtavamaarat-urakassa db {:urakka     urakka-id
                                                      :hoitokausi hoitokauden-alkuvuosi})))
(defn hae-tehtavahierarkia
  "Palauttaa tehtävähierarkian kokonaisuudessaan ilman urakkaan liittyviä tietoja."
  [db user]
  (into []
        (q/hae-tehtavahierarkia db)))

(defn- jarjesta-tehtavahierarkia
  "Järjestää tehtävähierarkian käyttöliittymän (Suunnittelu > Tehtävä- ja määräluettelo) tarvitsemaan muotoon.
  Suunnitteluosiossa ei tehtävähierarkian tasoilla (ylä-, väli- ja alataso) ole merkitystä. Tasoja käytetään budjettiseurannassa.
  Suunnittelussa tehtävähierarkia muodostuu sopimuksen liitteen mukaisista otsikkoriveistä sekä niiden alle jakautuvista tehtäväriveistä.
  Käyttäjä syöttää suunnitellut määrät tehtäväriveille. Käytä tehtävän id:tä tunnisteena, kun tallennat tiedot tietokantaan."
  [hierarkia]

  ;; [{:id "1" :nimi "1.0 TALVIHOITO" :tehtavaryhmatyyppi "otsikko" :piillotettu? false}
  ;; {:id "2" :tehtava-id 4548 :nimi "Ise 2-ajorat." :tehtavaryhmatyyppi "tehtava" :maara 50 :vanhempi "1" :piillotettu? false}
  ;; {:id "3" :nimi "2.1 LIIKENNEYMPÄRISTÖN HOITO" :tehtavaryhmatyyppi "otsikko" :piillotettu? false}
  ;; {:id "4" :tehtava-id 4565 :nimi "Liikennemerkkien ja opasteiden kunnossapito (oikominen, pesu yms.)" :tehtavaryhmatyyppi "tehtava" :maara 50 :vanhempi "3" :piillotettu? false}
  ;; {:id "5" :tehtava-id 4621  :nimi "Opastustaulun/-viitan uusiminen" :tehtavaryhmatyyppi "tehtava" :maara 50 :vanhempi "3" :piillotettu? false}]

  ;; TODO: Muodosta palautettavat tiedot. Vrt. println tulostukset.

  (let [cnt (atom 1)
        tulos (atom [])
        toimenpiteet (atom #{})
        tehtavahierarkia (sort-by first (group-by :otsikko hierarkia))] ;; Ryhmitelty hierarkia sisältää otsikot (first) ja niiden alle kuuluvat tehtävärivit (second)
    (doseq [rivi tehtavahierarkia]
      (let [emo (Long/valueOf @cnt)
            otsikko (first rivi)
            tehtavalista (second rivi)
            toimenpide (-> otsikko
                           (clojure.string/split #" " 2)
                           (second)
                           (clojure.string/replace " " "_")
                           (clojure.string/replace "Ä" "A")
                           (clojure.string/replace "Ö" "O")
                           (keyword))]
        ;; TODO: Muodosta otsikkotyyppinen rivi
        (swap! toimenpiteet conj {:id   toimenpide
                                  :nimi (-> otsikko
                                            (clojure.string/split #" " 2)
                                            (second))})
        (swap! tulos conj {:id @cnt
                           :tehtavaryhmatyyppi "otsikko"
                           :nimi otsikko
                           :piillotettu? false
                           :toimenpide toimenpide})
        (doseq [{:keys [tehtava-id tehtava maara yksikko hoitokauden-alkuvuosi urakka] :as teht} tehtavalista]
          (swap! cnt + 1)
          (swap! tulos conj {:id                 @cnt
                             :tehtava-id         tehtava-id
                             :tehtavaryhmatyyppi "tehtava"
                             :nimi               tehtava
                             :maara              (if (nil? maara) 0 maara)
                             :yksikko            yksikko
                             :vanhempi           emo
                             :hoitokauden-alkuvuosi hoitokauden-alkuvuosi
                             :urakka             urakka
                             :piillotettu?       false})
          ;; TODO: Muodosta tehtävätyyppinen rivi
         #_(println "{:id" @cnt ":tehtava-id" tehtava-id ":nimi" tehtava ":tehtavaryhmatyyppi tehtava :yksikko " yksikko " :maara" maara ":vanhempi" emo ":piillotettu? false :urakka}" urakka " :hoitikausi " hoitokauden-alkuvuosi))))
    (reduce #(conj %1 (assoc %2 :tehtavaryhmatyyppi "toimenpide"
                                :piillotettu? false)) @tulos @toimenpiteet)))

(defn hae-tehtavahierarkia-maarineen
  "Palauttaa tehtävähierarkian otsikko- ja tehtävärivit Suunnittelu > Tehtävä- ja määräluettelo-näkymää varten."
  [db user {:keys [urakka-id hoitokauden-alkuvuosi]}]
  (oikeudet/vaadi-lukuoikeus oikeudet/urakat-suunnittelu-tehtava-ja-maaraluettelo user urakka-id)
  (jarjesta-tehtavahierarkia
    (q/hae-tehtavahierarkia-maarineen db {:urakka     urakka-id
                                          :hoitokausi hoitokauden-alkuvuosi})))

(defn tallenna-tehtavamaarat
  "Luo tai päivittää urakan hoitokauden tehtävämäärät."
  [db user {:keys [urakka-id hoitokauden-alkuvuosi tehtavamaarat]}]
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-suunnittelu-tehtava-ja-maaraluettelo user urakka-id)
  (let [urakkatyyppi (keyword (:tyyppi (first (urakat-q/hae-urakan-tyyppi db urakka-id))))
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
                                  ;; TODO: Kaikki feilaa jos yksi feilaa. Olisiko parempi tallentaa ne mitkä voidaan?
                                  (when (empty?
                                          (filter #(= (:tehtava-id tm) (:tehtava-id %)) validit-tehtavat))
                                    (throw (IllegalArgumentException. (str "Tehtävälle " (:tehtava-id tm) " ei voi tallentaa määrätietoja."))))

                                  #_(println (tehtavamaarat-kannassa (tehtavamaara-avain (merge tm {:urakka                urakka-id
                                                                                                  :hoitokauden-alkuvuosi hoitokauden-alkuvuosi}))))

                                  (if-not (tehtavamaarat-kannassa (tehtavamaara-avain (merge tm {:urakka                urakka-id
                                                                                                 :hoitokauden-alkuvuosi hoitokauden-alkuvuosi})))
                                    ;; insert
                                    (do
                                      (apply q/lisaa-tehtavamaara<! parametrit))
                                    ;;  update
                                    (do
                                      (apply q/paivita-tehtavamaara! parametrit)))))))

  (hae-tehtavahierarkia-maarineen db user {:urakka-id     urakka-id
                                           :hoitokauden-alkuvuosi hoitokauden-alkuvuosi}))

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
