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

(defn hae-urakan-pot2-massat [db user {:keys [urakka-id]}]
  (oikeudet/vaadi-lukuoikeus oikeudet/urakat-kohdeluettelo-paallystysilmoitukset user urakka-id)
  (let [_ (println "hae-urakan-pot2-massat :: urakka-id" (pr-str urakka-id))
        massat
        (fetch db
               ::pot2-domain/pot2-massa
               #{:pot2-massa/id
                 ::pot2-domain/tyyppi
                 ::pot2-domain/nimen-tarkenne
                 ::pot2-domain/max-raekoko
                 ::pot2-domain/kuulamyllyluokka
                 ::pot2-domain/litteyslukuluokka
                 ::pot2-domain/dop-nro
                 [::pot2-domain/runkoaineet
                  #{:runkoaine/id
                    :pot2-massa/id
                    :runkoaine/tyyppi
                    :runkoaine/fillerityyppi
                    :runkoaine/esiintyma
                    :runkoaine/kuvaus
                    :runkoaine/kuulamyllyarvo
                    :runkoaine/litteysluku
                    :runkoaine/massaprosentti}]}
               {::pot2-domain/urakka-id urakka-id
                ::pot2-domain/poistettu? false})
        massat (map #(assoc % ::pot2-domain/sideaineet (fetch db
                                                              ::pot2-domain/pot2-massa-sideaine
                                                              #{:sideaine/id
                                                                :pot2-massa/id
                                                                :sideaine/tyyppi
                                                                :sideaine/pitoisuus
                                                                :sideaine/lopputuote?}
                                                              {:pot2-massa/id (:pot2-massa/id %)}))
                    massat)
        massat (map #(assoc % ::pot2-domain/lisaaineet (fetch db
                                                              ::pot2-domain/pot2-massa-lisaaine
                                                              #{:lisaaine/id
                                                                :pot2-massa/id
                                                                :lisaaine/tyyppi
                                                                :lisaaine/pitoisuus}
                                                              {:pot2-massa/id (:pot2-massa/id %)}))
                    massat)
        _ (println "hae-urakan-pot2-massat :: massat" (pr-str massat) )]
    massat))

(defn hae-pot2-koodistot [db user {:keys []}]
  (oikeudet/ei-oikeustarkistusta!)
  (let [massatyypit (fetch db ::pot2-domain/pot2-massatyyppi
                               (specql/columns ::pot2-domain/pot2-massatyyppi)
                               {})
        runkoainetyypit (fetch db ::pot2-domain/pot2-runkoainetyyppi
                           (specql/columns ::pot2-domain/pot2-runkoainetyyppi)
                           {})
        sideainetyypit (fetch db ::pot2-domain/pot2-sideainetyyppi
                          (specql/columns ::pot2-domain/pot2-sideainetyyppi)
                          {})
        lisaainetyypit (fetch db ::pot2-domain/pot2-lisaainetyyppi
                              (specql/columns ::pot2-domain/pot2-lisaainetyyppi)
                              {})
        koodistot {:massatyypit massatyypit
                   :runkoainetyypit runkoainetyypit
                   :sideainetyypit sideainetyypit
                   :lisaainetyypit lisaainetyypit}]
    koodistot))

(defn- tallenna-runkoaineet
  [db runkoaineet massa-id]
  (let [paluuarvo (atom [])]
    (doseq [[ra-tyyppi r] runkoaineet]
      (let [runkoaine-id (:runkoaine/id r)
            runkoaine (if (:valittu? r)
                        (upsert! db ::pot2-domain/pot2-massa-runkoaine
                                 (merge
                                   {:pot2-massa/id massa-id
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
                          (delete! db ::pot2-domain/pot2-massa-runkoaine {:runkoaine/id runkoaine-id})))]
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
                                                  (fetch db ::pot2-domain/pot2-massa-sideaine
                                                         #{:sideaine/id}
                                                         {:pot2-massa/id massa-id
                                                          :sideaine/lopputuote? (boolean (= kayttotapa :lopputuote))})))
        idt-jotka-poistettu-uilta (clojure.set/difference ennestaan-kannassa-olevien-idt (into #{}
                                                                                     (map :sideaine/id
                                                                                          (vals aineet))))]
    (doseq [poistettavan-id idt-jotka-poistettu-uilta]
      (when (int? poistettavan-id)
        (delete! db ::pot2-domain/pot2-massa-sideaine {:sideaine/id poistettavan-id
                                                       :pot2-massa/id massa-id})))
    (doseq [[_ {:sideaine/keys [id tyyppi pitoisuus]}] aineet]
      (let [paluurivi (if valittu?
                        (upsert! db ::pot2-domain/pot2-massa-sideaine
                                 (merge
                                   {:pot2-massa/id massa-id
                                    :sideaine/tyyppi tyyppi
                                    :sideaine/pitoisuus (bigdec pitoisuus)
                                    :sideaine/lopputuote? (boolean (= kayttotapa :lopputuote))}
                                   (when id
                                     {:sideaine/id id})))
                        ;; jos valittu? = false, kyseessä voi olla olemassaolevan sideainetyypin poistaminen
                        (when (int? id)
                          (delete! db ::pot2-domain/pot2-massa-sideaine {:sideaine/id id
                                                                         :pot2-massa/id massa-id})))]
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
                       (upsert! db ::pot2-domain/pot2-massa-lisaaine
                                (merge
                                  {:pot2-massa/id massa-id
                                   :lisaaine/tyyppi la-tyyppi
                                   :lisaaine/pitoisuus (bigdec pitoisuus)}
                                  (when id
                                    {:lisaaine/id id})))
                       ;; jos valittu? = false, kyseessä voi olla olemassaolevan lisaainetyypin poistaminen
                       (when (int? id)
                         (delete! db ::pot2-domain/pot2-massa-lisaaine {:lisaaine/id id
                                                                        :pot2-massa/id massa-id})))]
        (swap! paluuarvo conj lisaaine)))
    @paluuarvo))

(defn tallenna-urakan-paallystysmassa
  [db user {::pot2-domain/keys [runkoaineet sideaineet lisaaineet] :as tiedot}]
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-kohdeluettelo-paallystysilmoitukset user (:urakka-id tiedot))
  (jdbc/with-db-transaction
    [db db]
    (let [massa-id (:pot2-massa/id tiedot)
          _ (println (str "" (if massa-id "UPDATE" "INSERT") " massa-id  " (pr-str massa-id)))
          _ (println "tallenna-urakan-paallystysmassa :: runkoaineet" (pr-str runkoaineet))
          _ (println "tallenna-urakan-paallystysmassa :: sideaineet" (pr-str sideaineet))
          _ (println "tallenna-urakan-paallystysmassa :: lisaaineet" (pr-str lisaaineet))
          massa (upsert! db ::pot2-domain/pot2-massa
                         (merge
                           (if massa-id
                             {:pot2-massa/id massa-id
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
          massa-id (:pot2-massa/id massa)
          runkoaineet-kannasta (tallenna-runkoaineet db runkoaineet massa-id)
          _ (println "tallenna-urakan-paallystysmassa :: runkoaineet-kannasta" (pr-str runkoaineet-kannasta))

          sideaineet-kannasta (tallenna-sideaineet db sideaineet massa-id)
          _ (println "tallenna-urakan-paallystysmassa :: sideaineet-kannasta" (pr-str sideaineet-kannasta))

          lisaaineet-kannasta (tallenna-lisaaineet db lisaaineet massa-id)
          _ (println "tallenna-urakan-paallystysmassa :: lisaaineet-kannasta" (pr-str lisaaineet-kannasta))]
      (assoc massa :harja.domain.pot2/runkoaineet runkoaineet
                   :harja.domain.pot2/sideaineet sideaineet
                   :harja.domain.pot2/lisaaineet lisaaineet))))


(defn hae-kohteen-pot2-tiedot [db user {::pot2-domain/keys [yllapitokohde-id]}]
  (oikeudet/vaadi-lukuoikeus oikeudet/urakat-kohdeluettelo-paallystysilmoitukset user yllapitokohde-id)
  (let [_ (println "hae-kohteen-pot2-tiedot" (pr-str yllapitokohde-id))
        perustiedot (first (pot2-q/hae-kohteen-pot2-tiedot db {:kohde_id yllapitokohde-id}))

        ;; TODO: Tähän tulee myöh. erilliset kyselyt:
        ;; 1. Kkulutuskerroksen rivit
        ;; 2. Alustan rivit
        _ (println "hae-urakan-pot2-massat :: massat" (pr-str perustiedot))]
    {:perustiedot perustiedot}))

(defn tallenna-pot2-tiedot
  "Tallentaa uuden (POT2) päällystysilmoituksen tiedot kantaan.


  Lopuksi palauttaa päällystysilmoitukset ja ylläpitokohteet kannasta."
  [db user fim email {:keys [urakka-id sopimus-id vuosi paallystysilmoitus]}]
  (log/debug "Tallennetaan päällystysilmoitus: " paallystysilmoitus
             ". Urakka-id " urakka-id
             ", sopimus-id: " sopimus-id
             ", päällystyskohde-id:" (:paallystyskohde-id paallystysilmoitus))

  (log/debug "Aloitetaan päällystysilmoituksen tallennus")
  (jdbc/with-db-transaction [db db]
                            ;; Kirjoitusoikeudet tarkistetaan syvemällä, päivitetään vain ne osat, jotka saa
                            (yy/vaadi-yllapitokohde-kuuluu-urakkaan db urakka-id (:paallystyskohde-id paallystysilmoitus))
                            (yha-apurit/lukitse-urakan-yha-sidonta db urakka-id)
                            (let [hae-paallystysilmoitus (fn [paallystyskohde-id]
                                                           (first (into []
                                                                        (comp (map #(konversio/jsonb->clojuremap % :ilmoitustiedot))
                                                                              (map #(konversio/string-poluista->keyword %
                                                                                                                        [[:tila]])))
                                                                        (q/hae-paallystysilmoitus-paallystyskohteella
                                                                          db
                                                                          {:paallystyskohde paallystyskohde-id}))))

                                  tr-osoite (-> paallystysilmoitus :perustiedot :tr-osoite)
                                  ali-ja-muut-kohteet (remove :poistettu (-> paallystysilmoitus :ilmoitustiedot :osoitteet))
                                  alustatoimet (-> paallystysilmoitus :ilmoitustiedot :alustatoimet)
                                  kohde-id (:paallystyskohde-id paallystysilmoitus)
                                  virheviestit (yllapitokohteet-domain/validoi-kaikki-backilla db kohde-id urakka-id vuosi tr-osoite ali-ja-muut-kohteet alustatoimet)]
                              (if (empty? virheviestit)
                                (let [paallystyskohde-id (:paallystyskohde-id paallystysilmoitus)
                                      paivitetyt-kohdeosat (yllapitokohteet/tallenna-yllapitokohdeosat
                                                             db user {:urakka-id urakka-id :sopimus-id sopimus-id
                                                                      :vuosi vuosi
                                                                      :yllapitokohde-id paallystyskohde-id
                                                                      :osat (map #(assoc % :id (:kohdeosa-id %))
                                                                                 (->> paallystysilmoitus
                                                                                      :ilmoitustiedot
                                                                                      :osoitteet
                                                                                      (filter (comp not :poistettu))))})]
                                  (cond
                                    ;; Vaihetaan avainta, niin frontti ymmärtää tämän epäonnistuneeksi palvelukutsuksi eikä onnistuneeksi.
                                    (:validointivirheet paivitetyt-kohdeosat) (clj-set/rename-keys paivitetyt-kohdeosat {:validointivirheet :virhe})
                                    :else (let [tallennettava-kohde (-> (:perustiedot paallystysilmoitus)
                                                                        (select-keys #{:tr-numero :tr-ajorata :tr-kaista :tr-alkuosa :tr-alkuetaisyys :tr-loppuosa :tr-loppuetaisyys :kohdenumero :kohdenimi :tunnus})
                                                                        (clj-set/rename-keys {:kohdenimi :nimi}))
                                                paallystysilmoitus (lisaa-paallystysilmoitukseen-kohdeosien-idt paallystysilmoitus paivitetyt-kohdeosat)
                                                vanha-paallystysilmoitus (hae-paallystysilmoitus paallystyskohde-id)
                                                paallystysilmoitus-id (if vanha-paallystysilmoitus
                                                                        (paivita-paallystysilmoitus db user urakka-id paallystysilmoitus
                                                                                                    vanha-paallystysilmoitus)
                                                                        (luo-paallystysilmoitus db user urakka-id paallystysilmoitus))
                                                _ (q/paivita-yllapitokohde! db
                                                                            {:tr-alkuosa (:tr-alkuosa tallennettava-kohde)
                                                                             :tr-alkuetaisyys (:tr-alkuetaisyys tallennettava-kohde)
                                                                             :tr-loppuosa (:tr-loppuosa tallennettava-kohde)
                                                                             :tr-loppuetaisyys (:tr-loppuetaisyys tallennettava-kohde)
                                                                             :id paallystyskohde-id
                                                                             :urakka urakka-id
                                                                             :muokkaaja (:id user)})
                                                tuore-paallystysilmoitus (hae-paallystysilmoitus paallystyskohde-id)]


                                            (tallenna-paallystysilmoituksen-kommentti db user paallystysilmoitus paallystysilmoitus-id)
                                            (laheta-paallystysilmoitussahkoposti-tarvittaessa {:db db :fim fim :email email :urakka-id urakka-id
                                                                                               :paallystyskohde-id paallystyskohde-id
                                                                                               :uusi-tila (:tila tuore-paallystysilmoitus)
                                                                                               :vanha-tila (:tila vanha-paallystysilmoitus)
                                                                                               :uusi-paatos (:tekninen-osa_paatos tuore-paallystysilmoitus)
                                                                                               :vanha-paatos (:tekninen-osa_paatos vanha-paallystysilmoitus)})

                                            ;; Rakennetaan vastaus
                                            (let [yllapitokohteet (yllapitokohteet/hae-urakan-yllapitokohteet db user {:urakka-id urakka-id
                                                                                                                       :sopimus-id sopimus-id
                                                                                                                       :vuosi vuosi})
                                                  uudet-ilmoitukset (hae-urakan-paallystysilmoitukset db user {:urakka-id urakka-id
                                                                                                               :sopimus-id sopimus-id
                                                                                                               :vuosi vuosi})]
                                              {:yllapitokohteet yllapitokohteet
                                               :paallystysilmoitukset uudet-ilmoitukset}))))
                                {:virhe virheviestit}))))

(defrecord POT2 []
  component/Lifecycle
  (start [this]
    (let [http (:http-palvelin this)
          db (:db this)
          fim (:fim this)
          email (:sonja-sahkoposti this)]

      (julkaise-palvelu http :hae-urakan-pot2-massat
                        (fn [user tiedot]
                          (hae-urakan-pot2-massat db user tiedot)))
      (julkaise-palvelu http :hae-pot2-koodistot
                        (fn [user tiedot]
                          (hae-pot2-koodistot db user tiedot)))
      (julkaise-palvelu http :tallenna-urakan-pot2-massa
                        (fn [user tiedot]
                          (tallenna-urakan-paallystysmassa db user tiedot)))

      ;; POT2 palvelut tänne
      (julkaise-palvelu http :hae-kohteen-pot2-tiedot
                        (fn [user tiedot]
                          (hae-kohteen-pot2-tiedot db user tiedot)))
      (julkaise-palvelu http :tallenna-pot2-tiedot
                        (fn [user tiedot]
                          (tallenna-pot2-tiedot db user fim email tiedot)))
      this))

  (stop [this]
    (poista-palvelut
      (:http-palvelin this)
      :hae-urakan-pot2-massat
      :hae-pot2-koodistot
      :tallenna-urakan-pot2-massa)
    this))