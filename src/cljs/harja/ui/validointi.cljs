(ns harja.ui.validointi
  "Harjan käyttöön soveltuva geneerinen muokattava ruudukkokomponentti."
  (:require [reagent.core :refer [atom] :as r]
            [harja.loki :refer [log]]
            [harja.ui.yleiset :refer [ajax-loader linkki livi-pudotusvalikko]]
            [harja.ui.ikonit :as ikonit]
            [harja.ui.kentat :refer [tee-kentta]]
            [harja.pvm :as pvm]

            [cljs.core.async :refer [<! put! chan]]
            [clojure.string :as str]
            [schema.core :as s :include-macros true]
            [harja.pvm :as pvm]
            [harja.tiedot.urakka :as u]
            [harja.tiedot.navigaatio :as nav])
  (:require-macros [cljs.core.async.macros :refer [go]]))

;; Validointi
;; Rivin skeema voi määritellä validointisääntöjä.
;; validoi-saanto multimetodi toteuttaa tarkastuksen säännön keyword tyypin mukaan
;; nimi = Kentän nimi
;; data = Riville syötettävä data
;; rivi = Rivillä olevat tiedot
;; taulukko = Koko grid-taulukko
(defmulti validoi-saanto (fn [saanto nimi data rivi taulukko & optiot] saanto))

(defmethod validoi-saanto :hoitokaudella [_ _ data _ _ & [viesti]]
  (when (and data (not (pvm/valissa? data (first @u/valittu-hoitokausi) (second @u/valittu-hoitokausi))))
    (or viesti
        (str "Päivämäärä ei ole hoitokaudella " (pvm/pvm (first @u/valittu-hoitokausi))
             " - " (pvm/pvm (second @u/valittu-hoitokausi))))))

(defmethod validoi-saanto :urakan-aikana [_ _ data _ _ & [viesti]]
  (let [urakka @nav/valittu-urakka
        alkupvm (:alkupvm urakka)
        loppupvm (:loppupvm urakka)]
    (when (and data alkupvm loppupvm
               (not (pvm/valissa? data alkupvm loppupvm)))
      (or viesti
          (str "Päivämäärä ei ole urakan aikana (" (pvm/pvm alkupvm) " \u2014 " (pvm/pvm loppupvm) ")")))))

(defmethod validoi-saanto :urakan-aikana-ja-hoitokaudella [_ _ data _ _ & [viesti]]
  (let [urakka @nav/valittu-urakka
        alkupvm (:alkupvm urakka)
        loppupvm (:loppupvm urakka)
        hoitokausi-alku (first @u/valittu-hoitokausi)
        hoitokausi-loppu (second @u/valittu-hoitokausi)
        urakan-aikana? (and data alkupvm loppupvm
                           (pvm/valissa? data alkupvm loppupvm))
        hoitokaudella? (and data (pvm/valissa? data hoitokausi-alku hoitokausi-loppu))]
    (if (false? urakan-aikana?)
      (or viesti
          (str "Päivämäärä ei ole urakan aikana (" (pvm/pvm alkupvm) " \u2014 " (pvm/pvm loppupvm) ")"))
      (if (false? hoitokaudella?)
        (or viesti
            (str "Päivämäärä ei ole hoitokaudella " (pvm/pvm hoitokausi-alku)
                 " - " (pvm/pvm hoitokausi-loppu)))))))

(defmethod validoi-saanto :uusi-arvo-ei-setissa [_ _ data rivi taulukko & [setti-atom viesti]]
  "Tarkistaa, onko rivi uusi ja arvo annetussa setissä."
  (log "Tarkistetaan onko annettu arvo " (pr-str data) " setissä " (pr-str @setti-atom))
  (when (and (contains? @setti-atom data) (neg? (:id rivi)))
    viesti))

(defmethod validoi-saanto :ei-tyhja [_ nimi data _ _ & [viesti]]
  (when (str/blank? data)
    viesti))

(defmethod validoi-saanto :joku-naista [_ _ data rivi _ & avaimet-ja-viesti]
  (let [avaimet (if (string? (last avaimet-ja-viesti)) (butlast avaimet-ja-viesti) avaimet-ja-viesti)
        viesti (if (string? (last avaimet-ja-viesti))
                 (last avaimet-ja-viesti)

                 (str "Anna joku näistä: "
                      (clojure.string/join ", "
                                           (map (comp clojure.string/capitalize name) avaimet))))]
    (when-not (some #(not (str/blank? (% rivi))) avaimet) viesti)))

(defmethod validoi-saanto :uniikki [_ nimi data _ taulukko & [viesti]]
  (let [rivit-arvoittain (group-by nimi (vals taulukko))]
    (log "rivit-arvoittain:" (pr-str rivit-arvoittain) " JA DATA: " data)
    (when (> (count (get rivit-arvoittain data)) 1)
      viesti)))

(defmethod validoi-saanto :pvm-kentan-jalkeen [_ _ data rivi _ & [avain viesti]]
  (when (and
          (avain rivi)
          (pvm/ennen? data (avain rivi)))
    viesti))

(defmethod validoi-saanto :pvm-toisen-pvmn-jalkeen [_ _ data rivi _ & [vertailtava-pvm viesti]]
  (when (and
          vertailtava-pvm
          (pvm/ennen? data vertailtava-pvm))
    viesti))

(defmethod validoi-saanto :pvm-ei-annettu-ennen-toista [_ _ data rivi _ & [avain viesti]]
  (when (and
            (not (nil? data))
            (nil? (avain rivi)))
    viesti))

(defmethod validoi-saanto :lampotila [_ _ data rivi _ _ & [viesti]]
  (when-not (<= -55 data 55)
    (or viesti "Anna lämpotila välillä -55 \u2103 \u2014 +55 \u2103")))
  
(defn validoi-saannot
  "Palauttaa kaikki validointivirheet kentälle, jos tyhjä niin validointi meni läpi."
  [nimi data rivi taulukko saannot]
  (keep (fn [saanto]
          (if (fn? saanto)
            (saanto data rivi)
            (let [[saanto & optiot] saanto]
              (apply validoi-saanto saanto nimi data rivi taulukko optiot))))
        saannot))

(defn validoi-rivi
  "Tekee validoinnin yhden rivin / lomakkeen kaikille kentille. Palauttaa mäpin kentän nimi -> virheet vektori.
  Tyyppi on joko :validoi (default) tai :varoita"
  ([taulukko rivi skeema] (validoi-rivi taulukko rivi skeema :validoi))
  ([taulukko rivi skeema tyyppi]
   (loop [v {}
          [s & skeema] skeema]
     (if-not s
       v
       (let [{:keys [nimi hae]} s
             validoi (tyyppi s)]
         (if (empty? validoi)
           (recur v skeema)
           (let [virheet (validoi-saannot nimi (if hae
                                                 (hae rivi)
                                                 (get rivi nimi))
                                          rivi taulukko
                                          validoi)]
             (recur (if (empty? virheet) v (assoc v nimi virheet))
                    skeema))))))))
