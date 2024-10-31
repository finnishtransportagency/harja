(ns harja.palvelin.integraatiot.api.paikkaukset
  "Paikkausten ja niiden kustannusten hallinta API:n kautta"
  (:require [com.stuartsierra.component :as component]
            [compojure.core :refer [POST DELETE]]
            [slingshot.slingshot :refer [try+ throw+]]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-reitti poista-palvelut]]
            [harja.palvelin.integraatiot.api.tyokalut.kutsukasittely :refer [kasittele-kutsu tee-kirjausvastauksen-body]]
            [harja.palvelin.integraatiot.api.tyokalut.json-skeemat :as json-skeemat]
            [harja.palvelin.integraatiot.api.tyokalut.validointi :as validointi]
            [harja.palvelin.integraatiot.api.sanomat.paikkaussanoma :as paikkaussanoma]
            [harja.palvelin.integraatiot.api.sanomat.paikkaustoteumasanoma :as paikkaustoteumasanoma]
            [harja.palvelin.integraatiot.yha.yha-paikkauskomponentti :as yha-paikkauskomponentti]
            [harja.kyselyt.paikkaus :as paikkaus-q]
            [harja.kyselyt.tieverkko :as tieverkko-q]
            [harja.domain.tierekisteri.validointi :as tr-validointi]
            [harja.domain.paikkaus :as paikkaus]
            [harja.palvelin.palvelut.yllapitokohteet.reikapaikkaukset :as reikapaikkaus-palvelu]
            [taoensso.timbre :as log]
            [clojure.java.jdbc :as jdbc]
            [harja.kyselyt.paikkaus :as q-paikkaus]
            [specql.op :as op]
            [harja.palvelin.integraatiot.api.tyokalut.virheet :as virheet]
            [harja.validointi :refer [onko-koordinaatit-suomen-alueella?]])
  (:use [slingshot.slingshot :only [throw+]]))

(defn- poista-paikkaustoteumat
  "Merkitsee poistetuksi paikkaustoteumat eli paikkauskustannukset."
  [db urakka-id kayttaja-id paikkaustoteumat]
    (paikkaus-q/paivita-paikkaustoteumat-poistetuksi db kayttaja-id urakka-id paikkaustoteumat))

(defn- poista-paikkaukset
  "Merkitsee poistetuksi paikkaukset."
  [db urakka-id kayttaja-id paikkaukset]
    (paikkaus-q/paivita-paikkaukset-poistetuksi db kayttaja-id urakka-id paikkaukset))

(defn- poista-paikkauskohteet
  "Merkitsee poistetuksi paikkauskohteet sekä niistä riippuvaiset paikkaukset ja paikkaustoteumat eli paikkauskustannukset."
  [db urakka-id kayttaja-id paikkauskohteet]
  (paikkaus-q/paivita-paikkauskohteet-poistetuksi db kayttaja-id urakka-id paikkauskohteet))

(defn poista-paikkaustiedot [db yhap {id :id} data kayttaja]
  (log/debug (format "Poistetaan paikkaustietoja urakasta: %s käyttäjän: %s toimesta"
                     id kayttaja))
  (let [urakka-id (Integer/parseInt id)
        kayttaja-id (:id kayttaja)
        kohde-idt (:poistettavat-paikkauskohteet data)
        kohteet (q-paikkaus/hae-paikkauskohteet db {::paikkaus/ulkoinen-id (op/in kohde-idt)
                                                    :harja.domain.paikkaus/urakka-id urakka-id})]
    (when (empty? kohteet)
      (virheet/heita-ei-hakutuloksia-apikutsulle-poikkeus
        {:koodi virheet/+ei-hakutuloksia+
         :viesti "Annetulla kohde id:llä ei löydy kohdetta."}))

    (validointi/tarkista-urakka-ja-kayttaja db urakka-id kayttaja)
    (jdbc/with-db-transaction [tx db]
      (poista-paikkauskohteet tx urakka-id kayttaja-id kohde-idt)
      (poista-paikkaukset tx urakka-id kayttaja-id (:poistettavat-paikkaukset data))
      (poista-paikkaustoteumat tx urakka-id kayttaja-id (:poistettavat-paikkauskustannukset data)))
    (doseq [kohde-id kohde-idt]
      (try+
        (when-let [harja-id (paikkaus-q/hae-paikkauskohteen-harja-id db {:ulkoinen-id kohde-id})]
          (yha-paikkauskomponentti/poista-paikkauskohde yhap urakka-id harja-id))
        (catch [:type yha-paikkauskomponentti/+virhe-paikkauskohteen-poistossa+] {:keys [virheet]}
          (log/error "Poista paikkauskohde YHA:sta epäonnistui, tiedot: " (pr-str virheet))))))
  (tee-kirjausvastauksen-body {:ilmoitukset "Paikkauskohteet ja -kustannukset poistettu onnistuneesti"}))


(defn tallenna-paikkaus [db urakka-id kayttaja-id {paikkaukset :paikkaukset}]
  (let [paikkaukset (map #(paikkaussanoma/api->domain urakka-id (:paikkaus %)) paikkaukset)]
    (doseq [paikkaus paikkaukset]
      (paikkaus-q/tallenna-paikkaus db urakka-id kayttaja-id paikkaus))))

(defn tallenna-paikkaustoteuma
  "Tallentaa paikkauskustannuksiin liittyvät tiedot. Poistaa sitä ennen kannasta."
  [db urakka-id kayttaja-id {paikkauskustannukset :paikkauskustannukset}]
  (let [toteumat (map #(paikkaustoteumasanoma/api->domain urakka-id (:paikkauskustannus %)) paikkauskustannukset)]
    (doseq [[ulkoinen-id toteumat] (group-by ::paikkaus/ulkoinen-id (apply concat toteumat))]
      (paikkaus-q/poista-paikkaustoteuma db kayttaja-id urakka-id ulkoinen-id)
      (doseq [toteuma toteumat]
        (paikkaus-q/tallenna-paikkaustoteuma db urakka-id kayttaja-id toteuma)))))

(defn kirjaa-paikkaus [db {id :id} data kayttaja]
  (log/debug (format "Kirjataan paikkauksia: %s kpl urakalle: %s käyttäjän: %s toimesta"
                     (count (:paikkaukset data)) id kayttaja))
  (let [urakka-id (Integer/parseInt id)
        kayttaja-id (:id kayttaja)
        paikkaukset (map #(paikkaussanoma/api->domain urakka-id (:paikkaus %)) (:paikkaukset data))
        ;; Valitoidaan tierekisteriosoite
        validointivirheet (into [] (remove nil?
                                           (reduce (fn [virheet p]
                                                     (let [tro (:harja.domain.paikkaus/tierekisteriosoite p)
                                                           virhe (tr-validointi/validoi-tieosoite
                                                                   #{} (:harja.domain.tierekisteri/tie tro)
                                                                   (:harja.domain.tierekisteri/aosa tro)
                                                                   (:harja.domain.tierekisteri/losa tro)
                                                                   (:harja.domain.tierekisteri/aet tro)
                                                                   (:harja.domain.tierekisteri/let tro))]
                                                       (when-not (empty? virhe)
                                                         (conj virheet virhe))))
                                                   nil
                                                   paikkaukset)))]
    (validointi/tarkista-urakka-ja-kayttaja db urakka-id kayttaja)
    (when-not (empty? validointivirheet)
      (throw+ {:type virheet/+viallinen-kutsu+
               :virheet [{:koodi virheet/+invalidi-json+
                          :viesti (str "Json aineistosta löytyi virhe: " validointivirheet)}]}))
    (tallenna-paikkaus db urakka-id kayttaja-id data))
  (tee-kirjausvastauksen-body {:ilmoitukset "Paikkaukset kirjattu onnistuneesti"}))

(defn kirjaa-reikapaikkaus [db {id :id} data kayttaja]
  (log/debug (format "Kirjataan reikäpaikkauksia: %s kpl urakalle: %s käyttäjän: %s toimesta"
               (count (:reikapaikkaukset data)) id kayttaja))

  (let [urakka-id (Integer/parseInt id)
        _ (validointi/tarkista-urakka-ja-kayttaja db urakka-id kayttaja)
        reikapaikkaukset (:reikapaikkaukset data)

        ;; Valitoidaan tierekisteriosoite - mikäli se on annettu
        validointivirheet []
        validointivirheet
        (into [] (remove nil?
                   (reduce (fn [virheet rp]
                             (let [tro (get-in rp [:reikapaikkaus :sijainti :tieosoite])
                                   virhe (when-not (nil? tro)
                                           (tr-validointi/validoi-tieosoite
                                             #{} (:numero tro) (:aosa tro) (:losa tro) (:aet tro) (:let tro)))]
                               (when-not (empty? virhe)
                                 (conj virheet virhe))))
                     validointivirheet
                     reikapaikkaukset)))

        ;; Validoidaan pistegeometria, mikäli se on annettu
        ;;TODO: Tee validointi loppuun
        validointivirheet
        (into [] (remove nil?
                   (reduce (fn [virheet rp]
                             (let [piste (get-in rp [:reikapaikkaus :sijainti :pistegeometria])
                                   suomessa? (if piste
                                               (onko-koordinaatit-suomen-alueella? (:x piste) (:y piste))
                                               true)
                                   tieosoite (if piste
                                               (first (tieverkko-q/hae-tr-osoite db {:x (:x piste)
                                                                                               :y (:y piste)
                                                                                               :treshold 250}))
                                               {})
                                   virheet (cond-> virheet
                                             (not suomessa?)
                                             (conj "Pisteei ole suomen alueella. Virheellinen piste (" (:x piste) "," (:y piste) ")")

                                             (and suomessa? (nil? tieosoite))
                                             (conj "Piste ei ole tieverkolla: " piste))]
                               virheet))
                     validointivirheet
                     reikapaikkaukset)))

        ;; Validoidaan viivageometria, mikäli se on annettu
        validointivirheet
        (into [] (remove nil?
                   (reduce (fn [virheet rp]
                             (let [geometriat (get-in rp [:reikapaikkaus :sijainti :viivageometria :coordinates])
                                   ;; Piirretään viiva (ainakin tässä vaiheessa) pelkästään ensimmäisen ja viimeisen pisteen välille
                                   viivapisteet (if geometriat
                                                  (let [ensimmainen (first geometriat)
                                                        viimeinen (last geometriat)]
                                                    (list ensimmainen viimeinen))
                                                  [])
                                   gvirheet (reduce (fn [virheet geometria]
                                                      (let [suomessa? (onko-koordinaatit-suomen-alueella? (first geometria) (second geometria))
                                                            tieosoite (first (tieverkko-q/hae-tr-osoite db {:x (first geometria)
                                                                                                            :y (second geometria)
                                                                                                            :treshold 250}))
                                                            virheet (cond-> virheet
                                                                      (not suomessa?)
                                                                      (conj "Viivageometrian koordinaattipiste, ei ole suomen alueella. Virheellinen piste (" (first geometria) "," (second geometria) ")")

                                                                      (and suomessa? (nil? tieosoite))
                                                                      (conj "Piste ei ole tieverkolla: (" (first geometria) "," (second geometria) ")"))]
                                                        virheet))
                                              virheet viivapisteet)]
                               (when-not (empty? gvirheet)
                                 (conj virheet gvirheet))))
                     validointivirheet
                     reikapaikkaukset)))

        ;; Työmenetelmät tietokannasta
        tyomenetelmat (paikkaus-q/hae-paikkauskohteiden-tyomenetelmat db)

        ;; Täydennä reikapaikkaus tarvittavilla tiedoilla
        reikapaikkaukset (map :reikapaikkaus reikapaikkaukset)

        reikapaikkaukset (map
                           (fn [r]
                             (let [;; Käytä ensisijaisesti tieosoitetta, mikäli se on annettu
                                  tieosoite (when (get-in r [:sijainti :tieosoite])
                                               {:tie (get-in r [:sijainti :tieosoite :numero])
                                                :aosa (get-in r [:sijainti :tieosoite :aosa])
                                                :losa (get-in r [:sijainti :tieosoite :losa])
                                                :aet (get-in r [:sijainti :tieosoite :aet])
                                                :let (get-in r [:sijainti :tieosoite :let])})
                                   tieosoite (if (and (not tieosoite) (get-in r [:sijainti :pistegeometria]))
                                               (let [piste (get-in r [:sijainti :pistegeometria])
                                                     t (first (tieverkko-q/hae-tr-osoite db {:x (:x piste)
                                                                                             :y (:y piste)
                                                                                             :treshold 250}))
                                                     ;; Tieosoitteessa ei ole let ja losa arvoja, koska se haettiin pisteellä.
                                                     ;; Reikäpaikkaus olettaa, että ne on annettu, joten täytetään ne käsin
                                                     t (assoc t :losa (:aosa t) :let (:aet t))]
                                                 t)
                                               tieosoite)
                                   tieosoite (if (and (not tieosoite) (get-in r [:sijainti :viivageometria]))
                                               (let [ensimmainen (first (get-in r [:sijainti :viivageometria :coordinates]))
                                                     viimeinen (last (get-in r [:sijainti :viivageometria :coordinates]))
                                                     e (first (tieverkko-q/hae-tr-osoite db {:x (first ensimmainen)
                                                                                             :y (second ensimmainen)
                                                                                             :treshold 250}))
                                                     v (first (tieverkko-q/hae-tr-osoite db {:x (first viimeinen)
                                                                                             :y (second viimeinen)
                                                                                             :treshold 250}))
                                                     ;; Otetaan loppuosa viimeisestä pisteestä - Mutta koska pisteellä ei ole let tai losa arvoja
                                                     ;; Niin käytetään osa ja aet arvoja
                                                     t (assoc e :losa (:aosa v) :let (:aet v))]
                                                 t)
                                               tieosoite)

                                   tyomenetelma-id (paikkaus/tyomenetelma-id (:tyomenetelma r) tyomenetelmat)

                                   r
                                   (-> r

                                     (assoc :tie (:tie tieosoite))
                                     (assoc :aosa (:aosa tieosoite))
                                     (assoc :aet (:aet tieosoite))
                                     (assoc :losa (:losa tieosoite))
                                     (assoc :let (:let tieosoite))
                                     (assoc :urakka-id urakka-id)
                                     (assoc :tyomenetelma-id tyomenetelma-id)
                                     (dissoc :sijainti)
                                     (assoc :tunniste (get-in (first reikapaikkaukset) [:tunniste :id])))]
                               r))
                           reikapaikkaukset)]

    (when-not (empty? validointivirheet)
      (throw+ {:type virheet/+viallinen-kutsu+
               :virheet [{:koodi virheet/+invalidi-json+
                          :viesti (str "Reikäpaikkaus aineistosta löytyi virhe: " validointivirheet)}]}))
    ;; Tallennetaan
    (reikapaikkaus-palvelu/tallenna-reikapaikkaukset db kayttaja urakka-id reikapaikkaukset)
    (tee-kirjausvastauksen-body {:ilmoitukset "Paikkaukset kirjattu onnistuneesti"})))

(defn kirjaa-paikkaustoteuma [db {id :id} data kayttaja]
  (log/debug (format "Kirjataan paikkauskustannuksia: %s kpl urakalle: %s käyttäjän: %s toimesta"
                     (count (:paikkauskustannukset data)) id kayttaja))
  (let [urakka-id (Integer/parseInt id)
        kayttaja-id (:id kayttaja)]
    (validointi/tarkista-urakka-ja-kayttaja db urakka-id kayttaja)
    (tallenna-paikkaustoteuma db urakka-id kayttaja-id data))
  (tee-kirjausvastauksen-body {:ilmoitukset "Paikkauskustannukset kirjattu onnistuneesti"}))

(defrecord Paikkaukset []
  component/Lifecycle
  (start [{http :http-palvelin db :db
           integraatioloki :integraatioloki
           yha-paikkaus :yha-paikkauskomponentti
           :as this}]
    (julkaise-reitti
      http :kirjaa-paikkaus
      (POST "/api/urakat/:id/paikkaus" request
        (kasittele-kutsu db
                         integraatioloki
                         :kirjaa-paikkaus
                         request
                         json-skeemat/paikkausten-kirjaus-request
                         json-skeemat/kirjausvastaus
                         (fn [parametrit data kayttaja db]
                           (kirjaa-paikkaus db parametrit data kayttaja))
          :kirjoitus))
      true)
    (julkaise-reitti
      http :kirjaa-paikkaus
      (POST "/api/urakat/:id/reikapaikkaus" request
        (kasittele-kutsu db
          integraatioloki
          :kirjaa-reikapaikkaus
          request
          json-skeemat/reikapaikkausten-kirjaus-request
          json-skeemat/kirjausvastaus
          (fn [parametrit data kayttaja db]
            (kirjaa-reikapaikkaus db parametrit data kayttaja))
          :kirjoitus))
      true)
    (julkaise-reitti
      http :kirjaa-paikkaustoteuma
      (POST "/api/urakat/:id/paikkaus/kustannus" request
        (kasittele-kutsu db
                         integraatioloki
                         :kirjaa-paikkaustoteuma
                         request
                         json-skeemat/paikkauskustannusten-kirjaus-request
                         json-skeemat/kirjausvastaus
                         (fn [parametrit data kayttaja db]
                           (kirjaa-paikkaustoteuma db parametrit data kayttaja))
          :kirjoitus))
      true)
    (julkaise-reitti
      http :poista-paikkaustiedot
      (DELETE "/api/urakat/:id/paikkaus" request
        (kasittele-kutsu db
                         integraatioloki
                         :poista-paikkaustiedot
                         request
                         json-skeemat/paikkausten-poisto-request
                         json-skeemat/kirjausvastaus
                         (fn [parametrit data kayttaja db]
                           (poista-paikkaustiedot db yha-paikkaus parametrit data kayttaja))
          :kirjoitus))
      true)
    this)

  (stop [{http :http-palvelin :as this}]
    (poista-palvelut http
      :kirjaa-paikkaus
      :kirjaa-reikapaikkaus
      :kirjaa-paikkaustoteuma
      :poista-paikkaustiedot)
    this))
