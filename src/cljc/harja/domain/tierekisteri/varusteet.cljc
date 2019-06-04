(ns harja.domain.tierekisteri.varusteet
  "Tierekisterin Varusteet ja laitteet -teeman tietojen käsittelyä"
  (:require [clojure.string :as str]
    #?@(:clj [
            [clj-time.core :as t]])
    #?@(:cljs [[cljs-time.core :as t]])
            [harja.domain.tierekisteri :as tr]))

(def varuste-toimenpide->string {nil "Kaikki"
                                 :lisatty "Lisätty"
                                 :paivitetty "Päivitetty"
                                 :poistettu "Poistettu"
                                 :tarkastus "Tarkastus"})

(def varustetoteumatyypit
  (vec varuste-toimenpide->string))

(def tietolaji->selitys
  {"tl501" "Kaiteet"
   "tl517" "Portaat"
   "tl507" "Bussipysäkin varusteet"
   "tl508" "Bussipysäkin katos"
   "tl506" "Liikennemerkki"
   "tl522" "Reunakivet"
   "tl513" "Reunapaalut"
   "tl519" "Puomit ja kulkuaukot"
   "tl505" "Jätehuolto"
   "tl504" "WC"
   "tl518" "Kivetyt alueet"
   "tl514" "Melurakenteet"
   "tl509" "Rummut"
   "tl515" "Aidat"
   "tl503" "Levähdysalueiden varusteet"
   "tl512" "Viemärit"
   "tl516" "Hiekkalaatikot"
   "tl524" "Viherkuviot"
   "tl523" "Tekninen piste"})

(def selitys->tietolaji
  {"Kaiteet" "tl501"
   "Portaat" "tl517"
   "Bussipysäkin varusteet" "tl507"
   "Bussipysäkin katos" "tl508"
   "Liikennemerkki" "tl506"
   "Reunakivet" "tl522"
   "Reunapaalut" "tl513"
   "Puomit ja kulkuaukot" "tl519"
   "Jätehuolto" "tl505"
   "WC" "tl504"
   "Kivetyt alueet" "tl518"
   "Melurakenteet" "tl514"
   "Rummut" "tl509"
   "Aidat" "tl515"
   "Levähdysalueiden varusteet" "tl503"
   "Viemärit" "tl512"
   "Hiekkalaatikot" "tl516"
   "Viherkuviot" "tl524"
   "Tekninen piste" "tl523"})

(defn tien-puolet [tietolaji]
  (case tietolaji
    "tl523" [1 2 3]
    "tl501" [1 2 3 8]
    "tl517" [1 2]
    "tl506" [1 2 3]
    "tl507" [1 2 7]
    "tl508" [1 2 7]
    "tl509" [1 2 9]
    "tl522" [1 2 3 8]
    "tl513" [1 2 3]
    "tl196" [1 2 7]
    "tl519" [1 2 3]
    "tl505" [1 2 7 9]
    "tl195" [1 2 7 9]
    "tl504" [1 2 7 9]
    "tl198" [1 2 3 7]
    "tl518" [1 2 3 8]
    "tl514" [1 2 3]
    "tl515" [1 2 3]
    "tl503" [1 2 7 9]
    "tl512" [1 2 3 7 8 9]
    "tl165" [1 2]
    "tl516" [1 2 3 8]
    "tl524" [1 2 3 4 7 8 9]
    []))

(defn pistemainen-tietolaji? [tietolaji]
  (contains? #{"tl113",
               "tl191",
               "tl192",
               "tl195",
               "tl196",
               "tl197",
               "tl198",
               "tl202",
               "tl211",
               "tl230",
               "tl232",
               "tl251",
               "tl261",
               "tl262",
               "tl263",
               "tl264",
               "tl270",
               "tl310",
               "tl503",
               "tl504",
               "tl505",
               "tl506",
               "tl507",
               "tl508",
               "tl509",
               "tl511",
               "tl512",
               "tl516",
               "tl517",
               "tl519",
               "tl523",
               "tl524",
               "tl703"} tietolaji))

(defn valikohtainen-tietolaji [tietolaji]
  (contains? #{"tl109",
               "tl111",
               "tl112",
               "tl128",
               "tl130",
               "tl131",
               "tl132",
               "tl133",
               "tl134",
               "tl135",
               "tl136",
               "tl137",
               "tl138",
               "tl139",
               "tl141",
               "tl144",
               "tl145",
               "tl146",
               "tl149",
               "tl150",
               "tl151",
               "tl152",
               "tl153",
               "tl157",
               "tl158",
               "tl159",
               "tl161",
               "tl162",
               "tl164",
               "tl165",
               "tl166",
               "tl167",
               "tl168",
               "tl169",
               "tl170",
               "tl171",
               "tl173",
               "tl174",
               "tl180",
               "tl181",
               "tl194",
               "tl201",
               "tl210",
               "tl231",
               "tl233",
               "tl250",
               "tl271",
               "tl303",
               "tl305",
               "tl309",
               "tl312",
               "tl314",
               "tl317",
               "tl322",
               "tl323",
               "tl326",
               "tl327",
               "tl328",
               "tl330",
               "tl331",
               "tl332",
               "tl340",
               "tl341",
               "tl501",
               "tl510",
               "tl513",
               "tl514",
               "tl515",
               "tl518",
               "tl522",
               "tl704",
               "tl705",
               "tl714",
               "tl750",
               "tl751"} tietolaji))

(defn tien-puolellinen-tietolaji? [tietolaji]
  ;; Kaikki tietolajit ovat nyt puolellisia.
  true)

(defn tarkastaminen-sallittu? [tietolaji]
  (nil? (#{"tl524" "tl523"} tietolaji)))

(defn muokkaaminen-sallittu? [tietolaji]
  (nil? (#{"tl523"} tietolaji)))

(defn muokattavat-tietolajit []
  (filter #(muokkaaminen-sallittu? (first %)) tietolaji->selitys))

(def oletus-ajoradat
  [0])

(def kaikki-ajoradat
  [0 1 2])

(defn kiinnostaa-listauksessa?
  [ominaisuus]
  (let [tunniste (:kenttatunniste (:ominaisuus ominaisuus))]
    (and (not (#{"x" "y" "z" "urakka"} tunniste))
         (not (re-matches #".*tunn" tunniste)))))

(def varusteen-perustiedot-skeema
  [{:otsikko "Tietolaji"
    :tyyppi :tunniste
    :hae #(let [tietolaji (get-in % [:varuste :tietue :tietolaji :tunniste])]
            (str (tietolaji->selitys tietolaji) " (" tietolaji ")"))
    :leveys 1}
   {:otsikko "Tunniste"
    :tyyppi :tunniste
    :hae (comp :tunniste :varuste)
    :leveys 1}
   {:otsikko "Tieosoite"
    :tyyppi :tierekisteriosoite
    :hae (comp :tie :sijainti :tietue :varuste)
    :fmt tr/tierekisteriosoite-tekstina
    :leveys 2}])

(defn parsi-luku [s]
  #?(:cljs (js/parseInt s)
     :clj  (Integer/parseInt s)))

(defmulti varusteominaisuus->skeema
  "Muodostaa lomake/grid tyyppisen kentän skeeman varusteen ominaisuuden kuvauksen perusteella.
  Dispatch tapahtuu ominaisuuden tietotyypin perusteella."
  (fn [ominaisuus _]
    ((comp :tietotyyppi :ominaisuus) ominaisuus)))

(defn- varusteominaisuus-skeema-perus [ominaisuus muokattava?]
  {:otsikko (str/capitalize (:selite ominaisuus))
   :pakollinen? (and muokattava? (:pakollinen ominaisuus))
   :nimi (keyword (:kenttatunniste ominaisuus))
   :hae #(let [arvo (or (get-in % [:arvot (keyword (:kenttatunniste ominaisuus))])
                        (get-in % [:varuste :tietue :tietolaji :arvot (:kenttatunniste ominaisuus)]))]
           arvo)
   :aseta (fn [rivi arvo]
            (assoc-in rivi [:arvot (keyword (:kenttatunniste ominaisuus))] arvo))
   ;; Varusteen tunnistetta ei saa muokata koskaan
   :muokattava? #(and (not (= "tunniste" (:kenttatunniste ominaisuus))) muokattava?)
   :pituus-max (:pituus ominaisuus)})

(defn tietolajin-koodi-voimassa? [koodi]
  (if-let [{alkupvm :alkupvm loppupvm :loppupvm} (:voimassaolo koodi)]
    (cond
      (and alkupvm loppupvm) (t/within? (t/interval alkupvm loppupvm) (t/now))
      alkupvm (t/after? (t/now) alkupvm)
      loppupvm (t/before? (t/now) loppupvm)
      :else true)
    true))

(defmethod varusteominaisuus->skeema :koodisto
  [{ominaisuus :ominaisuus} muokattava?]
  (let [koodisto (map #(assoc % :selite (str/capitalize (:selite %))
                                :koodi (str (:koodi %)))
                      (:koodisto ominaisuus))
        ;; vanhat arvot saa näyttää vanhoille varusteille, mutta niitä ei saa käyttää muokatessa
        koodisto (if muokattava?
                   (filter tietolajin-koodi-voimassa? koodisto)
                   koodisto)
        hae-selite (fn [arvo]
                     (some #(when (= (:koodi %) arvo)
                              (str (:koodi %) " " (:selite %)))
                           koodisto))
        jarjestys-fn #(try (#?(:clj Float. :cljs js/parseFloat) (re-find #"^\d*" %))
                           #?(:clj  (catch Exception e
                                      1)
                              :cljs (catch :default e
                                      1)))]
    (merge (varusteominaisuus-skeema-perus ominaisuus muokattava?)
           {:tyyppi :valinta
            :valinnat (sort-by (comp jarjestys-fn hae-selite)
                               (map :koodi koodisto))
            :valinta-nayta (fn [arvo muokattava?]
                             (if arvo
                               (let [selite (hae-selite arvo)]
                                 selite)
                               (if muokattava?
                                 "- Valitse -"
                                 "")))
            :leveys 3
            :fmt (fn [arvo]
                   (let [koodi (first (filter #(= arvo (str (:koodi %))) koodisto))]
                     (if koodi
                       (hae-selite arvo)
                       arvo)))})))

(defmethod varusteominaisuus->skeema :numeerinen
  [{{:keys [pakollinen pituus alaraja ylaraja] :as ominaisuus} :ominaisuus} muokattava?]
  (merge (varusteominaisuus-skeema-perus ominaisuus muokattava?)
         {:tyyppi :string
          :regex (re-pattern (str "-?\\d*"))
          :validoi [#(cond
                       (and alaraja (not (str/blank? %)) (< (parsi-luku %) alaraja)) (str "Arvon pitää olla vähintään: " alaraja)
                       (and ylaraja (not (str/blank? %)) (> (parsi-luku %) ylaraja)) (str "Arvon pitää olla vähemmän kuin: " ylaraja)
                       :default nil)]
          :leveys 1}))

(defmethod varusteominaisuus->skeema :default
  [{ominaisuus :ominaisuus} muokattava?]
  (merge (varusteominaisuus-skeema-perus ominaisuus muokattava?)
         {:tyyppi :string
          :leveys (if (= "tunniste" (:kenttatunniste ominaisuus))
                    1
                    3)}))
