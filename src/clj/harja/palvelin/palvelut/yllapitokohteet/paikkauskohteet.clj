(ns harja.palvelin.palvelut.yllapitokohteet.paikkauskohteet
  (:require [com.stuartsierra.component :as component]
            [slingshot.slingshot :refer [throw+ try+]]
            [clojure.spec.alpha :as s]
            [harja.domain.oikeudet :as oikeudet]
            [harja.pvm :as pvm]
            [harja.kyselyt.konversio :as konv]
            [harja.geo :as geo]
            [harja.kyselyt.paikkaus :as q]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelut]]
            [taoensso.timbre :as log]))

(defn validi-pvm-vali? [validointivirheet alku loppu]
  (if (and (not (nil? alku)) (not (nil? loppu)) (.after (konv/sql-timestamp alku) (konv/sql-timestamp loppu)))
    (conj validointivirheet "Loppuaika tulee ennen alkuaikaa.")
    validointivirheet))

(defn validit-tr_osat? [validointivirheet tie alkuosa alkuetaisyys loppuosa loppuetaisyys]
  (if (and tie alkuosa alkuetaisyys loppuosa loppuetaisyys
           (>= loppuosa alkuosa))
    validointivirheet
    (conj validointivirheet "Tierekisterissä virhe.")))

(defn- validi-aika? [^java.util.Date aika]
  (if (and
        (.after aika (pvm/->pvm-aika "01.01.2000 00:00:00"))
        (.before aika (pvm/->pvm-aika "01.01.2100 00:00:00")))
    true
    false))

(defn- validi-nimi? [nimi]
  (if (or (nil? nimi) (= "" nimi))
    false
    true))

(s/def ::nimi (s/and string? #(validi-nimi? %)))
(s/def ::alkuaika (s/and #(instance? java.util.Date %) #(validi-aika? %)))
(s/def ::loppuaika (s/and #(instance? java.util.Date %) #(validi-aika? %)))

(defn paikkauskohde-validi? [kohde]
  (let [validointivirheet (as-> #{} virheet
                                (if (s/valid? ::nimi (:nimi kohde))
                                  virheet
                                  (conj virheet "Paikkauskohteen nimi puuttuu."))
                                (if (s/valid? ::alkuaika (:alkuaika kohde))
                                  virheet
                                  (conj virheet "Paikkauskohteen lopetusajassa virhe."))
                                (if (s/valid? ::loppuaika (:loppuaika kohde))
                                  virheet
                                  (conj virheet "Paikkauskohteen aloitusajassa virhe."))
                                (validi-pvm-vali? virheet (:alkuaika kohde) (:loppuaika kohde))
                                (validit-tr_osat? virheet (:tie kohde) (:aosa kohde) (:losa kohde) (:aet kohde) (:let kohde)))]
    validointivirheet))

(defn paikkauskohteet [db user {:keys [vastuuyksikko tila aikavali tyomenetelmat urakka-id] :as tiedot}]
  ;; TODO: Tarkista oikeudet
  (oikeudet/vaadi-lukuoikeus oikeudet/urakat-paikkaukset-toteumat user (:urakka-id tiedot))
  (let [_ (println "paikkauskohteet :: tiedot" (pr-str tiedot))
        urakan-paikkauskohteet (q/paikkauskohteet-urakalle db {:urakka-id urakka-id})
        urakan-paikkauskohteet (map (fn [p]
                                      (-> p
                                          (assoc :sijainti (geo/pg->clj (:geometria p)))
                                          (dissoc :geometria)))
                                    urakan-paikkauskohteet)]
    urakan-paikkauskohteet))

(defn tallenna-paikkauskohde! [db user kohde]
  ;;TODO: Tarkista oikeudet
  (oikeudet/vaadi-lukuoikeus oikeudet/urakat-paikkaukset-toteumat user (:urakka-id kohde))
  (let [_ (println "tallenna-paikkauskohde! :: kohde " (pr-str kohde))
        kohde-id (:id kohde)
        ;; Tarkista pakolliset tiedot ja tietojen oikeellisuus
        validointivirheet (paikkauskohde-validi? kohde)
        _ (println "tallenna-paikkauskohde! :: validointivirheet" (pr-str validointivirheet))
        ;; Jos annetulla kohteella on olemassa id, niin päivitetään. Muuten tehdään uusi
        kohde (when (empty? validointivirheet)
                (if kohde-id
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
                                                }))))

        _ (println "kohde: " (pr-str kohde))
        ]
    (if (empty? validointivirheet)
      kohde
      (throw+ {:type "Error"
               :virheet [{:koodi "ERROR" :viesti validointivirheet}]}))
    ))

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
