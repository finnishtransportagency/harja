(ns harja.palvelin.palvelut.yllapitokohteet.pot2
  "Tässä namespacessa on esitelty palvelut, jotka liittyvät erityisesti pot2"
  (:require [com.stuartsierra.component :as component]
            [taoensso.timbre :as log]
            [hiccup.core :refer [html]]
            [clojure.java.jdbc :as jdbc]
            [specql.core :refer [fetch update! insert! upsert! delete!]]

            [harja.kyselyt.pot2 :as pot2-q]
            [harja.domain
             [pot2 :as pot2-domain]
             [skeema :refer [Toteuma validoi] :as skeema]
             [oikeudet :as oikeudet]
             [muokkaustiedot :as muokkaustiedot]]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelut]]
            [harja.tyokalut.html :refer [sanitoi]]

            [harja.pvm :as pvm]
            [specql.core :as specql]
            [jeesql.core :refer [defqueries]]))

(defn- liita-sideaineet [db massat]
  (map #(assoc % ::pot2-domain/sideaineet (fetch db
                                                 ::pot2-domain/pot2-mk-massan-sideaine
                                                 #{:sideaine/id
                                                   ::pot2-domain/massa-id
                                                   :sideaine/tyyppi
                                                   :sideaine/pitoisuus
                                                   :sideaine/lopputuote?}
                                                 {::pot2-domain/massa-id (::pot2-domain/massa-id %)}))
       massat))

(defn- liita-lisaaineet [db massat]
  (map #(assoc % ::pot2-domain/lisaaineet (fetch db
                                                 ::pot2-domain/pot2-mk-massan-lisaaine
                                                 #{:lisaaine/id
                                                   ::pot2-domain/massa-id
                                                   :lisaaine/tyyppi
                                                   :lisaaine/pitoisuus}
                                                 {::pot2-domain/massa-id (::pot2-domain/massa-id %)}))
       massat))


(defn hae-urakan-massat-ja-murskeet [db user {:keys [urakka-id]}]
  (oikeudet/vaadi-lukuoikeus oikeudet/urakat-kohdeluettelo-paallystysilmoitukset user urakka-id)
  (let [_ (println "hae-urakan-pot2-massat :: urakka-id" (pr-str urakka-id))
        massat
        (->> (fetch db
                    ::pot2-domain/pot2-mk-urakan-massa
                    #{::pot2-domain/massa-id
                      ::pot2-domain/tyyppi
                      ::pot2-domain/nimen-tarkenne
                      ::pot2-domain/max-raekoko
                      ::pot2-domain/kuulamyllyluokka
                      ::pot2-domain/litteyslukuluokka
                      ::pot2-domain/dop-nro
                      [::pot2-domain/runkoaineet
                       #{:runkoaine/id
                         ::pot2-domain/massa-id
                         :runkoaine/tyyppi
                         :runkoaine/fillerityyppi
                         :runkoaine/esiintyma
                         :runkoaine/kuvaus
                         :runkoaine/kuulamyllyarvo
                         :runkoaine/litteysluku
                         :runkoaine/massaprosentti}]}
                    {::pot2-domain/urakka-id urakka-id
                     ::pot2-domain/poistettu? false})
             ;; Specql:ssä ominaisuus, ettei voi tehdä monta joinia samalla kyselyllä, siksi erilliset kyselyt, sorry
             (liita-sideaineet db)
             (liita-lisaaineet db))
        murskeet (fetch db
                        ::pot2-domain/pot2-mk-urakan-murske
                        #{::pot2-domain/murske-id
                          ::pot2-domain/nimen-tarkenne
                          ::pot2-domain/tyyppi
                          ::pot2-domain/tyyppi-tarkenne
                          ::pot2-domain/esiintyma
                          ::pot2-domain/lahde
                          ::pot2-domain/rakeisuus
                          ::pot2-domain/rakeisuus-tarkenne
                          ::pot2-domain/iskunkestavyys
                          ::pot2-domain/dop-nro}
                        {::pot2-domain/urakka-id urakka-id
                         ::pot2-domain/poistettu? false})
        _ (println "hae-urakan-massat-ja-murskeet :: massat" (pr-str massat))
        _ (println "hae-urakan-massat-ja-murskeet :: murskeet" (pr-str murskeet))]
    {:massat massat
     :murskeet murskeet}))

(defn hae-pot2-koodistot [db user {:keys []}]
  (oikeudet/ei-oikeustarkistusta!)
  (let [massatyypit (fetch db ::pot2-domain/pot2-mk-massatyyppi
                               (specql/columns ::pot2-domain/pot2-mk-massatyyppi)
                               {} {::specql/order-by ::pot2-domain/koodi})
        mursketyypit (fetch db ::pot2-domain/pot2-mk-mursketyyppi
                           (specql/columns ::pot2-domain/pot2-mk-mursketyyppi)
                           {} {::specql/order-by ::pot2-domain/koodi})
        runkoainetyypit (fetch db ::pot2-domain/pot2-mk-runkoainetyyppi
                           (specql/columns ::pot2-domain/pot2-mk-runkoainetyyppi)
                           {} {::specql/order-by ::pot2-domain/koodi})
        sideainetyypit (fetch db ::pot2-domain/pot2-mk-sideainetyyppi
                          (specql/columns ::pot2-domain/pot2-mk-sideainetyyppi)
                          {})
        lisaainetyypit (fetch db ::pot2-domain/pot2-mk-lisaainetyyppi
                              (specql/columns ::pot2-domain/pot2-mk-lisaainetyyppi)
                              {})
        alusta-toimenpiteet (fetch db ::pot2-domain/pot2-mk-alusta-toimenpide
                                          (specql/columns ::pot2-domain/pot2-mk-alusta-toimenpide)
                                          {})
        paallystekerros-toimenpiteet (fetch db ::pot2-domain/pot2-mk-paallystekerros-toimenpide
                                          (specql/columns ::pot2-domain/pot2-mk-paallystekerros-toimenpide)
                                          {})
        verkon-sijainnit (fetch db ::pot2-domain/pot2-verkon-sijainti
                                          (specql/columns ::pot2-domain/pot2-verkon-sijainti)
                                          {})
        verkon-tarkoitukset (fetch db ::pot2-domain/pot2-verkon-tarkoitus
                                (specql/columns ::pot2-domain/pot2-verkon-tarkoitus)
                                {})
        verkon-tyypit (fetch db ::pot2-domain/pot2-verkon-tyyppi
                                (specql/columns ::pot2-domain/pot2-verkon-tyyppi)
                                {})
        koodistot {:massatyypit massatyypit
                   :mursketyypit mursketyypit
                   :runkoainetyypit runkoainetyypit
                   :sideainetyypit sideainetyypit
                   :lisaainetyypit lisaainetyypit
                   :alusta-toimenpiteet alusta-toimenpiteet
                   :paallystekerros-toimenpiteet paallystekerros-toimenpiteet
                   :verkon-sijainnit verkon-sijainnit
                   :verkon-tarkoitukset verkon-tarkoitukset
                   :verkon-tyypit verkon-tyypit
                   }]
    koodistot))

(defn- tallenna-runkoaineet
  [db runkoaineet massa-id]
  (let [paluuarvo (atom [])]
    (doseq [[ra-tyyppi r] runkoaineet]
      (let [runkoaine-id (:runkoaine/id r)
            runkoaine (if (:valittu? r)
                        (upsert! db ::pot2-domain/pot2-mk-massan-runkoaine
                                 (merge
                                   {::pot2-domain/massa-id massa-id
                                    :runkoaine/tyyppi ra-tyyppi
                                    :runkoaine/esiintyma (:runkoaine/esiintyma r)
                                    :runkoaine/kuulamyllyarvo (when (:runkoaine/kuulamyllyarvo r)
                                                                (bigdec (:runkoaine/kuulamyllyarvo r)))
                                    :runkoaine/litteysluku (when (:runkoaine/litteysluku r)
                                                             (bigdec (:runkoaine/litteysluku r)))
                                    :runkoaine/massaprosentti (:runkoaine/massaprosentti r)
                                    :runkoaine/fillerityyppi (:runkoaine/fillerityyppi r)}
                                   (when (:runkoaine/kuvaus r)
                                     {:runkoaine/kuvaus (:runkoaine/kuvaus r)})
                                   (when runkoaine-id
                                     {:runkoaine/id runkoaine-id})))
                        ;; jos valittu? = false, kyseessä voi olla olemassaolevan runkoainetyypin poistaminen
                        (when (int? runkoaine-id)
                          (delete! db ::pot2-domain/pot2-mk-massan-runkoaine {:runkoaine/id runkoaine-id})))]
        (swap! paluuarvo conj runkoaine)))
    @paluuarvo))

(defn- iteroi-sideaineet
  [db aineet-map kayttotapa massa-id]
  (let [paluuarvot (atom [])
        {valittu? :valittu?
         aineet :aineet} aineet-map
        ;; UI:lta voi poistaa lisättyjä sideaineita siten että poistetut eivät tule mukaan payloadiin
        ;; tarkistetaan ensin kannasta, onko sellaisia ja jos ne puuttuvat payloadista, poistetaan
        ennestaan-kannassa-olevien-idt (into #{}
                                             (map :sideaine/id
                                                  (fetch db ::pot2-domain/pot2-mk-massan-sideaine
                                                         #{:sideaine/id}
                                                         {::pot2-domain/massa-id massa-id
                                                          :sideaine/lopputuote? (boolean (= kayttotapa :lopputuote))})))
        idt-jotka-poistettu-uilta (clojure.set/difference ennestaan-kannassa-olevien-idt (into #{}
                                                                                     (map :sideaine/id
                                                                                          (vals aineet))))]
    (doseq [poistettavan-id idt-jotka-poistettu-uilta]
      (when (int? poistettavan-id)
        (delete! db ::pot2-domain/pot2-mk-massan-sideaine {:sideaine/id poistettavan-id
                                                       ::pot2-domain/massa-id massa-id})))
    (doseq [[_ {:sideaine/keys [id tyyppi pitoisuus]}] aineet]
      (let [paluurivi (if valittu?
                        (upsert! db ::pot2-domain/pot2-mk-massan-sideaine
                                 (merge
                                   {::pot2-domain/massa-id massa-id
                                    :sideaine/tyyppi tyyppi
                                    :sideaine/pitoisuus (bigdec pitoisuus)
                                    :sideaine/lopputuote? (boolean (= kayttotapa :lopputuote))}
                                   (when id
                                     {:sideaine/id id})))
                        ;; jos valittu? = false, kyseessä voi olla olemassaolevan sideainetyypin poistaminen
                        (when (int? id)
                          (delete! db ::pot2-domain/pot2-mk-massan-sideaine {:sideaine/id id
                                                                         ::pot2-domain/massa-id massa-id})))]
        (swap! paluuarvot conj paluurivi)))
    @paluuarvot))

(defn- tallenna-sideaineet
  [db sideaineet massa-id]
  (let [lopputuotteen-sideaineet (:lopputuote sideaineet)
        lisatyt-sideaineet (:lisatty sideaineet)
        lopputuotteen-paluuarvot (iteroi-sideaineet db lopputuotteen-sideaineet
                                                    :lopputuote
                                                    massa-id)
        lisattyjen-paluuarvot (iteroi-sideaineet db lisatyt-sideaineet
                                                 :lisatty
                                                 massa-id)]
    {:lopputuote lopputuotteen-paluuarvot
     :lisatty lisattyjen-paluuarvot}))

(defn- tallenna-lisaaineet
  [db lisaaineet massa-id]
  (let [paluuarvo (atom [])]
    (doseq [[la-tyyppi {:lisaaine/keys [id pitoisuus]
                        valittu? :valittu?}] lisaaineet]
      (let [lisaaine (if valittu?
                       (upsert! db ::pot2-domain/pot2-mk-massan-lisaaine
                                (merge
                                  {::pot2-domain/massa-id massa-id
                                   :lisaaine/tyyppi la-tyyppi
                                   :lisaaine/pitoisuus (bigdec pitoisuus)}
                                  (when id
                                    {:lisaaine/id id})))
                       ;; jos valittu? = false, kyseessä voi olla olemassaolevan lisaainetyypin poistaminen
                       (when (int? id)
                         (delete! db ::pot2-domain/pot2-mk-massan-lisaaine {:lisaaine/id id
                                                                        ::pot2-domain/massa-id massa-id})))]
        (swap! paluuarvo conj lisaaine)))
    @paluuarvo))

(defn tallenna-urakan-massa
  [db user {::pot2-domain/keys [runkoaineet sideaineet lisaaineet urakka-id] :as tiedot}]
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-kohdeluettelo-paallystysilmoitukset user urakka-id)
  (jdbc/with-db-transaction
    [db db]
    (let [massa-id (::pot2-domain/massa-id tiedot)
          massa (upsert! db ::pot2-domain/pot2-mk-urakan-massa
                         (merge
                           (if massa-id
                             {::pot2-domain/massa-id massa-id
                              ::muokkaustiedot/muokattu (pvm/nyt)
                              ::muokkaustiedot/muokkaaja-id (:id user)
                              ::pot2-domain/poistettu? (boolean (::pot2-domain/poistettu? tiedot))}
                             {::muokkaustiedot/luotu (pvm/nyt)
                              ::muokkaustiedot/luoja-id (:id user)})
                           (select-keys tiedot [::pot2-domain/urakka-id
                                                ::pot2-domain/nimen-tarkenne
                                                ::pot2-domain/tyyppi
                                                ::pot2-domain/max-raekoko
                                                ::pot2-domain/kuulamyllyluokka
                                                ::pot2-domain/litteyslukuluokka
                                                ::pot2-domain/dop-nro])))
          _ (println "tallenna-urakan-paallystysmassa :: massa" (pr-str massa))
          massa-id (::pot2-domain/massa-id massa)
          runkoaineet-kannasta (tallenna-runkoaineet db runkoaineet massa-id)
          _ (println "tallenna-urakan-paallystysmassa :: runkoaineet-kannasta" (pr-str runkoaineet-kannasta))

          sideaineet-kannasta (tallenna-sideaineet db sideaineet massa-id)
          _ (println "tallenna-urakan-paallystysmassa :: sideaineet-kannasta" (pr-str sideaineet-kannasta))

          lisaaineet-kannasta (tallenna-lisaaineet db lisaaineet massa-id)
          _ (println "tallenna-urakan-paallystysmassa :: lisaaineet-kannasta" (pr-str lisaaineet-kannasta))]
      (assoc massa :harja.domain.pot2/runkoaineet runkoaineet
                   :harja.domain.pot2/sideaineet sideaineet
                   :harja.domain.pot2/lisaaineet lisaaineet))))

(defn- validoi-murske [db murske poistettu?]
  (let [muun-tyypin-id (::pot2-domain/koodi
                         (first (fetch db
                                       ::pot2-domain/pot2-mk-mursketyyppi
                                       #{::pot2-domain/koodi}
                                       {::pot2-domain/nimi "Muu"})))]
    ;; poistettua ei haluta validoida koska se joka tapauksessa poistetaan
    (when-not poistettu?
      (assert (or (not= muun-tyypin-id (::pot2-domain/tyyppi murske))
                  (and (= muun-tyypin-id (::pot2-domain/tyyppi murske))
                       (some? (::pot2-domain/tyyppi-tarkenne murske)))) "Tyyppi annettu tai muulla tyypillä tarkenne")
      (assert (or (not= "Muu" (::pot2-domain/rakeisuus murske))
                  (some? (::pot2-domain/rakeisuus-tarkenne murske))) "Rakeisuus annettu tai muulla rakeisuudella tarkenne"))))

(defn- mursketyypin-sarakkeet [db tyyppi]
  (let [mursketyypit (fetch db ::pot2-domain/pot2-mk-mursketyyppi
                            #{::pot2-domain/koodi ::pot2-domain/lyhenne}
                            {})
        lyhenne (pot2-domain/ainetyypin-koodi->lyhenne mursketyypit tyyppi)]
    (pot2-domain/mursketyypin-lyhenne->sarakkeet lyhenne)))

(defn tallenna-urakan-murske
  [db user {::pot2-domain/keys [urakka-id poistettu? murske-id] :as tiedot}]
  (println "tallenna-urakan-murske: "(pr-str tiedot))
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-kohdeluettelo-paallystysilmoitukset user urakka-id)
  (validoi-murske db tiedot poistettu?)
  (jdbc/with-db-transaction
    [db db]
    (let [murske (upsert! db ::pot2-domain/pot2-mk-urakan-murske
                         (merge
                           (if murske-id
                             {::pot2-domain/murske-id murske-id
                              ::muokkaustiedot/muokattu (pvm/nyt)
                              ::muokkaustiedot/muokkaaja-id (:id user)
                              ::pot2-domain/poistettu? (boolean poistettu?)}
                             {::muokkaustiedot/luotu (pvm/nyt)
                              ::muokkaustiedot/luoja-id (:id user)})
                           (select-keys tiedot (mursketyypin-sarakkeet db (::pot2-domain/tyyppi tiedot)))))
          _ (println "tallenna-urakan-paallystysmurske onnistui, palautetaan:" (pr-str murske))]
      murske)))

(defrecord POT2 []
  component/Lifecycle
  (start [this]
    (let [http (:http-palvelin this)
          db (:db this)
          fim (:fim this)
          email (:sonja-sahkoposti this)]

      (julkaise-palvelu http :hae-urakan-massat-ja-murskeet
                        (fn [user tiedot]
                          (hae-urakan-massat-ja-murskeet db user tiedot)))
      (julkaise-palvelu http :hae-pot2-koodistot
                        (fn [user tiedot]
                          (hae-pot2-koodistot db user tiedot)))
      (julkaise-palvelu http :tallenna-urakan-massa
                        (fn [user tiedot]
                          (tallenna-urakan-massa db user tiedot)))
      (julkaise-palvelu http :tallenna-urakan-murske
                        (fn [user tiedot]
                          (tallenna-urakan-murske db user tiedot)))
      ;; POT2 liittyviä palveluita myös harja.palvelin.palvelut.yllapitokohteet.paallystys ns:ssä
      this))

  (stop [this]
    (poista-palvelut
      (:http-palvelin this)
      :hae-urakan-massat-ja-murskeet
      :hae-pot2-koodistot
      :tallenna-urakan-massa
      :tallenna-urakan-murske)
    this))