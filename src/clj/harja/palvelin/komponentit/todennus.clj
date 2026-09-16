(ns harja.palvelin.komponentit.todennus
  "Tämä namespace määrittelee käyttäjäidentiteetin todentamisen. Käyttäjän todentaminen
   WWW-palvelussa tehdään KOKA ympäristön antamilla header tiedoilla. Tämä komponentti ei tee
   käyttöoikeustarkistuksia, vaan pelkästään hakee käyttäjälle sallitut käyttöoikeudet ja tarkistaa käyttäjän identiteetin."
  (:require [clojure.core.cache :as cache]
            [clojure.set :as set]
            [clojure.string :as str]
            [clojure.data.json :as json]
            [harja.kyselyt.konversio :as konv]
            [com.stuartsierra.component :as component]
            [harja.domain
             [oikeudet :as oikeudet]]
            [harja.kyselyt
             [kayttajat :as q]]
            [harja.palvelin.integraatiot.integraatiotapahtuma :as integraatiotapahtuma]
            [harja.tyokalut.loki :as loki]
            [slingshot.slingshot :refer [throw+]]
            [taoensso.timbre :as log]
            [harja.palvelin.komponentit.todennus-varmistus :as varmistus]
            [harja.palvelin.asetukset :refer [ominaisuus-kaytossa?]])
  (:import (org.apache.commons.codec.binary Base64)
           (org.apache.commons.codec.net BCodec)))

(def todennusvirhe {:virhe :todennusvirhe})

(defn- ryhman-rooli-ja-linkki
  "Etsii annetulle OAM ryhmälle roolin. Ryhmä voi olla suoraan roolin nimi
   tai linkitetyssä roolissa muotoa <linkitetty id>_<roolin nimi>. Palauttaa
   roolin tiedot ja linkitetyn id:n vektorissa, jos rooli ei ole linkitetty id on nil."
  [roolit ryhma]
  (let [roolit (vals roolit)
        ryhmanimet (into #{}
                     (map :nimi roolit))]
    (some (fn [{:keys [nimi linkki] :as rooli}]
            (cond
              (= nimi ryhma)
              [rooli nil]

              (and (not (ryhmanimet ryhma)) linkki (str/ends-with? ryhma (str "_" nimi)))
              [rooli (str/trim (first (str/split ryhma #"_")))]))
      roolit)))

(defn- yleisroolit [roolit-ja-linkit]
  (into #{}
    ;; Haetaan kaikki roolit, joilla ei ole linkkiä
    (comp (map first)
      (filter (comp empty? :linkki))
      (map :nimi))
    roolit-ja-linkit))

(defn- roolien-nimet
  [roolit]
  (into #{}
    (map (comp :nimi first))
    roolit))

(defn poista-nil-id []
  (filter #(some? (first %))))

(defn- urakkaroolit [urakan-id roolit-ja-linkit]
  (into {}
    (comp
      ;; Muuta key Sampo id:stä Harjan urakka id:ksi
      (map #(update-in % [0] urakan-id))
      (poista-nil-id)
      ;; Muuta [[rooli id] ...] -> #{nimi ...}
      (map #(update-in % [1] roolien-nimet)))
    ;; Valitaan vain "urakka" linkitetyt roolit ja
    ;; ryhmitellään ne id:n perusteella
    (group-by second
      (filter (comp #(= "urakka" %)
                :linkki
                first)
        roolit-ja-linkit))))

(defn organisaatioroolit [urakoitsijan-id roolit-ja-linkit]
  (into {}
    (comp
      (map #(update-in % [0] urakoitsijan-id))
      ;; Poistetaan roolit, joille ei löydy organisaatiota.
      ;; Muuten muiden järjestelmien roolit (esim. Extranet_Liito_Kayttaja) rooli voi sekoittua
      ;; Harjan rooleihin.
      (poista-nil-id)
      (map #(update-in % [1] roolien-nimet)))
    (group-by second
      (filter (comp #(= "urakoitsija" %) :linkki first)
        roolit-ja-linkit))))

(defn kayttajan-roolit
  "Palauttaa annetun käyttäjän roolit OAM_GROUPS header arvon perusteella.
   Roolit on mäppäys roolinimestä sen tietoihin. Sähken antama urakan tai
   urakoitsijan id muutetaan harjan id:ksi kutsumalla annettuja urakan-id
   ja urakoitsijan-id funktioita."
  [urakan-id urakoitsijan-id roolit oam-groups]
  (let [roolit-ja-linkit (->> (str/split oam-groups #",")
                           (keep (partial ryhman-rooli-ja-linkki roolit)))
        ;; Uudelleenohjaa käyttäjä jos todennus epäonnistuu
        roolit-ja-linkit (if (= oam-groups "failed")
                           [[{:nimi "failed" :kuvaus "Todennus epäonnistui." :osapuoli nil :linkki nil} nil]]
                           roolit-ja-linkit)]
    {:roolit (yleisroolit roolit-ja-linkit)
     :urakkaroolit (urakkaroolit urakan-id roolit-ja-linkit)
     :organisaatioroolit (organisaatioroolit urakoitsijan-id roolit-ja-linkit)}))

(defn kasittele-miam-vastaus
  "Käsittelee MIAM-rajapinnan HTTP-vastauksen ja palauttaa vastauksen bodyn onnistumisen tapauksessa.

  Parametrit:
  - status: HTTP-statuskoodi (numero)
  - body: Vastauksen body (merkkijono)

  Palauttaa:
  - body, jos statuskoodi on 200
  - nil, jos statuskoodi on muu kuin 200 tai käsittelyssä tapahtuu virhe

  Virhetilanteet lokitetaan."
  [status body]

  (try
    (if (= 200 status)
      body
      (do
        ;; Virheen sattuessa palauta nil
        (log/error (->
                     (str "Virhe miam kutsussa :: saatu virhe: ")
                     (loki/koristele-lokiviesti loki/miam-error))
          body)
        nil))
    (catch Exception e
      (log/error
        (->
          (str "Virhe miam kutsussa :: poikkeus ")
          (loki/koristele-lokiviesti loki/miam-error))
        (.getMessage e))
      ;; Palautetaan virheen sattuessa nil
      nil)))

(defn hae-kayttajaroolit-rajapinnasta
  "Haetaan käyttäjätunnuksen perusteella käyttäjän roolit kutsumalla ulkoista
   käyttäjäroolien hakupalvelua. Harjassa rajapinnan palauttamat tiedot ovat samoja mitä tulee oam_headers parametrista kutsun headereissa/otsikkotiedoissa.

   Parametrit:
   - db: Tietokantayhteys integraatiolokituksen tallentamiseen
   - integraatioloki: Integraatiolokikomponentti rajapintakutsujen lokitukseen
   - miam: MIAM-rajapinnan asetukset (url ja apiavain)
   - kayttajanimi: Käyttäjätunnus, jonka roolit haetaan

   Palauttaa JSON-muotoisen merkkijonon, joka sisältää käyttäjän roolit MIAM-rajapinnasta,
   tai nil jos rajapintakutsu epäonnistuu.

   Lokaalisti ja muuallakin, missä tiedot ovat puutteelliset on hyvä pitää miam rajapinnan kutsu pois päältä,
   vaikka rooleja, ei tulisikaan headereista"
  [db integraatioloki miam kayttajanimi]
  (let [timeout (get miam :timeout 30000) ;; Jos ympäristöön ei ole asetettu mitään, niin 30sek defaulttina
        max-yritykset (get miam :max-yritykset 3) ;; Jos ympäristöön ei ole asetettu mitään, niin 3 yritystä defaulttina
        sleep-ms (get miam :sleep-ms 5000)] ;; Jos ympäristöön ei ole asetettu mitään, niin 5s defaulttina

    ;; Yritetään uudestaan maksimi määrään asti yrityksiä, jos kutsu epäonnistuu
    (loop [yritys 1]
      (let [vastaus (try
                      (integraatiotapahtuma/suorita-integraatio
                        db integraatioloki "miam" "hae-kayttajan-roolit" nil
                        (fn [konteksti]
                          (let [http-asetukset {:metodi :GET
                                                :url (str (:url miam) kayttajanimi)
                                                :timeout timeout
                                                :otsikot (merge
                                                           {"Content-Type" "application/json"}
                                                           {"x-api-key" (:apiavain miam)})}
                                {body :body headers :headers status :status} (integraatiotapahtuma/laheta konteksti :http http-asetukset)]
                            (kasittele-miam-vastaus status body))))
                      (catch Exception e
                        (log/error e "MIAM-kutsu epäonnistui poikkeukseen")
                        nil))]
        (if (or vastaus (>= yritys max-yritykset))
          vastaus
          (do
            (log/warn
              (->
                (str "MIAM-kutsu epäonnistui, yritetään uudelleen " sleep-ms " päästä:  (" yritys "/" max-yritykset ")")
                (loki/koristele-lokiviesti loki/miam-retry)))
            ;; Pidetään ihan pieni tauko ennen seuraavaa yritystä
            (Thread/sleep (* yritys sleep-ms))
            (recur (inc yritys))))))))

(defn kayttajaroolit-rajapintavastauksesta
  "Parsii käyttäjäroolit MIAM-rajapinnan JSON-vastauksesta ja muuntaa ne Harjan ryhmärakenteeksi.

   Parametrit:
   - db: Tietokantayhteys, jota käytetään Sampo-id:iden ja Y-tunnusten muuntamiseen Harjan id:ksi
   - vastaus: JSON-muotoinen merkkijono MIAM-rajapinnasta, tai nil jos kutsu epäonnistui

   Palauttaa käyttäjän roolit Harjan formaatissa (yleis-, urakka- ja organisaatioroolit),
   tai nil jos vastaus on nil, tyhjä, epävalidi JSON tai ei sisällä tarvittavia kenttiä.

   Rajanpinnasta saadaan vastaus tyyliin:
   {\"Table1\": [{ \"CompanyID\": \"<y-tunnus>\", \"Company\": \"<yrityksen nimi>\", \"UserName\": \"<käyttäjän käyttäjätunnus>\", \"Name\": \"<Käyttäjän nimi>\",
                   \"Role\": \"<ytunnus_Paakayttaja>\",
                   \"StartDate\": \"9.4.2024 13:01:03\", \"EndDate\": \"31.3.2029 0:00:00\", \"Agreementname\": \"_Organisaatio peruste <yrityksen nimi>\",
                   \"Appname\": \"HARJA\", \"email\": \"<käyttäjän sähköpostiosoite>\" }]}

   "
  [db vastaus ryhmat-asetuksista]
  (let [roolit-asetuksista (when ryhmat-asetuksista
                             (kayttajan-roolit
                               (partial q/hae-urakan-id-sampo-idlla db)
                               (partial q/hae-urakoitsijan-id-ytunnuksella db)
                               oikeudet/roolit
                               ryhmat-asetuksista))]
    (cond
      ;; Tarkista onko vastaus nil tai tyhjä string
      (or (nil? vastaus) (str/blank? vastaus))
      ;; Jos vastaus on nil/tyhjä, niin palauta vain mahdolliset sähke-headereista saadut roolit (jos niitä on)
      (when ryhmat-asetuksista roolit-asetuksista)

      ;; Yritä parsea JSON ja tarkista sisältö
      :else
      (try
        (let [vastaus-map (json/read-str vastaus)
              table1 (get vastaus-map "Table1")]
          ;; Tarkista onko Table1 olemassa ja ei-tyhjä
          (if (or (nil? table1) (empty? table1))
            (do
              (log/warn
                (->
                  (str "MIAM-vastaus ei sisällä Table1-kenttää tai se on tyhjä")
                  (loki/koristele-lokiviesti loki/miam-error)))
              ;; Jos on virheellinen vastaus, niin palauta vain mahdolliset sähke-headereista saadut roolit (jos niitä on)
              (when ryhmat-asetuksista roolit-asetuksista))
            ;; Otetaan talteen roolit -> jotka on sama kuin oam_groupsit
            ;; Olemme kiinnostuneita pelkästään roolista eli Role kentästä. Mitään muuta arvoa ei tarkasteta tai validoida
            (let [ryhmat (->> table1
                           (keep #(get % "Role"))
                           (remove str/blank?) ; poista tyhjät
                           (str/join ","))
                  ;; Lisätään mahdolliset ryhmät asetuksista
                  ryhmat (str/join "," (remove nil? [ryhmat-asetuksista ryhmat]))]
              (kayttajan-roolit
                (partial q/hae-urakan-id-sampo-idlla db)
                (partial q/hae-urakoitsijan-id-ytunnuksella db)
                oikeudet/roolit
                ryhmat))))
        (catch Exception e
          (log/error e
            (str "Virhe parsittaessa MIAM-rajapinnan vastausta")
            (loki/koristele-lokiviesti loki/miam-error))
          nil)))))

;; Pidetään käyttäjätietoja muistissa 2h, jotta ei tarvitse koko ajan hakea tietokannasta tai miam-rajapinnasta
;; uudestaan. KOKA->käyttäjätiedot pitää hakea joka ikiselle HTTP pyynnölle.
;; Eli, kun cache hittiä ei ensimmäisen sivulautauksen voi olla, niin tietokantaa ja miam-rajapintaa kutsutaan neljä kertaa.
(def cache-ttl-minuutit 120)
(def kayttajatiedot-cache-atom (atom (cache/ttl-cache-factory {} :ttl (* cache-ttl-minuutit 60 1000))))

(defn- pura-header-arvo
  "KOKA lähettää ääkkösellisen headerin muodossa \"=?UTF?B?...base64...?=\"."
  [teksti]
  (if (and teksti (str/starts-with? teksti "=?"))
    (.decode (BCodec.) teksti)
    teksti))

(defn parsi-json-entraid-roolit [oam-groups]
  (->>
    (json/read-str oam-groups)
    (str/join ",")))

(defn jwt-vahvistus-epaonnistui? [headerit]
  (boolean
    (= (get headerit "oam_groups") "failed")))

(defn- pura-cognito-headerit
  "Purkaa AWS Cognitolta palautuneet headerit ja hakee niistä OAM-tiedot.
   Tiedot mapataan vanhan mallisiksi OAM_-headereiksi
   JWT Signaturen vahvistukset suoritetaan samalla, jonka epäonnistuessa todennus ei etene"
  [headerit kehitysmoodi? {:keys [public-key-url todennus-varmistus-paalla?] :as _todennus-varmistus-asetukset}]
  (let [;; Sisältää mm. Cogniton user poolin url:n ja app client id:n, kertoo koska token on annettu, ja kenelle
        ;; Mukana myös signature joka vahvistetaan
        accesstoken (get headerit "x-iam-accesstoken")

        ;; Sisältää käyttäjän käyttäjän tietoja, roolit, yhteystiedot, lxtunnus
        ;; Mukana myös signature joka vahvistetaan 
        iam-data (get headerit "x-iam-data")

        ;; Subject ID (sub), eli käyttäjä kenelle JWT on myönnetty, tällä voidaan tunnistaa käyttäjä (mukana myös yllä olevissa tokeneissa)
        ;; Tällä voidaan esim invalitoida token, kun käyttäjä kirjautuu ulos, mutta Harjassa ei tuollaista tarvetta kirjoitushetkellä taida olla
        ; iam-identity (get headerit "x-iam-identity")

        ;; Vahvistetaan että tokenien payloadit on eheät
        vahvistetut-tunnustiedot (if (and
                                       iam-data
                                       todennus-varmistus-paalla?)
                                   (varmistus/vahvista-jwt-signaturet accesstoken iam-data kehitysmoodi? public-key-url)
                                   (varmistus/tunnistetiedot iam-data))

        ;; Käsittele vielä EntraID muodossa olevat roolit (json)
        dekoodatut-headerit (update vahvistetut-tunnustiedot "custom:rooli" #(if (konv/onko-json? %)
                                                                               (parsi-json-entraid-roolit %)
                                                                               %))]

    ;; Mapataan Cognito-headerit vanhan mallisiksi vastaaviksi OAM-headereiksi
    ;; TODO: Siirrytään mahdollisesti myöhemmin käyttämään pelkkiä cognito-headereita
    (reduce-kv
      (fn [m k v]
        (assoc m k (pura-header-arvo v)))
      {}
      (set/rename-keys dekoodatut-headerit
        {"custom:rooli" "oam_groups"
         "custom:uid" "oam_remote_user"
         "custom:etunimi" "oam_user_first_name"
         "custom:sukunimi" "oam_user_last_name"
         ;; Huom. ei custom:-alkuliitettä
         "email" "oam_user_mail"
         "custom:puhelin" "oam_user_mobile"
         "custom:osasto" "oam_departmentnumber"
         "custom:organisaatio" "oam_organization"
         "custom:ytunnus" "oam_user_companyid"}))))


(defn- koka-headerit [headerit]
  (reduce-kv
    (fn [m k v]
      (assoc m k (pura-header-arvo v)))
    {}
    (select-keys headerit
      [;; Käyttäjätunnus ja ryhmät
       "oam_remote_user" "oam_groups"
       ;; ELY-numero (tai null), org nimi ja Y-tunnus
       "oam_departmentnumber" "oam_organization" "oam_user_companyid"
       ;; Etu- ja sukunimi
       "oam_user_first_name" "oam_user_last_name"
       ;; Sähköposti ja puhelin
       "oam_user_mail" "oam_user_mobile"])))

(defn prosessoi-apikayttaja-header
  "Integraatioväylä välittää apikäyttäjän tunnuksen Harjaan harja-api-username-nimisessä headerissä.
   Koodissa käyttäjätieto luetaan oam_remote_user-headeristä. Muutetaan headerin nimi, jotta tarvittava apikäyttäjätunnus saadaan käyttöön.
   Tilannetta jolloin cognito-headereitten käsittelystä syntyy oam_remote_user ja headereissa on välitetty username, ei pitäisi syntyä.
   Ylikirjoitetaan kuitenkin varalta mahdollinen ylimääräinen oam_remote_user-header.
   Funktio suoritetaan pilvipuolella, kun koka- ei oam-headereitä ei saada kutsun yhteydessä."
  [headerit]
  (if (get headerit "harja-api-username")
    (assoc-in headerit ["oam_remote_user"] (get headerit "harja-api-username"))
    headerit))

(defn prosessoi-kayttaja-headerit
  "Palauttaa headerit sellaisenaan, mikäli headereiden joukosta löytyy jokin OAM_-headeri.
   Muutoin, yritetään purkaa AWS Cognitolta saadut headerit, jotka mapataan OAM_-headereiksi ja lisätään 
   muiden headereiden joukkoon."
  [headerit kehitysmoodi? todennus-varmistus-asetukset]
  (if (empty? (koka-headerit headerit))
    (->
      (merge headerit (pura-cognito-headerit headerit kehitysmoodi? todennus-varmistus-asetukset))
      (prosessoi-apikayttaja-header))
    headerit))

(defn- hae-organisaatio-elynumerolla [db ely]
  (some->> ely
    (re-matches #"\d+")
    Long/parseLong
    (q/hae-ely-numerolla db)
    first))

(defn- hae-organisaatio-elinvoimakeskusnumerolla [db elinvoimakeskus]
  (some->> elinvoimakeskus
    (re-matches #"\d+")
    Long/parseLong
    (q/hae-elinvoimakeskus-numerolla db)
    first))

(defn- hae-organisaatio-nimella [db nimi]
  (first (q/hae-organisaatio-nimella db nimi)))

(defn- hae-organisaatio-liitetylle-roolille [db roolit]
  (some->> roolit
    :organisaatioroolit
    keys
    first
    (q/hae-organisaatio-idlla db)
    first))

(defn- hae-organisaatio-y-tunnuksella [db y-tunnus]
  (some->> y-tunnus
    (q/hae-organisaatio-y-tunnuksella db)
    first))

(defn- hae-kayttajalle-organisaatio
  [db elinvoimakeskus ely y-tunnus organisaatio roolit]
  (or
    ;; Jos elinvoimakeskusnumero annettu, haetaan organisaatio sillä
    (hae-organisaatio-elinvoimakeskusnumerolla db elinvoimakeskus)
    ;; Jos ELY-numero haetaan se
    (hae-organisaatio-elynumerolla db ely)
    ;; Jos yrityksen Y-tunnus annettu, hae sillä X
    (hae-organisaatio-y-tunnuksella db y-tunnus)
    ;; Muuten haetaan org. nimellä
    (hae-organisaatio-nimella db organisaatio)
    ;; Muuten etsitään urakoitsijakohtaista roolia
    (hae-organisaatio-liitetylle-roolille db roolit)))

(defn- varmista-kayttajatiedot
  "Ottaa tietokannan ja käyttäjän OAM headerit. Varmistaa että käyttäjä on olemassa
   ja palauttaa käyttäjätiedot"
  ([db integraatioloki miam headerit]
   (varmista-kayttajatiedot db integraatioloki miam headerit false))
  ([db integraatioloki miam {kayttajanimi "oam_remote_user"
                             ryhmat "oam_groups"
                             organisaationumero "oam_departmentnumber"
                             organisaation_nimi "oam_organization"
                             etunimi "oam_user_first_name"
                             sukunimi "oam_user_last_name"
                             sahkoposti "oam_user_mail"
                             puhelin "oam_user_mobile"
                             y-tunnus "oam_user_companyid"
                             :as headerit} token-epaonnistui?]
   ;; Järjestelmätunnuksilla ei saa kirjautua varsinaiseen Harjaan
   (log/debug "onko-jarjestelma?" kayttajanimi "->" (q/onko-jarjestelma? db kayttajanimi))
   (if (q/onko-jarjestelma? db kayttajanimi)
     (throw+ todennusvirhe)
     (let [roolit (if (or (ominaisuus-kaytossa? :header-roolit) (empty? (:apiavain miam))) ;; Varmistetaan että miam api-avain on määritetty ja ominaisuus on käytössä
                    ;; Vanha tapa käsitellä roolit on muodostaa ne suoraan OAM headereista
                    (kayttajan-roolit
                      (partial q/hae-urakan-id-sampo-idlla db)
                      (partial q/hae-urakoitsijan-id-ytunnuksella db)
                      oikeudet/roolit
                      ryhmat)
                    ;; Uusi tapa käsitellä roolit tarkoittaa, että roolit haetaan apin kautta ulkoisesta lähteestä
                    (let [miam-tulos (if token-epaonnistui?
                                       nil
                                       (hae-kayttajaroolit-rajapinnasta db integraatioloki miam kayttajanimi))]
                      (when (and (not token-epaonnistui?) (nil? miam-tulos))
                        (throw+ {:type :miam-virhe
                                 :viesti "MIAM-haku epäonnistui, kirjautuminen estetty"}))
                      (kayttajaroolit-rajapintavastauksesta db miam-tulos ryhmat)))
           elinvoimakeskus (when (= "elinvoimakeskus" (str/lower-case (or organisaation_nimi ""))) organisaationumero)
           ely (when (= "ely" (str/lower-case (or organisaation_nimi ""))) organisaationumero)
           organisaatio (hae-kayttajalle-organisaatio db elinvoimakeskus ely y-tunnus organisaation_nimi roolit)
           kayttaja {:kayttajanimi kayttajanimi
                     :etunimi etunimi
                     :sukunimi sukunimi
                     :sahkoposti sahkoposti
                     :puhelin puhelin
                     :organisaatio (:id organisaatio)}
           kayttaja-kannassa (first (q/hae-kayttaja-kayttajanimella db {:kayttajanimi kayttajanimi}))
           piilota-nimi? (:piilota_nimi kayttaja-kannassa)
           kayttaja-id-kannassa (:id kayttaja-kannassa)
           kayttaja-kannassa (merge (select-keys kayttaja-kannassa #{:kayttajanimi
                                                                     :etunimi
                                                                     :sukunimi
                                                                     :sahkoposti
                                                                     :puhelin})
                               {:organisaatio (:org_id kayttaja-kannassa)})
           kayttajan-tiedot-samat? (= kayttaja kayttaja-kannassa)
           kayttaja-id (or
                         kayttaja-id-kannassa
                         (:id (q/luo-kayttaja<! db kayttaja)))
           ;; Päivitetään käyttäjätiedot Jarjestelmavastaava :lle, jos nimeä ei ole vielä piilotettu
           _ (when (and (not piilota-nimi?) (contains? (:roolit roolit) "Jarjestelmavastaava"))
               (q/piilota-jvh-nimi! db {:id kayttaja-id}))]
       (when (and kayttaja-id-kannassa (not kayttajan-tiedot-samat?))
         (q/paivita-kayttaja! db (merge kayttaja
                                   {:id kayttaja-id})))
       (log/info
         "SÄHKE HEADERIT: " (str kayttajanimi ": " ryhmat)
         "; ELY-NUMERO: " ely
         "; ORGANISAATION NIMI: " organisaation_nimi
         "; Y-TUNNUS: " y-tunnus
         "; KÄYTTÄJÄ ID: " kayttaja-id
         "; ORGANISAATIO: " organisaatio)
       (merge (assoc kayttaja
                :organisaatio organisaatio
                :organisaation-urakat (into #{}
                                        (map :id)
                                        (q/hae-organisaation-urakat db (:id organisaatio)))
                :id kayttaja-id)
         roolit)))))

(defn- ohita-oikeudet
  "Mahdollista kaikkien OAM_* headerien ohittaminen tietyille käyttäjille konfiguraatiossa.
   Jos käyttäjälle on ohitetut headerit, ne palautetaan KOKAn antamien headerien sijasta, muuten
   headerit palautetaan normaalisti."
  [{kayttaja "oam_remote_user" :as koka-headerit} oikeudet]
  (or (and oikeudet (oikeudet kayttaja))
    koka-headerit))

;; Atomilla seurataan käynnissä olevia hakuja
(def odottavat-kutsut-atom (atom {}))

(defn- hae-tai-odota-kayttajatietoja
  "Hakee käyttäjätiedot tai odottaa käynnissä olevaa hakua samalle käyttäjälle.
   Estää samanaikaiset MIAM-kutsut samalle käyttäjälle."
  [db integraatioloki miam oam-tiedot]
  (let [kayttajanimi (get oam-tiedot "oam_remote_user")
        ;; Luodaan promise tai haetaan olemassa oleva atomisesti
        ;; Palautetaan [promise uusi?] jossa uusi? kertoo luotiinko uusi promise
        [vastaus-promise uusi?]
        (let [p (promise)]
          (if-let [olemassa? (get @odottavat-kutsut-atom kayttajanimi)]
            [olemassa? false]
            ;; Yritä lisätä uusi promise atomisesti
            (let [result (swap! odottavat-kutsut-atom
                           (fn [pending]
                             (if (contains? pending kayttajanimi)
                               pending
                               (assoc pending kayttajanimi p))))]
              ;; Tarkista lisättiinkö meidän promise vai oliko toinen thread nopeampi
              (if (identical? p (get result kayttajanimi))
                [p true]
                [(get result kayttajanimi) false]))))
        vastaus (if uusi?
                  (do
                    (deliver vastaus-promise
                      (try
                        {:ok (varmista-kayttajatiedot db integraatioloki miam oam-tiedot)}
                        (catch Throwable t
                          (log/error t "Virhe käyttäjätietojen haussa")
                          {:error t})
                        (finally
                          ;; Siivotaan promise pois atomista
                          (swap! odottavat-kutsut-atom dissoc kayttajanimi))))
                    @vastaus-promise)
                  ;; Koska kyselyt on jo menossa, niin muuten vain palautetaan promisen odotus
                  @vastaus-promise)]

    ;; Jos thread loi promisen, deliveroi aina
    (if-let [t (:error vastaus)]
      (throw t)
      (:ok vastaus))))

(defn koka->kayttajatiedot
  ([db integraatioloki miam headerit oikeudet kehitysmoodi?]
   (koka->kayttajatiedot db integraatioloki miam headerit oikeudet kehitysmoodi? nil))
  ([db integraatioloki miam headerit oikeudet kehitysmoodi? todennus-varmistus-asetukset]
   (let [headerit (prosessoi-kayttaja-headerit headerit kehitysmoodi? todennus-varmistus-asetukset)
         oam-tiedot (ohita-oikeudet (koka-headerit headerit) oikeudet)
         jwt-epaonnistui? (jwt-vahvistus-epaonnistui? oam-tiedot)]
     (if jwt-epaonnistui?
       ;; JW Token epäonnistui (tässä on aiheelliset hälytykset jo) 
       ;; Älä etene miam hakuun, vaan mene suoraan varmistukseen  
       ;;     Tämä ohjaa  -> div.ei-kayttooikeutta -> "Todennus epäonnistui"
       (varmista-kayttajatiedot db integraatioloki miam oam-tiedot jwt-epaonnistui?)
       (try
         ;; Tarkista ensin cachesta
         (if-let [cached (cache/lookup @kayttajatiedot-cache-atom oam-tiedot)]
           ;; Cache-osuma, palauta arvo
           cached
           ;; Cache-huti, hae tai odota (hakuja tehdään vain yksi, muut säikeet odottavat sen valmistumista)
           (let [result (hae-tai-odota-kayttajatietoja db integraatioloki miam oam-tiedot)]
             ;; Tallenna cacheen
             (swap! kayttajatiedot-cache-atom cache/miss oam-tiedot result)
             result))
         (catch Throwable t
           (log/error t "Käyttäjätietojen varmistuksessa virhe!")))))))

(defprotocol Todennus
  "Protokolla HTTP pyyntöjen käyttäjäidentiteetin todentamiseen."
  (todenna-pyynto [this req kehitysmoodi?]
    "Todenna annetun HTTP-pyynnön käyttäjätiedot, palauttaa uuden
     req mäpin, jossa käyttäjän tiedot on lisätty avaimella :kayttaja."))

(defrecord HttpTodennus
  [oikeudet todennus-varmistus-asetukset miam]
  component/Lifecycle
  (start [this]
    (log/info "Todennetaan HTTP käyttäjä KOKA headereista.")
    this)
  (stop [this]
    this)

  Todennus
  (todenna-pyynto [{db :db integraatioloki :integraatioloki :as this} req kehitysmoodi?]
    (let [headerit (:headers req)
          kayttaja-id (headerit "oam_remote_user")]
      (if (nil? kayttaja-id)
        (do
          (log/warn (str "Todennusheader oam_remote_user puuttui kokonaan: " headerit))
          (throw+ todennusvirhe))
        (if-let [kayttajatiedot (koka->kayttajatiedot db integraatioloki miam headerit oikeudet kehitysmoodi? todennus-varmistus-asetukset)]
          (assoc req :kayttaja kayttajatiedot)
          (do
            (log/warn (str
                        "Ei löydetty koka-käyttäjätietoja id:lle: " (headerit "oam_remote_user") " "
                        "Jos kyseessä on järjestelmäkäyttäjä, tarkista kutsutaanko oikeaa endpointtia: " (:uri req)))
            (throw+ todennusvirhe)))))))

(defrecord FeikkiHttpTodennus [kayttaja]
  component/Lifecycle
  (start [this]
    (log/warn "Käytetään FEIKKI käyttäjätodennusta, käyttäjä = " (pr-str kayttaja))
    this)
  (stop [this]
    this)

  Todennus
  (todenna-pyynto [this req kehitysmoodi?]
    (assoc req
      :kayttaja kayttaja)))

(defn http-todennus
  ([] (http-todennus nil nil nil))
  ([oikeudet] (http-todennus oikeudet nil nil))
  ([oikeudet todennus-varmistus miam]
   (->HttpTodennus oikeudet todennus-varmistus miam)))

(defn feikki-http-todennus [kayttaja]
  (->FeikkiHttpTodennus kayttaja))
