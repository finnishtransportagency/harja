(ns harja.palvelin.palvelut.yllapitokohteet.pot2
  "Tässä namespacessa on esitelty palvelut, jotka liittyvät erityisesti pot2"
  (:require [com.stuartsierra.component :as component]
            [cheshire.core :as cheshire]
            [taoensso.timbre :as log]
            [hiccup.core :refer [html]]

            [clojure.set :as clj-set]
            [clojure.java.jdbc :as jdbc]
            [specql.core :refer [fetch update! insert! upsert! delete!]]
            [harja.kyselyt
             [kommentit :as kommentit-q]
             [yllapitokohteet :as yllapito-q]
             [paallystys :as q]
             [urakat :as urakat-q]
             [konversio :as konversio]
             [tieverkko :as tieverkko-q]]
            [harja.domain
             [paallystysilmoitus :as pot-domain]
             [pot2 :as pot2-domain]
             [skeema :refer [Toteuma validoi] :as skeema]
             [urakka :as urakka-domain]
             [sopimus :as sopimus-domain]
             [oikeudet :as oikeudet]
             [paallystyksen-maksuerat :as paallystyksen-maksuerat]
             [yllapitokohde :as yllapitokohteet-domain]
             [tierekisteri :as tr-domain]
             [muokkaustiedot :as muokkaustiedot]]
            [harja.palvelin.palvelut
             [yha-apurit :as yha-apurit]
             [yllapitokohteet :as yllapitokohteet]
             [viestinta :as viestinta]]
            [harja.palvelin.palvelut.yllapitokohteet
             [maaramuutokset :as maaramuutokset]
             [yleiset :as yy]]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelut]]
            [harja.tyokalut.html :refer [sanitoi]]

            [harja.pvm :as pvm]
            [specql.core :as specql]
            [jeesql.core :refer [defqueries]]))

(defqueries "harja/kyselyt/pot2.sql")

(defn hae-urakan-pot2-massat [db user {:keys [urakka-id]}]
  (oikeudet/vaadi-lukuoikeus oikeudet/urakat-kohdeluettelo-paallystysilmoitukset user urakka-id)
  (let [_ (println "hae-urakan-pot2-massat :: urakka-id" (pr-str urakka-id))
        massat (fetch db
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
                           :runkoaine/massaprosentti}]
                        [::pot2-domain/sideaineet
                         #{:sideaine/id
                           :pot2-massa/id
                           :sideaine/tyyppi
                           :sideaine/pitoisuus
                           :sideaine/lopputuote?}]
                        [::pot2-domain/lisa-aineet
                         #{:lisaaine/id
                           :pot2-massa/id
                           :lisaaine/tyyppi
                           :lisaaine/pitoisuus}]}
                      {::pot2-domain/urakka-id urakka-id
                       ::pot2-domain/poistettu? false})
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

(defn tallenna-urakan-paallystysmassa [db user tiedot]
  (oikeudet/vaadi-lukuoikeus oikeudet/urakat-kohdeluettelo-paallystysilmoitukset user (:urakka-id tiedot))
  (jdbc/with-db-transaction
    [db db]
    (let [massa-id (:massa-id tiedot)
          _ (println "tallenna-urakan-paallystysmassa :: tiedot" (pr-str tiedot))
          massan_runkoaineet (:runkoaineet tiedot)
          massan_sideaineet (:sideaineet tiedot)
          massan_lisa-aineet (:lisa-aineet tiedot)
          massa (upsert! db ::pot2-domain/pot2-massa
                         (merge
                           (if massa-id
                             {::pot2-domain/massa-id massa-id
                              ::muokkaustiedot/muokattu (pvm/nyt)
                              ::muokkaustiedot/muokkaaja-id (:id user)}
                             {::muokkaustiedot/luotu (pvm/nyt)
                              ::muokkaustiedot/luoja-id (:id user)}
                             )
                           (select-keys tiedot [::pot2-domain/urakka-id
                                                ::pot2-domain/nimen-tarkenne
                                                ::pot2-domain/tyyppi
                                                ::pot2-domain/max-raekoko
                                                ::pot2-domain/kuulamyllyluokka
                                                ::pot2-domain/litteyslukuluokka
                                                ::pot2-domain/dop-nro])
                           ))
          _ (println "tallenna-urakan-paallystysmassa :: massa" (pr-str massa))
          massa-id (:pot2-massa/id massa)
          _ (println "tallenna-urakan-paallystysmassa :: massa-id" (pr-str massa-id))
          #_#_#_#_#_#_#_#_#_#_#_#_#_#_#_#_#_#_ runkoaineet (for [r massan_runkoaineet]
                        (upsert! db ::pot2-domain/pot2-massa-runkoaine
                                 {:pot2-massa/id massa-id
                                  :runkoaine/esiintyma (:kiviaine-esiintyma r)
                                  :runkoaine/kuulamyllyarvo (when (:kuulamyllyarvo r)
                                                              (bigdec (:kuulamyllyarvo r)))
                                  :runkoaine/muotoarvo (:muotoarvo r)
                                  :runkoaine/massaprosentti (when (:massaprosentti r)
                                                              (bigdec (:massaprosentti r)))
                                  :runkoaine/erikseen-lisattava-fillerikiviaines (:erikseen-lisattafa-fillerikiviaines r)}))
          _ (println "tallenna-urakan-paallystysmassa :: runkoaineet" (pr-str runkoaineet))
          massa (assoc massa :runkoaineet runkoaineet)
          sideaineet (for [s massan_sideaineet]
                       (upsert! db ::pot2-domain/pot2-massa-sideaine
                                {:pot2-massa/id massa-id
                                 :sideaine/tyyppi (:tyyppi s)
                                 :sideaine/pitoisuus (bigdec (:pitoisuus s))
                                 :sideaine/lopputuote? (:lopputuote? s)}))
          _ (println "tallenna-urakan-paallystysmassa :: sideaineet" (pr-str sideaineet))
          massa (assoc massa :sideaineet sideaineet)
          lisa-aineet (for [s massan_lisa-aineet]
                        (upsert! db ::pot2-domain/pot2-massa-lisaaine
                                 {:pot2-massa/id massa-id
                                  :lisaaine/nimi (:nimi s)
                                  :lisaaine/pitoisuus (bigdec (:pitoisuus s))}))
          _ (println "tallenna-urakan-paallystysmassa :: lisa-aineet" (pr-str lisa-aineet))
          massa (assoc massa :lisa-aineet lisa-aineet)]
      massa)))


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
      this))

  (stop [this]
    (poista-palvelut
      (:http-palvelin this)
      :hae-urakan-pot2-massat
      :hae-pot2-koodistot
      :tallenna-urakan-pot2-massa)
    this))