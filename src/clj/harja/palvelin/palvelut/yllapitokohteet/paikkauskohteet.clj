(ns harja.palvelin.palvelut.yllapitokohteet.paikkauskohteet
  (:require [com.stuartsierra.component :as component]
            [harja.domain.oikeudet :as oikeudet]
            [harja.pvm :as pvm]
            [harja.geo :as geo]
            [harja.kyselyt.paikkaus :as q]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelut]]
            [taoensso.timbre :as log]))



(defn paikkauskohteet [db user {:keys [vastuuyksikko tila aikavali tyomenetelmat urakka-id] :as tiedot}]
  ;; TODO: Tarkista oikeudet
  (oikeudet/vaadi-lukuoikeus oikeudet/urakat-paikkaukset-toteumat user (:urakka-id tiedot))
  (let [_ (println "paikkauskohteet :: tiedot" (pr-str tiedot))
        urakan-paikkauskohteet (q/paikkauskohteet-urakalle db {:urakka-id urakka-id})
        urakan-paikkauskohteet (map (fn [p]
                                      (-> p
                                          (assoc :sijainti (geo/pg->clj (:geometria p)))
                                          (dissoc :geometria)))
                                    urakan-paikkauskohteet)

        _ (println "urakan-paikkauskohteet geometria siivottu: " (pr-str urakan-paikkauskohteet))]
    urakan-paikkauskohteet))

(defn tallenna-paikkauskohde! [db user kohde]
  ;;TODO: Tarkista oikeudet
  (oikeudet/vaadi-lukuoikeus oikeudet/urakat-paikkaukset-toteumat user (:urakka-id kohde))
  (let [_ (println "käyttäjän id " (pr-str (:id user)))
        kohde-id (:id kohde)
        ;; Jos annetulla kohteella on olemassa id, niin päivitetään. Muuten tehdään uusi
        kohde (if kohde-id
                (do
                  (q/paivita-paikkauskohde! db
                                            {:id kohde-id
                                             :ulkoinen-id (or (:ulkoinen-id kohde) 0)
                                             :nimi (:nimi kohde)
                                             :poistettu (or (:poistettu kohde) false)
                                             :muokkaaja-id (:id user)
                                             :muokattu (pvm/nyt)
                                             :yhalahetyksen-tila (:yhalahetyksen-tila kohde)
                                             :virhe (:virhe kohde)
                                             :tarkistettu (or (:tarkistettu kohde) nil)
                                             :tarkistaja-id (or (:tarkistaja-id kohde) nil)
                                             :ilmoitettu-virhe (or (:ilmoitettu-virhe kohde) nil)
                                             :nro (:nro kohde)
                                             :alkuaika (:alkuaika kohde)
                                             :loppuaika (:loppuaika kohde)
                                             :tyomenetelma (or (:tyomenetelma kohde) nil)
                                             :tyomenetelma-kuvaus (or (:tyomenetelma-kuvaus kohde) nil)
                                             :tie (:tie kohde)
                                             :aosa (:aosa kohde)
                                             :losa (:losa kohde)
                                             :aet (:aet kohde)
                                             :let (:let kohde)
                                             :paikkauskohteen-tila (:paikkauskohteen-tila kohde)})
                  kohde)
                (do
                  (println "Tallennettiin uusi :: antamalla " (pr-str kohde))
                  (q/luo-uusi-paikkauskohde! db
                                             {:luoja-id (:id user)
                                              :ulkoinen-id (or (:ulkoinen-id kohde) 0)
                                              :nimi (:nimi kohde)
                                              :urakka-id (:urakka-id kohde)
                                              :luotu (or (:luotu kohde) (pvm/nyt))
                                              :yhalahetyksen-tila (:yhalahetyksen-tila kohde)
                                              :virhe (:virhe kohde)
                                              :nro (:nro kohde)
                                              :alkuaika (:alkuaika kohde)
                                              :loppuaika (:loppuaika kohde)
                                              :tyomenetelma (:tyomenetelma kohde)
                                              :tyomenetelma-kuvaus (:tyomenetelma-kuvaus kohde)
                                              :tie (:tie kohde)
                                              :aosa (:aosa kohde)
                                              :losa (:losa kohde)
                                              :aet (:aet kohde)
                                              :let (:let kohde)
                                              :paikkauskohteen-tila (:paikkauskohteen-tila kohde)
                                              })))

        _ (println "kohde: " (pr-str kohde))
        ]
    kohde))

(defrecord Paikkauskohteet []
  component/Lifecycle
  (start [this]
    (let [http (:http-palvelin this)
          ;email (:sonja-sahkoposti this)
          db (:db this)]
      (julkaise-palvelu http :paikkauskohteet-urakalle
                        (fn [user tiedot]
                          (paikkauskohteet db user tiedot)))
      (julkaise-palvelu http :tallenna-paikkauskohde-urakalle
                        (fn [user kohde]
                          (tallenna-paikkauskohde! db user kohde)))
      this))

  (stop [this]
    (poista-palvelut
      (:http-palvelin this)
      :paikkauskohteet-urakalle
      :tallenna-paikkauskohde-urakalle)
    this))
