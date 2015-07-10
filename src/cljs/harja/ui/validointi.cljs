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
    viesti))

(defmethod validoi-saanto :urakan-aikana [_ _ data _ _ & [viesti]]
  (let [urakka @nav/valittu-urakka
        alkupvm (:alkupvm urakka)
        loppupvm (:loppupvm urakka)]
    (log "VALIDOI urakan aikana? " data " väliss? " alkupvm " - " loppupvm)
    (when (and data alkupvm loppupvm
               (not (pvm/valissa? data alkupvm loppupvm)))
      (or viesti
          (str "Päivämäärä ei ole urakan sisällä (" (pvm/pvm alkupvm) " \u2014 " (pvm/pvm loppupvm) ")")))))

(defmethod validoi-saanto :arvo-ei-setissa [_ _ data rivi taulukko & [setti-atom viesti]]
  (log "Tarkistetaan onko annettu arvo " (pr-str data) " setissä " (pr-str @setti-atom))
  (when (contains? @setti-atom data)
    viesti))
    
(defmethod validoi-saanto :ei-tyhja [_ nimi data _ _ & [viesti]]
  (when (str/blank? data)
    viesti))

(defmethod validoi-saanto :positiivinen-luku [_ _ data _ _ & [viesti]]
  (when (not (pos? data)) viesti))

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
