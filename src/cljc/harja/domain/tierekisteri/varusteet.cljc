(ns harja.domain.tierekisteri.varusteet
  "Tierekisterin Varusteet ja laitteet -teeman tietojen käsittelyä"
  (:require [clojure.string :as str]
    #?@(:clj [[clj-time.core :as t]])
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

(defn tien-puolet [tietolaji]
  (case tietolaji
    "tl523" [1 2 3]
    "tl501" [1 2 3 8]
    "tl517" [1 2]
    "tl507" [1 2 7]
    "tl508" [1 2 7]
    "tl506" [1 2 3]
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

(defn tarkastaminen-sallittu? [tietolaji]
  (nil? (#{"tl524" "tl523"} tietolaji)))

(defn muokkaaminen-sallittu? [tietolaji]
  (nil? (#{"tl523"} tietolaji)))

(defn muokattavat-tietolajit[]
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
          (comp :tietotyyppi :ominaisuus))

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
        hae-selite (fn [arvo] (:selite (first (filter #(= (:koodi %) arvo) koodisto))))]
    (merge (varusteominaisuus-skeema-perus ominaisuus muokattava?)
           {:tyyppi :valinta
            :valinnat (map :koodi koodisto)
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
