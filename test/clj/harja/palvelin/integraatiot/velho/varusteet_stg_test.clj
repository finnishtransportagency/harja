(ns harja.palvelin.integraatiot.velho.varusteet-stg-test
  (:require [clojure.data.json :as json]
            [clojure.test :refer :all]
            [com.stuartsierra.component :as component]
            [harja.palvelin.asetukset :as asetukset]
            [harja.palvelin.integraatiot.integraatiotapahtuma :as integraatiotapahtuma]
            [harja.palvelin.integraatiot.velho.varusteet :as varusteet]
            [harja.palvelin.integraatiot.velho.velho-komponentti :as velho-integraatio]
            [harja.palvelin.integraatiot.velho.yhteiset :as velho-yhteiset]
            [harja.testi :refer [jarjestelma laajenna-integraatiojarjestelmafixturea q-map u]]))

(def kayttaja "jvh")

(defn- stg-testit-paalla?
  []
  (contains? #{"1" "true" "TRUE" "yes" "YES"}
    (System/getenv "HARJA_AJA_VELHO_STG_TESTIT")))

(def stg-ohitettu-viesti
  "Aseta HARJA_AJA_VELHO_STG_TESTIT=true ajaaksesi nämä oikeaa Velho stg -rajapintaa käyttävät testit.")

(defn- envissa-maaritellyt-velho-asetukset []
  (let [asetukset {:token-url (System/getenv "HARJA_VELHO_TOKEN_URL")
                   :varuste-api-juuri-url (System/getenv "HARJA_VELHO_API_JUURI_URL")
                   :varuste-kayttajatunnus (System/getenv "HARJA_VELHO_KAYTTAJATUNNUS")
                   :varuste-salasana (System/getenv "HARJA_VELHO_SALASANA")}]
    (when (every? seq (vals asetukset))
      asetukset)))

(defn- lue-stg-velho-asetukset []
  (let [env-asetukset (envissa-maaritellyt-velho-asetukset)]
    (assoc
      (or env-asetukset
        (try
          (:velho (asetukset/lue-asetukset "asetukset.edn"))
          (catch Throwable e
            (throw (ex-info
                     "Velho stg -testit tarvitsevat joko HARJA_VELHO_* -ympäristömuuttujat tai toimivan asetukset.edn/:velho-konfiguraation."
                     {:syy (.getMessage e)}
                     e)))))
      :varuste-tuonti-suoritusaika nil
      :oid-tuonti-suoritusaika nil)))

(defn- stg-jarjestelma-fixture [testit]
  (if-not (stg-testit-paalla?)
    (testit)
    (let [perus-fixture
          (laajenna-integraatiojarjestelmafixturea
            kayttaja
            :velho-integraatio (component/using
                                 (velho-integraatio/->Velho (lue-stg-velho-asetukset))
                                 [:db :integraatioloki]))]
      (perus-fixture
        testit))))

(use-fixtures :once stg-jarjestelma-fixture)

(defn- stg-kontekstissa [integraation-nimi f]
  (integraatiotapahtuma/suorita-integraatio
    (:db jarjestelma)
    (:integraatioloki jarjestelma)
    "velho"
    integraation-nimi
    nil
    (fn [konteksti]
      (let [asetukset (get-in jarjestelma [:velho-integraatio :asetukset])
            token (velho-yhteiset/hae-velho-token
                    (:token-url asetukset)
                    (:varuste-kayttajatunnus asetukset)
                    (:varuste-salasana asetukset)
                    konteksti)
            http-asetukset {:metodi :POST
                            :otsikot (velho-yhteiset/velho-otsikot token)
                            :url (str (:varuste-api-juuri-url asetukset)
                                  velho-yhteiset/hakupalvelu-url)}]
        (f {:konteksti konteksti
            :http-asetukset http-asetukset})))))

(defonce loydetty-kandidaatti (atom nil))

(defn- turvallinen-oid? [oid]
  (boolean (and oid (re-matches #"[0-9.]+" oid))))

(defn- hae-valimaisten-varustetoimenpiteiden-raakadata [http-asetukset konteksti]
  (let [payload {:asetukset {:tyyppi "kohdeluokkahaku"
                             :liitoshaku false
                             :palautettavat-kentat [["yleiset/perustiedot" "oid"]
                                                    ["yleiset/perustiedot" "muutoksen-lahde-oid"]
                                                    ["toimenpiteet/valimaiset-varustetoimenpiteet" "ominaisuudet" "toimenpiteen-kohde"]]}
                 :kohdeluokat ["toimenpiteet/valimaiset-varustetoimenpiteet"]}
        {vastaus-str :body} (integraatiotapahtuma/laheta konteksti :http http-asetukset (json/write-str payload))]
    (:osumat (json/read-str vastaus-str :key-fn keyword))))

(defn- hae-paikallinen-urakka-oidilla [oid]
  (when (turvallinen-oid? oid)
    (first (q-map (str "SELECT id, nimi, velho_oid AS velho_oid FROM urakka WHERE velho_oid = '" oid "' LIMIT 1")))))

(defn- hae-vapaa-testiurakka []
  (first (q-map
           "SELECT id, nimi
              FROM urakka
             WHERE tyyppi = 'teiden-hoito'
               AND velho_oid IS NULL
               AND poistettu = FALSE
             ORDER BY id
             LIMIT 1")))

(defn- hae-testiurakka-oidille [oid]
  (when (turvallinen-oid? oid)
    (or (some-> (hae-paikallinen-urakka-oidilla oid)
          (assoc :alkuperainen-velho-oid oid))
      (when-let [{:keys [id nimi]} (hae-vapaa-testiurakka)]
        {:id id
         :nimi nimi
         :alkuperainen-velho-oid nil}))))

(defn- paivita-urakan-velho-oid! [urakka-id oid]
  (when (or (nil? oid) (turvallinen-oid? oid))
    (u (if oid
         (str "UPDATE urakka SET velho_oid = '" oid "' WHERE id = " urakka-id)
         (str "UPDATE urakka SET velho_oid = NULL WHERE id = " urakka-id)))))

(defn- hae-varusteet-oidilla-stgsta [http-asetukset konteksti oidit]
  (mapcat (fn [oidit-erassa]
            (or (#'varusteet/hae-varusteet-oideilla
                  http-asetukset
                  konteksti
                  oidit-erassa
                  varusteet/+tietolajien-lahteet+
                  nil
                  nil
                  nil)
              []))
    (partition-all 200 oidit)))

(defn- hae-valimaisiin-toimenpiteisiin-sopiva-kandidaatti! []
  (or @loydetty-kandidaatti
    (let [kandidaatti
          (stg-kontekstissa
            "varustetoteumien-haku"
            (fn [{:keys [konteksti http-asetukset]}]
              (let [valimaiset-toimenpiteet (hae-valimaisten-varustetoimenpiteiden-raakadata http-asetukset konteksti)
                    kohde-oidit (vec (distinct (keep #(get-in % [:ominaisuudet :toimenpiteen-kohde])
                                                valimaiset-toimenpiteet)))
                    kohdevarusteet (hae-varusteet-oidilla-stgsta http-asetukset konteksti kohde-oidit)
                    varusteet-oidilla (into {} (map (juxt :oid identity) kohdevarusteet))]
                (first
                  (keep
                    (fn [toimenpide]
                      (let [urakka-velho-oid (:muutoksen-lahde-oid toimenpide)
                            projektivaruste (get varusteet-oidilla (get-in toimenpide [:ominaisuudet :toimenpiteen-kohde]))
                            testiurakka (when (and urakka-velho-oid
                                               projektivaruste
                                               (not= urakka-velho-oid (:muutoksen-lahde-oid projektivaruste)))
                                         (hae-testiurakka-oidille urakka-velho-oid))]
                        (when testiurakka
                          {:urakka-id (:id testiurakka)
                           :urakka-nimi (:nimi testiurakka)
                           :alkuperainen-urakan-velho-oid (:alkuperainen-velho-oid testiurakka)
                           :urakka-velho-oid urakka-velho-oid
                           :projektivaruste-oid (:oid projektivaruste)
                           :projektivarusteen-kohdeluokka (:kohdeluokka projektivaruste)
                           :projektivarusteen-muutoksen-lahde-oid (:muutoksen-lahde-oid projektivaruste)
                           :valimainen-toimenpide-oid (:oid toimenpide)})))
                    valimaiset-toimenpiteet)))))]
      (reset! loydetty-kandidaatti kandidaatti)
      kandidaatti)))

(deftest stg-varustehaku-loytaa-projektivarusteen-urakan-valimaisella-toimenpiteella
  (if-not (stg-testit-paalla?)
    (is true stg-ohitettu-viesti)
    (let [{:keys [urakka-id
                  urakka-nimi
                  alkuperainen-urakan-velho-oid
                  urakka-velho-oid
                  projektivaruste-oid
                  projektivarusteen-muutoksen-lahde-oid] :as kandidaatti}
          (hae-valimaisiin-toimenpiteisiin-sopiva-kandidaatti!)
          vastaus (when kandidaatti
                    (try
                      (paivita-urakan-velho-oid! urakka-id urakka-velho-oid)
                      (varusteet/hae-urakan-varustetoteumat
                        (:velho-integraatio jarjestelma)
                        {:urakka-id urakka-id
                         :hoitokauden-alkuvuosi nil})
                      (finally
                        (paivita-urakan-velho-oid! urakka-id alkuperainen-urakan-velho-oid))))
          projektivaruste (some #(when (= projektivaruste-oid (:ulkoinen-oid %)) %)
                           (:toteumat vastaus))]
      (is (some? kandidaatti)
        "Stg:stä pitää löytyä ainakin yksi urakka, jossa urakan välimäinen varustetoimenpide kohdistuu eri muutoksen-lähde-oidiin kirjattuun varusteeseen")
      (is (not= urakka-velho-oid projektivarusteen-muutoksen-lahde-oid)
        (str "Kandidaatin " urakka-nimi " kohdevarusteen pitää olla eri muutoksen-lähde-oidiin kirjattu kuin urakan oma OID"))
      (is (some? projektivaruste)
        (str "Varustehaku ei palauttanut projektivarustetta OID:lla " projektivaruste-oid
          " vaikka stg-raakadatassa urakan välimäinen toimenpide kohdistuu siihen"))
      (is (= projektivarusteen-muutoksen-lahde-oid
            (:muutoksen-lahde-oid projektivaruste))
        "Palautetun projektivarusteen pitää välittää backendista sama muutoksen-lähde-oid kuin stg-raakadata näyttää")
      (is (string? (:toimenpide projektivaruste))
        "Palautetulla projektivarusteella pitää olla toimenpidekenttä merkkijonona"))))