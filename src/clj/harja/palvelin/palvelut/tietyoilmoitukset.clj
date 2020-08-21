(ns harja.palvelin.palvelut.tietyoilmoitukset
  (:require
    [clj-time.coerce :as c :refer [from-sql-time]]
    [clojure.core.async :as async]
    [clojure.spec.alpha :as s]
    [clojure.string :as clj-str]
    [com.stuartsierra.component :as component]
    [slingshot.slingshot :refer [throw+ try+]]
    [specql.core :refer [fetch upsert!]]
    [specql.op :as op]
    [taoensso.timbre :as log]

    [harja.domain.tietyoilmoituksen-email :as tietyoilmoituksen-e]
    [harja.domain.muokkaustiedot :as m]
    [harja.domain.oikeudet :as oikeudet]
    [harja.domain.roolit :as roolit]
    [harja.domain.tierekisteri :as tr]
    [harja.domain.tietyoilmoitus :as t]
    [harja.geo :as geo]
    [harja.kyselyt.konversio :as konv]
    [harja.kyselyt.tietyoilmoituksen-email :as q-tietyoilmoituksen-e]
    [harja.kyselyt.tietyoilmoitukset :as q-tietyoilmoitukset]
    [harja.kyselyt.yhteyshenkilot :as q-yhteyshenkilot]
    [harja.kyselyt.yllapitokohteet :as q-yllapitokohteet]
    [harja.palvelin.integraatiot.api.tyokalut.virheet :as virheet]
    [harja.palvelin.integraatiot.sahkoposti :as sahkoposti]
    [harja.palvelin.integraatiot.tloik.tloik-komponentti :as tloik]
    [harja.palvelin.komponentit.fim :as fim]
    [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelut async]]
    [harja.palvelin.komponentit.pdf-vienti :as pdf-vienti]
    [harja.palvelin.komponentit.tapahtumat :as tapahtumat]
    [harja.palvelin.palvelut.kayttajatiedot :as kayttajatiedot]
    [harja.palvelin.palvelut.pois-kytketyt-ominaisuudet :as ominaisuudet]
    [harja.palvelin.palvelut.tierekisteri-haku :as tr-haku]
    [harja.palvelin.palvelut.tietyoilmoitukset.pdf :as pdf]
    [harja.palvelin.palvelut.viestinta :as viestinta]
    [harja.pvm :as pvm]
    [clojure.java.jdbc :as jdbc])
  (:import (org.postgresql.util PSQLException)))

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
                                             urakka-id
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
                          :urakat (if urakka-id
                                    #{urakka-id}
                                    kayttajan-urakat)
                          :urakattomat? (nil? urakka-id)
                          :luojaid (when vain-kayttajan-luomat (:id user))
                          :sijainti (when sijainti (geo/geometry (geo/clj->pg sijainti)))
                          :maxmaara max-maara
                          :organisaatio (:id (:organisaatio user))}
        tietyoilmoitukset (q-tietyoilmoitukset/hae-ilmoitukset db kyselyparametrit)]
    tietyoilmoitukset))

(defn hae-ilmoituksen-sahkopostitiedot [db user {urakka-id ::t/urakka-id ilmoitus-id ::t/id email-id ::tietyoilmoituksen-e/id}]
  (oikeudet/vaadi-lukuoikeus oikeudet/ilmoitukset-ilmoitukset user urakka-id)
  (let [{ilmoituksen-emailit ::t/email-lahetykset} (first (q-tietyoilmoitukset/hae-sahkopostitiedot db {::t/id ilmoitus-id}))
        ilmoituksen-emailit (if (nil? ilmoituksen-emailit) '() ilmoituksen-emailit)]
    (if email-id
      (some #(when (= (::tietyoilmoituksen-e/id %) email-id)
               %)
            ilmoituksen-emailit)
      ilmoituksen-emailit)))

(defn hae-tietyoilmoitus [db user tietyoilmoitus-id]
  ;; todo: lisää oikeustarkistus, kun tiedetään miten se pitää tehdä
  (oikeudet/ei-oikeustarkistusta!)
  (q-tietyoilmoitukset/hae-ilmoitus db tietyoilmoitus-id))

(defn laheta-viesti-harjasta!
  "Wrapper funktio, joka hoitaa mahdolliset virhetilanteet sähköpostin lähetyksessä.
   Tallentaa myös lähetetyn viestin tiedot kantaan. Palauttaa true, jos viestin lähetys
   onnistui ja false, jos se epäonnistui."
  [db lahetys-fn mailin-tiedot]
  (let [viesti-id-atom (atom nil)]
    (try+ (do (reset! viesti-id-atom (:viesti-id (lahetys-fn)))
              (q-tietyoilmoituksen-e/tallenna-lahetetyn-emailin-tiedot
                db
                (merge mailin-tiedot
                       {::tietyoilmoituksen-e/lahetysid @viesti-id-atom}))
              true)
          (catch [:type virheet/+sisainen-kasittelyvirhe-koodi+] {:keys [viesti-id]}
            (reset! viesti-id-atom viesti-id)
            (try (q-tietyoilmoituksen-e/tallenna-lahetetyn-emailin-tiedot
                   db
                   (merge mailin-tiedot
                          {::tietyoilmoituksen-e/lahetysid @viesti-id
                           ::tietyoilmoituksen-e/lahetysvirhe (pvm/nyt)}))
                 (catch PSQLException e
                   nil))
            false)
          (catch PSQLException e
            ;; TODO Sähköpostin lähetystä ei tallenata kantaan.
            (log/error (str "Sähköposti lähetettiin viesti-id:llä: " @viesti-id-atom
                            ", mutta sitä ei tallennettu kantaan " (.getMessage e)))
            true))))

(defn laheta-tietyoilmoituksen-pdf-sahkopostitse
  "Lähettää tietyöilmoituksen PDF:n sähköpostitse"
  [{:keys [email vastaanottaja muut-vastaanottajat kopio-itselle?
           viestin-otsikko viestin-vartalo pdf saate db
           tietyoilmoitus-id ilmoittaja tiedostonimi] :as params}]
  (log/debug " Palvelu: laheta-tietyoilmoituksen-pdf-sahkopostitse, params " params)
  (try
    (let [viestin-vartalo (if (empty? saate)
                            viestin-vartalo
                            (str viestin-vartalo "\nSaate:\n" saate))
          vastaanottajat (if (not (empty? muut-vastaanottajat))
                           (conj muut-vastaanottajat vastaanottaja)
                           [vastaanottaja])
          ;; varsinainen lähetys Tieliikennekeskukseen
          lahetetty-harjasta? (laheta-viesti-harjasta! db
                                                       #(sahkoposti/laheta-viesti-ja-liite!
                                                          email
                                                          (sahkoposti/vastausosoite email)
                                                          vastaanottajat
                                                          (str "Harja: " viestin-otsikko)
                                                          {:viesti viestin-vartalo
                                                           :pdf-liite pdf}
                                                          tiedostonimi)
                                                       {::tietyoilmoituksen-e/tietyoilmoitus-id tietyoilmoitus-id
                                                        ::tietyoilmoituksen-e/tiedostonimi tiedostonimi
                                                        ::tietyoilmoituksen-e/lahetetty (pvm/nyt)
                                                        ::tietyoilmoituksen-e/lahettaja-id (:id ilmoittaja)})]
      (log/debug " Lähetys tehty")

      ;; kopio mailitsta itselle
      (when (and kopio-itselle? (:sahkoposti ilmoittaja) lahetetty-harjasta?)
        (try+ (viestinta/laheta-sahkoposti-itselle
               {:email email
                :kopio-viesti "Tämä viesti on kopio sähköpostista, joka lähettiin Harjasta\n-----\n"
                :sahkoposti [(:sahkoposti ilmoittaja)]
                :viesti-otsikko viestin-otsikko
                :viesti-body viestin-vartalo
                :liite pdf
                :tiedostonimi tiedostonimi})
             (catch [:type virheet/+sisainen-kasittelyvirhe-koodi+] {:keys [virheet viesti-id]}
               false))
        (log/debug " Lähetys itselle tehty osoitteeseen " (:sahkoposti ilmoittaja))))

    (catch Exception e
      (log/error e (format "Tietyöilmoituksen (id: %s) lähetyksessä sähköpostitse T-LOIK:n tapahtui poikkeus." tietyoilmoitus-id))
      :sahkopostilahetys-epaonnistui)))

(defn tietyoilmoitus-pdf [db user params]
  (let [{ilmoituksen-emailit ::t/email-lahetykset}
        (first (q-tietyoilmoitukset/hae-sahkopostitiedot db {::t/id (:id params)}))]
    (pdf/tietyoilmoitus-pdf
      (assoc
        (first (fetch db ::t/ilmoitus+pituus
                      q-tietyoilmoitukset/ilmoitus-pdf-kentat
                      {::t/id (:id params)}))
        ;; Vasta kun Tieliikennekeskuksesta on tullut kuittaus, että ilmoitus on kertaalleen tullut perille,
        ;; voidaan jatkossa PDF:ssä käyttää ilmoituksessa termiä Muutos aiempaan VHAR-2465
        :lahetetty? (boolean (some :harja.domain.tietyoilmoituksen-email/kuitattu ilmoituksen-emailit))))))

(defn tallenna-tietyoilmoitus [tloik db email pdf user ilmoitus sahkopostitiedot]
  (log/debug "PALVELU: Tallenna tietyöilmoitus" ilmoitus " sahkopostitiedot " sahkopostitiedot " email " email)
  (let [kayttajan-urakat (urakat db user oikeudet/voi-kirjoittaa?)]
    (if (::t/urakka-id ilmoitus)
      (oikeudet/vaadi-kirjoitusoikeus oikeudet/ilmoitukset-ilmoitukset user (::t/urakka-id ilmoitus))
      (oikeudet/ei-oikeustarkistusta!))

    ;; jos käyttäjä on urakoitsija, tarkistetaan että urakka on tyhjä tai oman organisaation urakoima
    (when (not (t/voi-tallentaa? user kayttajan-urakat ilmoitus))
      (throw+ (roolit/->EiOikeutta (str "Ei oikeutta kirjata urakkaan"))))

    (let [org (get-in user [:organisaatio :id])
          ilmoitus (-> ilmoitus
                       (m/lisaa-muokkaustiedot ::t/id user)
                       (update-in [::t/osoite ::tr/geometria]
                                  #(when % (geo/geometry (geo/clj->pg %)))))
          tallennettu (jdbc/with-db-transaction [db db]
                        (let [tallennettu (upsert! db ::t/ilmoitus
                                                   ilmoitus
                                                   (op/or
                                                     {::m/luoja-id (:id user)}
                                                     {::t/urakoitsija-id org}
                                                     {::t/tilaaja-id org}
                                                     {::t/urakka-id (op/in kayttajan-urakat)}))
                              ilmoituksen-sahkopostitiedot (hae-ilmoituksen-sahkopostitiedot db user (select-keys tallennettu [::t/urakka-id ::t/id]))]
                          (merge tallennettu {::t/email-lahetykset ilmoituksen-sahkopostitiedot})))]
      (when (and (not (empty? sahkopostitiedot))
                 (ominaisuudet/ominaisuus-kaytossa? :tietyoilmoitusten-lahetys))
        (async/thread
          (let [{pdf-bytet :tiedosto-bytet
                 tiedostonimi :tiedostonimi} (pdf-vienti/luo-pdf pdf :tietyoilmoitus user {:id (::t/id tallennettu)})
                viestin-otsikko (-> tiedostonimi
                                    (clj-str/replace #".pdf" "")
                                    (clj-str/replace #"-" " ")
                                    (clj-str/replace #"\d \d" "-"))
                viestin-vartalo "Liitteenä on HARJA:ssa tehty tietyöilmoitus."]
            (laheta-tietyoilmoituksen-pdf-sahkopostitse {:email email
                                                         :db db
                                                         :vastaanottaja (:vastaanottaja sahkopostitiedot)
                                                         :muut-vastaanottajat (:muut-vastaanottajat sahkopostitiedot)
                                                         :kopio-itselle? (:kopio-itselle? sahkopostitiedot)
                                                         :saate (:saate sahkopostitiedot)
                                                         :viestin-otsikko viestin-otsikko
                                                         :viestin-vartalo viestin-vartalo
                                                         :pdf pdf-bytet
                                                         :tietyoilmoitus-id (::t/id tallennettu)
                                                         :ilmoittaja user
                                                         :tiedostonimi tiedostonimi}))))
      tallennettu)))

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

(defn hae-yllapitokohteen-tiedot-tietyoilmoitukselle [db fim user {:keys [yllapitokohde-id valittu-urakka-id]}]
  (log/debug "Haetaan ylläpitokohteen " yllapitokohde-id " tiedot tietyöilmoitukselle, valittu-urakka-id: " valittu-urakka-id)
  ;; todo: lisää oikeustarkastus, kun tiedetään mitä tarvitaan
  (oikeudet/ei-oikeustarkistusta!)
  (let [{:keys [urakka-id
                urakka-sampo-id
                tr-numero
                tr-alkuosa
                tr-alkuetaisyys
                tr-loppuosa
                tr-loppuetaisyys]
         :as yllapitokohde}
        (first (q-tietyoilmoitukset/hae-yllapitokohteen-tiedot-tietyoilmoitukselle db {:kohdeid yllapitokohde-id
                                                                                       :valittu_urakka_id valittu-urakka-id}))
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
  (oikeudet/ei-oikeustarkistusta!)
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
  (start [{tloik :tloik
           db :db
           http :http-palvelin
           pdf :pdf-vienti
           fim :fim
           email :sonja-sahkoposti
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
                      (fn [user {:keys [ilmoitus sahkopostitiedot] :as tiedot}]
                        (tallenna-tietyoilmoitus tloik db email pdf user ilmoitus sahkopostitiedot))
                      {:kysely-spec ::t/ilmoitus
                       :vastaus-spec ::t/ilmoitus})
    (julkaise-palvelu http :hae-yllapitokohteen-tiedot-tietyoilmoitukselle
                      (fn [user tiedot]
                        (hae-yllapitokohteen-tiedot-tietyoilmoitukselle db fim user tiedot))
                      {:vastaus-spec ::t/hae-yllapitokohteen-tiedot-tietyoilmoitukselle-vastaus})
    (julkaise-palvelu http :hae-urakan-tiedot-tietyoilmoitukselle
                      (fn [user tiedot]
                        (hae-urakan-tiedot-tietyoilmoitukselle db fim user tiedot)))
    (julkaise-palvelu http :hae-ilmoituksen-sahkopostitiedot
                      (fn [user tiedot]
                        (hae-ilmoituksen-sahkopostitiedot db user tiedot)))
    (when pdf
      (pdf-vienti/rekisteroi-pdf-kasittelija!
        pdf :tietyoilmoitus (partial #'tietyoilmoitus-pdf db)))
    (when (ominaisuudet/ominaisuus-kaytossa? :tietyoilmoitusten-lahetys)
      (tapahtumat/kuuntele! email
                            (-> email :jonot :sahkoposti-ja-liite-ulos-kuittausjono)
                            (fn [{:keys [viesti-id aika onnistunut]} _ _]
                              (q-tietyoilmoituksen-e/paivita-lahetetyn-emailin-tietoja db
                                                                                       (merge {::tietyoilmoituksen-e/kuitattu aika}
                                                                                              (when-not onnistunut
                                                                                                {::tietyoilmoituksen-e/lahetysvirhe aika}))
                                                                                       {::tietyoilmoituksen-e/lahetysid viesti-id}))))
    this)

  (stop [this]
    (poista-palvelut (:http-palvelin this)
                     :hae-tietyoilmoitukset
                     :hae-tietyoilmoitus
                     :tallenna-tietyoilmoitus
                     :hae-yllapitokohteen-tiedot-tietyoilmoitukselle
                     :hae-urakan-tiedot-tietyoilmoitukselle
                     :hae-ilmoituksen-sahkopostitiedot)
    (when (:pdf-vienti this)
      (pdf-vienti/poista-pdf-kasittelija! (:pdf-vienti this) :tietyoilmoitus))
    this))
