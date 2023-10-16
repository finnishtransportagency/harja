(ns harja.palvelin.integraatiot.velho.varusteet
  (:require
    [clojure.core.memoize :as memo]
    [clojure.data.json :as json]
    [clojure.string :as str]
    [taoensso.timbre :as log]
    [harja.kyselyt.koodistot :as koodistot]
    [harja.kyselyt.toteumat :as q-toteumat]
    [harja.kyselyt.urakat :as q-urakat]
    [harja.kyselyt.velho-nimikkeistot :as q-nimikkeistot]
    [harja.palvelin.integraatiot.integraatiotapahtuma :as integraatiotapahtuma]
    [harja.palvelin.integraatiot.velho.sanomat.varuste-vastaanottosanoma :as varuste-vastaanottosanoma]
    [harja.palvelin.integraatiot.velho.yhteiset :as velho-yhteiset]
    [harja.pvm :as pvm]))

(def +kohde-haku-maksimi-koko+ 1000)
(def +virhe-oidit-memoize-ttl+ (* 10 60 1000))
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


(defn lokita-oid-haku [oidit url]
  (log/info (str "Haku Velhosta onnistui. Saatiin " (count oidit) " oidia. Url: " url)))

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
                (mapv (partial varuste-velhosta->harja db) varusteet)]
            {:urakka-id urakka-id :toteumat varusteet}))))))

(defn hae-varusteen-historia [integraatioloki db {:keys [token-url
                                                             varuste-kayttajatunnus
                                                             varuste-salasana]}
                                  {:keys [ulkoinen-oid kohdeluokka]}]
  (integraatiotapahtuma/suorita-integraatio db integraatioloki "velho" "varustetoteuman-historian-haku" nil
    (fn [konteksti]
      (let [virheet (atom #{})]
        (when-let [token (velho-yhteiset/hae-velho-token token-url varuste-kayttajatunnus varuste-salasana konteksti
                           (fn [x]
                             (swap! virheet conj (str "Virhe velho token haussa " x))
                             (log/error "Virhe velho token haussa" x)))]
          (let [otsikot {"Content-Type" "application/json"
                         "Authorization" (str "Bearer " token)}

                {:keys [api-versio palvelu]} (first (filter #(= (:kohdeluokka %) kohdeluokka) +tietolajien-lahteet+))

                http-asetukset {:metodi :GET
                                :otsikot otsikot
                                ;; TODO: Ota url asetuksista
                                :url (str "https://apiv2stgvelho.testivaylapilvi.fi/"
                                       palvelu "/api/" api-versio
                                       (when-not (= "sijaintipalvelu" palvelu) "/historia/")
                                       "/kohde/" ulkoinen-oid)}

                {vastaus :body
                 _ :headers} (integraatiotapahtuma/laheta konteksti :http http-asetukset)
                varusteet (json/read-str vastaus :key-fn keyword)

                varusteet
                (mapv (partial varuste-velhosta->harja db) varusteet)]
            varusteet))))))

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
