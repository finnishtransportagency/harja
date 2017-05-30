(ns harja.palvelin.palvelut.tietyoilmoitukset
  (:require [com.stuartsierra.component :as component]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelut async]]
            [harja.kyselyt.konversio :as konv]
            [taoensso.timbre :as log]
            [clj-time.coerce :refer [from-sql-time]]
            [harja.kyselyt.tietyoilmoitukset :as q-tietyoilmoitukset]
            [harja.kyselyt.yllapitokohteet :as q-yllapitokohteet]
            [harja.kyselyt.yhteyshenkilot :as q-yhteyshenkilot]
            [harja.domain.oikeudet :as oikeudet]
            [harja.palvelin.palvelut.kayttajatiedot :as kayttajatiedot]
            [harja.geo :as geo]
            [harja.palvelin.komponentit.pdf-vienti :as pdf-vienti]
            [harja.palvelin.palvelut.tietyoilmoitukset.pdf :as pdf]
            [harja.domain.tietyoilmoitukset :as t]
            [harja.domain.muokkaustiedot :as m]
            [specql.core :refer [fetch upsert!]]
            [clojure.spec.alpha :as s]
            [clj-time.coerce :as c]
            [harja.pvm :as pvm]
            [specql.op :as op]
            [harja.domain.tierekisteri :as tr]
            [harja.palvelin.palvelut.tierek-haku :as tr-haku]
            [harja.palvelin.komponentit.fim :as fim]
            [harja.domain.roolit :as roolit]
            [slingshot.slingshot :refer [throw+]]))

(defn aikavaliehto [vakioaikavali alkuaika loppuaika]
  (when (not (:ei-rajausta? vakioaikavali))
    (if-let [tunteja (:tunteja vakioaikavali)]
      [(c/to-date (pvm/tuntia-sitten tunteja)) (pvm/nyt)]
      [alkuaika loppuaika])))

(defn- urakat [db user oikeus]
  (kayttajatiedot/kayttajan-urakka-idt-aikavalilta
    db
    user
    (partial oikeus oikeudet/ilmoitukset-ilmoitukset)))

(defn hae-tietyoilmoitukset [db user {:keys [luotu-alkuaika
                                             luotu-loppuaika
                                             luotu-vakioaikavali
                                             kaynnissa-alkuaika
                                             kaynnissa-loppuaika
                                             kaynnissa-vakioaikavali
                                             sijainti
                                             urakka
                                             vain-kayttajan-luomat]
                                      :as hakuehdot}
                             max-maara]
  (let [kayttajan-urakat (urakat db user oikeudet/voi-lukea?)
        alkuehto (fn [aikavali] (when (first aikavali) (konv/sql-timestamp (first aikavali))))
        loppuehto (fn [aikavali] (when (second aikavali) (konv/sql-timestamp (second aikavali))))
        luotu-aikavali (aikavaliehto luotu-vakioaikavali luotu-alkuaika luotu-loppuaika)
        luotu-alku (alkuehto luotu-aikavali)
        luotu-loppu (loppuehto luotu-aikavali)
        kaynnissa-aikavali (aikavaliehto kaynnissa-vakioaikavali kaynnissa-alkuaika kaynnissa-loppuaika)
        kaynnissa-alku (alkuehto kaynnissa-aikavali)
        kaynnissa-loppu (loppuehto kaynnissa-aikavali)
        kyselyparametrit {:luotu-alku luotu-alku
                          :luotu-loppu luotu-loppu
                          :kaynnissa-alku kaynnissa-alku
                          :kaynnissa-loppu kaynnissa-loppu
                          :urakat (if urakka
                                    [urakka]
                                    kayttajan-urakat)
                          :urakattomat? (nil? urakka)
                          :luojaid (when vain-kayttajan-luomat (:id user))
                          :sijainti (when sijainti (geo/geometry (geo/clj->pg sijainti)))
                          :maxmaara max-maara
                          :organisaatio (:id (:organisaatio user))}
        tietyoilmoitukset (q-tietyoilmoitukset/hae-ilmoitukset db kyselyparametrit)]
    tietyoilmoitukset))

(defn hae-tietyoilmoitus [db user tietyoilmoitus-id]
  ;; todo: lisää oikeustarkistus, kun tiedetään miten se pitää tehdä
  (q-tietyoilmoitukset/hae-ilmoitus db tietyoilmoitus-id))

(defn tallenna-tietyoilmoitus [db user ilmoitus]
  (let [kayttajan-urakat (urakat db user oikeudet/voi-kirjoittaa?)]
    (when (::t/urakka-id ilmoitus)
      (oikeudet/vaadi-kirjoitusoikeus oikeudet/ilmoitukset-ilmoitukset user (::t/urakka-id ilmoitus)))

    ;; jos käyttäjä on urakoitsija, tarkistetaan että urakka on tyhjä tai oman organisaation urakoima
    (when (not (t/voi-tallentaa? user kayttajan-urakat ilmoitus))
      (throw+ (roolit/->EiOikeutta (str "Ei oikeutta kirjata urakkaan"))))

    (let [org (get-in user [:organisaatio :id])
          ilmoitus (-> ilmoitus
                       (m/lisaa-muokkaustiedot ::t/id user)
                       (update-in [::t/osoite ::tr/geometria]
                                  #(when % (geo/geometry (geo/clj->pg %)))))]

      (upsert! db ::t/ilmoitus
               ilmoitus
               (op/or
                 {::m/luoja-id (:id user)}
                 {::t/urakoitsija-id org}
                 {::t/tilaaja-id org}
                 {::t/urakka-id (op/in kayttajan-urakat)})))))


(defn tietyoilmoitus-pdf [db user params]
  (pdf/tietyoilmoitus-pdf
    (first (fetch db ::t/ilmoitus+pituus
                  q-tietyoilmoitukset/ilmoitus-pdf-kentat
                  {::t/id (:id params)}))))

(defn hae-yhteyshenkilo-roolissa [rooli kayttajat]
  (first (filter (fn [k] (and
                           (= rooli (:rooli k))
                           (:ensisijainen k)))
                 kayttajat)))

(defn hae-urakan-yllapitokohdelista [db urakka-id]
  (let [yllapitokohteet (q-yllapitokohteet/hae-kaikki-urakan-yllapitokohteet db {:urakka urakka-id})]
    (mapv (fn [kohde]
            {:yllapitokohde-id (:id kohde)
             :nimi (:nimi kohde)
             :alku (:kohde-alku kohde)
             :loppu (:paallystys-loppu kohde)
             :tr-kaista (:tr-kaista kohde)
             :tr-ajorata (:tr-ajorata kohde)
             :tr-loppuosa (:tr-loppuosa kohde)
             :tr-alkuosa (:tr-alkuosa kohde)
             :tr-loppuetaisyys (:tr-loppuetaisyys kohde)
             :tr-alkuetaisyys (:tr-alkuetaisyys kohde)
             :tr-numero (:tr-numero kohde)
             :geometria (:kohdeosa_sijainti kohde)})
          yllapitokohteet)))

(defn hae-yllapitokohteen-tiedot-tietyoilmoitukselle [db fim user yllapitokohde-id]
  (log/debug "Haetaan ylläpitokohteen " yllapitokohde-id " tiedot tietyöilmoitukselle")
  ;; todo: lisää oikeustarkastus, kun tiedetään mitä tarvitaan
  (let [{:keys [urakka-id
                urakka-sampo-id
                tr-numero
                tr-alkuosa
                tr-alkuetaisyys
                tr-loppuosa
                tr-loppuetaisyys]
         :as yllapitokohde}
        (first (q-tietyoilmoitukset/hae-yllapitokohteen-tiedot-tietyoilmoitukselle db {:kohdeid yllapitokohde-id}))
        geometria (tr-haku/hae-tr-viiva db {:numero tr-numero
                                            :alkuosa tr-alkuosa
                                            :alkuetaisyys tr-alkuetaisyys
                                            :loppuosa tr-loppuosa
                                            :loppuetaisyys tr-loppuetaisyys})
        urakan-yllapitokohteet (hae-urakan-yllapitokohdelista db urakka-id)
        yllapitokohde (assoc yllapitokohde :geometria geometria :kohteet urakan-yllapitokohteet)]
    (if urakka-sampo-id
      (let [kayttajat (q-yhteyshenkilot/hae-urakan-vastuuhenkilot db urakka-id)
            urakoitsijan-yhteyshenkilo (hae-yhteyshenkilo-roolissa "vastuuhenkilo" kayttajat)
            tilaajan-yhteyshenkilo (hae-yhteyshenkilo-roolissa "ELY_Urakanvalvoja" kayttajat)
            yllapitokohde (assoc yllapitokohde :urakoitsijan-yhteyshenkilo urakoitsijan-yhteyshenkilo
                                               :tilaajan-yhteyshenkilo tilaajan-yhteyshenkilo)]
        yllapitokohde)
      yllapitokohde)))

(defn hae-urakan-tiedot-tietyoilmoitukselle [db fim user urakka-id]
  ;; todo: lisää oikeustarkastus, kun tiedetään mitä tarvitaan
  (let [{:keys [urakka-sampo-id] :as urakka}
        (or (first (q-tietyoilmoitukset/hae-urakan-tiedot-tietyoilmoitukselle db {:urakkaid urakka-id})) {})
        urakka (if urakka-sampo-id
                 (let [kayttajat (q-yhteyshenkilot/hae-urakan-vastuuhenkilot db urakka-id)
                       urakoitsijan-yhteyshenkilo (hae-yhteyshenkilo-roolissa "vastuuhenkilo" kayttajat)
                       tilaajan-yhteyshenkilo (hae-yhteyshenkilo-roolissa "ELY_Urakanvalvoja" kayttajat)
                       urakka (assoc urakka :urakoitsijan-yhteyshenkilo urakoitsijan-yhteyshenkilo
                                            :tilaajan-yhteyshenkilo tilaajan-yhteyshenkilo)]
                   urakka)
                 urakka)
        kohdelista (hae-urakan-yllapitokohdelista db urakka-id)]
    (assoc urakka :kohteet kohdelista)))

(s/def ::tietyoilmoitukset (s/coll-of ::t/ilmoitus))

(defrecord Tietyoilmoitukset []
  component/Lifecycle
  (start [{db :db
           http :http-palvelin
           pdf :pdf-vienti
           fim :fim
           :as this}]
    (julkaise-palvelu http :hae-tietyoilmoitukset
                      (fn [user tiedot]
                        (hae-tietyoilmoitukset db user tiedot 501))
                      {:vastaus-spec ::tietyoilmoitukset})
    (julkaise-palvelu http :hae-tietyoilmoitus
                      (fn [user tietyoilmoitus-id]
                        (hae-tietyoilmoitus db user tietyoilmoitus-id))
                      {:vastaus-spec ::t/ilmoitus})
    (julkaise-palvelu http :tallenna-tietyoilmoitus
                      (fn [user ilmoitus]
                        (tallenna-tietyoilmoitus db user ilmoitus))
                      {:kysely-spec ::t/ilmoitus
                       :vastaus-spec ::t/ilmoitus})
    (julkaise-palvelu http :hae-yllapitokohteen-tiedot-tietyoilmoitukselle
                      (fn [user tiedot]
                        (hae-yllapitokohteen-tiedot-tietyoilmoitukselle db fim user tiedot))
                      {:vastaus-spec ::t/hae-yllapitokohteen-tiedot-tietyoilmoitukselle-vastaus})
    (julkaise-palvelu http :hae-urakan-tiedot-tietyoilmoitukselle
                      (fn [user tiedot]
                        (hae-urakan-tiedot-tietyoilmoitukselle db fim user tiedot)))
    (when pdf
      (pdf-vienti/rekisteroi-pdf-kasittelija!
        pdf :tietyoilmoitus (partial #'tietyoilmoitus-pdf db)))
    this)

  (stop [this]
    (poista-palvelut (:http-palvelin this)
                     :hae-tietyoilmoitukset
                     :hae-tietyoilmoitus
                     :tallenna-tietyoilmoitus
                     :hae-yllapitokohteen-tiedot-tietyoilmoitukselle
                     :hae-urakan-tiedot-tietyoilmoitukselle)
    (when (:pdf-vienti this)
      (pdf-vienti/poista-pdf-kasittelija! (:pdf-vienti this) :tietyoilmoitus))
    this))
