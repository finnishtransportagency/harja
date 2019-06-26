(ns harja.palvelin.integraatiot.api.varusteet
  "Varusteiden API-kutsut."
  (:require [com.stuartsierra.component :as component]
            [compojure.core :refer [POST GET DELETE PUT]]
            [taoensso.timbre :as log]
            [harja.palvelin.komponentit.http-palvelin
             :refer [julkaise-reitti julkaise-palvelu poista-palvelut async]]
            [harja.palvelin.integraatiot.api.tyokalut.kutsukasittely :refer [tee-sisainen-kasittelyvirhevastaus
                                                                             tee-viallinen-kutsu-virhevastaus
                                                                             tee-vastaus
                                                                             tee-kirjausvastauksen-body]]
            [harja.palvelin.integraatiot.api.tyokalut.json-skeemat :as json-skeemat]
            [harja.palvelin.integraatiot.api.tyokalut.kutsukasittely :refer [kasittele-kutsu-async]]
            [harja.palvelin.integraatiot.tierekisteri.tierekisteri-komponentti :as tierekisteri]
            [harja.palvelin.integraatiot.api.sanomat.tierekisteri-sanomat :as tierekisteri-sanomat]
            [harja.palvelin.integraatiot.api.validointi.parametrit :as validointi]
            [harja.kyselyt.livitunnisteet :as livitunnisteet-q]
            [harja.kyselyt.geometriapaivitykset :as geometriapaivitykset-q]
            [harja.kyselyt.tieverkko :as tieverkko-q]
            [harja.tyokalut.merkkijono :as merkkijono]
            [harja.pvm :as pvm]
            [clj-time.core :as t]
            [clojure.string :as str]
            [harja.domain.tierekisteri.tietolajit :as tietolajit]
            [harja.domain.oikeudet :as oikeudet]
            [harja.palvelin.integraatiot.api.tyokalut.virheet :as virheet]
            [harja.geo :as geo]
            [harja.tyokalut.xml :as xml]
            [harja.palvelin.integraatiot.vkm.vkm-komponentti :as vkm]
            [clojure.set :as set]
            [harja.palvelin.integraatiot.api.tyokalut.parametrit :as parametrit])
  (:use [slingshot.slingshot :only [try+ throw+]]))

(defn tarkista-parametrit [saadut vaaditut]
  (doseq [{:keys [parametri tyyppi]} vaaditut]
    (if-let [arvo (get saadut parametri)]
      (when tyyppi
        (case tyyppi
          :int (when (not (merkkijono/parsittavissa-intiksi? arvo))
                 (validointi/heita-virheelliset-parametrit-poikkeus (format "Parametri: %s ei ole kokonaisluku. Annettu arvo: %s." parametri arvo)))
          :date (when (not (merkkijono/iso-8601-paivamaara? arvo))
                  (validointi/heita-virheelliset-parametrit-poikkeus (format "Parametri: %s ei ole päivämäärä. Anna arvo muodossa: yyyy-MM-dd. Annettu arvo: %s." parametri arvo)))
          "default"))
      (validointi/heita-virheelliset-parametrit-poikkeus (format "Pakollista parametria: %s ei ole annettu" parametri)))))

(defn tarkista-tietueiden-haun-parametrit [parametrit]
  (tarkista-parametrit
    parametrit
    [{:parametri "tietolajitunniste"
      :tyyppi :string}
     {:parametri "numero"
      :tyyppi :int}
     {:parametri "aosa"
      :tyyppi :int}
     {:parametri "aet"
      :tyyppi :int}
     {:parametri "aet"
      :tyyppi :int}
     {:parametri "let"
      :tyyppi :int}
     {:parametri "voimassaolopvm"
      :tyyppi :date}
     {:parametri "tilannepvm"
      :tyyppi :date}]))

(defn tarkista-tietueen-haun-parametrit [parametrit]
  (tarkista-parametrit
    parametrit
    [{:parametri "tunniste"
      :tyyppi :string}
     {:parametri "tietolajitunniste"
      :tyyppi :string}
     {:parametri "tilannepvm"
      :tyyppi :date}]))

(defn hae-tietolaji-tunnisteella
  "Hakee tietolajin Tierekisteristä tunnisteen perusteella."
  [tierekisteri tunniste]
  (let [vastausdata (tierekisteri/hae-tietolaji tierekisteri tunniste nil)
        ominaisuudet (get-in vastausdata [:tietolaji :ominaisuudet])]
    (tierekisteri-sanomat/muunna-tietolajin-hakuvastaus vastausdata ominaisuudet)))

(defn hae-kaikki-tietolajit [tierekisteri]
  "Hakee kaikkien tietolajien kuvaukset Tierekisteristä tunnisteen perusteella."
  (let [vastausdata (tierekisteri/hae-kaikki-tietolajit tierekisteri nil)]
    (tierekisteri-sanomat/muunna-tietolajien-hakuvastaus vastausdata)))

(defn hae-tietolajit [tierekisteri parametrit kayttaja]
  (oikeudet/ei-oikeustarkistusta!)
  (let [tunniste (get parametrit "tunniste")]
    (if (not (str/blank? tunniste))
      (do
        (log/debug "Haetaan tietolajin: " tunniste " kuvaus käyttäjälle: " kayttaja)
        (hae-tietolaji-tunnisteella tierekisteri tunniste))
      (do
        (log/debug "Haetaan kaikkien tietolajien kuvaukset käyttäjälle: " kayttaja)
        (hae-kaikki-tietolajit tierekisteri)))))

(defn hae-geometria-varusteelle [db {:keys [numero aosa aet losa let] :as tiesijainti}]
  (when (and numero aosa aet)
    (if (and losa let)
      (tieverkko-q/tierekisteriosoite-viivaksi db {:tie numero
                                                   :aosa aosa
                                                   :aet aet
                                                   :losa losa
                                                   :loppuet let})
      (tieverkko-q/tierekisteriosoite-pisteeksi db {:tie numero
                                                    :aosa aosa
                                                    :aet aet}))))

(defn- muodosta-tietueiden-hakuvastaus [tierekisteri db vastausdata]
  {:varusteet
   (mapv
     (fn [tietue]
       (let [tietolaji (get-in tietue [:tietue :tietolaji :tietolajitunniste])
             vastaus (tierekisteri/hae-tietolaji tierekisteri tietolaji nil)
             tietolajin-kuvaus (:tietolaji vastaus)
             arvot (first (get-in tietue [:tietue :tietolaji :arvot]))
             arvot-mappina (tietolajit/tietolajin-arvot-merkkijono->map arvot tietolajin-kuvaus)
             tiesijainti (get-in tietue [:tietue :sijainti :tie])]
         {:varuste
          {:tunniste (get-in tietue [:tietue :tunniste])
           :tietue
           {:sijainti {:tie {:numero (:numero tiesijainti),
                             :aosa (:aosa tiesijainti),
                             :aet (:aet tiesijainti),
                             :losa (:losa tiesijainti),
                             :let (:let tiesijainti),
                             :ajr (:ajr tiesijainti),
                             :puoli (:puoli tiesijainti),
                             :kaista (:kaista tiesijainti)}},
            :alkupvm (get-in tietue [:tietue :alkupvm])
            :loppupvm (get-in tietue [:tietue :loppupvm])
            :karttapvm (get-in tietue [:tietue :karttapvm]),
            :kuntoluokitus (get-in tietue [:tietue :kuntoluokka]),
            :ely (Integer/parseInt (get-in tietue [:tietue :piiri]))
            :tietolaji {:tunniste tietolaji,
                        :arvot arvot-mappina}}}
          :sijainti (geo/pg->clj (hae-geometria-varusteelle db tiesijainti))}))
     (:tietueet vastausdata))})


(defn hae-varusteet [tierekisteri db parametrit kayttaja]
  (tarkista-tietueiden-haun-parametrit parametrit)
  (let [tierekisteriosoite (tierekisteri-sanomat/luo-tierekisteriosoite parametrit)
        tietolajitunniste (get parametrit "tietolajitunniste")
        voimassaolopvm (pvm/iso-8601->pvm (get parametrit "voimassaolopvm"))
        tilannepvm (pvm/iso-8601->pvm (get parametrit "tilannepvm"))]
    (log/debug "Haetaan tietueet tietolajista " tietolajitunniste " voimassaolopäivämäärällä " voimassaolopvm
               ", käyttäjälle " kayttaja " tr osoitteesta: "
               (pr-str tierekisteriosoite) " tilannepäivämäärällä: " tilannepvm)
    (let [vastaus (tierekisteri/hae-tietueet tierekisteri
                                             tierekisteriosoite
                                             tietolajitunniste
                                             voimassaolopvm
                                             tilannepvm)
          muunnettu-vastausdata (muodosta-tietueiden-hakuvastaus tierekisteri db vastaus)]
      (if (> (count (:varusteet muunnettu-vastausdata)) 0)
        muunnettu-vastausdata
        {:varusteet []}))))

(defn hae-varuste [tierekisteri db parametrit kayttaja]
  (tarkista-tietueen-haun-parametrit parametrit)
  (let [tunniste (get parametrit "tunniste")
        tietolajitunniste (get parametrit "tietolajitunniste")
        tilannepvm (pvm/iso-8601->pvm (get parametrit "tilannepvm"))]
    (log/debug "Haetaan tietue tunnisteella " tunniste " tietolajista " tietolajitunniste " kayttajalle " kayttaja)
    (let [vastaus (tierekisteri/hae-tietue tierekisteri tunniste tietolajitunniste tilannepvm)
          muunnettu-vastausdata (muodosta-tietueiden-hakuvastaus tierekisteri db vastaus)]
      (if (> (count (:varusteet muunnettu-vastausdata)) 0)
        muunnettu-vastausdata
        {:varusteet []}))))

(defn muunna-sijainti [vkm db tiedot]
  (let [harjan-karttapvm (geometriapaivitykset-q/harjan-verkon-pvm db)
        karttapvm (some->
                    (get-in tiedot [:varuste :tietue :karttapvm])
                    (parametrit/pvm-aika))
        vanha-sijainti (set/rename-keys (get-in tiedot [:varuste :tietue :sijainti :tie]) {:numero :tie})]
    (if (and karttapvm (not= karttapvm harjan-karttapvm))
      (let [uusi-sijainti (first (vkm/muunna-osoitteet-verkolta-toiselle vkm [vanha-sijainti] karttapvm harjan-karttapvm))
            uusi-sijainti (set/rename-keys uusi-sijainti {:tie :numero})]
        (if uusi-sijainti
          (-> tiedot
              (assoc-in [:varuste :tietue :karttapvm] (pvm/aika-iso8601-aikavyohykkeen-kanssa harjan-karttapvm))
              (assoc-in [:varuste :tietue :sijainti :tie] uusi-sijainti))
          tiedot))
      tiedot)))

(defn lisaa-varuste [tierekisteri vkm db {:keys [otsikko] :as data} kayttaja]
  (log/debug (format "Lisätään varuste käyttäjän: %s pyynnöstä. Data: %s" kayttaja data))
  (let [livitunniste (livitunnisteet-q/hae-seuraava-livitunniste db)
        toimenpiteen-tiedot (muunna-sijainti vkm db (:varusteen-lisays data))
        tietolaji (get-in toimenpiteen-tiedot [:varuste :tietue :tietolaji :tunniste])
        tietolajin-arvot (assoc (get-in toimenpiteen-tiedot [:varuste :tietue :tietolaji :arvot]) :tunniste livitunniste)
        arvot-string (when tietolajin-arvot
                       (tietolajit/validoi-ja-muunna-arvot-merkkijonoksi
                         tierekisteri
                         tietolajin-arvot
                         tietolaji))
        lisayssanoma (tierekisteri-sanomat/luo-tietueen-lisayssanoma
                       otsikko
                       livitunniste
                       toimenpiteen-tiedot
                       arvot-string)]
    (tierekisteri/lisaa-tietue tierekisteri lisayssanoma)
    (tee-kirjausvastauksen-body
      {:id livitunniste
       :ilmoitukset (str "Uusi varuste lisätty onnistuneesti tunnisteella: " livitunniste)})))

(defn paivita-varuste [tierekisteri vkm db {:keys [otsikko] :as data} kayttaja]
  (log/debug (format "Päivitetään varuste käyttäjän: %s pyynnöstä. Data: %s" kayttaja data))
  (let [toimenpiteen-tiedot (muunna-sijainti vkm db (:varusteen-paivitys data))
        tietolaji (get-in toimenpiteen-tiedot [:varuste :tietue :tietolaji :tunniste])
        tietueen-tunniste (get-in toimenpiteen-tiedot [:varuste :tunniste])
        tietueen-arvot (assoc (get-in toimenpiteen-tiedot [:varuste :tietue :tietolaji :arvot]) :tunniste tietueen-tunniste)
        arvot-string (when tietueen-arvot
                       (tietolajit/validoi-ja-muunna-arvot-merkkijonoksi
                         tierekisteri
                         tietueen-arvot
                         tietolaji))
        paivityssanoma (tierekisteri-sanomat/luo-tietueen-paivityssanoma
                         otsikko
                         toimenpiteen-tiedot
                         arvot-string)]
    (tierekisteri/paivita-tietue tierekisteri paivityssanoma))
  (tee-kirjausvastauksen-body {:ilmoitukset "Varuste päivitetty onnistuneesti"}))

(defn poista-varuste [tierekisteri {:keys [otsikko] :as data} kayttaja]
  (log/debug (format "Poistetaan varuste käyttäjän: %s pyynnöstä. Data: %s " kayttaja data))
  (let [poistosanoma (tierekisteri-sanomat/luo-tietueen-poistosanoma
                       otsikko
                       (:varusteen-poisto data))]
    (tierekisteri/poista-tietue tierekisteri poistosanoma))
  (tee-kirjausvastauksen-body {:ilmoitukset "Varuste poistettu onnistuneesti"}))

(defn hae-varusteita
  "Käsittelee UI:lta tulleen varustehaun: hakee joko tunnisteen tai tietolajin ja
  tierekisteriosoitteen perusteella"
  [user tierekisteri db {:keys [tunniste tierekisteriosoite tietolaji voimassaolopvm] :as tiedot}]
  (oikeudet/ei-oikeustarkistusta!)
  (log/debug "Haetaan varusteita Tierekisteristä: " (pr-str tiedot))

  (try+
    (let [nyt (t/now)
          tulos
          (cond
            (not (str/blank? tunniste))
            (tierekisteri/hae-tietue tierekisteri tunniste tietolaji nyt)

            (and tierekisteriosoite
                 (not (str/blank? tietolaji)))
            (tierekisteri/hae-tietueet
              tierekisteri
              {:numero (:numero tierekisteriosoite)
               :aet (:alkuetaisyys tierekisteriosoite)
               :aosa (:alkuosa tierekisteriosoite)
               :let (:loppuetaisyys tierekisteriosoite)
               :losa (:loppuosa tierekisteriosoite)}
              tietolaji
              (if voimassaolopvm
                (pvm/joda-timeksi voimassaolopvm)
                nyt)
              nyt)

            :default
            {:error "Ei hakuehtoja"})]

      (if (:onnistunut tulos)
        (merge {:onnistunut true}
               (hae-tietolaji-tunnisteella tierekisteri tietolaji)
               (muodosta-tietueiden-hakuvastaus tierekisteri db tulos))
        tulos))

    (catch [:type virheet/+ulkoinen-kasittelyvirhe-koodi+] {:keys [virheet]}
      {:onnistunut false
       :virheet virheet})))

(defrecord Varusteet []
  component/Lifecycle
  (start [{http :http-palvelin db :db integraatioloki :integraatioloki tierekisteri :tierekisteri vkm :vkm :as this}]
    (julkaise-reitti
      http :hae-tietolaji
      (GET "/api/varusteet/tietolaji" request
        (kasittele-kutsu-async db integraatioloki :hae-tietolaji request nil json-skeemat/tietolajien-haku
                               (fn [parametrit _ kayttaja _]
                                 (validointi/tarkista-ominaisuus :varuste-api)
                                 (validointi/tarkista-ominaisuus :tierekisteri)
                                 (hae-tietolajit tierekisteri parametrit kayttaja)))))

    (julkaise-reitti
      http :hae-tietueet
      (GET "/api/varusteet/haku" request
        (kasittele-kutsu-async db integraatioloki :hae-tietueet request nil json-skeemat/varusteiden-haku-vastaus
                               (fn [parametrit _ kayttaja _]
                                 (validointi/tarkista-ominaisuus :varuste-api)
                                 (validointi/tarkista-ominaisuus :tierekisteri)
                                 (hae-varusteet tierekisteri db parametrit kayttaja)))))

    (julkaise-reitti
      http :hae-tietue
      (GET "/api/varusteet/varuste" request
        (kasittele-kutsu-async db integraatioloki :hae-tietue request nil json-skeemat/varusteiden-haku-vastaus
                               (fn [parametrit _ kayttaja _]
                                 (validointi/tarkista-ominaisuus :varuste-api)
                                 (validointi/tarkista-ominaisuus :tierekisteri)
                                 (hae-varuste tierekisteri db parametrit kayttaja)))))

    (julkaise-reitti
      http :lisaa-tietue
      (POST "/api/varusteet/varuste" request
        (kasittele-kutsu-async db integraatioloki :lisaa-tietue request json-skeemat/varusteen-lisays json-skeemat/kirjausvastaus
                               (fn [_ data kayttaja _]
                                 (validointi/tarkista-ominaisuus :varuste-api)
                                 (validointi/tarkista-ominaisuus :tierekisteri)
                                 (lisaa-varuste tierekisteri vkm db data kayttaja)))))

    (julkaise-reitti
      http :paivita-tietue
      (PUT "/api/varusteet/varuste" request
        (kasittele-kutsu-async db integraatioloki :paivita-tietue request json-skeemat/varusteen-paivitys json-skeemat/kirjausvastaus
                               (fn [_ data kayttaja _]
                                 (validointi/tarkista-ominaisuus :varuste-api)
                                 (validointi/tarkista-ominaisuus :tierekisteri)
                                 (paivita-varuste tierekisteri vkm db data kayttaja)))))

    (julkaise-reitti
      http :poista-tietue
      (DELETE "/api/varusteet/varuste" request
        (kasittele-kutsu-async db integraatioloki :poista-tietue request json-skeemat/varusteen-poisto json-skeemat/kirjausvastaus
                               (fn [_ data kayttaja _]
                                 (validointi/tarkista-ominaisuus :varuste-api)
                                 (validointi/tarkista-ominaisuus :tierekisteri)
                                 (poista-varuste tierekisteri data kayttaja)))))

    (julkaise-palvelu http :hae-tietolajin-kuvaus
                      (fn [user tietolaji]
                        (let [tietolajit (hae-tietolajit tierekisteri {"tunniste" tietolaji} user)]
                          (:tietolaji (first (:tietolajit tietolajit))))))

    (julkaise-palvelu http :hae-varusteita
                      (fn [user tiedot]
                        (async
                          (hae-varusteita user tierekisteri db tiedot))))
    this)

  (stop [{http :http-palvelin :as this}]
    (poista-palvelut http
                     :hae-tietolaji
                     :hae-tietueet
                     :hae-tietue
                     :lisaa-tietue
                     :paivita-tietue
                     :poista-tietue
                     :hae-tietolajin-kuvaus
                     :hae-varusteita)
    this))
