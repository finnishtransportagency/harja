(ns harja.palvelin.integraatiot.velho.varusteet
  (:require [clojure.data.json :as json]
            [clojure.string :as str]
            [clojure.core.memoize :as memo]
            [harja.palvelin.integraatiot.api.tyokalut.virheet :as virheet]
            [taoensso.timbre :as log]
            [harja.kyselyt.toteumat :as q-toteumat]
            [harja.kyselyt.urakat :as q-urakat]
            [harja.kyselyt.velho-nimikkeistot :as q-nimikkeistot]
            [harja.palvelin.integraatiot.integraatiotapahtuma :as integraatiotapahtuma]
            [harja.palvelin.integraatiot.velho.sanomat.varuste-vastaanottosanoma :as varuste-vastaanottosanoma]
            [harja.palvelin.integraatiot.velho.yhteiset :as velho-yhteiset]
            [harja.pvm :as pvm]
            [harja.tyokalut.yleiset :as yleiset])
  (:use [slingshot.slingshot :only [throw+ try+]]))


(def memoized-hae-nimikkeen-tiedot
  (memo/ttl
    q-nimikkeistot/hae-nimikkeen-tiedot
    :ttl/threshold (* 24 60 60))) ; 24 tunnin cache nimikkeistoille

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

(def kaiteet
  "tl501 Kaiteet" {:kohdeluokka "kaiteet" 
                   :palvelu "varusterekisteri" :api-versio "v1"
                   :nimiavaruus "varusteet" :kohdeluokka->tyyppi-fn varuste-kohdeluokka->tyyppi
                   :tyyppi-polku varuste-tyyppi-polku 
                   :oid-prefix "1.2.246.578.4.3.1"
                   :sijaintityyppi :vali})
(def tienvarsikalusteet
  "tl503 tl504 tl505 tl507 tl508 tl516 *" {:kohdeluokka "tienvarsikalusteet" :palvelu "varusterekisteri" :api-versio "v1"
                                           :nimiavaruus "varusteet" :kohdeluokka->tyyppi-fn varuste-kohdeluokka->tyyppi
                                           :tyyppi-polku varuste-tyyppi-polku 
                                           :oid-prefix "1.2.246.578.4.3.11"
                                           :sijaintityyppi :piste})
(def liikennemerkit
  "tl506 Liikennemerkki" {:kohdeluokka "liikennemerkit" :palvelu "varusterekisteri" :api-versio "v1"
                          :nimiavaruus "varusteet" :kohdeluokka->tyyppi-fn varuste-kohdeluokka->tyyppi
                          :tyyppi-polku varuste-tyyppi-polku
                          :oid-prefix "1.2.246.578.4.3.15"
                          :sijaintityyppi :piste})
(def rumpuputket
  "tl509 Rummut" {:kohdeluokka "rumpuputket" :palvelu "varusterekisteri" :api-versio "v1"
                  :nimiavaruus "varusteet" :kohdeluokka->tyyppi-fn varuste-kohdeluokka->tyyppi
                  :tyyppi-polku varuste-tyyppi-polku 
                  :oid-prefix "1.2.246.578.4.3.6"
                  :sijaintityyppi :piste})
(def kaivot
  "tl512 Viemärit" {:kohdeluokka "kaivot" :palvelu "varusterekisteri" :api-versio "v1"
                    :nimiavaruus "varusteet" :kohdeluokka->tyyppi-fn kaivo-kohdeluokka->tyyppi
                    :tyyppi-polku kaivo-tyyppi-polku 
                    :oid-prefix "1.2.246.578.4.3.12"
                    :sijaintityyppi :piste})
(def reunapaalut
  "tl513 Reunapaalut" {:kohdeluokka "reunapaalut" :palvelu "varusterekisteri" :api-versio "v1"
                       :nimiavaruus "varusteet" :kohdeluokka->tyyppi-fn varuste-kohdeluokka->tyyppi
                       :tyyppi-polku varuste-tyyppi-polku 
                       :oid-prefix "1.2.246.578.4.3.3"
                       :sijaintityyppi :vali})
(def luiskat
  "tl514 Melurakenteet tl518 Kivetyt alueet" {:kohdeluokka "luiskat" :palvelu "sijaintipalvelu" :api-versio "v3"
                                              :nimiavaruus "tiealueen-poikkileikkaus" :kohdeluokka->tyyppi-fn muu-kohdeluokka->tyyppi
                                              :tyyppi-polku muu-kohdeluokka-tyyppi-polku
                                              :oid-prefix "1.2.246.578.4.1.10"
                                              :sijaintityyppi :vali})
(def aidat
  "tl515 Aidat" {:kohdeluokka "aidat" :palvelu "varusterekisteri" :api-versio "v1"
                 :nimiavaruus "varusteet" :kohdeluokka->tyyppi-fn varuste-kohdeluokka->tyyppi
                 :tyyppi-polku varuste-tyyppi-polku 
                 :oid-prefix "1.2.246.578.4.3.2"
                 :sijaintityyppi :vali})
(def portaat
  "tl517 Portaat" {:kohdeluokka "portaat" :palvelu "varusterekisteri" :api-versio "v1"
                   :nimiavaruus "varusteet" :kohdeluokka->tyyppi-fn varuste-kohdeluokka->tyyppi
                   :tyyppi-polku varuste-tyyppi-polku 
                   :oid-prefix "1.2.246.578.4.3.9"
                   :sijaintityyppi :piste})
(def erotusalueet
  "tl518 Kivetyt alueet" {:kohdeluokka "erotusalueet" :palvelu "sijaintipalvelu" :api-versio "v3"
                          :nimiavaruus "tiealueen-poikkileikkaus" :kohdeluokka->tyyppi-fn muu-kohdeluokka->tyyppi
                          :tyyppi-polku muu-kohdeluokka-tyyppi-polku
                          :oid-prefix "1.2.246.578.4.1.6"
                          :sijaintityyppi :vali})
(def puomit
  "tl520 Puomit" {:kohdeluokka "puomit-sulkulaitteet-pollarit" :palvelu "varusterekisteri" :api-versio "v1"
                  :nimiavaruus "varusteet" :kohdeluokka->tyyppi-fn varuste-kohdeluokka->tyyppi
                  :tyyppi-polku varuste-tyyppi-polku
                  :oid-prefix "1.2.246.578.4.3.10"
                  :sijaintityyppi :piste})
(def reunatuet
  "tl522 Reunakivet" {:kohdeluokka "reunatuet" :palvelu "varusterekisteri" :api-versio "v1"
                      :nimiavaruus "varusteet" :kohdeluokka->tyyppi-fn varuste-kohdeluokka->tyyppi
                      :tyyppi-polku varuste-tyyppi-polku
                      :oid-prefix "1.2.246.578.4.3.7"
                      :sijaintityyppi :vali})
(def viherkuviot
  "tl524 Viherkuviot" {:kohdeluokka "viherhoitokuvio" :palvelu "tiekohderekisteri" :api-versio "v1"
                       :nimiavaruus "ymparisto" :kohdeluokka->tyyppi-fn muu-kohdeluokka->tyyppi
                       :tyyppi-polku muu-kohdeluokka-tyyppi-polku
                       :oid-prefix "1.2.246.578.4.4.5"
                       :sijaintityyppi :piste})

(def +pylvaat+
  {:kohdeluokka "pylvaat" :palvelu "tiekohderekisteri" :api-versio "v1" :nimiavaruus "varusteet" :kohdeluokka->tyyppi-fn varuste-kohdeluokka->tyyppi
   :tyyppi-polku varuste-tyyppi-polku})

(def +tietolajien-lahteet+ [kaiteet
                            tienvarsikalusteet
                            liikennemerkit
                            rumpuputket
                            kaivot
                            reunapaalut
                            luiskat
                            aidat
                            portaat
                            erotusalueet
                            puomit
                            reunatuet
                            viherkuviot
                            +pylvaat+])

(def valimaiset-kohdeluokat ["varusteet/aidat"
                             "varusteet/kaiteet"
                             "varusteet/reunapaalut"
                             "varusteet/reunatuet"])

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


(defn- tee-varustetyyppi-hakuparametri
  "Tekee velhon hakuparametrin varustetyypeille, joilla on sama kohdeluokka"
  [varustetyypit kohdeluokka]
  ["kohdeluokka" ((comp #(str/join "/" %) (juxt :nimiavaruus :kohdeluokka)) kohdeluokka)
   ["joukossa"
    (concat
      [((comp #(str/join "/" %) (juxt :nimiavaruus :kohdeluokka)) kohdeluokka)]
      (:tyyppi-polku kohdeluokka))
    (mapv :tyyppi varustetyypit)]])

(defn yhdista-valimaiset-toimenpiteet-stringiksi [db valimaiset-toimenpiteet]
  (str/join ","
    (keep
      (fn [toimenpide]
        (:otsikko
         (first (memoized-hae-nimikkeen-tiedot db {:tyyppi-nimi toimenpide}))))
      valimaiset-toimenpiteet)))

(defn varusteen-toimenpide [db {:keys [version-voimassaolo ominaisuudet paattyen alkaen oid valimaiset-toimenpiteet]}]
  (let [version-alku (:alku version-voimassaolo)
        version-loppu (:loppu version-voimassaolo)
        toimenpiteet (:toimenpiteet ominaisuudet)
        poistettu? (and paattyen
                     (pvm/sama-tai-jalkeen?
                       (pvm/iso-8601->pvm paattyen)
                       (pvm/nyt-suomessa)))]
    (if (seq toimenpiteet)
      (do
        (when (< (count toimenpiteet) 1)
          (log/warn (str "Löytyi varusteversio, jolla on monta toimenpidettä: oid: " oid
                      " version-alku:" version-alku ". Toimenpiteet: " (str/join ", " toimenpiteet)
                      " Otetaan vain 1. toimenpide talteen.")))
        (:otsikko (first (memoized-hae-nimikkeen-tiedot db {:tyyppi-nimi (first toimenpiteet)}))))
      (cond
        (seq valimaiset-toimenpiteet) (yhdista-valimaiset-toimenpiteet-stringiksi db valimaiset-toimenpiteet)
        (some? poistettu?) "Poistettu"
        (or
          (and (nil? version-voimassaolo) alkaen (not poistettu?))
          (and alkaen version-alku (not version-loppu) (not poistettu?))) "Lisätty"
        :else "Päivitetty"))))

(defn liikennemerkin-lisatieto [db liikennemerkki]
  (let [toiminnalliset-ominaisuudet (get-in liikennemerkki [:ominaisuudet :toiminnalliset-ominaisuudet])
        laki-tai-asetusnumero (or
                                (:asetusnumero toiminnalliset-ominaisuudet)
                                (:lakinumero toiminnalliset-ominaisuudet))
        laki-tai-asetusteksti (:otsikko (first (memoized-hae-nimikkeen-tiedot db
                                                 {:tyyppi-nimi laki-tai-asetusnumero})))
        lisatietoja (:lisatietoja toiminnalliset-ominaisuudet)]
    (str/join ": " (keep identity [laki-tai-asetusteksti lisatietoja]))))

(defn varuste-velhosta->harja
  "Luetaan velhosta tullut varuste harjalle sopivampaan muotoon"
  [db varuste]
  (let [{tie :tie alkuet :etaisyys alkuosa :osa} (or (:sijainti varuste) (:alkusijainti varuste))
        {loppuetaisyys :etaisyys loppuosa :osa} (:loppusijainti varuste)
        alkupvm (some-> (or (get-in varuste [:version-voimassaolo :alku]) (:alkaen varuste))
                  (pvm/iso-8601->pvm)
                  (varuste-vastaanottosanoma/aika->sql))
        tyyppi (or
                 (get-in varuste [:ominaisuudet :rakenteelliset-ominaisuudet :tyyppi])
                 (get-in varuste [:ominaisuudet :tyyppi])
                 (get-in varuste [:ominaisuudet :rakenteelliset-ominaisuudet :kaivon-tyyppi]))
        kuntoluokka (get-in varuste [:ominaisuudet
                                     :kunto-ja-vauriotiedot
                                     :yleinen-kuntoluokka])
        {tyyppi :otsikko kohdeluokka :kohdeluokka} (first (memoized-hae-nimikkeen-tiedot db
                                                            {:tyyppi-nimi tyyppi}))
        kuntoluokka (or (:otsikko (first (memoized-hae-nimikkeen-tiedot db
                                           {:tyyppi-nimi kuntoluokka})))
                      "Kuntoluokka puuttuu")]
    {:alkupvm alkupvm
     :kuntoluokka kuntoluokka
     :lisatieto (liikennemerkin-lisatieto db varuste)
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

(def loppuaika-olemassa ["tai" ["olemassa" ["yleiset/perustiedot"
                                            "paattyen"]]
                         ["olemassa" ["yleiset/versioitu"
                                      "version-voimassaolo"
                                      "loppu"]]])

(defn- tee-loppuaika-parametri [operaattori]
  [operaattori
   ["yleiset/perustiedot"
    "paattyen"]
   (->
     (pvm/nyt)
     (pvm/joda-date-timeksi)
     (pvm/suomen-aikavyohykkeeseen)
     pvm/pvm->iso-8601-pvm-aika-ei-ms)])

(def varustetoimenpiteet-polku
  ["toimenpiteet/varustetoimenpiteet"
   "ominaisuudet"
   "toimenpiteet"])

(def valimaiset-varustetoimenpiteet-polku
  ["toimenpiteet/valimaiset-varustetoimenpiteet"
   "ominaisuudet"
   "toimenpide"])

(def valimainen-sijainti-polku
  ["yleiset/valisijainti"
   "alkusijainti"])

(defn- tee-toimenpide-lisatty-parametri [toimenpiteella-suodatetut-oidit]
    ["tai"
     ["ja"
      ["ei" ["kohdeluokka"
             "yleiset/valisijainti"
             ["olemassa"
              valimainen-sijainti-polku]]]
      ["ei" ["kohdeluokka"
             "toimenpiteet/varustetoimenpiteet"
             ["olemassa"
              varustetoimenpiteet-polku]]]
      ["tai"
       ["ei" loppuaika-olemassa]
       (tee-loppuaika-parametri "pvm-suurempi-kuin")]]
     ["joukossa"
      ["yleiset/perustiedot"
       "oid"]
      toimenpiteella-suodatetut-oidit]])

(defn- tee-valimainen-toimenpide-lisatty-parametri []
  ["ja"
   ["ei" ["kohdeluokka"
          "toimenpiteet/valimaiset-varustetoimenpiteet"
          ["olemassa"
           valimaiset-varustetoimenpiteet-polku]]]
   ["tai"
    ["ei" loppuaika-olemassa]
    (tee-loppuaika-parametri "pvm-suurempi-kuin")]])

(defn- tee-kohteen-poisto-parametri [toimenpiteella-suodatetut-oidit]
  ["tai"
   ["ja" loppuaika-olemassa
    (tee-loppuaika-parametri "pvm-pienempi-kuin")]
   ["joukossa"
    ["yleiset/perustiedot"
     "oid"]
    toimenpiteella-suodatetut-oidit]])

(defn- tee-muut-varustetoimenpiteet-parametri [db toimenpiteella-suodatetut-oidit]
  (let [nimikkeet (map #(str (:nimiavaruus %) "/" (:nimi %)) (q-nimikkeistot/hae-muut-varustetoimenpide-nimikkeet db))]
    ["tai"
     ["ja"
      ["kohdeluokka" "toimenpiteet/varustetoimenpiteet"
       ["joukossa"
        varustetoimenpiteet-polku
        nimikkeet]]
      ["tai"
       ["ei" loppuaika-olemassa]
       (tee-loppuaika-parametri "pvm-suurempi-kuin")]]
     ["joukossa"
      ["yleiset/perustiedot"
       "oid"]
      toimenpiteella-suodatetut-oidit]]))

(defn- tee-muut-valimaiset-varustetoimenpiteet-parametri [db]
  (let [nimikkeet (map #(str (:nimiavaruus %) "/" (:nimi %)) (q-nimikkeistot/hae-muut-varustetoimenpide-nimikkeet db))]
    ["ja"
     ["kohdeluokka" "toimenpiteet/valimaiset-varustetoimenpiteet"
      ["joukossa"
       valimaiset-varustetoimenpiteet-polku
       nimikkeet]]
     ["tai"
      ["ei" loppuaika-olemassa]
      (tee-loppuaika-parametri "pvm-suurempi-kuin")]]))

(defn- tee-varustetoimenpide-parametri [db otsikko toimenpiteella-suodatetut-oidit]
  (let [varustetoimenpidenimike (q-nimikkeistot/hae-nimike-otsikolla db {:otsikko otsikko})]
    (when varustetoimenpidenimike
      ["kohdeluokka" "toimenpiteet/varustetoimenpiteet"
       ["tai"
        ["joukossa"
         varustetoimenpiteet-polku
         [(str "varustetoimenpide/" varustetoimenpidenimike)]]
        ["joukossa"
         ["yleiset/perustiedot"
          "oid"]
         toimenpiteella-suodatetut-oidit]]])))

(defn- tee-valimainen-varustetoimenpide-parametri [db otsikko]
  (let [varustetoimenpidenimike (q-nimikkeistot/hae-nimike-otsikolla db {:otsikko otsikko})]
    (when varustetoimenpidenimike
      ["kohdeluokka" "toimenpiteet/valimaiset-varustetoimenpiteet"
       ["joukossa"
        valimaiset-varustetoimenpiteet-polku
        [(str "varustetoimenpide/" varustetoimenpidenimike)]]])))

(defn tee-toimenpide-parametri [db toimenpide toimenpiteella-suodatetut-oidit]
  (case toimenpide
    :lisatty (tee-toimenpide-lisatty-parametri toimenpiteella-suodatetut-oidit)
    :kohteen-poisto (tee-kohteen-poisto-parametri toimenpiteella-suodatetut-oidit)
    :muut (tee-muut-varustetoimenpiteet-parametri db toimenpiteella-suodatetut-oidit)
    (:korjaus :tarkastettu :puhdistaminen) (tee-varustetoimenpide-parametri db 
                                             (str/capitalize (name toimenpide)) 
                                             toimenpiteella-suodatetut-oidit)
    (log/error "Yritettiin hakea varustetoimenpiteitä tuntemattomalla varustetoimenpiteellä" (name toimenpide))))

(defn tee-valimainen-toimenpide-parametri [db toimenpide]
  (case toimenpide
    :lisatty (tee-valimainen-toimenpide-lisatty-parametri)
    :kohteen-poisto (tee-kohteen-poisto-parametri [])
    :muut (tee-muut-valimaiset-varustetoimenpiteet-parametri db)
    (:korjaus :tarkastettu :puhdistaminen) (tee-valimainen-varustetoimenpide-parametri db
                                             (str/capitalize (name toimenpide)))
    (log/error "Yritettiin hakea valimaisia-varustetoimenpiteitä tuntemattomalla varustetoimenpiteellä" (name toimenpide))))

(defn yhdista-valimaiset-toimenpiteet-varusteisiin [map]
  (let [varusteet-toimenpiteilla (yleiset/liita-yhteen-mapit-ja-korvaa-avain map)]
    varusteet-toimenpiteilla))

(defn hae-valimaiset-varuste-toimenpiteet-oideille [db oidit http-asetukset konteksti toimenpide]
  (let [toimenpide-rajaus (when toimenpide (tee-valimainen-toimenpide-parametri db toimenpide))
        payload {:asetukset {:tyyppi "kohdeluokkahaku"
                             :liitoshaku false}
                 :kohdeluokat ["toimenpiteet/valimaiset-varustetoimenpiteet"]
                 :lauseke (keep identity
                            ["ja"
                             ["joukossa"
                              ["toimenpiteet/valimaiset-varustetoimenpiteet"
                               "ominaisuudet"
                               "toimenpiteen-kohde"]
                              oidit]
                             toimenpide-rajaus])}
        {vastaus-str :body} (integraatiotapahtuma/laheta konteksti :http http-asetukset (json/write-str payload))
        vastaus (:osumat (json/read-str vastaus-str :key-fn keyword))]
    vastaus))

(defn hae-urakan-valimaisten-varusteiden-oidit [http-asetukset konteksti urakka-velho-oid alkuaika-parametri loppuaika-parametri]
  (let [payload {:asetukset {:tyyppi "kohdeluokkahaku"
                             :liitoshaku false
                             :palautettavat-kentat [["yleiset/perustiedot" "oid"]]}
                 :kohdeluokat valimaiset-kohdeluokat
                 :lauseke (keep identity
                            ["ja"
                             ["kohdeluokka" "yleiset/perustiedot"
                              ["joukossa"
                               ["yleiset/perustiedot"
                                "muutoksen-lahde-oid"]
                               [urakka-velho-oid]]]
                             alkuaika-parametri
                             loppuaika-parametri])}
        {vastaus-str :body} (integraatiotapahtuma/laheta konteksti :http http-asetukset (json/write-str payload))
        vastaus (:osumat (json/read-str vastaus-str :key-fn keyword))]
    vastaus))



(defn hae-urakan-varustetoteumat [{:keys [integraatioloki db asetukset]}
                                  {:keys [urakka-id kohdeluokat varustetyypit kuntoluokat tie aosa aeta losa leta
                                          hoitovuoden-kuukausi hoitokauden-alkuvuosi toimenpide]}]
  (integraatiotapahtuma/suorita-integraatio db integraatioloki "velho" "varustetoteumien-haku" nil
    (fn [konteksti]
      (let [virheet (atom #{})
            {:keys [token-url
                    varuste-kayttajatunnus
                    varuste-salasana
                    varuste-api-juuri-url]} asetukset]
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
                                :url (str varuste-api-juuri-url "/hakupalvelu/api/v1/haku/kohdeluokat")}
                urakka-velho-oid (q-urakat/hae-urakan-velho-oid db {:id urakka-id})
                _ (assert urakka-velho-oid "Urakalle ei löytynyt vastaavaa Velho-oidia.")

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

                kuntoluokat-parametri (when (seq (filter #(string? %) kuntoluokat))
                                        ["kohdeluokka" "kunto-ja-vauriotiedot/yleinen-kuntoluokka"
                                         ["joukossa"
                                          ["kunto-ja-vauriotiedot/yleinen-kuntoluokka"
                                           "ominaisuudet"
                                           "kunto-ja-vauriotiedot"
                                           "yleinen-kuntoluokka"]
                                          kuntoluokat]])

                ei-kuntoluokkaa-parametri (when (some #(= :ei-kuntoluokkaa %) kuntoluokat)
                                            ["kohdeluokka" "kunto-ja-vauriotiedot/yleinen-kuntoluokka"
                                             ["ei" ["olemassa" ["kunto-ja-vauriotiedot/yleinen-kuntoluokka"
                                                                "ominaisuudet"
                                                                "kunto-ja-vauriotiedot"
                                                                "yleinen-kuntoluokka"]]]])

                kuntoluokat-parametri (when (or kuntoluokat-parametri ei-kuntoluokkaa-parametri)
                                        (keep identity ["tai"
                                                        kuntoluokat-parametri
                                                        ei-kuntoluokkaa-parametri]))

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

                alkuaika-parametri ["kohdeluokka" "yleiset/versioitu"
                                    ["pvm-suurempi-kuin"
                                     ["yleiset/versioitu" "version-voimassaolo" "alku"]
                                     (first aikavali)]]

                loppuaika-parametri ["kohdeluokka" "yleiset/versioitu"
                                     ["pvm-pienempi-kuin"
                                      ["yleiset/versioitu" "version-voimassaolo" "alku"]
                                      (second aikavali)]]
                
                valimaiset-oidit  (hae-urakan-valimaisten-varusteiden-oidit 
                                    http-asetukset 
                                    konteksti 
                                    urakka-velho-oid 
                                    alkuaika-parametri
                                    loppuaika-parametri)
                oidit (mapv :oid valimaiset-oidit)
                valimaiset-toimenpiteet (hae-valimaiset-varuste-toimenpiteet-oideille db oidit http-asetukset konteksti toimenpide)
                toimenpiteella-suodatetut-valimaiset-oidit (vec (map #(get-in % [:ominaisuudet :toimenpiteen-kohde]) valimaiset-toimenpiteet))
                varustetoimenpide-parametri (when toimenpide (tee-toimenpide-parametri db toimenpide toimenpiteella-suodatetut-valimaiset-oidit)) 
                payload {:asetukset {:tyyppi "kohdeluokkahaku"
                                     :liitoshaku true}
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
                
                {vastaus-str :body} (integraatiotapahtuma/laheta konteksti :http http-asetukset (json/write-str payload)) 
                varusteet-vastaus (:osumat (json/read-str vastaus-str :key-fn keyword))
                varusteet-valimaisilla-toimenpiteilla (yhdista-valimaiset-toimenpiteet-varusteisiin
                                                        {:kokoelma1 varusteet-vastaus
                                                         :kokoelma2 valimaiset-toimenpiteet
                                                         :yhteinen-key1 [:oid]
                                                         :yhteinen-key2 [:ominaisuudet :toimenpiteen-kohde]
                                                         :etsittava-avain [:ominaisuudet :toimenpide]
                                                         :asetettava-avain :valimaiset-toimenpiteet})
                varusteet (mapv (partial varuste-velhosta->harja db) varusteet-valimaisilla-toimenpiteilla)]
            {:urakka-id urakka-id :toteumat varusteet}))))))

(defn hae-varusteen-historia [{:keys [integraatioloki db asetukset]}
                              {:keys [ulkoinen-oid kohdeluokka]}]
  (integraatiotapahtuma/suorita-integraatio db integraatioloki "velho" "varustetoteuman-historian-haku" nil
    (fn [konteksti]
      (let [virheet (atom #{})
            {:keys [token-url
                    varuste-api-juuri-url
                    varuste-kayttajatunnus
                    varuste-salasana]} asetukset]
        (when-let [token (velho-yhteiset/hae-velho-token token-url varuste-kayttajatunnus varuste-salasana konteksti
                           (fn [x]
                             (swap! virheet conj (str "Virhe velho token haussa " x))
                             (log/error "Virhe velho token haussa" x)))]
          (let [otsikot {"Content-Type" "application/json"
                         "Authorization" (str "Bearer " token)}

                {:keys [api-versio palvelu]} (first (filter #(= (:kohdeluokka %) kohdeluokka) +tietolajien-lahteet+))

                http-asetukset {:metodi :GET
                                :otsikot otsikot
                                :url (str/join "/" (keep identity [varuste-api-juuri-url
                                                                   palvelu "api" api-versio
                                                                   (when-not (= "sijaintipalvelu" palvelu) "historia")
                                                                   "kohde" ulkoinen-oid]))}

                {vastaus :body} (integraatiotapahtuma/laheta konteksti :http http-asetukset)
                varusteet (json/read-str vastaus :key-fn keyword)

                varusteet
                (mapv (partial varuste-velhosta->harja db) varusteet)]
            varusteet))))))

(defn hae-ja-tallenna-kohdeluokan-nimikkeisto [{:keys [db asetukset]} virheet hae-token-fn konteksti
                                               {:keys [kohdeluokka kohdeluokka->tyyppi-fn nimiavaruus]}
                                               hae-kuntoluokat? hae-varustetoimenpiteet?]
  (when-let [token (hae-token-fn)]
    (try+
      (let [{:keys [varuste-api-juuri-url]} asetukset
            otsikot {"Content-Type" "application/json"
                     "Authorization" (str "Bearer " token)}
            http-asetukset {:metodi :GET
                            :otsikot otsikot
                            :url (str/join "/"
                                   [varuste-api-juuri-url "metatietopalvelu/api/v2/metatiedot/kohdeluokka"
                                    nimiavaruus kohdeluokka])}
            {vastaus :body} (integraatiotapahtuma/laheta konteksti :http http-asetukset)

            vastaus (json/read-str vastaus :key-fn keyword)

            ;; nimikkeisto_nimiavaruus_kohdeluokka-tyyppi
            kohdeluokan-tyyppi-nimike (some-> vastaus
                                        :components
                                        :schemas
                                        ((keyword (str/join "_" ["kohdeluokka" nimiavaruus kohdeluokka])))
                                        kohdeluokka->tyyppi-fn
                                        :$ref
                                        (str/split #"/")
                                        last)
            ;; kohdeluokka-tyyppi
            kohdeluokan-tyyppi (when kohdeluokan-tyyppi-nimike
                                 (last (str/split kohdeluokan-tyyppi-nimike #"_")))

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
                   (some-> vastaus :info :x-velho-nimikkeistot ((keyword nimiavaruus kohdeluokan-tyyppi)) :nimikkeistoversiot)))

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
                     (some-> vastaus :info :x-velho-nimikkeistot :kunto-ja-vauriotiedot/kuntoluokka :nimikkeistoversiot))))

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
                     (some-> vastaus :info :x-velho-nimikkeistot :toimenpiteet/varustetoimenpide :nimikkeistoversiot))))

            _liikennemerkkien-haku-onnistui?
            (seq (when (= kohdeluokka (:kohdeluokka liikennemerkit))
                   (concat
                     (mapv (fn [[versio asetusnumero-info]]
                             (mapv (fn [asetusnumero]
                                     (let [[nimiavaruus nimi] (str/split asetusnumero #"/")]
                                       (q-nimikkeistot/luo-velho-nimikkeisto<! db
                                         {:tyyppi-avain nimiavaruus
                                          :kohdeluokka ""
                                          :nimiavaruus nimiavaruus
                                          :nimi nimi
                                          :versio (Integer/parseInt (name versio))
                                          :otsikko (:otsikko ((keyword asetusnumero) asetusnumero-info))})))
                               (-> vastaus :components :schemas :nimikkeisto_varusteet_liikennemerkki-asetusnumero :enum)))
                       (-> vastaus :info :x-velho-nimikkeistot :varusteet/liikennemerkki-asetusnumero :nimikkeistoversiot))

                     (mapv (fn [[versio lakinumero-info]]
                             (mapv (fn [lakinumero]
                                     (let [[nimiavaruus nimi] (str/split lakinumero #"/")]
                                       (q-nimikkeistot/luo-velho-nimikkeisto<! db
                                         {:tyyppi-avain nimiavaruus
                                          :kohdeluokka ""
                                          :nimiavaruus nimiavaruus
                                          :nimi nimi
                                          :versio (Integer/parseInt (name versio))
                                          :otsikko (:otsikko ((keyword lakinumero) lakinumero-info))})))
                               (-> vastaus :components :schemas :nimikkeisto_varusteet_liikennemerkki-lakinumero :enum)))
                       (-> vastaus :info :x-velho-nimikkeistot :varusteet/liikennemerkki-lakinumero :nimikkeistoversiot)))))]

        {:kohdeluokkatyyppien-haku-onnistui? kohdeluokkatyyppien-haku-onnistui?
         :kuntoluokkien-haku-onnistui? kuntoluokkien-haku-onnistui?
         :varustetoimenpiteiden-haku-onnistui? varustetoimenpiteiden-haku-onnistui?})
      (catch [:type virheet/+ulkoinen-kasittelyvirhe-koodi+] error
        (swap! virheet conj (:virheet error))
        nil))))

(defn tuo-velho-nimikkeisto [{:keys [db integraatioloki asetukset] :as this}]
  (let [{:keys [token-url varuste-kayttajatunnus varuste-salasana]} asetukset]
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
              (let [{:keys [kuntoluokkien-haku-onnistui?
                            varustetoimenpiteiden-haku-onnistui?]}
                    (hae-ja-tallenna-kohdeluokan-nimikkeisto
                      this virheet hae-token-fn konteksti (first kohdeluokat)
                      hae-kohdeluokat? hae-varustetoimenpiteet?)]
                (recur (rest kohdeluokat)
                  (and hae-kohdeluokat? (not kuntoluokkien-haku-onnistui?))
                  (and hae-varustetoimenpiteet? (not varustetoimenpiteiden-haku-onnistui?))))))

          (when-not (empty? @virheet)
            (log/error "Velhon nimikkeistön tuonnissa virheitä!" @virheet)
            (throw (Error. "Velhon nimikkeistön tuonnissa virheitä"))))))))


;; Funktioita tietojen selvittelyyn Velhosta
(comment
  (defn test-hae-valimaisten-varusteiden-muutoksen-lahde [http-asetukset konteksti]
    (let [payload {:asetukset {:tyyppi "kohdeluokkahaku"
                               :liitoshaku false
                               :palautettavat-kentat []}
                   :kohdeluokat ["varusteet/aidat"]
                   :lauseke (keep identity
                              ["kohdeluokka" "yleiset/perustiedot"
                               ["olemassa"
                                ["yleiset/perustiedot"
                                 "muutoksen-lahde-oid"]]])}
          {vastaus-str :body} (integraatiotapahtuma/laheta konteksti :http http-asetukset (json/write-str payload))
          vastaus (:osumat (json/read-str vastaus-str :key-fn keyword))]
      vastaus))

  (defn test-hae-kaikki-valimaiset-varuste-toimenpiteet [http-asetukset konteksti]
    (let [payload {:asetukset {:tyyppi "kohdeluokkahaku"
                               :liitoshaku false
                               :palautettavat-kentat [["toimenpiteet/valimaiset-varustetoimenpiteet"
                                                       "ominaisuudet"
                                                       "toimenpiteen-kohde"]]}
                   :kohdeluokat ["toimenpiteet/valimaiset-varustetoimenpiteet"]}
          {vastaus-str :body} (integraatiotapahtuma/laheta konteksti :http http-asetukset (json/write-str payload))
          vastaus (:osumat (json/read-str vastaus-str :key-fn keyword))]
      vastaus))

  (defn test-hae-varusteet-oideille [oidit http-asetukset konteksti]
    (let [payload {:asetukset {:tyyppi "kohdeluokkahaku"
                               :liitoshaku false
                               :palautettavat-kentat [["yleiset/perustiedot" "muutoksen-lahde-oid"]]}
                   :kohdeluokat ["varusteet/aidat"
                                 "varusteet/kaiteet"
                                 "varusteet/reunapaalut"
                                 "varusteet/reunatuet"]
                   :lauseke (keep identity
                              ["joukossa"
                               ["yleiset/perustiedot"
                                "oid"]
                               oidit])}
          {vastaus-str :body} (integraatiotapahtuma/laheta konteksti :http http-asetukset (json/write-str payload))
          vastaus (:osumat (json/read-str vastaus-str :key-fn keyword))]
      vastaus))


  (defn test-hae-varusteet-oideille [oidit http-asetukset konteksti]
    (let [payload {:asetukset {:tyyppi "kohdeluokkahaku"
                               :liitoshaku false
                               :palautettavat-kentat [["yleiset/perustiedot" "muutoksen-lahde-oid"]]}
                   :kohdeluokat ["varusteet/aidat"
                                 "varusteet/kaiteet"
                                 "varusteet/reunapaalut"
                                 "varusteet/reunatuet"]
                   :lauseke (keep identity
                              ["joukossa"
                               ["yleiset/perustiedot"
                                "oid"]
                               oidit])}
          {vastaus-str :body} (integraatiotapahtuma/laheta konteksti :http http-asetukset (json/write-str payload))
          vastaus (:osumat (json/read-str vastaus-str :key-fn keyword))]
      vastaus))

  (defn test-hae-urakan-nimi-oideilla [oidit http-asetukset konteksti]
    (let [payload {:asetukset {:tyyppi "kohdeluokkahaku"
                               :liitoshaku false
                               :palautettavat-kentat [["yleiset/perustiedot" "oid"]
                                                      ["urakka/urakka" "ominaisuudet" "nimi"]]}
                   :kohdeluokat ["urakka/maanteiden-hoitourakka"]
                   :lauseke (keep identity
                              ["joukossa"
                               ["yleiset/perustiedot"
                                "oid"]
                               oidit])}
          {vastaus-str :body} (integraatiotapahtuma/laheta konteksti :http http-asetukset (json/write-str payload))
          vastaus (:osumat (json/read-str vastaus-str :key-fn keyword))]
      vastaus)))