(ns harja.palvelin.integraatiot.velho.varusteet
  (:require [clojure.data.json :as json]
            [clojure.string :as str]
            [harja.palvelin.integraatiot.api.tyokalut.virheet :as virheet]
            [taoensso.timbre :as log]
            [harja.kyselyt.toteumat :as q-toteumat]
            [harja.kyselyt.urakat :as q-urakat]
            [harja.kyselyt.velho-nimikkeistot :as q-nimikkeistot]
            [harja.palvelin.integraatiot.integraatiotapahtuma :as integraatiotapahtuma]
            [harja.palvelin.integraatiot.velho.sanomat.varuste-vastaanottosanoma :as varuste-vastaanottosanoma]
            [harja.palvelin.integraatiot.velho.yhteiset :as velho-yhteiset]
            [harja.pvm :as pvm])
  (:use [slingshot.slingshot :only [throw+ try+]]))

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
  "tl501 Kaiteet" {:kohdeluokka "kaiteet" :palvelu "varusterekisteri" :api-versio "v1"
                   :nimiavaruus "varusteet" :kohdeluokka->tyyppi-fn varuste-kohdeluokka->tyyppi
                   :tyyppi-polku varuste-tyyppi-polku})
(def tienvarsikalusteet
  "tl503 tl504 tl505 tl507 tl508 tl516 *" {:kohdeluokka "tienvarsikalusteet" :palvelu "varusterekisteri" :api-versio "v1"
                                           :nimiavaruus "varusteet" :kohdeluokka->tyyppi-fn varuste-kohdeluokka->tyyppi
                                           :tyyppi-polku varuste-tyyppi-polku})
(def liikennemerkit
  "tl506 Liikennemerkki" {:kohdeluokka "liikennemerkit" :palvelu "varusterekisteri" :api-versio "v1"
                          :nimiavaruus "varusteet" :kohdeluokka->tyyppi-fn varuste-kohdeluokka->tyyppi
                          :tyyppi-polku varuste-tyyppi-polku})
(def rumpuputket
  "tl509 Rummut" {:kohdeluokka "rumpuputket" :palvelu "varusterekisteri" :api-versio "v1"
                  :nimiavaruus "varusteet" :kohdeluokka->tyyppi-fn varuste-kohdeluokka->tyyppi
                  :tyyppi-polku varuste-tyyppi-polku})
(def kaivot
  "tl512 Viemärit" {:kohdeluokka "kaivot" :palvelu "varusterekisteri" :api-versio "v1"
                    :nimiavaruus "varusteet" :kohdeluokka->tyyppi-fn kaivo-kohdeluokka->tyyppi
                    :tyyppi-polku kaivo-tyyppi-polku})
(def reunapaalut
  "tl513 Reunapaalut" {:kohdeluokka "reunapaalut" :palvelu "varusterekisteri" :api-versio "v1"
                       :nimiavaruus "varusteet" :kohdeluokka->tyyppi-fn varuste-kohdeluokka->tyyppi
                       :tyyppi-polku varuste-tyyppi-polku})
(def luiskat
  "tl514 Melurakenteet tl518 Kivetyt alueet" {:kohdeluokka "luiskat" :palvelu "sijaintipalvelu" :api-versio "v3"
                                              :nimiavaruus "tiealueen-poikkileikkaus" :kohdeluokka->tyyppi-fn muu-kohdeluokka->tyyppi
                                              :tyyppi-polku muu-kohdeluokka-tyyppi-polku})
(def aidat
  "tl515 Aidat" {:kohdeluokka "aidat" :palvelu "varusterekisteri" :api-versio "v1"
                 :nimiavaruus "varusteet" :kohdeluokka->tyyppi-fn varuste-kohdeluokka->tyyppi
                 :tyyppi-polku varuste-tyyppi-polku})
(def portaat
  "tl517 Portaat" {:kohdeluokka "portaat" :palvelu "varusterekisteri" :api-versio "v1"
                   :nimiavaruus "varusteet" :kohdeluokka->tyyppi-fn varuste-kohdeluokka->tyyppi
                   :tyyppi-polku varuste-tyyppi-polku})
(def erotusalueet
  "tl518 Kivetyt alueet" {:kohdeluokka "erotusalueet" :palvelu "sijaintipalvelu" :api-versio "v3"
                          :nimiavaruus "tiealueen-poikkileikkaus" :kohdeluokka->tyyppi-fn muu-kohdeluokka->tyyppi
                          :tyyppi-polku muu-kohdeluokka-tyyppi-polku})
(def puomit
  "tl520 Puomit" {:kohdeluokka "puomit-sulkulaitteet-pollarit" :palvelu "varusterekisteri" :api-versio "v1"
                  :nimiavaruus "varusteet" :kohdeluokka->tyyppi-fn varuste-kohdeluokka->tyyppi
                  :tyyppi-polku varuste-tyyppi-polku})
(def reunatuet
  "tl522 Reunakivet" {:kohdeluokka "reunatuet" :palvelu "varusterekisteri" :api-versio "v1"
                      :nimiavaruus "varusteet" :kohdeluokka->tyyppi-fn varuste-kohdeluokka->tyyppi
                      :tyyppi-polku varuste-tyyppi-polku})
(def viherkuviot
  "tl524 Viherkuviot" {:kohdeluokka "viherkuviot" :palvelu "tiekohderekisteri" :api-versio "v1"
                       :nimiavaruus "ymparisto" :kohdeluokka->tyyppi-fn muu-kohdeluokka->tyyppi
                       :tyyppi-polku muu-kohdeluokka-tyyppi-polku})

(def +pylvaat+
  {:kohdeluokka "pylvaat" :api-versio "v1" :nimiavaruus "varusteet" :kohdeluokka->tyyppi-fn varuste-kohdeluokka->tyyppi
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

(defn liikennemerkin-lisatieto [db liikennemerkki]
  (let [toiminnalliset-ominaisuudet (get-in liikennemerkki [:ominaisuudet :toiminnalliset-ominaisuudet])
        laki-tai-asetusnumero (or
                                (:asetusnumero toiminnalliset-ominaisuudet)
                                (:lakinumero toiminnalliset-ominaisuudet))
        laki-tai-asetusteksti (:otsikko (first (q-nimikkeistot/hae-nimikkeen-tiedot db
                                                 {:tyyppi-nimi laki-tai-asetusnumero})))
        lisatietoja (:lisatietoja toiminnalliset-ominaisuudet)]
    (str/join ": " (keep identity [laki-tai-asetusteksti lisatietoja]))))

(defn varuste-velhosta->harja
  "Luetaan velhosta tullut varuste harjalle sopivampaan muotoon"
  [db varuste]
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
        {tyyppi :otsikko kohdeluokka :kohdeluokka} (first (q-nimikkeistot/hae-nimikkeen-tiedot db
                                                            {:tyyppi-nimi tyyppi}))
        {kuntoluokka :otsikko} (first (q-nimikkeistot/hae-nimikkeen-tiedot db
                                        {:tyyppi-nimi kuntoluokka}))]
    {:alkupvm alkupvm
     :kuntoluokka kuntoluokka
     :lisatieto (when (= kohdeluokka (:kohdeluokka liikennemerkit))
                  (liikennemerkin-lisatieto db varuste))
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
                {vastaus :body} (integraatiotapahtuma/laheta konteksti :http http-asetukset (json/write-str payload))
                varusteet (mapv (partial varuste-velhosta->harja db) (:osumat (json/read-str vastaus :key-fn keyword)))]
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
                                :url (str/join "/" [varuste-api-juuri-url
                                                    palvelu "api" api-versio
                                                    (when-not (= "sijaintipalvelu" palvelu) "historia")
                                                    "kohde" ulkoinen-oid])}

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
