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
                        ::pot2-domain/nimi
                        [::pot2-domain/runkoaineet
                         #{:runkoaine/id
                           :runkoaine/pot2-massa-id
                           :runkoaine/erikseen-lisattava-fillerikiviaines
                           :runkoaine/kiviaine_esiintyma}]
                        [::pot2-domain/lisa-aineet
                         #{:lisaaine/id
                           :lisaaine/pot2-massa-id
                           :lisaaine/nimi
                           :lisaaine/pitoisuus}]
                        [::pot2-domain/sideaineet
                         #{:sideaine/id
                           :sideaine/pot2-massa-id
                           :sideaine/tyyppi
                           :sideaine/pitoisuus
                           :sideaine/lopputuote?}]}
                      {::pot2-domain/urakka-id urakka-id
                       ::pot2-domain/poistettu? false})
        _ (println "hae-urakan-pot2-massat :: massat" (pr-str massat) )]
    massat))

(defn hae-urakan-pot2-runkoaineet [db user {:keys [urakka-id]}]
  (oikeudet/vaadi-lukuoikeus oikeudet/urakat-kohdeluettelo-paallystysilmoitukset user urakka-id)
  (let [_ (println "hae-urakan-pot2-runkoaineet :: urakka-id" (pr-str urakka-id))
        runkoaineet (fetch db ::pot2-domain/pot2-runkoaine
                           (specql/columns ::pot2-domain/pot2-runkoaine)
                           {})
        _ (println "hae-urakan-pot2-massat :: massat" (pr-str runkoaineet) )]
    runkoaineet))

(defn tallenna-urakan-paallystysmassa [db user tiedot]
  (oikeudet/vaadi-lukuoikeus oikeudet/urakat-kohdeluettelo-paallystysilmoitukset user (:urakka-id tiedot))
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
                         {::pot2-domain/urakka-id (::pot2-domain/urakka-id tiedot)
                          ::pot2-domain/nimi (::pot2-domain/nimi tiedot)
                          ::pot2-domain/massatyyppi (::pot2-domain/massatyyppi tiedot)
                          ::pot2-domain/max_raekoko (::pot2-domain/max_raekoko tiedot)
                          ::pot2-domain/asfalttiasema (::pot2-domain/asfalttiasema tiedot)
                          ::pot2-domain/kuulamyllyluokka (::pot2-domain/kuulamyllyluokka tiedot)
                          ::pot2-domain/litteyslukuluokka (str (::pot2-domain/litteyslukuluokka tiedot))
                          ::pot2-domain/dop_nro (bigdec (::pot2-domain/dop_nro tiedot))}))
        _ (println "tallenna-urakan-paallystysmassa :: massa" (pr-str massa))
        massa-id (:pot2-massa/id massa)
        _ (println "tallenna-urakan-paallystysmassa :: massa-id" (pr-str massa-id))
        runkoaineet (for [r massan_runkoaineet]
                      (upsert! db ::pot2-domain/pot2-massa-runkoaine
                               {:runkoaine/pot2-massa-id massa-id
                                :runkoaine/kiviaine_esiintyma (:kiviaine-esiintyma r)
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
                              {:sideaine/pot2-massa-id massa-id
                               :sideaine/tyyppi (:tyyppi s)
                               :sideaine/pitoisuus (bigdec (:pitoisuus s))
                               :sideaine/lopputuote? (:lopputuote? s)}))
        _ (println "tallenna-urakan-paallystysmassa :: sideaineet" (pr-str sideaineet))
        massa (assoc massa :sideaineet sideaineet)
        lisa-aineet (for [s massan_lisa-aineet]
                     (upsert! db ::pot2-domain/pot2-massa-lisaaine
                              {:lisaaine/pot2-massa-id massa-id
                               :lisaaine/nimi (:nimi s)
                               :lisaaine/pitoisuus (bigdec (:pitoisuus s))}))
        _ (println "tallenna-urakan-paallystysmassa :: lisa-aineet" (pr-str lisa-aineet))
        massa (assoc massa :lisa-aineet lisa-aineet)]
    massa))


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
      (julkaise-palvelu http :hae-urakan-pot2-runkoaineet
                        (fn [user tiedot]
                          (hae-urakan-pot2-runkoaineet db user tiedot)))
      (julkaise-palvelu http :tallenna-urakan-pot2-massa
                        (fn [user tiedot]
                          (tallenna-urakan-paallystysmassa db user tiedot)))
      this))

  (stop [this]
    (poista-palvelut
      (:http-palvelin this)
      :hae-urakan-pot2-massat
      :hae-urakan-pot2-runkoaineet
      :tallenna-urakan-pot2-massa)
    this))