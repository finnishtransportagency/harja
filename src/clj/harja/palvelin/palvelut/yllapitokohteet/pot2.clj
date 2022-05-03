(ns harja.palvelin.palvelut.yllapitokohteet.pot2
  "Tässä namespacessa on esitelty palvelut, jotka liittyvät erityisesti pot2"
  (:require [com.stuartsierra.component :as component]
            [taoensso.timbre :as log]
            [hiccup.core :refer [html]]
            [clojure.java.jdbc :as jdbc]
            [specql.core :refer [fetch update! insert! upsert! delete!]]
            [harja.palvelin.asetukset :refer [ominaisuus-kaytossa?]]
            [harja.kyselyt.paallystys-kyselyt :as paallystys-q]
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

(defn- liita-materiaalin-kayttotieto
  "Liittää tiedon missä urakan kohteissa massaa tai mursketta on jo käytetty (POT:ien mukaan), if any"
  [db tyyppi materiaalit]
  (mapv #(assoc % ::pot2-domain/kaytossa
                  (case tyyppi
                    :murske
                    (paallystys-q/murskeen-kayttotiedot db {:id (::pot2-domain/murske-id %)})

                    :massa
                    (paallystys-q/massan-kayttotiedot db {:id (::pot2-domain/massa-id %)})

                    nil))
        materiaalit))

(defn hae-urakan-massat-ja-murskeet [db user {:keys [urakka-id]}]
  (oikeudet/vaadi-lukuoikeus oikeudet/urakat-kohdeluettelo-paallystysilmoitukset user urakka-id)
  (let [massat
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
             (liita-lisaaineet db)
             (liita-materiaalin-kayttotieto db :massa))
        murskeet (->> (fetch db
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
                      (liita-materiaalin-kayttotieto db :murske))]
    {:massat massat
     :murskeet murskeet}))

(defn hae-pot2-koodistot [db user {:keys []}]
  (oikeudet/ei-oikeustarkistusta!)
  (let [massatyypit (fetch db ::pot2-domain/pot2-mk-massatyyppi
                               (specql/columns ::pot2-domain/pot2-mk-massatyyppi)
                               {} {::specql/order-by ::pot2-domain/jarjestys})
        mursketyypit (fetch db ::pot2-domain/pot2-mk-mursketyyppi
                           (specql/columns ::pot2-domain/pot2-mk-mursketyyppi)
                           {} {::specql/order-by ::pot2-domain/koodi})
        runkoainetyypit (fetch db ::pot2-domain/pot2-mk-runkoainetyyppi
                           (specql/columns ::pot2-domain/pot2-mk-runkoainetyyppi)
                           {} {::specql/order-by ::pot2-domain/koodi})
        sideainetyypit (fetch db ::pot2-domain/pot2-mk-sideainetyyppi
                          (specql/columns ::pot2-domain/pot2-mk-sideainetyyppi)
                          {})
        sidotun-kantavan-kerroksen-sideaine (fetch db ::pot2-domain/pot2-mk-sidotun-kantavan-kerroksen-sideaine
                                                   (specql/columns ::pot2-domain/pot2-mk-sidotun-kantavan-kerroksen-sideaine)
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
                   :sidotun-kantavan-kerroksen-sideaine sidotun-kantavan-kerroksen-sideaine
                   :lisaainetyypit lisaainetyypit
                   :alusta-toimenpiteet alusta-toimenpiteet
                   :paallystekerros-toimenpiteet paallystekerros-toimenpiteet
                   :verkon-sijainnit verkon-sijainnit
                   :verkon-tarkoitukset verkon-tarkoitukset
                   :verkon-tyypit verkon-tyypit}]
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
                                    :runkoaine/massaprosentti (when (:runkoaine/massaprosentti r)
                                                                (bigdec (:runkoaine/massaprosentti r)))
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
          massa-id (::pot2-domain/massa-id massa)
          runkoaineet-kannasta (tallenna-runkoaineet db runkoaineet massa-id)
          sideaineet-kannasta (tallenna-sideaineet db sideaineet massa-id)
          lisaaineet-kannasta (tallenna-lisaaineet db lisaaineet massa-id)]
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

(defn hae-muut-urakat-joissa-materiaaleja
  [db user {:keys [urakka-id]}]
  (oikeudet/vaadi-lukuoikeus oikeudet/urakat-kohdeluettelo-paallystysilmoitukset user urakka-id)
  (let [organisaatiot-jossa-rooleja (keys (:organisaatioroolit user))
        urakat-jossa-rooleja (keys (:urakkaroolit user))
        jarjestelmavastaava? (contains? (:roolit user) "Jarjestelmavastaava")]
    (paallystys-q/muut-urakat-joissa-materiaaleja db {:valittu_urakka urakka-id
                                                      :urakat urakat-jossa-rooleja
                                                      :organisaatiot organisaatiot-jossa-rooleja
                                                      :jarjestelmavastaava jarjestelmavastaava?})))

(defn paivita-tarkennetta
  "Päivittää massan/murskeen tarkennetta, käyttämällä tarkenteen perässä juoksevaa numerointia."
  [tarkenne]
  (if (empty? tarkenne)
    " 2"
    (let [tarkenteen-viimeinen-merkki (subs tarkenne (- (count tarkenne) 1))
          uusi-viimeinen-merkki (try
                                  ;; korvataan viimeisin merkki (numero) yhden isommalla numerolla
                                  (str (subs tarkenne 0 (- (count tarkenne) 1))
                                       (+ 1 (Integer/parseInt tarkenteen-viimeinen-merkki)))
                                  (catch Exception e
                                    ;; viimeisin ei ollut numero, joten lisätään tarkenteeseen 2
                                    (str tarkenne " 2")))]
      uusi-viimeinen-merkki)))

(defn tuo-materiaalit-toisesta-urakasta
  "Monistaa valitut massat ja murskeet toisesta käyttäjän urakasta."
  [db user {:keys [urakka-id massa-idt murske-idt]}]
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-kohdeluettelo-paallystysilmoitukset user urakka-id)
  (jdbc/with-db-transaction
    [db db]

    (doseq [massa-id massa-idt
            :let [uusi-massa (paallystys-q/monista-massa<! db {:id massa-id
                                                               :kayttaja (:id user)
                                                               :urakka_id urakka-id})
                  samannimiset-massat-urakassa (paallystys-q/hae-samannimiset-massat-urakasta db {:tyyppi (:tyyppi uusi-massa)
                                                                                                  :max_raekoko (:max_raekoko uusi-massa)
                                                                                                  :nimen_tarkenne (:nimen_tarkenne uusi-massa)
                                                                                                  :dop_nro (:dop_nro uusi-massa)
                                                                                                  :urakka_id urakka-id
                                                                                                  :id (:id uusi-massa)})]]
      (when-not (empty? samannimiset-massat-urakassa)
        ;; etsitään kannassaolevista samannimisistä massoista se, jonka tarkenne on "viimeisin" juoksevassa numeroinnissa
        (let [viimeisin-tarkenne (paallystys-q/hae-samannimisten-massojen-viimeisin-tarkenne db {:tyyppi (:tyyppi uusi-massa)
                                                                                                      :max_raekoko (:max_raekoko uusi-massa)
                                                                                                      :dop_nro (:dop_nro uusi-massa)
                                                                                                      :urakka_id urakka-id
                                                                                                      :id (:id uusi-massa)})]
(paallystys-q/paivita-massan-nimen-tarkennetta<! db {:id (:id uusi-massa)
                                                               :urakka_id urakka-id
                                                               :nimen_tarkenne (paivita-tarkennetta viimeisin-tarkenne)})))
      ;; jos löytyi samannimisiä massoja urakassa, niin lisätään tarkenteeseen juoksevalla numeroinnilla 2, 3, 4, ...
      ;; jotta käyttäjä voi erottaa ne helpommin käyttöliittmässä. Tilanne voi syntyä jos käyttäjä tuo saman materiaalin kahteen
      ;; kertaan
      (paallystys-q/monista-massan-runkoaineet<! db {:vanha_pot2_massa_id massa-id
                                                     :uusi_pot2_massa_id (:id uusi-massa)})
      (paallystys-q/monista-massan-sideaineet<! db {:vanha_pot2_massa_id massa-id
                                                    :uusi_pot2_massa_id (:id uusi-massa)})
      (paallystys-q/monista-massan-lisaaineet<! db {:vanha_pot2_massa_id massa-id
                                                    :uusi_pot2_massa_id (:id uusi-massa)}))

    (doseq [murske-id murske-idt
            :let [uusi-murske (paallystys-q/monista-murske<! db {:id murske-id
                                                                 :kayttaja (:id user)
                                                                 :urakka_id urakka-id})
                  samannimiset-murskeet-urakassa (paallystys-q/hae-samannimiset-murskeet-urakasta db {:tyyppi (:tyyppi uusi-murske)
                                                                                                      :nimen_tarkenne (:nimen_tarkenne uusi-murske)
                                                                                                      :dop_nro (:dop_nro uusi-murske)
                                                                                                      :urakka_id urakka-id
                                                                                                      :id (:id uusi-murske)})]]
      (when-not (empty? samannimiset-murskeet-urakassa)
        ;; etsitään kannassaolevista samannimisistä massoista se, jonka tarkenne on "viimeisin" juoksevassa numeroinnissa
        (let [viimeisin-tarkenne (paallystys-q/hae-samannimisten-murskeiden-viimeisin-tarkenne db {:tyyppi (:tyyppi uusi-murske)
                                                                                                 :dop_nro (:dop_nro uusi-murske)
                                                                                                 :urakka_id urakka-id
                                                                                                 :id (:id uusi-murske)})]
          (paallystys-q/paivita-murskeen-nimen-tarkennetta<! db {:id (:id uusi-murske)
                                                                 :urakka_id urakka-id
                                                                 :nimen_tarkenne (paivita-tarkennetta viimeisin-tarkenne)}))))

    ;; Palautetaan käyttäjälle tuoreet materiaalit kannasta monistamisen jälkeen
    (hae-urakan-massat-ja-murskeet db user {:urakka-id urakka-id})))

(defn poista-urakan-massa
  [db user {:keys [id]}]
  (jdbc/with-db-transaction
    [db db]
    (let [massan-urakka-id (paallystys-q/hae-massan-urakka-id db {:id id})
          _ (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-kohdeluettelo-paallystysilmoitukset user massan-urakka-id)
          kaytossa? (paallystys-q/massan-kayttotiedot db {:id id})]
      (if-not (empty? kaytossa?)
        (throw (SecurityException. (str "Et voi poistaa käytössäolevaa massaa, massan id: " id)))
        (paallystys-q/poista-urakan-massa<! db {:id id
                                               :urakka_id massan-urakka-id}))

      (hae-urakan-massat-ja-murskeet db user {:urakka-id massan-urakka-id}))))

(defn poista-urakan-murske
  [db user {:keys [id]}]
  (jdbc/with-db-transaction
    [db db]
    (let [murskeen-urakka-id (paallystys-q/hae-murskeen-urakka-id db {:id id})
          _ (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-kohdeluettelo-paallystysilmoitukset user murskeen-urakka-id)
          kaytossa? (paallystys-q/murskeen-kayttotiedot db {:id id})]
      (if-not (empty? kaytossa?)
        (throw (SecurityException. (str "Et voi poistaa käytössäolevaa mursketta, murskeen id: " id)))
        (paallystys-q/poista-urakan-murske<! db {:id id
                                                 :urakka_id murskeen-urakka-id}))

      (hae-urakan-massat-ja-murskeet db user {:urakka-id murskeen-urakka-id}))))

(defrecord POT2 []
  component/Lifecycle
  (start [this]
    (let [http (:http-palvelin this)
          db (:db this)
          fim (:fim this)
          email (if (ominaisuus-kaytossa? :sonja-sahkoposti)
                  (:sonja-sahkoposti this)
                  (:api-sahkoposti this))]

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
      (julkaise-palvelu http :hae-muut-urakat-joissa-materiaaleja
                        (fn [user tiedot]
                          (hae-muut-urakat-joissa-materiaaleja db user tiedot)))
      (julkaise-palvelu http :tuo-materiaalit-toisesta-urakasta
                        (fn [user tiedot]
                          (tuo-materiaalit-toisesta-urakasta db user tiedot)))
      (julkaise-palvelu http :poista-urakan-massa
                        (fn [user tiedot]
                          (poista-urakan-massa db user tiedot)))
      (julkaise-palvelu http :poista-urakan-murske
                        (fn [user tiedot]
                          (poista-urakan-murske db user tiedot)))
      ;; POT2 liittyviä palveluita myös harja.palvelin.palvelut.yllapitokohteet.paallystys ns:ssä
      this))

  (stop [this]
    (poista-palvelut
      (:http-palvelin this)
      :hae-urakan-massat-ja-murskeet
      :hae-pot2-koodistot
      :tallenna-urakan-massa
      :tallenna-urakan-murske
      :hae-muut-urakat-joissa-materiaaleja
      :tuo-materiaalit-toisesta-urakasta
      :poista-urakan-massa
      :poista-urakan-murske)
    this))
