(ns harja.palvelin.palvelut.suunnittelu.suolarajoitus-palvelu
  (:require [com.stuartsierra.component :as component]
            [harja.kyselyt.suolarajoitus-kyselyt :as suolarajoitus-kyselyt]
            [harja.kyselyt.tieverkko :as tieverkko-kyselyt]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelut]]
            [harja.domain.oikeudet :as oikeudet]
            [taoensso.timbre :as log]
            [harja.palvelin.tyokalut.tyokalut :as tyokalut]
            [slingshot.slingshot :refer [throw+ try+]]))

(defn hae-suolarajoitukset [db user {:keys [urakka_id hoitokauden_alkuvuosi] :as tiedot}]
  (oikeudet/vaadi-lukuoikeus oikeudet/urakat-suunnittelu-suola user urakka_id)
  (let [_ (log/debug "Suolarajoitus :: hae-suolarajoitukset :: tiedot " tiedot)
        rajoitukset (suolarajoitus-kyselyt/hae-suolarajoitukset-hoitokaudelle db
                      {:hoitokauden_alkuvuosi hoitokauden_alkuvuosi
                       :urakka_id urakka_id})
        _ (log/debug "Suolarajoitus :: hae-suolarajoitukset :: Löydettiin rajoitukset:" rajoitukset)]
    rajoitukset))

(defn tallenna-suolarajoitus [db user suolarajoitus]
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-suunnittelu-suola user (:urakka_id suolarajoitus))
  (let [;; Tallennetaan ensin rajoitusalue uutena tai päivityksenä
        db-rajoitusalue {:id (when (:rajoitusalue_id suolarajoitus) (:rajoitusalue_id suolarajoitus))
                         :tie (:tie suolarajoitus)
                         :aosa (:aosa suolarajoitus)
                         :aet (:aet suolarajoitus)
                         :losa (:losa suolarajoitus)
                         :let (:let suolarajoitus)
                         :pituus (:pituus suolarajoitus)
                         :ajoratojen_pituus (:ajoratojen_pituus suolarajoitus)
                         :urakka_id (:urakka_id suolarajoitus)
                         :kayttaja_id (:id user)}
        ;; Päivitä tai tallenna uutena
        rajoitusalue (if (:id db-rajoitusalue)
                       (do
                         (suolarajoitus-kyselyt/paivita-rajoitusalue! db db-rajoitusalue)
                         (first (suolarajoitus-kyselyt/hae-suolarajoitusalue db {:id (:rajoitusalue_id suolarajoitus)})))
                       (let [vastaus (suolarajoitus-kyselyt/tallenna-rajoitusalue<! db (dissoc db-rajoitusalue :id))]
                         (first (suolarajoitus-kyselyt/hae-suolarajoitusalue db {:id (:id vastaus)}))))

        db-rajoitus {:id (when (:rajoitus_id suolarajoitus) (:rajoitus_id suolarajoitus))
                     :rajoitusalue_id (:id rajoitusalue)
                     :suolarajoitus (:suolarajoitus suolarajoitus)
                     :formiaatti (:formiaatti suolarajoitus)
                     :hoitokauden_alkuvuosi (:hoitokauden_alkuvuosi suolarajoitus)
                     :kayttaja_id (:id user)}
        ;; Päivitä tai tallenna uutena
        suolarajoitus (if (:id db-rajoitus)
                        (do
                          (suolarajoitus-kyselyt/paivita-suolarajoitus! db db-rajoitus)
                          (first (suolarajoitus-kyselyt/hae-suolarajoitus db {:rajoitusalue_id (:id rajoitusalue)
                                                                              :hoitokauden_alkuvuosi (:hoitokauden_alkuvuosi suolarajoitus)})))
                        (do
                          (suolarajoitus-kyselyt/tallenna-suolarajoitus<! db (dissoc db-rajoitus :id))
                          (first (suolarajoitus-kyselyt/hae-suolarajoitus db {:rajoitusalue_id (:id rajoitusalue)
                                                                              :hoitokauden_alkuvuosi (:hoitokauden_alkuvuosi suolarajoitus)}))))]
    suolarajoitus))

(defn poista-suolarajoitus [db user {:keys [urakka_id rajoitusalue_id hoitokauden_alkuvuosi]}]
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-suunnittelu-suola user urakka_id)
  (let [_ (log/debug "Suolarajoitus :: poista-suolarajoitus :: rajoitusalue_id:" rajoitusalue_id " hoitokauden_alkuvuosi: " hoitokauden_alkuvuosi)
        ;; Poistaa kaikki suolarajoitukset joilla on sama tai suurempi hoitokauden alkuvuosi
        poistetut-alueet (suolarajoitus-kyselyt/poista-suolarajoitus<! db {:rajoitusalue_id rajoitusalue_id
                                                                           :hoitokauden_alkuvuosi hoitokauden_alkuvuosi})
        ;; Jos suolarajoituksia ei jää rajoitusalue_rajoitus tauluun, niin poistetaan myös alkuperäinen rajoitusalue
        suolarajoitukset (suolarajoitus-kyselyt/hae-suolarajoitukset-rajoitusalueelle db {:rajoitusalue_id rajoitusalue_id})
        poistetut-rajoitukset (when (empty? suolarajoitukset)
                                (suolarajoitus-kyselyt/poista-suolarajoitusalue<! db {:id rajoitusalue_id}))]
    (if (nil? poistetut-alueet)
      (throw+ {:type "Error"
               :virheet [{:koodi "ERROR" :viesti "Poisto epäonnistui."}]})
      "OK")))

(defn pituuden-laskennan-data-validi?
  [{:keys [tie aosa losa aet let] :as suolarajoitus}]
  (and
    (pos-int? tie)
    (int? aosa)
    (int? losa)
    (int? aet)
    (int? let)))

(defn laske-suolarajoituksen-pituudet [db user suolarajoitus]
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-suunnittelu-suola user (:urakka_id suolarajoitus))
  (if (pituuden-laskennan-data-validi? suolarajoitus)
    (let [tie-osien-pituudet (tieverkko-kyselyt/hae-osien-pituudet db {:tie (:tie suolarajoitus)
                                                                       :aosa (:aosa suolarajoitus)
                                                                       :losa (:losa suolarajoitus)})
          pituus (tieverkko-kyselyt/laske-tien-osien-pituudet tie-osien-pituudet suolarajoitus)
          ajoratojen-pituudet (tieverkko-kyselyt/hae-ajoratojen-pituudet db {:tie (:tie suolarajoitus)
                                                                             :aosa (:aosa suolarajoitus)
                                                                             :losa (:losa suolarajoitus)})
          ajoratojen-pituus (reduce (fn [summa ajorata]
                                      (let [pituus (tieverkko-kyselyt/laske-tien-osien-pituudet (conj [] ajorata) suolarajoitus)
                                            summa (+ summa (:pituus pituus))]
                                        summa))
                              0 ajoratojen-pituudet)]
      {:pituus (:pituus pituus)
       :ajoratojen_pituus ajoratojen-pituus})
    (throw+ {:type "Error"
             :virheet [{:koodi "ERROR" :viesti "Tierekisteriosoitteessa virhe."}]})))

(defrecord Suolarajoitus []
  component/Lifecycle
  (start [this]
    (julkaise-palvelu (:http-palvelin this)
      :hae-suolarajoitukset
      (fn [user tiedot]
        (hae-suolarajoitukset (:db this) user tiedot)))

    (julkaise-palvelu (:http-palvelin this)
      :tallenna-suolarajoitus
      (fn [user tiedot]
        (tallenna-suolarajoitus (:db this) user tiedot)))

    (julkaise-palvelu (:http-palvelin this)
      :poista-suolarajoitus
      (fn [user tiedot]
        (poista-suolarajoitus (:db this) user tiedot)))

    (julkaise-palvelu (:http-palvelin this)
      :laske-suolarajoituksen-pituudet
      (fn [user tiedot]
        (laske-suolarajoituksen-pituudet (:db this) user tiedot)))
    this)

  (stop [this]
    (poista-palvelut (:http-palvelin this)
      :hae-suolarajoitukset
      :tallenna-suolarajoitus
      :poista-suolarajoitus
      :laske-suolarajoituksen-pituudet)
    this))
