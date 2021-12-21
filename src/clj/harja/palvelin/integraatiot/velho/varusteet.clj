(ns harja.palvelin.integraatiot.velho.varusteet
  (:import (org.postgresql.util PSQLException))
  (:require [hiccup.core :refer [html]]
            [taoensso.timbre :as log]
            [harja.kyselyt.konversio :as konversio]
            [harja.kyselyt.koodistot :as koodistot]
            [harja.kyselyt.toteumat :as q-toteumat]
            [harja.kyselyt.urakat :as q-urakat]
            [harja.palvelin.integraatiot.integraatiotapahtuma :as integraatiotapahtuma]
            [harja.palvelin.integraatiot.velho.yhteiset :refer [hae-velho-token]]
            [harja.palvelin.integraatiot.velho.sanomat.varuste-vastaanottosanoma :as varuste-vastaanottosanoma]
            [harja.palvelin.integraatiot.api.tyokalut.virheet :as virheet]
            [harja.palvelin.integraatiot.api.tyokalut.json :refer
             [aika-string->java-sql-timestamp]]
            [harja.pvm :as pvm]
            [clojure.data.json :as json]
            [clojure.set :as set]
            [clojure.string :as str]
            [org.httpkit.client :as http])
  (:use [slingshot.slingshot :only [throw+ try+]]))

(def +virhe-varustetoteuma-haussa+ ::velho-virhe-varustetoteuma-haussa)
(def +kohde-haku-maksimi-koko+ 1000)

(def +oid-hakujen-epokki+ "2000-01-01 00:00:00.0")
(def +oid-hakujen-epokki-sqllle+ "2000-01-01T00:00:00Z")

; tl523 "Tekninen piste" Lähde puuttuu - "Siirtyy Fintraffic:n vastuulle (tiedon masterjärjestelmä)! Tietolajia ei migroida."

(def +tl501+
  "tl501 Kaiteet" {:kohdeluokka "varusteet/kaiteet" :palvelu "varusterekisteri" :api-versio "v1"})
(def +tl503_504_505_507_508_516+
  "tl503 tl504 tl505 tl507 tl508 tl516 *" {:kohdeluokka "varusteet/tienvarsikalusteet" :palvelu "varusterekisteri" :api-versio "v1"})
(def +tl506+
  "tl506 Liikennemerkki" {:kohdeluokka "varusteet/liikennemerkit" :palvelu "varusterekisteri" :api-versio "v1"})
(def +tl509+
  "tl509 Rummut" {:kohdeluokka "varusteet/rumpuputket" :palvelu "varusterekisteri" :api-versio "v1"})
(def +tl512+
  "tl512 Viemärit" {:kohdeluokka "varusteet/kaivot" :palvelu "varusterekisteri" :api-versio "v1"})
(def +tl513+
  "tl513 Reunapaalut" {:kohdeluokka "varusteet/reunapaalut" :palvelu "varusterekisteri" :api-versio "v1"})
(def +tl514_518+
  "tl514 Melurakenteet tl518 Kivetyt alueet" {:kohdeluokka "tiealueen-poikkileikkaus/luiskat" :palvelu "sijaintipalvelu" :api-versio "v3"})
(def +tl515+
  "tl515 Aidat" {:kohdeluokka "varusteet/aidat" :palvelu "varusterekisteri" :api-versio "v1"})
(def +tl517+
  "tl517 Portaat" {:kohdeluokka "varusteet/portaat" :palvelu "varusterekisteri" :api-versio "v1"})
(def +tl518+
  "tl518 Kivetyt alueet" {:kohdeluokka "tiealueen-poikkileikkaus/erotusalueet" :palvelu "sijaintipalvelu" :api-versio "v3"})
(def +tl520+
  "tl520 Puomit" {:kohdeluokka "varusteet/puomit-sulkulaitteet-pollarit" :palvelu "varusterekisteri" :api-versio "v1"})
(def +tl522+
  "tl522 Reunakivet" {:kohdeluokka "varusteet/reunatuet" :palvelu "varusterekisteri" :api-versio "v1"})
(def +tl524+
  "tl524 Viherkuviot" {:kohdeluokka "ymparisto/viherkuviot" :palvelu "tiekohderekisteri" :api-versio "v1"})

(def +tietolajien-lahteet+ [+tl501+
                            +tl503_504_505_507_508_516+
                            +tl506+
                            +tl509+
                            +tl512+
                            +tl513+
                            +tl514_518+
                            +tl515+
                            +tl517+
                            +tl518+
                            +tl520+
                            +tl522+
                            +tl524+])

; * tl503 "Levähdysalueiden varusteet"
;   tl504 "WC"
;   tl505 "Jätehuolto"
;   tl507 "Bussipysäkin varusteet"
;   tl508 "Bussipysäkin katos"
;   tl516 "Hiekkalaatikot"

(defn lokita-ja-tallenna-hakuvirhe
  [db {:keys [oid version-voimassaolo] :as kohde} virhekuvaus]
  (let [hakuvirhe {:ulkoinen_oid (or oid "000")
                   :alkupvm (-> version-voimassaolo
                                :alku
                                varuste-vastaanottosanoma/velho-pvm->pvm
                                varuste-vastaanottosanoma/aika->sql)
                   :virhekuvaus virhekuvaus}]
    (log/error virhekuvaus)
    (q-toteumat/tallenna-varustetoteuma-ulkoiset-kohdevirhe<! db hakuvirhe)))

(defn urakka-id-kohteelle [db {:keys [sijainti alkusijainti version-voimassaolo alkaen] :as kohde}]
  (let [s (or sijainti alkusijainti)
        alkupvm (or (:alku version-voimassaolo)
                    alkaen)                                 ; Sijaintipalvelu ei palauta versioita
        tr-osoite {:tie (:tie s)
                   :aosa (:osa s)
                   :aet (:etaisyys s)
                   :paivamaara alkupvm}
        urakka-id (-> (q-urakat/hae-hoito-urakka-tr-pisteelle db tr-osoite)
                      first
                      :id)]
    (assert (some? s) "`sijainti` tai `alkusijainti` on pakollinen")
    (assert (some? alkupvm) "`alkupvm` on pakollinen")
    (when (nil? urakka-id)
      (lokita-ja-tallenna-hakuvirhe
        db kohde (str "varuste-urakka-id-kohteelle: Kohteelle ei löydy urakkaa: oid: " (:oid kohde) " sijainti: " sijainti " alkusijainti: " alkusijainti " alkupvm: " alkupvm)))
    urakka-id))

(defn alku-500 [s]
  (subs s 0 (min 499 (count s))))

(defn lokita-oid-haku [oidit url]
  (log/info (str "Haku Velhosta onnistui. Saatiin " (count oidit) " oidia. Url: " url)))

(defn jasenna-varusteiden-oidit [url sisalto otsikot]
  (let [{oidit :oidit status :tila} (try (let [oidit (json/read-str sisalto :key-fn keyword)]
                                           {:oidit oidit
                                            :tila {:virheet []
                                                   :sanoman-lukuvirhe? false}})
                                         (catch Throwable e
                                           {:oidit nil
                                            :tila {:virheet [{:selite (.getMessage e) :url url
                                                              :otsikot otsikot :sisalto (alku-500 sisalto)}]
                                                   :sanoman-lukuvirhe? true}}))
        virheet (:virheet status)                           ; todo virhekäsittelyä, ainakin 404, 500, 405?
        onnistunut? (and (some? oidit) (empty? virheet))
        virhe-viesti (str "Jäsennettäessä Velhon vastausta tapahtui seuraavat virheet: " (str/join ", " virheet))]

    (if onnistunut?
      (do
        (lokita-oid-haku oidit url)
        {:tila true :oidit oidit})
      (do
        (log/error (str "Virheitä haettaessa Velhosta: " virhe-viesti " Url: " url))
        {:tila false :oidit nil}
        ))))

(defn json->kohde-array [json]
  (let [tulos (json/read-str json :key-fn keyword)]
    (if (vector? tulos)
      tulos
      [tulos])))

(defn kohteet-historia-ndjson->kohteet [ndjson]
  "Jäsentää `ndjson` listan kohteita flatten listaksi kohde objekteja
  Jos syötteenä on merkkojonossa kohteiden versioita JSON muodossa,
  palauttaa listan, jossa on kohteiden versioita.
  `ndjson`:
  [<JSON-kohde1-v1>, <JSON-kohde1-v2>, ...  <JSON-kohde1-vn>]
  [<JSON-kohde2-v1>, ...]
  ...
  paluuarvo:
  `'(<kohde1-v1> <kohde1-v2> ... <kohde2-v1> <kohde2-v2> ...)`

  Jäsennys merkkaa jokaisen kohteen uusimman elementin konversiota varten.
  Konversion täytyy tietää uusin elementti, koska sen voimassa-olo määrää
  varustetapahtuman tapahtumalajin silloin, kun kyseessä on poisto."
  (let [rivit (clojure.string/split-lines ndjson)
        merkitse-vektorin-viimeinen (fn [v] (concat (butlast v) [(assoc (last v) :uusin-versio true)]))]
    (->> rivit
         (map json->kohde-array)
         (map merkitse-vektorin-viimeinen)
         flatten)))

(defn nayte10 [c]
  (str "(" (min (count c) 10) "/" (count c) "): " (vec (take 10 c))))

(defn hae-viimeisin-hakuaika-kohdeluokalle [db kohdeluokka]
  "Hakee tietokannasta kohdeluokan viimeisimmän hakuajan, jolloin kyseistä kohdeluokkaa on haettu Velhosta.
  Jos kohdeluokkaa ei ole koskaan vielä haettu, palautetaan 2000-01-01T00:00:00Z ja insertoidaan se tietokantaan."
  (let [kohdeluokka-haettu-viimeksi (->> (q-toteumat/varustetoteuma-ulkoiset-viimeisin-hakuaika-kohdeluokalle db kohdeluokka)
                                         first
                                         :viimeisin_hakuaika)]
    (if kohdeluokka-haettu-viimeksi
      kohdeluokka-haettu-viimeksi
      (let [parametrit {:kohdeluokka kohdeluokka
                        :viimeisin_hakuaika (aika-string->java-sql-timestamp +oid-hakujen-epokki-sqllle+)}]
        (q-toteumat/varustetoteuma-ulkoiset-luo-viimeisin-hakuaika-kohdeluokalle>! db parametrit)
        (pvm/iso-8601->aika +oid-hakujen-epokki+)))))

(defn tallenna-viimeisin-hakuaika-kohdeluokalle [db kohdeluokka viimeisin-hakuaika]
  (let [parametrit {:kohdeluokka kohdeluokka
                    :viimeisin_hakuaika viimeisin-hakuaika}]
    (q-toteumat/varustetoteuma-ulkoiset-paivita-viimeisin-hakuaika-kohdeluokalle! db parametrit)))

(defn tallenna-kohde [kohteiden-historiat-ndjson haetut-oidit url tallenna-fn]
  "1. Jäsentää kohteet `kohteiden-historiat-ndjson`sta.
   `kohteiden-historiat-ndjson`:
    [{<kohde1-v1>},{kohde2-v2}]
    [{<kohde2-v1>}]
    [{<kohde3-v1>},{kohde3-v2}]
    ...
   2. Vertailee saatujen kohteiden oideja `haetut-oidit` joukkoon ja log/warn eroista.
   3. Päättelee tietolajit.
   4. Etsii urakka-idt.
   5. Tallentaa tietokantaan varustetoteumat `tallenna-fn` funktion avulla."
  (let [haetut-oidit (set haetut-oidit)
        {saadut-kohteet :kohteet
         jasennys-onnistui :onnistui} (try
                                        {:kohteet (kohteet-historia-ndjson->kohteet kohteiden-historiat-ndjson)
                                         :onnistui true}
                                        (catch Throwable t (log/error "Virhe jäsennettäessä kohdehistoria json vastausta. Throwable: " t)
                                                           {:kohteet nil :onnistui false}))
        saadut-oidit (as-> saadut-kohteet a
                           (set/project a [:oid])
                           (map :oid a)
                           (set a))
        puuttuvat-oidit (set/difference haetut-oidit saadut-oidit)
        ylimaaraiset-oidit (set/difference saadut-oidit haetut-oidit)]
    (when (not-empty puuttuvat-oidit)
      (log/warn "Varustekohdeiden historiahaku palautti vajaan joukon kohteita. Puuttuvat oidit " (nayte10 puuttuvat-oidit) " Url: " url))
    (when (not-empty ylimaaraiset-oidit)
      (log/warn "Varustekohteiden historiahaku palautti ylimääräisiä kohteita. Ylimääräiset oidit " (nayte10 ylimaaraiset-oidit) " Url: " url))
    (log/info "Varustehaku Velhosta palautti " (count saadut-kohteet) " historia-kohdetta. Yksikäsitteisiä oideja: "
              (count saadut-oidit) " kpl. Url: " url)
    (let [tulokset (map (fn [kohde]
                          (try
                            (tallenna-fn kohde)
                            true
                            (catch Throwable t
                              (log/error "Virhe tallennettaessa varustetoteumaa: url: " url " Throwable: " t)
                              false)
                            )) saadut-kohteet)]
      (and jasennys-onnistui
           (every? true? tulokset)))))

(defn oid-lista->json [oidit]
  (json/write-str oidit))

(defn muodosta-kohteet-url [varuste-api-juuri-url {:keys [palvelu api-versio] :as lahde}]
  (let [historia-osa (if (= "sijaintipalvelu" palvelu)
                       ""
                       "/historia")]
    (str varuste-api-juuri-url "/" palvelu "/api/" api-versio historia-osa "/kohteet?rikasta=sijainnit")))

(defn hae-kohdetiedot-ja-tallenna-kohde [lahde varuste-api-juuri-url konteksti token-fn oidit tallenna-fn]
  (let [url (muodosta-kohteet-url varuste-api-juuri-url lahde)
        token (token-fn)]
    (if token
      (try+
        (let [pyynto (oid-lista->json oidit)
              otsikot {"Content-Type" "application/json; charset=utf-8"
                       "Authorization" (str "Bearer " token)}
              http-asetukset {:metodi :POST
                              :url url
                              :otsikot otsikot}
              {sisalto :body otsikot :headers} (integraatiotapahtuma/laheta konteksti :http http-asetukset pyynto)
              onnistunut? (tallenna-kohde sisalto oidit url tallenna-fn)]
          onnistunut?)
        (catch [:type virheet/+ulkoinen-kasittelyvirhe-koodi+] {:keys [virheet]}
          (log/error "Haku Velhosta epäonnistui. url: " url " virheet: " virheet)
          false)
        (catch Throwable t
          (log/error "Haku Velhosta epäonnistui. url: " url " Throwable: " t)
          false))
      (do (log/error "Kohteen haku Velhosta epäonnistui. Ei saatu autorisaatio tokenia. Kohteiden url: " url)
          false))))

(defn muodosta-oidit-url
  "`kohdeluokka` sisältää /-merkin. esim. `varusteet/kaiteet`"
  [lahde varuste-api-juuri viimeksi-haettu-velhosta]
  (let [viimeksi-haettu-iso-8601 (varuste-vastaanottosanoma/aika->velho-aika viimeksi-haettu-velhosta)]
    (str varuste-api-juuri "/" (:palvelu lahde) "/api/" (:api-versio lahde)
         "/tunnisteet/" (:kohdeluokka lahde) "?jalkeen="
         (http/url-encode viimeksi-haettu-iso-8601))))


(defn hae-ja-tallenna [lahde viimeksi-haettu-velhosta konteksti varuste-api-juuri token-fn tallenna-fn tallenna-hakuaika-fn]
  (let [url (muodosta-oidit-url lahde varuste-api-juuri viimeksi-haettu-velhosta)
        token (token-fn)]
    (if token
      (try+
        (let [haku-alkanut (pvm/nyt)
              otsikot {"Content-Type" "application/json"
                       "Authorization" (str "Bearer " token)}
              http-asetukset {:metodi :GET
                              :url url
                              :otsikot otsikot}
              {body :body headers :headers} (integraatiotapahtuma/laheta konteksti :http http-asetukset)
              {tila :tila oidit :oidit} (jasenna-varusteiden-oidit url body headers)]
          (when (and tila (not-empty oidit))
            (let [oidit-alijoukot (partition
                                    +kohde-haku-maksimi-koko+
                                    +kohde-haku-maksimi-koko+
                                    nil
                                    oidit)
                  osajoukon-haku-fn (fn [oidit-alijoukko]
                                      (hae-kohdetiedot-ja-tallenna-kohde lahde varuste-api-juuri konteksti token-fn
                                                                         oidit-alijoukko tallenna-fn))
                  tulokset (map osajoukon-haku-fn oidit-alijoukot)
                  kaikki-onnistunut (every? true? tulokset)]
              (when kaikki-onnistunut
                (tallenna-hakuaika-fn haku-alkanut)))))
        (catch [:type virheet/+ulkoinen-kasittelyvirhe-koodi+] {:keys [virheet]}
          (log/error "Haku Velhosta epäonnistui. Virheet: " virheet)))
      (log/error "Haku Velhosta epäonnistui. Autorisaatio tokenia ei saatu. Kohdeluokan url: " url))))

(defn sijainti-kohteelle [db {:keys [sijainti alkusijainti loppusijainti] :as kohde}]
  (let [a (or sijainti alkusijainti)
        b loppusijainti]
    (assert (some? a) "`sijainti` tai `alkusijainti` on pakollinen")
    (let [parametrit {:tie (:tie a)
                      :aosa (:osa a)
                      :aet (:etaisyys a)
                      :losa (or (:osa b) (:osa a))
                      :let (or (:etaisyys b) (:etaisyys a))}]
      (:sijainti (first (q-toteumat/varustetoteuman-toimenpiteelle-sijainti db parametrit))))))

(defn lisaa-tai-paivita-kantaan
  [lisaa-fn paivita-fn lokita-epaonnistuminen-fn]
  (let [lokita-ja-heita-poikkeus-fn (fn [poikkeus]
                                      (lokita-epaonnistuminen-fn poikkeus)
                                      (throw poikkeus))]
    (try
      (lisaa-fn)
      (catch PSQLException e
        (if (= (str/includes? (.getMessage e) "duplicate key value violates unique constraint"))
          (try
            (paivita-fn)
            (catch Throwable t
              (lokita-ja-heita-poikkeus-fn t)))
          (lokita-ja-heita-poikkeus-fn e)))
      (catch Throwable t
        (lokita-ja-heita-poikkeus-fn t))
      )))

(defn tuo-uudet-varustetoteumat-velhosta
  [integraatioloki
   db
   {:keys [token-url
           varuste-api-juuri-url
           varuste-kayttajatunnus
           varuste-salasana] :as asetukset}]
  (log/debug (format "Haetaan uusia varustetoteumia Velhosta."))
  (try+
    (integraatiotapahtuma/suorita-integraatio
      db integraatioloki "velho" "varustetoteumien-haku" nil
      (fn [konteksti]
        (let [token-virhe-fn (fn [x] (log/error "Virhe Velho token haussa: " x))
              token-fn (fn [] (hae-velho-token token-url varuste-kayttajatunnus varuste-salasana konteksti token-virhe-fn))
              token (token-fn)]
          (when token
            (doseq [lahde +tietolajien-lahteet+]
              (let [kohdeluokka (:kohdeluokka lahde)
                    viimeksi-haettu (hae-viimeisin-hakuaika-kohdeluokalle db kohdeluokka)
                    tallenna-hakuaika-fn (partial tallenna-viimeisin-hakuaika-kohdeluokalle db kohdeluokka)
                    tallenna-toteuma-fn (fn [kohde]
                                          (log/debug "Tallennetaan kohdeluokka: " kohdeluokka "oid: " (:oid kohde)
                                                     " version-voimassaolo.alku: " (get-in kohde [:version-voimassaolo :alku]))
                                          (let [urakka-id-kohteelle-fn (partial urakka-id-kohteelle db)
                                                sijainti-kohteelle-fn (partial sijainti-kohteelle db)
                                                konversio-fn (partial koodistot/konversio db)
                                                {varustetoteuma2 :tulos
                                                 tietolaji :tietolaji
                                                 virheviesti :virheviesti} (varuste-vastaanottosanoma/velho->harja urakka-id-kohteelle-fn
                                                                                                                   sijainti-kohteelle-fn
                                                                                                                   konversio-fn kohde)
                                                saatu-kohdeluokka (:kohdeluokka kohde)]
                                            (if varustetoteuma2
                                              (let [lisaa-fn (fn []
                                                               (assert (= kohdeluokka saatu-kohdeluokka)
                                                                       (format "Kohdeluokka ei vastaa odotettua. Odotettu: %s saatu: %s " kohdeluokka saatu-kohdeluokka))
                                                               (q-toteumat/luo-varustetoteuma-ulkoiset<! db varustetoteuma2))
                                                    paivita-fn (fn []
                                                                 (log/warn "Päivitetään varustetoteuma oid: "
                                                                           (:ulkoiset_oid varustetoteuma2) " alkupvm: "
                                                                           (varuste-vastaanottosanoma/aika->velho-aika (:alkupvm varustetoteuma2)))
                                                                 (q-toteumat/paivita-varustetoteuma-ulkoiset! db varustetoteuma2))
                                                    lokita-epaonnistuminen-fn (fn [poikkeus]
                                                                                (lokita-ja-tallenna-hakuvirhe
                                                                                  db kohde
                                                                                  (str "hae-varustetoteumat-velhosta: tallenna-toteuma-fn: " poikkeus)))]
                                                (lisaa-tai-paivita-kantaan
                                                  lisaa-fn
                                                  paivita-fn
                                                  lokita-epaonnistuminen-fn))
                                              (when tietolaji ; Pelkkä tietolaji aiheuttaa virheen, koska emme saaneet varustetoteuma2 kohdetta.
                                                (lokita-ja-tallenna-hakuvirhe
                                                  db kohde
                                                  (str "hae-varustetoteumat-velhosta: tallenna-toteuma-fn: Kohde ei onnistu muuttaa Harjan muotoon. ulkoinen_oid: "
                                                       (:oid kohde) " muokattu: " (:muokattu kohde) " validointivirhe: " virheviesti))
                                                ))))]
                (hae-ja-tallenna
                  lahde viimeksi-haettu konteksti varuste-api-juuri-url
                  token-fn tallenna-toteuma-fn tallenna-hakuaika-fn)
                true))))))
    (catch [:type virheet/+ulkoinen-kasittelyvirhe-koodi+] {:keys [virheet]}
      (log/error "Päällystysilmoituksen lähetys Velhoon epäonnistui. Virheet: " virheet)
      false)))

(defn hae-velho-varustetoteumat
  [db]
  nil)