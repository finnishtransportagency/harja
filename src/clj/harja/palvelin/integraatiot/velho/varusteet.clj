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
  ([db {:keys [oid version-voimassaolo] :as kohde} virhekuvaus]
   (let [hakuvirhe {:aikaleima (pvm/nyt)
                    :virhekuvaus virhekuvaus
                    :virhekohteen_oid oid
                    :virhekohteen_alkupvm (-> version-voimassaolo
                                              :alku
                                              varuste-vastaanottosanoma/velho-pvm->pvm
                                              varuste-vastaanottosanoma/aika->sql)
                    :virhekohteen_vastaus (str kohde)}
         _ (println hakuvirhe)]
     (q-toteumat/tallenna-varustetoteuma-ulkoiset-virhe<! db hakuvirhe))))

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
    urakka-id))

(defn alku-500 [s]
  (subs s 0 (min 499 (count s))))

(defn lokita-oid-haku [oidit url]
  (log/info (str "Haku Velhosta onnistui. Saatiin " (count oidit) " oidia. Url: " url)))

(defn jasenna-varusteiden-oidit [url sisalto otsikot tallenna-virhe-fn]
  (let [{oidit :oidit
         status :tila} (try (let [oidit (json/read-str sisalto :key-fn keyword)]
                              {:oidit oidit
                               :tila {:virheet []
                                      :sanoman-lukuvirhe? false}})
                            (catch Throwable e
                              {:oidit nil
                               :tila {:virheet [{:selite (.getMessage e) :url url
                                                 :otsikot otsikot :sisalto (alku-500 sisalto)}]
                                      :sanoman-lukuvirhe? true}}))
        virheet (:virheet status)
        onnistunut? (and (some? oidit) (empty? virheet))
        virhe-viesti (str "Jäsennettäessä Velhon vastausta tapahtui virheitä. Url: " url " virheet: " (str/join ", " virheet))]

    (if onnistunut?
      (do
        (lokita-oid-haku oidit url)
        {:tila true :oidit oidit})
      (do
        (tallenna-virhe-fn nil virhe-viesti)
        {:tila false :oidit nil}))))

(defn json->kohde-array [json]                              ; json <=> [<kohde-v1-json>,<kohde-v2-json>,...,<kohde-v2-json>]
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
         (map json->kohde-array)                            ;[{:kohde <kohde> :json <json>} ... ]
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


(defn tallenna-kohde [kohteiden-historiat-ndjson haetut-oidit url tallenna-fn tallenna-virhe-fn]
  "Kohdetietojen hakeminen ja tallentaminen on kaksivaiheinen toimenpide.

  Aiemmin on Velhosta haettu lista tunnisteita (OID), joille on Velhossa kohdentunut muutoksia annetun pvm jälkeen.
  Nämä saadaan parametrina `haetut-oidit`. Haku tapahtuu maksimissaan 1000 kohteen joukkoina (batch).
  Tämä funktio saa maksimissaan 1000 oidia haettavaksi jokaisella kutsukerralla.

  Tässä haetaan itse kohteet Velhon rajapinnasta.

  Velho voi palauttaa vähemmän kohteita kuin pyydetyn joukon. Puuttuvien kohteiden katsotaan olevan poistunutta tietoa, eikä niihin reagoida mitenkään.
  Tiedon poistuminen on eri asia kuin kohteen poistaminen. Kohde poistuu merkkaamalla sen versio loppuneeksi.

  Koodissa on varauduttu myös siihen, jos Velho palauttaa enemmän kohteita kuin pyydettiin. (Ei voida vielä olla varmoja voiko näin käydä)
  Myös siihen on varauduttu, että kaikki palautuneet kohteet eivät ole pyydettyjen joukossa.

  Sekvenssi on seuraavan kaltainen:
   1. Jäsentää kohteet `kohteiden-historiat-ndjson`sta.
   `kohteiden-historiat-ndjson`:
    [{<kohde1-v1>},{kohde2-v2}]
    [{<kohde2-v1>}]
    [{<kohde3-v1>},{kohde3-v2}]
    ...
   2. Vertailee saatujen kohteiden oideja `haetut-oidit` joukkoon ja lokitetaan tietokantaan tieto näiden eroista.
   3. Päättelee tietolajit.
   4. Etsii urakka-idt.
   5. Päättelee geometriset sijainnit.
   6. Tallentaa tietokantaan varustetoteumat `tallenna-fn` funktion avulla.

   Osittain onnistuminen on mahdollista, vaikka kaikkia kohteita ei saada jäsennettyä ja muunnettua.
   Siitä kuitenkin seuraa, ettei inkrementaalisen hakemisen seuraavan hakukerran päivämäärää kasvateta,
   vaan ensikerralla kohteita haetaan uudelleen samasta päivästä alkaen. "
  (let [haetut-oidit (set haetut-oidit)
        {saadut-kohteet :kohteet
         jasennys-onnistui? :onnistui} (try
                                         {:kohteet (kohteet-historia-ndjson->kohteet kohteiden-historiat-ndjson)
                                          :onnistui true}
                                         (catch Throwable t
                                           (tallenna-virhe-fn nil (str "Virhe jäsennettäessä kohdehistoria json vastausta. Throwable: " t))
                                           {:kohteet nil :onnistui false}))
        saadut-oidit (as-> saadut-kohteet a
                           (set/project a [:oid])
                           (map :oid a)
                           (set a))
        puuttuvat-oidit (set/difference haetut-oidit saadut-oidit)
        ylimaaraiset-oidit (set/difference saadut-oidit haetut-oidit)
        tallennettavat-oidit (set/difference saadut-oidit ylimaaraiset-oidit)
        tallennettavat-kohteet (filter #(contains? tallennettavat-oidit (:oid %)) saadut-kohteet)]
    ; TODO VHAR-6139 palauta kohteiden haun ja tallentamisen lopputulokset lokita koostetusti kutsussa
    #_(log/info "Varustehaku Velhosta palautti " (count saadut-kohteet) " historia-kohdetta. Yksikäsitteisiä oideja: "
                (count saadut-oidit) " kpl. Tallennetaan " (count tallennettavat-oidit) " kpl. (Ylimääräisiä oideja " (count ylimaaraiset-oidit) " kpl.) Url: " url)
    (if jasennys-onnistui?
      (do
        (when (and jasennys-onnistui? (seq puuttuvat-oidit))
          (tallenna-virhe-fn nil (str "Varustekohdeiden historiahaku palautti vajaan joukon kohteita. Url: " url " Puuttuvat oidit: " puuttuvat-oidit)))
        (when (and jasennys-onnistui? (seq ylimaaraiset-oidit))
          (tallenna-virhe-fn nil (str "Varustekohteiden historiahaku palautti ylimääräisiä kohteita. Ylimääräiset oidit " ylimaaraiset-oidit " Url: " url)))
        (let [tulokset (map (fn [kohde]
                              (try
                                (tallenna-fn kohde)
                                true
                                (catch Throwable t
                                  (tallenna-virhe-fn kohde (str "Virhe tallennettaessa varustetoteumaa: url: " url " Throwable: " t))
                                  false)
                                )) tallennettavat-kohteet)]
          (every? true? tulokset)))
      false)))

(defn oid-lista->json [oidit]
  (json/write-str oidit))

(defn muodosta-kohteet-url [varuste-api-juuri-url {:keys [palvelu api-versio] :as lahde}]
  (let [historia-osa (if (= "sijaintipalvelu" palvelu)
                       ""
                       "/historia")]
    (str varuste-api-juuri-url "/" palvelu "/api/" api-versio historia-osa "/kohteet?rikasta=sijainnit")))

(defn hae-kohdetiedot-ja-tallenna-kohde [lahde varuste-api-juuri-url konteksti token-fn oidit tallenna-fn tallenna-virhe-fn]
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
              onnistunut? (tallenna-kohde sisalto oidit url tallenna-fn tallenna-virhe-fn)]
          onnistunut?)
        (catch [:type virheet/+ulkoinen-kasittelyvirhe-koodi+] {:keys [virheet]}
          (tallenna-virhe-fn nil (str "Ulkoinen käsittelyvirhe. Haku Velhosta epäonnistui. url: " url " virheet: " virheet))
          false)
        (catch Throwable t
          (tallenna-virhe-fn nil (str "Poikkeus. Haku Velhosta epäonnistui. url: " url " Throwable: " t))
          false))
      (do
        (tallenna-virhe-fn nil
                           (str "Autentikaatio virhe. Haku Velhosta epäonnistui. Käyttäjätunnistus epäonnistui. Kohteiden url: " url))
        false))))

(defn muodosta-oidit-url
  "`kohdeluokka` sisältää /-merkin. esim. `varusteet/kaiteet`"
  [lahde varuste-api-juuri viimeksi-haettu-velhosta]
  (let [viimeksi-haettu-iso-8601 (varuste-vastaanottosanoma/aika->velho-aika viimeksi-haettu-velhosta)]
    (str varuste-api-juuri "/" (:palvelu lahde) "/api/" (:api-versio lahde)
         "/tunnisteet/" (:kohdeluokka lahde) "?jalkeen="
         (http/url-encode viimeksi-haettu-iso-8601))))


(defn hae-ja-tallenna
  "Hakee muuttuneiden kohdeiten oid-listan ja sen avulla kutsuu partitio kerrallaan joukolle oideja kohdehakua.

  Virhekäsittely menee siten, että kutsutut funktiot hoitavat virheet yksittäisten kohdeiden osalta ja kutsuva funktio hoitaa
  virheet kokonaisille joukoille kohteita."
  [lahde viimeksi-haettu-velhosta konteksti varuste-api-juuri token-fn tallenna-fn tallenna-hakuaika-fn tallenna-virhe-fn]
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
              {tila :tila oidit :oidit} (jasenna-varusteiden-oidit url body headers tallenna-virhe-fn)]
          (when (and tila (not-empty oidit))
            (let [oidit-alijoukot (partition
                                    +kohde-haku-maksimi-koko+
                                    +kohde-haku-maksimi-koko+
                                    nil
                                    oidit)
                  osajoukon-haku-fn (fn [oidit-alijoukko]
                                      (hae-kohdetiedot-ja-tallenna-kohde lahde varuste-api-juuri konteksti token-fn
                                                                         oidit-alijoukko tallenna-fn tallenna-virhe-fn))
                  tulokset (map osajoukon-haku-fn oidit-alijoukot)
                  kaikki-onnistunut (every? true? tulokset)]
              (when kaikki-onnistunut
                (tallenna-hakuaika-fn haku-alkanut)))))
        (catch [:type virheet/+ulkoinen-kasittelyvirhe-koodi+] {:keys [virheet]}
          (let [virheilmoitus (str "Haku Velhosta epäonnistui. Virheet: " virheet)]
            (tallenna-virhe-fn nil virheilmoitus))))
      (let [virheviesti (str "Haku Velhosta epäonnistui. Autorisaatio tokenia ei saatu. Kohdeluokan url: " url)]
        (tallenna-virhe-fn nil virheviesti)))))

(defn sijainti-kohteelle [db {:keys [sijainti alkusijainti loppusijainti] :as kohde}]
  (let [a (or sijainti alkusijainti)
        b loppusijainti
        piste? (some? sijainti)]
    (assert (some? a) "`sijainti` tai `alkusijainti` on pakollinen")
    (if piste?
      (let [parametrit {:tie (:tie a)
                        :aosa (:osa a)
                        :aet (:etaisyys a)}]
        (:sijainti (first (q-toteumat/varustetoteuman-piste-sijainti db parametrit))))
      (let [parametrit {:tie (:tie a)
                        :aosa (:osa a)
                        :aet (:etaisyys a)
                        :losa (:osa b)
                        :let (:etaisyys b)}]
        (:sijainti (first (q-toteumat/varustetoteuman-viiva-sijainti db parametrit)))))))

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
        (lokita-ja-heita-poikkeus-fn t)))))

(defn tuo-uudet-varustetoteumat-velhosta
  [integraatioloki
   db
   {:keys [token-url
           varuste-api-juuri-url
           varuste-kayttajatunnus
           varuste-salasana] :as asetukset}]
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
                    tallenna-virhe-fn (partial lokita-ja-tallenna-hakuvirhe db)
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
                                                                           (:ulkoinen_oid varustetoteuma2) " alkupvm: "
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
                                                       (:oid kohde) " muokattu: " (:muokattu kohde) " validointivirhe: " virheviesti))))))]
                (hae-ja-tallenna
                  lahde viimeksi-haettu konteksti varuste-api-juuri-url
                  token-fn tallenna-toteuma-fn tallenna-hakuaika-fn tallenna-virhe-fn)
                true))))))
    (catch [:type virheet/+ulkoinen-kasittelyvirhe-koodi+] {:keys [virheet]}
      (log/error "Integraatioajo tuo-uudet-varustetoteumat-velhosta epäonnistui. Virheet: " virheet)
      false)))

(defn hae-mhu-urakka-oidt
  [integraatioloki
   db
   {:keys [token-url
           varuste-kayttajatunnus
           varuste-salasana
           varuste-urakka-oid-url
           varuste-urakka-kohteet-url
           ] :as asetukset}]
  (log/debug (format "Haetaan uusia MHU tunnistetietoja (OID) Velhosta."))
  (try+
    (integraatiotapahtuma/suorita-integraatio
      db integraatioloki "velho" "urakoiden-haku" nil
      (fn [konteksti]
        (let [token-virhe-fn (fn [x] (log/error "Virhe Velho token haussa: " x))
              token-fn (fn [] (hae-velho-token token-url varuste-kayttajatunnus varuste-salasana konteksti token-virhe-fn))
              token (token-fn)]
          (when token
            ;Haetaan OID lista
            (let [otsikot {"Authorization" (str "Bearer " token)}
                  http-asetukset {:metodi :GET
                                  :otsikot otsikot
                                  :url varuste-urakka-oid-url}
                  oid-lista (integraatiotapahtuma/laheta konteksti :http http-asetukset)
                  oid-joukko (set oid-lista)]
              (assert (empty? oid-lista) "OID lista ei saa olla tyhjä")
              ;Haetaan kohteet, (jos saatiin oideja)
              (when (seq oid-lista)
                (let [otsikot {"Content-Type" "application/json"
                               "Authorization" (str "Bearer " token)}
                      http-asetukset {:metodi :POST
                                      :otsikot otsikot
                                      :url varuste-urakka-oid-url}
                      oid-lista-json (oid-lista->json oid-lista)
                      urakka-kohteet (integraatiotapahtuma/laheta konteksti :http http-asetukset oid-lista-json)
                      urakka-oid-joukko (set (map :oid urakka-kohteet))
                      liikaa (set/difference oid-joukko urakka-oid-joukko)
                      puuttuu (set/difference urakka-oid-joukko oid-joukko)]
                  (assert (= oid-joukko urakka-oid-joukko) (str "Urakka kohteet.oid pitää olla sama joukko kuin oid-joukko. Liikaa: "
                                                                liikaa " puuttuu: " puuttuu))
                  ;Poista kannasta kaikki velho-oid sarakkeen tiedot UPDATE urakka SET velho_oid=null;
                  ;Päivitetään urakka tauluun saadut velho-oid:t löytyneille WHERE urakkanro = kohde.ominaisuudet.urakkakoodi
                  (doseq [kohde urakka-kohteet]
                    ; P1 Assert yksi ja vain yksi rivi päivittyy jokaisella updatella.
                    ; P2 Sama rivi ei päivity kahdesti.
                    ; P3 Kannassa on lopuksi yhtä monta velho-oidia kuin urakka-kohteet oli vastauksessa.
                    ; P3 <==> P1 && P2
                    ; P3 on riittävä ehto, ei tarvita P1 ja P2, Voi toteutua, jos kannassa urakka.urakkanro ei ole
                    ; unique tyyppi IN [hoito, teiden-hoito] joukossa. []
                    #_(q-toteumat/paivita-urakkanumero db {:urakkanro (-> kohde :ominaisuudet :urakkakohde) :foo (:oid kohde)}))))
              )))))
    (catch [:type virheet/+ulkoinen-kasittelyvirhe-koodi+] {:keys [virheet]}
      (log/error "Urakka OID haku Velhosta epäonnistunut. Virheet: " virheet)
      false)))

(defn hae-mhu-urakka-oidt-velhosta
  [integraatioloki
   db
   {:keys [] :as asetukset}]
  (log/debug (format "Haetaan MHU urakoita Velhosta."))
  (try+
    (integraatiotapahtuma/suorita-integraatio
      db integraatioloki "velho" "urakoiden-haku" nil
      (fn [konteksti]
        true))
    (catch [:type virheet/+ulkoinen-kasittelyvirhe-koodi+] {:keys [virheet]}
      (log/error "MHU urakoiden haku Velhosta epäonnistui. Virheet: " virheet)
      false)))