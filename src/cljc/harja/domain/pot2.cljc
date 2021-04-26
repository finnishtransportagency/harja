(ns harja.domain.pot2
  "Ylläpidon päällystysurakoissa käytettävän POT2-lomakkeen skeemat."
  (:require [schema.core :as schema]
            [harja.domain.paallystys-ja-paikkaus :as paallystys-ja-paikkaus]
            [specql.impl.registry]
            [specql.data-types]
            [harja.domain.urakka :as urakka]
            [harja.domain.muokkaustiedot :as m]
            [harja.domain.tierekisteri :as tr]
            [harja.validointi :as v]
            [clojure.set :as set]
            [clojure.spec.alpha :as s]
            [clojure.string :as str]
            #?@(:clj [[harja.kyselyt.specql-db :refer [define-tables]]]))
  #?(:cljs
     (:require-macros [harja.kyselyt.specql-db :refer [define-tables]])))

(def +masuunihiekkastabilointi-tp-koodi+ 15)
(def +masuunikuonan-sideainetyyppi-koodi+ 12)

(def alusta-toimenpide-kaikki-lisaavaimet
  {:lisatty-paksuus {:nimi :lisatty-paksuus :otsikko "Lisätty paksuus" :yksikko "cm"
                     :tyyppi :positiivinen-numero :kokonaisluku? true}
   :massamaara {:nimi :massamaara :otsikko "Massa\u00ADmäärä" :yksikko "kg/m²"
                :tyyppi :positiivinen-numero :kokonaisluku? true}
   :murske {:nimi :murske :otsikko "Murske"
            :tyyppi :valinta
            :valinta-arvo ::murske-id}
   :kasittelysyvyys {:nimi :kasittelysyvyys :otsikko "Käsittely\u00ADsyvyys" :yksikko "cm"
                     :tyyppi :positiivinen-numero :kokonaisluku? true}
   :leveys {:nimi :leveys :otsikko "Leveys" :yksikko "m"
            :tyyppi :positiivinen-numero :kokonaisluku? true}
   :pinta-ala {:nimi :pinta-ala :otsikko "Pinta-ala" :yksikko "m²"
               :tyyppi :positiivinen-numero :kokonaisluku? true}
   :kokonaismassamaara {:nimi :kokonaismassamaara :otsikko "Kokonais\u00ADmassa\u00ADmäärä" :yksikko "t"
                        :tyyppi :positiivinen-numero :kokonaisluku? true}
   :massa {:nimi :massa :otsikko "Massa"
           :tyyppi :valinta
           :valinta-arvo ::massa-id}
   :sideaine {:nimi :sideaine :otsikko "Sideaine"
              :tyyppi :valinta :valinnat-koodisto :sideainetyypit
              :valinta-arvo ::koodi :valinta-nayta ::nimi}
   :sideainepitoisuus {:nimi :sideainepitoisuus :otsikko "Sideaine\u00ADpitoisuus"
                       :tyyppi :positiivinen-numero :desimaalien-maara 1
                       :validoi-kentta-fn (fn [numero] (v/validoi-numero numero 0 100 1))}
   :sideaine2 {:nimi :sideaine2 :otsikko "Sideaine"
               :tyyppi :valinta :valinnat-koodisto :sidotun-kantavan-kerroksen-sideaine
               :valinta-arvo ::koodi :valinta-nayta ::nimi}
   :verkon-tyyppi {:nimi :verkon-tyyppi :otsikko "Verkon tyyppi"
                   :tyyppi :valinta :valinnat-koodisto :verkon-tyypit
                   :valinta-arvo ::koodi :valinta-nayta ::nimi}
   :verkon-sijainti {:nimi :verkon-sijainti :otsikko "Verkon sijainti"
                     :tyyppi :valinta :valinnat-koodisto :verkon-sijainnit
                     :valinta-arvo ::koodi :valinta-nayta ::nimi}
   :verkon-tarkoitus {:nimi :verkon-tarkoitus :otsikko "Verkon tarkoitus"
                      :tyyppi :valinta :valinnat-koodisto :verkon-tarkoitukset
                      :valinta-arvo ::koodi :valinta-nayta ::nimi}})

(defn alusta-toimenpidespesifit-metadata
  [toimenpide]
  "Palauta alusta toimenpide metadata lisäkenteistä"
  (let [alusta-toimenpidespesifit-lisaavaimet {1            ;; MV
                                               [:kasittelysyvyys :lisatty-paksuus :murske
                                                {:nimi :massamaara :pakollinen? false}]
                                               2            ;; AB
                                               [:massa :pinta-ala :kokonaismassamaara :massamaara]
                                               3            ;; Verkko
                                               [:verkon-tyyppi :verkon-sijainti
                                                {:nimi :verkon-tarkoitus :pakollinen? false}]
                                               4            ;; REM-TAS
                                               [:massa {:nimi :kokonaismassamaara :pakollinen? false} :massamaara]
                                               11           ;; BEST
                                               [:kasittelysyvyys :sideaine :sideainepitoisuus
                                                {:nimi :murske :pakollinen? false}
                                                {:nimi :massamaara :jos :murske}]
                                               12           ;; VBST
                                               [:kasittelysyvyys :sideaine :sideainepitoisuus
                                                {:nimi :murske :pakollinen? false}
                                                {:nimi :massamaara :jos :murske}]
                                               13           ;; REST
                                               [:kasittelysyvyys :sideaine :sideainepitoisuus
                                                {:nimi :murske :pakollinen? false}
                                                {:nimi :massamaara :jos :murske}]
                                               14           ;; SST
                                               [:kasittelysyvyys :sideaine2 :sideainepitoisuus
                                                {:nimi :murske :pakollinen? false}
                                                {:nimi :massamaara :jos :murske}]
                                               15           ;; MHST
                                               [:kasittelysyvyys
                                                {:nimi :sideaine2
                                                 ;; MHST toimenpiteelle sideainetyypin on aina oltava Masuunikuona
                                                 :muokattava? (fn [rivi]
                                                                (not= +masuunihiekkastabilointi-tp-koodi+
                                                                      (:toimenpide rivi)))}
                                                :sideainepitoisuus
                                                {:nimi :murske :pakollinen? false}
                                                {:nimi :massamaara :jos :murske}]
                                               16           ;; KOST
                                               [:kasittelysyvyys :sideaine :sideainepitoisuus
                                                {:nimi :sideaine2 :otsikko "Sideaine 2"}
                                                {:nimi :murske :pakollinen? false}
                                                {:nimi :massamaara :jos :murske}]
                                               21           ;; ABK
                                               [:massa :pinta-ala :kokonaismassamaara :massamaara]
                                               22           ;; ABS
                                               [:massa :pinta-ala :kokonaismassamaara :massamaara]
                                               23           ;; MS
                                               [:lisatty-paksuus
                                                {:nimi :massamaara :pakollinen? false}
                                                :murske]
                                               24           ;; SJYR
                                               [:kasittelysyvyys
                                                {:nimi :murske :pakollinen? false}]
                                               31           ;; TASK
                                               []
                                               32           ;; TAS
                                               [:massa {:nimi :kokonaismassamaara :pakollinen? false} :massamaara]
                                               41           ;; TJYR
                                               []
                                               42           ;; LJYR
                                               [:kasittelysyvyys
                                                :leveys :pinta-ala]
                                               43           ;; RJYR
                                               []}
        avaimet (get alusta-toimenpidespesifit-lisaavaimet toimenpide)
        luo-metadata-ja-oletusarvot (fn [avain-tai-metadata]
                                      (let [toimenpide-spesifinen-kentta-metadata (if (keyword? avain-tai-metadata)
                                                                                    {:nimi avain-tai-metadata :pakollinen? true}
                                                                                    (let [pakollinen? (:pakollinen? avain-tai-metadata)]
                                                                                      (if (nil? pakollinen?)
                                                                                        (assoc avain-tai-metadata :pakollinen? true)
                                                                                        avain-tai-metadata)))
                                            kentta-metadata (get alusta-toimenpide-kaikki-lisaavaimet (:nimi toimenpide-spesifinen-kentta-metadata))]
                                        (merge kentta-metadata toimenpide-spesifinen-kentta-metadata)))]
    (map luo-metadata-ja-oletusarvot avaimet)))

(defn alusta-kaikki-lisaparams
  "Palauta mappi jossa kaikki non-nil alustan lisäkentät"
  [alusta]
  (let [keep-some (fn [params]
                    (into {} (filter
                               (fn [[_ arvo]] (some? arvo))
                               params)))
        lisaparams (select-keys alusta (keys alusta-toimenpide-kaikki-lisaavaimet))
        annetut-lisaparams (keep-some lisaparams)]
    annetut-lisaparams))

(defn alusta-sallitut-ja-pakolliset-lisaavaimet
  "Palauta vain sallttut avaimet"
  [alusta]
  (let [relevantti-metadata (->> (alusta-toimenpidespesifit-metadata (:toimenpide alusta))
                                 (filter (fn [{:keys [jos]}]
                                           (or (nil? jos)
                                               (and (some? jos)
                                                    (some? (get alusta jos)))))))
        sallitut (->> relevantti-metadata
                      (map #(:nimi %))
                      set)
        pakolliset (->> relevantti-metadata
                        (filter #(:pakollinen? %))
                        (map #(:nimi %))
                        set)]
    [sallitut pakolliset]))

(defn alusta-ylimaaraiset-lisaparams-avaimet
  "Palauta vector jossa kaikki non-nil lisäparametrien avaimet jotka eivät kuulu toimenpiteeseen.
  Voi sisältää myös pakolliset-ehdolliset avaimet, jotka eivät kuuluu annetulle alustalle"
  [{:keys [toimenpide] :as alusta}]
  (let [annettu-lisaparams (alusta-kaikki-lisaparams alusta)
        [sallitut-lisaavaimet _] (alusta-sallitut-ja-pakolliset-lisaavaimet alusta)
        ylimaaraiset-avaimet (set/difference
                               (set (keys annettu-lisaparams))
                               sallitut-lisaavaimet)]
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
  ["pot2_mk_paallystekerros_toimenpide" ::pot2-mk-paallystekerros-toimenpide
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
  ["pot2_mk_sidotun_kantavan_kerroksen_sideaine" ::pot2-mk-sidotun-kantavan-kerroksen-sideaine]
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

(defn murskeen-rikastettu-nimi [mursketyypit murske]
  ; (str ydin (when-not (empty? tarkennukset) (str "(" tarkennukset ")")))
  (let [ydin (str (ainetyypin-koodi->lyhenne mursketyypit (::tyyppi murske)) " "
                  (rivin-avaimet->str murske #{::nimen-tarkenne}))
        tarkennukset (rivin-avaimet->str murske #{::dop-nro} ", ")
        tarkennukset-teksti (when (seq tarkennukset) (str " (" tarkennukset ")"))]
    [ydin tarkennukset-teksti]))

(def asfalttirouheen-tyypin-id 2)

(defn massan-rc-pitoisuus
  "Palauttaa massan RC-pitoisuuden jos sellainen on (=asfalttirouheen massaprosentti)"
  [rivi]
  (when-let [runkoaineet (::runkoaineet rivi)]
    (when-let [asfalttirouhe (first (filter #(= (:runkoaine/tyyppi %) asfalttirouheen-tyypin-id)
                                            runkoaineet))]
      (str "RC" (:runkoaine/massaprosentti asfalttirouhe)))))

(defn massan-rikastettu-nimi
  "Formatoi massan nimen. Jos haluat Reagent-komponentin, anna fmt = :komponentti, muuten anna :string"
  [massatyypit massa]
  ;; esim AB16 (AN15, RC40, 2020/09/1234) tyyppi (raekoko, nimen tarkenne, DoP, Kuulamyllyluokka, RC%)
  (let [massa (assoc massa ::rc% (massan-rc-pitoisuus massa))
        ydin (str (ainetyypin-koodi->lyhenne massatyypit (::tyyppi massa))
                  (rivin-avaimet->str massa [::max-raekoko
                                             ::nimen-tarkenne]))

        tarkennukset (rivin-avaimet->str massa [::dop-nro] ", ")
        tarkennukset-teksti (when (seq tarkennukset) (str " (" tarkennukset ")"))]
    [ydin tarkennukset-teksti]))
