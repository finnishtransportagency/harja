(ns harja.palvelin.palvelut.lupaus.lupaus-palvelu
  (:require
   [cheshire.core :as cheshire]
   [clojure.java.jdbc :as jdbc]
   [clojure.set :as set]
   [clojure.string :as str]
   [com.stuartsierra.component :as component]
   [harja.domain.kulut.kustannusten-seuranta :as kustannusten-seuranta]
   [harja.domain.lupaus-domain :as lupaus-domain]
   [harja.domain.lupaus.kustannusennuste-domain :as kustannusennuste-domain]
   [harja.domain.oikeudet :as oikeudet]
   [harja.domain.roolit :as roolit]
   [harja.id :refer [id-olemassa?]]
   [harja.kyselyt
             [lupaus-kyselyt :as lupaus-kyselyt]
             [urakat :as urakat-q]
             [budjettisuunnittelu :as budjetti-q]
             [valikatselmus :as valikatselmus-q]
             [paatos-kyselyt :as paatos-kyselyt]]
   [harja.kyselyt.lupaus.kustannusennuste-kyselyt :as kustannusennuste-kyselyt]
   [harja.kyselyt.kommentit :as kommentit]
   [harja.kyselyt.konversio :as konversio]
   [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu
                                                     poista-palvelut]]
   [harja.palvelin.palvelut.kulut.kustannusten-seuranta :as kustannusten-seuranta-palvelu]
   [harja.pvm :as pvm]
   [taoensso.timbre :as log]))

(defn- sitoutumistiedot [lupausrivit]
  {:pisteet (:sitoutuminen-pisteet (first lupausrivit))
   :id (:sitoutuminen-id (first lupausrivit))})

(defn- liita-lupausryhmien-pisteet [lupausrivit]
  (let [ryhmat (group-by :lupausryhma-id lupausrivit)]
    (into {}
          (map (fn [[avain rivit]]
                 (let [pisteet (reduce + 0 (map :pisteet rivit))
                       kyselypisteet (reduce + 0 (map :kyselypisteet rivit))]
                   {avain {:pisteet pisteet
                           :kyselypisteet kyselypisteet
                           :pisteet-max (+ pisteet kyselypisteet)
                           :pisteet-ennuste (lupaus-domain/rivit->ennuste rivit)
                           :pisteet-toteuma (lupaus-domain/rivit->toteuma rivit)
                           :odottaa-kannanottoa (lupaus-domain/lupaus->odottaa-kannanottoa rivit)
                           :merkitsevat-odottaa-kannanottoa (lupaus-domain/lupaus->merkitseva-odottaa-kannanottoa rivit)}}))
               ryhmat))))

(defn- lupausryhman-max-pisteet [max-pisteet ryhma-id]
  (get max-pisteet ryhma-id))

(defn- lupausryhman-tiedot [lupausrivit]
  (let [ryhma-id->lupaukset (group-by :lupausryhma-id lupausrivit)
        ryhmat (map first (vals ryhma-id->lupaukset))
        lupausryhman-pisteet (liita-lupausryhmien-pisteet lupausrivit)]
    (->> ryhmat
         (map #(select-keys % [:lupausryhma-id :lupausryhma-otsikko
                               :lupausryhma-jarjestys :lupausryhma-alkuvuosi]))
         (map #(assoc % :lupaukset (get ryhma-id->lupaukset (:lupausryhma-id %))))
         (map #(set/rename-keys % {:lupausryhma-id :id
                                   :lupausryhma-otsikko :otsikko
                                   :lupausryhma-jarjestys :jarjestys
                                   :lupausryhma-alkuvuosi :alkuvuosi}))
         (map #(assoc % :kirjain (lupaus-domain/numero->kirjain (:jarjestys %))))
         (map #(merge % (lupausryhman-max-pisteet lupausryhman-pisteet (:id %)))))))

(def db-vastaus->speqcl-avaimet
  {:f1 :id
   :f2 :kuukausi
   :f3 :vuosi
   :f4 :vastaus
   :f5 :lupaus-vaihtoehto-id
   :f6 :pisteet
   :f7 :veto-oikeutta-kaytetty
   :f8 :veto-oikeus-aika
   :f9 :paatos})

(defn maarita-urakan-tavoitehinta
  "Urakalle voidaan budjetoida tavoitehinta hoitokausittain. Päätellään siis hoitokauden järjestysnumero ja tarkistetaan urakka_tavoite taulusta,
  että mikä on kulloisenkin hoitokauden tavoitehinta."
  [db urakka-id hk-alkupvm]
  (let [;; Jotta voidaan päätellä hoitokauden numero, joudutaan hakemaan urakan tietoja
        urakan-tiedot (first (urakat-q/hae-urakka db {:id urakka-id}))
        valitun-hoitokauden-alkuvuosi (pvm/vuosi hk-alkupvm)
        kuluva-hoitokausi-nro (pvm/hoitokausivuosi->mhu-hoitovuosi-nro (:alkupvm urakan-tiedot) valitun-hoitokauden-alkuvuosi)
        budjetit (budjetti-q/hae-budjettitavoite db {:urakka urakka-id})
        valitun-hoitokauden-budjetti (first (filterv (fn [b]
                                                       (= (:hoitokausi b) kuluva-hoitokausi-nro))
                                                     budjetit))
        tavoitehinta (when valitun-hoitokauden-budjetti (:tarjous-tavoitehinta valitun-hoitokauden-budjetti))]
    tavoitehinta))

(defn- lupauksen-vastausvaihtoehdot [db {:keys [lupaus-id lupaustyyppi]}]
  (when-not (= lupaustyyppi "yksittainen")
    (lupaus-kyselyt/hae-lupaus-vaihtoehdot db {:lupaus-id lupaus-id})))

(defn- liita-lupaus-vaihtoehdot [db lupaus]
  (assoc lupaus :vaihtoehdot (lupauksen-vastausvaihtoehdot db lupaus)))

(defn hae-lupauspaatos
  "Haetaan lupaukseen liittyvä päätös hoitokaudelle"
  [db urakka-id hoitokauden-alkuvuosi]
  (let [lupauspaatos (first (paatos-kyselyt/hae-lupauspaatokset db {:urakkaid urakka-id
                                                                    :hoitokauden_alkuvuosi hoitokauden-alkuvuosi}))]
    lupauspaatos))

(defn valikatselmus-tehty-urakalle?
"Onko urakalle tehty välikatselmus minä tahansa hoitokautena."
  [db urakka-id hoitokauden-alkuvuosi]
  {:pre [(number? urakka-id)]}
  (let [lupauspaatos (first (paatos-kyselyt/hae-lupauspaatokset db {:urakkaid urakka-id :hoitokauden_alkuvuosi hoitokauden-alkuvuosi}))]
    (boolean lupauspaatos)))

(defn laske-maarapaiva-tiedot
  [db lupaukset urakka-id urakan-tiedot hoitokauden-alkuvuosi nykyhetki]
  {:pre [(some? db)
         (coll? lupaukset)
         (number? urakka-id)
         (map? urakan-tiedot)
         (number? hoitokauden-alkuvuosi)
         (inst? nykyhetki)]}
  (try
    (let [kustannusennuste-lupaus (first (filter #(= (:lupaustyyppi %) "kustannusennuste") lupaukset))]
      (if-not kustannusennuste-lupaus
        (do
          ;; Varoita vain, jos kustannusennusten-lupaus vaaditaan urakalle. Ja se vaaditaan vasta -25 ja sen jälkeen
          (when (>= (pvm/vuosi (:alkupvm urakan-tiedot)) 2025)
            (log/warn "Kustannusennustelupausta ei löytynyt urakalle" urakka-id))
          {})
        (let [maarapaivat (kustannusennuste-kyselyt/hae-kustannusennuste-maarapaivat
                            db {:lupaus-id (:lupaus-id kustannusennuste-lupaus)
                                :hoitokauden-alkuvuosi hoitokauden-alkuvuosi})]

          (into {}
            (map (fn [{:keys [kuukausi maarapaiva_pvm]}]
                   (let [maarapaiva-mennyt-ohi? (pvm/jalkeen? nykyhetki maarapaiva_pvm)]
                     [kuukausi {:maarapaiva-mennyt-ohi? maarapaiva-mennyt-ohi?
                                :maarapaiva-pvm maarapaiva_pvm}]))
              maarapaivat)))))
    (catch Exception e
      (log/error e "Virhe määräpäivätietojen haussa urakalle" urakka-id)
      {})))

(defn- hae-lupaus-kustannukset-jarjestettyna [db urakkaid hoitovuosi hoitokauden-alkupvm hoitokauden-loppupvm]
  (let [kustannukset (kustannusten-seuranta-palvelu/hae-urakan-kustannusten-seuranta-paaryhmittain-ilman-validointia
                       db {:urakka-id urakkaid
                           :hoitokauden-alkuvuosi hoitovuosi
                           :alkupvm hoitokauden-alkupvm
                           :loppupvm hoitokauden-loppupvm})
        urakan-sopimustyyppi (:sopimustyyppi (first (urakat-q/hae-urakan-tiedot db urakkaid)))
        kustannukset-jarjestettyna (kustannusten-seuranta/jarjesta-tehtavat kustannukset urakan-sopimustyyppi)]
    kustannukset-jarjestettyna))

(defn- hae-perustiedot
  "Hakee urakan ja hoitokauden perustiedot.

   Palauttaa mapin joka sisältää:
   - :urakan-tiedot - Urakan täydelliset tiedot
   - :urakan-alkuvuosi - Urakan alkuvuosi
   - :urakan-alkupvm - Urakan alkupäivämäärä
   - :hoitokauden-alkuvuosi - Valitun hoitokauden alkuvuosi
   - :hoitovuosi-nro - Hoitovuoden järjestysnumero (1, 2, 3...)
   - :hk-alkupvm - Hoitokauden alkupäivämäärä
   - :hk-loppupvm - Hoitokauden loppupäivämäärä"
  [db urakka-id valittu-hoitokausi]
  {:pre [(number? urakka-id) (vector? valittu-hoitokausi) (= 2 (count valittu-hoitokausi))]}
  (let [[hk-alkupvm hk-loppupvm] valittu-hoitokausi
        urakan-tiedot (first (urakat-q/hae-urakka db {:id urakka-id}))
        urakan-alkupvm (:alkupvm urakan-tiedot)
        urakan-alkuvuosi (pvm/vuosi urakan-alkupvm)
        hoitokauden-alkuvuosi (pvm/vuosi hk-alkupvm)
        hoitovuosi-nro (pvm/hoitokausivuosi->mhu-hoitovuosi-nro urakan-alkupvm hoitokauden-alkuvuosi)]
    {:urakan-tiedot urakan-tiedot
     :urakan-alkupvm urakan-alkupvm
     :urakan-alkuvuosi urakan-alkuvuosi
     :hoitokauden-alkuvuosi hoitokauden-alkuvuosi
     :hoitovuosi-nro hoitovuosi-nro
     :hk-alkupvm hk-alkupvm
     :hk-loppupvm hk-loppupvm
     :valittu-hoitokausi valittu-hoitokausi}))

(defn- rikasta-lupaus-lisatiedoilla
  "Rikastaa lupauksen tyyppi-spesifisillä lisätiedoilla.

   Kustannusennuste-lupaukset rikastavat kustannusennuste-datalla ja tiloinformaatiolla.
   Muut lupaustyyppit (yksittäinen, monivalinta, kysely) palauttavat sellaisenaan."
  [lupaus db urakka-id hoitokauden-alkuvuosi urakan-alkuvuosi hoitovuosi-nro]
  (if (= "kustannusennuste" (:lupaustyyppi lupaus))
    (let [kustannusennusteet (when (and db (:lupaus-id lupaus) urakka-id hoitokauden-alkuvuosi)
                               (kustannusennuste-kyselyt/hae-lupauksen-kustannusennusteet
                                 db (:lupaus-id lupaus) urakka-id hoitokauden-alkuvuosi))
          pisteet-tila (when (and db urakka-id hoitokauden-alkuvuosi)
                         (kustannusennuste-kyselyt/onko-kustannusennuste-pisteet-laskettu?
                           db urakka-id hoitokauden-alkuvuosi))
          lopputilanne (when (and db urakka-id hoitokauden-alkuvuosi)
                         (first (lupaus-kyselyt/hae-hoitovuoden-lopputilanne
                                  db {:urakka-id urakka-id
                                      :hoitovuosi-alkuvuosi hoitokauden-alkuvuosi})))]
      (assoc lupaus
        :kustannusennusteet kustannusennusteet
        :hoitovuosi-paattynyt? (if pisteet-tila
                                 (:kaikki-laskettu pisteet-tila)
                                 false)
        :urakan-alkuvuosi urakan-alkuvuosi
        :hoitovuosi-nro hoitovuosi-nro
        :lopputilanne lopputilanne))
    lupaus))

(defn ylikirjoita-hoitovuosikohtaiset-arvot
  "Ylikirjoittaa lupauksen oletusarvot hoitovuosikohtaisilla arvoilla, jos niitä on määritelty.

   Hakee lupaus_hoitovuoden_kirjauskuukaudet-taulusta hoitovuosikohtaiset arvot ja korvaa:
   - kirjaus-kkt: aina
   - paatos-kk: jos ei nil
   - joustovara-kkta: jos ei nil

   Arvot ylikirjoitetaan suoraan lupaus-mappiin, jolloin domain-funktiot saavat
   ne automaattisesti fallback-logiikan kautta."
  [db vastaus hoitovuosi-nro]
  {:pre [(coll? vastaus) (or (nil? hoitovuosi-nro) (number? hoitovuosi-nro))]}
  (let [lupaus-idt (mapv :lupaus-id vastaus)
        hoitovuoden-arvot (when (seq lupaus-idt)
                            (->> lupaus-idt
                              (map #(lupaus-kyselyt/hae-lupauksen-hoitovuoden-kirjauskuukaudet
                                     db {:lupaus-id %
                                         :hoitovuosi-nro hoitovuosi-nro}))
                              (filter seq)
                              (map first)
                              (group-by :lupaus-id)))]
    (mapv (fn [r]
            (if-let [hv-arvot (first (get hoitovuoden-arvot (:lupaus-id r)))]
              (cond-> (assoc r :kirjaus-kkt (:kirjaus-kkt hv-arvot))
                (:paatos-kk hv-arvot) (assoc :paatos-kk (:paatos-kk hv-arvot))
                (:joustovara-kkta hv-arvot) (assoc :joustovara-kkta (:joustovara-kkta hv-arvot)))
              r))
      vastaus)))

(defn- prosessoi-lupausvastaukset
  "Prosessoi lupausvastaukset: konvertoi JSON, rikastaa domain-logiikalla.

  Parametrit:
  - vastaus: Lupausvastaukset (arvot on jo ylikirjoitettu hoitovuosikohtaisilla arvoilla)
  - maarapaiva-tiedot: Map lupaus-id -> määräpäivätiedot
  - opts: {:db, :urakka-id, :urakan-alkuvuosi, :hoitokauden-alkuvuosi,
           :hoitovuosi-nro, :valittu-hoitokausi, :nykyhetki}

  Palauttaa prosessoidun lupausvastausvektorin."
  [vastaus maarapaiva-tiedot opts]
  {:pre [(coll? vastaus)
         (map? maarapaiva-tiedot)
         (map? opts)
         (some? (:db opts))
         (number? (:urakka-id opts))
         (number? (:urakan-alkuvuosi opts))
         (number? (:hoitokauden-alkuvuosi opts))
         (or (nil? (:hoitovuosi-nro opts)) (number? (:hoitovuosi-nro opts)))
         (vector? (:valittu-hoitokausi opts))
         (some? (:nykyhetki opts))]}
  (let [{:keys [db urakka-id urakan-alkuvuosi hoitokauden-alkuvuosi
                hoitovuosi-nro valittu-hoitokausi nykyhetki]} opts]
    (->> vastaus
      (mapv #(update % :vastaukset konversio/jsonb->clojuremap))
      (mapv #(update % :vastaukset
               (fn [rivit]
                 (let [tulos (keep
                               (fn [r]
                                 ;; Haku käyttää hakemisessa left joinia, joten on mahdollista, että taulusta
                                 ;; löytyy nil id
                                 (when (not (nil? (:f1 r)))
                                   (clojure.set/rename-keys r db-vastaus->speqcl-avaimet)))
                               rivit)]
                   tulos))))
      (mapv #(lupaus-domain/liita-odottaa-kannanottoa % nykyhetki valittu-hoitokausi))
      (mapv #(rikasta-lupaus-lisatiedoilla % db urakka-id hoitokauden-alkuvuosi urakan-alkuvuosi hoitovuosi-nro))
      (mapv #(let [lisatiedot (:kustannusennusteet %)]
               (lupaus-domain/liita-lupaus-kuukaudet % nykyhetki valittu-hoitokausi hoitovuosi-nro
                 nil lisatiedot maarapaiva-tiedot)))
      (mapv #(liita-lupaus-vaihtoehdot db %))
      (mapv lupaus-domain/liita-ennuste-tai-toteuma))))

(defn- hae-talouslaskelmat
  "Hakee talouslaskelmat lupausten näkymään.

  Parametrit:
  - db: Tietokantayhteys
  - urakka-id: Urakan ID
  - hk-alkupvm: Hoitokauden alkupäivä (voi olla nil)
  - hk-loppupvm: Hoitokauden loppupäivä (voi olla nil)
  - hoitokauden-alkuvuosi: Hoitokauden alkuvuosi
  - lupaus-sitoutuminen: Lupauksen sitoutumistiedot (sisältää :pisteet)

  Palauttaa mapin jossa:
  - :tavoitehinta - Urakan tavoitehinta
  - :oikaistu-tavoitehinta - Oikaistu tavoitehinta välikatselmuksesta
  - :oikaistu-toteutuneet-kustannukset - Oikaistu toteutuneet kustannukset
  - :tavoitehinta-puuttuu? - Boolean: true jos tavoitehinta puuttuu tai on 0
  - :luvatut-pisteet-puuttuu? - Boolean: true jos luvatut pisteet puuttuvat"
  [db urakka-id hk-alkupvm hk-loppupvm hoitokauden-alkuvuosi lupaus-sitoutuminen]
  {:pre [(some? db)
         (number? urakka-id)
         (or (nil? hk-alkupvm) (inst? hk-alkupvm))
         (or (nil? hk-loppupvm) (inst? hk-loppupvm))
         (number? hoitokauden-alkuvuosi)
         (map? lupaus-sitoutuminen)]}
  (let [tavoitehinta (when hk-alkupvm (maarita-urakan-tavoitehinta db urakka-id hk-alkupvm))
        ;; Hae oikaistu tavoitehinta välikatselmuksesta vertailua varten
        oikaistu-tavoitehinta-data (try
                                     (when hk-alkupvm
                                       (valikatselmus-q/hae-oikaistu-tavoitehinta
                                         db {:urakka-id urakka-id
                                             :hoitokauden-alkuvuosi hoitokauden-alkuvuosi}))
                                     (catch Exception e
                                       (log/warn "Oikaistun tavoitehinnan haku epäonnistui:" (.getMessage e))
                                       nil))
        kustannukset-jarjestettyna (try
                                     (when hk-alkupvm
                                       (hae-lupaus-kustannukset-jarjestettyna
                                         db urakka-id hoitokauden-alkuvuosi hk-alkupvm hk-loppupvm))
                                     (catch Exception e
                                       (log/warn "Kustannusten haku epäonnistui:" (.getMessage e))
                                       nil))
        oikaistu-tavoitehinta oikaistu-tavoitehinta-data
        oikaistu-toteutuneet-kustannukset (get-in kustannukset-jarjestettyna [:yhteensa :yht-toteutunut-summa])
        tavoitehinta-puuttuu? (not (and tavoitehinta (pos? tavoitehinta)))
        luvatut-pisteet-puuttuu? (not (:pisteet lupaus-sitoutuminen))]
    {:tavoitehinta tavoitehinta
     :oikaistu-tavoitehinta oikaistu-tavoitehinta
     :oikaistu-toteutuneet-kustannukset oikaistu-toteutuneet-kustannukset
     :tavoitehinta-puuttuu? tavoitehinta-puuttuu?
     :luvatut-pisteet-puuttuu? luvatut-pisteet-puuttuu?}))

(defn- muodosta-yhteenveto
  "Muodostaa yhteenvedon lupausten näkymään.

  Parametrit (options-map):
  - piste-maksimi: Maksimipisteet
  - piste-ennuste: Ennustepisteet
  - piste-toteuma: Toteumapisteet
  - bonus-tai-sanktio: Bonuksen tai sanktion määrä
  - tavoitehinta: Urakan tavoitehinta
  - oikaistu-tavoitehinta: Oikaistu tavoitehinta välikatselmuksesta
  - oikaistu-toteutuneet-kustannukset: Oikaistu toteutuneet kustannukset
  - kustannusennuste-pisteet-tila: Onko kustannusennuste-pisteet laskettu?
  - odottaa-kannanottoa: Lupausten määrä jotka odottavat kannanottoa
  - merkitsevat-odottaa-kannanottoa: Merkitsevien lupausten määrä jotka odottavat kannanottoa
  - odottaa-urakoitsijan-kannanottoa?: Boolean: true jos urakoitsija on antamatta kannanottoa
  - valikatselmus-tehty?: Boolean: onko välikatselmus tehty
  - tavoitehinta-puuttuu?: Boolean: puuttuuko tavoitehinta
  - lupausprosentit-puuttuu?: Boolean: puuttuuko bonus- tai sanktioprosentti urakan parametreista
  - luvatut-pisteet-puuttuu?: Boolean: puuttuuko luvatut pisteet
  - ennusteen-tila: Ennusteen tila (:katselmoitu-toteuma, :alustava-toteuma, :ennuste, :ei-viela-ennustetta)
  - tallennettu-paatos: Tallennettu päätös (voi olla nil)

  Palauttaa yhteenvedon mapin jossa:
  - :ennusteen-tila - Ennusteen tila
  - :pisteet - Map jossa :maksimi, :ennuste, :toteuma
  - :bonus-tai-sanktio - Bonuksen tai sanktion määrä
  - :tavoitehinta - Tavoitehinta (päätöksestä tai alkuperäinen)
  - :oikaistu-tavoitehinta - Oikaistu tavoitehinta
  - :oikaistu-toteutuneet-kustannukset - Oikaistu toteutuneet kustannukset
  - :kustannusennuste-pisteet-laskettu - Onko kustannusennuste-pisteet laskettu?
  - :odottaa-kannanottoa - Odottavien lupausten määrä
  - :merkitsevat-odottaa-kannanottoa - Merkitsevien odottavien lupausten määrä
  - :odottaa-urakoitsijan-kannanottoa? - Boolean
  - :valikatselmus-tehty-urakalle? - Boolean
  - :tavoitehinta-puuttuu? - Boolean
  - :lupausprosentit-puuttuu? - Boolean
  - :luvatut-pisteet-puuttuu? - Boolean"
  [{:keys [piste-maksimi piste-ennuste piste-toteuma
           bonus-tai-sanktio tavoitehinta
           oikaistu-tavoitehinta oikaistu-toteutuneet-kustannukset
           kustannusennuste-pisteet-tila
           odottaa-kannanottoa merkitsevat-odottaa-kannanottoa
           odottaa-urakoitsijan-kannanottoa?
           valikatselmus-tehty?
           tavoitehinta-puuttuu? lupausprosentit-puuttuu? luvatut-pisteet-puuttuu?
           ennusteen-tila tallennettu-paatos]}]
  {:pre [(keyword? ennusteen-tila)]}
  {:ennusteen-tila ennusteen-tila
   :pisteet {:maksimi piste-maksimi
             :ennuste piste-ennuste
             :toteuma (or
                        ;; Näytetään päätökseen tallennetut pisteet, jos saatavilla
                        (:toteutuneet_pisteet tallennettu-paatos)
                        piste-toteuma)}
   :bonus-tai-sanktio bonus-tai-sanktio
   :tavoitehinta (or
                   ;; Näytetään päätökseen tallennettu tavoitehinta, jos saatavilla
                   (:tavoitehinta tallennettu-paatos)
                   tavoitehinta)
   ;; Lisätään oikaistu tavoitehinta välikatselmuksesta
   :oikaistu-tavoitehinta oikaistu-tavoitehinta
   :oikaistu-toteutuneet-kustannukset oikaistu-toteutuneet-kustannukset
   :kustannusennuste-pisteet-laskettu kustannusennuste-pisteet-tila
   :odottaa-kannanottoa odottaa-kannanottoa
   :merkitsevat-odottaa-kannanottoa merkitsevat-odottaa-kannanottoa
   :odottaa-urakoitsijan-kannanottoa? odottaa-urakoitsijan-kannanottoa?
   :valikatselmus-tehty-urakalle? valikatselmus-tehty?
   :tavoitehinta-puuttuu? tavoitehinta-puuttuu?
  :lupausprosentit-puuttuu? lupausprosentit-puuttuu?
   :luvatut-pisteet-puuttuu? luvatut-pisteet-puuttuu?})

(defn- yhteinen-paatos->bonus-tai-sanktio
  "Muuntaa yhteisen päätöslaskennan tuloksen palvelun API-muotoon.
  
  Yhteinen muoto (välikatselmuksen logiikka):
  - {:lupausbonus <positiivinen>} -> {:bonus <positiivinen>}
  - {:lupaussanktio <positiivinen>} -> {:sanktio <positiivinen>}
  - {:tavoite-taytetty true} -> {:tavoite-taytetty true}
  
  Palvelun API-muoto:
  - {:bonus <positiivinen>} kun bonus
  - {:sanktio <positiivinen>} kun sanktio
  - {:tavoite-taytetty true} kun tavoite täytetty"
  [yhteinen-paatos]
  (cond
    (:lupausbonus yhteinen-paatos)
    {:bonus (:lupausbonus yhteinen-paatos)}
    
    (:lupaussanktio yhteinen-paatos)
    {:sanktio (:lupaussanktio yhteinen-paatos)}
    
    (:tavoite-taytetty yhteinen-paatos)
    {:tavoite-taytetty true}
    
    :else
    nil))

(defn- laske-bonus-ja-ennuste
  "Laskee bonuksen/sanktion ja ennusteen tilan.

  Parametrit (options-map):
  - db: Tietokantayhteys
  - urakka-id: Urakan ID
  - tallennettu-paatos: Tallennettu lupaus-päätös (voi olla nil)
  - piste-toteuma: Toteutuneet pisteet (voi olla nil)
  - piste-ennuste: Ennustepisteet
  - lupaus-sitoutuminen: Lupauksen sitoutumistiedot (sisältää :pisteet)
  - tavoitehinta: Urakan tavoitehinta
  - nykyhetki: Nykyinen aika
  - hk-alkupvm: Hoitokauden alkupäivä

  Palauttaa mapin jossa:
  - :bonus-tai-sanktio - Bonuksen tai sanktion määrä (API-muodossa: :bonus/:sanktio/:tavoite-taytetty)
  - :ennusteen-tila - Ennusteen tila (:katselmoitu-toteuma, :alustava-toteuma, :ennuste, :ei-viela-ennustetta)
  - :lupausprosentit-puuttuu? - Boolean: true jos bonus- tai sanktioprosentti puuttuu urakan parametreista"
  [{:keys [db urakka-id tallennettu-paatos piste-toteuma piste-ennuste
           lupaus-sitoutuminen tavoitehinta nykyhetki hk-alkupvm]}]
  {:pre [(some? db)
         (number? urakka-id)
         (inst? nykyhetki)
         (or (nil? hk-alkupvm) (inst? hk-alkupvm))
         (map? lupaus-sitoutuminen)]}
  (let [;; Jos päätös on jo tallennettu, käytä sitä (päätös on jo API-muodossa)
        tallennettu-bonus-tai-sanktio (some-> tallennettu-paatos lupaus-domain/paatos->bonus-tai-sanktio)
        urakan-parametrit (when-not tallennettu-bonus-tai-sanktio
                            (first (urakat-q/hae-urakan-parametrit db {:urakkaid urakka-id})))
        sanktioprosentti (:lupauspaatoksen_sanktioprosentti urakan-parametrit)
        bonusprosentti (:lupauspaatoksen_bonusprosentti urakan-parametrit)
        lupausprosentit-puuttuu? (and (nil? tallennettu-bonus-tai-sanktio)
                                      (or (nil? sanktioprosentti)
                                          (nil? bonusprosentti)))
        
        ;; Laske ennuste/toteuma kanonisella funktiolla
        bonus-tai-sanktio (if tallennettu-bonus-tai-sanktio
                            tallennettu-bonus-tai-sanktio
                            (when (and sanktioprosentti bonusprosentti)
                              ;; Laske yhteinen päätös ja muunna API-muotoon
                              (some-> (lupaus-domain/laske-lupauspaatos-bonus-tai-sanktio
                                        {:toteutuneet-pisteet (or piste-toteuma piste-ennuste)
                                         :luvatut-pisteet (:pisteet lupaus-sitoutuminen)
                                         :tavoitehinta tavoitehinta
                                         :sanktioprosentti sanktioprosentti
                                         :bonusprosentti bonusprosentti})
                                yhteinen-paatos->bonus-tai-sanktio)))
        
        ;; Ennuste voidaan tehdä, jos hoitokauden alkupäivä on menneisyydessä ja bonus-tai-sanktio != nil
        ennusteen-voi-tehda? (and (pvm/sama-tai-jalkeen? nykyhetki hk-alkupvm)
                               bonus-tai-sanktio)
        hoitovuosi-valmis? (boolean piste-toteuma)
        ennusteen-tila (cond tallennettu-bonus-tai-sanktio
                         :katselmoitu-toteuma

                         (and hoitovuosi-valmis? bonus-tai-sanktio)
                         :alustava-toteuma

                         ennusteen-voi-tehda?
                         :ennuste

                         :else
                         :ei-viela-ennustetta)]
    {:bonus-tai-sanktio bonus-tai-sanktio
      :ennusteen-tila ennusteen-tila
      :lupausprosentit-puuttuu? lupausprosentit-puuttuu?}))

(defn hae-urakan-lupaustiedot-hoitokaudelle [db {:keys [urakka-id nykyhetki
                                                        valittu-hoitokausi] :as tiedot}]
  ;; Hae perustiedot - eliminoi duplikaatit
  (let [perustiedot (hae-perustiedot db urakka-id valittu-hoitokausi)
        {:keys [urakan-tiedot urakan-alkuvuosi hoitokauden-alkuvuosi
                hoitovuosi-nro hk-alkupvm hk-loppupvm valittu-hoitokausi]} perustiedot

        kustannusennuste-pisteet-tila (kustannusennuste-kyselyt/onko-kustannusennuste-pisteet-laskettu?
                                        db urakka-id hoitokauden-alkuvuosi)
        vastaus (into []
                  (lupaus-kyselyt/hae-urakan-lupaustiedot db {:urakka urakka-id
                                                              :alkupvm hk-alkupvm
                                                              :urakan-alkuvuosi urakan-alkuvuosi
                                                              :loppupvm hk-loppupvm}))
        maarapaiva-tiedot (laske-maarapaiva-tiedot db vastaus urakka-id urakan-tiedot hoitokauden-alkuvuosi nykyhetki)

        ;; Ylikirjoita hoitovuosikohtaiset arvot esim. kirjaus-kkt tarkemmilla säännöillä
        vastaus (ylikirjoita-hoitovuosikohtaiset-arvot db vastaus hoitovuosi-nro)

        ;; Prosessoi vastaukset
        vastaus (prosessoi-lupausvastaukset
                  vastaus
                  maarapaiva-tiedot
                  {:db db
                   :urakka-id urakka-id
                   :urakan-alkuvuosi urakan-alkuvuosi
                   :hoitokauden-alkuvuosi hoitokauden-alkuvuosi
                   :hoitovuosi-nro hoitovuosi-nro
                   :valittu-hoitokausi valittu-hoitokausi
                   :nykyhetki nykyhetki})

        lupaus-sitoutuminen (sitoutumistiedot vastaus)
        lupausryhmat (lupausryhman-tiedot vastaus)
        piste-maksimi (lupaus-domain/rivit->maksimipisteet lupausryhmat)
        piste-ennuste (lupaus-domain/rivit->ennuste lupausryhmat)
        piste-toteuma (lupaus-domain/rivit->toteuma lupausryhmat)
        odottaa-kannanottoa (lupaus-domain/lupausryhmat->odottaa-kannanottoa lupausryhmat)
        merkitsevat-odottaa-kannanottoa (lupaus-domain/lupausryhmat->merkitsevat-odottaa-kannanottoa lupausryhmat)
        odottaa-urakoitsijan-kannanottoa? (> odottaa-kannanottoa merkitsevat-odottaa-kannanottoa)

        ;; Hae talouslaskelmat
        {:keys [tavoitehinta
                oikaistu-tavoitehinta
                oikaistu-toteutuneet-kustannukset
                tavoitehinta-puuttuu?
                luvatut-pisteet-puuttuu?]} (hae-talouslaskelmat db urakka-id
                                                                 hk-alkupvm hk-loppupvm
                                                                 hoitokauden-alkuvuosi
                                                                 lupaus-sitoutuminen)

        tallennettu-paatos (hae-lupauspaatos db urakka-id (pvm/vuosi hk-alkupvm))
        valikatselmus-tehty? (valikatselmus-tehty-urakalle? db urakka-id hoitokauden-alkuvuosi)

        ;; Laske bonus ja ennuste
        {:keys [bonus-tai-sanktio ennusteen-tila lupausprosentit-puuttuu?]}
        (laske-bonus-ja-ennuste
          {:db db
           :urakka-id urakka-id
           :tallennettu-paatos tallennettu-paatos
           :piste-toteuma piste-toteuma
           :piste-ennuste piste-ennuste
           :lupaus-sitoutuminen lupaus-sitoutuminen
           :tavoitehinta tavoitehinta
           :nykyhetki nykyhetki
           :hk-alkupvm hk-alkupvm})

        ;; Muodosta yhteenveto
        yhteenveto (muodosta-yhteenveto
                     {:piste-maksimi piste-maksimi
                      :piste-ennuste piste-ennuste
                      :piste-toteuma piste-toteuma
                      :bonus-tai-sanktio bonus-tai-sanktio
                      :tavoitehinta tavoitehinta
                      :oikaistu-tavoitehinta oikaistu-tavoitehinta
                      :oikaistu-toteutuneet-kustannukset oikaistu-toteutuneet-kustannukset
                      :kustannusennuste-pisteet-tila kustannusennuste-pisteet-tila
                      :odottaa-kannanottoa odottaa-kannanottoa
                      :merkitsevat-odottaa-kannanottoa merkitsevat-odottaa-kannanottoa
                      :odottaa-urakoitsijan-kannanottoa? odottaa-urakoitsijan-kannanottoa?
                      :valikatselmus-tehty? valikatselmus-tehty?
                      :tavoitehinta-puuttuu? tavoitehinta-puuttuu?
                      :lupausprosentit-puuttuu? lupausprosentit-puuttuu?
                      :luvatut-pisteet-puuttuu? luvatut-pisteet-puuttuu?
                      :ennusteen-tila ennusteen-tila
                      :tallennettu-paatos tallennettu-paatos})]
    {:lupaus-sitoutuminen (if tallennettu-paatos
                            ;; Näytetään päätökseen tallennetut pisteet, jos saatavilla
                            {:pisteet (:luvatut_pisteet tallennettu-paatos)}
                            lupaus-sitoutuminen)
     :lupausryhmat lupausryhmat
     ;; Lähtötiedot tarkistusta varten, ei välttämätöntä
     :lahtotiedot {:urakka-id urakka-id
                   :valittu-hoitokausi valittu-hoitokausi
                   :nykyhetki nykyhetki} ; Minkä hetken mukaan on laskettu
     ;; Yhteenveto
     :yhteenveto yhteenveto}))

(defn- hae-urakan-lupaustiedot [db user {:keys [urakka-id valittu-hoitokausi] :as tiedot}]
  {:pre [(number? urakka-id) valittu-hoitokausi
         (inst? (first valittu-hoitokausi)) (inst? (second valittu-hoitokausi))]}
  (log/debug "hae-urakan-lupaustiedot " tiedot)
  (oikeudet/vaadi-lukuoikeus oikeudet/urakat-lupaukset user urakka-id)
  (hae-urakan-lupaustiedot-hoitokaudelle db tiedot))

(defn vaadi-lupaus-sitoutuminen-kuuluu-urakkaan
  "Tarkistaa, että lupaus-sitoutuminen kuuluu annettuun urakkaan"
  [db urakka-id lupaus-sitoutuminen-id]
  (when (id-olemassa? lupaus-sitoutuminen-id)
    (let [lupauksen-urakka (:urakka-id (first (lupaus-kyselyt/hae-lupauksen-urakkatieto db {:id lupaus-sitoutuminen-id})))]
      (when-not (= lupauksen-urakka urakka-id)
        (throw (SecurityException. (str "Lupaus " lupaus-sitoutuminen-id " ei kuulu valittuun urakkaan "
                                        urakka-id " vaan urakkaan " lupauksen-urakka)))))))

(defn tallenna-urakan-luvatut-pisteet
  [db user {:keys [id urakka-id pisteet valittu-hoitokausi] :as tiedot}]
  (log/debug "tallenna-urakan-luvatut-pisteet tiedot " tiedot)
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-lupaukset user urakka-id)
  (when (not (roolit/tilaajan-kayttaja? user))
    (throw (SecurityException. "Luvattujen pisteiden tallentaminen vaatii tilaajan käyttäjän.")))
  (when id
    (vaadi-lupaus-sitoutuminen-kuuluu-urakkaan db urakka-id id))
  (assert (not (valikatselmus-tehty-urakalle? db urakka-id (pvm/vuosi (first valittu-hoitokausi))))
    "Luvattuja pisteitä ei voi enää muuttaa, jos urakalle on tehty välikatselmus.")
  (jdbc/with-db-transaction [db db]
                            (let [;; lupaussitoutumisia pitää olla kannassa maksimissaan 1. Aiemmin oli vika, että frontti saattoi lähettää alkutilanteessa useita
                                  ;; rivejä ilman id:tä, ja syntyi enemmän kuin yksi aktiivinen rivi. Nyt estetään se tarkistamalla kannasta onko ko. urakalle jo tieto
                                  sitoutumistiedot-id (:id (first (lupaus-kyselyt/hae-sitoutumistiedot db {:urakka-id urakka-id})))
                                  params {:id sitoutumistiedot-id
                                          :urakka-id urakka-id
                                          :pisteet pisteet
                                          :kayttaja (:id user)}]
                              (if sitoutumistiedot-id
                                (lupaus-kyselyt/paivita-urakan-luvatut-pisteet<! db params)
                                (lupaus-kyselyt/lisaa-urakan-luvatut-pisteet<! db params)))))

(defn- paivita-lupaus-vastaus [db user-id {:keys [id vastaus lupaus-vaihtoehto-id]}]
  {:pre [db user-id id]}
  (let [paivitetyt-rivit (lupaus-kyselyt/paivita-lupaus-vastaus!
                           db
                           {:vastaus vastaus
                            :lupaus-vaihtoehto-id lupaus-vaihtoehto-id
                            :muokkaaja user-id
                            :id id})]
    (assert (pos? paivitetyt-rivit) (str "lupaus_vastaus id " id " ei löytynyt"))
    (first (lupaus-kyselyt/hae-lupaus-vastaus db {:id id}))))

(defn- lisaa-lupaus-vastaus [db user-id {:keys [lupaus-id urakka-id kuukausi vuosi paatos vastaus lupaus-vaihtoehto-id]}]
  {:pre [db user-id lupaus-id urakka-id kuukausi vuosi (boolean? paatos)]}
  (lupaus-kyselyt/lisaa-lupaus-vastaus<!
    db
    {:lupaus-id lupaus-id
     :urakka-id urakka-id
     :kuukausi kuukausi
     :vuosi vuosi
     :paatos paatos
     :vastaus vastaus
     :lupaus-vaihtoehto-id lupaus-vaihtoehto-id
     :luoja user-id}))

(defn- sallittu-vaihtoehto?
  "Tarkista, että lupaus-vaihtoehto viittaa oikeaan lupaukseen."
  [db lupaus-id lupaus-vaihtoehto-id]
  (if lupaus-vaihtoehto-id
    (let [sallittu? (some-> (lupaus-kyselyt/hae-lupaus-vaihtoehto db {:id lupaus-vaihtoehto-id})
                            first
                            :lupaus-id
                            (= lupaus-id))]
      (log/debug "sallittu-vaihtoehto?" sallittu? lupaus-id lupaus-vaihtoehto-id)
      sallittu?)
    ;; Sallitaan nil-arvon asettaminen.
    true))

(defn- tallenna-kustannusennuste-vastaus [db user {:keys [lupaus-id urakka-id kuukausi vuosi
                                                           kustannusennuste] :as tiedot}]
  ;; EI lasketa pisteitä automaattisesti - ne lasketaan vasta välikatselmuksessa
   (let [maarapaiva (pvm/luo-pvm vuosi (dec kuukausi) 15)
         syotetty-pvm (pvm/nyt)
         hoitovuosi (pvm/hoitokauden-alkuvuosi vuosi kuukausi)

        ;; Tarkista onko kustannusennuste jo olemassa
         olemassa-oleva-id (kustannusennuste-kyselyt/hae-kustannusennuste-id
                             db {:lupaus-id lupaus-id
                                 :urakka-id urakka-id
                                 :maarapaiva maarapaiva})]

     (if olemassa-oleva-id
      ;; Päivitä olemassa oleva
       (kustannusennuste-kyselyt/paivita-kustannusennuste<! db
         {:id olemassa-oleva-id
          :tavoitehinta (:tavoitehinta kustannusennuste)
          :toteutuneet-kustannukset (:toteutuneet-kustannukset kustannusennuste)
          :syotetty-pvm syotetty-pvm
          :hoitovuosi hoitovuosi
         ;; EI tallenneta pisteitä vielä - ne lasketaan välikatselmuksessa
          :pisteet nil
          :kayttaja (:id user)})

      ;; Luo uusi
       (kustannusennuste-kyselyt/lisaa-kustannusennuste<! db
         {:lupaus-id lupaus-id
          :urakka-id urakka-id
          :maarapaiva maarapaiva
          :tavoitehinta (:tavoitehinta kustannusennuste)
          :toteutuneet-kustannukset (:toteutuneet-kustannukset kustannusennuste)
          :syotetty-pvm syotetty-pvm
          :hoitovuosi hoitovuosi
         ;; EI tallenneta pisteitä vielä - ne lasketaan välikatselmuksessa
          :pisteet nil
          :kayttaja (:id user)}))

    ;; Palauta kustannusennusteen tiedot
   (kustannusennuste-kyselyt/hae-kustannusennuste db
     {:lupaus-id lupaus-id
      :urakka-id urakka-id
      :maarapaiva maarapaiva})))

(defn- tarkista-vastaus-ja-vaihtoehto
  "Tarkista vastaustyyppi lupauksen tyypin mukaan"
  [db lupaus vastaus lupaus-vaihtoehto-id kustannusennuste]
  (cond
    (= "yksittainen" (:lupaustyyppi lupaus))
    (assert (nil? lupaus-vaihtoehto-id))

    (= "kustannusennuste" (:lupaustyyppi lupaus))
    (do
      (assert (nil? vastaus))
      (assert (nil? lupaus-vaihtoehto-id))
      (assert kustannusennuste "Kustannusennuste on pakollinen")
      (assert (:tavoitehinta kustannusennuste) "Tavoitehinta on pakollinen")
      (assert (:toteutuneet-kustannukset kustannusennuste) "Toteutuneet kustannukset pakolliset"))

    (or (= "monivalinta" (:lupaustyyppi lupaus))
      (= "kysely" (:lupaustyyppi lupaus)))
    (do (assert (nil? vastaus))
      (assert (sallittu-vaihtoehto? db (:id lupaus) lupaus-vaihtoehto-id)))))

(defn- tarkista-lupaus-vastaus
  "Tarkistaa lupausvastauksen oikeudet ja validiteetin.
  
  OIKEUSTARKISTUKSET:
  Excel (roolit.xlsx) määrittää kaikki oikeudet:
  - Kustannusennuste: vaatii erikoisoikeuden 'kustannusennuste' (Excel: W,kustannusennuste)
  - Päätökset: vain tilaajalle
  - Muut vastaukset: kirjoitusoikeus riittää (Excel: W)
  
  Oikeuskoodit:
  - R = Read (lukuoikeus)
  - W = Write (kirjoitusoikeus)
  - W,erikoisoikeus = Kirjoitus + nimetty erikoisoikeus"
  [db user {:keys [id lupaus-id urakka-id kuukausi vuosi paatos vastaus lupaus-vaihtoehto-id kustannusennuste] :as tiedot}]
  {:pre [db user tiedot]}
  
  ;; Perustarkistus: lukuoikeus riittää katseluun (Excel: R)
  (oikeudet/vaadi-lukuoikeus oikeudet/urakat-lupaukset user urakka-id)
  
  (assert (not (and vastaus lupaus-vaihtoehto-id)))
  ;; HUOM: vastaus/lupaus-vaihtoehto-id saa päivittää nil-arvoon (= ei vastattu)
  (let [{:keys [lupaus-id urakka-id vuosi kuukausi]} (if id
                                                       (first (lupaus-kyselyt/hae-lupaus-vastaus db {:id id}))
                                                       tiedot)
        _ (assert lupaus-id)
        lupaus (first (lupaus-kyselyt/hae-lupaus db {:id lupaus-id}))
        urakan-tiedot (first (urakat-q/hae-urakka db {:id urakka-id}))
        urakan-alkupvm (:alkupvm urakan-tiedot)
        paivamaara (pvm/luo-pvm vuosi (dec kuukausi) 1)
        hoitovuosi-nro (pvm/paivamaara->mhu-hoitovuosi-nro urakan-alkupvm paivamaara)
        hoitovuoden-erikoisarvot (first (lupaus-kyselyt/hae-lupauksen-hoitovuoden-kirjauskuukaudet db {:lupaus-id lupaus-id :hoitovuosi-nro hoitovuosi-nro}))]
    
    ;; OIKEUSTARKISTUKSET - Excel määrittää kuka saa tehdä mitäkin
    (cond
      ;; Kustannusennuste: vaatii erikoisoikeuden (Excel: W,kustannusennuste)
      (and kustannusennuste (= "kustannusennuste" (:lupaustyyppi lupaus)))
      (oikeudet/vaadi-oikeus "kustannusennuste"
                             oikeudet/urakat-lupaukset
                             user
                             urakka-id)
      
      ;; Päätökset: vaatii erikoisoikeuden (Excel: W,päätös)
      paatos
      (oikeudet/vaadi-oikeus "päätös"
                             oikeudet/urakat-lupaukset
                             user
                             urakka-id)
      
      ;; Muut vastaukset: kirjoitusoikeus riittää (Excel: W)
      (or vastaus lupaus-vaihtoehto-id)
      (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-lupaukset user urakka-id))
    
    ;; MUUT VALIDOINNIT
    (assert (false? (valikatselmus-tehty-urakalle? db urakka-id (pvm/hoitokauden-alkuvuosi vuosi kuukausi)))
            "Vastauksia ei voi enää muuttaa välikatselmuksen jälkeen")
    ;; Tarkista, että "yksittainen"-tyyppiselle lupaukselle on annettu boolean "vastaus",
    ;; ja muun tyyppiselle sallittu "lupaus-vaihtoehto-id".
    (tarkista-vastaus-ja-vaihtoehto db lupaus vastaus lupaus-vaihtoehto-id kustannusennuste)
    (when-not id
      ;; Tarkista, että kirjaus/päätös tulee sallitulle kuukaudelle.
      (assert (lupaus-domain/sallittu-kuukausi-hoitovuodelle? lupaus kuukausi paatos hoitovuosi-nro hoitovuoden-erikoisarvot)
              (str "Kuukausi " kuukausi " ei ole sallittu (paatos=" paatos ") hoitovuodelle " hoitovuosi-nro ".")))))

(defn- nykyhetki
  "Mahdollistaa nykyhetken lähettämisen parametrina kehitysympäristössä.
  Tämän tarkoitus on helpottaa testaamista.
  Tuotantoympäristössä palauttaa aina todellisen nykyhetken."
  [{:keys [nykyhetki] :as _tiedot} {:keys [kehitysmoodi] :as _asetukset}]
  (or (and kehitysmoodi nykyhetki)
      (pvm/nyt)))

(defn- lisaa-nykyhetki [tiedot asetukset]
  (assoc tiedot :nykyhetki (nykyhetki tiedot asetukset)))

(defn- vastaa-lupaukseen
  [db user {:keys [id lupaus-id] :as tiedot}]
  {:pre [db user tiedot]}
  (log/debug "vastaa-lupaukseen " tiedot)
  (tarkista-lupaus-vastaus db user tiedot)

  (jdbc/with-db-transaction [db db]
    (if (= "kustannusennuste" (:lupaustyyppi (first (lupaus-kyselyt/hae-lupaus db {:id lupaus-id}))))
      ;; Kustannusennustelogiikka
      (tallenna-kustannusennuste-vastaus db user tiedot)
      ;; Tavallinen lupausvastaus
      (if id
        (paivita-lupaus-vastaus db (:id user) tiedot)
        (lisaa-lupaus-vastaus db (:id user) tiedot)))))

(defn- kommentit
  [db user {:keys [lupaus-id urakka-id aikavali] :as tiedot}]
  {:pre [db user tiedot (number? lupaus-id) (number? urakka-id)
         (inst? (first aikavali)) (inst? (second aikavali))]}
  (log/debug "kommentit" tiedot)
  (let [[alkupvm loppupvm] aikavali]
    (oikeudet/vaadi-lukuoikeus oikeudet/urakat-lupaukset user urakka-id)
    (lupaus-kyselyt/kommentit db {:lupaus-id lupaus-id
                               :urakka-id urakka-id
                               :vuosi-alku (pvm/vuosi alkupvm)
                               :kuukausi-alku (pvm/kuukausi alkupvm)
                               :vuosi-loppu (pvm/vuosi loppupvm)
                               :kuukausi-loppu (pvm/kuukausi loppupvm)})))

(defn- lisaa-kommentti
  [db user {:keys [lupaus-id urakka-id kuukausi vuosi kommentti] :as tiedot}]
  {:pre [db user tiedot (number? lupaus-id) (number? urakka-id) (number? kuukausi) (number? vuosi)
         (string? kommentti)]}
  (log/debug "lisaa-kommentti" tiedot)
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-lupaukset user urakka-id)
  (jdbc/with-db-transaction [db db]
                            (let [kommentti (kommentit/luo-kommentti<!
                                              db nil kommentti nil (:id user))
                                  lupaus-kommentti (lupaus-kyselyt/lisaa-lupaus-kommentti<!
                                                     db
                                                     {:lupaus-id lupaus-id
                                                      :urakka-id urakka-id
                                                      :kuukausi kuukausi
                                                      :vuosi vuosi
                                                      :kommentti-id (:id kommentti)})]
                              (merge kommentti lupaus-kommentti))))

(defn- poista-kommentti
  [db user {:keys [id] :as tiedot}]
  {:pre [db user tiedot (number? id) (number? (:id user))]}
  (log/debug "poista-kommentti" tiedot)
  ;; Kysely poistaa vain käyttäjän itse luomia kommentteja, joten muita tarkistuksia ei ole.
  (let [paivitetyt-rivit (lupaus-kyselyt/poista-kayttajan-oma-kommentti!
                           db
                           {:id id
                            :kayttaja (:id user)})]
    (when-not (= paivitetyt-rivit 1)
      (throw (SecurityException. "Kommentin poistaminen epäonnistui")))
    paivitetyt-rivit))

(defn- tallenna-kuukausittaiset-pisteet
  "Vuonna 2019/2020 alkaneet urakat eivät käytä lupauksia, vaan heille aluevastaava tallentaa ennustetut pisteet tai
  toteutuneet pisteet kuukausittain. Näiden pisteiden perusteella voidaan sitten laskea bonus/sanktio."
  [db user {:keys [urakka-id kuukausi vuosi pisteet tyyppi id] :as tiedot}]
  {:pre [db user tiedot (number? urakka-id) (number? kuukausi) (number? vuosi) (number? pisteet) (string? tyyppi)
         (number? (:id user))]}
  (log/debug "tallenna-kuukausittaiset-pisteet :: tiedot" tiedot)
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-lupaukset user urakka-id)
  ;; Syyskuuuhun saa vastata vain tilaajan käyttäjä
  (when (and (= 9 kuukausi) (not (roolit/tilaajan-kayttaja? user)))
    (throw (SecurityException. "Lopullisen päätöksen tekeminen vaatii tilaajan käyttäjän.")))
  (let [;; Varmistetaan, että annetun urakan alkuvuosi on 2019 tai 2020.
        urakan-tiedot (first (urakat-q/hae-urakka db {:id urakka-id}))
        urakan-alkuvuosi (pvm/vuosi (:alkupvm urakan-tiedot))
        _ (assert (lupaus-domain/vuosi-19-20? urakan-alkuvuosi)
                  "Kuukausittaiset pisteet sallittu vain urakoille, jotka ovat alkaneet 2019/2020")
        arvot {:urakka-id urakka-id
               :kuukausi kuukausi
               :vuosi vuosi
               :pisteet pisteet
               :tyyppi tyyppi
               :kayttaja (:id user)}]
    (if id
      (lupaus-kyselyt/paivita-kuukausittaiset-pisteet<! db (merge arvot {:id id}))
      (lupaus-kyselyt/tallenna-kuukausittaiset-pisteet<! db arvot))))

(defn- poista-kuukausittaiset-pisteet
  "Poistetaan jo syötetyt pisteet"
  [db user {:keys [urakka-id id] :as tiedot}]
  {:pre [db user tiedot (number? urakka-id) (number? id) (number? (:id user))]}
  (log/debug "poista-kuukausittaiset-pisteet :: tiedot" tiedot)
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-lupaukset user urakka-id)

  (let [;; Varmistetaan, että annetun urakan alkuvuosi on 2019 tai 2020.
        urakan-tiedot (first (urakat-q/hae-urakka db {:id urakka-id}))
        urakan-alkuvuosi (pvm/vuosi (:alkupvm urakan-tiedot))
        _ (assert (lupaus-domain/vuosi-19-20? urakan-alkuvuosi)
                  "Kuukausittaiset pisteet sallittu vain urakoille, jotka ovat alkaneet 2019/2020")
        arvot {:urakka-id urakka-id
               :id id}
        ;; Haetaan kuukausivastauksen tiedot
        kuukausivastaus (lupaus-kyselyt/hae-kuukausivastaus db {:id id})
        ;; Syyskuuuhun saa vastata vain tilaajan käyttäjä
        _ (when (and (= 9 (:kuukausi kuukausivastaus)) (not (roolit/tilaajan-kayttaja? user)))
          (throw (SecurityException. "Lopullisen päätöksen tekeminen vaatii tilaajan käyttäjän.")))]
    (lupaus-kyselyt/poista-kuukausittaiset-pisteet<! db arvot)))

(defn hae-kuukausittaiset-pisteet-hoitokaudelle
  "Kuukausittaiset pisteet haetaan 2019/2020 alkaville urakoille. Näillä ei ole varsinaisia lupauksia ollenkaan"
  ([db tiedot]
   (hae-kuukausittaiset-pisteet-hoitokaudelle db {} tiedot))
  ([db kayttaja {:keys [urakka-id valittu-hoitokausi nykyhetki] :as tiedot}]
   {:pre [db tiedot (number? urakka-id) (not (nil? valittu-hoitokausi))]}
   (log/debug "hae-kuukausittaiset-pisteet-hoitokaudelle :: tiedot" tiedot)
   (let [hk-alkupvm (first valittu-hoitokausi)
         vuosi (pvm/vuosi hk-alkupvm)
         urakan-tiedot (first (urakat-q/hae-urakka db {:id urakka-id}))
         _ (assert
             (lupaus-domain/urakka-19-20? urakan-tiedot)
             "Kuukausittaiset pisteet sallittu vain urakoille, jotka ovat alkaneet 2019/2020")
         kuukausipisteet (lupaus-kyselyt/hae-kuukausittaiset-pisteet db {:hk-alkuvuosi vuosi
                                                                         :urakka-id urakka-id})
         sitoutumistiedot (first (lupaus-kyselyt/hae-sitoutumistiedot db {:urakka-id urakka-id}))
         ;; Kuukausittaisten pisteiden muokkaamisessa vaikuttaa tämän hoitokauden välikatselmus
         valikatselmus-tehty-hoitokaudelle? (valikatselmus-tehty-urakalle? db urakka-id (pvm/vuosi hk-alkupvm))
         ;; Koko urakkakauden sitoutumispisteisiin vaikuttaa onko urakalle tehty yhtään välitkaselmusta
         valikatselmus-tehty-urakalle? (valikatselmus-tehty-urakalle? db urakka-id vuosi)
         ;; Hae välikatselmuksen vahvistetut kustannusennusteet
         vahvistetut-kustannusennusteet (kustannusennuste-kyselyt/hae-valikatselmuksen-vahvistetut-kustannusennusteet db {:urakka-id urakka-id
                                                                                                              :hoitokauden-alkuvuosi vuosi})
         lopulliset-pisteet (lupaus-domain/kokoa-vastauspisteet kayttaja kuukausipisteet urakka-id
                              valittu-hoitokausi valikatselmus-tehty-hoitokaudelle?
                              nykyhetki)
         tavoitehinta (when hk-alkupvm (maarita-urakan-tavoitehinta db urakka-id hk-alkupvm))
         ;; Haetaan annetuista ennusteista viimeinen, jossa on arvo
         ennuste-pisteet (last (keep #(when (:pisteet %)
                                        (:pisteet %))
                                 ;; Lasketaan kuukaudet 10,11,12,1-8 mukaan ennustepisteisiin eli skipataan viimeinen, koska syyskuu on toteuma
                                 (take 11 lopulliset-pisteet)))
         toteuma-pisteet (:pisteet (last lopulliset-pisteet))
         bonus-tai-sanktio (lupaus-domain/bonus-tai-sanktio-19-20-urakalle {:toteuma (or toteuma-pisteet ennuste-pisteet)
                                                                            :lupaus (:pisteet sitoutumistiedot)
                                                                            :tavoitehinta tavoitehinta})
         ;; Näille -19/-20 alkaneille MH-urakoille (muita ei voi tällä funktiolla käsitellä) lasketaan
         ;; Indeksikorjaus automaattisesti hintaan mukaan
         bonus-tai-sanktio-pvm (-> (second valittu-hoitokausi)
                                 (pvm/vuosi)
                                 (pvm/luo-pvm-dec-kk 9 15)
                                 (konversio/sql-date))
         indeksikorotus-parametrit {:pvm bonus-tai-sanktio-pvm
                                    :indeksi (:indeksi urakan-tiedot)
                                    :maara (if (:sanktio bonus-tai-sanktio)
                                             (:sanktio bonus-tai-sanktio)
                                             (:bonus bonus-tai-sanktio))
                                    :urakka-id urakka-id
                                    :sanktiolaji (if (:sanktio bonus-tai-sanktio) "lupaussanktio" nil)}
         indeksikorotus (:korotus (first (lupaus-kyselyt/hae-indeksikorotus-summalle db indeksikorotus-parametrit)))
         luvatut-pisteet-puuttuu? (not (:pisteet sitoutumistiedot))
         hoitovuosi-valmis? (boolean toteuma-pisteet)
         ;; Ennuste voidaan tehdä, jos hoitokauden alkupäivä on menneisyydessä ja bonus-tai-sanktio != nil
         ennusteen-voi-tehda? (and (pvm/sama-tai-jalkeen? nykyhetki hk-alkupvm)
                                bonus-tai-sanktio)
         tavoitehinta-puuttuu? (not (and tavoitehinta (pos? tavoitehinta)))
         ;; Tarvitseeko urakoitsijalle lähettää muistutussähköpostia?
         odottaa-urakoitsijan-kannanottoa? (and
                                             (lupaus-domain/odottaa-urakoitsijan-kannanottoa?
                                                  lopulliset-pisteet)
                                             ;; Lähetetään sähköposti vanhalle urakalle vasta
                                             ;; sitten kun aluevastaava on täyttänyt luvatut
                                             ;; pisteet (merkki siitä, että lupaukset on otettu
                                             ;; käyttöön urakalle).
                                             (:pisteet sitoutumistiedot))]
     {:lupaus-sitoutuminen sitoutumistiedot
      :kuukausipisteet lopulliset-pisteet
      :yhteenveto {:ennusteen-tila (cond
                                     valikatselmus-tehty-hoitokaudelle? :katselmoitu-toteuma
                                     hoitovuosi-valmis? :alustava-toteuma
                                     ennusteen-voi-tehda? :ennuste
                                     :else :ei-viela-ennustetta)
                   :pisteet {:maksimi 100
                             :ennuste ennuste-pisteet
                             :toteuma toteuma-pisteet}
                   :bonus-tai-sanktio bonus-tai-sanktio
                   :indeksikorotus indeksikorotus
                   :tavoitehinta tavoitehinta
                   :odottaa-urakoitsijan-kannanottoa? odottaa-urakoitsijan-kannanottoa?
                   :valikatselmus-tehty-urakalle? valikatselmus-tehty-urakalle?
                   :tavoitehinta-puuttuu? tavoitehinta-puuttuu?
                   :luvatut-pisteet-puuttuu? luvatut-pisteet-puuttuu?
                   :vahvistetut-kustannusennusteet vahvistetut-kustannusennusteet}})))

(defn- hae-kustannusennuste-pisterajat
  "Hakee kustannusennusteen pisterajat tietokannasta ja konvertoi JSONB:n Clojure-dataksi"
  [db lupaus-id kuukausi]
  (let [tulos (kustannusennuste-kyselyt/hae-kustannusennuste-kuukausi-pisterajat
                db
                {:lupaus-id lupaus-id
                 :kuukausi kuukausi})]
    (when tulos
      (konversio/jsonb->clojuremap tulos))))

(defn maarita-kustannusennuste-pisteet
  "Määrittää pisteet tarkkuuden ja kuukauden perusteella tietokannasta haettujen pisterajojen mukaan.
   Kuukausikohtaiset raja-arvot määrittävät pistemäärän."
  ([db tarkkuus-prosentti kuukausi lupaus-id]
   (maarita-kustannusennuste-pisteet db tarkkuus-prosentti kuukausi lupaus-id nil))
  ([db tarkkuus-prosentti kuukausi lupaus-id pisterajat-data]
   {:pre [(number? tarkkuus-prosentti) (number? kuukausi) (number? lupaus-id)]}
   (let [tarkkuus-abs (Math/abs tarkkuus-prosentti)
         pisterajat-result (when-not pisterajat-data
                             (hae-kustannusennuste-pisterajat db lupaus-id kuukausi))

         ;; Käy läpi pisterajat järjestyksessä ja etsi sopiva
         tulos (some (fn [raja]
                       (let [operaattori (:operaattori raja)
                             raja-arvo (:raja raja)
                             pisteet (:pisteet raja)]
                         (cond
                           (and (= operaattori "≤") (<= tarkkuus-abs raja-arvo))
                           pisteet

                           (and (= operaattori ">") (> tarkkuus-abs raja-arvo))
                           pisteet

                           :else nil)))
                 pisterajat-result)]
     (or tulos 0))))

(defn laske-lopullinen-kustannusennuste!
  "Laskee lopulliset pisteet kustannusennusteille kun välikatselmus on saatavilla.
   Kutsutaan välikatselmuksen päätöksestä."
  [db urakka-id hoitokauden-alkuvuosi toteutunut-tavoitehinta toteutunut-kustannus valikatselmus-pvm user-id]
  {:pre [(some? db)
         (number? urakka-id)
         (number? hoitokauden-alkuvuosi)
         (number? toteutunut-tavoitehinta)
         (number? toteutunut-kustannus)
         (inst? valikatselmus-pvm)
         (number? user-id)]}
  (try
    (log/info (format "Lasketaan lopulliset kustannusennuste pisteet urakalle %s hoitokaudelle %s"
                urakka-id hoitokauden-alkuvuosi))

    ;; Hae hoitovuoden alun tavoitehinta
    (let [hk-alkupvm (pvm/hoitokauden-alkupvm hoitokauden-alkuvuosi)
          hoitovuoden-alun-tavoitehinta (maarita-urakan-tavoitehinta db urakka-id hk-alkupvm)
          ;; Atom keräämään kaikki lasketut pisteet keskiarvon laskemista varten
          keraa-pisteet (atom [])]

      ;; Hae kaikki kustannusennusteet lupausten kautta
      (let [lupaukset (lupaus-kyselyt/hae-urakan-lupaukset db {:urakka-id urakka-id})
            kustannusennuste-lupaukset (filter #(= "kustannusennuste" (:lupaustyyppi %)) lupaukset)]

        ;; Käy läpi jokainen kustannusennuste-lupaus
        (doseq [lupaus kustannusennuste-lupaukset]
          (let [lupaus-id (:lupaus-id lupaus)
                kaikki-ennusteet (kustannusennuste-kyselyt/hae-lupauksen-kaikki-kustannusennusteet-kaikki-hoitovuodet
                                   db {:lupaus-id lupaus-id
                                       :urakka-id urakka-id})
                ;; Suodata ne jotka pisteytetään tälle hoitokaudelle (huomioi offset)
                kustannusennusteet (filter (fn [ke]
                                             (let [kuukausi (pvm/kuukausi (:maarapaiva ke))
                                                   tallennuksen-hoitovuosi (:hoitovuosi ke)
                                                   offset (or (kustannusennuste-kyselyt/hae-kustannusennuste-kuukausi-offset
                                                                db {:lupaus-id lupaus-id
                                                                    :kuukausi kuukausi})
                                                              0)
                                                   pisteytys-hoitovuosi (+ tallennuksen-hoitovuosi offset)]
                                               (= pisteytys-hoitovuosi hoitokauden-alkuvuosi)))
                                     kaikki-ennusteet)]

            (doseq [ke kustannusennusteet]
              (let [ennustettu-tavoitehinta (:tavoitehinta ke)
                    ennustetut-kustannukset (:toteutuneet-kustannukset ke)
                    maarapaiva (:maarapaiva ke)
                    kustannusennuste-id (:id ke)
                    puuttuvat-arvot (cond-> []
                                              (nil? ennustettu-tavoitehinta) (conj "ennustettu-tavoitehinta")
                                              (nil? ennustetut-kustannukset) (conj "ennustetut-kustannukset")
                                              (nil? hoitovuoden-alun-tavoitehinta) (conj "hoitovuoden-alun-tavoitehinta"))]

                ;; Laske lopulliset pisteet domain-logiikalla
                (if (empty? puuttuvat-arvot)
                  (try
                    (let [tarkkuus-tulos (kustannusennuste-domain/laske-kustannusennusteen-tarkkuus
                                           {:ennustettu-tavoitehinta ennustettu-tavoitehinta
                                            :ennustettu-kustannus ennustetut-kustannukset
                                            :toteutunut-tavoitehinta toteutunut-tavoitehinta
                                            :toteutunut-kustannus toteutunut-kustannus
                                            :hoitovuoden-alun-tavoitehinta hoitovuoden-alun-tavoitehinta})
                          lopulliset-pisteet (maarita-kustannusennuste-pisteet
                                               db
                                               (:tarkkuus-prosentti tarkkuus-tulos)
                                               (pvm/kuukausi maarapaiva)
                                               lupaus-id)]

                      ;; Kerää pisteet keskiarvon laskemista varten
                      (swap! keraa-pisteet conj lopulliset-pisteet)

                      ;; Päivitä lopulliset pisteet tietokantaan
                      (kustannusennuste-kyselyt/paivita-kustannusennuste-lopulliset-pisteet!
                        db {:kustannusennuste-id kustannusennuste-id
                            :ennustettu-tavoitehinta ennustettu-tavoitehinta
                            :ennustetut-kustannukset ennustetut-kustannukset
                            :lasketut-pisteet lopulliset-pisteet
                            :tarkkuus-prosentti (double (:tarkkuus-prosentti tarkkuus-tulos))
                            :laskentakaava-versio (:laskentakaava-versio tarkkuus-tulos)
                            :laskentakaava-teksti (:laskentakaava-teksti tarkkuus-tulos)
                            :laskentakaava-parametrit (cheshire/encode (:laskentakaava-parametrit tarkkuus-tulos))
                            :laskentakaava-vaiheet (cheshire/encode (:laskentakaava-vaiheet tarkkuus-tulos))
                            :muokkaaja user-id})

                      (log/info (format "Päivitettiin kustannusennuste %s lopulliset pisteet: %s"
                                  kustannusennuste-id lopulliset-pisteet)))
                    (catch Exception e
                      (log/error e (format "Virhe laskettaessa pisteitä kustannusennusteelle %s: %s"
                                     kustannusennuste-id (.getMessage e)))))

                  (log/warn (format "Kustannusennuste %s: Päivitys ohitettiin puuttuvien arvojen takia. Puuttuvat: %s. Arvot: ennustettu-tavoitehinta=%s, ennustetut-kustannukset=%s, hoitovuoden-alun-tavoitehinta=%s"
                              kustannusennuste-id
                              (str/join ", " puuttuvat-arvot)
                              ennustettu-tavoitehinta
                              ennustetut-kustannukset
                              hoitovuoden-alun-tavoitehinta)))))))

        ;; Laske keskiarvo kerätyistä pisteistä
        (let [kaikki-pisteet @keraa-pisteet
              keskiarvo (when (seq kaikki-pisteet)
                          (double (/ (reduce + kaikki-pisteet) (count kaikki-pisteet))))]

          (log/info (format "Kustannusennusteen pisteet (yhteensä %s kpl): %s, keskiarvo: %s"
                      (count kaikki-pisteet)
                      kaikki-pisteet
                      keskiarvo))

          ;; Tallenna lopputilanne keskiarvon kanssa
          (lupaus-kyselyt/tallenna-lopputilanne! db {:urakka-id urakka-id
                                                     :hoitovuosi-alkuvuosi hoitokauden-alkuvuosi
                                                     :lopullinen-tavoitehinta toteutunut-tavoitehinta
                                                     :lopulliset-kustannukset toteutunut-kustannus
                                                     :valikatselmus-pvm valikatselmus-pvm
                                                     :vahvistaja user-id
                                                     :kustannusennuste-keskiarvo-pisteet keskiarvo})))

      (log/info (format "Lopulliset kustannusennuste pisteet laskettu urakalle %s hoitokaudelle %s"
                  urakka-id hoitokauden-alkuvuosi)))

    (catch Exception e
      (log/error e (format "Virhe laskettaessa lopullisia kustannusennuste pisteitä urakalle %s: %s"
                     urakka-id (.getMessage e)))
      (throw e))))

(defn hae-kuukausittaiset-pisteet [db user {:keys [urakka-id valittu-hoitokausi nykyhetki] :as tiedot}]
  {:pre [db user tiedot (number? urakka-id) (not (nil? valittu-hoitokausi)) (number? (:id user))]}
  (log/debug "hae-kuukausittaiset-pisteet :: tiedot" tiedot)
  (oikeudet/vaadi-lukuoikeus oikeudet/urakat-lupaukset user urakka-id)
  (hae-kuukausittaiset-pisteet-hoitokaudelle db user tiedot))

(defn generoi-lupausvastaukset
  "Generoidaan annetulle urakalle ja hoitokaudelle lupausvastaukset kaikille lupauksille, riippumatta lupaustyypistä."
  [db user {:keys [urakka-id valittu-hoitokausi] :as tiedot}]
  {:pre [db user tiedot (number? urakka-id) (not (nil? valittu-hoitokausi)) (number? (:id user))]}
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-lupaukset user urakka-id)
  (let [urakan-tiedot (first (urakat-q/hae-urakka db {:id urakka-id}))
        urakan-alkuvuosi (pvm/vuosi (:alkupvm urakan-tiedot))
        _ (lupaus-kyselyt/generoi-lupaukset-urakalle db {:urakkaid urakka-id
                                                         :hoitokauden_alkuvuosi (pvm/vuosi (first valittu-hoitokausi))
                                                         :urakan_alkuvuosi urakan-alkuvuosi
                                                         :kayttajaid (:id user)})]
    {:ok "ok"}))



(defrecord Lupaus [asetukset]
  component/Lifecycle
  (start [this]
    (julkaise-palvelu (:http-palvelin this)
                      :hae-urakan-lupaustiedot
                      (fn [user tiedot]
                        (hae-urakan-lupaustiedot
                          (:db this)
                          user
                          (lisaa-nykyhetki tiedot asetukset))))

    (julkaise-palvelu (:http-palvelin this)
                      :tallenna-luvatut-pisteet
                      (fn [user tiedot]
                        (tallenna-urakan-luvatut-pisteet
                          (:db this)
                          user
                          (lisaa-nykyhetki tiedot asetukset))))

    (julkaise-palvelu (:http-palvelin this)
                      :vastaa-lupaukseen
                      (fn [user tiedot]
                        (vastaa-lupaukseen (:db this) user tiedot)))

    (julkaise-palvelu (:http-palvelin this)
                      :lupauksen-kommentit
                      (fn [user tiedot]
                        (kommentit (:db this) user tiedot)))

    (julkaise-palvelu (:http-palvelin this)
                      :lisaa-lupauksen-kommentti
                      (fn [user tiedot]
                        (lisaa-kommentti (:db this) user tiedot)))

    (julkaise-palvelu (:http-palvelin this)
                      :poista-lupauksen-kommentti
                      (fn [user tiedot]
                        (poista-kommentti (:db this) user tiedot)))

    (julkaise-palvelu (:http-palvelin this)
                      :tallenna-kuukausittaiset-pisteet
                      (fn [user tiedot]
                        (tallenna-kuukausittaiset-pisteet (:db this) user (lisaa-nykyhetki tiedot asetukset))))

    (julkaise-palvelu (:http-palvelin this)
                      :poista-kuukausittaiset-pisteet
                      (fn [user tiedot]
                        (poista-kuukausittaiset-pisteet (:db this) user (lisaa-nykyhetki tiedot asetukset))))

    (julkaise-palvelu (:http-palvelin this)
                      :hae-kuukausittaiset-pisteet
                      (fn [user tiedot]
                        (hae-kuukausittaiset-pisteet (:db this) user (lisaa-nykyhetki tiedot asetukset))))
    (julkaise-palvelu (:http-palvelin this)
      :generoi-lupausvastaukset
      (fn [user tiedot]
        (generoi-lupausvastaukset (:db this) user (lisaa-nykyhetki tiedot asetukset))))

    this)

  (stop [this]
    (poista-palvelut
      (:http-palvelin this)
      :hae-urakan-lupaustiedot
      :tallenna-luvatut-pisteet
      :vastaa-lupaukseen
      :lupauksen-kommentit
      :lisaa-lupauksen-kommentti
      :poista-lupauksen-kommentti
      :tallenna-kuukausittaiset-pisteet
      :poista-kuukausittaiset-pisteet
      :hae-kuukausittaiset-pisteet
      :generoi-lupausvastaukset)
    this))
