(ns harja.palvelin.integraatiot.velho.varusteet
  (:import (org.postgresql.util PSQLException))
  (:require
    [clojure.core.memoize :as memo]
    [clojure.data.json :as json]
    [clojure.set :as set]
    [clojure.string :as str]
    [org.httpkit.client :as http]
    [slingshot.slingshot :refer [try+]]
    [taoensso.timbre :as log]
    [harja.kyselyt.koodistot :as koodistot]
    [harja.kyselyt.toteumat :as q-toteumat]
    [harja.kyselyt.urakat :as q-urakat]
    [harja.kyselyt.velho-nimikkeistot :as q-nimikkeistot]
    [harja.palvelin.integraatiot.api.tyokalut.json :refer [aika-string->java-sql-timestamp]]
    [harja.palvelin.integraatiot.api.tyokalut.virheet :as virheet]
    [harja.palvelin.integraatiot.integraatiotapahtuma :as integraatiotapahtuma]
    [harja.palvelin.integraatiot.velho.sanomat.varuste-vastaanottosanoma :as varuste-vastaanottosanoma]
    [harja.palvelin.integraatiot.velho.yhteiset :as velho-yhteiset]
    [harja.pvm :as pvm]))

(def +kohde-haku-maksimi-koko+ 1000)
(def +virhe-oidit-memoize-ttl+ (* 10 60 1000))
(def +oid-hakujen-epokki+ "2000-01-01 00:00:00.0")
(def +oid-hakujen-epokki-sqllle+ "2000-01-01T00:00:00Z")
(def +urakka-memoize-ttl+ (* 10 60 1000))

(defn varuste-kohdeluokka->tyyppi
  "Hakee varusteiden kohdeluokan tyypin metatiedosta" [kohdeluokka]
  (->> kohdeluokka
    :allOf
    (some :properties)
    :ominaisuudet
    :allOf
    (some :properties)
    :rakenteelliset-ominaisuudet
    :properties
    :tyyppi))

(def varuste-tyyppi-polku
  [:ominaisuudet :rakenteelliset-ominaisuudet :tyyppi])

(defn kaivo-kohdeluokka->tyyppi
  "Hakee kaivojen kohdeluokan tyypin metatiedosta"
  [kohdeluokka]
  (->> kohdeluokka
    :allOf
    (some :properties)
    :ominaisuudet
    :allOf
    (some :properties)
    :rakenteelliset-ominaisuudet
    :properties
    :kaivon-tyyppi))

(def kaivo-tyyppi-polku
  [:ominaisuudet :rakenteelliset-ominaisuudet :kaivon-tyyppi])

(defn muu-kohdeluokka->tyyppi
  "Hakee muiden kohdeluokkien tyypin metatiedosta" [kohdeluokka]
  (->> kohdeluokka
    :allOf
    (some :properties)
    :ominaisuudet
    :allOf
    (some :properties)
    :tyyppi))

(def muu-kohdeluokka-tyyppi-polku
  [:ominaisuudet :tyyppi])


; tl523 "Tekninen piste" Lähde puuttuu - "Siirtyy Fintraffic:n vastuulle (tiedon masterjärjestelmä)! Tietolajia ei migroida."

(def +tl501+
  "tl501 Kaiteet" {:kohdeluokka "kaiteet" :palvelu "varusterekisteri" :api-versio "v1"
                   :nimiavaruus "varusteet" :kohdeluokka->tyyppi-fn varuste-kohdeluokka->tyyppi
                   :tyyppi-polku varuste-tyyppi-polku})
(def +tl503_504_505_507_508_516+
  "tl503 tl504 tl505 tl507 tl508 tl516 *" {:kohdeluokka "tienvarsikalusteet" :palvelu "varusterekisteri" :api-versio "v1"
                                           :nimiavaruus "varusteet" :kohdeluokka->tyyppi-fn varuste-kohdeluokka->tyyppi
                                           :tyyppi-polku varuste-tyyppi-polku})
(def +tl506+
  "tl506 Liikennemerkki" {:kohdeluokka "liikennemerkit" :palvelu "varusterekisteri" :api-versio "v1"
                          :nimiavaruus "varusteet" :kohdeluokka->tyyppi-fn varuste-kohdeluokka->tyyppi
                          :tyyppi-polku varuste-tyyppi-polku})
(def +tl509+
  "tl509 Rummut" {:kohdeluokka "rumpuputket" :palvelu "varusterekisteri" :api-versio "v1"
                  :nimiavaruus "varusteet" :kohdeluokka->tyyppi-fn varuste-kohdeluokka->tyyppi
                  :tyyppi-polku varuste-tyyppi-polku})
(def +tl512+
  "tl512 Viemärit" {:kohdeluokka "kaivot" :palvelu "varusterekisteri" :api-versio "v1"
                    :nimiavaruus "varusteet" :kohdeluokka->tyyppi-fn kaivo-kohdeluokka->tyyppi
                    :tyyppi-polku kaivo-tyyppi-polku})
(def +tl513+
  "tl513 Reunapaalut" {:kohdeluokka "reunapaalut" :palvelu "varusterekisteri" :api-versio "v1"
                       :nimiavaruus "varusteet" :kohdeluokka->tyyppi-fn varuste-kohdeluokka->tyyppi
                       :tyyppi-polku varuste-tyyppi-polku})
(def +tl514_518+
  "tl514 Melurakenteet tl518 Kivetyt alueet" {:kohdeluokka "luiskat" :palvelu "sijaintipalvelu" :api-versio "v3"
                                              :nimiavaruus "tiealueen-poikkileikkaus" :kohdeluokka->tyyppi-fn muu-kohdeluokka->tyyppi
                                              :tyyppi-polku muu-kohdeluokka-tyyppi-polku})
(def +tl515+
  "tl515 Aidat" {:kohdeluokka "aidat" :palvelu "varusterekisteri" :api-versio "v1"
                 :nimiavaruus "varusteet" :kohdeluokka->tyyppi-fn varuste-kohdeluokka->tyyppi
                 :tyyppi-polku varuste-tyyppi-polku})
(def +tl517+
  "tl517 Portaat" {:kohdeluokka "portaat" :palvelu "varusterekisteri" :api-versio "v1"
                   :nimiavaruus "varusteet" :kohdeluokka->tyyppi-fn varuste-kohdeluokka->tyyppi
                   :tyyppi-polku varuste-tyyppi-polku})
(def +tl518+
  "tl518 Kivetyt alueet" {:kohdeluokka "erotusalueet" :palvelu "sijaintipalvelu" :api-versio "v3"
                          :nimiavaruus "tiealueen-poikkileikkaus" :kohdeluokka->tyyppi-fn muu-kohdeluokka->tyyppi
                          :tyyppi-polku muu-kohdeluokka-tyyppi-polku})
(def +tl520+
  "tl520 Puomit" {:kohdeluokka "puomit-sulkulaitteet-pollarit" :palvelu "varusterekisteri" :api-versio "v1"
                  :nimiavaruus "varusteet" :kohdeluokka->tyyppi-fn varuste-kohdeluokka->tyyppi
                  :tyyppi-polku varuste-tyyppi-polku})
(def +tl522+
  "tl522 Reunakivet" {:kohdeluokka "reunatuet" :palvelu "varusterekisteri" :api-versio "v1"
                      :nimiavaruus "varusteet" :kohdeluokka->tyyppi-fn varuste-kohdeluokka->tyyppi
                      :tyyppi-polku varuste-tyyppi-polku})
(def +tl524+
  "tl524 Viherkuviot" {:kohdeluokka "viherkuviot" :palvelu "tiekohderekisteri" :api-versio "v1"
                       :nimiavaruus "ymparisto" :kohdeluokka->tyyppi-fn muu-kohdeluokka->tyyppi
                       :tyyppi-polku muu-kohdeluokka-tyyppi-polku})

(def +pylvaat+
  {:kohdeluokka "pylvaat" :api-versio "v1" :nimiavaruus "varusteet" :kohdeluokka->tyyppi-fn varuste-kohdeluokka->tyyppi
   :tyyppi-polku varuste-tyyppi-polku})

(def +valimaiset-varustetoimenpiteet+
  "Välimäiset varustetoimenpiteet" {:kohdeluokka "toimenpiteet/valimaiset-varustetoimenpiteet" :palvelu "toimenpiderekisteri" :api-versio "v1"})


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
                            +tl524+
                            +pylvaat+])

; * tl503 "Levähdysalueiden varusteet"
;   tl504 "WC"
;   tl505 "Jätehuolto"
;   tl507 "Bussipysäkin varusteet"
;   tl508 "Bussipysäkin katos"
;   tl516 "Hiekkalaatikot"


(defn lokita-urakkahakuvirhe [viesti]
  (log/error viesti))

(defn hae-id->urakka-pvm-map
  "Hakee urakan päivämäärätietoja sellaisille urakoille, joille on olemassa velho_oid (ovat siis velhosta löytyviä MHU urakoita).
  Palauttaa:
  {\"36\" {:alkupvm <clj-date> :loppupvm <clj-date>} \"38\" {:alkupvm <> :loppupvm <> ...  }}"
  [db]
  (->> (q-urakat/hae-kaikki-urakat-pvm db)                  ; [{:id 36 :alkupvm <sql-date> :loppupvm <sql-date>} {...} ... ]
       (map
         (fn [{:keys [id alkupvm loppupvm]}]
           [id {:alkupvm alkupvm :loppupvm loppupvm}]))     ; ([36 {:alkupvm <sql-date> :loppupvm <sql-date>}] [38 {...}])
       (into {})))                                          ; {36 {:alkupvm <sql-date> :loppupvm <sql-date>} 38 {...}}

(defn hae-velho-oid->urakka-id-map
  "Palauttaa:
  {\"1.2.3\" {:id 36 :alkupvm <clj-date> :loppupvm <clj-date>} \"1.2.4\" { :id 38...  }}"
  [db]
  ; [{:velho_oid "1.2.3" :id 36 :alkupvm <sql-date> :loppupvm <sql-date>} {...} ... ]
  ; (["1.2.3" 36]["1.2.4" 38 ]...)
  ; {"1.2.3" 36 "1.2.4" 38}
  (->> (q-urakat/hae-kaikki-urakka-velho-oid db)
       (map
         (fn [{:keys [velho_oid id]}]
           [velho_oid id]))
       (into {})))

(defn virhe-oidit-set
  [db]
  (->> (q-toteumat/varustetoteuma-ulkoiset-virhe-oidit db)
    (map :virhekohteen_oid)
    (set)))

(def memo-virhe-oidit-set
  (memo/ttl virhe-oidit-set :ttl/threshold +virhe-oidit-memoize-ttl+))

(def memo-id->urakka-pvm-map
  (memo/ttl hae-id->urakka-pvm-map :ttl/threshold +urakka-memoize-ttl+))

(def memo-velho-oid->urakka-map
  (memo/ttl hae-velho-oid->urakka-id-map :ttl/threshold +urakka-memoize-ttl+))

(defn virhe-oidit
  [db]
  (memo-virhe-oidit-set db))

(defn urakka-pvmt-idlla
  "Paluttaa {:alkupvm <sql-date> :loppupvm <sql-date>} kysytylle urakalle `id`."
  [db id]
  (get (memo-id->urakka-pvm-map db) id))

(defn hae-urakka-velho-oidlla
  "Paluttaa sen urakan id:n jolla on annettu Velhon muutoksen lähde:
  [Urakka] -> Maanteiden hoitourakka -> Yhteiset ominaisuudet -> Urakoiden yhteiset ominaisuudet -> Ominaisuudet -> Urakkakoodi"
  [db muutoksen-lahde-oid]
  (get (memo-velho-oid->urakka-map db) muutoksen-lahde-oid))

(defn urakka-id-kohteelle [db {:keys [muutoksen-lahde-oid]}]
  (hae-urakka-velho-oidlla db muutoksen-lahde-oid))

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

(defn kohteet-historia-ndjson->kohteet
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
  [ndjson]
  (let [rivit (clojure.string/split-lines ndjson)
        merkitse-vektorin-viimeinen (fn [v] (concat (butlast v) [(assoc (last v) :uusin-versio true)]))]
    (->> rivit
         (map json->kohde-array)                            ;[{:kohde <kohde> :json <json>} ... ]
         (map merkitse-vektorin-viimeinen)
         flatten)))

(defn hae-viimeisin-hakuaika-lahteelle
  "Hakee tietokannasta kohdeluokan viimeisimmän hakuajan, jolloin kyseistä kohdeluokkaa on haettu Velhosta.
  Jos kohdeluokkaa ei ole koskaan vielä haettu, palautetaan 2000-01-01T00:00:00Z ja insertoidaan se tietokantaan."
  [db kohdeluokka]
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

(defn tallenna-kohteet
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
  [{:keys [sisalto oidit]} url tallenna-fn tallenna-virhe-fn virhe-oidit-fn]
  (let [haetut-oidit (set oidit)
        {saadut-kohteet :kohteet
         jasennys-onnistui? :onnistui} (try
                                         {:kohteet (kohteet-historia-ndjson->kohteet sisalto)
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
        tallennettavat-oidit (set/difference saadut-oidit ylimaaraiset-oidit (virhe-oidit-fn))
        tallennettavat-kohteet (filter #(contains? tallennettavat-oidit (:oid %)) saadut-kohteet)]
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
                                  (tallenna-virhe-fn kohde (str t " url: " url))
                                  false)))
                            tallennettavat-kohteet)]
          {:onnistuneet (count (filter true? tulokset))
           :epaonnistuneet (count (filter false? tulokset))
           :ylimaaraiset (count ylimaaraiset-oidit)
           :saadut (count saadut-kohteet)}))
      {:onnistuneet 0
       :epaonnistuneet 0
       :ylimaaraiset 0
       :saadut 0})))

(defn muodosta-kohteet-url [varuste-api-juuri-url {:keys [palvelu api-versio]}]
  (let [historia-osa (if (= "sijaintipalvelu" palvelu)
                       ""
                       "/historia")]
    (str varuste-api-juuri-url "/" palvelu "/api/" api-versio historia-osa "/kohteet?rikasta=geometriat,sijainnit")))

(defn hae-kohdetiedot [kohteet-url konteksti token-fn oidit tallenna-virhe-fn]
  (let [token (token-fn)]
    (if token
      (try+
        (let [pyynto (json/write-str oidit)
              otsikot {"Content-Type" "application/json; charset=utf-8"
                       "Authorization" (str "Bearer " token)}
              http-asetukset {:metodi :POST
                              :url kohteet-url
                              :otsikot otsikot}
              {sisalto :body} (integraatiotapahtuma/laheta konteksti :http http-asetukset pyynto)]
          {:sisalto sisalto
           :oidit oidit})
        (catch [:type virheet/+ulkoinen-kasittelyvirhe-koodi+] {:keys [virheet]}
          (tallenna-virhe-fn nil (str "Ulkoinen käsittelyvirhe. Haku Velhosta epäonnistui. url: " kohteet-url " virheet: " virheet))
          false)
        (catch Throwable t
          (tallenna-virhe-fn nil (str "Poikkeus. Haku Velhosta epäonnistui. url: " kohteet-url " Throwable: " t))
          false))
      (do
        (tallenna-virhe-fn nil
                           (str "Autentikaatio virhe. Haku Velhosta epäonnistui. Käyttäjätunnistus epäonnistui. Kohteiden url: " kohteet-url))
        false))))

(defn muodosta-oidit-url
  "`kohdeluokka` sisältää /-merkin. esim. `varusteet/kaiteet`"
  [lahde varuste-api-juuri viimeksi-haettu-velhosta]
  (let [viimeksi-haettu-iso-8601 (varuste-vastaanottosanoma/aika->velho-aika viimeksi-haettu-velhosta)]
    (str varuste-api-juuri "/" (:palvelu lahde) "/api/" (:api-versio lahde)
         "/tunnisteet/" (:kohdeluokka lahde) "?alkumuokkausaika="
         (http/url-encode viimeksi-haettu-iso-8601))))


(defn- hae-oidt-velhosta
  [token oid-url konteksti tallenna-virhe-fn]
  (let [otsikot {"Content-Type" "application/json"
                 "Authorization" (str "Bearer " token)}
        http-asetukset {:metodi :GET
                        :url oid-url
                        :otsikot otsikot}
        {body :body headers :headers} (integraatiotapahtuma/laheta konteksti :http http-asetukset)
        tulos (jasenna-varusteiden-oidit oid-url body headers tallenna-virhe-fn)]
    tulos))

(defn hae-ja-palauta [lahde viimeksi-haettu-velhosta konteksti varuste-api-juuri token-fn tallenna-hakuaika-fn tallenna-virhe-fn virhe-oidit-fn]
  (let [url (muodosta-oidit-url lahde varuste-api-juuri viimeksi-haettu-velhosta)
        kohteet-url (muodosta-kohteet-url varuste-api-juuri lahde)
        token (token-fn)]
    (if token
      (try+
        (let [haku-alkanut (pvm/nyt)
              {tila :tila oidit :oidit} (hae-oidt-velhosta token url konteksti tallenna-virhe-fn)]
          (when (and tila (not-empty oidit))
            (let [oidit-alijoukot (partition
                                    +kohde-haku-maksimi-koko+
                                    +kohde-haku-maksimi-koko+
                                    nil
                                    oidit)
                  tulos (map #(hae-kohdetiedot kohteet-url konteksti token-fn
                                  % tallenna-virhe-fn)
                            oidit-alijoukot)
                  jasennetty-sisalto (mapcat #(kohteet-historia-ndjson->kohteet (:sisalto %)) tulos)]
              (tallenna-hakuaika-fn haku-alkanut)
              jasennetty-sisalto)))
        (catch [:type virheet/+ulkoinen-kasittelyvirhe-koodi+] {:keys [virheet]}
          (let [virheilmoitus (str "Haku Velhosta epäonnistui. Virheet: " virheet)]
            (tallenna-virhe-fn nil virheilmoitus))
          false))
      (let [virheviesti (str "Haku Velhosta epäonnistui. Autorisaatio tokenia ei saatu. Kohdeluokan url: " url)]
        (tallenna-virhe-fn nil virheviesti)
        false))))


(defn hae-ja-tallenna
  "Hakee muuttuneiden kohdeiten oid-listan ja sen avulla kutsuu partitio kerrallaan joukolle oideja kohdehakua.

  Virhekäsittely menee siten, että kutsutut funktiot hoitavat virheet yksittäisten kohdeiden osalta ja kutsuva funktio hoitaa
  virheet kokonaisille joukoille kohteita."
  [lahde viimeksi-haettu-velhosta konteksti varuste-api-juuri token-fn tallenna-fn tallenna-hakuaika-fn tallenna-virhe-fn virhe-oidit-fn]
  (let [url (muodosta-oidit-url lahde varuste-api-juuri viimeksi-haettu-velhosta)
        kohteet-url (muodosta-kohteet-url varuste-api-juuri lahde)
        token (token-fn)]
    (if token
      (try+
        (let [haku-alkanut (pvm/nyt)
              {tila :tila oidit :oidit} (hae-oidt-velhosta token url konteksti tallenna-virhe-fn)]
          (if (and tila (not-empty oidit))
            (let [oidit-alijoukot (partition
                                    +kohde-haku-maksimi-koko+
                                    +kohde-haku-maksimi-koko+
                                    nil
                                    oidit)
                  sisalto (map #(hae-kohdetiedot kohteet-url konteksti token-fn
                                   % tallenna-virhe-fn)
                             oidit-alijoukot)
                  tulokset (map #(tallenna-kohteet % url tallenna-fn tallenna-virhe-fn virhe-oidit-fn) sisalto)
                  {:keys [onnistuneet epaonnistuneet ylimaaraiset saadut]} (apply merge-with + tulokset)]
              (log/info "Varustehaku Velhosta palautti " saadut " kohdetta. "
                "Tallennettiin " onnistuneet " kpl. (Ylimääräisiä oideja " ylimaaraiset " kpl.)")
              (if (= 0 epaonnistuneet)
                (do
                  (tallenna-hakuaika-fn haku-alkanut)
                  true)
                false))
            (if tila
              (do
                (tallenna-hakuaika-fn haku-alkanut)
                true)
              false)))
        (catch [:type virheet/+ulkoinen-kasittelyvirhe-koodi+] {:keys [virheet]}
          (let [virheilmoitus (str "Haku Velhosta epäonnistui. Virheet: " virheet)]
            (tallenna-virhe-fn nil virheilmoitus))
          false))
      (let [virheviesti (str "Haku Velhosta epäonnistui. Autorisaatio tokenia ei saatu. Kohdeluokan url: " url)]
        (tallenna-virhe-fn nil virheviesti)
        false))))

(defn sijainti-kohteelle [db {:keys [sijainti alkusijainti loppusijainti]}]
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
  [db varustetoteuma kohde]
  (let [lokita-ja-heita-poikkeus-fn (fn [poikkeus]
                                      (velho-yhteiset/lokita-ja-tallenna-hakuvirhe
                                        db kohde
                                        (str "hae-varustetoteumat-velhosta: tallenna-toteuma-fn: " poikkeus))
                                      (throw poikkeus))]
    (try
      (q-toteumat/luo-varustetoteuma-ulkoiset<! db varustetoteuma)
      (catch PSQLException e
        (if (str/includes? (.getMessage e) "varustetoteuma_ulkoiset_unique_versio")
          (try
            (log/warn "Päivitetään varustetoteuma oid: "
                      (:ulkoinen_oid varustetoteuma) " alkupvm: "
                      (varuste-vastaanottosanoma/aika->velho-aika (:alkupvm varustetoteuma)))
            (q-toteumat/paivita-varustetoteuma-ulkoiset! db varustetoteuma)
            (catch Throwable t
              (lokita-ja-heita-poikkeus-fn t)))
          (lokita-ja-heita-poikkeus-fn e)))
      (catch Throwable t
        (lokita-ja-heita-poikkeus-fn t)))))

(defn jasenna-ja-tarkasta-varustetoteuma
  [db kohde]
  (let [urakka-id-kohteelle-fn (partial urakka-id-kohteelle db) ; tässä vielä toistaikseksi parametrinä kohde, joten memoize on syvemmällä
        urakka-pvmt-idlla-fn (partial urakka-pvmt-idlla db)
        sijainti-kohteelle-fn (partial sijainti-kohteelle db) ; sijaintiavaruus on liian suuri memoizelle
        konversio-fn (partial koodistot/konversio db velho-yhteiset/lokita-ja-tallenna-hakuvirhe)]
    (varuste-vastaanottosanoma/varustetoteuma-velho->harja urakka-id-kohteelle-fn
                                                           sijainti-kohteelle-fn
                                                           konversio-fn
                                                           urakka-pvmt-idlla-fn
                                                           kohde)))

(defn tallenna-kohde-tai-lokita-virhe [db tietolahteen-kohdeluokka {:keys [oid muokattu] :as kohde}]
  (try
    (let [{varustetoteuma :tulos
           virheviesti :virheviesti
           ohitusviesti :ohitusviesti} (jasenna-ja-tarkasta-varustetoteuma
                                         db (assoc kohde :kohdeluokka tietolahteen-kohdeluokka))]
      (cond varustetoteuma
        (do
          (log/debug "Tallennetaan kohdeluokka: " tietolahteen-kohdeluokka "oid: " oid
            " version-voimassaolo.alku: " (-> kohde :version-voimassaolo :alku))
          (lisaa-tai-paivita-kantaan db varustetoteuma kohde)
          true)

        virheviesti
        (do
          (velho-yhteiset/lokita-ja-tallenna-hakuvirhe
            db kohde
            (str "hae-varustetoteumat-velhosta: tallenna-toteuma-fn: Kohde ei onnistu muuttaa Harjan muotoon. ulkoinen_oid: "
              (format "%s muokattu: %s validointivirhe: %s"
                oid muokattu virheviesti)))
          false)

        :else
        (log/debug "Ohitettiin varustetoteuma. kohdeluokka: " tietolahteen-kohdeluokka "oid: " oid " version-voimassaolo.alku: " (-> kohde :version-voimassaolo :alku) " viesti: " ohitusviesti)))
    (catch Throwable t
      (log/error "Poikkeus käsiteltäessä varustetoteumaa. Kohdeluokka: "tietolahteen-kohdeluokka
        "oid: " oid " version-voimassaolo.alku: " (-> kohde :version-voimassaolo :alku)
        " Throwable:" t)
      (throw t))))



(defn paivita-varustetoteumat-valimaisille-kohteille [valimaiset-toimenpiteet db]
  (let [paivitetyt-kohteet (when (seq valimaiset-toimenpiteet)
                             (keep (fn [valimainen-toimepide]
                                    (when valimainen-toimepide
                                      (let [varuste-oid (get-in valimainen-toimepide [:ominaisuudet :toimenpiteen-kohde])
                                            alkupvm (-> (get-in valimainen-toimepide [:version-voimassaolo :alku])
                                                      varuste-vastaanottosanoma/velho-pvm->pvm
                                                      varuste-vastaanottosanoma/aika->sql)
                                            toimenpide (get-in valimainen-toimepide [:ominaisuudet :toimenpide])
                                            konversio (koodistot/konversio db velho-yhteiset/lokita-ja-tallenna-hakuvirhe "v/vtp" toimenpide valimainen-toimepide)
                                            _ (log/debug "Päivitetään välimäistä toimenpidettä: oid:" varuste-oid "alkupvm:" alkupvm "toteuma:" konversio)
                                            paivitetyt-varusteet (try (q-toteumat/paivita-varustetoteumat-oidilla-ulkoiset
                                                                      db
                                                                      {:ulkoinen_oid varuste-oid
                                                                       :toteuma konversio
                                                                       :alkupvm alkupvm})
                                                                 (catch Throwable t
                                                                   (velho-yhteiset/lokita-ja-tallenna-hakuvirhe
                                                                     db valimainen-toimepide
                                                                     (str "Välimäiseen varusteeseen yritetty toteuman päivistys epäonnistui. oid: " varuste-oid " alkupvm: " alkupvm " toimenpide: " toimenpide " konversio: " konversio " poikkeus:" t))
                                                                   (throw t)))]
                                        (when (seq paivitetyt-varusteet)
                                          paivitetyt-varusteet)))) valimaiset-toimenpiteet))
        _ (log/info "Päivitettiin välimäiset toimenpiteet onnistuneesti" (count paivitetyt-kohteet) "/ " (count valimaiset-toimenpiteet) "kohteelle")
        ei-paivitetyt-toimenpiteet (set/difference (set valimaiset-toimenpiteet) (set paivitetyt-kohteet))
        _ (log/info "Näille välimäisille toimenpiteille ei löytynyt päivitettävää varustetta:" ei-paivitetyt-toimenpiteet)]
    paivitetyt-kohteet))

(defn hae-ja-tallenna-tietolajin-lahde [tietolahde db konteksti varuste-api-juuri-url token-fn]
  (let [tietolahteen-kohdeluokka (:kohdeluokka tietolahde)
        viimeksi-haettu (hae-viimeisin-hakuaika-lahteelle db tietolahteen-kohdeluokka)
        _ (log/debug "Viimeksi haettu: " viimeksi-haettu "Kohdeluokka: " tietolahteen-kohdeluokka)
        tallenna-hakuaika-fn (partial tallenna-viimeisin-hakuaika-kohdeluokalle db tietolahteen-kohdeluokka)
        tallenna-virhe-fn (partial velho-yhteiset/lokita-ja-tallenna-hakuvirhe db)
        virhe-oidit-fn (partial virhe-oidit db)
        tallenna-toteuma-fn (partial tallenna-kohde-tai-lokita-virhe db tietolahteen-kohdeluokka)]
      (hae-ja-tallenna
        tietolahde viimeksi-haettu konteksti varuste-api-juuri-url token-fn tallenna-toteuma-fn tallenna-hakuaika-fn
        tallenna-virhe-fn virhe-oidit-fn)))

(defn hae-ja-palauta-tietolajin-lahde [tietolahde db konteksti varuste-api-juuri-url token-fn]
  (let [tietolahteen-kohdeluokka (:kohdeluokka tietolahde)
        viimeksi-haettu (hae-viimeisin-hakuaika-lahteelle db tietolahteen-kohdeluokka)
        _ (log/debug "Viimeksi haettu: " viimeksi-haettu "Kohdeluokka: " tietolahteen-kohdeluokka)
        tallenna-hakuaika-fn (partial tallenna-viimeisin-hakuaika-kohdeluokalle db tietolahteen-kohdeluokka)
        tallenna-virhe-fn (partial velho-yhteiset/lokita-ja-tallenna-hakuvirhe db)
        virhe-oidit-fn (partial virhe-oidit db)]
    (hae-ja-palauta
      tietolahde viimeksi-haettu konteksti varuste-api-juuri-url token-fn tallenna-hakuaika-fn
      tallenna-virhe-fn virhe-oidit-fn)))


(defn tuo-uudet-varustetoteumat-velhosta
  [integraatioloki
   db
   {:keys [token-url
           varuste-api-juuri-url
           varuste-kayttajatunnus
           varuste-salasana]}]
  (try+
    (integraatiotapahtuma/suorita-integraatio
      db integraatioloki "velho" "varustetoteumien-haku" nil
      (fn [konteksti]
        (let [token-fn (fn [] (velho-yhteiset/hae-velho-token token-url varuste-kayttajatunnus varuste-salasana konteksti))
              token (token-fn)]
          (if token
            (let [tulos (doall (map
                                 (fn [tietolahde]
                                   (hae-ja-tallenna-tietolajin-lahde tietolahde db konteksti varuste-api-juuri-url token-fn))
                                 +tietolajien-lahteet+))
                  valimaiset-toimenpiteet (hae-ja-palauta-tietolajin-lahde +valimaiset-varustetoimenpiteet+ db konteksti varuste-api-juuri-url token-fn)
                  _ (log/debug "Haettuja välimäisiä toimenpiteitä: " (when (seq valimaiset-toimenpiteet) (count valimaiset-toimenpiteet)))
                  _ (log/debug "Päivitetään välimäiset toimenpiteet kohteille lopuksi")
                  _ (paivita-varustetoteumat-valimaisille-kohteille valimaiset-toimenpiteet db)]
              (when-not (every? true? tulos)
                (virheet/heita-poikkeus virheet/+ulkoinen-kasittelyvirhe-koodi+ "Tietolajien lähteiden haussa virheitä")))
            (virheet/heita-poikkeus virheet/+ulkoinen-kasittelyvirhe-koodi+ "Velho-tokenin haku epäonnistui")))))
    (catch [:type virheet/+ulkoinen-kasittelyvirhe-koodi+] {:keys [virheet]}
      (log/error "Integraatioajo tuo-uudet-varustetoteumat-velhosta epäonnistui. Virheet: " virheet)
      false)))

(defn- paivita-velho-oid-urakalle-fn
  [db]
  (fn [kohde]
    (if kohde                                               ; Kun JSON jäsennys epäonnistuu => kohde on nil
      (let [urakkanro (-> kohde :ominaisuudet :urakkakoodi)
            velho-oid (:oid kohde)
            paivitetty (try
                         (let [rivien-maara (q-urakat/paivita-velho_oid-urakalle! db {:urakkanro urakkanro :velho_oid velho-oid})]
                           (when (= 0 rivien-maara)         ; Vain 0 mahdollinen, jos >1 => Duplicate key violation
                             (lokita-urakkahakuvirhe (str "Virhe kohdistettaessa Velho urakkaa '" velho-oid
                                                          "' Harjan WHERE urakka.urakkanro = '" urakkanro "'. SQL UPDATE palautti 0 muuttuneiden rivien lukumääräksi.")))
                           rivien-maara)
                         (catch Throwable e                 ; Duplicate key violation ja muut SQL virheet täällä
                           (lokita-urakkahakuvirhe (str "Virhe kohdistettaessa Velho urakkaa '" velho-oid
                                                        "' Harjan WHERE urakka.urakkanro = '" urakkanro "'. UPDATE poikkeus: "
                                                        e))
                           0))]
        paivitetty)
      0)))

(defn- tallenna-urakka-velho-oidt
  "Päivitetään urakka tauluun saadut velho-oid:t löytyneille WHERE urakkanro = kohde.ominaisuudet.urakkakoodi"
  [db urakka-kohteet]
  (let [paivitetty-rivit-lkm (map (paivita-velho-oid-urakalle-fn db) urakka-kohteet)
        paivittynyt-yhteensa (reduce + paivitetty-rivit-lkm)
        velho-oid-lkm-urakka-taulussa (:lkm (first (q-urakat/hae-velho-oid-lkm db)))]
    (when-not (= (count urakka-kohteet) velho-oid-lkm-urakka-taulussa)
      (lokita-urakkahakuvirhe (str "Urakka taulun velho_oid rivien lukumäärä ei vastaa Velhosta saatujen kohteiden määrää. Kohteita "
                                   (count urakka-kohteet) " taulussa velho_oideja: "
                                   velho-oid-lkm-urakka-taulussa " kpl.")))
    (log/info "Tallennettu" paivittynyt-yhteensa " velho_oid tunnistetta urakka-tauluun.")))

(defn- poista-velho-oidt
  "Poista kannasta kaikki velho-oid sarakkeen tiedot UPDATE urakka SET velho_oid=null"
  [db]
  (let [tyhjennetty-lkm (q-urakat/paivita-velho_oid-null-kaikille! db)]
    (log/info "Tyhjennetty kaikki urakka.velho_oid sarakkeen arvot, tyhjennettyjen lkm:" tyhjennetty-lkm)))

(defn- hae-urakka-kohteet-velhosta
  "Pyytää Velhosta joukon urakka-kohteita tunnisteiden avulla."
  [token varuste-urakka-kohteet-url oid-joukko konteksti]
  (let [otsikot {"Content-Type" "application/json"
                 "Authorization" (str "Bearer " token)}
        http-asetukset {:metodi :POST
                        :otsikot otsikot
                        :url varuste-urakka-kohteet-url}
        oid-joukko-json (json/write-str oid-joukko)
        {vastaus :body
         _ :headers} (integraatiotapahtuma/laheta konteksti :http http-asetukset oid-joukko-json)]
    vastaus))

(defn- jasenna-urakka-kohteet
  [urakka-kohteet-ndjson]
  (map #(try
          (json/read-str % :key-fn keyword)
          (catch Throwable _ (lokita-urakkahakuvirhe
                               (str "JSON jäsennys epäonnistui. JSON (alku 200 mki): '"
                                    (subs % 0 (min 199 (count %))) "'"))
                             nil))
       (str/split-lines urakka-kohteet-ndjson)))

(defn- tarkasta-urakka-kohteet-joukko
  [urakka-kohteet oid-joukko]
  (let [urakka-oid-joukko (set (keep :oid urakka-kohteet))
        liikaa (set/difference urakka-oid-joukko oid-joukko)
        puuttuu (set/difference oid-joukko urakka-oid-joukko)]
    (when-not (= (count urakka-oid-joukko) (count urakka-kohteet))
      (lokita-urakkahakuvirhe (str "Velhon urakkajoukko ei ole yksikäsitteinen velho_oid:lla.")))
    (when-not (= oid-joukko urakka-oid-joukko)
      (lokita-urakkahakuvirhe (str "Urakka kohteet.oid pitää olla sama joukko kuin pyynnön oid-joukko. Liikaa: "
                                   liikaa " puuttuu: " puuttuu)))))

(defn- hae-urakka-oidt-velhosta
  [token varuste-urakka-oid-url konteksti]
  (let [otsikot {"Authorization" (str "Bearer " token)}
        http-asetukset {:metodi :GET
                        :otsikot otsikot
                        :url varuste-urakka-oid-url}
        {vastaus :body
         _ :headers} (integraatiotapahtuma/laheta konteksti :http http-asetukset)
        oid-lista (json/read-str vastaus)]
    (when (empty? oid-lista) (lokita-urakkahakuvirhe "Velho palautti tyhjän OID listan"))
    (set oid-lista)))

(defn paivita-mhu-urakka-oidt-velhosta
  [integraatioloki
   db
   {:keys [token-url
           varuste-kayttajatunnus
           varuste-salasana
           varuste-urakka-oid-url
           varuste-urakka-kohteet-url]}]
  (log/debug (format "Haetaan MHU urakoita Velhosta."))
  (try+
    (integraatiotapahtuma/suorita-integraatio
      db integraatioloki "velho" "urakoiden-haku" nil
      (fn [konteksti]
        (let [virheet (atom #{})]
          (when-let [token (velho-yhteiset/hae-velho-token token-url varuste-kayttajatunnus varuste-salasana konteksti
                                            (fn [x]
                                              (swap! virheet conj (str "Virhe velho token haussa " x))
                                              (log/error "Virhe velho token haussa" x)))]
            (let [oid-joukko (hae-urakka-oidt-velhosta token varuste-urakka-oid-url konteksti)]
              (when (seq oid-joukko)
                (let [vastaus (hae-urakka-kohteet-velhosta token varuste-urakka-kohteet-url oid-joukko konteksti)
                      urakka-kohteet (jasenna-urakka-kohteet vastaus)]
                  (tarkasta-urakka-kohteet-joukko urakka-kohteet oid-joukko)
                  (poista-velho-oidt db)
                  (tallenna-urakka-velho-oidt db urakka-kohteet)))))
          (when-not (empty? @virheet)
            (virheet/heita-poikkeus virheet/+ulkoinen-kasittelyvirhe-koodi+ @virheet) ))))
    (catch [:type virheet/+ulkoinen-kasittelyvirhe-koodi+] {:keys [virheet]}
      (lokita-urakkahakuvirhe (str "MHU urakoiden haku Velhosta epäonnistui. Virheet: " virheet))
      false)
    (catch Throwable t
      (lokita-urakkahakuvirhe (str "Poikkeus MHU urakoiden haussa Velhosta. Throwable: " t))
      false)))

(defn- tee-varustetyyppi-hakuparametri
  "Tekee velhon hakuparametrin varustetyypeille, joilla on sama kohdeluokka"
  [varustetyypit kohdeluokka]
  ["kohdeluokka" ((comp #(str/join "/" %) (juxt :nimiavaruus :kohdeluokka)) kohdeluokka)
   ["joukossa"
    (concat
      [((comp #(str/join "/" %) (juxt :nimiavaruus :kohdeluokka)) kohdeluokka)]
      (:tyyppi-polku kohdeluokka))
    (mapv :tyyppi varustetyypit)]])

(defn varusteen-toimenpide [db {:keys [version-voimassaolo ominaisuudet tekninen-tapahtuma paattyen alkaen oid]}]
  (let [version-alku (:alku version-voimassaolo)
        version-loppu (:loppu version-voimassaolo)
        toimenpiteet (:toimenpiteet ominaisuudet)]
    (if (seq toimenpiteet)
      (do
        (when (< (count toimenpiteet) 1)
          (log/warn (str "Löytyi varusteversio, jolla on monta toimenpidettä: oid: " oid
                      " version-alku:" version-alku ". Toimenpiteet: (" (str/join ", " toimenpiteet) ")"
                      " Otetaan vain 1. toimenpide talteen.")))
        (:otsikko (first (q-nimikkeistot/hae-nimikkeen-tiedot db {:tyyppi-nimi (first toimenpiteet)}))))

      (cond (= "tekninen-tapahtuma/tt01" tekninen-tapahtuma) "Tieosoitemuutos"
        (= "tekninen-tapahtuma/tt02" tekninen-tapahtuma) "Muu tekninen toimenpide"
        (and (nil? version-voimassaolo) paattyen) "Poistettu" ; Sijaintipalvelu ei palauta versioita
        (and (nil? version-voimassaolo) (not paattyen)) "Lisätty"
        (= alkaen version-alku) "Lisätty" ; varusteen syntymäpäivä, onnea!
        (some? version-loppu) "Poistettu" ; uusimmalla versiolla on loppu
        :else "Päivitetty"))))

(defn hae-urakan-varustetoteumat [integraatioloki db {:keys [token-url
                                                             varuste-kayttajatunnus
                                                             varuste-salasana]}
                                  {:keys [urakka-id kohdeluokat varustetyypit kuntoluokat tie aosa aeta losa leta
                                          hoitovuoden-kuukausi hoitokauden-alkuvuosi toimenpide] :as tiedot}]
  (integraatiotapahtuma/suorita-integraatio db integraatioloki "velho" "varustetoteumien-haku" nil
    (fn [konteksti]
      (let [virheet (atom #{})]
        (when-let [token (velho-yhteiset/hae-velho-token token-url varuste-kayttajatunnus varuste-salasana konteksti
                           (fn [x]
                             (swap! virheet conj (str "Virhe velho token haussa " x))
                             (log/error "Virhe velho token haussa" x)))]
          (let [otsikot {"Content-Type" "application/json"
                         "Authorization" (str "Bearer " token)}
                kohdeluokat (if (and (set? kohdeluokat) (seq kohdeluokat))
                              (filter #(kohdeluokat (:kohdeluokka %)) +tietolajien-lahteet+)
                              +tietolajien-lahteet+)
                http-asetukset {:metodi :POST
                                :otsikot otsikot
                                ;; TODO: Ota url asetuksista
                                :url "https://apiv2stgvelho.testivaylapilvi.fi/hakupalvelu/api/v1/haku/kohdeluokat"}
                urakka-velho-oid (q-urakat/hae-urakan-velho-oid db {:id urakka-id})

                varustetyypit (group-by :kohdeluokka varustetyypit)

                varustetyypit-parametri (when (seq varustetyypit)
                                           (into ["tai"]
                                             (mapv (fn [[kohdeluokka varustetyypit]]
                                                     (let [kohdeluokka (first (filter #(= (:kohdeluokka %) kohdeluokka) kohdeluokat))]
                                                       (tee-varustetyyppi-hakuparametri varustetyypit kohdeluokka)))
                                               varustetyypit)))

                tieosoite-parametri (when tie
                                      (if losa
                                        ["kohteen-tieosoite-valilla"
                                         (cond-> {:tie tie}
                                           aosa (assoc :osa aosa)
                                           aeta (assoc :etaisyys aeta))
                                         (cond-> {:tie tie}
                                           losa (assoc :osa aosa)
                                           leta (assoc :etaisyys leta))]
                                        ["kohteen-tieosoite"
                                         (cond-> {:tie tie}
                                           aosa (assoc :osa aosa)
                                           aeta (assoc :etaisyys aeta))]))

                kuntoluokat-parametri (when (seq kuntoluokat)
                                         ["kohdeluokka" "kunto-ja-vauriotiedot/yleinen-kuntoluokka"
                                          ["joukossa"
                                           ["kunto-ja-vauriotiedot/yleinen-kuntoluokka"
                                            "ominaisuudet"
                                            "kunto-ja-vauriotiedot"
                                            "yleinen-kuntoluokka"]
                                           kuntoluokat]])

                aikavali (if hoitovuoden-kuukausi
                           (->>
                             (pvm/hoitokauden-alkuvuosi-kk->pvm hoitokauden-alkuvuosi hoitovuoden-kuukausi)
                             pvm/joda-timeksi
                             pvm/suomen-aikavyohykkeeseen
                             pvm/kuukauden-aikavali
                             (map (comp pvm/pvm->iso-8601-pvm-aika-ei-ms pvm/joda-date-timeksi)))

                           (->>
                             (pvm/hoitokauden-alkuvuosi-kk->pvm hoitokauden-alkuvuosi 9)
                             pvm/paivamaaran-hoitokausi
                             (map (comp pvm/pvm->iso-8601-pvm-aika-ei-ms pvm/utc-aikavyohykkeeseen pvm/joda-timeksi))))

                alkuaika-parametri ["kohdeluokka" "yleiset/perustiedot"
                                    ["pvm-suurempi-kuin"
                                     ["yleiset/perustiedot" "alkaen"]
                                     (first aikavali)]]

                loppuaika-parametri ["kohdeluokka" "yleiset/perustiedot"
                                    ["pvm-pienempi-kuin"
                                     ["yleiset/perustiedot" "alkaen"]
                                     (second aikavali)]]

                varustetoimenpide-parametri (when toimenpide
                                              ["kohdeluokka" "toimenpiteet/varustetoimenpiteet"
                                               ["joukossa"
                                                ["toimenpiteet/varustetoimenpiteet"
                                                 "ominaisuudet"
                                                 "toimenpiteet"]
                                                [toimenpide]]])

                payload {:asetukset {:tyyppi "kohdeluokkahaku"
                                     :liitoshaku true
                                     ;; TODO: Katso saako tämän toimimaan, jos haetaan turhia kenttiä
                                     :palautettavat-kentat []}
                         :kohdeluokat (mapv (comp #(str/join "/" %) (juxt :nimiavaruus :kohdeluokka)) kohdeluokat)
                         :lauseke (keep identity
                                    ["ja"
                                     ["kohdeluokka" "yleiset/perustiedot"
                                      ["joukossa"
                                       ["yleiset/perustiedot"
                                        "muutoksen-lahde-oid"]
                                       [urakka-velho-oid]]]
                                     varustetyypit-parametri
                                     tieosoite-parametri
                                     kuntoluokat-parametri
                                     varustetoimenpide-parametri
                                     alkuaika-parametri
                                     loppuaika-parametri])}
                {vastaus :body
                 _ :headers} (integraatiotapahtuma/laheta konteksti :http http-asetukset (json/write-str payload))
                varusteet (:osumat (json/read-str vastaus :key-fn keyword))

                varusteet
                (mapv (fn [varuste]
                        (let [{tie :tie alkuet :etaisyys alkuosa :osa} (or (:sijainti varuste) (:alkusijainti varuste))
                              {loppuetaisyys :etaisyys loppuosa :osa} (:loppusijainti varuste)
                              alkupvm (some-> (:alkaen varuste)
                                        (pvm/iso-8601->pvm)
                                        (varuste-vastaanottosanoma/aika->sql))
                              tyyppi (or
                                       (get-in varuste [:ominaisuudet :rakenteelliset-ominaisuudet :tyyppi])
                                       (get-in varuste [:ominaisuudet :tyyppi])
                                       (get-in varuste [:ominaisuudet :rakenteelliset-ominaisuudet :kaivon-tyyppi]))
                              kuntoluokka (get-in varuste [:ominaisuudet
                                                           :kunto-ja-vauriotiedot
                                                           :yleinen-kuntoluokka])
                              ;; TODO: Hae myös toimenpide ja kuntoluokka yms. tässä eikä frontissa
                              {tyyppi :otsikko kohdeluokka :kohdeluokka} (first (q-nimikkeistot/hae-nimikkeen-tiedot db {:tyyppi-nimi tyyppi}))
                              {kuntoluokka :otsikko} (first (q-nimikkeistot/hae-nimikkeen-tiedot db
                                                          {:tyyppi-nimi kuntoluokka}))]
                          {:alkupvm alkupvm
                           :kuntoluokka kuntoluokka
                           ;; TODO: Lisää lisätieto kuten ennenkin
                           :lisatieto (varuste-vastaanottosanoma/varusteen-lisatieto (partial koodistot/konversio db velho-yhteiset/lokita-ja-tallenna-hakuvirhe) tyyppi varuste)
                           :loppupvm (cond-> (get-in varuste [:version-voimassaolo :loppu])
                                       (get-in varuste [:version-voimassaolo :loppu])
                                       pvm/iso-8601->pvm
                                       (get-in varuste [:version-voimassaolo :loppu])
                                       varuste-vastaanottosanoma/aika->sql)
                           :muokattu (when (:muokattu varuste) (varuste-vastaanottosanoma/aika->sql (pvm/psql-timestamp->aika (:muokattu varuste))))
                           :muokkaaja (get-in varuste [:muokkaaja :kayttajanimi])
                           :sijainti (or (varuste-vastaanottosanoma/velhogeo->harjageo (:keskilinjageometria varuste))
                                       (sijainti-kohteelle db varuste))
                           :tyyppi tyyppi
                           :kohdeluokka kohdeluokka
                           :toimenpide (varusteen-toimenpide db varuste)
                           :tr-numero tie
                           :tr-alkuosa alkuosa
                           :tr-alkuetaisyys alkuet
                           :tr-loppuosa loppuosa
                           :tr-loppuetaisyys loppuetaisyys
                           :ulkoinen-oid (:oid varuste)}))
                  varusteet)]

            {:urakka-id urakka-id :toteumat varusteet}))))))

(defn hae-ja-tallenna-kohdeluokan-nimikkeisto [{:keys [db]} virheet hae-token-fn konteksti
                                               {:keys [kohdeluokka kohdeluokka->tyyppi-fn nimiavaruus]}
                                               hae-kuntoluokat? hae-varustetoimenpiteet?]
  (when-let [token (hae-token-fn)]
    (let [otsikot {"Content-Type" "application/json"
                   "Authorization" (str "Bearer " token)}
          http-asetukset {:metodi :GET
                          :otsikot otsikot
                          ;; TODO: Ota url asetuksista
                          :url (str/join "/"
                                 ["https://apiv2stgvelho.testivaylapilvi.fi/metatietopalvelu/api/v2/metatiedot/kohdeluokka/"
                                  nimiavaruus kohdeluokka])}
          {vastaus :body} (integraatiotapahtuma/laheta konteksti :http http-asetukset)

          vastaus (json/read-str vastaus :key-fn keyword)

          ;; nimikkeisto_nimiavaruus_kohdeluokka-tyyppi
          kohdeluokan-tyyppi-nimike (-> vastaus
                                      :components
                                      :schemas
                                      ((keyword (str/join "_" ["kohdeluokka" nimiavaruus kohdeluokka])))
                                      kohdeluokka->tyyppi-fn
                                      :$ref
                                      (str/split #"/")
                                      last)
          ;; kohdeluokka-tyyppi
          kohdeluokan-tyyppi (last (str/split kohdeluokan-tyyppi-nimike #"_"))

          ;; Kohdeluokilla on tyyppi, jonka avain on uniikki joka kohdeluokalle.
          ;; Haetaan siis se saadun metatiedon kuvauksesta
          ;; Tämän jälkeen haetaan sen kaikille versioille sen kaikki mahdolliset arvot ja tallennetaan ne kantaan.
          ;; Tallennetaan myös tieto siitä, mille kohdeluokalle tyyppi kuuluu, jotta voidaan tunnistaa varusteen kohdeluokka sen kohdeluokkatyypistä.
          kohdeluokkatyyppien-haku-onnistui?
          (seq (mapv (fn [[versio tyyppi-info]]
                       (mapv (fn [nimike]
                               (let [[tyyppi-avain nimi] (str/split nimike #"/")]
                                 (q-nimikkeistot/luo-velho-nimikkeisto<! db
                                   {:tyyppi-avain tyyppi-avain
                                    :kohdeluokka kohdeluokka
                                    :nimiavaruus nimiavaruus
                                    :nimi nimi
                                    :versio (Integer/parseInt (name versio))
                                    :otsikko (:otsikko ((keyword tyyppi-avain nimi) tyyppi-info))})))
                         (get-in vastaus [:components :schemas (keyword kohdeluokan-tyyppi-nimike) :enum])))
                 (-> vastaus :info :x-velho-nimikkeistot ((keyword nimiavaruus kohdeluokan-tyyppi)) :nimikkeistoversiot)))

          kuntoluokkien-haku-onnistui?
          (seq (when hae-kuntoluokat?
                 (mapv (fn [[versio kuntoluokka-info]]
                         (mapv (fn [kuntoluokka]
                                 (let [[nimiavaruus kuntoluokka] (str/split kuntoluokka #"/")]
                                   (q-nimikkeistot/luo-velho-nimikkeisto<! db
                                     {:tyyppi-avain nimiavaruus
                                      :kohdeluokka ""
                                      :nimiavaruus nimiavaruus
                                      :nimi kuntoluokka
                                      :versio (Integer/parseInt (name versio))
                                      :otsikko (:otsikko ((keyword nimiavaruus kuntoluokka) kuntoluokka-info))}))
                                 ) (-> vastaus :components :schemas :nimikkeisto_kunto-ja-vauriotiedot_kuntoluokka :enum)))
                   (-> vastaus :info :x-velho-nimikkeistot :kunto-ja-vauriotiedot/kuntoluokka :nimikkeistoversiot))))


          varustetoimenpiteiden-haku-onnistui?
          (seq (when hae-varustetoimenpiteet?
                 (mapv (fn [[versio varustetoimenpide-info]]
                         (mapv (fn [varustetoimenpide]
                                 (let [[nimiavaruus nimi] (str/split varustetoimenpide #"/")]
                                   (q-nimikkeistot/luo-velho-nimikkeisto<! db
                                     {:tyyppi-avain nimiavaruus
                                      :kohdeluokka ""
                                      :nimiavaruus nimiavaruus
                                      :nimi nimi
                                      :versio (Integer/parseInt (name versio))
                                      :otsikko (:otsikko ((keyword varustetoimenpide) varustetoimenpide-info))})))
                           (-> vastaus :components :schemas :nimikkeisto_toimenpiteet_varustetoimenpide :enum)))
                   (-> vastaus :info :x-velho-nimikkeistot :toimenpiteet/varustetoimenpide :nimikkeistoversiot))))]

      {:kohdeluokkatyyppien-haku-onnistui? kohdeluokkatyyppien-haku-onnistui?
       :kuntoluokkien-haku-onnistui? kuntoluokkien-haku-onnistui?
       :varustetoimenpiteiden-haku-onnistui? varustetoimenpiteiden-haku-onnistui?})))

(defn tuo-velho-nimikkeisto [{db :db integraatioloki :integraatioloki
                              {:keys [token-url varuste-kayttajatunnus varuste-salasana]} :asetukset :as this}]
  (integraatiotapahtuma/suorita-integraatio db integraatioloki "velho" "nimikkeiston-tuonti" nil
    (fn [konteksti]
      (let [virheet (atom #{})
            hae-token-fn #(velho-yhteiset/hae-velho-token token-url varuste-kayttajatunnus varuste-salasana konteksti
                            (fn [x]
                              (swap! virheet conj (str "Virhe velho token haussa " x))
                              (log/error "Virhe velho token haussa" x)))]
        (loop [kohdeluokat +tietolajien-lahteet+
               hae-kohdeluokat? true
               hae-varustetoimenpiteet? true]
          (when-not (empty? kohdeluokat)
            ;; TODO: Paranna virheiden lokitusta. Kohdeluokan nimikkeistön epäonnistuessa älä keskeytä koko integraatiota
            ;;       vaan lokita virhe ja jatka.
            (let [{:keys [kuntoluokkien-haku-onnistui?
                          varustetoimenpiteiden-haku-onnistui?]}
                  (hae-ja-tallenna-kohdeluokan-nimikkeisto
                    this virheet hae-token-fn konteksti (first kohdeluokat)
                    hae-kohdeluokat? hae-varustetoimenpiteet?)]
              (recur (rest kohdeluokat)
                (and hae-kohdeluokat? (not kuntoluokkien-haku-onnistui?))
                (and hae-varustetoimenpiteet? (not varustetoimenpiteiden-haku-onnistui?))))))

        (when-not (empty? @virheet)
          (log/error "Velhon nimikkeistön tuonnissa virheitä!" @virheet))))))

(comment
  (mapv :kohdeluokka +tietolajien-lahteet+)

  (def foo (hae-urakan-varustetoteumat (:integraatioloki harja.palvelin.main/harja-jarjestelma)
             (:db harja.palvelin.main/harja-jarjestelma)
             (:asetukset (:velho-integraatio harja.palvelin.main/harja-jarjestelma))
             35))

  (def foo2 (json/read-str foo :key-fn keyword))

  (tuo-velho-nimikkeisto (:velho-integraatio harja.palvelin.main/harja-jarjestelma)))
