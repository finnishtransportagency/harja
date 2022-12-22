(ns harja.palvelin.palvelut.yllapitokohteet.paikkaukset
  (:require [com.stuartsierra.component :as component]
            [clojure.java.jdbc :as jdbc]
            [specql.op :as op]
            [harja.geo :as geo]
            [harja.kyselyt.konversio :as konversio]
            [harja.domain.oikeudet :as oikeudet]
            [harja.domain.paikkaus :as paikkaus]
            [harja.domain.tierekisteri :as tierekisteri]
            [harja.domain.muokkaustiedot :as muokkaustiedot]
            [harja.kyselyt.urakat :as urakat-q]
            [harja.kyselyt.paikkaus :as q]
            [harja.kyselyt.tieverkko :as tv]
            [harja.palvelin.asetukset :refer [ominaisuus-kaytossa?]]
            [harja.palvelin.palvelut.yllapitokohteet.viestinta :as viestinta]
            [harja.palvelin.palvelut.yllapitokohteet.yleiset :as ypk-yleiset]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelut]]
            [harja.palvelin.integraatiot.yha.yha-paikkauskomponentti :as yha-paikkauskomponentti]
            [taoensso.timbre :as log]
            [slingshot.slingshot :refer [try+]]
            [harja.palvelin.integraatiot.yha.yha-komponentti :as yha]
            [specql.core :as specql]
            [clojure.data.json :as json])
  (:import (java.text SimpleDateFormat ParseException)
           (java.sql Date)
           (java.util TimeZone)))

(defn- kasittele-koko-ja-sijainti
  "Paikkauskohteiden sisään haetaan siis json objektina paikkaukset. Ja koska kyseessä on json objekti, niin kaikki
  data on string tyyppistä. Joten sijainnin geometriasta tulee db->clojure muunnoksessa vain PersistentVector eikä
  geometria MultiLineString. Niinpä tehdään se tässä käsityönä ja hartaudella.
  Samalla lasketaan paikkauksen pituus ja pita-ala."
  [db paikkauskohteet]
  (mapv (fn [kohde]
          (let [;; Koska tiedetään, että yksi paikkauskohde on aina yhdellä tiellä, niin haetaan jokaista paikkauskohdetta
                ;; kohti tien osien pituudet vain kerran ja käytetään tätä tietoa laskiessa paikkaus taulun riville (paikkaustoteuma)
                ;; pituutta. Mutta koska maailma ei ole täydellinen, niin on olemassa paikkauskohteita, joilla ei ole tietä.
                ;; Näille tie kaivetaan ensimmäisestä paikkaustoteumasta

                tie (if (:harja.domain.tierekisteri/tie kohde)
                      (:harja.domain.tierekisteri/tie kohde)
                      (:harja.domain.tierekisteri/tie (first (::paikkaus/paikkaukset kohde))))
                osan-pituudet (tv/hae-osien-pituudet db {:tie tie
                                                         :aosa (or (:aosa kohde) (::paikkaus/aosa (first (::paikkaus/paikkaukset kohde))))
                                                         :losa (or (:losa kohde) (::paikkaus/losa (first (::paikkaus/paikkaukset kohde))))})]
            (if-not (empty? (::paikkaus/paikkaukset kohde))
              (let [paikkaukset (::paikkaus/paikkaukset kohde)
                    kohde (-> kohde
                              (assoc ::paikkaus/paikkaukset
                                     (mapv
                                       (fn [p]
                                         (let [pituus (:pituus (tv/laske-tien-osien-pituudet osan-pituudet {:aosa (:harja.domain.tierekisteri/aosa p)
                                                                                                           :aet (:harja.domain.tierekisteri/aet p)
                                                                                                           :losa (:harja.domain.tierekisteri/losa p)
                                                                                                           :let (:harja.domain.tierekisteri/let p)}))
                                               p (update p ::paikkaus/sijainti (fn [sijainti]
                                                                                 (let [sijainti (when-not (nil? sijainti)
                                                                                                  (json/read-str sijainti :key-fn keyword))
                                                                                       sijainti (if (= "MultiPoint" (:type sijainti))
                                                                                                  nil
                                                                                                  sijainti)]
                                                                                   sijainti)))]
                                           (cond-> p
                                                   true (assoc :suirun-pituus pituus)
                                                   true (assoc :suirun-pinta-ala (if (and pituus (::paikkaus/leveys p))
                                                                                   (* pituus (::paikkaus/leveys p))
                                                                                   0))
                                                   ;; Jos löytyy koordinaatit, niin asetetaan sijainti
                                                   (get-in p [::paikkaus/sijainti :coordinates]) (assoc ::paikkaus/sijainti
                                                                                                        {:type :multiline
                                                                                                         :lines (reduce (fn [a rivi]
                                                                                                                          (conj a {:type :line
                                                                                                                                   :points rivi}))
                                                                                                                        [] (get-in p [::paikkaus/sijainti :coordinates]))}))))
                                       paikkaukset)))]
                kohde)
              kohde)))
        paikkauskohteet))

(defn hae-urakan-paikkaukset
  "Haetaan paikkauskohteet, joita ei ole poistettu ja joiden tila on tilattu/valmis ja joilla ei ole pot raportointitilana.
  Samalla haetaan paikkauskohteille paikkaus taulusta rivit (eli paikkauksen toteumat, huomaa taulujen nimiöinti) sekä
  paikkausten materiaalit ja tienkohdat."
  [db user {:keys [aikavali tyomenetelmat tr nayta] :as tiedot}]
  (oikeudet/vaadi-lukuoikeus oikeudet/urakat-paikkaukset-toteumat user (or (::paikkaus/urakka-id tiedot)
                                                                           (:urakka-id tiedot)))
  (let [urakka-id (or (::paikkaus/urakka-id tiedot)
                      (:urakka-id tiedot))
        menetelmat (disj tyomenetelmat "Kaikki")
        menetelmat (when (> (count menetelmat) 0)
                     menetelmat)
        vain-kohteet-joilla-toteumia? (= nayta :kohteet-joilla-toteumia)
        _ (log/debug "hae-urakan-paikkaukset :: tiedot" (pr-str tiedot) (pr-str (konversio/sql-date (first aikavali))) "tr" (pr-str tr) "vain-kohteet-joilla-toteumia?" vain-kohteet-joilla-toteumia?)
        paikkauskohteet (q/hae-urakan-paikkauskohteet-ja-paikkaukset db {:urakka-id urakka-id
                                                                         :alkuaika (when (and aikavali (first aikavali))
                                                                                     (konversio/sql-date (first aikavali)))
                                                                         :loppuaika (when (and aikavali (second aikavali))
                                                                                      (konversio/sql-date (second aikavali)))
                                                                         :tyomenetelmat menetelmat
                                                                         :tie (:numero tr)
                                                                         :aosa (:alkuosa tr)
                                                                         :aet (:alkuetaisyys tr)
                                                                         :losa (:loppuosa tr)
                                                                         :let (:loppuetaisyys tr)})
        paikkauskohteet (->> paikkauskohteet
                             (map #(clojure.set/rename-keys % paikkaus/paikkauskohde->specql-avaimet))
                             (map #(update % :paikkaukset konversio/jsonb->clojuremap))
                             (mapv #(update % :paikkaukset
                                            (fn [rivit]
                                              (let [tulos (keep
                                                            (fn [r]
                                                              ;; Haku käyttää paikkausten hakemisessa left joinia, joten on mahdollista, että paikkaus taulusta
                                                              ;; löytyy nil id
                                                              (when (not (nil? (:f1 r)))
                                                                (let [r (-> r
                                                                            (update :f2 (fn [aika]
                                                                                          (when aika
                                                                                            (.parse (SimpleDateFormat. "yyyy-MM-dd'T'HH:mm:ss") aika))))
                                                                            (update :f3 (fn [aika]
                                                                                          (when aika
                                                                                            (.parse (SimpleDateFormat. "yyyy-MM-dd'T'HH:mm:ss") aika))))
                                                                            (clojure.set/rename-keys
                                                                              paikkaus/db-paikkaus->speqcl-avaimet))]
                                                                  r)))
                                                            rivit)]
                                                tulos))))
                             (remove #(and vain-kohteet-joilla-toteumia? (empty? (:paikkaukset %))))
                             (mapv #(clojure.set/rename-keys % {:paikkaukset ::paikkaus/paikkaukset})))
        ;; Sijainnin ja tien pituuden käsittely - json objekti kannasta antaa string tyyppistä sijaintidataa. Muokataan se tässä käsityönä
        ;; multiline tyyppiseksi geometriaksi
        paikkauskohteet (kasittele-koko-ja-sijainti db paikkauskohteet)
        _ (log/debug "hae-urakan-paikkaukset :: paikkauskohteet kpl:" (pr-str (count paikkauskohteet)))
        ]
    paikkauskohteet))

(defn hae-paikkausurakan-kustannukset [db user tiedot]
  (assert (not (nil? (::paikkaus/urakka-id tiedot))) "Urakka-id on nil")
  (oikeudet/vaadi-lukuoikeus oikeudet/urakat-paikkaukset-kustannukset user (::paikkaus/urakka-id tiedot))
  (let [kysely-params-template {:alkuosa nil :numero nil :urakka-id nil :loppuaika nil :alkuaika nil
                                :alkuetaisyys nil :loppuetaisyys nil :loppuosa nil :paikkaus-idt nil
                                :tyomenetelmat nil :tyyppi "kokonaishintainen"}
        kysely-params (assoc (merge kysely-params-template (:tr tiedot))
                        :urakka-id (::paikkaus/urakka-id tiedot)
                        :paikkaus-idt (when-let [paikkaus-idt (:paikkaus-idt tiedot)]
                                        (konversio/seq->array paikkaus-idt))
                        :alkuaika (first (:aikavali tiedot))
                        :loppuaika (second (:aikavali tiedot))
                        :tyomenetelmat (when (not-empty (:tyomenetelmat tiedot))
                                         (konversio/seq->array (:tyomenetelmat tiedot))))
        haetaan-nollalla-paikkauksella? (and (not (nil? (:paikkaus-idt tiedot)))
                                             (empty? (:paikkaus-idt tiedot)))]
    ;; Palautetaan tyhjä lista, jos käyttäjä ei ole valinnut yhtäkään paikkauskohdetta. Jos paikkaus-idt on nil,
    ;; tarkoittaa se, että näkymään juuri tultiin ja ollaan suorittamassa ensimmäistä hakua. Tyhjä lista taasen, että
    ;; käyttäjä on ottanut kaikki paikkauskohteet pois.
    (cond
      (:ensimmainen-haku? tiedot) (jdbc/with-db-transaction [db db]
                                      (let [kustannukset (if haetaan-nollalla-paikkauksella?
                                                []
                                                (into []
                                                      (map konversio/alaviiva->rakenne)
                                                      (q/hae-paikkauskohteen-mahdolliset-kustannukset db kysely-params)))
                                            paikkauskohteet (q/hae-urakan-paikkauskohteet db (::paikkaus/urakka-id tiedot))
                                            tyomenetelmat (q/hae-urakan-tyomenetelmat db (::paikkaus/urakka-id tiedot))]
                                        {:kustannukset kustannukset
                                         :paikkauskohteet paikkauskohteet
                                         :tyomenetelmat tyomenetelmat}))
      haetaan-nollalla-paikkauksella? {:kustannukset []}
      :else {:kustannukset (into []
                                 (map konversio/alaviiva->rakenne)
                                 (q/hae-paikkauskohteen-mahdolliset-kustannukset db kysely-params))})))

(defn- paivita-paikkaustoteuma! [db user rivi]
  (q/paivita-paikkaustoteuma! db {:hinta (:hinta rivi)
                                  :poistettu (boolean (:poistettu rivi))
                                  :poistaja (when (:poistettu rivi)
                                              (:id user))
                                  :muokkaaja (:id user)
                                  :tie (:tie rivi)
                                  :aosa (:aosa rivi)
                                  :aet (:aet rivi)
                                  :losa (:losa rivi)
                                  :let (:let rivi)
                                  :tyomenetelma (:tyomenetelma rivi)
                                  :valmistumispvm (:valmistumispvm rivi)
                                  :paikkaustoteuma-id (:paikkaustoteuma-id rivi)}))

(defn- luo-paikkaustoteuma! [db user rivi urakka-id]
  (q/luo-paikkaustoteuma! db {:urakka urakka-id
                              :paikkauskohde (:paikkauskohde rivi)
                              :tie (:tie rivi)
                              :aosa (:aosa rivi)
                              :aet (:aet rivi)
                              :losa (:losa rivi)
                              :let (:let rivi)
                              :luoja (:id user)
                              :tyyppi (or (:tyyppi rivi) "kokonaishintainen")
                              :tyomenetelma (:tyomenetelma rivi)
                              :valmistumispvm (:valmistumispvm rivi)
                              :hinta (:hinta rivi)}))

(defn tallenna-paikkauskustannukset!
  [db user {:keys [hakuparametrit rivit] :as tiedot}]
  (assert (some? tiedot) "Tallenna-paikkauskustannukset tietoja puuttuu.")
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-paikkaukset-kustannukset user (::paikkaus/urakka-id tiedot))
  (jdbc/with-db-transaction [db db]
                            (doseq [rivi rivit]
                              (let [tyomenetelma (:tyomenetelma rivi)
                                    rivi (if (string? tyomenetelma)
                                           (assoc rivi :tyomenetelma (q/hae-tyomenetelman-id db tyomenetelma))
                                           rivi)]
                                (assert (:tyomenetelma rivi) "Työmenetelmä puuttuu.")
                                (assert (:valmistumispvm rivi) "Valmistumispvm puuttuu.")
                                (assert (:hinta rivi) "Hinta puuttuu.")
                                (ypk-yleiset/vaadi-paikkauskohde-kuuluu-urakkaan db
                                                                                 (::paikkaus/urakka-id tiedot)
                                                                                 (:paikkauskohde rivi))
                                (if (pos-int? (:paikkaustoteuma-id rivi))
                                  (paivita-paikkaustoteuma! db user rivi)
                                  (luo-paikkaustoteuma! db
                                                        user
                                                        rivi
                                                        (::paikkaus/urakka-id tiedot)))))

                            ;; Palautetaan symmetrisesti käyttäjän hakuehtojen mukaisesti urakan kustannukset
                            (hae-paikkausurakan-kustannukset db user hakuparametrit)))

(defn ilmoita-virheesta-paikkaustiedoissa!
  [db fim email user {::paikkaus/keys [id nimi urakka-id  pinta-ala-summa massamenekki-summa rivien-lukumaara
                                       tyomenetelma saate muut-vastaanottajat kopio-itselle?] :as tiedot}]
  (assert (some? tiedot) "ilmoita-virheesta-paikkaustiedoissa tietoja puuttuu.")
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-paikkaukset-kustannukset user (::paikkaus/urakka-id tiedot))
  (let [urakka-sampo-id (urakat-q/hae-urakan-sampo-id db urakka-id)
        paikkauskohde-id (get-in tiedot [::paikkaus/paikkauskohde ::paikkaus/id])
        response (viestinta/laheta-sposti-urakoitsijalle-paikkauskohteessa-virhe (merge tiedot
                                                                                          {:email email
                                                                                           :fim fim
                                                                                           :kopio-itselle? kopio-itselle?
                                                                                           :muut-vastaanottajat muut-vastaanottajat
                                                                                           :urakka-sampo-id urakka-sampo-id
                                                                                           :tyomenetelma tyomenetelma
                                                                                           ;:pinta-ala-summa pinta-ala-summa
                                                                                           ::massamenekki-summa massamenekki-summa
                                                                                           :rivien-lukumaara rivien-lukumaara
                                                                                           :saate saate
                                                                                           :ilmoittaja user}))]
    (if (not (contains? response :virhe))
      (q/paivita-paikkauskohteen-ilmoitettu-virhe! db {:id paikkauskohde-id :ilmoitettu-virhe saate}))
    response
  ))

(defn- laheta-paikkauskohde-yhaan
  "Lähettää annetut kohteet teknisine tietoineen YHAan."
  [db yhap {:keys [urakka-id kohde-id]}]
  (let [lahetys (try+ (yha-paikkauskomponentti/laheta-paikkauskohde yhap urakka-id kohde-id)
                      (catch [:type yha/+virhe-kohteen-lahetyksessa+] {:keys [virheet]}
                        virheet))
        lahetys-onnistui? (not (contains? lahetys :virhe))]
    (merge
      {:paikkauskohde nil}
      (when-not lahetys-onnistui?
        lahetys))))

(defn- paikkaustyomenetelman-koodi->lyhenne [db koodi]
  (:lyhenne (first (q/hae-paikkauskohteen-tyomenetelma db {:id koodi}))))

(defn- yha-lahetettava? [db paikkauskohde]
  (paikkaus/pitaako-paikkauskohde-lahettaa-yhaan?
    (paikkaustyomenetelman-koodi->lyhenne db (::paikkaus/tyomenetelma paikkauskohde))))

(defn merkitse-paikkauskohde-tarkistetuksi!
  [db yhap user {::paikkaus/keys [urakka-id paikkauskohde hakuparametrit] :as tiedot}]
  (assert (some? tiedot) "ilmoita-virheesta-paikkaustiedoissa tietoja puuttuu.")
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-paikkaukset-toteumat user (::paikkaus/urakka-id tiedot))
  (when (yha-lahetettava? db paikkauskohde)
    (laheta-paikkauskohde-yhaan db yhap {:urakka-id urakka-id :kohde-id (::paikkaus/id paikkauskohde)}))
  (let [paikkauskohde-id (::paikkaus/id paikkauskohde)
        user-id (:id user)]
    (assert (some? paikkauskohde-id) "Paikkauskohteen tunniste puuttuu")
    (assert (some? user-id) "Käyttäjän tunniste puuttuu")
    (q/merkitse-paikkauskohde-tarkistetuksi! db {:id paikkauskohde-id :tarkistaja-id user-id})
    (hae-urakan-paikkaukset db user hakuparametrit)))

(defrecord Paikkaukset []
  component/Lifecycle
  (start [this]
    (let [http (:http-palvelin this)
          email (:api-sahkoposti this)
          fim (:fim this)
          db (:db this)
          yha-paikkaus (:yha-paikkauskomponentti this)]
      (julkaise-palvelu http :hae-urakan-paikkaukset
                        (fn [user tiedot]
                          (hae-urakan-paikkaukset db user tiedot)))
      (julkaise-palvelu http :hae-paikkausurakan-kustannukset
                        (fn [user tiedot]
                          (hae-paikkausurakan-kustannukset db user tiedot))
                        {:kysely-spec ::paikkaus/paikkausurakan-kustannukset-kysely
                         :vastaus-spec ::paikkaus/paikkausurakan-kustannukset-vastaus})
      (julkaise-palvelu http :tallenna-paikkauskustannukset
                        (fn [user tiedot]
                          (tallenna-paikkauskustannukset! db user tiedot)))
      (julkaise-palvelu http :ilmoita-virheesta-paikkaustiedoissa
                        (fn [user tiedot]
                          (ilmoita-virheesta-paikkaustiedoissa! db fim email user tiedot)))
      (julkaise-palvelu http :merkitse-paikkauskohde-tarkistetuksi
                        (fn [user tiedot]
                            (merkitse-paikkauskohde-tarkistetuksi! db yha-paikkaus user tiedot)))
      this))

  (stop [this]
    (poista-palvelut
      (:http-palvelin this)
      :hae-urakan-paikkaukset
      :hae-paikkausurakan-kustannukset
      :tallenna-paikkauskustannukset
      :ilmoita-virheesta-paikkaustiedoissa
      :merkitse-paikkauskohde-tarkistetuksi)
    this))
