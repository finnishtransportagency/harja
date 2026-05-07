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

(declare hae-valimaiset-varuste-toimenpiteet-oideille)

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
        (or (:otsikko
              (first (memoized-hae-nimikkeen-tiedot db {:tyyppi-nimi toimenpide})))
          toimenpide))
      valimaiset-toimenpiteet)))

(defn- kohde-poistettu? [paattyen]
  (and paattyen
    (pvm/sama-tai-jalkeen?
      (pvm/nyt-suomessa)
      (pvm/iso-8601->pvm paattyen))))

(defn varusteen-toimenpide [db {:keys [version-voimassaolo ominaisuudet paattyen alkaen oid valimaiset-toimenpiteet]}]
  (let [version-alku (:alku version-voimassaolo)
        version-loppu (:loppu version-voimassaolo)
        toimenpiteet (:toimenpiteet ominaisuudet)
        poistettu? (kohde-poistettu? paattyen)]
    (if (seq toimenpiteet)
      (do
        (when (< (count toimenpiteet) 1)
          (log/warn (str "Löytyi varusteversio, jolla on monta toimenpidettä: oid: " oid
                      " version-alku:" version-alku ". Toimenpiteet: " (str/join ", " toimenpiteet)
                      " Otetaan vain 1. toimenpide talteen.")))
        (or (:otsikko (first (memoized-hae-nimikkeen-tiedot db {:tyyppi-nimi (first toimenpiteet)})))
          (first toimenpiteet)))
      (cond
        (seq valimaiset-toimenpiteet) (yhdista-valimaiset-toimenpiteet-stringiksi db valimaiset-toimenpiteet)
        poistettu? "Poistettu"
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
        ;; joskus tarkempi tyyppi on nil, ja nimikkeistö-taulusta ei saada osumaa.
        ;; Fallbackataan tällöin kohdeluokka suoraan
        {tyyppi :otsikko kohdeluokka :kohdeluokka} (if (nil? tyyppi)
                                                     {:tyyppi nil
                                                      ;; parsittava varusteet/ alku pois...
                                                      :kohdeluokka (when (string? (:kohdeluokka varuste))
                                                                     (last (clojure.string/split (:kohdeluokka varuste) #"/")))}
                                                     (first (memoized-hae-nimikkeen-tiedot db
                                                              {:tyyppi-nimi tyyppi})))

        kuntoluokka (or (:otsikko (first (memoized-hae-nimikkeen-tiedot db
                                           {:tyyppi-nimi kuntoluokka})))
                      "Kuntoluokka puuttuu")
        kohdevarusteen-kohdeluokka (or (:kohdevarusteen-kohdeluokka varuste)
                                     (when (string? (:kohdeluokka varuste))
                                       (last (clojure.string/split (:kohdeluokka varuste) #"/")))
                                     kohdeluokka)]
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
     :rivityyppi (or (:rivityyppi varuste) :tavallinen-varusterivi)
     :rivi-id (or (:rivi-id varuste) (:oid varuste))
     :toimenpide-oid (:toimenpide-oid varuste)
     :kohdevarusteen-oid (or (:kohdevarusteen-oid varuste) (:oid varuste))
     :kohdevarusteen-kohdeluokka kohdevarusteen-kohdeluokka
     :ulkoinen-oid (:oid varuste)}))

(def valimaisesta-toimenpiteesta-kopioitavat-kentat
  [:sijainti
   :alkusijainti
   :loppusijainti
   :version-voimassaolo
   :alkaen
   :paattyen
   :muokattu
   :muokkaaja])

(defn- ylikirjoita-olemassa-olevat-kentat [kohde lahde kentat]
  (reduce (fn [tulos kentta]
            (if-let [arvo (get lahde kentta)]
              (assoc tulos kentta arvo)
              tulos))
    kohde
    kentat))

(defn- valimainen-toimenpiderivi-velhosta->harja [db kohdevaruste toimenpide]
  (let [kohdevarusterivi (varuste-velhosta->harja db kohdevaruste)
        kohdevarusteen-kohdeluokka (or (when (string? (:kohdeluokka kohdevaruste))
                                         (last (clojure.string/split (:kohdeluokka kohdevaruste) #"/")))
                                       (:kohdeluokka kohdevarusterivi))
        yhdistetty-varuste (-> kohdevaruste
                             (assoc-in [:ominaisuudet :toimenpiteet]
                               [(get-in toimenpide [:ominaisuudet :toimenpide])])
                             (assoc :rivityyppi :valimainen-toimenpiderivi
                                    :rivi-id (:oid toimenpide)
                                    :toimenpide-oid (:oid toimenpide)
                                    :kohdevarusteen-oid (:ulkoinen-oid kohdevarusterivi)
                                    :kohdevarusteen-kohdeluokka kohdevarusteen-kohdeluokka)
                             (ylikirjoita-olemassa-olevat-kentat toimenpide valimaisesta-toimenpiteesta-kopioitavat-kentat))]
    (varuste-velhosta->harja db yhdistetty-varuste)))

(defn- muodosta-valimaiset-toimenpiderivit [db kohdevarusteet toimenpiteet]
  (let [kohdevarusteet-oidilla (into {} (map (juxt :oid identity) kohdevarusteet))]
    (keep (fn [toimenpide]
            (let [kohdevarusteen-oid (get-in toimenpide [:ominaisuudet :toimenpiteen-kohde])]
              (if-let [kohdevaruste (get kohdevarusteet-oidilla kohdevarusteen-oid)]
                (valimainen-toimenpiderivi-velhosta->harja db kohdevaruste toimenpide)
                (do
                  (log/warn "Välimäiselle varustetoimenpiteelle ei löytynyt kohdevarustetta" {:toimenpide-oid (:oid toimenpide) 
                                                                                              :kohdevarusteen-oid kohdevarusteen-oid})
                  nil))))
      toimenpiteet)))

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

(defn- normalisoi-hoitovuosirajaus [{:keys [hoitokauden-alkuvuosi] :as tiedot}]
  (if hoitokauden-alkuvuosi
    tiedot
    (assoc tiedot :hoitovuoden-kuukausi nil)))

(defn- muodosta-aikavali [hoitokauden-alkuvuosi hoitovuoden-kuukausi]
  (when hoitokauden-alkuvuosi
    (if hoitovuoden-kuukausi
      (->>
        (pvm/hoitokauden-alkuvuosi-kk->pvm hoitokauden-alkuvuosi hoitovuoden-kuukausi)
        pvm/joda-timeksi
        pvm/suomen-aikavyohykkeeseen
        pvm/kuukauden-aikavali
        (map (comp pvm/pvm->iso-8601-pvm-aika-ei-ms pvm/joda-date-timeksi)))

      (->>
        (pvm/hoitokauden-alkuvuosi-kk->pvm hoitokauden-alkuvuosi 9)
        pvm/paivamaaran-hoitokausi
        (map (comp pvm/pvm->iso-8601-pvm-aika-ei-ms pvm/utc-aikavyohykkeeseen pvm/joda-timeksi))))))

(def version-alku-polku ["yleiset/versioitu" "version-voimassaolo" "alku"])

(def alkaen-polku ["yleiset/perustiedot" "alkaen"])

(defn- tee-alkupvm-rajaus-fallbackilla [operaattori aikaleima]
  ["tai"
   ["kohdeluokka" "yleiset/versioitu"
    [operaattori version-alku-polku aikaleima]]
   ["ja"
    ["ei" ["kohdeluokka" "yleiset/versioitu"
            ["olemassa" version-alku-polku]]]
    ["kohdeluokka" "yleiset/perustiedot"
     [operaattori alkaen-polku aikaleima]]]])

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

(defn- lisaa-oid-haku-jos-tarvitaan
  "Lisää OID-perusteisen haun 'tai'-lausekkeeseen jos oidit-lista ei ole tyhjä.
   Jos oidit on tyhjä, palauttaa vain varsinaisen haun."
  [varsinainen-haku oidit]
  (if (seq oidit)
    ["tai"
     varsinainen-haku
     ["joukossa"
      ["yleiset/perustiedot"
       "oid"]
      oidit]]
    varsinainen-haku))

(defn- tee-toimenpide-lisatty-parametri [toimenpiteella-suodatetut-oidit]
  (lisaa-oid-haku-jos-tarvitaan
    ["ja"
     ["ei" ["kohdeluokka"
            "yleiset/valisijainti"
            ["olemassa" valimainen-sijainti-polku]]]
     ["ei" ["kohdeluokka"
            "toimenpiteet/varustetoimenpiteet"
            ["olemassa" varustetoimenpiteet-polku]]]
     ["tai"
      ["ei" loppuaika-olemassa]
      (tee-loppuaika-parametri "pvm-suurempi-kuin")]]
    toimenpiteella-suodatetut-oidit))

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
  (lisaa-oid-haku-jos-tarvitaan
    ["ja" loppuaika-olemassa
     (tee-loppuaika-parametri "pvm-pienempi-kuin")]
    toimenpiteella-suodatetut-oidit))

(defn- tee-muut-varustetoimenpiteet-parametri [db toimenpiteella-suodatetut-oidit]
  (let [nimikkeet (map #(str (:nimiavaruus %) "/" (:nimi %)) (q-nimikkeistot/hae-muut-varustetoimenpide-nimikkeet db))]
    (lisaa-oid-haku-jos-tarvitaan
      ["ja"
       ["kohdeluokka" "toimenpiteet/varustetoimenpiteet"
        ["joukossa" varustetoimenpiteet-polku nimikkeet]]
       ["tai"
        ["ei" loppuaika-olemassa]
        (tee-loppuaika-parametri "pvm-suurempi-kuin")]]
      toimenpiteella-suodatetut-oidit)))

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
      (lisaa-oid-haku-jos-tarvitaan
        ["kohdeluokka" "toimenpiteet/varustetoimenpiteet"
         ["joukossa"
          varustetoimenpiteet-polku
          [(str "varustetoimenpide/" varustetoimenpidenimike)]]]
        toimenpiteella-suodatetut-oidit))))

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

(defn- tee-muutoksen-lahde-oid-parametri [oid]
  ["kohdeluokka" "yleiset/perustiedot"
   ["joukossa"
    ["yleiset/perustiedot" "muutoksen-lahde-oid"]
    [oid]]])

(defn- tee-varusteen-oid-parametri [oidit]
  ["joukossa"
   ["yleiset/perustiedot" "oid"]
   oidit])

(defn- hae-kohdeluokan-lahde [kohdeluokka]
  (first (filter #(= (:kohdeluokka %) kohdeluokka) +tietolajien-lahteet+)))

(defn- kohdeluokan-polku [lahde]
  (str/join "/" [(:nimiavaruus lahde) (:kohdeluokka lahde)]))

(defn muodosta-varusteen-historian-palautettavat-kentat [lahde]
  (let [kohdeluokka-polku (kohdeluokan-polku lahde)]
    [["yleiset/perustiedot" "oid"]
     ["yleiset/perustiedot" "muutoksen-lahde-oid"]
     ["yleiset/perustiedot" "alkaen"]
     ["yleiset/perustiedot" "paattyen"]
     ["yleiset/perustiedot" "muokattu"]
     ["yleiset/perustiedot" "muokkaaja"]
     ["yleiset/versioitu" "version-voimassaolo" "alku"]
     ["yleiset/versioitu" "version-voimassaolo" "loppu"]
     [kohdeluokka-polku "sijainti"]
     [kohdeluokka-polku "alkusijainti"]
     [kohdeluokka-polku "loppusijainti"]
     [kohdeluokka-polku "keskilinjageometria"]
     [kohdeluokka-polku "ominaisuudet" "kunto-ja-vauriotiedot" "yleinen-kuntoluokka"]
     [kohdeluokka-polku "ominaisuudet" "rakenteelliset-ominaisuudet" "tyyppi"]
     [kohdeluokka-polku "ominaisuudet" "rakenteelliset-ominaisuudet" "kaivon-tyyppi"]
     [kohdeluokka-polku "ominaisuudet" "tyyppi"]
     [kohdeluokka-polku "ominaisuudet" "toiminnalliset-ominaisuudet" "asetusnumero"]
     [kohdeluokka-polku "ominaisuudet" "toiminnalliset-ominaisuudet" "lakinumero"]
     [kohdeluokka-polku "ominaisuudet" "toiminnalliset-ominaisuudet" "lisatietoja"]
     [kohdeluokka-polku "ominaisuudet" "toimenpiteet"]]))

(defn muodosta-varusteen-historian-hakupalvelu-payload [lahde ulkoinen-oid]
  {:asetukset {:tyyppi "kohdeluokkahaku"
               :liitoshaku true
               :oid-haku true
               :palautettavat-kentat (muodosta-varusteen-historian-palautettavat-kentat lahde)}
   :kohdeluokat [(kohdeluokan-polku lahde)]
   :lauseke (tee-varusteen-oid-parametri [ulkoinen-oid])})

(defn- varusteen-historian-jarjestysavain [varuste]
  [(or (get-in varuste [:version-voimassaolo :alku])
       (:alkaen varuste)
       "")
   (or (:muokattu varuste) "")
   (or (:oid varuste) "")])

(defn jarjesta-varusteen-historiaversiot [varusteet]
  (sort-by varusteen-historian-jarjestysavain #(compare %2 %1) varusteet))

(defn- historiaversiolla-eksplisiittinen-toimenpide? [{:keys [ominaisuudet paattyen valimaiset-toimenpiteet]}]
  (or (seq (:toimenpiteet ominaisuudet))
      (seq valimaiset-toimenpiteet)
      (kohde-poistettu? paattyen)))

(defn- paivita-historiarivin-oletustoimenpide [rivi historiaversio vanhin-versio?]
  (if (historiaversiolla-eksplisiittinen-toimenpide? historiaversio)
    rivi
    (assoc rivi :toimenpide (if vanhin-versio? "Lisätty" "Päivitetty"))))

(defn- paivita-historiarivien-oletustoimenpiteet [rivit historiaversiot]
  (let [vanhin-implisiittinen-indeksi (->> historiaversiot
                                        (keep-indexed (fn [indeksi historiaversio]
                                                        (when-not (historiaversiolla-eksplisiittinen-toimenpide? historiaversio)
                                                          indeksi)))
                                        last)]
    (mapv (fn [indeksi rivi historiaversio]
            (paivita-historiarivin-oletustoimenpide rivi historiaversio (= indeksi vanhin-implisiittinen-indeksi)))
      (range)
      rivit
      historiaversiot)))

(defn- muodosta-varusteen-historian-rivit [db varusteet]
  (let [jarjestetyt-historiaversiot (jarjesta-varusteen-historiaversiot varusteet)
        historiarivit (mapv (partial varuste-velhosta->harja db) jarjestetyt-historiaversiot)]
    (paivita-historiarivien-oletustoimenpiteet historiarivit jarjestetyt-historiaversiot)))

(defn- taydenna-varusteen-kohdeluokka [lahde varuste]
  (if (:kohdeluokka varuste)
    varuste
    (assoc varuste :kohdeluokka (kohdeluokan-polku lahde))))

(defn- varusteen-historiarivin-jarjestysavain [rivi]
  [(or (:alkupvm rivi) (:muokattu rivi))
   (:muokattu rivi)
   (or (:rivi-id rivi) (:ulkoinen-oid rivi) "")])

(defn- jarjesta-varusteen-historiarivit [rivit]
  (sort-by varusteen-historiarivin-jarjestysavain #(compare %2 %1) rivit))

(defn- valimainen-kohdeluokka? [lahde]
  (contains? (set valimaiset-kohdeluokat) (kohdeluokan-polku lahde)))

(defn- hae-varusteen-edustava-kohdeversio [lahde varusteet]
  (some->> varusteet
    jarjesta-varusteen-historiaversiot
    first
    (taydenna-varusteen-kohdeluokka lahde)))

(defn- hae-varusteen-historia-hakupalvelusta [http-asetukset konteksti lahde ulkoinen-oid]
  (let [payload (muodosta-varusteen-historian-hakupalvelu-payload lahde ulkoinen-oid)
        {vastaus-str :body} (integraatiotapahtuma/laheta konteksti :http http-asetukset (json/write-str payload))
        varusteet (:osumat (json/read-str vastaus-str :key-fn keyword))]
  varusteet))

(defn- hae-varusteen-valimaiset-toimenpiderivit [db http-asetukset konteksti lahde ulkoinen-oid varusteet]
  (if-let [kohdevaruste (when (valimainen-kohdeluokka? lahde)
                          (hae-varusteen-edustava-kohdeversio lahde varusteet))]
    (let [toimenpiteet (hae-valimaiset-varuste-toimenpiteet-oideille
                         db
                         [ulkoinen-oid]
                         http-asetukset
                         konteksti
                         nil)]
      (vec (muodosta-valimaiset-toimenpiderivit db [kohdevaruste] toimenpiteet)))
    []))

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

(defn hae-urakan-valimaiset-varustetoimenpiteet [db http-asetukset konteksti urakka-velho-oid alkuaika-parametri loppuaika-parametri tieosoite-parametri toimenpide]
  (let [toimenpide-rajaus (when toimenpide (tee-valimainen-toimenpide-parametri db toimenpide))
        payload {:asetukset {:tyyppi "kohdeluokkahaku"
                             :liitoshaku false}
                 :kohdeluokat ["toimenpiteet/valimaiset-varustetoimenpiteet"]
                 :lauseke (keep identity
                            ["ja"
                             (tee-muutoksen-lahde-oid-parametri urakka-velho-oid)
                             toimenpide-rajaus
                             tieosoite-parametri
                             alkuaika-parametri
                             loppuaika-parametri])}
        {vastaus-str :body} (integraatiotapahtuma/laheta konteksti :http http-asetukset (json/write-str payload))
        vastaus (:osumat (json/read-str vastaus-str :key-fn keyword))]
    vastaus))

(defn- hae-varusteet-oideilla [http-asetukset konteksti oidit kohdeluokat varustetyypit-parametri tieosoite-parametri kuntoluokat-parametri]
  (when (seq oidit)
    (let [payload {:asetukset {:tyyppi "kohdeluokkahaku"
                               :liitoshaku true}
                   :kohdeluokat (mapv (comp #(str/join "/" %) (juxt :nimiavaruus :kohdeluokka)) kohdeluokat)
                   :lauseke (keep identity
                              ["ja"
                               (tee-varusteen-oid-parametri oidit)
                               varustetyypit-parametri
                               tieosoite-parametri
                               kuntoluokat-parametri])}
          {vastaus-str :body} (integraatiotapahtuma/laheta konteksti :http http-asetukset (json/write-str payload))
          vastaus (:osumat (json/read-str vastaus-str :key-fn keyword))]
      vastaus)))

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


(defn hae-urakan-varustetoteumat [{:keys [integraatioloki db asetukset]}
          tiedot]
  (integraatiotapahtuma/suorita-integraatio db integraatioloki "velho" "varustetoteumien-haku" nil
    (fn [konteksti]
      (let [{:keys [urakka-id kohdeluokat varustetyypit kuntoluokat tie aosa aeta losa leta
        hoitovuoden-kuukausi hoitokauden-alkuvuosi toimenpide]}
      (normalisoi-hoitovuosirajaus tiedot)
      virheet (atom #{})
            {:keys [token-url
                    varuste-kayttajatunnus
                    varuste-salasana
                    varuste-api-juuri-url]} asetukset]
        (when-let [token (velho-yhteiset/hae-velho-token token-url varuste-kayttajatunnus varuste-salasana konteksti
                           (fn [x]
                             (swap! virheet conj (str "Virhe velho token haussa " x))
                             (log/error "Virhe velho token haussa" x)))]
          (let [otsikot (velho-yhteiset/velho-otsikot token)
                kohdeluokat (if (and (set? kohdeluokat) (seq kohdeluokat))
                              (filter #(kohdeluokat (:kohdeluokka %)) +tietolajien-lahteet+)
                              +tietolajien-lahteet+)
                http-asetukset {:metodi :POST
                                :otsikot otsikot
                                :url (str varuste-api-juuri-url velho-yhteiset/hakupalvelu-url)}
                urakka-velho-oid (q-urakat/hae-urakan-velho-oid db {:id urakka-id})
                _ (when-not urakka-velho-oid
                    (swap! virheet conj (str "Urakalle ei löytynyt vastaavaa Velho-oidia. Urakan id: " urakka-id))
                    (log/error "Urakalle ei löytynyt vastaavaa Velho-oidia. Urakan id: " urakka-id))

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

                aikavali (muodosta-aikavali hoitokauden-alkuvuosi hoitovuoden-kuukausi)

                alkuaika-parametri (when aikavali
                                     (tee-alkupvm-rajaus-fallbackilla "pvm-suurempi-kuin" (first aikavali)))

                loppuaika-parametri (when aikavali
                                      (tee-alkupvm-rajaus-fallbackilla "pvm-pienempi-kuin" (second aikavali)))

                valimaiset-toimenpiteet (hae-urakan-valimaiset-varustetoimenpiteet
                                          db
                                          http-asetukset
                                          konteksti
                                          urakka-velho-oid
                                          alkuaika-parametri
                                          loppuaika-parametri
                                          tieosoite-parametri
                                          toimenpide)
                toimenpiteella-suodatetut-valimaiset-oidit (vec (distinct (keep #(get-in % [:ominaisuudet :toimenpiteen-kohde]) valimaiset-toimenpiteet)))
                valimaisten-toimenpiteiden-kohdevarusteet (hae-varusteet-oideilla
                                                            http-asetukset
                                                            konteksti
                                                            toimenpiteella-suodatetut-valimaiset-oidit
                                                            kohdeluokat
                                                            varustetyypit-parametri
                                                            nil
                                                            kuntoluokat-parametri)
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
                tavalliset-varusterivit (mapv (partial varuste-velhosta->harja db) varusteet-vastaus)
                valimaiset-toimenpiderivit (vec (muodosta-valimaiset-toimenpiderivit
                                                 db
                                                 valimaisten-toimenpiteiden-kohdevarusteet
                                                 valimaiset-toimenpiteet))
                varusteet (sort-by :alkupvm
                            #(compare %2 %1)
                            (concat tavalliset-varusterivit valimaiset-toimenpiderivit))]
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
          (let [otsikot (velho-yhteiset/velho-otsikot token)
                lahde (hae-kohdeluokan-lahde kohdeluokka)]
            (when-not lahde
              (swap! virheet conj (str "Varusteen historian kohdeluokalle ei loytynyt Velho-lahdetta: " kohdeluokka))
              (log/error "Varusteen historian kohdeluokalle ei loytynyt Velho-lahdetta" {:kohdeluokka kohdeluokka}))
            (when lahde
              (let [hakupalvelun-http-asetukset {:metodi :POST
                                                 :otsikot otsikot
                                                 :url (str varuste-api-juuri-url velho-yhteiset/hakupalvelu-url)}
                    hakupalvelun-varusteet (hae-varusteen-historia-hakupalvelusta hakupalvelun-http-asetukset konteksti lahde ulkoinen-oid)
                    historiavarusteet hakupalvelun-varusteet
                    historiarivit (muodosta-varusteen-historian-rivit db (mapv #(taydenna-varusteen-kohdeluokka lahde %) historiavarusteet))
                    valimaiset-toimenpiderivit (hae-varusteen-valimaiset-toimenpiderivit
                                                 db
                                                 hakupalvelun-http-asetukset
                                                 konteksti
                                                 lahde
                                                 ulkoinen-oid
                                                 historiavarusteet)]
                (vec (jarjesta-varusteen-historiarivit (concat historiarivit valimaiset-toimenpiderivit)))))))))))

(defn hae-ja-tallenna-kohdeluokan-nimikkeisto [{:keys [db asetukset]} virheet hae-token-fn konteksti
                                               {:keys [kohdeluokka kohdeluokka->tyyppi-fn nimiavaruus]}
                                               hae-kuntoluokat? hae-varustetoimenpiteet?]
  (when-let [token (hae-token-fn)]
    (try+
      (let [{:keys [varuste-api-juuri-url]} asetukset
            otsikot (velho-yhteiset/velho-otsikot token)
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
      vastaus))) 
  
  ;; Tarvitsetko urakoita joista löytyy välimäisiä varusteita testaukseen - Hyödynnä näitä apu funktioita suoraan replissä
  (comment 
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
    
    (defn hae-urakat-valimaisilla-toimenpiteilla
      "Hakee urakat joissa on välimäisiä varustetoimenpiteitä.
             1. Hakee kaikki välimäiset toimenpiteet
             2. Poimii varuste-OID:t
             3. Hakee varusteet OID:eilla ja saa niiden muutoksen-lahde-oid:n (urakka-OID)
             4. Hakee urakkojen nimet
             
             Palauttaa listan urakkoja: [{:oid '...' :nimi '...'}]"
      [http-asetukset konteksti]
      (let [;; 1. Hae kaikki välimäiset toimenpiteet
            toimenpiteet (test-hae-kaikki-valimaiset-varuste-toimenpiteet http-asetukset konteksti)
            _ (log/info "DEBUG: Haettiin" (count toimenpiteet) "toimenpidettä")
            _ (when (seq toimenpiteet)
                (log/info "DEBUG: Ensimmäinen toimenpide:" (clojure.pprint/pprint (first toimenpiteet))))
    
            ;; 2. Poimii varuste-OID:t
            varuste-oidit (distinct (keep #(get-in % [:ominaisuudet :toimenpiteen-kohde]) toimenpiteet))
            _ (log/info "DEBUG: Löydettiin" (count varuste-oidit) "varustetta välimäisillä toimenpiteillä")
            _ (when (seq varuste-oidit)
                (log/info "DEBUG: Ensimmäinen varuste-OID:" (first varuste-oidit)))
    
            ;; 3. Hae varusteet ja niiden muutoksen-lahde-oid (urakka-OID)
            varusteet (when (seq varuste-oidit)
                        (test-hae-varusteet-oideille varuste-oidit http-asetukset konteksti))
            _ (log/info "DEBUG: Haettiin" (count varusteet) "varustetta")
            _ (when (seq varusteet)
                (log/info "DEBUG: Ensimmäinen varuste:" (pr-str (first varusteet))))
    
            ;; Suodatetaan vain maanteiden hoitourakan OID:t (alkavat 1.2.246.578.8.1.) - voi olla inventoiteja tai projekteja
            urakka-oidit (distinct (keep (fn [varuste]
                                           (let [oid (:muutoksen-lahde-oid varuste)]
                                             (when (and oid (str/starts-with? oid "1.2.246.578.8.1."))
                                               oid)))
                                         varusteet))
            _ (log/info "DEBUG: Löydettiin" (count urakka-oidit) "maanteiden hoitourakan OID:a") 

            _ (when (seq urakka-oidit)
                (log/info "DEBUG: Ensimmäinen urakka-OID:" (first urakka-oidit)))
            _ (log/info "DEBUG: oidit urakoille" urakka-oidit "urakkaa")] 
        urakka-oidit))
    
    (defn hae-urakat-valimaisilla-toimenpiteilla-wrapper []
      (let [varusteet-integraatio (:velho-integraatio harja.palvelin.main/harja-jarjestelma)]
        (integraatiotapahtuma/suorita-integraatio 
          (:db varusteet-integraatio)
          (:integraatioloki varusteet-integraatio)
          "velho"
          "urakoiden-haku"  ;; Käytetään olemassa olevaa nimeä!
          nil  ;; ulkoinen-id (voi olla nil)
          (fn [konteksti]  ;; tyonkulku-fn - saa kontekstin parametrina
            (let [asetukset (:asetukset varusteet-integraatio)
                  token (velho-yhteiset/hae-velho-token 
                          (:token-url asetukset)
                          (:varuste-kayttajatunnus asetukset)
                          (:varuste-salasana asetukset)
                          konteksti
                          (fn [x] (log/error "Token-virhe" x)))
                  http-asetukset {:metodi :POST
                                  :otsikot (velho-yhteiset/velho-otsikot token)
                                  :url (str (:varuste-api-juuri-url asetukset)
                                            velho-yhteiset/hakupalvelu-url)}]
              (hae-urakat-valimaisilla-toimenpiteilla http-asetukset konteksti))))))
    
    ;; Käytä hakua tästä - evaluoi kaikki muut funktiot ensin
    (def urakat (hae-urakat-valimaisilla-toimenpiteilla-wrapper))
    (clojure.pprint/pprint urakat))
  

