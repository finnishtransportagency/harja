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

(def +alustamenetelmat+
  "Kaikki alustan käsittelymenetelmät POT-lomake Excelistä ja
   :koodi 24 on asiakkaan jälkeenpäin pyytämä."
  [{:nimi "Massanvaihto" :lyhenne "MV" :koodi 1}
   {:nimi "Bitumiemusiostabilointi" :lyhenne "BEST" :koodi 11}
   {:nimi "Vaahtobitumistabilointi" :lyhenne "VBST" :koodi 12}
   {:nimi "Remix-stabilointi" :lyhenne "REST" :koodi 13}
   {:nimi "Sementtistabilointi" :lyhenne "SST" :koodi 14}
   {:nimi "Masuunihiekkastabilointi" :lyhenne "MHST" :koodi 15}
   {:nimi "Komposiittistabilointi" :lyhenne "KOST" :koodi 16}
   {:nimi "Kantavan kerroksen AB" :lyhenne "ABK" :koodi 21}
   {:nimi "Sidekerroksen AB" :lyhenne "ABS" :koodi 22}
   {:nimi "Murske" :lyhenne "MS" :koodi 23}
   {:nimi "Sekoitusjyrsintä" :lyhenne "SJYR" :koodi 24}
   {:nimi "Kuumennustasaus" :lyhenne "TASK" :koodi 31}
   {:nimi "Massatasaus" :lyhenne "TAS" :koodi 32}
   {:nimi "Tasausjyrsintä" :lyhenne "TJYR" :koodi 41}
   {:nimi "Laatikkojyrsintä" :lyhenne "LJYR" :koodi 42}
   {:nimi "Reunajyrsintä" :lyhenne "RJYR" :koodi 43}])

(def +alustamenetelmat-ja-nil+
  (conj +alustamenetelmat+ {:nimi "Ei menetelmää" :lyhenne "Ei menetelmää" :koodi nil}))

(def +alustamenetelma+ "Alustan käsittelymenetelmän valinta koodilla"
  (apply schema/enum (map :koodi +alustamenetelmat-ja-nil+)))

(defn alustamenetelma-koodi-nimella [nimi]
  (:koodi (first (filter #(= nimi (:nimi %)) +alustamenetelmat-ja-nil+))))

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

(def alusta-toimenpidespesifit-lisaavaimet
  {23 [:lisatty-paksuus :massamaara :murske]
   667 [:verkon-tyyppi :verkon-tarkoitus :verkon-sijainti]})

(defn alusta-toimenpide-lisaavaimet
  [toimenpide]
  (if-let [avaimet (get alusta-toimenpidespesifit-lisaavaimet toimenpide)]
    avaimet
    []))

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
        ylimaaraiset-avaimet (set/difference
                               (set (keys annettu-lisaparams))
                               (set (alusta-toimenpide-lisaavaimet toimenpide)))]
    (vec ylimaaraiset-avaimet)))

(def +tekniset-toimenpiteet+
  "Tekniset toimenpidetyypit POT-lomake Excelistä"
  [{:nimi "Rakentaminen" :koodi 1}
   {:nimi "Suuntauksen parantaminen" :koodi 2}
   {:nimi "Raskas rakenteen parantaminen" :koodi 3}
   {:nimi "Kevyt rakenteen parantaminen" :koodi 4}])

(def +tekniset-toimenpiteet-ja-nil+
  (conj +tekniset-toimenpiteet+ {:nimi "Ei toimenpidettä" :koodi nil}))

(def +tekninen-toimenpide-tai-nil+ "Teknisen toimenpiteen valinta koodilla"
  (apply schema/enum (map :koodi +tekniset-toimenpiteet-ja-nil+)))

(defn tekninentoimenpide-koodi-nimella [nimi]
  (:koodi (first (filter #(= nimi (:nimi %)) +tekniset-toimenpiteet-ja-nil+))))

(def +ajoradat-tekstina+
  "Ajoratavalinnat"
  [{:nimi "Yksiajoratainen" :koodi 0}
   {:nimi "Kaksiajorataisen ensimmäinen" :koodi 1}
   {:nimi "Kaksiajorataisen toinen ajorata" :koodi 2}])

(def +ajoradat-numerona+
  "Ajoratavalinnat"
  [{:nimi "0" :koodi 0}
   {:nimi "1" :koodi 1}
   {:nimi "2" :koodi 2}])

(def +kaistat+
  "Kaistavalinnat"
  [{:nimi "1" :koodi 1}
   {:nimi "11" :koodi 11}
   {:nimi "12" :koodi 12}
   {:nimi "13" :koodi 13}
   {:nimi "21" :koodi 21}
   {:nimi "22" :koodi 22}
   {:nimi "23" :koodi 23}
   {:nimi "14" :koodi 14}
   {:nimi "15" :koodi 15}
   {:nimi "16" :koodi 16}
   {:nimi "17" :koodi 17}
   {:nimi "18" :koodi 18}
   {:nimi "19" :koodi 19}
   {:nimi "24" :koodi 24}
   {:nimi "25" :koodi 25}
   {:nimi "26" :koodi 26}
   {:nimi "27" :koodi 27}
   {:nimi "28" :koodi 28}
   {:nimi "29" :koodi 29}
   {:nimi "31" :koodi 31}])

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