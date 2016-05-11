(ns harja.domain.oikeudet.makrot
  "Makro, joka lukee oikeusmatriisin Excelistä ja määrittelee sen Clojure datana.
  Täällä ei ole mitään muuta kutsuttavaa kuin maarittele-oikeudet!"
  (:require [dk.ative.docjure.spreadsheet :as xls]
            [clojure.string :as str]
            [clojure.set :refer [union]]
            [harja.tyokalut.functor :refer [fmap]]))

(defn- lue-workbook []
  (xls/load-workbook-from-file "resources/roolit.xlsx"))

(defn- roolimappaus [sheet]
  (->> sheet
       (xls/select-columns {:A :nimi :B :kuvaus :C :osapuoli :D :linkki})
       (drop 1)))

(defn- roolit-sheet [workbook]
  (xls/select-sheet "Roolit" workbook))

(defn- oikeudet-sheet [workbook]
  (xls/select-sheet "Oikeudet" workbook))

(defn- roolien-sarakkeet
  "Palauttaa Oikeudet sheetiltä löytyneet oikeudet ja niiden sarakkeet."
  [sheet]
  (keep (fn [sarake]
          (if-let [rooli (some-> sarake
                                 char
                                 (str 6)
                                 (xls/select-cell sheet)
                                 xls/read-cell)]
            {:sarake (char sarake) :rooli rooli}))
        (range (int \C) (int \Z))))

(defn- oikeuksien-rivit
  "Palauttaa eri oikeudet ja niiden rivinumerot."
  [sheet]
  (keep (fn [rivi]
          (let [osio (some-> (str "A" rivi)
                             (xls/select-cell sheet)
                             xls/read-cell)
                nakyma (some-> (str "B" rivi)
                               (xls/select-cell sheet)
                               xls/read-cell)]
            (when (and osio nakyma)
              {:osio osio :nakyma nakyma :rivi rivi})))
        (range 7 100)))

(defn- kanonisoi [teksti]
  (-> teksti
      str/lower-case
      (str/replace " " "")
      (str/replace "/" "-")
      (str/replace "ä" "a")
      (str/replace "ö" "o")))

(defn- oikeus-sym
  "Tekee symbol nimen oikeudelle osion ja näkymän yhdistämällä"
  [osio nakyma]
  (symbol (str (kanonisoi osio)
               "-"
               (kanonisoi nakyma))))

(defn- oikeudet [roolit sheet]
  (let [sarakkeet (roolien-sarakkeet sheet)
        rivit (oikeuksien-rivit sheet)
        kuvaus->rooli (into {}
                            (map (juxt :kuvaus :nimi))
                            roolit)]
    (map
     (fn [{:keys [osio nakyma rivi]}]
       (let [roolien-oikeudet (into {}
                                    (keep (fn [{:keys [sarake rooli]}]
                                            (let [oikeus (into #{}
                                                               (remove str/blank?)
                                                               (some-> (str sarake rivi)
                                                                       (xls/select-cell sheet)
                                                                       xls/read-cell
                                                                       (str/split #",")))
                                                  rooli (kuvaus->rooli rooli)]
                                              (when (and rooli (not (empty? oikeus)))
                                                [rooli oikeus])))
                                          sarakkeet))]
         {:sym (oikeus-sym osio nakyma)
          :osio osio
          :nakyma nakyma
          :taso1 (kanonisoi (str osio "-" (first (str/split nakyma #" "))))
          :kuvaus (str "Osio '" osio "' näkymä '" nakyma "'")
          :roolien-oikeudet roolien-oikeudet}))
     rivit)))

(defn- lue-oikeudet []
  (let [wb (lue-workbook)
        roolimappaus (-> wb roolit-sheet roolimappaus)
        oikeudet (->> wb oikeudet-sheet (oikeudet roolimappaus))]
    {:roolimappaus roolimappaus
     :oikeudet oikeudet}))

(defmacro maarittele-oikeudet! []
  (let [{:keys [roolimappaus oikeudet]} (lue-oikeudet)]
    `(do
       (def ~'roolit ~(into {}
                            (map (juxt :nimi identity))
                            roolimappaus))
       ~@(mapv
          (fn [oikeudet]
            `(do
               ~@(mapv (fn [{:keys [sym kuvaus roolien-oikeudet]}]
                         `(def ~sym (harja.domain.oikeudet/->KayttoOikeus
                                     ~kuvaus ~roolien-oikeudet)))
                       oikeudet)))
          (partition-all 16 oikeudet))

       ;; Määritellään osioille vielä roolit, jotka on union kaikista osion oikeuksista.
       ;; Näillä voi testata osion näkyvyyttä.
       (do
         ~@(mapv
            (fn [[osio oikeudet]]
              (let [roolien-oikeudet (apply merge-with union
                                            (map :roolien-oikeudet oikeudet))
                    sym (symbol (kanonisoi osio))]
                `(def ~sym
                   (harja.domain.oikeudet/->KayttoOikeus (str "Osio " ~osio)
                                                         ~roolien-oikeudet))))
            (group-by :osio oikeudet)))

       ;; Määritellään 1. tason näkymille oikeudet, jotka on union kaikista alaosioiden oikeuksista
       (do
         ~@(mapv (fn [[taso1 oikeudet]]
                   (let [roolien-oikeudet (apply merge-with union
                                                 (map :roolien-oikeudet oikeudet))
                         sym (symbol taso1)]
                     `(def ~sym
                        (harja.domain.oikeudet/->KayttoOikeus (str "Osio/taso " ~taso1)
                                                              ~roolien-oikeudet))))
                 (group-by :taso1
                           ;; Vain niille, joilla 1. taso ei ole ainoa taso
                           (filter (fn [{:keys [taso1 osio nakyma]}]
                                     (not= taso1 (str (kanonisoi osio) "-" (kanonisoi nakyma))))
                                   oikeudet))))

       ;; Määritellään yksi mäp, jossa on raporttien käyttöoikeudet nimen mukaan
       (def ~'raporttioikeudet ~(into {}
                                      (comp (filter #(= "raportit" (kanonisoi (:osio %))))
                                            (map (juxt :nakyma :sym)))
                                      oikeudet)))))
