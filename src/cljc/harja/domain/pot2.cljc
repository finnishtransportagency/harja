(ns harja.domain.pot2
  "Ylläpidon päällystysurakoissa käytettävän POT2-lomakkeen skeemat."
  (:require [schema.core :as schema]
            [harja.domain.paallystys-ja-paikkaus :as paallystys-ja-paikkaus]
            [specql.impl.registry]
            [specql.data-types]
            [harja.domain.urakka :as urakka]
            [harja.domain.muokkaustiedot :as m]
            [harja.domain.tierekisteri :as tr]
            [clojure.set :as set]
            [clojure.spec.alpha :as s]
            [clojure.string :as str]
            #?@(:clj [[harja.kyselyt.specql-db :refer [define-tables]]
                      ]))
  #?(:cljs
     (:require-macros [harja.kyselyt.specql-db :refer [define-tables]])))

(def alusta-toimenpide-kaikki-lisaavaimet
  [:lisatty-paksuus
   :massamaara
   :murske
   :kasittelysyvyys
   :leveys
   :pinta-ala
   :kokonaismassamaara
   :massa
   :sideaine
   :sideainepitoisuus
   :sideaine2
   :verkon-tyyppi
   :verkon-sijainti
   :verkon-tarkoitus])

(defn alusta-toimenpide-lisaavaimet
  [toimenpide]
  "Palauta alusta toimenpide metadata lisäkenteistä"
  (let [alusta-toimenpidespesifit-lisaavaimet {1            ;; MV
                                               [:kasittelysyvyys :lisatty-paksuus :murske
                                                {:nimi :massamaara :pakollinen? false}]
                                               23           ;; MS
                                               [:lisatty-paksuus
                                                {:nimi :massamaara :pakollinen? false}
                                                :murske]
                                               24           ;; SJYR
                                               [:kasittelysyvyys
                                                {:nimi :murske :pakollinen? false}]
                                               42           ;; LJYR
                                               [:kasittelysyvyys :leveys :pinta-ala]
                                               667          ;; Verkko
                                               [:verkon-tyyppi :verkon-sijainti
                                                {:nimi :verkon-tarkoitus :pakollinen? false}]}
        avaimet (get alusta-toimenpidespesifit-lisaavaimet toimenpide)
        luo-metadata (fn [avain]
                       (if (keyword? avain)
                         {:nimi avain :pakollinen? true}
                         avain))]
    (map luo-metadata avaimet)))

(defn alusta-kaikki-lisaparams
  "Palauta mappi jossa kaikki non-nil alustan lisäkentät"
  [alusta]
  (let [keep-some (fn [params]
                    (into {} (filter
                               (fn [[_ arvo]] (some? arvo))
                               params)))
        lisaparams (select-keys alusta alusta-toimenpide-kaikki-lisaavaimet)
        annetut-lisaparams (keep-some lisaparams)]
    annetut-lisaparams))

(defn alusta-ylimaaraiset-lisaparams-avaimet
  "Palauta vector jossa kaikki non-nil lisäparametrien avaimet jotka eivät kuulu toimenpiteeseen"
  [{:keys [toimenpide] :as alusta}]
  (let [annettu-lisaparams (alusta-kaikki-lisaparams alusta)
        sallitut-lisaavaimet (map #(:nimi %) (alusta-toimenpide-lisaavaimet toimenpide))
        ylimaaraiset-avaimet (set/difference
                               (set (keys annettu-lisaparams))
                               (set sallitut-lisaavaimet))]
    (vec ylimaaraiset-avaimet)))

(defn paattele-ilmoituksen-tila
  [valmis-kasiteltavaksi tekninen-osa-hyvaksytty]
  (cond
    tekninen-osa-hyvaksytty
    "lukittu"

    valmis-kasiteltavaksi
    "valmis"

    :default
    "aloitettu"))

(defn arvo-koodilla [koodisto koodi]
  (:nimi (first (filter #(= (:koodi %) koodi) koodisto))))

(define-tables
  ["pot2_mk_kulutuskerros_toimenpide" ::pot2-mk-kulutuskerros-toimenpide
   {"koodi" ::koodi
    "nimi" ::nimi
    "lyhenne" ::lyhenne}]
  ["pot2_mk_alusta_toimenpide" ::pot2-mk-alusta-toimenpide
   {"koodi" ::koodi
    "nimi" ::nimi
    "lyhenne" ::lyhenne}]
  ["pot2_verkon_sijainti" ::pot2-verkon-sijainti
   {"koodi" ::koodi
    "nimi" ::nimi
    "lyhenne" ::lyhenne}]
  ["pot2_verkon_tarkoitus" ::pot2-verkon-tarkoitus
   {"koodi" ::koodi
    "nimi" ::nimi
    "lyhenne" ::lyhenne}]
  ["pot2_verkon_tyyppi" ::pot2-verkon-tyyppi
   {"koodi" ::koodi
    "nimi" ::nimi
    "lyhenne" ::lyhenne}]

  ["pot2_paallystekerros" ::pot2-paallystekerros]
  ["pot2_alusta" ::pot2-alusta]
  ["pot2_mk_massatyyppi" ::pot2-mk-massatyyppi]
  ["pot2_mk_mursketyyppi" ::pot2-mk-mursketyyppi]
  ["pot2_mk_runkoainetyyppi" ::pot2-mk-runkoainetyyppi]
  ["pot2_mk_sideainetyyppi" ::pot2-mk-sideainetyyppi]
  ["pot2_mk_lisaainetyyppi" ::pot2-mk-lisaainetyyppi]
  ["pot2_mk_massan_runkoaine" ::pot2-mk-massan-runkoaine
   {"id" :runkoaine/id
    "pot2_massa_id" ::massa-id
    "tyyppi" :runkoaine/tyyppi
    "esiintyma" :runkoaine/esiintyma
    "fillerityyppi" :runkoaine/fillerityyppi
    "kuvaus" :runkoaine/kuvaus
    "kuulamyllyarvo" :runkoaine/kuulamyllyarvo
    "litteysluku" :runkoaine/litteysluku
    "massaprosentti" :runkoaine/massaprosentti}]
  ["pot2_mk_massan_sideaine" ::pot2-mk-massan-sideaine
   {"id" :sideaine/id
    "pot2_massa_id" ::massa-id
    "tyyppi" :sideaine/tyyppi
    "pitoisuus" :sideaine/pitoisuus
    "lopputuote?" :sideaine/lopputuote?}]
  ["pot2_mk_massan_lisaaine" ::pot2-mk-massan-lisaaine
   {"id" :lisaaine/id
    "pot2_massa_id" ::massa-id
    "tyyppi" :lisaaine/tyyppi
    "pitoisuus" :lisaaine/pitoisuus}]
  ["pot2_mk_urakan_massa" ::pot2-mk-urakan-massa
   {"id" ::massa-id
    "pot2_id" ::pot2-id
    "urakka_id" ::urakka-id
    "nimen_tarkenne" ::nimen-tarkenne
    "tyyppi" ::tyyppi
    "max_raekoko" ::max-raekoko
    "kuulamyllyluokka" ::kuulamyllyluokka
    "litteyslukuluokka" ::litteyslukuluokka
    "dop_nro" ::dop-nro
    "poistettu" ::poistettu?

    "muokkaaja" ::m/muokkaaja-id
    "muokattu" ::m/muokattu
    "luoja" ::m/luoja-id
    "luotu" ::m/luotu}
   {::runkoaineet (specql.rel/has-many ::massa-id
                                       ::pot2-mk-massan-runkoaine
                                       ::massa-id)}
   {::sideaineet (specql.rel/has-many ::massa-id
                                      ::pot2-mk-massan-sideaine
                                      ::massa-id)}
   {::lisaaineet (specql.rel/has-many ::massa-id
                                       ::pot2-mk-massan-lisaaine
                                       ::massa-id)}]
  ["pot2_mk_urakan_murske" ::pot2-mk-urakan-murske
   {"id" ::murske-id
    "urakka_id" ::urakka-id
    "nimen_tarkenne" ::nimen-tarkenne
    "tyyppi" ::tyyppi
    "tyyppi_tarkenne" ::tyyppi-tarkenne
    "esiintyma" ::esiintyma
    "lahde" ::lahde
    "rakeisuus" ::rakeisuus
    "rakeisuus_tarkenne" ::rakeisuus-tarkenne
    "iskunkestavyys" ::iskunkestavyys
    "dop_nro" ::dop-nro
    "poistettu" ::poistettu?

    "muokkaaja" ::m/muokkaaja-id
    "muokattu" ::m/muokattu
    "luoja" ::m/luoja-id
    "luotu" ::m/luotu}])

(def massan-max-raekoko [5, 8, 11, 16, 22, 31])
(def litteyslukuluokat [1, 2, 3, 4, 5, 6])
;; ao. arvot tulevat postgres CUSTOM ENUM typeistä. Pidettävä synkassa.
(def murskeen-rakeisuusarvot ["0/32" "0/40" "0/45" "0/56" "0/63" "Muu"])
(def murskeen-iskunkestavyysarvot ["LA30" "LA35" "LA40"])

(def erikseen-lisattava-fillerikiviaines
  ;; Huom! Tämän on matchattava postgres custom typen fillerityyppi -arvoihin
  ["Kalkkifilleri (KF)", "Lentotuhka (LT)", "Muu fillerikiviaines"])

(defn lisaa-paallystekerroksen-jarjestysnro
  "Lisää päällystekerroksen riveille järjesteysnumeron. 1 = kulutuskerros, 2 = alempi päällystekerros"
  [rivit nro]
  (assert (#{1 2} nro) "Päällystekerroksen järjestysnumero voi olla 1 tai 2")
  (map #(assoc % :jarjestysnro nro) rivit))

;; Murskelomakkeen sarakkeet vaihtelevat mursketyypin mukaan
(def murskesarakkeet-kam-ja-srm
  #{::esiintyma
    ::nimen-tarkenne
    ::iskunkestavyys
    ::tyyppi
    ::rakeisuus
    ::dop-nro
    ::rakeisuus-tarkenne
    ::urakka-id})

(def murskesarakkeet-ra
  #{::esiintyma
    ::nimen-tarkenne
    ::iskunkestavyys
    ::tyyppi
    ::rakeisuus
    ::rakeisuus-tarkenne
    ::urakka-id})

(def murskesarakkeet-bem1-ja-bem2
  #{::lahde
    ::nimen-tarkenne
    ::tyyppi
    ::urakka-id})

(def murskesarakkeet-muu
  #{::lahde
    ::nimen-tarkenne
    ::tyyppi-tarkenne
    ::tyyppi
    ::urakka-id})

(defn mursketyypin-lyhenne->sarakkeet [lyhenne]
  (case lyhenne
    "KaM" murskesarakkeet-kam-ja-srm
    "SrM" murskesarakkeet-kam-ja-srm
    "RA" murskesarakkeet-ra
    "BeM I" murskesarakkeet-bem1-ja-bem2
    "BeM II" murskesarakkeet-bem1-ja-bem2
    "Muu" murskesarakkeet-muu
    ;; haluamme ettei tule tuntemattomia, nil throwaa lopulta koska ei sarakkeita
    nil))

(defn- ainetyyppi-koodilla [ainetyypit koodi]
  (first
    (filter #(= (::koodi %) koodi)
            ainetyypit)))

(defn- ainetyyppi-nimella [ainetyypit nimi]
  (first
    (filter #(= (::nimi %) nimi)
            ainetyypit)))

(defn ainetyypin-koodi->lyhenne [ainetyypit koodi]
  (::lyhenne (ainetyyppi-koodilla ainetyypit koodi)))

(defn ainetyypin-nimi->koodi [ainetyypit nimi]
  (::koodi (ainetyyppi-nimella ainetyypit nimi) ))

(defn ainetyypin-koodi->nimi [ainetyypit koodi]
  (::nimi (ainetyyppi-koodilla ainetyypit koodi)))


(defn rivin-avaimet->str
  ([rivi avaimet] (rivin-avaimet->str rivi avaimet " "))
  ([rivi avaimet separator]
   (str/join separator
             (remove nil? (mapv val (select-keys rivi avaimet))))))

(defn mursken-rikastettu-nimi [mursketyypit murske]
  ; (str ydin (when-not (empty? tarkennukset) (str "(" tarkennukset ")")))
  (let [ydin (str (ainetyypin-koodi->lyhenne mursketyypit (::tyyppi murske)) " "
                  (rivin-avaimet->str murske #{::nimen-tarkenne ::dop-nro}))
        tarkennukset (rivin-avaimet->str murske #{::rakeisuus ::iskunkestavyys} ", ")
        tarkennukset-teksti (when (seq tarkennukset) (str "(" tarkennukset ")"))]
    [ydin tarkennukset-teksti]))
