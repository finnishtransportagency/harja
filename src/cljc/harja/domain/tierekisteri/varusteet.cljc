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
   "tl520" "Puomit"
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
   "Puomit" "tl520"
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

(def puolet
  {0 "Määrittämätön"
   1 "Oikea"
   2 "Vasen"
   3 "Ajoratojen välissä (ajosuuntia erottavalla keskialueella)"
   7 "Tien päässä"
   8 "Keskellä (ajoradalla tai sen yläpuolella)"
   9 "Tien päällä"})

(defn puoli->selitys
  [puoli]
  (get puolet puoli))

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

(defn varusteen-liikennemerkki-skeema
  [tietolaji]
  (let [ominaisuudet (get-in tietolaji [:tietolaji :ominaisuudet])
        liikennemerkki (:ominaisuus (first (filter #(= (get-in % [:ominaisuus :kenttatunniste]) "asetusnr") ominaisuudet)))
        asetusnr->teksti (fn [numero]
                           (:selite (first (filter #(= (str (:koodi %)) numero) (:koodisto liikennemerkki)))))]
    {:otsikko "Liikennemerkki"
     :tyyppi :string
     :hae (fn [rivi]
            (let [numero (get-in rivi [:varuste :tietue :tietolaji :arvot "asetusnr"])]
              (str numero " - " (asetusnr->teksti numero))))
     :leveys 2}))

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
    :leveys 1}
   {:otsikko "Puoli"
    :tyyppi :string
    :hae #(let [puoli (get-in % [:varuste :tietue :sijainti :tie :puoli])]
            (str puoli " - " (puoli->selitys puoli)))
    :leveys 1}])

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
           {:elementin-id (str (keyword (:kenttatunniste ominaisuus)) "-valikko")
            :tyyppi :valinta
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
